package com.offlinehub

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.offlinehub.data.AppDb
import com.offlinehub.data.SettingsStore
import com.offlinehub.player.PlayerController
import com.offlinehub.sync.NetworkMonitor
import com.offlinehub.sync.SyncScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class OfflineHubApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { AppDb.get(this) }
    val dao get() = db.dao()
    val settings by lazy { SettingsStore(this) }
    lateinit var network: NetworkMonitor
    lateinit var player: PlayerController

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        PDFBoxResourceLoader.init(this)
        network = NetworkMonitor(this)
        player = PlayerController(this)
        SyncScheduler.schedulePeriodic(this)

        // Reschedule when scheduling-related settings change.
        scope.launch {
            settings.flow
                .distinctUntilChanged { a, b ->
                    a.autoSync == b.autoSync && a.syncIntervalHours == b.syncIntervalHours &&
                        a.wifiOnly == b.wifiOnly && a.onlyCharging == b.onlyCharging
                }
                .drop(1)
                .collect { SyncScheduler.schedulePeriodic(this@OfflineHubApp) }
        }
        // Automatic synchronization the moment internet becomes available.
        scope.launch {
            var wasOnline = network.online.value
            network.online.collect { now ->
                if (now && !wasOnline && settings.value.autoSync && settings.value.onboarded) {
                    SyncScheduler.syncSoon(this@OfflineHubApp, "Internet connected")
                }
                wasOnline = now
            }
        }
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_SYNC, "Synchronization", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while OfflineHub synchronizes content"
            }
        )
    }

    companion object {
        const val CH_SYNC = "sync"
        lateinit var instance: OfflineHubApp
            private set
    }
}

val app: OfflineHubApp get() = OfflineHubApp.instance
