package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Recorded after user overrides a block and then exits the app */
@Entity(tableName = "reflection_records")
data class ReflectionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val dayKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val minutesSpent: Int = 0,      // how long in the app after override
    val wasWorthIt: Boolean? = null // null = dismissed without answering
)
