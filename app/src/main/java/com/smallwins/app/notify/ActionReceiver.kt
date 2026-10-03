package com.smallwins.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smallwins.app.async

/** Handles the Done / Snooze / Skip buttons on a reminder without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return@async
        app.notifier.cancelReminder(habitId)
        app.scheduler.cancelRering(habitId)
        when (intent.action) {
            ACTION_DONE -> {
                val outcome = app.repo.log(habitId, SOURCE)
                val item = outcome.after.items.firstOrNull { it.habit.id == habitId } ?: return@async
                val headline = when {
                    outcome.dayJustGold -> app.notifier.pick("gold", Copy.gold)
                    outcome.dayJustWon -> app.notifier.pick("dayWon", Copy.dayWon)
                    else -> null
                }
                app.notifier.showLogged(item, headline)
            }
            ACTION_SNOOZE -> app.repo.habit(habitId)?.let { app.scheduler.scheduleSnooze(habitId, it.ringing) }
            ACTION_SKIP -> Unit
        }
    }

    companion object {
        const val ACTION_DONE = "com.smallwins.app.DONE"
        const val ACTION_SNOOZE = "com.smallwins.app.SNOOZE"
        const val ACTION_SKIP = "com.smallwins.app.SKIP"
        const val EXTRA_HABIT_ID = "habitId"
        private const val SOURCE = "notification"
    }
}
