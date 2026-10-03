package com.karasu.calendarapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews

/**
 * Shared helpers for the mini-month widget: the deep-link into the app and a
 * palette that stays readable on any wallpaper.
 */
object WidgetKit {

    /** Intent extra: open date's task list ("yyyy-MM-dd" key). */
    const val EXTRA_DATE_KEY = "open_date"

    /** Opens the app with [dateKey] selected; null means the calendar home. */
    fun openDayIntent(context: Context, dateKey: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (dateKey != null) putExtra(EXTRA_DATE_KEY, dateKey)
        }
        return PendingIntent.getActivity(
            context,
            (dateKey ?: "home").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Widget backgrounds are fully transparent, so the wallpaper is what the
     * text sits on. Sample it and report whether it is bright, then pick a
     * palette that stays readable on either. Cached until the wallpaper moves.
     */
    private var wallpaperIsLight: Boolean? = null

    fun forgetWallpaper() {
        wallpaperIsLight = null
    }

    // Reading the live wallpaper needs a permission no normal app can hold, so
    // this is best-effort: a refusal falls back to the system night mode.
    @SuppressLint("MissingPermission")
    fun wallpaperIsLight(context: Context): Boolean {
        wallpaperIsLight?.let { return it }
        var light = !isNight(context)
        try {
            val drawable = WallpaperManager.getInstance(context).fastDrawable ?: return light
            val size = 16
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val pixels = IntArray(size * size)
            bmp.getPixels(pixels, 0, size, 0, 0, size, size)
            var total = 0f
            for (p in pixels) {
                val r = (p shr 16 and 0xFF) / 255f
                val g = (p shr 8 and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                total += 0.2126f * r + 0.7152f * g + 0.0722f * b
            }
            bmp.recycle()
            light = total / pixels.size > 0.45f
        } catch (_: Exception) {
        }
        wallpaperIsLight = light
        return light
    }

    /** Primary text/foreground: dark ink on a bright wallpaper, else light. */
    fun onWallpaper(context: Context): Int =
        if (wallpaperIsLight(context)) 0xFF16141A.toInt() else 0xFFF4F1F7.toInt()

    /** Secondary text that still reads but stays out of the way. */
    fun dimText(context: Context): Int =
        if (wallpaperIsLight(context)) 0xFF5C5865.toInt() else 0xFFBDB7C4.toInt()

    /**
     * A whisper-thin wash behind the widget. Looks like pure transparency, but
     * it gives the launcher a real surface: animating a widget with no
     * background at all made it flash a white panel mid-zoom.
     */
    fun scrim(context: Context): Int =
        if (wallpaperIsLight(context)) 0x0A000000 else 0x14000000

    /** Accent readable on both a bright and a dark wallpaper. */
    fun accent(context: Context): Int =
        if (wallpaperIsLight(context)) 0xFF5B3FBF.toInt() else 0xFFCDBCFF.toInt()

    /** Repaints text in the primary foreground colour for the wallpaper. */
    fun paintText(views: RemoteViews, context: Context, vararg ids: Int) {
        val color = onWallpaper(context)
        for (id in ids) views.setTextColor(id, color)
    }

    /** Repaints secondary/caption text for the wallpaper. */
    fun paintDim(views: RemoteViews, context: Context, vararg ids: Int) {
        val color = dimText(context)
        for (id in ids) views.setTextColor(id, color)
    }

    fun widgetIds(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, cls))

    /** Repaint the widget, e.g. after a task changed. */
    fun refreshWidgets(context: Context) {
        MonthWidget.refresh(context)
    }

    /** The wallpaper moved: drop the cached brightness and repaint. */
    fun onWallpaperChanged(context: Context) {
        forgetWallpaper()
        refreshWidgets(context)
    }
}
