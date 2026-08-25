package com.hermes.client

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.hermes.client.data.diagnostics.CrashReporter
import com.hermes.client.data.diagnostics.DebugLog
import com.hermes.client.data.network.HermesGatewayClient
import com.hermes.client.data.repository.SettingsStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltAndroidApp
class HermesApp : Application() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var gateway: HermesGatewayClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Capture uncaught exceptions to a file so the next launch can surface the trace (no adb).
        CrashReporter.install(this)
        // Restore the diagnostic-logging toggle at launch so capture is active before the
        // Diagnostics screen is ever opened (e.g. to catch a failure on the first session open).
        settingsStore.debugLogging
            .distinctUntilChanged()
            .onEach { DebugLog.setEnabled(it) }
            .launchIn(appScope)
        registerReconnectNudges()
    }

    /**
     * Nudge a reconnect on the same signals the desktop client uses (`window.online` +
     * `document.visibilitychange`) instead of an app-level heartbeat RPC: the socket's own
     * transport-level liveness (OkHttp's WS ping, configured in AppModule) already detects a
     * dead connection and triggers backoff reconnect on its own; these two hooks just make
     * recovery near-instant for the two situations a user actually notices — bringing the app
     * back to the foreground (screen unlock, app switch) and the network coming back after a
     * dead zone/airplane mode. connect() is idempotent, so calling it when already connected
     * or mid-backoff is always safe.
     */
    private fun registerReconnectNudges() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                DebugLog.log("ws", "app foregrounded — nudging reconnect")
                gateway.connect()
            }
        })
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager?.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    DebugLog.log("ws", "network available — nudging reconnect")
                    gateway.connect()
                }
            },
        )
    }
}
