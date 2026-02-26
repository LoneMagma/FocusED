package com.focused.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    // ── Session state ──────────────────────────────────────────────────────────
    // Counts how many reel attempts have happened since Instagram was last opened.
    // Resets to 0 whenever the user leaves Instagram entirely.
    private var reelCount = 0
    private var lastPkg = ""

    // Cooldown so one detection doesn't fire multiple redirects
    private var lastRedirectTime = 0L
    private val COOLDOWN_MS = 2500L

    // Tracks the current Instagram activity class name
    private var currentInstagramActivity = ""

    // ── Safe Instagram screens (never touch these) ─────────────────────────────
    private val INSTAGRAM_SAFE = listOf(
        "MainTabActivity",
        "DirectInbox",
        "DirectThread",
        "ProfileActivity",
        "ExploreActivity",
        "StoriesViewer",
        "StoryViewer",
        "LoginActivity",
        "SignupActivity"
    )

    // ── Confirmed reel screen patterns ────────────────────────────────────────
    private val INSTAGRAM_REELS = listOf(
        "reel",
        "clip"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // Session reset — user left Instagram, wipe the reel count
        if (lastPkg == "com.instagram.android" && pkg != "com.instagram.android") {
            reelCount = 0
            currentInstagramActivity = ""
        }
        lastPkg = pkg

        // ── Instagram ─────────────────────────────────────────────────────────
        if (pkg == "com.instagram.android") {
            // Only act on real screen changes — ignore content updates entirely.
            // This is the single most important change: content_changed fires
            // dozens of times per second and caused all the back-button spam.
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

            // Update the current screen name
            currentInstagramActivity = event.className?.toString() ?: ""

            checkInstagram()
            return
        }

        // ── YouTube ───────────────────────────────────────────────────────────
        if (pkg == "com.google.android.youtube") {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
            checkYouTube()
        }
    }

    // ── Instagram ─────────────────────────────────────────────────────────────

    private fun checkInstagram() {
        val activityLower = currentInstagramActivity.lowercase()

        // Safe screen — do nothing at all
        if (INSTAGRAM_SAFE.any { activityLower.contains(it.lowercase()) }) return

        // Confirmed reels screen (tab, feed thumbnail, explore reel)
        if (INSTAGRAM_REELS.any { activityLower.contains(it) }) {
            handleReelAttempt()
            return
        }

        // Unknown screen — scan only for nav tab selection (safe, no Layer 4)
        val root = rootInActiveWindow ?: return
        try {
            if (isReelsTabSelected(root)) handleReelAttempt()
        } finally {
            root.recycle()
        }
    }

    private fun handleReelAttempt() {
        reelCount++

        if (reelCount == 1) {
            // Free pass — first reel of the session, let it go
            return
        }

        // Second attempt onwards — redirect to DMs
        redirectToDMs()
    }

    private fun redirectToDMs() {
        val now = System.currentTimeMillis()
        if (now - lastRedirectTime < COOLDOWN_MS) return
        lastRedirectTime = now

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("instagram://direct-inbox"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Instagram not installed or deep link failed — do nothing
        }
    }

    private fun isReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        root.findAccessibilityNodeInfosByText("Reels").forEach { node ->
            if (node.isSelected) { node.recycle(); return true }
            node.recycle()
        }
        findByContentDesc(root, "reels").forEach { node ->
            if (node.isSelected) { node.recycle(); return true }
            node.recycle()
        }
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
            .forEach { node ->
                if (node.isSelected || node.isChecked) { node.recycle(); return true }
                node.recycle()
            }
        return false
    }

    // ── YouTube ───────────────────────────────────────────────────────────────

    private fun checkYouTube() {
        val root = rootInActiveWindow ?: return
        try {
            if (isShortsOpen(root)) goBack()
        } finally {
            root.recycle()
        }
    }

    private fun isShortsOpen(root: AccessibilityNodeInfo): Boolean {
        root.findAccessibilityNodeInfosByText("Shorts").forEach { node ->
            if (node.isSelected) { node.recycle(); return true }
            node.recycle()
        }
        findByContentDesc(root, "shorts").forEach { node ->
            if (node.isSelected) { node.recycle(); return true }
            node.recycle()
        }
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_pivot_item")
            .forEach { node -> node.recycle(); return true }

        val shortsPlayerIds = listOf(
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/shorts_container",
            "com.google.android.youtube:id/reels_player_overlay"
        )
        shortsPlayerIds.forEach { viewId ->
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes.isNotEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }
        }
        return false
    }

    private fun goBack() {
        val now = System.currentTimeMillis()
        if (now - lastRedirectTime < COOLDOWN_MS) return
        lastRedirectTime = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findByContentDesc(
        node: AccessibilityNodeInfo,
        keyword: String,
        results: MutableList<AccessibilityNodeInfo> = mutableListOf()
    ): List<AccessibilityNodeInfo> {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains(keyword.lowercase())) results.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findByContentDesc(child, keyword, results)
        }
        return results
    }

    override fun onInterrupt() {}
}
