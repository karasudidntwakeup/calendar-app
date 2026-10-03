package com.karasu.calendarapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the countdown notification's action buttons: pause/resume,
 * add 5 minutes, and stop. All three operate on the same [TimerState] the
 * Compose UI uses, so the app screen and the notification stay in sync.
 *
 * Pause is routed through [TimerService] so the service can freeze the
 * countdown while keeping a persistent "paused" card on screen.
 */
class TimerControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_COUNTDOWN -> TimerState.cancel(context)
            ACTION_PAUSE_RESUME ->
                if (TimerState.running) {
                    context.startService(
                        Intent(context, TimerService::class.java)
                            .setAction(TimerService.ACTION_PAUSE)
                    )
                } else {
                    TimerState.start(context)
                }
            ACTION_ADD_1_MIN -> {
                // Same semantics as the in-app +/- buttons: grow what is
                // left, then reschedule the alarm if currently running.
                TimerState.remainingMs =
                    (TimerState.remainingMs + ONE_MIN_MS).coerceAtMost(12 * 3_600_000L)
                TimerState.totalMs =
                    TimerState.totalMs.coerceAtLeast(TimerState.remainingMs)
                if (TimerState.running) {
                    TimerState.start(context)
                } else {
                    // Refresh a paused card if the service is holding one.
                    context.startService(
                        Intent(context, TimerService::class.java)
                            .setAction(TimerService.ACTION_SYNC)
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP_COUNTDOWN = "com.karasu.calendarapp.STOP_COUNTDOWN"
        const val ACTION_PAUSE_RESUME = "com.karasu.calendarapp.PAUSE_RESUME_COUNTDOWN"
        const val ACTION_ADD_1_MIN = "com.karasu.calendarapp.ADD_1_MIN"
        private const val ONE_MIN_MS = 60_000L
    }
}
