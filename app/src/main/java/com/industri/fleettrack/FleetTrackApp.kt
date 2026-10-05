package com.industri.fleettrack

import android.app.Application
import com.industri.fleettrack.data.AppContainer
import timber.log.Timber

class FleetTrackApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        container = AppContainer(this)
    }
}