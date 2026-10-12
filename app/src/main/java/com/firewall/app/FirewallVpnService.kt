package com.firewall.app

import android.net.VpnService
import android.content.Intent
import android.os.IBinder

class FirewallVpnService : VpnService() {

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // Safe placeholder: do not establish a VPN tunnel
        // until packet filtering and forwarding are implemented.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
