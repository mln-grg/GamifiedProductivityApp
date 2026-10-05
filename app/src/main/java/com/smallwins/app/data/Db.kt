package com.smallwins.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.smallwins.app.domain.DayStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** A task in the player's own list. Which ones count today is decided by that day's plan. */
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The real-life moment the quest hangs on, e.g. "After lunch". */
    val cue: String,
    val unit: String,
    /** Times a day it has to be ticked to be cleared. */
    val target: Int,
    /** XP for clearing it, before the streak bonus. */
    val xp: Int,
    /** Comma-separated HH:mm reminder times. */
    val reminderTimes: String,
    val ringing: Boolean,
    val sortOrder: Int,
    val archived: Boolean = false,
) {
    fun times(): List<LocalTime> =
        reminderTimes.split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it) }
}

@Entity(tableName = "logs", indices = [Index("date"), Index("questId")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: String,
    val date: String,
    val loggedAt: Long,
    val source: String,
    /** XP this tick paid, streak bonus included, so undoing it takes back exactly that. */
    val xp: Int,
)

/** The quests picked for one day. Days without a row fall back to the most recent earlier plan. */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val date: String,
    /** Comma-separated quest ids. */
    val questIds: String,
    /** False when the plan was carried over without the player choosing it. */
    val confirmed: Boolean,
) {
    fun ids(): List<String> = questIds.split(',').filter { it.isNotBlank() }
}

/** XP that does not come from a tick: the full-clear bonus and the comeback bonus. */
@Entity(tableName = "xp_events", primaryKeys = ["date", "kind"])
data class XpEventEntity(val date: String, val kind: String, val xp: Int)

@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val date: String,
    val status: DayStatus,
    val cleared: Int,
    val total: Int,
)

/** One row per reminder the app asked Android to deliver, so swallowed alarms can be spotted. */
@Entity(tableName = "alarm_events", indices = [Index("scheduledAt")])
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: String,
    val slot: Int,
    val scheduledAt: Long,
    val firedAt: Long? = null,
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE archived = 0 ORDER BY sortOrder, name")
    suspend fun active(): List<QuestEntity>

    @Query("SELECT * FROM quests")
    suspend fun all(): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun byId(id: String): QuestEntity?

    @Upsert
    suspend fun upsert(quest: QuestEntity)

    @Query("UPDATE quests SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM logs WHERE date = :date")
    fun observeOn(date: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE date = :date")
    suspend fun on(date: String): List<LogEntity>

    @Query("DELETE FROM logs WHERE id = (SELECT id FROM logs WHERE questId = :questId AND date = :date ORDER BY id DESC LIMIT 1)")
    suspend fun deleteLast(questId: String, date: String)

    @Query("SELECT COALESCE(SUM(xp), 0) FROM logs")
    fun observeTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(xp), 0) FROM logs")
    suspend fun totalXp(): Int
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE date = :date")
    fun observe(date: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE date = :date")
    suspend fun byDate(date: String): PlanEntity?

    @Query("SELECT * FROM plans WHERE date < :date ORDER BY date DESC LIMIT 1")
    fun observeLatestBefore(date: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun latestBefore(date: String): PlanEntity?

    @Query("SELECT MIN(date) FROM plans")
    suspend fun earliestDate(): String?

    @Upsert
    suspend fun upsert(plan: PlanEntity)
}

@Dao
interface XpEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: XpEventEntity)

    @Query("DELETE FROM xp_events WHERE date = :date AND kind = :kind")
    suspend fun delete(date: String, kind: String)

    @Query("SELECT COALESCE(SUM(xp), 0) FROM xp_events")
    fun observeTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(xp), 0) FROM xp_events")
    suspend fun totalXp(): Int
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_records ORDER BY date")
    fun observeAll(): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_records ORDER BY date")
    suspend fun all(): List<DayRecordEntity>

    @Upsert
    suspend fun upsert(record: DayRecordEntity)
}

@Dao
interface AlarmEventDao {
    @Insert
    suspend fun insert(event: AlarmEventEntity)

    @Query("UPDATE alarm_events SET firedAt = :firedAt WHERE questId = :questId AND slot = :slot AND scheduledAt = :scheduledAt")
    suspend fun markFired(questId: String, slot: Int, scheduledAt: Long, firedAt: Long)

    @Query("DELETE FROM alarm_events WHERE firedAt IS NULL AND scheduledAt > :now")
    suspend fun deletePending(now: Long)

    @Query("DELETE FROM alarm_events WHERE scheduledAt < :before")
    suspend fun deleteOlderThan(before: Long)

    /** Reminders that never fired, or fired more than [lateMs] after they were due. */
    @Query(
        "SELECT COUNT(*) FROM alarm_events WHERE scheduledAt > :since AND " +
            "((firedAt IS NULL AND scheduledAt < :now - :lateMs) OR (firedAt IS NOT NULL AND firedAt - scheduledAt > :lateMs))"
    )
    fun observeMissed(since: Long, now: Long, lateMs: Long): Flow<Int>
}

@Database(
    entities = [QuestEntity::class, LogEntity::class, PlanEntity::class, XpEventEntity::class, DayRecordEntity::class, AlarmEventEntity::class],
    version = 1,
)
abstract class AppDb : RoomDatabase() {
    abstract fun quests(): QuestDao
    abstract fun logs(): LogDao
    abstract fun plans(): PlanDao
    abstract fun xpEvents(): XpEventDao
    abstract fun dayRecords(): DayRecordDao
    abstract fun alarmEvents(): AlarmEventDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "smallwins.db").build()
    }
}
