package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.IntentionOption
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentionOptionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(option: IntentionOption)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(options: List<IntentionOption>)

    @Delete
    suspend fun delete(option: IntentionOption)

    @Query("SELECT * FROM intention_options WHERE packageName = :pkg AND isActive = 1 ORDER BY sortOrder")
    suspend fun getForPackage(pkg: String): List<IntentionOption>

    @Query("SELECT * FROM intention_options WHERE packageName = :pkg AND isActive = 1 ORDER BY sortOrder")
    fun getForPackageFlow(pkg: String): Flow<List<IntentionOption>>

    @Query("DELETE FROM intention_options WHERE packageName = :pkg")
    suspend fun deleteAllForPackage(pkg: String)
}
