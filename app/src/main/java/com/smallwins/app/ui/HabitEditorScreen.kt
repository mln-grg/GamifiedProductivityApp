package com.smallwins.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smallwins.app.data.HabitEntity
import com.smallwins.app.domain.ScheduleType
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun templateHabit(template: Template, sortOrder: Int): HabitEntity {
    val base = HabitEntity(
        id = UUID.randomUUID().toString(), name = "", cue = "", unit = "times", schedule = ScheduleType.DAILY,
        target = 1, minimum = 1, weeklyDays = 4, reminderTimes = "", ringing = false,
        startDate = LocalDate.now().toString(), sortOrder = sortOrder,
    )
    return when (template) {
        Template.WATER -> base.copy(
            name = "Water", cue = "After each meal", unit = "glasses", target = 8, minimum = 4,
            reminderTimes = "09:00,11:00,13:30,16:00,18:30,21:00",
        )
        Template.MOVE -> base.copy(name = "Move", cue = "After work", unit = "session", schedule = ScheduleType.WEEKLY, reminderTimes = "18:00")
        Template.CUSTOM -> base
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    initial: HabitEntity,
    isNew: Boolean,
    onSave: (HabitEntity) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sw.colors
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var cue by rememberSaveable { mutableStateOf(initial.cue) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var weekly by rememberSaveable { mutableStateOf(initial.schedule == ScheduleType.WEEKLY) }
    var target by rememberSaveable { mutableStateOf(initial.target) }
    var minimum by rememberSaveable { mutableStateOf(initial.minimum) }
    var weeklyDays by rememberSaveable { mutableStateOf(initial.weeklyDays) }
    var times by rememberSaveable { mutableStateOf(initial.reminderTimes) }
    var ringing by rememberSaveable { mutableStateOf(initial.ringing) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val timeList = times.split(',').filter { it.isNotBlank() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (isNew) "New habit" else "Edit habit", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(name, { name = it }, label = { Text("Habit") }, placeholder = { Text("Drink water") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            cue, { cue = it }, label = { Text("When? Tie it to something you already do") },
            placeholder = { Text("After lunch") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Every day") })
            FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Some days a week") })
        }

        if (weekly) {
            Stepper("Days per week", weeklyDays, 1..7) { weeklyDays = it }
            Hint("Any days you like. It only becomes due once you can't put it off and still make the week.")
        } else {
            Stepper("Full target per day", target, 1..50) { target = it; if (minimum > it) minimum = it }
            Stepper("Minimum that keeps the streak", minimum, 1..target) { minimum = it }
            Hint("Hitting the minimum wins the day. Hitting the full target makes it a gold day.")
            OutlinedTextField(unit, { unit = it }, label = { Text("Counted in") }, placeholder = { Text("glasses") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                timeList.forEach { t ->
                    InputChip(
                        selected = false, onClick = { times = (timeList - t).joinToString(",") },
                        label = { Text(t) }, trailingIcon = { Text("×", color = colors.muted) },
                    )
                }
                FilterChip(selected = false, onClick = { pickingTime = true }, label = { Text("Add a time") })
            }
            if (timeList.isEmpty()) Hint("No reminders yet. Without one this habit relies on you remembering.")
            else Hint("Reminders stop for the day once the target is met.")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Ring like an alarm", style = MaterialTheme.typography.titleMedium)
                Hint("Uses your alarm volume and keeps sounding until you respond. Off: a normal notification.")
            }
            Switch(ringing, { ringing = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), cue = cue.trim(), unit = unit.trim().ifBlank { "times" },
                            schedule = if (weekly) ScheduleType.WEEKLY else ScheduleType.DAILY,
                            target = if (weekly) 1 else target, minimum = if (weekly) 1 else minimum,
                            weeklyDays = weeklyDays, reminderTimes = timeList.sorted().joinToString(","), ringing = ringing,
                        )
                    )
                },
            ) { Text("Save habit") }
            TextButton(onClick = onCancel) { Text("Cancel") }
            if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = colors.partner) }
        }
    }

    if (pickingTime) {
        TimeDialog(onPick = { t -> times = (timeList + t).distinct().joinToString(","); pickingTime = false }, onDismiss = { pickingTime = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("Its reminders stop and it leaves your Today screen. Days you already won stay won.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete habit") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val now = LocalTime.now()
    val state = rememberTimePickerState(initialHour = now.hour, initialMinute = 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("Add reminder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val colors = Sw.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleLarge, color = colors.ink)
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Sw.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (enabled) colors.ink else colors.line)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Sw.colors.muted)
}
