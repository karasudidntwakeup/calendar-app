package com.karasu.calendarapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TodoEntry(
    val id: Long,
    val text: String,
    val done: Boolean
)

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
                            done = o.optBoolean("done", false)
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
                })
            }
            root.put(key, arr)
        }
        prefs(ctx).edit().putString("notes", root.toString()).apply()
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
