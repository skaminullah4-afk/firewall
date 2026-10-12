package com.firewall.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

class FirewallVpnService : VpnService() {

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var packetThread: Thread? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (vpnInterface != null) {
            return START_STICKY
        }

        val prefs = getSharedPreferences(
            "firewall_rules_v2",
            MODE_PRIVATE
        )

        val blockedApps = prefs.all
            .filter { entry ->
                entry.value == "block" &&
                    entry.key != packageName
            }
            .keys

        // Never create a VPN with no confirmed blocked apps.
        if (blockedApps.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val builder = Builder()
                .setSession("Firewall")
                .addAddress("10.10.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:1:fd00:1::1", 128)
                .addRoute("::", 0)
                .setMtu(1500)

            var addedApps = 0

            for (app in blockedApps) {
                try {
                    builder.addAllowedApplication(app)
                    addedApps++
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    // Ignore apps that are no longer installed.
                }
            }

            // Safety check: never establish a VPN if no
            // blocked application could be added.
            if (addedApps == 0) {
                stopSelf()
                return START_NOT_STICKY
            }

            val descriptor = builder.establish()

            if (descriptor == null) {
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

            return START_NOT_STICKY

        } catch (_: Exception) {
            closeVpn()
            stopSelf()
            return START_NOT_STICKY
        }
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

                    if (length <= 0) {
                        break
                    }

                    // Intentionally discard packets.
                    // This VPN does not forward traffic.
                }
            }
        } catch (_: IOException) {
            // Expected when the VPN interface is closed.
        } finally {
            if (vpnInterface === descriptor) {
                closeVpn()
                stopSelf()
            }
        }
    }

    private fun closeVpn() {
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

    override fun onDestroy() {
        closeVpn()
        super.onDestroy()
    }

    override fun onBind(intent: Intent) =
        super.onBind(intent)
}
