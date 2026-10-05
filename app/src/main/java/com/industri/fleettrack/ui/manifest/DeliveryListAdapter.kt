package com.industri.fleettrack.ui.manifest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.industri.fleettrack.R
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.databinding.ItemDeliveryOrderBinding
import com.industri.fleettrack.util.CurrencyFormatter

class DeliveryListAdapter(
    private val onDeliverClicked: (DeliveryOrderEntity) -> Unit
) : ListAdapter<DeliveryOrderEntity, DeliveryListAdapter.DeliveryViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryViewHolder {
        val binding = ItemDeliveryOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeliveryViewHolder(binding, onDeliverClicked)
    }

    override fun onBindViewHolder(holder: DeliveryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeliveryViewHolder(
        private val binding: ItemDeliveryOrderBinding,
        private val onDeliverClicked: (DeliveryOrderEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: DeliveryOrderEntity) {
            val context = binding.root.context
            val status = order.status

            binding.textTrackingNumber.text = order.trackingNumber
            binding.textRecipientName.text = order.recipientName
            binding.textRecipientPhone.text = order.recipientPhone
            binding.textAddress.text = order.destinationAddress
            binding.textCodAmount.text = CurrencyFormatter.formatRupiah(order.codAmount)

            binding.textStatus.text = context.getString(
                when (status) {
                    DeliveryStatus.PENDING -> R.string.status_pending
                    DeliveryStatus.IN_DELIVERY -> R.string.status_in_delivery
                    DeliveryStatus.DELIVERED -> R.string.status_delivered
                    DeliveryStatus.FAILED -> R.string.status_failed
                }
            )

            val statusAppearance = status.appearance()
            binding.textStatus.setTextColor(context.getColor(statusAppearance.textColorRes))
            binding.badgeStatus.setCardBackgroundColor(
                context.getColor(statusAppearance.backgroundColorRes)
            )
            binding.textSyncState.text = context.getString(
                if (order.isSynced) R.string.sync_done else R.string.sync_pending
            )
            binding.textSyncState.setTextColor(
                context.getColor(
                    if (order.isSynced) R.color.text_secondary else R.color.status_warning
                )
            )

            val isDelivered = status == DeliveryStatus.DELIVERED
            binding.buttonDeliver.text = context.getString(
                if (isDelivered) R.string.action_delivered_done else R.string.action_deliver
            )
            binding.buttonDeliver.isEnabled = !isDelivered
            binding.buttonDeliver.setTextColor(
                context.getColor(if (isDelivered) R.color.white else R.color.status_pending_text)
            )
            binding.buttonDeliver.backgroundTintList = android.content.res.ColorStateList.valueOf(
                context.getColor(
                    if (isDelivered) R.color.status_delivered else R.color.status_pending
                )
            )
            binding.buttonDeliver.contentDescription = context.getString(
                R.string.cd_mark_delivered,
                order.trackingNumber
            )

            // Listener selalu di-install ulang pada setiap bind dengan item terkini.
            binding.buttonDeliver.setOnClickListener {
                if (!isDelivered) {
                    onDeliverClicked(order)
                }
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DeliveryOrderEntity>() {
            override fun areItemsTheSame(
                oldItem: DeliveryOrderEntity,
                newItem: DeliveryOrderEntity
            ): Boolean = oldItem.orderId == newItem.orderId

            override fun areContentsTheSame(
                oldItem: DeliveryOrderEntity,
                newItem: DeliveryOrderEntity
            ): Boolean = oldItem == newItem
        }
    }
}

private data class StatusAppearance(val backgroundColorRes: Int, val textColorRes: Int)

private fun DeliveryStatus.appearance(): StatusAppearance = when (this) {
    DeliveryStatus.DELIVERED -> StatusAppearance(R.color.status_delivered, R.color.white)
    DeliveryStatus.FAILED -> StatusAppearance(R.color.status_failed, R.color.white)
    DeliveryStatus.IN_DELIVERY -> StatusAppearance(R.color.status_in_delivery, R.color.white)
    DeliveryStatus.PENDING -> StatusAppearance(R.color.status_pending_soft, R.color.status_pending_text)
}