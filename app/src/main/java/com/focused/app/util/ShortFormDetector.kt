package com.focused.app.util

/**
 * ShortFormDetector
 *
 * Detects when the user has navigated to short-form content feeds
 * within an app — specifically YouTube Shorts and Instagram Reels.
 *
 * Uses AccessibilityEvent class names and window titles to identify
 * the specific content type, not just the host app.
 *
 * This is the core detection that makes FocusED different from a
 * plain app timer — we care about Reels specifically, not all of Instagram.
 */
object ShortFormDetector {

    /**
     * Returns the content type label if this event represents a short-form feed,
     * or null if it's just normal app usage.
     *
     * Called on every TYPE_WINDOW_STATE_CHANGED event.
     */
    fun detectShortForm(packageName: String, className: String?, windowTitle: String?): ContentType? {
        val cls = className ?: ""
        val title = windowTitle?.lowercase() ?: ""

        return when (packageName) {
            "com.google.android.youtube" -> {
                if (cls.contains("Shorts", ignoreCase = true) ||
                    cls.contains("shorts", ignoreCase = true) ||
                    title.contains("shorts")) {
                    ContentType.YOUTUBE_SHORTS
                } else null
            }
            "com.instagram.android" -> {
                if (cls.contains("ReelViewer", ignoreCase = true) ||
                    cls.contains("Reels", ignoreCase = true) ||
                    cls.contains("ClipsView", ignoreCase = true) ||
                    cls.contains("IgReels", ignoreCase = true)) {
                    ContentType.INSTAGRAM_REELS
                } else null
            }
            "com.zhiliaoapp.musically" -> {
                // TikTok — the entire app is short-form
                ContentType.TIKTOK_FEED
            }
            "com.reddit.frontpage" -> {
                if (cls.contains("Video", ignoreCase = true) ||
                    title.contains("video")) {
                    ContentType.REDDIT_VIDEO
                } else null
            }
            else -> null
        }
    }

    enum class ContentType(val label: String, val appLabel: String) {
        YOUTUBE_SHORTS("Shorts", "YouTube"),
        INSTAGRAM_REELS("Reels", "Instagram"),
        TIKTOK_FEED("Feed", "TikTok"),
        REDDIT_VIDEO("Video feed", "Reddit")
    }
}
