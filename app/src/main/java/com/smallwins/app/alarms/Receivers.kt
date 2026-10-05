package com.smallwins.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smallwins.app.SmallWinsApp
import com.smallwins.app.async
import com.smallwins.app.domain.Rules

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        val questId = intent.getStringExtra(AlarmScheduler.EXTRA_QUEST_ID).orEmpty()
        when (intent.getStringExtra(AlarmScheduler.EXTRA_KIND)) {
            AlarmScheduler.KIND_QUEST -> {
                app.db.alarmEvents().markFired(
                    questId,
                    intent.getIntExtra(AlarmScheduler.EXTRA_SLOT, 0),
                    intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L),
                    System.currentTimeMillis(),
                )
                app.scheduler.rescheduleAll()
                // After a clock change or a long freeze, old alarms arrive in a burst; a reminder that late is noise.
                val lateBy = System.currentTimeMillis() - intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
                if (lateBy < STALE_AFTER_MS) remind(app, questId, rering = false, andReringAfter = true)
            }
            AlarmScheduler.KIND_SNOOZE -> remind(app, questId, rering = false, andReringAfter = false)
            AlarmScheduler.KIND_RERING -> {
                val doneAtRing = intent.getIntExtra(AlarmScheduler.EXTRA_DONE_AT_RING, 0)
                remind(app, questId, rering = true, andReringAfter = false, onlyIfDoneIs = doneAtRing)
            }
            AlarmScheduler.KIND_MORNING -> {
                // Yesterday's unanswered reminders no longer apply.
                app.notifier.cancelAll()
                app.repo.rollover()
                app.scheduler.rescheduleAll()
                val snap = app.repo.snapshot()
                if (!snap.planConfirmed) app.notifier.showMorning(canCarryOver = snap.items.size >= Rules.MIN_QUESTS)
            }
            AlarmScheduler.KIND_SAVER -> {
                app.scheduler.rescheduleAll()
                val snap = app.repo.snapshot()
                if (snap.open.isNotEmpty()) app.notifier.showSaver(snap)
            }
            AlarmScheduler.KIND_ROLLOVER -> {
                app.repo.rollover()
                app.scheduler.rescheduleAll()
            }
            AlarmScheduler.KIND_TEST -> app.notifier.showTest()
        }
    }

    /** Stays quiet for quests that are not picked today or are already cleared. */
    private suspend fun remind(app: SmallWinsApp, questId: String, rering: Boolean, andReringAfter: Boolean, onlyIfDoneIs: Int? = null) {
        val item = app.repo.snapshot().items.firstOrNull { it.quest.id == questId } ?: return
        if (item.cleared) return
        if (onlyIfDoneIs != null && item.done != onlyIfDoneIs) return
        app.notifier.showReminder(item, rering)
        if (andReringAfter) app.scheduler.scheduleRering(questId, item.done, item.quest.ringing)
    }

    private companion object {
        const val STALE_AFTER_MS = 30 * 60_000L
    }
}

/** Android drops an app's alarms on reboot, app update and permission changes; put them back. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        app.repo.rollover()
        app.scheduler.rescheduleAll()
    }
}
