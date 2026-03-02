package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.BudgetRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BudgetRule)

    @Update
    suspend fun update(rule: BudgetRule)

    @Delete
    suspend fun delete(rule: BudgetRule)

    @Query("SELECT * FROM budget_rules WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): BudgetRule?

    @Query("SELECT * FROM budget_rules WHERE isActive = 1")
    fun getAllActiveFlow(): Flow<List<BudgetRule>>

    @Query("SELECT * FROM budget_rules WHERE isActive = 1")
    suspend fun getAllActive(): List<BudgetRule>

    @Query("SELECT * FROM budget_rules")
    suspend fun getAll(): List<BudgetRule>

    // Fast lookup — called on every foreground event
    @Query("SELECT packageName FROM budget_rules WHERE isActive = 1")
    suspend fun getMonitoredPackages(): List<String>

    @Query("UPDATE budget_rules SET isActive = :active WHERE packageName = :pkg")
    suspend fun setActive(pkg: String, active: Boolean)
}
