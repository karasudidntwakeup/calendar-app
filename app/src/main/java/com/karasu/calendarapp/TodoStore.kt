package com.karasu.calendarapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TodoEntry(
    val id: Long,
    val text: String,
    val done: Boolean,
    /** Partially finished: the circle sits halfway. Cycles empty -> half -> done. */
    val half: Boolean = false
) {
    /** One tap on the circle advances the task to the next step. */
    fun advance(): TodoEntry = when {
        done -> copy(done = false, half = false)
        half -> copy(done = true, half = false)
        else -> copy(half = true)
    }
}

/**
 * Date -> todo list, keyed by "yyyy-MM-dd". Mirrors the quickshell popup's
 * ~/.cache/quickshell/calendar-notes.json format ({"date": [{id,text,done}]}),
 * so notes can be copied between the desktop and the phone.
 */
object TodoStore {
    private const val PREFS = "calendar_notes"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(ctx: Context): MutableMap<String, MutableList<TodoEntry>> {
        val raw = prefs(ctx).getString("notes", null) ?: return mutableMapOf()
        val out = mutableMapOf<String, MutableList<TodoEntry>>()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = root.optJSONArray(key) ?: continue
                val list = mutableListOf<TodoEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
list.add(
                    TodoEntry(
                        id = o.optLong("id", i.toLong()),
                        text = o.optString("text"),
                        done = o.optBoolean("done", false),
                        half = o.optBoolean("half", false)
                    )
                )
                }
                out[key] = list
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveAll(ctx: Context, notes: Map<String, List<TodoEntry>>) {
        val root = JSONObject()
        for ((key, entries) in notes) {
            if (entries.isEmpty()) continue
            val arr = JSONArray()
            for (e in entries) {
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("text", e.text)
                    put("done", e.done)
                    put("half", e.half)
                })
            }
            root.put(key, arr)
        }
        prefs(ctx).edit().putString("notes", root.toString()).apply()
    }

    /**
     * Anything a past day held unfinished (half counts as unfinished) moves to
     * today, so a missed day is never lost. Runs at most once per calendar day;
     * the marker lives next to the notes so it survives process death.
     * Returns true when something was carried over.
     */
    fun rollOverUnfinished(ctx: Context): Boolean {
        val p = prefs(ctx)
        val today = todayKey()
        if (p.getString("rolled_on", null) == today) return false

        val notes = loadAll(ctx)
        var nextId = (notes.values.flatMap { it }.maxOfOrNull { it.id } ?: 0L) + 1
        val carried = mutableListOf<TodoEntry>()
        var trimmed = false
        for (key in notes.keys.sorted()) {
            // yyyy-MM-dd sorts chronologically; today and later stay put.
            if (key >= today) continue
            val list = notes[key].orEmpty()
            val stay = mutableListOf<TodoEntry>()
            for (e in list) {
                when {
                    // Blank drafts never survive a rollover.
                    e.text.isBlank() -> trimmed = true
                    e.done -> stay.add(e)
                    else -> carried.add(e)
                }
            }
            if (stay.isEmpty()) {
                notes.remove(key)
                trimmed = true
            } else {
                notes[key] = stay
            }
        }

        if (carried.isNotEmpty()) {
            val todayList = notes[today]?.toMutableList() ?: mutableListOf()
            val used = todayList.mapTo(mutableSetOf()) { it.id }
            for (e in carried) {
                // Re-key on collision: ids are what the lists key on.
                val id = if (e.id in used) nextId++ else e.id
                used.add(id)
                todayList.add(e.copy(id = id))
            }
            notes[today] = todayList
        }
        if (carried.isNotEmpty() || trimmed) saveAll(ctx, notes)
        p.edit().putString("rolled_on", today).apply()
        return carried.isNotEmpty()
    }

    /** Today as a "yyyy-MM-dd" key. */
    fun todayKey(): String {
        val c = Calendar.getInstance()
        return keyFor(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun keyFor(year: Int, month: Int, day: Int): String {
        // month is 1-based here; matches the desktop "YYYY-MM-DD" key format.
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(year - 1900, month - 1, day))
    }

    fun prettyDate(key: String): String {
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)!!
            SimpleDateFormat("EEE, MMM d", Locale.US).format(d)
        } catch (_: Exception) {
            key
        }
    }
}
