package com.laxwifi.app

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Holds a Wi-Fi network callback for as long as the app is installed and runs
 * the saved connect / disconnect actions. Networks are recognised by their
 * [Fingerprint], so this works with Location turned off.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cm: ConnectivityManager

    private val linkProps = HashMap<Network, LinkProperties>()
    private val pending = HashMap<Network, Runnable>()

    /** Saved-network id each live Wi-Fi network was recognised as. */
    private val matched = HashMap<Network, String>()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            linkProps[network] = lp
            publish()
            if (network !in matched) schedule(network, SETTLE_MS)
        }

        override fun onLost(network: Network) {
            pending.remove(network)?.let(handler::removeCallbacks)
            linkProps.remove(network)
            val id = matched.remove(network)
            if (id != null && id !in matched.values) left(id)
            publish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(ConnectivityManager::class.java)
        ActionRunner.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!registered) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, callback, handler)
            registered = true
            // If the phone left the network while the service was not running,
            // the disconnect actions are still owed.
            handler.postDelayed(::reconcile, RECONCILE_MS)
        } else if (intent?.action == ACTION_REEVALUATE) {
            for (network in linkProps.keys.toList()) {
                matched.remove(network)
                schedule(network, 0)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) cm.unregisterNetworkCallback(callback)
        handler.removeCallbacksAndMessages(null)
        MonitorState.status.value = WifiStatus()
        super.onDestroy()
    }

    /** Link properties arrive in pieces (IPv4, then IPv6), so wait for them to settle. */
    private fun schedule(network: Network, delayMs: Long) {
        pending.remove(network)?.let(handler::removeCallbacks)
        val task = Runnable {
            pending.remove(network)
            evaluate(network)
        }
        pending[network] = task
        handler.postDelayed(task, delayMs)
    }

    private fun evaluate(network: Network) {
        val lp = linkProps[network] ?: return
        val match = Repo.networks.value.bestMatch(lp.toFingerprint())
        if (match == null) {
            publish()
            return
        }
        matched[network] = match.id
        val previous = Repo.activeId
        if (previous != match.id) {
            if (previous != null && previous !in matched.values) left(previous)
            Repo.activeId = match.id
            Log.i(TAG, "Connected to ${match.name}")
            ActionRunner.run(this, match, match.onConnect)
        }
        publish()
    }

    private fun left(id: String) {
        if (Repo.activeId != id) return
        Repo.activeId = null
        val network = Repo.get(id) ?: return
        Log.i(TAG, "Disconnected from ${network.name}")
        ActionRunner.run(this, network, network.onDisconnect)
    }

    private fun reconcile() {
        val active = Repo.activeId ?: return
        if (active !in matched.values && pending.isEmpty()) left(active)
    }

    private fun publish() {
        val status = WifiStatus(onWifi = linkProps.isNotEmpty(), matchedId = matched.values.firstOrNull())
        MonitorState.status.value = status
    }

    // The text is fixed on purpose: re-posting it on every connect and disconnect
    // read as a fresh notification each time.
    private fun buildNotification(): Notification {
        return Notification.Builder(this, ActionRunner.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setColor(0xFFFF00D4.toInt())
            .setContentTitle("Lax Wi-Fi")
            .setContentText("Watching for Wi-Fi changes")
            .setContentIntent(ActionRunner.openAppIntent(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "LaxWifi"
        private const val NOTIFICATION_ID = 1
        private const val SETTLE_MS = 2500L
        private const val RECONCILE_MS = 8000L
        const val ACTION_REEVALUATE = "com.laxwifi.app.REEVALUATE"

        /** Starts the monitor; with [reevaluate] it also re-matches the current network. */
        fun start(context: Context, reevaluate: Boolean = false) {
            val intent = Intent(context, MonitorService::class.java)
            if (reevaluate) intent.action = ACTION_REEVALUATE
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start monitor", e)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> MonitorService.start(context)
        }
    }
}
