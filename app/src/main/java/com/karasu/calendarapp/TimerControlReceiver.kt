package com.karasu.calendarapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Stop action on the live countdown notification: stops the
 * running timer and dismisses the notification.
 */
class TimerControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_COUNTDOWN) {
            TimerState.cancel(context)
        }
    }

    companion object {
        const val ACTION_STOP_COUNTDOWN = "com.karasu.calendarapp.STOP_COUNTDOWN"
    }
}
