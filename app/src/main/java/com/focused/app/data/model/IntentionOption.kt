package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * IntentionOption
 *
 * One row per chip label the user has defined for a given app.
 * E.g. for Instagram: "Check DMs", "Catch up", "Watch saved".
 *
 * When the intention gate fires, it loads all options for that packageName
 * and displays them as tappable chips. One tap = done. No typing.
 *
 * Default options are inserted when a BudgetRule is first created.
 * Users can add/remove options from the app management screen.
 *
 * sortOrder: determines display order on the overlay.
 */
@Entity(tableName = "intention_options")
data class IntentionOption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val packageName: String,

    val label: String,

    val sortOrder: Int = 0,

    val isActive: Boolean = true
)
