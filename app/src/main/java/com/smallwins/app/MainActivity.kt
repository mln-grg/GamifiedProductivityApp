package com.smallwins.app

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smallwins.app.data.HabitEntity
import com.smallwins.app.ui.Celebration
import com.smallwins.app.ui.HabitEditorScreen
import com.smallwins.app.ui.MainViewModel
import com.smallwins.app.ui.SetupScreen
import com.smallwins.app.ui.SmallWinsTheme
import com.smallwins.app.ui.Sparks
import com.smallwins.app.ui.Sw
import com.smallwins.app.ui.Template
import com.smallwins.app.ui.TodayScreen
import com.smallwins.app.ui.templateHabit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SmallWinsTheme { AppRoot() } }
    }
}

private const val TODAY = "today"
private const val SETUP = "setup"
private const val NEW = "new:"
private const val EDIT = "edit:"

@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    val colors = Sw.colors
    val view = LocalView.current
    var route by rememberSaveable { mutableStateOf(if (vm.setupSeen) TODAY else SETUP) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val missed by vm.missedReminders.collectAsStateWithLifecycle()
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.celebrations.collectLatest {
            celebration = it
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            delay(2_600)
            celebration = null
        }
    }
    BackHandler(enabled = route != TODAY) {
        vm.markSetupSeen()
        route = TODAY
    }

    Box(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding()) {
        when {
            route == SETUP -> SetupScreen(
                firstRun = !vm.setupSeen,
                onChanged = { vm.reschedule() },
                onTest = { vm.sendTestReminder() },
                onDone = { vm.markSetupSeen(); route = TODAY },
            )
            route.startsWith(NEW) -> {
                val template = Template.valueOf(route.removePrefix(NEW))
                val initial = remember(route) { templateHabit(template, snap?.items?.size ?: 0) }
                HabitEditorScreen(initial, isNew = true, onSave = { vm.save(it); route = TODAY }, onDelete = { route = TODAY }, onCancel = { route = TODAY })
            }
            route.startsWith(EDIT) -> {
                val id = route.removePrefix(EDIT)
                val habit by produceState<HabitEntity?>(null, id) { value = vm.habit(id) }
                habit?.let { h ->
                    HabitEditorScreen(h, isNew = false, onSave = { vm.save(it); route = TODAY }, onDelete = { vm.archive(id); route = TODAY }, onCancel = { route = TODAY })
                }
            }
            else -> snap?.let { s ->
                TodayScreen(
                    snap = s, missedReminders = missed,
                    onLog = vm::log, onUndo = vm::undo,
                    onEdit = { route = EDIT + it }, onAdd = { route = NEW + it.name },
                    onSetup = { route = SETUP }, onDismissMissed = vm::acknowledgeMissed,
                )
            }
        }
        celebration?.let { c ->
            Sparks(c.id, Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).clip(RoundedCornerShape(20.dp)).background(colors.ink).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.headline, style = MaterialTheme.typography.titleLarge, color = colors.bg)
                Text(c.detail, style = MaterialTheme.typography.bodyMedium, color = colors.bg)
            }
        }
    }
}
