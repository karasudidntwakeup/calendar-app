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
import androidx.core.app.NotificationCompat
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
 *
 * The notification is the standard native template with a plain progress
 * bar plus Pause/Resume, +1 min and Stop actions.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticking = false

    /** True while paused via the notification: service stays foreground with
     * a persistent card so Resume stays one tap away. */
    private var paused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                paused = false
                startTicking()
            }
            ACTION_PAUSE -> pauseTicking()
            ACTION_SYNC -> syncNotification()
            ACTION_STOP -> {
                paused = false
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
        paused = false
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
                    TimerState.remainingMs = 25 * 60_000L
                    TimerState.totalMs = 25 * 60_000L
                    TimerState.targetTime = 0L
                    TimerState.clearAlarmTarget(this@TimerService)
                    ticking = false
                    paused = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                delay(250L)
            }
        }
    }

    /** Freeze the countdown but keep the service foreground so the paused
     * card (with Resume) stays visible. */
    private fun pauseTicking() {
        if (!ticking) return
        ticking = false
        paused = true
        TimerState.remainingMs =
            (TimerState.targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
        TimerState.running = false
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmPending())
        notifyManager.notify(NOTIFICATION_ID, buildNotification())
    }

    /** Re-render the current card (used after +5 min while paused). */
    private fun syncNotification() {
        if (ticking || paused) {
            notifyManager.notify(NOTIFICATION_ID, buildNotification())
        } else {
            stopSelf()
        }
    }

    private fun stopTicking() {
        ticking = false
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmPending())
        notifyManager.cancel(NOTIFICATION_ID)
    }

    private fun alarmPending(): PendingIntent =
        PendingIntent.getBroadcast(
            this, 1001, Intent(this, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Drop the legacy LOW-importance channel so the countdown is
            // always listed inline instead of minimized into the tray chip.
            notifyManager.deleteNotificationChannel(COUNTDOWN_CHANNEL_ID_LEGACY)
            notifyManager.createNotificationChannel(
                NotificationChannel(COUNTDOWN_CHANNEL_ID, "Timer running",
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Live countdown with wavy progress and timer controls"
                }
            )
        }
    }

    private fun controlPending(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this, requestCode,
            Intent(this, TimerControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val total = TimerState.totalMs.coerceAtLeast(1L)
        val remaining = TimerState.remainingMs.coerceAtLeast(0L)
        val showPaused = paused && !ticking
        val time = fmt(remaining)

        val openApp = PendingIntent.getActivity(
            this, 1004, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = controlPending(TimerControlReceiver.ACTION_STOP_COUNTDOWN, 1005)
        val pauseResume = controlPending(TimerControlReceiver.ACTION_PAUSE_RESUME, 2005)
        val add1 = controlPending(TimerControlReceiver.ACTION_ADD_1_MIN, 2006)

        // Classic native template: title + remaining time + straight
        // progress bar, draining as the countdown runs.
        return NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (showPaused) "Timer paused" else "Timer running")
            .setContentText(time)
            .setProgress(total.toInt(), remaining.toInt(), false)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(getColor(R.color.zigzag_accent))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(
                if (showPaused) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause,
                if (showPaused) "Resume" else "Pause",
                pauseResume
            )
            .addAction(android.R.drawable.ic_input_add, "+1 min", add1)
            .addAction(0, "Stop", stop)
            .build()
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
        private const val COUNTDOWN_CHANNEL_ID = "timer_progress_v2"
        private const val COUNTDOWN_CHANNEL_ID_LEGACY = "timer_progress"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.karasu.calendarapp.TIMER_START"
        const val ACTION_PAUSE = "com.karasu.calendarapp.TIMER_PAUSE"
        const val ACTION_SYNC = "com.karasu.calendarapp.TIMER_SYNC"
        const val ACTION_STOP = "com.karasu.calendarapp.TIMER_STOP"
    }
}
