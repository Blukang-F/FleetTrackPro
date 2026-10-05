package com.industri.fleettrack.data.repository

import com.industri.fleettrack.data.local.dao.DeliveryDao
import com.industri.fleettrack.data.local.dao.SyncQueueDao
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.DeliveryStatus
import com.industri.fleettrack.data.local.entity.SyncQueueEntity
import com.industri.fleettrack.data.remote.api.CourierApiService
import com.industri.fleettrack.data.remote.api.ManifestOrderDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber
import java.io.IOException

/**
 * Single Source of Truth untuk data manifest.
 *
 * Kontrak repository:
 * - UI hanya membaca dari [manifestOrdersFlow] (bersumber Room).
 * - Semua penulisan status dilakukan lokal lebih dulu, lalu dicoba dikirim ke server.
 * - Kegagalan jaringan tidak pernah menghapus atau membatalkan perubahan kurir,
 *   melainkan dimasukkan ke tabel sync_queue untuk dicoba lagi nanti.
 */
class DeliveryRepository(
    private val deliveryDao: DeliveryDao,
    private val syncQueueDao: SyncQueueDao,
    private val api: CourierApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Seluruh order, urut dari yang paling baru di-update. */
    val manifestOrdersFlow: Flow<List<DeliveryOrderEntity>> = deliveryDao.observeAllOrders()

    /** Order yang statusnya tertentu. */
    fun ordersByStatusFlow(status: DeliveryStatus): Flow<List<DeliveryOrderEntity>> =
        deliveryDao.observeOrdersByStatus(status.raw)

    /** Pencarian lokal (offline) berdasarkan resi, nama penerima, atau alamat. */
    fun searchOrdersFlow(query: String): Flow<List<DeliveryOrderEntity>> =
        if (query.isBlank()) manifestOrdersFlow else deliveryDao.searchOrders(query.trim())

    /** Jumlah perubahan lokal yang belum berhasil dikirim ke server. */
    val pendingSyncCount: Flow<Int> = syncQueueDao.observePendingCount()

    suspend fun pendingSyncCountSnapshot(): Int = withContext(ioDispatcher) {
        syncQueueDao.pendingCount()
    }

    suspend fun seedIfEmpty() = withContext(ioDispatcher) {
        com.industri.fleettrack.data.local.DemoSeedData.let { seed ->
            deliveryDao.seedIfEmpty(seed.orders)
        }
    }

    suspend fun refreshManifest(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val response = api.getTodayManifest()
            val body = response.requireBody("Gagal memuat manifest")
            if (body.isEmpty()) {
                Timber.w("Manifest kosong dari server, data lokal dipertahankan")
            } else {
                persistRemoteOrders(body)
            }
        }.onFailure { error ->
            Timber.w(error, "Refresh manifest gagal, data lokal dipertahankan")
        }.also {
            if (it.isSuccess) {
                syncPendingChanges()
            }
        }
    }

    /**
     * Offline-first update status: tulis ke Room lebih dulu (UI langsung berubah),
     * baru coba kirim ke server.
     */
    suspend fun updateDeliveryStatus(
        orderId: String,
        newStatus: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val order = deliveryDao.getOrderById(orderId)
        if (order == null) {
            return@withContext Result.failure(NoSuchElementException("Order $orderId tidak ditemukan"))
        }

        // 1. Tulis lokal + tandai belum tersinkron.
        deliveryDao.updateStatus(orderId, newStatus, System.currentTimeMillis())

        // 2. Hapus antrean lama untuk status yang sama agar tidak dobel.
        syncQueueDao.removeByOrderIdAndStatus(orderId, newStatus)

        val pushed = pushStatusToServer(orderId, newStatus)
        if (pushed) {
            deliveryDao.markAsSynced(orderId)
            Result.success(Unit)
        } else {
            // 3. Gagal/offline -> antrekan, perubahan kurir tetap ada.
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    orderId = orderId,
                    targetStatus = newStatus,
                    notes = "Diperbarui dari perangkat kurir saat offline"
                )
            )
            Result.success(Unit)
        }
    }

    /** Mengirim ulang seluruh antrean yang masih tertunda. */
    suspend fun syncPendingChanges(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val pending = syncQueueDao.getAllQueued()
            if (pending.isEmpty()) return@runCatching

            val failed = pending.count { item ->
                val pushed = pushStatusToServer(item.orderId, item.targetStatus)
                if (pushed) {
                    deliveryDao.markAsSynced(item.orderId)
                    syncQueueDao.removeById(item.queueId)
                }
                !pushed
            }
            if (failed > 0) {
                throw IOException("$failed perubahan belum tersinkron ke server")
            }
        }.onFailure { error ->
            Timber.w(error, "Sinkronisasi tertunda, antrean tetap disimpan")
        }
    }

    private suspend fun pushStatusToServer(orderId: String, status: String): Boolean {
        return try {
            val response: Response<*> = api.updateDeliveryStatus(orderId, status)
            response.requireBody("Server menolak pembaruan status") != null
        } catch (e: IOException) {
            Timber.w(e, "Gagal mengirim status $orderId -> $status")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error tak terduga saat push status $orderId")
            false
        }
    }

    /**
     * Menyimpan hasil API ke Room. Order lokal yang belum tersinkron
     * (isSynced = false) tidak ditimpa supaya perubahan kurir tidak hilang.
     */
    private suspend fun persistRemoteOrders(dtoList: List<ManifestOrderDto>) {
        val mapped = dtoList.mapNotNull { it.toEntityOrNull() }
        if (mapped.isEmpty()) return

        val unsyncedIds = deliveryDao.getUnsyncedOrders().map { it.orderId }.toSet()
        val resolved = mapped.map { remote ->
            if (remote.orderId in unsyncedIds) {
                val local = deliveryDao.getOrderById(remote.orderId)
                remote.copy(
                    deliveryStatus = local?.deliveryStatus ?: remote.deliveryStatus,
                    codAmount = local?.codAmount ?: remote.codAmount,
                    isSynced = false
                )
            } else {
                remote
            }
        }
        deliveryDao.upsertOrders(resolved)
    }

    private fun <T> Response<T>.requireBody(errorMessage: String): T {
        if (!isSuccessful) {
            throw ApiException(code(), errorMessage, body()?.toString().orEmpty())
        }
        val payload = body() ?: throw ApiException(code(), "Respons server kosong", "")
        return payload
    }

    private fun ManifestOrderDto.toEntityOrNull(): DeliveryOrderEntity? {
        val id = orderId?.trim().orEmpty()
        val resi = trackingNumber?.trim().orEmpty()
        if (id.isEmpty() || resi.isEmpty()) {
            Timber.w("Manifest item dilewati karena order_id/tracking_number kosong")
            return null
        }
        return DeliveryOrderEntity(
            orderId = id,
            trackingNumber = resi,
            recipientName = recipientName?.trim().orEmpty().ifEmpty { "Penerima tidak diketahui" },
            recipientPhone = recipientPhone?.trim().orEmpty(),
            destinationAddress = destinationAddress?.trim().orEmpty(),
            codAmount = codAmount ?: 0.0,
            deliveryStatus = DeliveryStatus.from(status).raw,
            failureReason = failureReason,
            isSynced = true,
            updatedAt = System.currentTimeMillis()
        )
    }
}

class ApiException(
    val code: Int,
    override val message: String,
    val rawBody: String
) : IOException(message)