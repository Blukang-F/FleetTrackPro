package com.industri.fleettrack.ui.manifest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.data.repository.DeliveryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManifestViewModel(
    private val repository: DeliveryRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val refreshingFlow = MutableStateFlow(false)
    private val hasLoadedFlow = MutableStateFlow(false)

    private val eventsChannel = Channel<ManifestEvent>(Channel.BUFFERED)
    val events: Flow<ManifestEvent> = eventsChannel.receiveAsFlow()

    val searchQuery: StateFlow<String> = queryFlow.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = refreshingFlow.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredOrders: Flow<List<DeliveryOrderEntity>> = queryFlow
        .distinctUntilChanged()
        .flatMapLatest { repository.searchOrdersFlow(it) }

    val uiState: StateFlow<ManifestUiState> =
        combine(filteredOrders, hasLoadedFlow) { orders, hasLoaded ->
            if (!hasLoaded && orders.isEmpty()) {
                ManifestUiState.Loading
            } else {
                ManifestUiState.Success(orders)
            }
        }.catch { error ->
            emit(ManifestUiState.Error(error.message ?: "Terjadi kesalahan saat memuat data"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ManifestUiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.refreshManifest()
            hasLoadedFlow.value = true
            repository.syncPendingChanges()
        }
    }

    fun onSearchQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun refreshData() {
        if (refreshingFlow.value) return
        viewModelScope.launch {
            refreshingFlow.value = true
            val result = repository.refreshManifest()
            refreshingFlow.value = false

            result
                .onSuccess {
                    val pending = repository.pendingSyncCountSnapshot()
                    if (pending > 0) {
                        eventsChannel.trySend(ManifestEvent.PendingSync(pending))
                    }
                }
                .onFailure { error ->
                    eventsChannel.trySend(ManifestEvent.RefreshFailed(error.friendlyMessage()))
                }
        }
    }

    fun markAsDelivered(orderId: String) {
        viewModelScope.launch {
            repository.updateDeliveryStatus(orderId, DeliveryStatus.DELIVERED.raw)
                .onSuccess {
                    val pending = repository.pendingSyncCountSnapshot()
                    if (pending > 0) {
                        eventsChannel.trySend(ManifestEvent.PendingSync(pending))
                    }
                }
                .onFailure { error ->
                    eventsChannel.trySend(ManifestEvent.UpdateFailed(error.friendlyMessage()))
                }
        }
    }

    fun retryPendingSync() {
        viewModelScope.launch {
            eventsChannel.trySend(ManifestEvent.SyncStarted)
            repository.syncPendingChanges()
                .onSuccess {
                    eventsChannel.trySend(ManifestEvent.SyncFinished(repository.pendingSyncCountSnapshot()))
                }
                .onFailure { error ->
                    eventsChannel.trySend(ManifestEvent.SyncFinished(repository.pendingSyncCountSnapshot(), error.friendlyMessage()))
                }
        }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Tidak ada koneksi internet. Data disimpan di perangkat."
        is java.net.SocketTimeoutException -> "Koneksi timeout. Data disimpan di perangkat."
        is java.io.IOException -> message ?: "Jaringan bermasalah. Data disimpan di perangkat."
        else -> message ?: "Terjadi kesalahan."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as com.industri.fleettrack.FleetTrackApp
                ManifestViewModel(app.container.deliveryRepository)
            }
        }

        }
}

sealed interface ManifestEvent {
    data class PendingSync(val count: Int) : ManifestEvent
    data class RefreshFailed(val message: String) : ManifestEvent
    data class UpdateFailed(val message: String) : ManifestEvent
    data object SyncStarted : ManifestEvent
    data class SyncFinished(val remaining: Int, val message: String? = null) : ManifestEvent
}