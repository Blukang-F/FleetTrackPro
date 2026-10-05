package com.industri.fleettrack.ui.manifest

import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity

sealed class ManifestUiState {
    data object Loading : ManifestUiState()

    data class Success(
        val orders: List<DeliveryOrderEntity>
    ) : ManifestUiState()

    data class Error(
        val message: String
    ) : ManifestUiState()
}