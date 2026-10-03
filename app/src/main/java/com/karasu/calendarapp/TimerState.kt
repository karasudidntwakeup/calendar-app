package com.karasu.calendarapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
/**
 * Process-wide timer state so both the Compose UI and the notification's
 * Stop action can control the same running countdown.
 */
object TimerState {
    var running by mutableStateOf(false)
    var remainingMs by mutableLongStateOf(25 * 60_000L)
    var totalMs by mutableLongStateOf(25 * 60_000L)
    var targetTime by mutableLongStateOf(0L)

    fun start(ctx: Context) {
        if (remainingMs <= 0) remainingMs = 25 * 60_000L
        targetTime = System.currentTimeMillis() + remainingMs
        totalMs = remainingMs
        running = true

        // Fire the ring through AlarmManager so it works even if the service
        // is killed mid-countdown.
        val pending = PendingIntent.getBroadcast(
            ctx, 1001,
            Intent(ctx, TimerAlarmReceiver::class.java)
                .putExtra(TimerAlarmReceiver.EXTRA_TARGET_TIME, targetTime),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Persisted so a stale alarm (e.g. from before an app update) can be
        // recognized and ignored by the receiver, even after process death.
        ctx.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ALARM_TARGET, targetTime)
            .apply()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pending)
        }

        // Run the live notification from a foreground service. From a
        // background context (e.g. cold start) the system may refuse the
        // FGS start — the exact alarm above still fires, and the in-app
        // tick keeps counting once the UI is visible.
        try {
            ctx.startForegroundService(
                Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_START)
            )
        } catch (e: Exception) {
            android.util.Log.w("TimerState", "FGS start denied, alarm-only mode", e)
        }
    }

    fun cancel(ctx: Context) {
        running = false
        clearAlarmTarget(ctx)
        val pending = PendingIntent.getBroadcast(
            ctx, 1001, Intent(ctx, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        ctx.startService(
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_STOP)
        )
    }

    fun reset() {
        running = false
        remainingMs = 25 * 60_000L
        totalMs = 25 * 60_000L
        targetTime = 0L
    }

    /** Clears the persisted alarm target (see [TimerAlarmReceiver]). */
    fun clearAlarmTarget(ctx: Context) {
        ctx.getSharedPreferences(ALARM_PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ALARM_TARGET)
            .apply()
    }

    internal const val ALARM_PREFS = "timer_alarm"
    internal const val KEY_ALARM_TARGET = "target"
}
