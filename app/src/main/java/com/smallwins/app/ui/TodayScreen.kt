package com.smallwins.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallwins.app.data.QuestItem
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.domain.DayStatus
import com.smallwins.app.domain.Rules
import java.time.format.TextStyle
import java.util.Locale

/** The quest log for today: tick quests off, watch the XP bar move. */
@Composable
fun TodayScreen(
    snap: TodaySnapshot,
    missedReminders: Int,
    onLog: (String) -> Unit,
    onUndo: (String) -> Unit,
    onChangeQuests: () -> Unit,
    onSetup: () -> Unit,
    onDismissMissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sw.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerBar(snap)
        if (missedReminders > 0) {
            Notice("$missedReminders ${if (missedReminders == 1) "reminder" else "reminders"} did not ring on time in the last 3 days. Your phone is probably putting SmallWins to sleep.") {
                SystemLink("Fix phone settings", onSetup)
                SystemLink("Dismiss", onDismissMissed)
            }
        }
        snap.earnBack?.let { eb ->
            val left = Rules.EARN_BACK_GOLD_DAYS - eb.goldDays
            val day = eb.deadline.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Notice("Your ${eb.lostStreak}-day streak can still be won back: $left more full ${if (left == 1) "clear" else "clears"} by $day.")
        }
        if (snap.comeback && snap.earnBack == null && snap.items.all { it.done == 0 }) {
            Notice("Welcome back. Your first tick today pays +${Rules.COMEBACK_XP} bonus XP.")
        }

        SystemWindow("Daily quest") {
            snap.items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                QuestRow(item, snap.finishedStreak, onLog, onUndo)
            }
            val bonus = Rules.scaled(Rules.CLEAR_BONUS_XP, snap.finishedStreak)
            Text(
                when {
                    snap.eval.gold -> "All quests cleared. Bonus paid."
                    snap.eval.won -> "${snap.eval.cleared} of ${snap.eval.total} cleared. Streak is safe. Clear them all for +$bonus XP."
                    else -> "${snap.eval.cleared} of ${snap.eval.total} cleared. Clear ${(snap.eval.total + 1) / 2} to keep the streak, all for +$bonus XP."
                },
                style = MaterialTheme.typography.bodySmall, color = colors.muted,
            )
            SystemLink("Change today's quests", onChangeQuests)
        }

        WeekStrip(snap)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(Rules.REST_TOKENS_PER_WEEK) { i ->
                    val left = i < snap.restTokensLeft
                    Box(Modifier.size(11.dp).clip(CircleShape).background(if (left) colors.gold else Color.Transparent).border(1.dp, if (left) colors.gold else colors.line, CircleShape))
                }
                Text(
                    "${snap.restTokensLeft} rest ${if (snap.restTokensLeft == 1) "token" else "tokens"} this week",
                    style = MaterialTheme.typography.bodySmall, color = colors.muted,
                )
            }
            SystemLink("Phone setup", onSetup)
        }
    }
}

@Composable
private fun QuestRow(item: QuestItem, streak: Int, onLog: (String) -> Unit, onUndo: (String) -> Unit) {
    val colors = Sw.colors
    val haptics = LocalHapticFeedback.current
    val q = item.quest
    val accent = if (item.cleared) colors.ok else colors.glow
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(q.name, style = MaterialTheme.typography.titleMedium, color = if (item.cleared) colors.ok else colors.ink)
            val amount = if (q.target > 1) "[${item.done}/${q.target}] ${q.unit}" else q.unit
            val cue = if (q.cue.isBlank()) "" else " · ${q.cue}"
            Text("$amount$cue · +${Rules.scaled(q.xp, streak)} XP", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            if (item.done > 0) SystemLink("Undo last", { onUndo(q.id) })
        }
        Box(
            Modifier.width(60.dp).height(50.dp).background(accent.copy(alpha = 0.12f)).border(1.dp, accent)
                .clickable(enabled = !item.cleared, onClickLabel = "Tick ${q.name}") {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLog(q.id)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when { item.cleared -> "✓"; q.target > 1 -> "+1"; else -> "DONE" },
                style = MaterialTheme.typography.labelLarge, color = accent, fontSize = if (item.cleared || q.target > 1) 20.sp else 15.sp,
            )
        }
    }
}

@Composable
private fun WeekStrip(snap: TodaySnapshot) {
    val colors = Sw.colors
    val start = Rules.weekStart(snap.date)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0L..6L) {
            val day = start.plusDays(i)
            val status = snap.records.firstOrNull { it.date == day }?.status
            val isToday = day == snap.date
            val fill = when {
                isToday && snap.eval.gold -> colors.ok
                isToday && snap.eval.won -> colors.glow
                status == DayStatus.GOLD -> colors.ok
                status == DayStatus.WON -> colors.glow
                status == DayStatus.RESTED -> colors.gold.copy(alpha = 0.4f)
                else -> Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall, color = if (isToday) colors.ink else colors.muted,
                )
                Box(
                    Modifier.size(24.dp).background(fill).border(if (isToday) 2.dp else 1.dp, if (isToday) colors.ink else colors.line),
                    contentAlignment = Alignment.Center,
                ) {
                    if (status == DayStatus.MISSED) Text("×", color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, actions: @Composable () -> Unit = {}) {
    val colors = Sw.colors
    Column(Modifier.fillMaxWidth().border(1.dp, colors.gold.copy(alpha = 0.6f)).background(colors.gold.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { actions() }
    }
}
