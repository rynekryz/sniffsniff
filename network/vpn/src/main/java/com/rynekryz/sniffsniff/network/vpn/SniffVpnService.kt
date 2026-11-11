package com.rynekryz.sniffsniff.network.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.rynekryz.sniffsniff.core.common.settings.AppFilterMode
import com.rynekryz.sniffsniff.core.common.settings.AppSettings
import com.rynekryz.sniffsniff.core.common.settings.SettingsRepository
import com.rynekryz.sniffsniff.network.capture.CaptureEngine
import com.rynekryz.sniffsniff.network.forwarding.SocketProtector
import com.rynekryz.sniffsniff.network.forwarding.TrafficForwarder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import javax.inject.Inject
import kotlin.concurrent.thread

@AndroidEntryPoint
class SniffVpnService : VpnService() {

    @Inject lateinit var engine: CaptureEngine
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var starting: Job? = null
    private var tunnel: Tunnel? = null

    private class Tunnel(
        val descriptor: ParcelFileDescriptor,
        val forwarder: TrafficForwarder,
        val reader: Thread,
    ) {
        fun close() {
            reader.interrupt()
            forwarder.close()
            try {
                descriptor.close()
            } catch (_: IOException) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopCapture() else startCapture()
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        stopCapture()
    }

    override fun onDestroy() {
        starting?.cancel()
        tunnel?.close()
        tunnel = null
        engine.endSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture() {
        if (tunnel != null || starting?.isActive == true) return
        showForeground()
        engine.onStarting()
        starting = scope.launch {
            try {
                establish(settingsRepository.settings.first())
            } catch (error: Exception) {
                engine.onFailed(error.message ?: "Unable to start capture")
                mainHandler.post { shutdown() }
            }
        }
    }

    private fun establish(settings: AppSettings) {
        val dns = selectDns(settings)
        val builder = Builder()
            .setSession("SniffSniff")
            .setMtu(MTU)
            .setBlocking(true)
            .setMetered(false)
            .addAddress(TUN_ADDRESS_V4, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(dns)
        if (settings.ipv6) builder.addAddress(TUN_ADDRESS_V6, 128).addRoute("::", 0)
        applyAppFilter(builder, settings)
        val descriptor = builder.establish() ?: error("VPN permission was not granted")
        val output = FileOutputStream(descriptor.fileDescriptor)
        val input = FileInputStream(descriptor.fileDescriptor)
        val forwarder = TrafficForwarder(
            protector = object : SocketProtector {
                override fun protect(socket: Socket): Boolean = this@SniffVpnService.protect(socket)
                override fun protect(socket: DatagramSocket): Boolean = this@SniffVpnService.protect(socket)
            },
            writer = { packet -> output.write(packet) },
            observer = engine,
            mtu = MTU,
        )
        engine.beginSession(settings)
        forwarder.start()
        val reader = thread(name = "sniffsniff-tun", isDaemon = true) {
            val buffer = ByteArray(32767)
            while (!Thread.currentThread().isInterrupted) {
                val read = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    break
                }
                if (read < 0) break
                if (read > 0) forwarder.onPacket(buffer, read)
            }
            mainHandler.post { if (tunnel != null) stopCapture() }
        }
        tunnel = Tunnel(descriptor, forwarder, reader)
    }

    private fun selectDns(settings: AppSettings): InetAddress {
        val custom = settings.customDns.trim()
        if (custom.isNotEmpty()) {
            return runCatching { InetAddresses.parseNumericAddress(custom) }
                .getOrElse { error("Custom DNS server is not a valid IP address") }
        }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val servers = network?.let { connectivity.getLinkProperties(it)?.dnsServers }.orEmpty()
        return servers.firstOrNull { settings.ipv6 || it is Inet4Address }
            ?: error("No DNS server found. Set one in Settings, Capture.")
    }

    private fun applyAppFilter(builder: Builder, settings: AppSettings) {
        val selected = settings.filteredPackages
        when (settings.appFilterMode) {
            AppFilterMode.ALL -> disallow(builder, packageName)
            AppFilterMode.EXCLUDE_SELECTED -> {
                disallow(builder, packageName)
                selected.forEach { disallow(builder, it) }
            }
            AppFilterMode.ONLY_SELECTED -> {
                var added = 0
                for (name in selected) {
                    try {
                        builder.addAllowedApplication(name)
                        added++
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                if (added == 0) error("No applications selected. Choose apps in Settings, Capture.")
            }
        }
    }

    private fun disallow(builder: Builder, name: String) {
        try {
            builder.addDisallowedApplication(name)
        } catch (_: PackageManager.NameNotFoundException) {
        }
    }

    private fun stopCapture() {
        starting?.cancel()
        val current = tunnel
        tunnel = null
        current?.close()
        engine.endSession()
        shutdown()
    }

    private fun shutdown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Capture status", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SniffVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sniff)
            .setContentTitle("SniffSniff is capturing")
            .setContentText("Traffic is analysed locally on this device")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_sniff), "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.rynekryz.sniffsniff.action.START"
        const val ACTION_STOP = "com.rynekryz.sniffsniff.action.STOP"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
        private const val TUN_ADDRESS_V4 = "10.215.173.1"
        private const val TUN_ADDRESS_V6 = "fd00:215:173::1"
    }
}
