package com.smallwins.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Brand-specific steps for keeping the app alive in the background. Menu names shift between OS versions. */
private fun brandSteps(): Pair<String, List<String>> {
    val maker = Build.MANUFACTURER.lowercase()
    return when {
        "samsung" in maker -> "Samsung" to listOf(
            "Settings → Battery → Background usage limits → Never sleeping apps → add SmallWins.",
            "In SmallWins app info → Battery, choose Unrestricted.",
        )
        "vivo" in maker || "iqoo" in maker -> "Vivo / iQOO" to listOf(
            "In SmallWins app info → Permissions, turn on Autostart.",
            "Settings → Battery → Background power consumption management → SmallWins → allow high background power use.",
            "Open recent apps, pull SmallWins down (or tap its menu) and choose Lock.",
        )
        "xiaomi" in maker || "redmi" in maker || "poco" in maker -> "Xiaomi / Redmi / Poco" to listOf(
            "In SmallWins app info, turn on Autostart.",
            "In SmallWins app info → Battery saver, choose No restrictions.",
            "Open recent apps, long-press SmallWins and tap the lock.",
        )
        "oneplus" in maker || "oppo" in maker || "realme" in maker -> "OnePlus / Oppo / Realme" to listOf(
            "In SmallWins app info → Battery usage, allow background activity and auto-launch.",
            "Open recent apps, tap the menu on SmallWins and choose Lock.",
        )
        "huawei" in maker || "honor" in maker -> "Huawei / Honor" to listOf(
            "Settings → Battery → App launch → SmallWins → manage manually, and turn on all three switches.",
        )
        else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() } to listOf(
            "In SmallWins app info → Battery, choose Unrestricted if your phone offers it.",
        )
    }
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        startActivity(appDetails().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

@Composable
fun SetupScreen(firstRun: Boolean, onChanged: () -> Unit, onTest: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = Sw.colors
    var tick by remember { mutableIntStateOf(0) }
    var testSent by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        // Coming back from a settings page: re-read what was granted and re-register alarms.
        tick++
        onChanged()
        onPauseOrDispose { }
    }

    val notificationsOn = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOn = remember(tick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val batteryOn = remember(tick) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val (brand, steps) = remember { brandSteps() }

    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (!granted) context.open(notificationSettings)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (firstRun) "Let me reach you" else "Phone setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Phones love to silence apps to save battery. These steps make sure reminders ring on time, even with the screen off.",
            style = MaterialTheme.typography.bodyMedium, color = colors.muted,
        )

        Step("Allow notifications", "Reminders arrive as notifications with Done, Snooze and Skip buttons.", notificationsOn, "Allow") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.open(notificationSettings)
        }
        Step("Allow alarms & reminders", "Lets reminders fire at the exact minute. Without it Android may delay them by up to an hour.", exactOn, "Open setting") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }
        Step("Turn off battery limits", "In app info, open Battery and choose Unrestricted (or Don't optimise).", batteryOn, "Open app info") {
            context.open(context.appDetails())
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.panel)
                .border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Extra steps for $brand", style = MaterialTheme.typography.titleMedium)
            Text("I can't check these for you. Menu names differ a little between versions.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { context.open(context.appDetails()) }) { Text("Open app info") }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.panelHi).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Prove it works", style = MaterialTheme.typography.titleMedium)
            Text(
                if (testSent) "Test reminder set. Lock your phone now; it should arrive in one minute."
                else "Send a test reminder, then lock the phone and wait a minute.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { onTest(); testSent = true }) { Text("Send a test reminder in 1 minute") }
        }

        Button(onClick = onDone) { Text(if (firstRun) "Continue" else "Done") }
    }
}

@Composable
private fun Step(title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit) {
    val colors = Sw.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                if (done) "Done" else "Needs you", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                color = if (done) colors.ok else colors.ember,
            )
        }
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        if (!done) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
