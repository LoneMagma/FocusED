package com.focused.app.manager

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IntentionGatekeeper
 *
 * Shows a bottom-card overlay with single-tap intention buttons before
 * a monitored app is allowed full foreground access.
 *
 * Only fires on a genuinely fresh session start (not a resume).
 * Skip logic: if shown for this pkg in the last 5 minutes, skip.
 * Auto-dismisses after 8 seconds if untouched.
 */
class IntentionGatekeeper(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val TAG = "IntentionGate"
        private const val OVERLAY_KEY_PREFIX = "intention_"
        private const val SKIP_WITHIN_MS = 5 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastShownAt = mutableMapOf<String, Long>()

    suspend fun maybeShowGate(pkg: String, appLabel: String) {
        val last = lastShownAt[pkg] ?: 0L
        if (System.currentTimeMillis() - last < SKIP_WITHIN_MS) {
            Log.d(TAG, "Skipping intention gate for $pkg (shown recently)")
            return
        }

        val options = FocusedDatabase.get(context)
            .intentionOptionDao()
            .getForPackage(pkg)

        if (options.isEmpty()) return

        lastShownAt[pkg] = System.currentTimeMillis()

        withContext(Dispatchers.Main) {
            showGate(pkg, appLabel, options.map { it.label })
        }
    }

    private fun showGate(pkg: String, appLabel: String, labels: List<String>) {
        val view = LayoutInflater.from(overlayManager.themedContext)
            .inflate(R.layout.overlay_intention_gate, null)

        view.findViewById<TextView>(R.id.tv_intention_prompt).text =
            "Opening $appLabel for…"

        val container = view.findViewById<LinearLayout>(R.id.container_intentions)
        val dp = context.resources.displayMetrics.density

        labels.forEach { label ->
            val btn = android.widget.Button(context).apply {
                text = label
                textSize = 15f
                setTextColor(context.getColor(R.color.text_primary))
                setBackgroundColor(context.getColor(R.color.bg_card))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (52 * dp).toInt()
                ).apply { bottomMargin = (8 * dp).toInt() }
                setOnClickListener { onIntentionSelected(pkg, label) }
            }
            container.addView(btn)
        }

        view.findViewById<android.widget.Button>(R.id.btn_intention_skip)
            .setOnClickListener {
                overlayManager.dismiss("${OVERLAY_KEY_PREFIX}$pkg")
            }

        overlayManager.show(
            "${OVERLAY_KEY_PREFIX}$pkg",
            view,
            overlayManager.buildBottomCardParams(320)
        )

        view.postDelayed({
            overlayManager.dismiss("${OVERLAY_KEY_PREFIX}$pkg")
        }, 8000L)
    }

    private fun onIntentionSelected(pkg: String, label: String) {
        overlayManager.dismiss("${OVERLAY_KEY_PREFIX}$pkg")
        scope.launch {
            sessionManager.setIntention(pkg, label)
            ActivityLogger.log(context, ActivityLogger.INTENTION_SET, pkg, label)
            Log.d(TAG, "Intention set for $pkg: $label")
        }
    }
}
