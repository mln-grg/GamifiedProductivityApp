package com.smallwins.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallwins.app.data.HabitItem
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.domain.DayStatus
import com.smallwins.app.domain.Rules
import com.smallwins.app.domain.ScheduleType
import java.time.format.TextStyle
import java.util.Locale

enum class Template { WATER, MOVE, CUSTOM }

@Composable
fun TodayScreen(
    snap: TodaySnapshot,
    missedReminders: Int,
    onLog: (String) -> Unit,
    onUndo: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (Template) -> Unit,
    onSetup: () -> Unit,
    onDismissMissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!snap.hasHabits) EmptyState(onAdd) else TodayContent(snap, missedReminders, onLog, onUndo, onEdit, onAdd, onSetup, onDismissMissed)
    }
}

@Composable
private fun TodayContent(
    snap: TodaySnapshot,
    missedReminders: Int,
    onLog: (String) -> Unit,
    onUndo: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (Template) -> Unit,
    onSetup: () -> Unit,
    onDismissMissed: () -> Unit,
) {
    val colors = Sw.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(snap)
        WeekStrip(snap)
        if (missedReminders > 0) {
            Banner(
                text = "$missedReminders ${if (missedReminders == 1) "reminder" else "reminders"} did not ring on time in the last 3 days. Your phone is probably putting SmallWins to sleep.",
                action = "Fix phone settings", onAction = onSetup, dismiss = "Dismiss", onDismiss = onDismissMissed,
            )
        }
        snap.earnBack?.let { eb ->
            val left = Rules.EARN_BACK_GOLD_DAYS - eb.goldDays
            val day = eb.deadline.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Banner("Your ${eb.lostStreak}-day streak can still be won back: $left more gold ${if (left == 1) "day" else "days"} by $day.")
        }
        if (snap.comeback && snap.earnBack == null) {
            Banner(
                if (snap.comebackBonus > 0) "Comeback bonus earned: +${Rules.COMEBACK_BONUS} to this week's score."
                else "Welcome back. Your first log today earns a comeback bonus."
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(colors.sunk).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Ring(snap.eval.score / 100f, colors.you, Modifier.size(56.dp))
                Column {
                    Text("${snap.eval.score}%", style = MaterialTheme.typography.titleLarge)
                    Text("Today", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(colors.sunk).padding(12.dp)) {
                Text("${snap.weekScore}", style = MaterialTheme.typography.titleLarge)
                Text("This week's score", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text("${snap.lifetimeWins} ${if (snap.lifetimeWins == 1) "win" else "wins"} all time", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        snap.items.forEach { HabitCard(it, onLog, onUndo, onEdit) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onAdd(Template.CUSTOM) }) { Text("Add a habit") }
            TextButton(onClick = onSetup) { Text("Phone setup") }
        }
    }
}

@Composable
private fun Header(snap: TodaySnapshot) {
    val colors = Sw.colors
    val mood = when {
        snap.eval.gold -> Mood.BLAZE
        snap.eval.won -> Mood.OK
        else -> Mood.LOW
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ember(mood, Modifier.size(84.dp).semantics { contentDescription = "Ember, your flame" })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${snap.streak}", style = MaterialTheme.typography.displaySmall, fontSize = 34.sp)
                Spacer(Modifier.width(6.dp))
                Text("day streak", color = colors.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(
                when (mood) {
                    Mood.BLAZE -> "Gold day. I am enormous."
                    Mood.OK -> "Day won. Keep going for gold."
                    Mood.LOW -> "Feed me. A few taps and today counts."
                },
                style = MaterialTheme.typography.bodyMedium, color = colors.muted,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(Rules.REST_TOKENS_PER_WEEK) { i ->
                    val left = i < snap.restTokensLeft
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (left) colors.emberHi else colors.sunk).border(1.dp, if (left) colors.emberHi else colors.line, CircleShape))
                }
                Text(
                    "${snap.restTokensLeft} rest ${if (snap.restTokensLeft == 1) "token" else "tokens"} left this week",
                    style = MaterialTheme.typography.bodySmall, color = colors.muted,
                )
            }
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
                isToday && snap.eval.gold -> colors.gold
                isToday && snap.eval.won -> colors.you
                status == DayStatus.GOLD -> colors.gold
                status == DayStatus.WON -> colors.you
                status == DayStatus.RESTED -> colors.emberHi.copy(alpha = 0.45f)
                else -> Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall, color = if (isToday) colors.ink else colors.muted,
                )
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(fill)
                        .border(if (isToday) 2.dp else 1.dp, if (isToday) colors.ink else colors.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (status == DayStatus.MISSED) Text("×", color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HabitCard(item: HabitItem, onLog: (String) -> Unit, onUndo: (String) -> Unit, onEdit: (String) -> Unit) {
    val colors = Sw.colors
    val haptics = LocalHapticFeedback.current
    val h = item.habit
    val weekly = h.schedule == ScheduleType.WEEKLY
    val total = if (weekly) item.weeklyTarget.coerceAtLeast(1) else h.target
    val filled = if (weekly) item.weekDone else item.done
    val full = filled >= total
    val counts = if (weekly) item.finishedForToday else item.done >= h.minimum
    val accent = if (full) colors.gold else colors.you
    val state = when {
        weekly && full -> "Week complete"
        weekly && item.done > 0 -> "Done today"
        weekly && item.due -> "Due today to make the week"
        weekly -> "$filled of $total days this week"
        full -> "Gold"
        counts -> "Counts for today"
        else -> "${h.minimum - item.done} more to count"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp)).clickable { onEdit(h.id) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (weekly) state else "${item.done}/${h.target} ${h.unit} · $state",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = if (counts || full) accent else colors.muted,
                )
                if (h.cue.isNotBlank()) Text(h.cue, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            val canLog = if (weekly) item.done == 0 else item.done < h.target
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (canLog) colors.you else colors.sunk)
                    .clickable(enabled = canLog, onClickLabel = "Log ${h.name}") {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLog(h.id)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (canLog) "+1" else "✓", style = MaterialTheme.typography.titleLarge,
                    color = if (canLog) colors.surface else accent,
                )
            }
        }
        Segments(filled, total, if (weekly) total else h.minimum, accent)
        if (item.done > 0) {
            Text(
                "Undo last", style = MaterialTheme.typography.bodySmall, color = colors.muted,
                modifier = Modifier.clickable { onUndo(h.id) }.padding(vertical = 2.dp),
            )
        }
    }
}

/** One block per unit up to 12; beyond that a single bar. The notch marks the minimum that counts. */
@Composable
private fun Segments(filled: Int, total: Int, minimum: Int, accent: Color) {
    val colors = Sw.colors
    if (total > 12) {
        val fraction by animateFloatAsState((filled.toFloat() / total).coerceIn(0f, 1f), label = "bar")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.sunk)) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(accent))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= filled) accent else colors.sunk))
            if (i == minimum && minimum < total) Box(Modifier.width(2.dp).height(14.dp).background(colors.ink))
        }
    }
}

@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = Sw.colors.line
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    Canvas(modifier) {
        val stroke = size.minDimension * 0.125f
        val inset = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Banner(text: String, action: String? = null, onAction: () -> Unit = {}, dismiss: String? = null, onDismiss: () -> Unit = {}) {
    val colors = Sw.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.emberHi.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (action != null || dismiss != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                action?.let { TextButton(onClick = onAction) { Text(it) } }
                dismiss?.let { TextButton(onClick = onDismiss) { Text(it, color = colors.muted) } }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: (Template) -> Unit) {
    val colors = Sw.colors
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Ember(Mood.OK, Modifier.size(120.dp))
        Text("Pick your first small win", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "I'm Ember. Do the small thing every day and I grow. Start with one habit; you can add more later.",
            style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Choice("Drink water", "8 glasses a day, 4 to count, with reminders") { onAdd(Template.WATER) }
        Choice("Move", "A workout or walk, 4 days a week") { onAdd(Template.MOVE) }
        Choice("Something else", "Build your own habit") { onAdd(Template.CUSTOM) }
    }
}

@Composable
private fun Choice(title: String, detail: String, onClick: () -> Unit) {
    val colors = Sw.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}
