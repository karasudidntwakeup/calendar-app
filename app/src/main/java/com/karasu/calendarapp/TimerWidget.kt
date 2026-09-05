package com.karasu.calendarapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class TimerWidget : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticking = false

    override fun onEnabled(context: Context) {
        refresh(context)
    }

    override fun onDisabled(context: Context) {
        scope.cancel()
        ticking = false
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_PAUSE -> {
                if (TimerState.running) TimerState.cancel(context) else TimerState.start(context)
            }
            ACTION_ADJUST -> adjust(context, intent.getLongExtra(EXTRA_DELTA_MS, 0L))
            ACTION_RESET -> {
                TimerState.cancel(context)
                TimerState.reset()
            }
            ACTION_REFRESH -> Unit
            else -> super.onReceive(context, intent).also { return }
        }
        refresh(context)
    }

    private fun adjust(context: Context, deltaMs: Long) {
        val minutes = ((TimerState.remainingMs + deltaMs).coerceIn(60_000L, 3599 * 60_000L)) / 60_000
        TimerState.remainingMs = minutes * 60_000L
        TimerState.totalMs = TimerState.remainingMs
        if (TimerState.running) TimerState.start(context) else TimerState.targetTime = 0L
    }

    // Picks the compact layout for narrow hosts (e.g. 2 cells) by reading the
    // size the launcher reports in options, so longer widgets get full controls.
    private fun layoutFor(context: Context, widgetId: Int): Int {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        return if (minWidth < 160) R.layout.widget_timer_small else R.layout.widget_timer
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
        if (ids.isEmpty()) return
        for (id in ids) manager.updateAppWidget(id, buildViews(context, id))
        if (TimerState.running) startTicker(context, ids)
    }

    // Drives the live countdown on the launcher while a timer runs. The
    // foreground service keeps this process alive, so the loop survives
    // backgrounding. Starts at most one ticker and refreshes at the end.
    private fun startTicker(context: Context, ids: IntArray) {
        if (ticking) return
        ticking = true
        val manager = AppWidgetManager.getInstance(context)
        scope.launch {
            while (isActive && TimerState.running) {
                val left = (TimerState.targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
                if (left <= 0L) break
                for (id in ids) {
                    val views = RemoteViews(context.packageName, layoutFor(context, id))
                    views.setTextViewText(R.id.time, format(left))
                    views.setTextViewText(R.id.start_pause, context.getString(R.string.pause))
                    manager.updateAppWidget(id, views)
                }
                delay(1000L)
            }
            ticking = false
            refresh(context)
        }
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val layout = layoutFor(context, widgetId)
        val views = RemoteViews(context.packageName, layout)
        val left = if (TimerState.running)
            (TimerState.targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
        else TimerState.remainingMs
        views.setTextViewText(R.id.time, format(left))
        views.setTextViewText(R.id.start_pause, context.getString(if (TimerState.running) R.string.pause else R.string.start))
        views.setOnClickPendingIntent(R.id.minus, pending(context, widgetId, ACTION_ADJUST, -5 * 60_000L))
        views.setOnClickPendingIntent(R.id.plus, pending(context, widgetId, ACTION_ADJUST, 5 * 60_000L))
        views.setOnClickPendingIntent(R.id.start_pause, pending(context, widgetId, ACTION_START_PAUSE, 0L))
        if (layout == R.layout.widget_timer) {
            views.setOnClickPendingIntent(R.id.reset, pending(context, widgetId, ACTION_RESET, 0L))
        }
        return views
    }

    private fun pending(context: Context, widgetId: Int, action: String, deltaMs: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode() + widgetId + deltaMs.toInt(),
            Intent(context, TimerWidget::class.java)
                .setAction(action)
                .putExtra(EXTRA_DELTA_MS, deltaMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun format(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    companion object {
        const val ACTION_START_PAUSE = "com.karasu.calendarapp.TIMER_WIDGET_START_PAUSE"
        const val ACTION_ADJUST = "com.karasu.calendarapp.TIMER_WIDGET_ADJUST"
        const val ACTION_RESET = "com.karasu.calendarapp.TIMER_WIDGET_RESET"
        const val ACTION_REFRESH = "com.karasu.calendarapp.TIMER_WIDGET_REFRESH"
        const val EXTRA_DELTA_MS = "delta_ms"

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(context, TimerWidget::class.java).setAction(ACTION_REFRESH))
        }
    }
}