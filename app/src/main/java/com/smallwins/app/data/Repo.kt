package com.smallwins.app.data

import com.smallwins.app.domain.DayEval
import com.smallwins.app.domain.DayRecord
import com.smallwins.app.domain.EarnBack
import com.smallwins.app.domain.LevelState
import com.smallwins.app.domain.QuestProgress
import com.smallwins.app.domain.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

data class QuestItem(val quest: QuestEntity, val done: Int) {
    val cleared get() = done >= quest.target
}

data class TodaySnapshot(
    val date: LocalDate,
    /** Every quest in the player's list, picked today or not. */
    val library: List<QuestEntity>,
    /** Today's quests: the confirmed plan, or the last plan carried over. */
    val items: List<QuestItem>,
    /** False until the player has chosen (or accepted) today's quests. */
    val planConfirmed: Boolean,
    val eval: DayEval,
    val records: List<DayRecord>,
    /** Streak of finished days; this is what the XP bonus is based on. */
    val finishedStreak: Int,
    val earnBack: EarnBack?,
    val restTokensLeft: Int,
    val totalXp: Int,
    val comeback: Boolean,
) {
    val level: LevelState get() = Rules.level(totalXp)

    /** Streak including today once today is won. */
    val streak get() = finishedStreak + if (eval.won) 1 else 0
    val bonusPercent get() = Rules.streakBonusPercent(finishedStreak)
    val open get() = items.filter { !it.cleared }
}

data class LogOutcome(val quest: QuestItem, val before: TodaySnapshot, val after: TodaySnapshot, val bonusXp: Int) {
    val xpGained get() = after.totalXp - before.totalXp
    val leveledUp get() = after.level.level > before.level.level
    val fullClear get() = !before.eval.gold && after.eval.gold
}

class Repo(private val db: AppDb) {
    private val quests = db.quests()
    private val logs = db.logs()
    private val plans = db.plans()
    private val xpEvents = db.xpEvents()
    private val dayRecords = db.dayRecords()
    private val writes = Mutex()

    fun observeToday(date: LocalDate): Flow<TodaySnapshot> {
        val day = date.toString()
        val plan = combine(plans.observe(day), plans.observeLatestBefore(day)) { today, previous -> today to previous }
        val xp = combine(logs.observeTotalXp(), xpEvents.observeTotalXp()) { a, b -> a + b }
        return combine(quests.observeActive(), logs.observeOn(day), plan, dayRecords.observeAll(), xp) { q, l, p, r, total ->
            build(date, q, l, p.first, p.second, r.toDomain().filter { it.date.isBefore(date) }, total)
        }
    }

    suspend fun snapshot(date: LocalDate = LocalDate.now()): TodaySnapshot {
        val day = date.toString()
        return build(
            date, quests.active(), logs.on(day), plans.byDate(day), plans.latestBefore(day),
            dayRecords.all().toDomain().filter { it.date.isBefore(date) }, logs.totalXp() + xpEvents.totalXp(),
        )
    }

    suspend fun quest(id: String): QuestEntity? = quests.byId(id)

    suspend fun saveQuest(quest: QuestEntity) = quests.upsert(quest)

    suspend fun archiveQuest(id: String) = quests.archive(id)

    /** A first list to pick from, so the first morning is not an empty screen. */
    suspend fun seedStarterQuests() {
        if (quests.all().isNotEmpty()) return
        listOf(
            starter("Water", "After each meal", "glasses", 8, 40, "09:00,11:00,13:30,16:00,18:30,21:00"),
            starter("Read", "In bed, before the phone", "10 pages", 1, 40, "22:00"),
            starter("Walk", "After work", "20 minutes", 1, 40, "18:00"),
            starter("Exercise", "Before breakfast", "session", 1, 60, "07:45"),
            starter("Cook dinner", "When you get home", "meal", 1, 40, "19:00"),
        ).forEachIndexed { i, q -> quests.upsert(q.copy(sortOrder = i)) }
    }

    suspend fun confirmPlan(questIds: Collection<String>, date: LocalDate = LocalDate.now()) =
        plans.upsert(PlanEntity(date.toString(), questIds.joinToString(","), confirmed = true))

    /** Accepts yesterday's quests for today. Returns false when there is nothing to carry over. */
    suspend fun acceptCarriedPlan(date: LocalDate = LocalDate.now()): Boolean {
        val ids = snapshot(date).items.map { it.quest.id }
        if (ids.size < Rules.MIN_QUESTS) return false
        confirmPlan(ids, date)
        return true
    }

    /** Ticks a quest once. Returns null when it is not one of today's quests or is already cleared. */
    suspend fun log(questId: String, source: String, date: LocalDate = LocalDate.now()): LogOutcome? = writes.withLock {
        val day = date.toString()
        val before = snapshot(date)
        val item = before.items.firstOrNull { it.quest.id == questId } ?: return null
        if (item.cleared) return null
        val xp = Rules.unitXp(item.quest.xp, item.quest.target, item.done + 1, before.finishedStreak)
        logs.insert(LogEntity(questId = questId, date = day, loggedAt = System.currentTimeMillis(), source = source, xp = xp))

        var bonus = 0
        if (before.comeback && before.items.all { it.done == 0 }) {
            xpEvents.insertIfAbsent(XpEventEntity(day, KIND_COMEBACK, Rules.COMEBACK_XP))
            bonus += Rules.COMEBACK_XP
        }
        if (!before.eval.gold && snapshot(date).eval.gold) {
            val clear = Rules.scaled(Rules.CLEAR_BONUS_XP, before.finishedStreak)
            xpEvents.insertIfAbsent(XpEventEntity(day, KIND_CLEAR, clear))
            bonus += clear
        }
        val after = snapshot(date)
        LogOutcome(after.items.first { it.quest.id == questId }, before, after, bonus)
    }

    /** Takes back the last tick of a quest, and any bonus that tick had unlocked. */
    suspend fun undoLast(questId: String, date: LocalDate = LocalDate.now()) = writes.withLock {
        val day = date.toString()
        logs.deleteLast(questId, day)
        val after = snapshot(date)
        if (!after.eval.gold) xpEvents.delete(day, KIND_CLEAR)
        if (after.items.all { it.done == 0 }) xpEvents.delete(day, KIND_COMEBACK)
    }

    /**
     * Writes a record for every finished day that does not have one yet, oldest first.
     * It also freezes each day's plan, so later edits never rewrite history.
     */
    suspend fun rollover(today: LocalDate = LocalDate.now()) = writes.withLock {
        val first = plans.earliestDate()?.let { LocalDate.parse(it) } ?: return@withLock
        val records = dayRecords.all().toDomain().toMutableList()
        var day = records.lastOrNull()?.date?.plusDays(1) ?: first
        val allQuests = quests.all().associateBy { it.id }
        while (day.isBefore(today)) {
            val key = day.toString()
            val plan = plans.byDate(key) ?: plans.latestBefore(key)?.copy(date = key, confirmed = false)?.also { plans.upsert(it) }
            val dayLogs = logs.on(key)
            // A quest deleted since then only counts for days it was actually ticked on.
            val progress = plan?.ids().orEmpty().mapNotNull { allQuests[it] }
                .filter { q -> !q.archived || dayLogs.any { it.questId == q.id } }
                .map { q -> QuestProgress(q.target, dayLogs.count { it.questId == q.id }) }
            val eval = Rules.evaluate(progress)
            val record = DayRecord(day, Rules.finalizeStatus(eval, day, records))
            dayRecords.upsert(DayRecordEntity(key, record.status, eval.cleared, eval.total))
            records += record
            day = day.plusDays(1)
        }
    }

    private fun build(
        date: LocalDate, library: List<QuestEntity>, logs: List<LogEntity>,
        plan: PlanEntity?, previous: PlanEntity?, records: List<DayRecord>, totalXp: Int,
    ): TodaySnapshot {
        val ids = (plan ?: previous)?.ids().orEmpty().toSet()
        val items = library.filter { it.id in ids }.map { q -> QuestItem(q, logs.count { it.questId == q.id }) }
        val state = Rules.streak(records)
        return TodaySnapshot(
            date = date,
            library = library,
            items = items,
            planConfirmed = plan?.confirmed == true,
            eval = Rules.evaluate(items.map { QuestProgress(it.quest.target, it.done) }),
            records = records,
            finishedStreak = state.current,
            earnBack = state.earnBack?.takeIf { !date.isAfter(it.deadline) },
            restTokensLeft = Rules.restTokensLeft(date, records),
            totalXp = totalXp,
            comeback = Rules.isComeback(records),
        )
    }

    private fun starter(name: String, cue: String, unit: String, target: Int, xp: Int, times: String) = QuestEntity(
        id = UUID.randomUUID().toString(), name = name, cue = cue, unit = unit, target = target, xp = xp,
        reminderTimes = times, ringing = false, sortOrder = 0,
    )

    private fun List<DayRecordEntity>.toDomain() = map { DayRecord(LocalDate.parse(it.date), it.status) }

    private companion object {
        const val KIND_CLEAR = "clear"
        const val KIND_COMEBACK = "comeback"
    }
}
