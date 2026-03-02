package com.focused.app.util

import java.util.concurrent.CopyOnWriteArrayList

data class AppEvent(
    val type: Type,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val contentType: com.focused.app.util.ShortFormDetector.ContentType? = null
) {
    enum class Type {
        APP_FOREGROUND,
        APP_BACKGROUND,
        SCROLL_DETECTED,
    }
}

object AppEventBus {
    // CopyOnWriteArrayList is thread-safe for concurrent register/emit/unregister
    private val listeners = CopyOnWriteArrayList<(AppEvent) -> Unit>()

    fun register(listener: (AppEvent) -> Unit) {
        listeners.add(listener)
    }

    fun unregister(listener: (AppEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(event: AppEvent) {
        listeners.forEach { it(event) }
    }
}
