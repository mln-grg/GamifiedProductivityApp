package com.smallwins.app.data

import com.smallwins.app.domain.DayEval
import com.smallwins.app.domain.DayRecord
import com.smallwins.app.domain.EarnBack
import com.smallwins.app.domain.HabitProgress
import com.smallwins.app.domain.Rules
import com.smallwins.app.domain.ScheduleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class HabitItem(
    val habit: HabitEntity,
    val progress: HabitProgress,
    val due: Boolean,
    val weeklyTarget: Int,
) {
    val done get() = progress.doneToday

    /** Days done this week including today, for weekly habits. */
    val weekDone get() = progress.doneDaysEarlierThisWeek + if (progress.doneToday > 0) 1 else 0

    /** Nothing more to do for this habit today, so its reminders stay quiet. */
    val finishedForToday: Boolean
        get() = when (habit.schedule) {
            ScheduleType.DAILY -> done >= habit.target
            ScheduleType.WEEKLY -> done > 0 || progress.doneDaysEarlierThisWeek >= weeklyTarget
        }
}

data class TodaySnapshot(
    val date: LocalDate,
    val items: List<HabitItem>,
    val eval: DayEval,
    val records: List<DayRecord>,
    /** Streak including today when today is already won. */
    val streak: Int,
    val best: Int,
    val lifetimeWins: Int,
    val earnBack: EarnBack?,
    val restTokensLeft: Int,
    val weekScore: Int,
    val comeback: Boolean,
) {
    val hasHabits get() = items.isNotEmpty()
    val comebackBonus get() = if (comeback && items.any { it.done > 0 }) Rules.COMEBACK_BONUS else 0
}

data class LogOutcome(val before: TodaySnapshot, val after: TodaySnapshot) {
    val dayJustWon get() = !before.eval.won && after.eval.won
    val dayJustGold get() = !before.eval.gold && after.eval.gold
    val milestone get() = dayJustWon && Rules.milestoneReached(after.streak)
}

class Repo(private val db: AppDb) {
    private val habits = db.habits()
    private val logs = db.logs()
    private val dayRecords = db.dayRecords()

    fun observeToday(date: LocalDate): Flow<TodaySnapshot> = combine(
        habits.observeActive(),
        logs.observeFrom(Rules.weekStart(date).toString()),
        dayRecords.observeAll(),
    ) { h, l, r -> buildSnapshot(date, h, l, r.toDomain().filter { it.date.isBefore(date) }) }

    suspend fun snapshot(date: LocalDate): TodaySnapshot = buildSnapshot(
        date,
        habits.active(),
        logs.between(Rules.weekStart(date).toString(), date.toString()),
        dayRecords.all().toDomain().filter { it.date.isBefore(date) },
    )

    suspend fun habit(id: String): HabitEntity? = habits.byId(id)

    suspend fun saveHabit(habit: HabitEntity) = habits.upsert(habit)

    suspend fun archiveHabit(id: String) = habits.archive(id)

    suspend fun activeHabits(): List<HabitEntity> = habits.active()

    suspend fun log(habitId: String, source: String, date: LocalDate = LocalDate.now()): LogOutcome {
        val before = snapshot(date)
        logs.insert(LogEntity(habitId = habitId, date = date.toString(), loggedAt = System.currentTimeMillis(), source = source))
        return LogOutcome(before, snapshot(date))
    }

    suspend fun undoLast(habitId: String, date: LocalDate = LocalDate.now()) =
        logs.deleteLast(habitId, date.toString())

    /**
     * Writes a record for every finished day that does not have one yet, oldest first,
     * so streaks stay fixed even if habits are edited later.
     */
    suspend fun rollover(today: LocalDate = LocalDate.now()) {
        val allHabits = habits.all()
        val firstStart = allHabits.minOfOrNull { LocalDate.parse(it.startDate) } ?: return
        val records = dayRecords.all().toDomain().toMutableList()
        var day = records.lastOrNull()?.date?.plusDays(1) ?: firstStart
        if (!day.isBefore(today)) return
        val allLogs = logs.between(Rules.weekStart(day).toString(), today.toString())
        while (day.isBefore(today)) {
            // Habits archived since still count for the days they were logged on.
            val relevant = allHabits.filter { h -> !h.archived || allLogs.any { it.habitId == h.id && it.date == day.toString() } }
            val items = items(day, relevant, allLogs)
            val eval = Rules.evaluateDay(day, items.map { it.progress })
            val bonus = if (Rules.isComeback(records) && items.any { it.done > 0 }) Rules.COMEBACK_BONUS else 0
            val record = DayRecord(day, Rules.finalizeStatus(eval, day, records), eval.score + bonus)
            dayRecords.upsert(DayRecordEntity(record.date.toString(), record.status, record.score))
            records += record
            day = day.plusDays(1)
        }
    }

    private fun items(date: LocalDate, habits: List<HabitEntity>, logs: List<LogEntity>): List<HabitItem> {
        val weekStart = Rules.weekStart(date).toString()
        val day = date.toString()
        return habits.map { h ->
            val mine = logs.filter { it.habitId == h.id }
            val progress = HabitProgress(
                rule = h.rule(),
                doneToday = mine.count { it.date == day },
                doneDaysEarlierThisWeek = mine.filter { it.date >= weekStart && it.date < day }.map { it.date }.distinct().size,
            )
            HabitItem(h, progress, Rules.isDue(progress, date), Rules.weeklyTarget(progress.rule, date))
        }
    }

    private fun buildSnapshot(date: LocalDate, habits: List<HabitEntity>, logs: List<LogEntity>, records: List<DayRecord>): TodaySnapshot {
        val items = items(date, habits, logs)
        val eval = Rules.evaluateDay(date, items.map { it.progress })
        val state = Rules.streak(records)
        val comeback = Rules.isComeback(records)
        val counted = items.isNotEmpty() && eval.won
        val bonus = if (comeback && items.any { it.done > 0 }) Rules.COMEBACK_BONUS else 0
        return TodaySnapshot(
            date = date,
            items = items,
            eval = eval,
            records = records,
            streak = state.current + if (counted) 1 else 0,
            best = state.best,
            lifetimeWins = state.lifetimeWins + if (counted) 1 else 0,
            earnBack = state.earnBack?.takeIf { !date.isAfter(it.deadline) },
            restTokensLeft = Rules.restTokensLeft(date, records),
            weekScore = Rules.weekScore(date, records, if (items.isEmpty()) 0 else eval.score + bonus),
            comeback = comeback,
        )
    }

    private fun List<DayRecordEntity>.toDomain() = map { DayRecord(LocalDate.parse(it.date), it.status, it.score) }
}
