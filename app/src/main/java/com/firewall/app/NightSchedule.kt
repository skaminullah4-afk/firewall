package com.firewall.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object NightSchedule {

    private const val PREFS = "night_schedule"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTIVE = "night_block_active"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun isNightActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled && isNightTime()) {
            startNightBlock(context)
        } else if (!enabled) {
            stopNightBlock(context)
        }

        scheduleNext(context)
    }

    private fun isNightTime(): Boolean {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        return hour > 22 ||
            (hour == 22 && minute >= 30) ||
            hour < 5
    }
    
    // Reconcile state after reboot or a clock/time-zone change.
    fun reconcile(context: Context) {
        if (!isEnabled(context)) {
            if (FirewallVpnService.isRunning) {
                stopNightBlock(context)
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ACTIVE, false)
                    .apply()
            }
            scheduleNext(context)
            return
        }

        if (isNightTime()) {
            startNightBlock(context)
        } else if (isNightActive(context)) {
            if (FirewallVpnService.isRunning) {
                stopNightBlock(context)
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ACTIVE, false)
                    .apply()
            }
        }

        scheduleNext(context)
    }
    
    fun scheduleNext(context: Context) {
        val manager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!isEnabled(context)) {
            cancelAlarm(context, manager, NightScheduleReceiver.ACTION_NIGHT_START)
            cancelAlarm(context, manager, NightScheduleReceiver.ACTION_NIGHT_END)
            return
        }

        val ending = isNightTime()
        val action = if (ending) {
            NightScheduleReceiver.ACTION_NIGHT_END
        } else {
            NightScheduleReceiver.ACTION_NIGHT_START
        }

        val next = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (ending) {
                set(Calendar.HOUR_OF_DAY, 5)
                set(Calendar.MINUTE, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 30)
            }

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmIntent = Intent(context, NightScheduleReceiver::class.java).apply {
            this.action = action
        }

        val requestCode =
            if (ending) REQUEST_END else REQUEST_START

        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.canScheduleExactAlarms()
            ) {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.timeInMillis,
                    pending
                )
            } else {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.timeInMillis,
                    pending
                )
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.timeInMillis,
                pending
            )
        }
    }

    private fun cancelAlarm(
        context: Context,
        manager: AlarmManager,
        action: String
    ) {
        val requestCode =
            if (action == NightScheduleReceiver.ACTION_NIGHT_START) {
                REQUEST_START
            } else {
                REQUEST_END
            }

        val intent = Intent(context, NightScheduleReceiver::class.java).apply {
            this.action = action
        }

        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.cancel(pending)
    }

    fun startNightBlock(context: Context) {
        if (!isEnabled(context)) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .apply()

        val intent = Intent(context, FirewallVpnService::class.java).apply {
            action = FirewallVpnService.ACTION_START_NIGHT
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, false)
                .apply()
        }
    }

    fun stopNightBlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()

        val intent = Intent(context, FirewallVpnService::class.java).apply {
            action = FirewallVpnService.ACTION_STOP_NIGHT
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
            // Android may prevent background service starts.
        }
    }

    private const val REQUEST_START = 301
    private const val REQUEST_END = 302
}
