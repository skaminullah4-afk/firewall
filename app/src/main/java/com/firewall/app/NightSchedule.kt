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

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        scheduleNext(context)
    }

    fun isNightActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)

    fun scheduleNext(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!isEnabled(context)) {
            cancelAlarm(context, alarmManager, NightScheduleReceiver.ACTION_NIGHT_START)
            cancelAlarm(context, alarmManager, NightScheduleReceiver.ACTION_NIGHT_END)
            return
        }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val nightNow = hour > 22 || (hour == 22 && minute >= 30) || hour < 5

        val action = if (nightNow) {
            NightScheduleReceiver.ACTION_NIGHT_END
        } else {
            NightScheduleReceiver.ACTION_NIGHT_START
        }

        val next = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (action == NightScheduleReceiver.ACTION_NIGHT_END) {
                set(Calendar.HOUR_OF_DAY, 5)
                set(Calendar.MINUTE, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 30)
            }
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, NightScheduleReceiver::class.java).apply {
            this.action = action
        }
        val pending = PendingIntent.getBroadcast(
            context,
            if (action == NightScheduleReceiver.ACTION_NIGHT_START) 301 else 302,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.timeInMillis,
                    pending
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.timeInMillis,
                    pending
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
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
            if (action == NightScheduleReceiver.ACTION_NIGHT_START) 301 else 302
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
            .edit().putBoolean(KEY_ACTIVE, true).apply()

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
            // The app must report that automatic blocking could not start.
        }
    }

    fun stopNightBlock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE, false).apply()

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
            // The app must report if the VPN could not be stopped.
        }
    }
}
