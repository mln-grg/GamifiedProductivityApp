package com.smallwins.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smallwins.app.async
import com.smallwins.app.data.LogOutcome

/** Handles the buttons on a notification without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async(context) { app ->
        if (intent.action == ACTION_SAME) {
            app.notifier.cancelMorning()
            if (app.repo.acceptCarriedPlan()) app.notifier.showProgress("Quests accepted", "Same as yesterday. Reminders are set.")
            return@async
        }
        val questId = intent.getStringExtra(EXTRA_QUEST_ID) ?: return@async
        app.notifier.cancelReminder(questId)
        app.scheduler.cancelRering(questId)
        when (intent.action) {
            ACTION_DONE -> app.repo.log(questId, SOURCE)?.let { app.notifier.showProgress(headline(it), detail(it)) }
            ACTION_SNOOZE -> app.repo.quest(questId)?.let { app.scheduler.scheduleSnooze(questId, it.ringing) }
            ACTION_SKIP -> Unit
        }
    }

    companion object {
        const val ACTION_DONE = "com.smallwins.app.DONE"
        const val ACTION_SNOOZE = "com.smallwins.app.SNOOZE"
        const val ACTION_SKIP = "com.smallwins.app.SKIP"
        const val ACTION_SAME = "com.smallwins.app.SAME"
        const val EXTRA_QUEST_ID = "questId"
        private const val SOURCE = "notification"

        fun headline(o: LogOutcome): String = when {
            o.leveledUp -> "Level up. You are now level ${o.after.level.level}"
            o.fullClear -> "All quests cleared"
            o.quest.cleared -> "${o.quest.quest.name} cleared"
            else -> "${o.quest.quest.name}: ${o.quest.done} of ${o.quest.quest.target}"
        }

        fun detail(o: LogOutcome): String {
            val level = o.after.level
            return "+${o.xpGained} XP · ${level.xpInto} / ${level.xpNeeded} to level ${level.level + 1}"
        }
    }
}
