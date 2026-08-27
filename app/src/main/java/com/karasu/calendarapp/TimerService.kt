package com.karasu.calendarapp

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the live countdown notification. It keeps
 * updating every 250 ms even when the app is backgrounded or its UI is
 * destroyed, and it schedules+fires the alarm via AlarmManager.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTicking()
            ACTION_STOP -> {
                stopTicking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        ensureChannel()
        val notif = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            // If the FGS type is rejected, degrade to a plain notification so
            // the timer still shows and the alarm still fires.
            Log.w(TAG, "FGS start failed", e)
        }

        scope.launch {
            while (isActive && ticking) {
                val left = (TimerState.targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
                TimerState.remainingMs = left
                if (TimerState.remainingMs > 0) {
                    notifyManager.notify(NOTIFICATION_ID, buildNotification())
                } else {
                    TimerState.running = false
                    ticking = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                delay(250L)
            }
        }
    }

    private fun stopTicking() {
        ticking = false
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(
            PendingIntent.getBroadcast(
                this, 1001, Intent(this, TimerAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        notifyManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifyManager.createNotificationChannel(
                NotificationChannel(COUNTDOWN_CHANNEL_ID, "Timer running",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val total = TimerState.totalMs.coerceAtLeast(1L)
        val remaining = TimerState.remainingMs.coerceAtLeast(0L)
        val text = fmt(remaining)
        val openApp = PendingIntent.getActivity(
            this, 1004, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            this, 1005,
            Intent(this, TimerControlReceiver::class.java)
                .setAction(TimerControlReceiver.ACTION_STOP_COUNTDOWN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 16+: expressive wavy/zigzag progress bar built with the
        // framework builder. Pre-Baklava falls back to the standard linear
        // progress bar via NotificationCompat.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val accent = getColor(R.color.zigzag_accent)
            val gray = getColor(android.R.color.darker_gray)
            val style = Notification.ProgressStyle()
                .setProgress(total.toInt())
                .setProgressSegments(
                    listOf(
                        Notification.ProgressStyle.Segment(remaining.toInt()).setColor(accent),
                        Notification.ProgressStyle.Segment((total - remaining).toInt()).setColor(gray)
                    )
                )
            Notification.Builder(this, COUNTDOWN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Timer running")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .addAction(0, "Stop", stop)
                .setStyle(style)
                .build()
        } else {
            androidx.core.app.NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Timer running")
                .setContentText(text)
                .setProgress(total.toInt(), remaining.toInt(), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp)
                .addAction(0, "Stop", stop)
                .build()
        }
    }

    private fun fmt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val notifyManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "TimerService"
        private const val COUNTDOWN_CHANNEL_ID = "timer_progress"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.karasu.calendarapp.TIMER_START"
        const val ACTION_STOP = "com.karasu.calendarapp.TIMER_STOP"
    }
}
