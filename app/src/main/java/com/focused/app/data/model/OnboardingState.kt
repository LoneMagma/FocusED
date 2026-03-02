package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OnboardingState
 *
 * Single-row table. Tracks where the user is in the onboarding flow
 * and whether they have completed it and accepted the compact.
 *
 * If onboardingComplete = false on app launch, MainActivity routes
 * to OnboardingActivity before anything else.
 */
@Entity(tableName = "onboarding_state")
data class OnboardingState(
    @PrimaryKey
    val id: Int = 1,                     // always 1 — single row

    val onboardingComplete: Boolean = false,

    val compactAccepted: Boolean = false,

    val compactAcceptedAt: Long? = null,

    val lastCompletedScreen: Int = 0     // 0–4, for resume-on-reopen
)
