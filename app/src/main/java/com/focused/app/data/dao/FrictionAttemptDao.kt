package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.FrictionAttempt

@Dao
interface FrictionAttemptDao {

    @Insert
    suspend fun insert(attempt: FrictionAttempt): Long

    @Update
    suspend fun update(attempt: FrictionAttempt)

    @Query("SELECT * FROM friction_attempts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FrictionAttempt?

    // How many attempts today — shown as self-reflection in dashboard
    @Query("SELECT COUNT(*) FROM friction_attempts WHERE dayKey = :dayKey")
    suspend fun attemptsToday(dayKey: String): Int

    // How many completed today (actually disabled Focused)
    @Query("SELECT COUNT(*) FROM friction_attempts WHERE dayKey = :dayKey AND completed = 1")
    suspend fun completedToday(dayKey: String): Int

    // Clean up records older than 30 days
    @Query("DELETE FROM friction_attempts WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
