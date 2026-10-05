package com.industri.fleettrack.data

import android.content.Context
import com.industri.fleettrack.data.local.AppDatabase
import com.industri.fleettrack.data.remote.NetworkModule
import com.industri.fleettrack.data.repository.DeliveryRepository

/**
 * Dependency container sederhana. Aplikasi ini kecil sehingga DI manual
 * (service locator) cukup dan tidak menambah layer/framework tambahan.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val deliveryRepository: DeliveryRepository by lazy {
        DeliveryRepository(
            deliveryDao = database.deliveryDao(),
            syncQueueDao = database.syncQueueDao(),
            api = NetworkModule.apiService
        )
    }
}