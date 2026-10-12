package com.firewall.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

class FirewallVpnService : VpnService() {

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var packetThread: Thread? = null

    private val channelId = "firewall_status"
    private val notificationId = 101

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            closeVpn()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (vpnInterface != null) {
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(
            "firewall_rules_v2",
            MODE_PRIVATE
        )

        val blockedApps = prefs.all
            .filter { entry ->
                entry.value == "block" && entry.key != packageName
            }
            .keys

        if (blockedApps.isEmpty()) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            startForeground(notificationId, buildNotification())
      
val builder = Builder()
    .setSession("Firewall")
    .addAddress("10.10.0.1", 32)
    .addRoute("0.0.0.0", 0)
    .addAddress("fd00:1:fd00:1::1", 128)
    .addRoute("::", 0)
    .setMtu(1500)
    .setBlocking(true)
    
            var addedApps = 0

            for (app in blockedApps) {
                try {
                    builder.addAllowedApplication(app)
                    addedApps++
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    // The app may have been uninstalled.
                }
            }

            if (addedApps == 0) {
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            val descriptor = builder.establish()

            if (descriptor == null) {
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            vpnInterface = descriptor

            packetThread = Thread {
                discardPackets(descriptor)
            }.apply {
                name = "FirewallPacketDropper"
                isDaemon = true
                start()
            }

            isRunning = true
            return START_NOT_STICKY

        } catch (_: Exception) {
            isRunning = false
            closeVpn()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Firewall status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Firewall is active"
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java)

        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
 
val startApp = Intent(this, MainActivity::class.java).apply {
    action = "com.firewall.app.START_FROM_NOTIFICATION"
}

val startPendingIntent = PendingIntent.getActivity(
    this,
    3,
    startApp,
    PendingIntent.FLAG_UPDATE_CURRENT or
        PendingIntent.FLAG_IMMUTABLE
)

        val stopIntent = Intent(this, FirewallVpnService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Firewall service")
            .setContentText("VPN service status is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun discardPackets(
        descriptor: ParcelFileDescriptor
    ) {
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val packet = ByteArray(32767)

                while (
                    !Thread.currentThread().isInterrupted &&
                    vpnInterface === descriptor
                ) {
                    val length = input.read(packet)
                    if (length <= 0) break

                    // Packets are discarded, not forwarded.
                }
            }
        } catch (_: IOException) {
            // Expected when the VPN interface closes.
        } 
        finally {
            if (vpnInterface === descriptor) {
                closeVpn()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun closeVpn() {
        isRunning = false

        val oldInterface = vpnInterface
        vpnInterface = null

        try {
            oldInterface?.close()
        } catch (_: IOException) {
            // Already closed.
        }

        packetThread?.interrupt()
        packetThread = null
    }

    override fun onRevoke() {
        closeVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent) = super.onBind(intent)

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_STOP = "com.firewall.app.STOP_FIREWALL"
    }
}
