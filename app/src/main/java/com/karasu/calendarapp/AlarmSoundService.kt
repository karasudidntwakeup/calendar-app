package com.karasu.calendarapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Keeps beeping + vibrating until the user taps the notification (or its
 * Stop action). Runs as a foreground service so the process isn't killed
 * mid-ring and the notification doubles as the "tap to stop" surface.
 */
class AlarmSoundService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var beeper: ToneGenerator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Drop the stale "Timer running / 00:00" countdown notification so the
        // user only sees the alarm-ring notification.
        nm.cancel(2001)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sound = android.net.Uri.parse(
                "android.resource://$packageName/${R.raw.alarm_elapsed}"
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
        }

        // Any tap on the notification (or the Stop action) stops the ringing.
        val stopPending = PendingIntent.getService(
            this, REQUEST_STOP,
            Intent(this, AlarmSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Timer")
                .setContentText("Time is up! Tap to stop")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(stopPending, true)
                .setContentIntent(stopPending)
                .addAction(0, "Stop", stopPending)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK can be rejected on some
            // ROMs when the app isn't visibly active. Fall back to a plain
            // foreground start instead of crashing.
            try {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } catch (e: Exception) {
                Log.w(TAG, "mediaPlayback FGS rejected, starting plain FGS", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        playAlarm()
        startVibrating()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun playAlarm() {
        try {
            player = MediaPlayer.create(
                this,
                R.raw.alarm_elapsed,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0
            )?.apply {
                isLooping = true
                start()
            }
        } catch (_: Exception) {
            player = null
        }

        // If the raw OGG/Vorbis clip can't be decoded by this device's
        // MediaPlayer (some ROMs don't ship a Vorbis decoder), fall back to a
        // loud repeating beep so the alarm is never silent.
        if (player == null) {
            try {
                beeper = ToneGenerator(AudioManager.STREAM_ALARM, 100).apply {
                    startTone(ToneGenerator.TONE_PROP_BEEP, -1)
                }
            } catch (_: Exception) {
                beeper = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibrating() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300), 0))
    }

    private fun stopRinging() {
        try {
            player?.let { if (it.isPlaying) it.stop() }
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            beeper?.stopTone()
            beeper?.release()
        } catch (_: Exception) {
        }
        beeper = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            beeper?.stopTone()
            beeper?.release()
        } catch (_: Exception) {
        }
        beeper = null
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmSoundService"
        const val CHANNEL_ID = "timer_alarm_v2"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_STOP = 1003
        const val ACTION_STOP = "com.karasu.calendarapp.STOP_ALARM"
    }
}
