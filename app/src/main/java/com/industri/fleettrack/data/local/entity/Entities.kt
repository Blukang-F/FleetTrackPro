package com.industri.fleettrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Status enum untuk pengiriman. Nilai string yang disimpan di database
 * sengaja disimpan sebagai String agar mudah dipetakan dengan API.
 */
enum class DeliveryStatus(val raw: String, val label: String) {
    PENDING("PENDING", "Menunggu"),
    IN_DELIVERY("IN_DELIVERY", "Dalam Pengiriman"),
    DELIVERED("DELIVERED", "Selesai"),
    FAILED("FAILED", "Gagal");

    companion object {
        fun from(raw: String?): DeliveryStatus =
            entries.firstOrNull { it.raw.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

@Entity(
    tableName = "delivery_orders",
    indices = [
        Index(value = ["tracking_number"], unique = true)
    ]
)
data class DeliveryOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "order_id")
    val orderId: String,

    @ColumnInfo(name = "tracking_number")
    val trackingNumber: String,

    @ColumnInfo(name = "recipient_name")
    val recipientName: String,

    @ColumnInfo(name = "recipient_phone")
    val recipientPhone: String,

    @ColumnInfo(name = "destination_address")
    val destinationAddress: String,

    @ColumnInfo(name = "cod_amount")
    val codAmount: Double = 0.0,

    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String,

    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    val status: DeliveryStatus
        get() = DeliveryStatus.from(deliveryStatus)
}

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val queueId: Long = 0,

    val orderId: String,
    val targetStatus: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)