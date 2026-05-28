package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "attendance_entries")
data class AttendanceEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,             // Format: YYYY-MM-DD
    val type: String,             // "In", "Out", "Break Start", "Break End", "Falta"
    val time: String,             // Format: HH:MM
    val latenessMinutes: Int = 0, // user-specified or calculated lateness
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_entries ORDER BY date DESC, time DESC, id DESC")
    fun getAllEntries(): Flow<List<AttendanceEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: AttendanceEntry)

    @Query("DELETE FROM attendance_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)

    @Query("DELETE FROM attendance_entries")
    suspend fun clearAll()
}

@Database(entities = [AttendanceEntry::class], version = 1, exportSchema = false)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao
}
