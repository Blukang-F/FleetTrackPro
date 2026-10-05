package com.industri.fleettrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.industri.fleettrack.data.local.entity.DeliveryOrderEntity
import com.industri.fleettrack.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {

    @Query("SELECT * FROM delivery_orders ORDER BY updated_at DESC")
    fun observeAllOrders(): Flow<List<DeliveryOrderEntity>>

    @Query(
        """
        SELECT * FROM delivery_orders
        WHERE delivery_status = :status
        ORDER BY updated_at DESC
        """
    )
    fun observeOrdersByStatus(status: String): Flow<List<DeliveryOrderEntity>>

    @Query(
        """
        SELECT * FROM delivery_orders
        WHERE tracking_number LIKE '%' || :query || '%'
           OR recipient_name LIKE '%' || :query || '%'
           OR destination_address LIKE '%' || :query || '%'
        ORDER BY updated_at DESC
        """
    )
    fun searchOrders(query: String): Flow<List<DeliveryOrderEntity>>

    @Query("SELECT * FROM delivery_orders WHERE order_id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): DeliveryOrderEntity?

    @Query("SELECT * FROM delivery_orders WHERE tracking_number = :trackingNumber LIMIT 1")
    suspend fun getOrderByTrackingNumber(trackingNumber: String): DeliveryOrderEntity?

    @Query("SELECT COUNT(*) FROM delivery_orders")
    suspend fun countOrders(): Int

    @Upsert
    suspend fun upsertOrders(orders: List<DeliveryOrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<DeliveryOrderEntity>)

    @Query(
        """
        UPDATE delivery_orders
        SET delivery_status = :newStatus,
            is_synced = 0,
            updated_at = :updatedAt
        WHERE order_id = :orderId
        """
    )
    suspend fun updateStatus(orderId: String, newStatus: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query(
        """
        UPDATE delivery_orders
        SET is_synced = 1
        WHERE order_id = :orderId
        """
    )
    suspend fun markAsSynced(orderId: String): Int

    @Query("SELECT * FROM delivery_orders WHERE is_synced = 0 ORDER BY updated_at DESC")
    suspend fun getUnsyncedOrders(): List<DeliveryOrderEntity>

    @Query("DELETE FROM delivery_orders WHERE order_id = :orderId")
    suspend fun deleteOrder(orderId: String): Int

    @Query("DELETE FROM delivery_orders")
    suspend fun deleteAllOrders()

    /**
     * Seeds data demo hanya ketika tabel masih kosong.
     */
    @Transaction
    suspend fun seedIfEmpty(seed: List<DeliveryOrderEntity>) {
        if (countOrders() == 0 && seed.isNotEmpty()) {
            insertOrders(seed)
        }
    }
}

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getAllQueued(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    fun observeAllQueued(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun pendingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE queue_id = :queueId")
    suspend fun removeById(queueId: Long): Int

    @Query("DELETE FROM sync_queue WHERE order_id = :orderId")
    suspend fun removeByOrderId(orderId: String): Int

    @Query("DELETE FROM sync_queue WHERE order_id = :orderId AND target_status = :targetStatus")
    suspend fun removeByOrderIdAndStatus(orderId: String, targetStatus: String): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}