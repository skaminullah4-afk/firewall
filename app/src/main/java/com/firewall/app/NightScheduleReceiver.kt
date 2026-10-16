package com.firewall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NightScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                NightSchedule.reconcile(context)
            }

            ACTION_NIGHT_START -> {
                NightSchedule.startNightBlock(context)
                NightSchedule.scheduleNext(context)
            }

            ACTION_NIGHT_END -> {
                NightSchedule.stopNightBlock(context)
                NightSchedule.scheduleNext(context)
            }
        }
    }

    companion object {
        const val ACTION_NIGHT_START =
            "com.firewall.app.NIGHT_START"
        const val ACTION_NIGHT_END =
            "com.firewall.app.NIGHT_END"
    }
}
