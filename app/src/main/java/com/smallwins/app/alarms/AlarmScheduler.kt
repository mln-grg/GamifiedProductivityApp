package com.smallwins.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.smallwins.app.MainActivity
import com.smallwins.app.data.AlarmEventEntity
import com.smallwins.app.data.AppDb
import com.smallwins.app.data.Prefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context, private val db: AppDb, private val prefs: Prefs) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * Cancels every repeating alarm the app owns and registers the next occurrence of each.
     * Every quest in the list gets its alarms; whether it is one of today's quests is
     * checked when the alarm fires, so a changed plan never leaves stale or missing alarms.
     */
    suspend fun rescheduleAll() = mutex.withLock {
        val now = System.currentTimeMillis()
        prefs.alarmKeys.forEach { alarms.cancel(pending(it, Intent(context, AlarmReceiver::class.java))) }
        db.alarmEvents().deletePending(now)
        db.alarmEvents().deleteOlderThan(now - KEEP_EVENTS_MS)

        val keys = mutableSetOf<String>()
        for (quest in db.quests().active()) {
            quest.times().forEachIndexed { slot, time ->
                val at = nextOccurrence(time, now)
                val key = "quest/${quest.id}/$slot"
                set(key, at, quest.ringing, intent(KIND_QUEST) {
                    putExtra(EXTRA_QUEST_ID, quest.id)
                    putExtra(EXTRA_SLOT, slot)
                    putExtra(EXTRA_SCHEDULED_AT, at)
                })
                db.alarmEvents().insert(AlarmEventEntity(questId = quest.id, slot = slot, scheduledAt = at))
                keys += key
            }
        }
        // The morning call is a real wake-up alarm, so it uses the alarm-clock slot.
        set(KEY_MORNING, nextOccurrence(LocalTime.parse(prefs.morningTime), now), true, intent(KIND_MORNING))
        set(KEY_SAVER, nextOccurrence(SAVER_TIME, now), false, intent(KIND_SAVER))
        set(KEY_ROLLOVER, nextOccurrence(ROLLOVER_TIME, now), false, intent(KIND_ROLLOVER))
        prefs.alarmKeys = keys + KEY_MORNING + KEY_SAVER + KEY_ROLLOVER
    }

    /** One more ring for a reminder that got no response. */
    fun scheduleRering(questId: String, doneAtRing: Int, ringing: Boolean) =
        oneShot(reringKey(questId), RERING_DELAY_MS, ringing, KIND_RERING, questId, doneAtRing)

    fun scheduleSnooze(questId: String, ringing: Boolean) =
        oneShot(reringKey(questId), SNOOZE_DELAY_MS, ringing, KIND_SNOOZE, questId, 0)

    fun cancelRering(questId: String) =
        alarms.cancel(pending(reringKey(questId), Intent(context, AlarmReceiver::class.java)))

    fun scheduleTest(delayMs: Long) =
        set("test", System.currentTimeMillis() + delayMs, false, intent(KIND_TEST))

    private fun oneShot(key: String, delayMs: Long, ringing: Boolean, kind: String, questId: String, doneAtRing: Int) =
        set(key, System.currentTimeMillis() + delayMs, ringing, intent(kind) {
            putExtra(EXTRA_QUEST_ID, questId)
            putExtra(EXTRA_DONE_AT_RING, doneAtRing)
        })

    private fun set(key: String, at: Long, ringing: Boolean, intent: Intent) {
        val pi = pending(key, intent)
        when {
            // Without the "Alarms & reminders" permission Android only allows inexact alarms.
            !canScheduleExact() -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            ringing -> {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            }
            else -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun intent(kind: String, extras: Intent.() -> Unit = {}) =
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_KIND, kind).apply(extras)

    // The data URI makes each alarm a distinct PendingIntent; extras alone do not.
    private fun pending(key: String, intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context, 0, intent.setData(Uri.parse("smallwins://alarm/$key")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reringKey(questId: String) = "rering/$questId"

    private fun nextOccurrence(time: LocalTime, now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDateTime.of(LocalDate.now(zone), time).atZone(zone).toInstant().toEpochMilli()
        return if (today > now) today
        else LocalDateTime.of(LocalDate.now(zone).plusDays(1), time).atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_QUEST_ID = "questId"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val EXTRA_DONE_AT_RING = "doneAtRing"

        const val KIND_QUEST = "quest"
        const val KIND_RERING = "rering"
        const val KIND_SNOOZE = "snooze"
        const val KIND_MORNING = "morning"
        const val KIND_SAVER = "saver"
        const val KIND_ROLLOVER = "rollover"
        const val KIND_TEST = "test"

        private const val KEY_MORNING = "morning"
        private const val KEY_SAVER = "saver"
        private const val KEY_ROLLOVER = "rollover"
        private val SAVER_TIME: LocalTime = LocalTime.of(21, 0)
        private val ROLLOVER_TIME: LocalTime = LocalTime.of(0, 5)
        private const val RERING_DELAY_MS = 15 * 60_000L
        private const val SNOOZE_DELAY_MS = 15 * 60_000L
        private const val KEEP_EVENTS_MS = 14 * 24 * 3_600_000L
    }
}
