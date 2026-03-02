package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.ReflectionRecord

@Dao
interface ReflectionRecordDao {
    @Insert
    suspend fun insert(record: ReflectionRecord): Long

    @Update
    suspend fun update(record: ReflectionRecord)

    @Query("SELECT * FROM reflection_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReflectionRecord?

    @Query("SELECT COUNT(*) FROM reflection_records WHERE dayKey = :dayKey AND wasWorthIt = 0")
    suspend fun regretsToday(dayKey: String): Int

    @Query("SELECT COUNT(*) FROM reflection_records WHERE wasWorthIt IS NOT NULL")
    suspend fun totalAnswered(): Int

    @Query("SELECT COUNT(*) FROM reflection_records WHERE wasWorthIt = 1")
    suspend fun totalWorthIt(): Int

    /** Streak: consecutive days with zero regret overrides. Returns list of dayKeys. */
    @Query("SELECT DISTINCT dayKey FROM reflection_records WHERE wasWorthIt = 0 ORDER BY dayKey DESC")
    suspend fun daysWithRegret(): List<String>

    @Query("DELETE FROM reflection_records WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
