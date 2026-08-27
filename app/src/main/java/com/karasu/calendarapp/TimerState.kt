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
            ctx, 1001, Intent(ctx, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pending)
        }

        // Run the live notification from a foreground service.
        ctx.startForegroundService(
            Intent(ctx, TimerService::class.java).setAction(TimerService.ACTION_START)
        )
    }

    fun cancel(ctx: Context) {
        running = false
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
}
