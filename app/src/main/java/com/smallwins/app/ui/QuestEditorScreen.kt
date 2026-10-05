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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.smallwins.app.data.QuestEntity
import java.time.LocalTime
import java.util.UUID

fun blankQuest(sortOrder: Int) = QuestEntity(
    id = UUID.randomUUID().toString(), name = "", cue = "", unit = "", target = 1, xp = 40,
    reminderTimes = "", ringing = false, sortOrder = sortOrder,
)

private val Efforts = listOf("Light" to 20, "Normal" to 40, "Hard" to 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestEditorScreen(
    initial: QuestEntity,
    isNew: Boolean,
    onSave: (QuestEntity) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sw.colors
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var cue by rememberSaveable { mutableStateOf(initial.cue) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var target by rememberSaveable { mutableStateOf(initial.target) }
    var xp by rememberSaveable { mutableStateOf(initial.xp) }
    var times by rememberSaveable { mutableStateOf(initial.reminderTimes) }
    var ringing by rememberSaveable { mutableStateOf(initial.ringing) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val timeList = times.split(',').filter { it.isNotBlank() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SystemWindow(if (isNew) "New quest" else "Edit quest") {
            OutlinedTextField(name, { name = it }, label = { Text("Quest") }, placeholder = { Text("Stretch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                cue, { cue = it }, label = { Text("When? Tie it to something you already do") },
                placeholder = { Text("After coffee") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Times a day", target, 1..30) { target = it }
            OutlinedTextField(
                unit, { unit = it }, label = { Text(if (target > 1) "Counted in" else "What counts as done") },
                placeholder = { Text(if (target > 1) "glasses" else "10 minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("How hard is it?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Efforts.forEach { (label, value) ->
                    FilterChip(selected = xp == value, onClick = { xp = value }, label = { Text("$label · $value XP") })
                }
            }

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
            Hint(
                if (timeList.isEmpty()) "No reminders yet. Without one this quest relies on you remembering."
                else "They ring only on days you pick this quest, and stop once it is cleared."
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Ring like an alarm", style = MaterialTheme.typography.titleMedium)
                    Hint("Uses your alarm volume and keeps sounding until you respond. Off: a normal notification.")
                }
                Switch(ringing, { ringing = it })
            }

            SystemButton(
                "Save quest", enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), cue = cue.trim(), unit = unit.trim().ifBlank { if (target > 1) "times" else "once" },
                            target = target, xp = xp, reminderTimes = timeList.sorted().joinToString(","), ringing = ringing,
                        )
                    )
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SystemLink("Cancel", onCancel)
                if (!isNew) SystemLink("Delete quest", { confirmDelete = true })
            }
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = null, confirm = "Add reminder",
            onPick = { t -> times = (timeList + t).distinct().joinToString(","); pickingTime = false }, onDismiss = { pickingTime = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("Its reminders stop and it leaves your quest list. XP you already earned stays.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete quest") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
}

/** A time picker returning HH:mm. [initial] is HH:mm, or null to start at the current hour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: String?, confirm: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val start = initial?.let { LocalTime.parse(it) } ?: LocalTime.now().withMinute(0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StepButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleLarge)
        StepButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Sw.colors
    Box(
        Modifier.size(44.dp).background(colors.bg).border(1.dp, if (enabled) colors.edge else colors.line).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (enabled) colors.glow else colors.line)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Sw.colors.muted)
}
