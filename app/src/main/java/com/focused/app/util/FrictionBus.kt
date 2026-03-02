package com.focused.app.util

/**
 * FrictionBus
 *
 * Thin communication channel between FrictionUnlockWorker and FrictionManager.
 * When the WorkManager countdown fires, it calls notifyUnlocked().
 * FrictionManager registers a listener to receive that signal and act on it.
 */
object FrictionBus {
    private val listeners = mutableMapOf<String, () -> Unit>()

    fun registerUnlockListener(pkg: String, onUnlocked: () -> Unit) {
        listeners[pkg] = onUnlocked
    }

    fun unregisterUnlockListener(pkg: String) {
        listeners.remove(pkg)
    }

    fun notifyUnlocked(pkg: String) {
        listeners[pkg]?.invoke()
        listeners.remove(pkg)
    }
}
