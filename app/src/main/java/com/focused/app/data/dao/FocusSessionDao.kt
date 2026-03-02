package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Update
    suspend fun update(session: FocusSession)

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): FocusSession?

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FocusSession?

    // For dashboard stats — how many completed today
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE dayKey = :dayKey AND status = 'COMPLETED'")
    suspend fun completedToday(dayKey: String): Int

    // Recent sessions for history view
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC LIMIT 20")
    fun getRecentFlow(): Flow<List<FocusSession>>

    @Query("DELETE FROM focus_sessions WHERE startTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
