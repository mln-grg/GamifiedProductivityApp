package com.smallwins.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smallwins.app.SmallWinsApp
import com.smallwins.app.data.QuestEntity
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.notify.ActionReceiver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Feedback {
    val id: Long

    /** A short line for a tick or a cleared quest. */
    data class Gain(override val id: Long, val headline: String, val detail: String, val sparks: Boolean) : Feedback

    /** The full-screen moment. */
    data class LevelUp(override val id: Long, val level: Int, val rank: String, val newRank: Boolean) : Feedback
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SmallWinsApp
    private val today = MutableStateFlow(LocalDate.now())
    private val clock = MutableStateFlow(System.currentTimeMillis())

    val snapshot: StateFlow<TodaySnapshot?> = today
        .flatMapLatest { app.repo.observeToday(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reminders in the last three days that never rang or rang late. */
    val missedReminders: StateFlow<Int> = clock
        .flatMapLatest { now ->
            app.db.alarmEvents().observeMissed(maxOf(app.prefs.missedAckAt, now - MISSED_WINDOW_MS), now, LATE_MS)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Quests ticked on the pick screen; null until the player changes the suggested set. */
    private val _selection = MutableStateFlow<Set<String>?>(null)
    val selection = _selection.asStateFlow()

    private val _morningTime = MutableStateFlow(app.prefs.morningTime)
    val morningTime = _morningTime.asStateFlow()

    private val _feedback = MutableSharedFlow<Feedback>(extraBufferCapacity = 2)
    val feedback = _feedback.asSharedFlow()

    val setupSeen get() = app.prefs.setupSeen

    fun markSetupSeen() {
        app.prefs.setupSeen = true
    }

    /** The date can change while the app sits in the background. */
    fun onResume() {
        today.value = LocalDate.now()
        clock.value = System.currentTimeMillis()
        app.notifier.cancelMorning()
        viewModelScope.launch { app.repo.rollover() }
    }

    fun toggle(questId: String, suggested: Set<String>) = _selection.update { current ->
        val base = current ?: suggested
        if (questId in base) base - questId else base + questId
    }

    fun acceptQuests(questIds: Set<String>) = viewModelScope.launch {
        app.repo.confirmPlan(questIds)
        _selection.value = null
    }

    fun log(questId: String) = viewModelScope.launch {
        val outcome = app.repo.log(questId, "app") ?: return@launch
        app.notifier.cancelReminder(questId)
        app.scheduler.cancelRering(questId)
        // The level-up window carries the big news, so the line under it stays about the quest.
        val headline = if (outcome.leveledUp) "${outcome.quest.quest.name} logged" else ActionReceiver.headline(outcome)
        _feedback.tryEmit(Feedback.Gain(System.nanoTime(), headline, "+${outcome.xpGained} XP", sparks = outcome.fullClear))
        if (outcome.leveledUp) {
            val level = outcome.after.level
            _feedback.tryEmit(Feedback.LevelUp(System.nanoTime(), level.level, level.rank, level.rank != outcome.before.level.rank))
        }
    }

    fun undo(questId: String) = viewModelScope.launch { app.repo.undoLast(questId) }

    suspend fun quest(id: String): QuestEntity? = app.repo.quest(id)

    /** Saves a quest; a new one is picked for today straight away. */
    fun save(quest: QuestEntity, isNew: Boolean, suggested: Set<String>) = viewModelScope.launch {
        app.repo.saveQuest(quest)
        if (isNew) _selection.update { (it ?: suggested) + quest.id }
        app.scheduler.rescheduleAll()
    }

    fun archive(id: String) = viewModelScope.launch {
        app.repo.archiveQuest(id)
        _selection.update { it?.minus(id) }
        app.notifier.cancelReminder(id)
        app.scheduler.cancelRering(id)
        app.scheduler.rescheduleAll()
    }

    fun setMorningTime(time: String) {
        app.prefs.morningTime = time
        _morningTime.value = time
        reschedule()
    }

    fun acknowledgeMissed() {
        app.prefs.missedAckAt = System.currentTimeMillis()
        clock.value = System.currentTimeMillis()
    }

    fun sendTestReminder() = app.scheduler.scheduleTest(TEST_DELAY_MS)

    fun reschedule() = viewModelScope.launch { app.scheduler.rescheduleAll() }

    companion object {
        private const val MISSED_WINDOW_MS = 3 * 24 * 3_600_000L
        private const val LATE_MS = 10 * 60_000L
        const val TEST_DELAY_MS = 60_000L
    }
}
