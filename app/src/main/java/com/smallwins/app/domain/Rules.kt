package com.smallwins.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScheduleType { DAILY, WEEKLY }

enum class DayStatus { WON, GOLD, RESTED, MISSED }

/**
 * The parts of a habit the rules need.
 * DAILY: [target] and [minimum] are counts per day.
 * WEEKLY: done on [weeklyDays] days out of 7; one log on a day counts as that day's session.
 */
data class HabitRule(
    val id: String,
    val schedule: ScheduleType,
    val target: Int,
    val minimum: Int,
    val weeklyDays: Int,
    val startDate: LocalDate,
)

data class HabitProgress(
    val rule: HabitRule,
    val doneToday: Int,
    /** Days earlier in this Monday-to-Sunday week on which the habit was logged. */
    val doneDaysEarlierThisWeek: Int = 0,
)

data class DayEval(val score: Int, val won: Boolean, val gold: Boolean, val dueCount: Int)

data class DayRecord(val date: LocalDate, val status: DayStatus, val score: Int)

data class EarnBack(val lostStreak: Int, val goldDays: Int, val deadline: LocalDate)

data class StreakState(
    val current: Int,
    val best: Int,
    val lifetimeWins: Int,
    val earnBack: EarnBack?,
)

object Rules {
    const val REST_TOKENS_PER_WEEK = 2
    const val EARN_BACK_GOLD_DAYS = 2
    const val EARN_BACK_WINDOW_DAYS = 3L
    const val COMEBACK_BONUS = 15
    val MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** A habit started mid-week gets the days before it existed as free off-days. */
    fun weeklyTarget(rule: HabitRule, date: LocalDate): Int {
        val start = weekStart(date)
        if (!rule.startDate.isAfter(start)) return rule.weeklyDays
        val daysBeforeStart = ChronoUnit.DAYS.between(start, rule.startDate).toInt()
        return max(0, rule.weeklyDays - daysBeforeStart)
    }

    /**
     * Daily habits are due every day. A weekly habit only becomes due once it can no
     * longer be put off: the sessions still needed equal the days left in the week.
     */
    fun isDue(progress: HabitProgress, date: LocalDate): Boolean {
        val rule = progress.rule
        if (date.isBefore(rule.startDate)) return false
        return when (rule.schedule) {
            ScheduleType.DAILY -> true
            ScheduleType.WEEKLY -> {
                val daysLeft = 8 - date.dayOfWeek.value
                val needed = weeklyTarget(rule, date) - progress.doneDaysEarlierThisWeek
                needed > 0 && needed >= daysLeft
            }
        }
    }

    fun fraction(progress: HabitProgress): Float {
        val target = max(1, progress.rule.target)
        return min(1f, progress.doneToday.toFloat() / target)
    }

    /** A day with nothing due is a free win: it keeps the streak but is never gold. */
    fun evaluateDay(date: LocalDate, habits: List<HabitProgress>): DayEval {
        val due = habits.filter { isDue(it, date) }
        if (due.isEmpty()) return DayEval(score = 100, won = true, gold = false, dueCount = 0)
        val score = (due.map { fraction(it) }.average() * 100).roundToInt()
        return DayEval(
            score = score,
            won = due.all { it.doneToday >= it.rule.minimum },
            gold = due.all { it.doneToday >= it.rule.target },
            dueCount = due.size,
        )
    }

    fun restTokensLeft(date: LocalDate, records: List<DayRecord>): Int {
        val start = weekStart(date)
        val used = records.count { it.status == DayStatus.RESTED && weekStart(it.date) == start }
        return max(0, REST_TOKENS_PER_WEEK - used)
    }

    /**
     * Status a finished day gets. A lost day spends a rest token automatically, but only
     * when there is a streak worth protecting.
     */
    fun finalizeStatus(eval: DayEval, date: LocalDate, earlier: List<DayRecord>): DayStatus = when {
        eval.gold -> DayStatus.GOLD
        eval.won -> DayStatus.WON
        streak(earlier).current > 0 && restTokensLeft(date, earlier) > 0 -> DayStatus.RESTED
        else -> DayStatus.MISSED
    }

    /** [records] must be sorted by date, oldest first. */
    fun streak(records: List<DayRecord>): StreakState {
        var current = 0
        var best = 0
        var wins = 0
        var earnBack: EarnBack? = null
        for (r in records) {
            if (earnBack != null && r.date.isAfter(earnBack.deadline)) earnBack = null
            when (r.status) {
                DayStatus.WON, DayStatus.GOLD -> {
                    current++
                    wins++
                    val eb = earnBack
                    if (r.status == DayStatus.GOLD && eb != null) {
                        val gold = eb.goldDays + 1
                        if (gold >= EARN_BACK_GOLD_DAYS) {
                            current += eb.lostStreak
                            earnBack = null
                        } else {
                            earnBack = eb.copy(goldDays = gold)
                        }
                    }
                }
                DayStatus.RESTED -> Unit
                DayStatus.MISSED -> {
                    if (earnBack == null && current > 0) {
                        earnBack = EarnBack(current, 0, r.date.plusDays(EARN_BACK_WINDOW_DAYS))
                    }
                    current = 0
                }
            }
            best = max(best, current)
        }
        return StreakState(current, best, wins, earnBack)
    }

    /** True when the last finished day was not won and there is history to come back to. */
    fun isComeback(records: List<DayRecord>): Boolean {
        val last = records.lastOrNull() ?: return false
        val lostLast = last.status == DayStatus.RESTED || last.status == DayStatus.MISSED
        return lostLast && records.any { it.status == DayStatus.WON || it.status == DayStatus.GOLD }
    }

    fun weekScore(date: LocalDate, records: List<DayRecord>, todayScore: Int): Int {
        val start = weekStart(date)
        return records.filter { weekStart(it.date) == start && it.date.isBefore(date) }.sumOf { it.score } + todayScore
    }

    fun milestoneReached(streak: Int): Boolean = streak in MILESTONES
}
