package com.smallwins.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import com.smallwins.app.alarms.AlarmScheduler
import com.smallwins.app.data.AppDb
import com.smallwins.app.data.Prefs
import com.smallwins.app.data.Repo
import com.smallwins.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmallWinsApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { AppDb.create(this) }
    val repo by lazy { Repo(db) }
    val prefs by lazy { Prefs(this) }
    val notifier by lazy { Notifier(this, prefs) }
    val scheduler by lazy { AlarmScheduler(this, db, prefs) }

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        scope.launch {
            repo.seedStarterQuests()
            repo.rollover()
            scheduler.rescheduleAll()
        }
    }
}

val Context.app: SmallWinsApp get() = applicationContext as SmallWinsApp

/** Runs [block] off the main thread and keeps the receiver alive until it finishes. */
fun BroadcastReceiver.async(context: Context, block: suspend (SmallWinsApp) -> Unit) {
    val pending = goAsync()
    val app = context.app
    app.scope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}
