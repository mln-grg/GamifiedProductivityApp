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
import com.smallwins.app.data.HabitItem
import com.smallwins.app.data.Prefs
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.domain.ScheduleType

class Notifier(private val context: Context, private val prefs: Prefs) {
    private val manager = NotificationManagerCompat.from(context)

    fun enabled(): Boolean = manager.areNotificationsEnabled()

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Habit reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders at the times you set for each habit"
                enableVibration(true)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CH_RINGING, "Ringing reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders that ring like an alarm until you respond"
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
            NotificationChannel(CH_NUDGES, "Streak nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The evening streak saver and confirmations"
            }
        )
    }

    fun showReminder(item: HabitItem, rering: Boolean) {
        val habit = item.habit
        val title = if (habit.cue.isBlank()) habit.name else "${habit.cue} → ${habit.name}"
        val line = if (rering) pick("rering", Copy.rering) else pick("reminder", Copy.reminder)
        val text = "${progressText(item, next = true)}. $line"
        val builder = base(if (habit.ringing) CH_RINGING else CH_REMINDERS, title, text)
            .setCategory(if (habit.ringing) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, habit.id))
            .addAction(0, "Snooze 15", action(ActionReceiver.ACTION_SNOOZE, habit.id))
            .addAction(0, "Skip", action(ActionReceiver.ACTION_SKIP, habit.id))
        val notification = builder.build()
        // Insistent repeats the sound until the reminder is opened or answered.
        if (habit.ringing) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(reminderId(habit.id), notification)
    }

    fun showLogged(item: HabitItem, headline: String?) {
        val title = headline ?: "Logged ${item.habit.name}"
        post(ID_LOGGED, base(CH_NUDGES, title, progressText(item, next = false)).setTimeoutAfter(6_000).setSilent(true).build())
    }

    fun showSaver(snap: TodaySnapshot) {
        val open = snap.items.filter { it.due && it.done < it.habit.minimum }
        val what = open.joinToString(", ") { "${it.habit.minimum - it.done} ${it.habit.name.lowercase()}" }
        val title = if (snap.streak > 0) "Day ${snap.streak + 1} hangs on this" else "Today is still winnable"
        post(ID_SAVER, base(CH_NUDGES, title, "Left to count: $what. ${pick("saver", Copy.saver)}").build())
    }

    fun showTest() =
        post(ID_TEST, base(CH_REMINDERS, "Test reminder", "It worked. Reminders can reach you on this phone.").build())

    fun cancelReminder(habitId: String) = manager.cancel(reminderId(habitId))

    fun pick(kind: String, lines: List<String>): String = lines[prefs.nextIndex(kind, lines.size)]

    private fun progressText(item: HabitItem, next: Boolean): String {
        val h = item.habit
        return when (h.schedule) {
            ScheduleType.WEEKLY -> "${item.weekDone} of ${item.weeklyTarget} days this week"
            ScheduleType.DAILY -> "${item.done} of ${h.target} ${h.unit}" + if (next) " so far" else ""
        }
    }

    private fun base(channel: String, title: String, text: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_ember)
        .setColor(context.getColor(R.color.ember))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        )

    private fun action(action: String, habitId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("smallwins://action/$action/$habitId"))
            .putExtra(ActionReceiver.EXTRA_HABIT_ID, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Suppress("MissingPermission") // enabled() is checked; without permission the reminder is simply not shown
    private fun post(id: Int, notification: Notification) {
        if (enabled()) manager.notify(id, notification)
    }

    private fun reminderId(habitId: String) = habitId.hashCode()

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_RINGING = "ringing"
        const val CH_NUDGES = "nudges"
        private const val ID_SAVER = 1
        private const val ID_LOGGED = 2
        private const val ID_TEST = 3
    }
}
