package com.smallwins.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    // 2026-10-05 is a Monday.
    private val mon = LocalDate.of(2026, 10, 5)
    private val longAgo = LocalDate.of(2026, 1, 1)

    private fun daily(target: Int, min: Int) = HabitRule("d", ScheduleType.DAILY, target, min, 0, longAgo)
    private fun weekly(days: Int, start: LocalDate = longAgo) = HabitRule("w", ScheduleType.WEEKLY, 1, 1, days, start)
    private fun rec(dayOffset: Long, status: DayStatus, score: Int = 100) = DayRecord(mon.plusDays(dayOffset), status, score)

    @Test fun `minimum wins the day, target makes it gold`() {
        val water = daily(target = 8, min = 4)
        val below = Rules.evaluateDay(mon, listOf(HabitProgress(water, 3)))
        val atMin = Rules.evaluateDay(mon, listOf(HabitProgress(water, 4)))
        val full = Rules.evaluateDay(mon, listOf(HabitProgress(water, 9)))
        assertFalse(below.won)
        assertTrue(atMin.won); assertFalse(atMin.gold); assertEquals(50, atMin.score)
        assertTrue(full.gold); assertEquals(100, full.score)
    }

    @Test fun `weekly habit is not due until it cannot be put off`() {
        val gym = weekly(4)
        assertFalse(Rules.isDue(HabitProgress(gym, 0, 0), mon))               // 7 days left, 4 needed
        assertFalse(Rules.isDue(HabitProgress(gym, 0, 0), mon.plusDays(2)))   // Wed: 5 left
        assertTrue(Rules.isDue(HabitProgress(gym, 0, 0), mon.plusDays(3)))    // Thu: 4 left, 4 needed
        assertFalse(Rules.isDue(HabitProgress(gym, 0, 2), mon.plusDays(3)))   // 2 already done
        assertFalse(Rules.isDue(HabitProgress(gym, 0, 4), mon.plusDays(6)))   // week complete
    }

    @Test fun `weekly habit started mid-week gets earlier days free`() {
        val sat = mon.plusDays(5)
        val gym = weekly(4, start = sat)
        assertEquals(0, Rules.weeklyTarget(gym, sat))
        assertFalse(Rules.isDue(HabitProgress(gym, 0, 0), sat))
        assertEquals(4, Rules.weeklyTarget(gym, mon.plusDays(7)))
    }

    @Test fun `day with nothing due is a free win but not gold`() {
        val eval = Rules.evaluateDay(mon, listOf(HabitProgress(weekly(4), 0, 0)))
        assertTrue(eval.won); assertFalse(eval.gold); assertEquals(0, eval.dueCount)
    }

    @Test fun `streak counts wins and rest days do not break it`() {
        val s = Rules.streak(listOf(rec(0, DayStatus.WON), rec(1, DayStatus.GOLD), rec(2, DayStatus.RESTED), rec(3, DayStatus.WON)))
        assertEquals(3, s.current); assertEquals(3, s.lifetimeWins); assertNull(s.earnBack)
    }

    @Test fun `missed day resets streak and opens an earn-back window`() {
        val s = Rules.streak(listOf(rec(0, DayStatus.WON), rec(1, DayStatus.WON), rec(2, DayStatus.MISSED)))
        assertEquals(0, s.current); assertEquals(2, s.best)
        assertNotNull(s.earnBack); assertEquals(2, s.earnBack!!.lostStreak)
        assertEquals(mon.plusDays(5), s.earnBack!!.deadline)
    }

    @Test fun `two gold days inside the window restore the lost streak`() {
        val s = Rules.streak(listOf(
            rec(0, DayStatus.WON), rec(1, DayStatus.WON), rec(2, DayStatus.MISSED),
            rec(3, DayStatus.GOLD), rec(4, DayStatus.WON), rec(5, DayStatus.GOLD),
        ))
        assertEquals(5, s.current); assertNull(s.earnBack)
    }

    @Test fun `earn-back expires after the window`() {
        val s = Rules.streak(listOf(
            rec(0, DayStatus.WON), rec(1, DayStatus.WON), rec(2, DayStatus.MISSED),
            rec(3, DayStatus.GOLD), rec(4, DayStatus.WON), rec(5, DayStatus.WON), rec(6, DayStatus.GOLD),
        ))
        assertEquals(4, s.current); assertNull(s.earnBack)
    }

    @Test fun `lost day spends a rest token only while a streak exists and tokens remain`() {
        val lost = DayEval(score = 20, won = false, gold = false, dueCount = 1)
        assertEquals(DayStatus.MISSED, Rules.finalizeStatus(lost, mon, emptyList()))
        val one = listOf(rec(0, DayStatus.WON))
        assertEquals(DayStatus.RESTED, Rules.finalizeStatus(lost, mon.plusDays(1), one))
        val two = one + rec(1, DayStatus.RESTED) + rec(2, DayStatus.RESTED)
        assertEquals(0, Rules.restTokensLeft(mon.plusDays(3), two))
        assertEquals(DayStatus.MISSED, Rules.finalizeStatus(lost, mon.plusDays(3), two))
    }

    @Test fun `rest tokens refill on Monday`() {
        val lastWeek = listOf(rec(-3, DayStatus.WON), rec(-2, DayStatus.RESTED), rec(-1, DayStatus.RESTED))
        assertEquals(2, Rules.restTokensLeft(mon, lastWeek))
    }

    @Test fun `comeback needs a lost last day and earlier wins`() {
        assertFalse(Rules.isComeback(emptyList()))
        assertFalse(Rules.isComeback(listOf(rec(0, DayStatus.MISSED))))
        assertTrue(Rules.isComeback(listOf(rec(0, DayStatus.WON), rec(1, DayStatus.RESTED))))
        assertFalse(Rules.isComeback(listOf(rec(0, DayStatus.RESTED), rec(1, DayStatus.WON))))
    }

    @Test fun `week score sums this week's finished days plus today`() {
        val records = listOf(rec(-1, DayStatus.WON, 90), rec(0, DayStatus.WON, 80), rec(1, DayStatus.GOLD, 100))
        assertEquals(230, Rules.weekScore(mon.plusDays(2), records, todayScore = 50))
    }
}
