package com.smallwins.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smallwins.app.MainActivity
import com.smallwins.app.R
import com.smallwins.app.data.Prefs
import com.smallwins.app.data.QuestItem
import com.smallwins.app.data.TodaySnapshot

class Notifier(private val context: Context, private val prefs: Prefs) {
    private val manager = NotificationManagerCompat.from(context)

    fun enabled(): Boolean = manager.areNotificationsEnabled()

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Quest reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders at the times you set for each quest"
                enableVibration(true)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CH_RINGING, "Ringing reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The morning call, and quests set to ring like an alarm"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CH_NUDGES, "Progress and nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "XP confirmations and the evening nudge"
            }
        )
    }

    fun showReminder(item: QuestItem, rering: Boolean) {
        val quest = item.quest
        val title = if (quest.cue.isBlank()) quest.name else "${quest.cue} → ${quest.name}"
        val line = if (rering) pick("rering", Copy.rering) else pick("reminder", Copy.reminder)
        val soFar = if (quest.target > 1) "${item.done} of ${quest.target} ${quest.unit} so far. " else ""
        val builder = base(if (quest.ringing) CH_RINGING else CH_REMINDERS, title, soFar + line)
            .setCategory(if (quest.ringing) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, quest.id))
            .addAction(0, "Snooze 15", action(ActionReceiver.ACTION_SNOOZE, quest.id))
            .addAction(0, "Skip", action(ActionReceiver.ACTION_SKIP, quest.id))
        post(reminderId(quest.id), builder.build().insistentIf(quest.ringing))
    }

    /** The wake-up call asking for today's quests. */
    fun showMorning(canCarryOver: Boolean) {
        val builder = base(CH_RINGING, "Today's quests are waiting", pick("morning", Copy.morning))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Choose quests", openApp())
        if (canCarryOver) builder.addAction(0, "Same as yesterday", action(ActionReceiver.ACTION_SAME, "plan"))
        post(ID_MORNING, builder.build().insistentIf(true))
    }

    fun showProgress(title: String, text: String) =
        post(ID_PROGRESS, base(CH_NUDGES, title, text).setTimeoutAfter(8_000).setSilent(true).build())

    fun showSaver(snap: TodaySnapshot) {
        val open = snap.open
        val title = "${open.size} ${if (open.size == 1) "quest" else "quests"} still open"
        val names = open.joinToString(", ") { it.quest.name }
        post(ID_SAVER, base(CH_NUDGES, title, "$names. Clear them and today's XP is yours. ${pick("saver", Copy.saver)}").build())
    }

    fun showTest() =
        post(ID_TEST, base(CH_REMINDERS, "Test reminder", "It worked. Reminders can reach you on this phone.").build())

    fun cancelReminder(questId: String) = manager.cancel(reminderId(questId))

    fun cancelMorning() = manager.cancel(ID_MORNING)

    fun cancelAll() = manager.cancelAll()

    fun pick(kind: String, lines: List<String>): String = lines[prefs.nextIndex(kind, lines.size)]

    // Insistent repeats the sound until the notification is opened or answered.
    private fun Notification.insistentIf(ringing: Boolean) = apply {
        if (ringing) flags = flags or Notification.FLAG_INSISTENT
    }

    private fun base(channel: String, title: String, text: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_ember)
        .setColor(context.getColor(R.color.ember))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setContentIntent(openApp())

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun action(action: String, questId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("smallwins://action/$action/$questId"))
            .putExtra(ActionReceiver.EXTRA_QUEST_ID, questId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Suppress("MissingPermission") // enabled() is checked; without permission the reminder is simply not shown
    private fun post(id: Int, notification: Notification) {
        if (enabled()) manager.notify(id, notification)
    }

    private fun reminderId(questId: String) = questId.hashCode()

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_RINGING = "ringing"
        const val CH_NUDGES = "nudges"
        private const val ID_SAVER = 1
        private const val ID_PROGRESS = 2
        private const val ID_TEST = 3
        private const val ID_MORNING = 4
    }
}
