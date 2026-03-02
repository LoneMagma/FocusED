package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.OnboardingState

@Dao
interface OnboardingStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: OnboardingState)

    @Query("SELECT * FROM onboarding_state WHERE id = 1")
    suspend fun get(): OnboardingState?

    @Query("UPDATE onboarding_state SET onboardingComplete = 1, lastCompletedScreen = 5 WHERE id = 1")
    suspend fun markComplete()

    @Query("UPDATE onboarding_state SET compactAccepted = 1, compactAcceptedAt = :timestamp WHERE id = 1")
    suspend fun acceptCompact(timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE onboarding_state SET lastCompletedScreen = :screen WHERE id = 1")
    suspend fun saveProgress(screen: Int)
}
