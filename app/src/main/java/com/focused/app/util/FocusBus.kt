package com.focused.app.util

/**
 * FocusBus
 *
 * Simple signal channel between FocusEndWorker (runs off main thread via WorkManager)
 * and FocusSessionManager (lives in the AccessibilityService).
 */
object FocusBus {
    private var listener: (() -> Unit)? = null

    fun registerCompleteListener(l: () -> Unit) { listener = l }
    fun unregisterCompleteListener()             { listener = null }
    fun notifyComplete()                         { listener?.invoke() }
}
