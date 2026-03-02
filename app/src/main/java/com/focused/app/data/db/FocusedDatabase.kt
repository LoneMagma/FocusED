package com.focused.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focused.app.data.dao.*
import com.focused.app.data.model.*

@Database(
    entities = [
        ActivityLog::class,
        OnboardingState::class,
        BudgetRule::class,
        AppSession::class,
        IntentionOption::class,
        FrictionAttempt::class,
        FocusSession::class,
        ReflectionRecord::class
    ],
    version = 6,
    exportSchema = true
)
abstract class FocusedDatabase : RoomDatabase() {

    abstract fun activityLogDao(): ActivityLogDao
    abstract fun onboardingStateDao(): OnboardingStateDao
    abstract fun budgetRuleDao(): BudgetRuleDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun intentionOptionDao(): IntentionOptionDao
    abstract fun frictionAttemptDao(): FrictionAttemptDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun reflectionRecordDao(): ReflectionRecordDao

    companion object {
        @Volatile private var INSTANCE: FocusedDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS budget_rules (packageName TEXT NOT NULL PRIMARY KEY, appLabel TEXT NOT NULL, maxSessionsPerDay INTEGER NOT NULL DEFAULT 3, maxSessionDurationMs INTEGER NOT NULL DEFAULT 600000, intentionEnabled INTEGER NOT NULL DEFAULT 1, isActive INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageName TEXT NOT NULL, sessionStart INTEGER NOT NULL, sessionEnd INTEGER, pausedAt INTEGER, status TEXT NOT NULL DEFAULT 'ACTIVE', sessionNumber INTEGER NOT NULL DEFAULT 1, dayKey TEXT NOT NULL DEFAULT '', intentionLabel TEXT, durationMs INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS intention_options (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageName TEXT NOT NULL, label TEXT NOT NULL, sortOrder INTEGER NOT NULL DEFAULT 0, isActive INTEGER NOT NULL DEFAULT 1)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS friction_attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestamp INTEGER NOT NULL, packageName TEXT, tiersReached INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, dayKey TEXT NOT NULL DEFAULT '')")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskLabel TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER, plannedDurationMs INTEGER NOT NULL, actualDurationMs INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'ACTIVE', dayKey TEXT NOT NULL DEFAULT '')")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_sessions ADD COLUMN resumedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // BudgetRule new columns
                db.execSQL("ALTER TABLE budget_rules ADD COLUMN maxOpensPerDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE budget_rules ADD COLUMN shortFormLimitMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE budget_rules ADD COLUMN downtimeStartMin INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE budget_rules ADD COLUMN downtimeEndMin INTEGER NOT NULL DEFAULT -1")
                // AppSession shortFormMs
                db.execSQL("ALTER TABLE app_sessions ADD COLUMN shortFormMs INTEGER NOT NULL DEFAULT 0")
                // Reflection records table
                db.execSQL("CREATE TABLE IF NOT EXISTS reflection_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, packageName TEXT NOT NULL, dayKey TEXT NOT NULL, timestamp INTEGER NOT NULL, minutesSpent INTEGER NOT NULL DEFAULT 0, wasWorthIt INTEGER)")
            }
        }

        fun get(context: Context): FocusedDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, FocusedDatabase::class.java, "focused.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
