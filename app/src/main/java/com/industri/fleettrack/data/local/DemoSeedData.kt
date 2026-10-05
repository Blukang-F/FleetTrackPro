package com.industri.fleettrack.data.local

import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus

/**
 * Data demo lokal. Dipakai sebagai fallback ketika server tidak dapat dihubungi
 * sehingga kurir tetap bisa melihat manifest hari ini.
 */
object DemoSeedData {

    private const val T0 = 1_757_000_000_000L // timestamp dasar tetap agar urutannya konsisten

    val orders: List<DeliveryOrderEntity> = listOf(
        DeliveryOrderEntity(
            orderId = "ORD-2026-8801",
            trackingNumber = "EXP-90021-JKT",
            recipientName = "Ibu Ratna Dewi",
            recipientPhone = "081234567890",
            destinationAddress = "Jl. Melati Raya No. 45, Harjamukti, Cirebon",
            codAmount = 125000.0,
            deliveryStatus = DeliveryStatus.PENDING.raw,
            isSynced = true,
            updatedAt = T0
        ),
        DeliveryOrderEntity(
            orderId = "ORD-2026-8802",
            trackingNumber = "EXP-90022-BDG",
            recipientName = "Pak Bambang Sutrisno",
            recipientPhone = "081398765432",
            destinationAddress = "Jl. Kartini No. 12, Kejaksan, Cirebon",
            codAmount = 0.0,
            deliveryStatus = DeliveryStatus.IN_DELIVERY.raw,
            isSynced = true,
            updatedAt = T0 + 1_000L
        ),
        DeliveryOrderEntity(
            orderId = "ORD-2026-8803",
            trackingNumber = "EXP-90023-CRB",
            recipientName = "Sdr. Dimas Prasetyo",
            recipientPhone = "085712349988",
            destinationAddress = "Perum Citra Garden Blok B-3, Kedawung",
            codAmount = 75000.0,
            deliveryStatus = DeliveryStatus.DELIVERED.raw,
            isSynced = true,
            updatedAt = T0 + 2_000L
        )
    )
}