package com.smallwins.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smallwins.app.SmallWinsApp
import com.smallwins.app.async
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        val habitId = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_ID).orEmpty()
        when (intent.getStringExtra(AlarmScheduler.EXTRA_KIND)) {
            AlarmScheduler.KIND_HABIT -> {
                app.db.alarmEvents().markFired(
                    habitId,
                    intent.getIntExtra(AlarmScheduler.EXTRA_SLOT, 0),
                    intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L),
                    System.currentTimeMillis(),
                )
                app.scheduler.rescheduleAll()
                remind(app, habitId, rering = false, andReringAfter = true)
            }
            AlarmScheduler.KIND_SNOOZE -> remind(app, habitId, rering = false, andReringAfter = false)
            AlarmScheduler.KIND_RERING -> {
                val doneAtRing = intent.getIntExtra(AlarmScheduler.EXTRA_DONE_AT_RING, 0)
                remind(app, habitId, rering = true, andReringAfter = false, onlyIfDoneIs = doneAtRing)
            }
            AlarmScheduler.KIND_SAVER -> {
                app.scheduler.rescheduleAll()
                val snap = app.repo.snapshot(LocalDate.now())
                if (snap.hasHabits && !snap.eval.won) app.notifier.showSaver(snap)
            }
            AlarmScheduler.KIND_ROLLOVER -> {
                app.repo.rollover()
                app.scheduler.rescheduleAll()
            }
            AlarmScheduler.KIND_TEST -> app.notifier.showTest()
        }
    }

    private suspend fun remind(app: SmallWinsApp, habitId: String, rering: Boolean, andReringAfter: Boolean, onlyIfDoneIs: Int? = null) {
        val item = app.repo.snapshot(LocalDate.now()).items.firstOrNull { it.habit.id == habitId } ?: return
        if (item.finishedForToday) return
        if (onlyIfDoneIs != null && item.done != onlyIfDoneIs) return
        app.notifier.showReminder(item, rering)
        if (andReringAfter) app.scheduler.scheduleRering(habitId, item.done, item.habit.ringing)
    }
}

/** Android drops an app's alarms on reboot, app update and permission changes; put them back. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        app.repo.rollover()
        app.scheduler.rescheduleAll()
    }
}
