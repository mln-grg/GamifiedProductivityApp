package com.smallwins.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smallwins.app.SmallWinsApp
import com.smallwins.app.data.HabitEntity
import com.smallwins.app.data.TodaySnapshot
import com.smallwins.app.notify.Copy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class Celebration(val id: Long, val headline: String, val detail: String)

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

    private val _celebrations = MutableSharedFlow<Celebration>(extraBufferCapacity = 1)
    val celebrations = _celebrations.asSharedFlow()

    val setupSeen get() = app.prefs.setupSeen

    fun markSetupSeen() {
        app.prefs.setupSeen = true
    }

    /** The date can change while the app sits in the background. */
    fun onResume() {
        today.value = LocalDate.now()
        clock.value = System.currentTimeMillis()
        viewModelScope.launch { app.repo.rollover() }
    }

    fun log(habitId: String) = viewModelScope.launch {
        val outcome = app.repo.log(habitId, "app")
        app.notifier.cancelReminder(habitId)
        app.scheduler.cancelRering(habitId)
        val streak = outcome.after.streak
        val streakLine = "$streak-day streak"
        val celebration = when {
            outcome.milestone -> Celebration(System.nanoTime(), "$streak days", "Milestone. The flame has never been this big.")
            outcome.dayJustGold -> Celebration(System.nanoTime(), app.notifier.pick("gold", Copy.gold), streakLine)
            outcome.dayJustWon -> Celebration(System.nanoTime(), app.notifier.pick("dayWon", Copy.dayWon), streakLine)
            else -> null
        }
        celebration?.let { _celebrations.tryEmit(it) }
    }

    fun undo(habitId: String) = viewModelScope.launch { app.repo.undoLast(habitId) }

    suspend fun habit(id: String): HabitEntity? = app.repo.habit(id)

    suspend fun habitCount(): Int = app.repo.activeHabits().size

    fun save(habit: HabitEntity) = viewModelScope.launch {
        app.repo.saveHabit(habit)
        app.scheduler.rescheduleAll()
    }

    fun archive(id: String) = viewModelScope.launch {
        app.repo.archiveHabit(id)
        app.notifier.cancelReminder(id)
        app.scheduler.cancelRering(id)
        app.scheduler.rescheduleAll()
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
