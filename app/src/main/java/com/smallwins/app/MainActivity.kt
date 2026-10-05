package com.smallwins.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smallwins.app.data.QuestEntity
import com.smallwins.app.ui.Feedback
import com.smallwins.app.ui.MainViewModel
import com.smallwins.app.ui.PickScreen
import com.smallwins.app.ui.QuestEditorScreen
import com.smallwins.app.ui.SetupScreen
import com.smallwins.app.ui.SmallWinsTheme
import com.smallwins.app.ui.Sparks
import com.smallwins.app.ui.Sw
import com.smallwins.app.ui.SystemButton
import com.smallwins.app.ui.SystemWindow
import com.smallwins.app.ui.TodayScreen
import com.smallwins.app.ui.blankQuest
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark in both system themes, so the bar icons are always light.
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        setContent { SmallWinsTheme { AppRoot() } }
    }
}

private const val TODAY = "today"
private const val PICK = "pick"
private const val SETUP = "setup"
private const val NEW = "new"
private const val EDIT = "edit:"

@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    val colors = Sw.colors
    val view = LocalView.current
    var route by rememberSaveable { mutableStateOf(if (vm.setupSeen) TODAY else SETUP) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val missed by vm.missedReminders.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val morningTime by vm.morningTime.collectAsStateWithLifecycle()
    var gain by remember { mutableStateOf<Feedback.Gain?>(null) }
    var levelUp by remember { mutableStateOf<Feedback.LevelUp?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.feedback.collect {
            when (it) {
                is Feedback.Gain -> gain = it
                is Feedback.LevelUp -> {
                    levelUp = it
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }
    LaunchedEffect(gain?.id) {
        if (gain != null) {
            delay(2_400)
            gain = null
        }
    }
    BackHandler(enabled = route != TODAY) {
        vm.markSetupSeen()
        route = if (route == NEW || route.startsWith(EDIT)) PICK else TODAY
    }

    Box(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        val s = snap
        val suggested = s?.items?.map { it.quest.id }?.toSet().orEmpty()
        when {
            route == SETUP -> SetupScreen(
                firstRun = !vm.setupSeen,
                onChanged = { vm.reschedule() },
                onTest = { vm.sendTestReminder() },
                onDone = { vm.markSetupSeen(); route = TODAY },
            )
            s == null -> Unit
            route == NEW -> {
                val initial = remember { blankQuest(s.library.size) }
                QuestEditorScreen(initial, isNew = true, onSave = { vm.save(it, true, suggested); route = PICK }, onDelete = { route = PICK }, onCancel = { route = PICK })
            }
            route.startsWith(EDIT) -> {
                val id = route.removePrefix(EDIT)
                val quest by produceState<QuestEntity?>(null, id) { value = vm.quest(id) }
                quest?.let { q ->
                    QuestEditorScreen(q, isNew = false, onSave = { vm.save(it, false, suggested); route = PICK }, onDelete = { vm.archive(id); route = PICK }, onCancel = { route = PICK })
                }
            }
            route == PICK || !s.planConfirmed -> {
                val selected = selection ?: suggested
                PickScreen(
                    snap = s, selected = selected, morningTime = morningTime,
                    onToggle = { vm.toggle(it, suggested) },
                    onAccept = { vm.acceptQuests(s.library.map { it.id }.filter { it in selected }.toSet()); route = TODAY },
                    onNew = { route = NEW }, onEdit = { route = EDIT + it },
                    onMorningTime = vm::setMorningTime, onSetup = { route = SETUP },
                )
            }
            else -> TodayScreen(
                snap = s, missedReminders = missed,
                onLog = vm::log, onUndo = vm::undo,
                onChangeQuests = { route = PICK }, onSetup = { route = SETUP }, onDismissMissed = vm::acknowledgeMissed,
            )
        }

        gain?.let { g ->
            if (g.sparks) Sparks(g.id, Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).background(colors.panelHi).border(1.dp, colors.edge).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(g.headline, style = MaterialTheme.typography.titleMedium)
                Text(g.detail, style = MaterialTheme.typography.labelLarge, color = colors.gold)
            }
        }
        levelUp?.let { l ->
            Box(
                Modifier.fillMaxSize().background(colors.bg.copy(alpha = 0.88f))
                    // Swallow taps so nothing behind the window reacts.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Sparks(l.id, Modifier.fillMaxSize())
                SystemWindow("System", Modifier.widthIn(max = 320.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LEVEL UP", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                        Text("${l.level}", style = MaterialTheme.typography.displayLarge, fontSize = 72.sp, color = colors.glow)
                        Text(
                            if (l.newRank) "New rank reached: ${l.rank}." else "Rank ${l.rank}. Ember grows.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center,
                        )
                    }
                    SystemButton("Continue", { levelUp = null })
                }
            }
        }
    }
}
