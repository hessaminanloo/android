package com.hess.meetmate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val title: String,
    val participants: String,
    val notes: String = "",
    val transcript: String = "",
    val summary: String = "",
    val tasks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val audioPath: String? = null,
    val agents: String = "Summary,Task extraction"
)

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC") fun observeAll(): Flow<List<MeetingEntity>>
    @Query("SELECT * FROM meetings WHERE id = :id") suspend fun get(id: Long): MeetingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(meeting: MeetingEntity)
    @Delete suspend fun delete(meeting: MeetingEntity)
}

@Database(entities = [MeetingEntity::class], version = 1, exportSchema = false)
abstract class MeetMateDatabase : RoomDatabase() { abstract fun meetings(): MeetingDao }
