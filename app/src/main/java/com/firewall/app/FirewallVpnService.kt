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
        when (intent?.action) {
            ACTION_STOP, ACTION_STOP_NIGHT -> {
                closeVpn()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, ACTION_START_NIGHT -> {
                closeVpn()
            }
        }

        if (vpnInterface != null) return START_NOT_STICKY

        val nightMode = intent?.action == ACTION_START_NIGHT

        val prefs = getSharedPreferences(
            "firewall_rules_v2",
            MODE_PRIVATE
        )

        val blockedApps = prefs.all
            .filter { it.value == "block" && it.key != packageName }
            .keys

        if (!nightMode && blockedApps.isEmpty()) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()
            startForeground(notificationId, buildNotification())

            val builder = Builder()
                .setSession(
                    if (nightMode) "Death Reminder — Night Block"
                    else "Death Reminder — App Firewall"
                )
                .addAddress("10.10.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1::1", 128)
                .addRoute("::", 0)
                .setMtu(1500)
                .setBlocking(true)

            // Night mode captures all apps.
            // Manual mode captures only apps marked "block".
            if (!nightMode) {
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
                name = "DeathReminderPacketBlocker"
                isDaemon = true
                start()
            }

            isRunning = true
            getSystemService(NotificationManager::class.java)
                .notify(notificationId, buildNotification())

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
                "Death Reminder firewall",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows firewall status"
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)

        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
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
            .setContentTitle("Death Reminder")
            .setContentText(
                if (isRunning) "Firewall VPN is active"
                else "Firewall VPN is stopped"
            )
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop VPN", stopPendingIntent)
            .build()
    }

    private fun discardPackets(descriptor: ParcelFileDescriptor) {
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val packet = ByteArray(32767)

                while (
                    !Thread.currentThread().isInterrupted &&
                    vpnInterface === descriptor
                ) {
                    if (input.read(packet) <= 0) break
                    // Intentionally discard captured traffic.
                }
            }
        } catch (_: IOException) {
            // The VPN may have been closed.
        } finally {
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

    override fun onBind(intent: Intent?) = super.onBind(intent)

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_STOP =
            "com.firewall.app.STOP_FIREWALL"
        const val ACTION_START =
            "com.firewall.app.START_FIREWALL"
        const val ACTION_START_NIGHT =
            "com.firewall.app.START_NIGHT"
        const val ACTION_STOP_NIGHT =
            "com.firewall.app.STOP_NIGHT"
    }
}
