package com.smallwins.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.smallwins.app.domain.DayStatus
import com.smallwins.app.domain.HabitRule
import com.smallwins.app.domain.ScheduleType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The real-life moment the habit hangs on, e.g. "After lunch". */
    val cue: String,
    val unit: String,
    val schedule: ScheduleType,
    val target: Int,
    val minimum: Int,
    val weeklyDays: Int,
    /** Comma-separated HH:mm reminder times. */
    val reminderTimes: String,
    val ringing: Boolean,
    /** ISO date the habit starts counting from. */
    val startDate: String,
    val sortOrder: Int,
    val archived: Boolean = false,
) {
    fun rule() = HabitRule(id, schedule, target, minimum, weeklyDays, LocalDate.parse(startDate))

    fun times(): List<LocalTime> =
        reminderTimes.split(',').filter { it.isNotBlank() }.map { LocalTime.parse(it) }
}

@Entity(tableName = "logs", indices = [Index("date"), Index("habitId")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val date: String,
    val loggedAt: Long,
    val source: String,
)

@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val date: String,
    val status: DayStatus,
    val score: Int,
)

/** One row per reminder the app asked Android to deliver, so swallowed alarms can be spotted. */
@Entity(tableName = "alarm_events", indices = [Index("scheduledAt")])
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val slot: Int,
    val scheduledAt: Long,
    val firedAt: Long? = null,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, name")
    suspend fun active(): List<HabitEntity>

    @Query("SELECT * FROM habits ORDER BY sortOrder, name")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: String): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("UPDATE habits SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM logs WHERE date >= :from")
    fun observeFrom(from: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE date >= :from AND date <= :to")
    suspend fun between(from: String, to: String): List<LogEntity>

    @Query("DELETE FROM logs WHERE id = (SELECT id FROM logs WHERE habitId = :habitId AND date = :date ORDER BY id DESC LIMIT 1)")
    suspend fun deleteLast(habitId: String, date: String)
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

    @Query("UPDATE alarm_events SET firedAt = :firedAt WHERE habitId = :habitId AND slot = :slot AND scheduledAt = :scheduledAt")
    suspend fun markFired(habitId: String, slot: Int, scheduledAt: Long, firedAt: Long)

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
    entities = [HabitEntity::class, LogEntity::class, DayRecordEntity::class, AlarmEventEntity::class],
    version = 1,
)
abstract class AppDb : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun logs(): LogDao
    abstract fun dayRecords(): DayRecordDao
    abstract fun alarmEvents(): AlarmEventDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "smallwins.db").build()
    }
}
