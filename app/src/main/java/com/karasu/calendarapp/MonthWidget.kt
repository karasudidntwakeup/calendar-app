package com.karasu.calendarapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Widget 3/5 — "Mini month": a compact month grid with today highlighted,
 * dots-days (task days) tinted, month navigation and tap-a-day deep link.
 * Each widget instance remembers its own shown month.
 */
class MonthWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        update(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        when (intent.action) {
            ACTION_PREV, ACTION_NEXT -> {
                if (id != -1) {
                    shiftShown(context, id, if (intent.action == ACTION_NEXT) 1 else -1)
                    update(context, intArrayOf(id))
                }
                return
            }
            ACTION_REFRESH -> {
                update(context, WidgetKit.widgetIds(context, MonthWidget::class.java))
                return
            }
            android.content.Intent.ACTION_WALLPAPER_CHANGED -> {
                WidgetKit.onWallpaperChanged(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_PREV = "com.karasu.calendarapp.WIDGET_MONTH_PREV"
        const val ACTION_NEXT = "com.karasu.calendarapp.WIDGET_MONTH_NEXT"
        const val ACTION_REFRESH = "com.karasu.calendarapp.WIDGET_MONTH_REFRESH"
        const val EXTRA_WIDGET_ID = "widget_id"
        private const val PREFS = "calendar_widgets"

        // Matches the generated cell_00..cell_41 ids in widget_month.xml.
        private val cellIds: IntArray = intArrayOf(
            R.id.cell_00, R.id.cell_01, R.id.cell_02, R.id.cell_03, R.id.cell_04,
            R.id.cell_05, R.id.cell_06, R.id.cell_07, R.id.cell_08, R.id.cell_09,
            R.id.cell_10, R.id.cell_11, R.id.cell_12, R.id.cell_13, R.id.cell_14,
            R.id.cell_15, R.id.cell_16, R.id.cell_17, R.id.cell_18, R.id.cell_19,
            R.id.cell_20, R.id.cell_21, R.id.cell_22, R.id.cell_23, R.id.cell_24,
            R.id.cell_25, R.id.cell_26, R.id.cell_27, R.id.cell_28, R.id.cell_29,
            R.id.cell_30, R.id.cell_31, R.id.cell_32, R.id.cell_33, R.id.cell_34,
            R.id.cell_35, R.id.cell_36, R.id.cell_37, R.id.cell_38, R.id.cell_39,
            R.id.cell_40, R.id.cell_41
        )

        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, MonthWidget::class.java).setAction(ACTION_REFRESH)
            )
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun shownYearMonth(context: Context, widgetId: Int): Pair<Int, Int> {
            val raw = prefs(context).getString("month_shown_$widgetId", null)
            if (raw != null) {
                val parts = raw.split("-")
                if (parts.size == 2) {
                    return parts[0].toIntOrNull()?.let { y ->
                        parts[1].toIntOrNull()?.let { m -> y to (m - 1) }
                    } ?: fallback()
                }
            }
            return fallback()
        }

        private fun fallback(): Pair<Int, Int> {
            val c = Calendar.getInstance()
            return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
        }

        private fun shiftShown(context: Context, widgetId: Int, amount: Int) {
            var (y, m) = shownYearMonth(context, widgetId)
            m += amount
            while (m < 0) { m += 12; y-- }
            while (m > 11) { m -= 12; y++ }
            prefs(context).edit()
                .putString("month_shown_$widgetId", "%04d-%02d".format(y, m + 1))
                .apply()
        }

        private fun navPending(context: Context, widgetId: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, action.hashCode() + widgetId,
                Intent(context, MonthWidget::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_WIDGET_ID, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun update(context: Context, ids: IntArray) {
            if (ids.isEmpty()) return
            TodoStore.rollOverUnfinished(context)
            val manager = AppWidgetManager.getInstance(context)
            val notes = TodoStore.loadAll(context)
            val today = Calendar.getInstance()
            val monthNames = context.resources.getStringArray(R.array.month_names)
            val accent = WidgetKit.accent(context)
            val ink = WidgetKit.onWallpaper(context)
            for (id in ids) {
                val (year, month) = shownYearMonth(context, id)
                val views = RemoteViews(context.packageName, R.layout.widget_month)
                // Fully transparent grid: every glyph is painted for whatever
                // the wallpaper behind it happens to be, over a near-invisible
                // scrim so the launcher never flashes a white panel.
                views.setInt(R.id.month_root, "setBackgroundColor", WidgetKit.scrim(context))
                WidgetKit.paintDim(
                    views, context, R.id.month_label,
                    R.id.month_wd_0, R.id.month_wd_1, R.id.month_wd_2, R.id.month_wd_3,
                    R.id.month_wd_4, R.id.month_wd_5, R.id.month_wd_6
                )
                WidgetKit.paintText(
                    views, context, R.id.month_title, R.id.month_prev, R.id.month_next
                )
                views.setTextViewText(
                    R.id.month_title, "${monthNames[month]} $year"
                )
                views.setOnClickPendingIntent(R.id.month_prev, navPending(context, id, ACTION_PREV))
                views.setOnClickPendingIntent(R.id.month_next, navPending(context, id, ACTION_NEXT))
                views.setOnClickPendingIntent(
                    R.id.month_title, WidgetKit.openDayIntent(context, null)
                )

                val firstDow = Calendar.getInstance().apply {
                    set(year, month, 1)
                }.get(Calendar.DAY_OF_WEEK) - 1
                val daysInMonth = Calendar.getInstance().apply {
                    set(year, month + 1, 0)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)

                for (i in 0 until 42) {
                    val cellId = cellIds[i]
                    val day = i - firstDow + 1
                    if (day < 1 || day > daysInMonth) {
                        views.setTextViewText(cellId, "")
                        views.setOnClickPendingIntent(cellId, null)
                        views.setInt(cellId, "setBackgroundResource", android.R.color.transparent)
                        continue
                    }
                    val key = TodoStore.keyFor(year, month + 1, day)
                    val isToday = today.get(Calendar.YEAR) == year &&
                        today.get(Calendar.MONTH) == month &&
                        today.get(Calendar.DAY_OF_MONTH) == day
                    val hasNotes = notes[key]?.isNotEmpty() == true
                    views.setTextViewText(cellId, day.toString())
                    views.setViewVisibility(cellId, View.VISIBLE)
                    if (isToday) {
                        views.setInt(cellId, "setBackgroundResource", R.drawable.widget_today_circle)
                        views.setTextColor(cellId, 0xFFFFFFFF.toInt())
                    } else {
                        views.setInt(cellId, "setBackgroundResource", android.R.color.transparent)
                        views.setTextColor(
                            cellId,
                            if (hasNotes) accent else ink
                        )
                    }
                    views.setOnClickPendingIntent(
                        cellId,
                        PendingIntent.getActivity(
                            context, id * 10000 + i,
                            Intent(context, MainActivity::class.java)
                                .putExtra(WidgetKit.EXTRA_DATE_KEY, key),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
