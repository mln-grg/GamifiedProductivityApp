package com.smallwins.app.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("smallwins", Context.MODE_PRIVATE)

    var setupSeen: Boolean
        get() = sp.getBoolean("setupSeen", false)
        set(value) = sp.edit().putBoolean("setupSeen", value).apply()

    /** Missed reminders before this moment have been acknowledged by the user. */
    var missedAckAt: Long
        get() = sp.getLong("missedAckAt", 0L)
        set(value) = sp.edit().putLong("missedAckAt", value).apply()

    /** Keys of the alarms currently registered with Android, so stale ones can be cancelled. */
    var alarmKeys: Set<String>
        get() = sp.getStringSet("alarmKeys", emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet("alarmKeys", value).apply()

    /** Returns the next index for a rotating list of lines, so the same one never repeats back to back. */
    fun nextIndex(kind: String, size: Int): Int {
        val next = (sp.getInt("copy_$kind", -1) + 1) % size
        sp.edit().putInt("copy_$kind", next).apply()
        return next
    }
}
