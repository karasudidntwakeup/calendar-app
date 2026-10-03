package com.karasu.calendarapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by AlarmManager when the countdown hits zero. All the work happens
 * in [AlarmSoundService] so the ringing survives process death and can be
 * stopped from its notification.
 *
 * Stale alarms (scheduled before an app update, a cancel that raced the
 * delivery, or any target mismatch) are ignored via the persisted target.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val expected = context
            .getSharedPreferences(TimerState.ALARM_PREFS, Context.MODE_PRIVATE)
            .getLong(TimerState.KEY_ALARM_TARGET, -1L)
        val fired = intent.getLongExtra(EXTRA_TARGET_TIME, -2L)
        if (expected == -1L || fired != expected) return
        TimerState.clearAlarmTarget(context)
        context.startForegroundService(Intent(context, AlarmSoundService::class.java))
    }

    companion object {
        const val EXTRA_TARGET_TIME = "target_time"
    }
}
