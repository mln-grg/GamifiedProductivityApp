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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smallwins.app.data.QuestEntity
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.domain.Rules

/** The morning step: tick which quests from your list count today. */
@Composable
fun PickScreen(
    snap: TodaySnapshot,
    selected: Set<String>,
    morningTime: String,
    onToggle: (String) -> Unit,
    onAccept: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onMorningTime: (String) -> Unit,
    onSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sw.colors
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val count = snap.library.count { it.id in selected }
    val enough = count >= Rules.MIN_QUESTS

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerBar(snap)
        SystemWindow("Choose today's quests") {
            snap.library.forEachIndexed { i, quest ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                PickRow(quest, quest.id in selected, { onToggle(quest.id) }, { onEdit(quest.id) })
            }
            SystemLink("+ New quest", onNew)
            Text(
                if (enough) "$count quests picked. Reminders ring only for these."
                else "Pick at least ${Rules.MIN_QUESTS} quests to start the day.",
                style = MaterialTheme.typography.bodySmall, color = colors.muted,
            )
            SystemButton(if (count == 1) "Accept 1 quest" else "Accept $count quests", onAccept, enabled = enough)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SystemLink("Morning call at $morningTime", { pickingTime = true })
            SystemLink("Phone setup", onSetup)
        }
    }
    if (pickingTime) {
        TimeDialog(
            initial = morningTime, confirm = "Set morning call",
            onPick = { onMorningTime(it); pickingTime = false }, onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun PickRow(quest: QuestEntity, on: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = Sw.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp).background(if (on) colors.glow else colors.bg).border(1.dp, colors.edge), contentAlignment = Alignment.Center) {
            if (on) Text("✓", color = colors.bg, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(quest.name, style = MaterialTheme.typography.titleMedium)
            val amount = if (quest.target > 1) "${quest.target} ${quest.unit}" else quest.unit
            val reminders = if (quest.reminderTimes.isBlank()) "no reminders" else quest.reminderTimes.replace(",", ", ")
            Text("$amount · $reminders", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("+${quest.xp} XP", style = MaterialTheme.typography.labelLarge, color = colors.gold)
            SystemLink("Edit", onEdit)
        }
    }
}
