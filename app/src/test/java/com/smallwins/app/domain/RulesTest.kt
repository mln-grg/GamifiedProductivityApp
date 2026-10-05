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

    private fun rec(dayOffset: Long, status: DayStatus) = DayRecord(mon.plusDays(dayOffset), status)
    private fun day(vararg quests: Pair<Int, Int>) = Rules.evaluate(quests.map { QuestProgress(target = it.first, done = it.second) })

    @Test fun `clearing half the quests wins the day, clearing all is gold`() {
        assertFalse(day(8 to 7, 1 to 0, 1 to 0).won)
        assertTrue(day(8 to 8, 1 to 1, 1 to 0).won)
        assertFalse(day(8 to 8, 1 to 1, 1 to 0).gold)
        assertTrue(day(8 to 8, 1 to 1).gold)
        assertTrue(day(1 to 1, 1 to 0).won)   // 1 of 2
        assertFalse(day(1 to 1, 1 to 0, 1 to 0, 1 to 0).won)   // 1 of 4
    }

    @Test fun `a day with no quests is never won`() {
        assertFalse(day().won); assertFalse(day().gold)
    }

    @Test fun `streak bonus is 2 percent a day capped at 50`() {
        assertEquals(0, Rules.streakBonusPercent(0))
        assertEquals(24, Rules.streakBonusPercent(12))
        assertEquals(50, Rules.streakBonusPercent(25))
        assertEquals(50, Rules.streakBonusPercent(400))
        assertEquals(60, Rules.scaled(40, 25))
    }

    @Test fun `every tick pays and ticks add up to the quest's scaled xp`() {
        for (streak in listOf(0, 3, 12, 40)) {
            for (target in listOf(1, 3, 7, 8, 30)) {
                val ticks = (1..target).map { Rules.unitXp(40, target, it, streak) }
                assertEquals(Rules.scaled(40, streak), ticks.sum())
                assertTrue(ticks.all { it >= 0 })
            }
        }
        assertEquals(listOf(5, 5, 5, 5, 5, 5, 5, 5), (1..8).map { Rules.unitXp(40, 8, it, 0) })
    }

    @Test fun `levels need 100 xp then 50 more each`() {
        assertEquals(LevelState(1, 0, 100), Rules.level(0))
        assertEquals(LevelState(1, 99, 100), Rules.level(99))
        assertEquals(LevelState(2, 0, 150), Rules.level(100))
        assertEquals(LevelState(4, 10, 250), Rules.level(460))
    }

    @Test fun `ranks step up at fixed levels`() {
        assertEquals("E", Rules.rank(6)); assertEquals("D", Rules.rank(7)); assertEquals("C", Rules.rank(12))
        assertEquals("B", Rules.rank(22)); assertEquals("A", Rules.rank(35)); assertEquals("S", Rules.rank(50))
    }

    @Test fun `streak counts wins and rest days do not break it`() {
        val s = Rules.streak(listOf(rec(0, DayStatus.WON), rec(1, DayStatus.GOLD), rec(2, DayStatus.RESTED), rec(3, DayStatus.WON)))
        assertEquals(3, s.current); assertNull(s.earnBack)
    }

    @Test fun `missed day resets streak and opens an earn-back window`() {
        val s = Rules.streak(listOf(rec(0, DayStatus.WON), rec(1, DayStatus.WON), rec(2, DayStatus.MISSED)))
        assertEquals(0, s.current); assertEquals(2, s.best)
        assertNotNull(s.earnBack); assertEquals(2, s.earnBack!!.lostStreak)
        assertEquals(mon.plusDays(5), s.earnBack!!.deadline)
    }

    @Test fun `two full clears inside the window restore the lost streak`() {
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
        val lost = DayEval(cleared = 0, total = 3)
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
}
