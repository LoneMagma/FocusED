package com.focused.app.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * DateUtil
 * Centralises all date/time helpers used across managers.
 */
object DateUtil {

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** Returns today's day key, e.g. "2025-03-15" */
    fun todayKey(): String = dayKeyFormat.format(Date())

    /** Returns the epoch ms of midnight today (local timezone) */
    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Default intentions per app — inserted when a BudgetRule is created */
    fun defaultIntentionsFor(packageName: String): List<String> = when (packageName) {
        "com.instagram.android"       -> listOf("Check DMs", "Catch up", "Watch saved", "Quick browse")
        "com.zhiliaoapp.musically"    -> listOf("Watch saved", "Quick browse", "Post something")
        "com.google.android.youtube"  -> listOf("Watch saved", "Search something specific", "Quick browse")
        "com.twitter.android"         -> listOf("Check news", "Check notifications", "Quick browse")
        "com.snapchat.android"        -> listOf("Check snaps", "Message friends")
        "com.reddit.frontpage"        -> listOf("Check a specific sub", "Quick browse")
        "com.facebook.katana"         -> listOf("Check notifications", "Message friends", "Quick browse")
        else                          -> listOf("Quick check", "Something specific", "Browse")
    }

    /** Human-readable app name from package name */
    fun appLabel(packageName: String): String = when (packageName) {
        "com.instagram.android"       -> "Instagram"
        "com.zhiliaoapp.musically"    -> "TikTok"
        "com.google.android.youtube"  -> "YouTube"
        "com.twitter.android"         -> "X (Twitter)"
        "com.snapchat.android"        -> "Snapchat"
        "com.reddit.frontpage"        -> "Reddit"
        "com.facebook.katana"         -> "Facebook"
        else                          -> packageName.substringAfterLast(".")
            .replaceFirstChar { it.uppercase() }
    }

    /** Format ms duration as "4m 32s" */
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    /** Format ms as "8 minutes" for overlay messages */
    fun formatMinutes(ms: Long): String {
        val min = ms / 60000
        return if (min == 1L) "1 minute" else "$min minutes"
    }
}
