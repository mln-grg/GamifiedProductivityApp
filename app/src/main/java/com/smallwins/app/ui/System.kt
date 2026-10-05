package com.smallwins.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smallwins.app.data.TodaySnapshot

private val WindowShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

/** The framed panel everything in the app sits in: cut corners, blue edge, title between two rules. */
@Composable
fun SystemWindow(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Sw.colors
    Column(
        modifier.fillMaxWidth().clip(WindowShape)
            .background(Brush.verticalGradient(listOf(colors.panelHi, colors.panel)))
            .border(1.dp, colors.edge, WindowShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(colors.edge))
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.glow)
            Box(Modifier.weight(1f).height(1.dp).background(colors.edge))
        }
        content()
    }
}

@Composable
fun SystemButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Sw.colors
    Box(
        modifier.fillMaxWidth().background(if (enabled) colors.edge else colors.line)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.ink else colors.muted, textAlign = TextAlign.Center)
    }
}

/** A quiet text action inside a window. */
@Composable
fun SystemLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Sw.colors.glow,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

/** Ember, level, rank and the XP bar. Shown on top of both the pick and the quest screens. */
@Composable
fun PlayerBar(snap: TodaySnapshot, modifier: Modifier = Modifier) {
    val colors = Sw.colors
    val level = snap.level
    val mood = when {
        snap.eval.gold -> Mood.BLAZE
        snap.eval.won -> Mood.OK
        else -> Mood.LOW
    }
    val fill by animateFloatAsState(level.xpInto.toFloat() / level.xpNeeded, label = "xp")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Ember(mood, Modifier.size(64.dp).semantics { contentDescription = "Ember" })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                Text("${level.level}", style = MaterialTheme.typography.headlineMedium, fontSize = 32.sp)
                Text(
                    "RANK ${level.rank}", style = MaterialTheme.typography.labelSmall, color = colors.gold,
                    modifier = Modifier.border(1.dp, colors.gold).padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(colors.line)) {
                Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(8.dp).background(Brush.horizontalGradient(listOf(colors.edge, colors.glow))))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${level.xpInto} / ${level.xpNeeded} XP", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text(
                    when {
                        snap.streak == 0 -> "No streak yet"
                        snap.bonusPercent > 0 -> "${snap.streak}-day streak · +${snap.bonusPercent}% XP"
                        else -> "${snap.streak}-day streak"
                    },
                    style = MaterialTheme.typography.bodySmall, color = colors.muted,
                )
            }
        }
    }
}
