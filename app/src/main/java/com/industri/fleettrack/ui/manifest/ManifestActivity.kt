package com.industri.fleettrack.ui.manifest

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.industri.fleettrack.R
import com.industri.fleettrack.databinding.ActivityManifestBinding
import kotlinx.coroutines.launch

class ManifestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManifestBinding
    private val viewModel: ManifestViewModel by viewModels { ManifestViewModel.Factory }

    private val adapter: DeliveryListAdapter by lazy {
        DeliveryListAdapter(onDeliverClicked = { order ->
            viewModel.markAsDelivered(order.orderId)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManifestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecycler()
        setupSearch()
        setupSwipeRefresh()
        binding.buttonRetry.setOnClickListener { viewModel.refreshData() }
        binding.buttonRetrySync.setOnClickListener { viewModel.retryPendingSync() }

        observeViewModel()
    }

    private fun setupRecycler() {
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)
        binding.recyclerOrders.setHasFixedSize(true)
        binding.recyclerOrders.adapter = adapter
    }

    private fun setupSearch() {
        binding.editSearch.doAfterTextChanged { editable ->
            viewModel.onSearchQueryChanged(editable?.toString().orEmpty())
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshData() }
        binding.swipeRefresh.setColorSchemeResources(R.color.color_primary, R.color.color_accent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> render(state) }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.swipeRefresh.isRefreshing = it }
                }
                launch {
                    viewModel.pendingSyncCount.collect { count ->
                        binding.pendingSyncBar.visibility =
                            if (count > 0) View.VISIBLE else View.GONE
                        binding.textPendingSync.text =
                            resources.getQuantityString(
                                R.plurals.pending_sync_message,
                                count,
                                count
                            )
                    }
                }
                launch {
                    viewModel.events.collect { event -> handleEvent(event) }
                }
            }
        }
    }

    private fun render(state: ManifestUiState) {
        when (state) {
            is ManifestUiState.Loading -> {
                binding.progressInitial.visibility = View.VISIBLE
                binding.stateEmpty.visibility = View.GONE
                binding.stateError.visibility = View.GONE
            }

            is ManifestUiState.Success -> {
                binding.progressInitial.visibility = View.GONE
                binding.stateError.visibility = View.GONE
                binding.stateEmpty.visibility =
                    if (state.orders.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = getString(
                    if (viewModel.searchQuery.value.isBlank()) {
                        R.string.empty_manifest
                    } else {
                        R.string.empty_search_result
                    }
                )
                adapter.submitList(state.orders)
            }

            is ManifestUiState.Error -> {
                binding.progressInitial.visibility = View.GONE
                binding.stateEmpty.visibility = View.GONE
                binding.stateError.visibility = View.VISIBLE
                binding.textError.text = state.message
            }
        }
    }

    private fun handleEvent(event: ManifestEvent) {
        val message = when (event) {
            is ManifestEvent.PendingSync ->
                resources.getQuantityString(R.plurals.pending_sync_message, event.count, event.count)

            is ManifestEvent.RefreshFailed -> event.message
            is ManifestEvent.UpdateFailed -> event.message
            ManifestEvent.SyncStarted -> getString(R.string.sync_started)

            is ManifestEvent.SyncFinished -> event.message
                ?: getString(R.string.sync_finished, event.remaining)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_retry_sync) { viewModel.retryPendingSync() }
            .show()
    }
}