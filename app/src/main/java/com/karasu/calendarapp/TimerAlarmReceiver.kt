package com.karasu.calendarapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by AlarmManager when the countdown hits zero. All the work happens
 * in [AlarmSoundService] so the ringing survives process death and can be
 * stopped from its notification.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, AlarmSoundService::class.java))
    }
}
