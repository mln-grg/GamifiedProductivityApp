package com.smallwins.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

enum class DayStatus { WON, GOLD, RESTED, MISSED }

data class QuestProgress(val target: Int, val done: Int) {
    val cleared get() = done >= target
}

/** How a day's quests went. Clearing at least half keeps the streak; clearing all is a full clear. */
data class DayEval(val cleared: Int, val total: Int) {
    val won get() = total > 0 && cleared >= (total + 1) / 2
    val gold get() = total > 0 && cleared == total
}

data class DayRecord(val date: LocalDate, val status: DayStatus)

data class EarnBack(val lostStreak: Int, val goldDays: Int, val deadline: LocalDate)

data class StreakState(val current: Int, val best: Int, val earnBack: EarnBack?)

data class LevelState(val level: Int, val xpInto: Int, val xpNeeded: Int) {
    val rank get() = Rules.rank(level)
}

object Rules {
    const val MIN_QUESTS = 2
    const val REST_TOKENS_PER_WEEK = 2
    const val EARN_BACK_GOLD_DAYS = 2
    const val EARN_BACK_WINDOW_DAYS = 3L
    const val CLEAR_BONUS_XP = 40
    const val COMEBACK_XP = 20
    private const val STREAK_BONUS_PER_DAY = 2
    private const val MAX_STREAK_BONUS = 50

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun evaluate(quests: List<QuestProgress>) = DayEval(quests.count { it.cleared }, quests.size)

    /** Extra XP in percent for a streak of finished days: 2% a day, capped at 50%. */
    fun streakBonusPercent(streak: Int): Int = min(MAX_STREAK_BONUS, STREAK_BONUS_PER_DAY * max(0, streak))

    fun scaled(baseXp: Int, streak: Int): Int = baseXp * (100 + streakBonusPercent(streak)) / 100

    /**
     * XP for the [unit]-th tick (1-based) of a quest done [target] times a day.
     * Every tick pays, and the ticks always add up to exactly the quest's scaled XP.
     */
    fun unitXp(baseXp: Int, target: Int, unit: Int, streak: Int): Int {
        val total = scaled(baseXp, streak)
        val t = max(1, target)
        return total * unit / t - total * (unit - 1) / t
    }

    /** Levels get slower: 100 XP for the first (a full first day reaches level 2), 50 more for each one after. */
    fun xpNeeded(level: Int): Int = 100 + 50 * (level - 1)

    fun level(totalXp: Int): LevelState {
        var level = 1
        var left = max(0, totalXp)
        while (left >= xpNeeded(level)) {
            left -= xpNeeded(level)
            level++
        }
        return LevelState(level, left, xpNeeded(level))
    }

    fun rank(level: Int): String = when {
        level >= 50 -> "S"
        level >= 35 -> "A"
        level >= 22 -> "B"
        level >= 12 -> "C"
        level >= 7 -> "D"
        else -> "E"
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
        var earnBack: EarnBack? = null
        for (r in records) {
            if (earnBack != null && r.date.isAfter(earnBack.deadline)) earnBack = null
            when (r.status) {
                DayStatus.WON, DayStatus.GOLD -> {
                    current++
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
        return StreakState(current, best, earnBack)
    }

    /** True when the last finished day was not won and there is history to come back to. */
    fun isComeback(records: List<DayRecord>): Boolean {
        val last = records.lastOrNull() ?: return false
        val lostLast = last.status == DayStatus.RESTED || last.status == DayStatus.MISSED
        return lostLast && records.any { it.status == DayStatus.WON || it.status == DayStatus.GOLD }
    }
}
