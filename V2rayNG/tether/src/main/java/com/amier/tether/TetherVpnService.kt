package com.amier.tether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.v2ray.ang.service.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * The VPN of Amier Tether: finds the hotspot phone's SOCKS5 proxy, then runs hev-socks5-tunnel to
 * carry all traffic of this phone through it. Re-detects when Wi-Fi or the proxy changes.
 */
class TetherVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var hevRunning = false

    @Volatile
    private var wifiNetwork: Network? = null

    @Volatile
    private var wifiGateway: String? = null
    private val wifiChanged = Channel<Unit>(Channel.CONFLATED)
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        enterForeground(getString(R.string.status_searching))
        if (loop == null) {
            watchWifi()
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
    }

    override fun onDestroy() {
        stopTunnel()
        unwatchWifi()
        scope.cancel()
        TetherState.set(TetherStatus.Off)
        super.onDestroy()
    }

    private fun shutdown() {
        loop?.cancel()
        loop = null
        stopTunnel()
        unwatchWifi()
        TetherState.set(TetherStatus.Off)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- main loop ----------

    private suspend fun runLoop() {
        var hadProxy = false
        while (scope.isActive) {
            TetherState.set(TetherStatus.Searching(lost = hadProxy))
            updateNotification(getString(if (hadProxy) R.string.status_lost else R.string.status_searching))
            val target = findProxy()
            if (target == null) {
                // Keep a lost tunnel's VPN up: traffic waits instead of leaking past the proxy.
                waitForWifiChange(RETRY_MS)
                continue
            }
            if (!startTunnel(target)) {
                waitForWifiChange(RETRY_MS)
                continue
            }
            hadProxy = true
            val since = System.currentTimeMillis()
            updateNotification(getString(R.string.status_connected_to, "${target.host}:${target.port}"))
            watchConnection(target, since)
        }
    }

    /** Stays here while [target] keeps answering and Wi-Fi does not change. */
    private suspend fun watchConnection(target: ProxyTarget, since: Long) {
        var failures = 0
        var tick = 0
        while (scope.isActive) {
            val bytes = TProxyService.TProxyGetStats()?.let { it.getOrElse(1) { 0L } + it.getOrElse(3) { 0L } } ?: 0L
            TetherState.set(TetherStatus.Connected(target, since, bytes))
            if (wifiChanged.tryReceive().isSuccess && currentGateway() != target.host && target.source != ProxySource.MANUAL) {
                Log.i(TAG, "Wi-Fi changed, looking for the proxy again")
                return
            }
            if (++tick % HEALTH_EVERY_TICKS == 0) {
                failures = if (ProxyFinder.probe(target.host, target.port)) 0 else failures + 1
                if (failures >= 2) {
                    Log.w(TAG, "Proxy ${target.host}:${target.port} stopped answering")
                    stopHev()
                    return
                }
            }
            delay(1000)
        }
    }

    // ---------- detection ----------

    private suspend fun findProxy(): ProxyTarget? {
        val settings = TetherPrefs.load(this)
        val userPort = ProxyFinder.parsePort(settings.port)
        if (!settings.auto) {
            val host = settings.host.trim()
            if (host.isEmpty() || userPort == null) return null
            return ProxyTarget(host, userPort, ProxySource.MANUAL).takeIf { ProxyFinder.probe(it.host, it.port) }
        }
        discoverAmierVpn()?.let { if (ProxyFinder.probe(it.host, it.port)) return it }
        val gateway = currentGateway() ?: return null
        return ProxyFinder.candidatePorts(userPort).firstNotNullOfOrNull { port ->
            if (ProxyFinder.probe(gateway, port)) ProxyTarget(gateway, port, ProxySource.GATEWAY) else null
        }
    }

    private fun currentGateway(): String? = wifiGateway

    /** Amier VPN announces its proxy on the hotspot network while Proxy Tethering is on. */
    private suspend fun discoverAmierVpn(): ProxyTarget? {
        val nsd = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val found = withTimeoutOrNull(DISCOVERY_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                }
                try {
                    nsd.discoverServices(ProxyFinder.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
            }
        } ?: return null
        return resolve(nsd, found)
    }

    // Platform: NsdManager.resolveService is deprecated from API 34 in favour of
    // registerServiceInfoCallback, which does not exist below 34. Remove when minSdk >= 34.
    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): ProxyTarget? =
        withTimeoutOrNull(DISCOVERY_MS) {
            suspendCancellableCoroutine { cont ->
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = (serviceInfo.host as? Inet4Address)?.hostAddress
                        val target = host?.let { ProxyTarget(it, serviceInfo.port, ProxySource.AMIER_VPN) }
                        if (cont.isActive) cont.resume(target)
                    }
                })
            }
        }

    private fun watchWifi() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val gateway = linkProperties.routes
                    .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                    ?.gateway?.hostAddress
                if (gateway != wifiGateway || network != wifiNetwork) {
                    wifiNetwork = network
                    wifiGateway = gateway
                    wifiChanged.trySend(Unit)
                }
            }

            override fun onLost(network: Network) {
                if (network == wifiNetwork) {
                    wifiNetwork = null
                    wifiGateway = null
                    wifiChanged.trySend(Unit)
                }
            }
        }
        cm.registerNetworkCallback(request, cb)
        callback = cb
    }

    private fun unwatchWifi() {
        val cb = callback ?: return
        callback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    private suspend fun waitForWifiChange(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { wifiChanged.receive() }
    }

    // ---------- tunnel ----------

    private fun startTunnel(target: ProxyTarget): Boolean {
        stopHev()
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(ProxyFinder.TUN_MTU)
            .addAddress(ProxyFinder.TUN_IPV4, 30)
            .addRoute("0.0.0.0", 0)
            .addAddress(ProxyFinder.TUN_IPV6, 126)
            .addRoute("::", 0)
            .addDnsServer(ProxyFinder.MAPDNS_ADDRESS)
        // This app's own sockets (probes, and hev's link to the proxy) must not enter the VPN.
        runCatching { builder.addDisallowedApplication(packageName) }
        wifiNetwork?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        val newTun = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN establish failed", e)
            null
        } ?: return false
        val old = tun
        tun = newTun
        runCatching { old?.close() }
        val config = File(filesDir, "hev-socks5-tunnel.yaml").apply { writeText(ProxyFinder.hevConfig(target)) }
        hevRunning = TProxyService.TProxyStartService(config.absolutePath, newTun.fd)
        if (!hevRunning) Log.e(TAG, "hev-socks5-tunnel did not start")
        return hevRunning
    }

    private fun stopHev() {
        if (hevRunning) {
            runCatching { TProxyService.TProxyStopService() }
            hevRunning = false
        }
    }

    private fun stopTunnel() {
        stopHev()
        runCatching { tun?.close() }
        tun = null
    }

    // ---------- notification ----------

    private fun notification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TetherVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    private fun enterForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(text), type)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        const val ACTION_START = "com.amier.tether.START"
        const val ACTION_STOP = "com.amier.tether.STOP"
        private const val TAG = "AmierTether"
        private const val CHANNEL_ID = "tether"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_MS = 5_000L
        private const val DISCOVERY_MS = 3_000L
        private const val HEALTH_EVERY_TICKS = 10

        fun start(context: Context) {
            val intent = Intent(context, TetherVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TetherVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
