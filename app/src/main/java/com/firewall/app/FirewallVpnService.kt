package com.firewall.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class FirewallVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (vpnInterface != null) {
            return START_STICKY
        }

        try {
            vpnInterface = Builder()
                .setSession("Firewall")
                .addAddress("10.10.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .establish()

            if (vpnInterface == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            // Traffic filtering will be added in the next steps.
            // Do not treat this tunnel as a working firewall yet.

            return START_STICKY

        } catch (e: Exception) {
            vpnInterface?.close()
            vpnInterface = null
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent) =
        super.onBind(intent)
}
