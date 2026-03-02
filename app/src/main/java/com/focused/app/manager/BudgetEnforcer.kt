package com.focused.app.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.BudgetRule
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import com.focused.app.util.AppEvent
import com.focused.app.util.AppEventBus
import com.focused.app.util.DateUtil
import com.focused.app.util.ShortFormDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class BudgetEnforcer(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val sessionManager: SessionManager,
    private val intentionGatekeeper: IntentionGatekeeper,
    private val frictionManager: FrictionManager,
    private val focusSessionManager: FocusSessionManager,
    private val reflectionManager: ReflectionManager,
    private val grayscaleManager: GrayscaleManager
) {
    companion object {
        private const val TAG = "BudgetEnforcer"
        private const val DURATION_CHECK_INTERVAL_MS = 15_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val durationCheckers = mutableMapOf<String, Runnable>()
    private val foregroundSince = mutableMapOf<String, Long>()
    // Track which packages had their block overridden for reflection
    private val overriddenPackages = mutableSetOf<String>()
    // Track short-form time per package (foreground start of short-form segment)
    private val shortFormStartedAt = mutableMapOf<String, Long>()

    private val listener: (AppEvent) -> Unit = { event ->
        scope.launch { handleEvent(event) }
    }

    fun start() {
        AppEventBus.register(listener)
        Log.d(TAG, "BudgetEnforcer started")
    }

    fun stop() {
        AppEventBus.unregister(listener)
        durationCheckers.values.forEach { mainHandler.removeCallbacks(it) }
        durationCheckers.clear()
        grayscaleManager.removeAll()
    }

    fun pauseEnforcementFor(pkg: String) {
        cancelDurationChecker(pkg)
        overlayManager.dismiss("budget_block_$pkg")
        overlayManager.dismiss("budget_warn_$pkg")
        overriddenPackages.add(pkg)
        reflectionManager.markOverrideStart(pkg)
        Log.d(TAG, "Enforcement paused for $pkg after friction completion")
    }

    // -------------------------------------------------------------------------

    private suspend fun handleEvent(event: AppEvent) {
        // Focus mode: block all monitored apps
        if (focusSessionManager.isActive) {
            val db = FocusedDatabase.get(context)
            val rule = db.budgetRuleDao().getByPackage(event.packageName) ?: return
            when (event.type) {
                AppEvent.Type.APP_FOREGROUND -> showFocusBlockOverlay(event.packageName, rule.appLabel, focusSessionManager.currentTask)
                AppEvent.Type.APP_BACKGROUND -> overlayManager.dismiss("focus_block_${event.packageName}")
                else -> {}
            }
            return
        }

        val db = FocusedDatabase.get(context)
        val rule = db.budgetRuleDao().getByPackage(event.packageName) ?: return

        when (event.type) {
            AppEvent.Type.APP_FOREGROUND -> handleForeground(event, rule, db)
            AppEvent.Type.APP_BACKGROUND -> handleBackground(event, rule)
            else -> {}
        }
    }

    private suspend fun handleForeground(event: AppEvent, rule: BudgetRule, db: FocusedDatabase) {
        val pkg = event.packageName
        foregroundSince[pkg] = event.timestamp

        // 1. DOWNTIME CHECK
        if (rule.isInDowntime()) {
            showDowntimeOverlay(pkg, rule.appLabel, rule.downtimeEndMin)
            ActivityLogger.log(context, ActivityLogger.APP_BLOCKED, pkg, "Downtime window")
            return
        }

        // 2. OPEN COUNT LIMIT
        if (rule.maxOpensPerDay > 0) {
            val opensToday = db.appSessionDao().opensTodayCount(pkg, DateUtil.todayKey())
            if (opensToday >= rule.maxOpensPerDay) {
                showBlockOverlay(
                    pkg = pkg,
                    appLabel = rule.appLabel,
                    reason = "You've opened ${rule.appLabel} ${rule.maxOpensPerDay} times today.",
                    subText = "That's your limit. Opens reset at midnight.",
                    showFriction = true
                )
                ActivityLogger.log(context, ActivityLogger.APP_BLOCKED, pkg, "Open count limit: ${rule.maxOpensPerDay}")
                return
            }
        }

        // 3. SHORT-FORM CONTENT LIMIT
        if (rule.shortFormLimitMs > 0 && event.contentType != null) {
            val shortFormToday = db.appSessionDao().totalShortFormTodayMs(pkg, DateUtil.todayKey())
            if (shortFormToday >= rule.shortFormLimitMs) {
                showBlockOverlay(
                    pkg = pkg,
                    appLabel = rule.appLabel,
                    reason = "Your ${event.contentType.label} limit is up.",
                    subText = "${rule.appLabel} is still open — just not ${event.contentType.label}.",
                    showFriction = true
                )
                ActivityLogger.log(context, ActivityLogger.APP_BLOCKED, pkg, "Short-form limit: ${event.contentType.label}")
                return
            }
            shortFormStartedAt[pkg] = System.currentTimeMillis()
        }

        // 4. DAILY BUDGET CHECK
        val usedToday = sessionManager.totalUsageTodayMs(pkg)
        if (usedToday >= rule.maxSessionDurationMs) {
            showBlockOverlay(
                pkg = pkg,
                appLabel = rule.appLabel,
                reason = "You've used your ${DateUtil.formatMinutes(rule.maxSessionDurationMs)} of ${rule.appLabel} today.",
                subText = "Sessions reset at midnight.",
                showResetTime = true,
                showFriction = true
            )
            ActivityLogger.log(context, ActivityLogger.APP_BLOCKED, pkg, "Daily limit: ${DateUtil.formatDuration(rule.maxSessionDurationMs)}")
            return
        }

        val (sessionNumber, isResume) = sessionManager.openSession(pkg)
        Log.d(TAG, "$pkg session $sessionNumber resume=$isResume")

        // 5. INTENTION GATE
        if (rule.intentionEnabled && !isResume) {
            intentionGatekeeper.maybeShowGate(pkg, rule.appLabel)
        }

        // 6. DURATION CHECKER (enforces daily budget + grayscale nudge)
        val remaining = rule.maxSessionDurationMs - usedToday
        startDurationChecker(pkg, rule.appLabel, rule.maxSessionDurationMs, remaining, rule)
    }

    private suspend fun handleBackground(event: AppEvent, rule: BudgetRule) {
        val pkg = event.packageName
        val duration = foregroundSince.remove(pkg)?.let { System.currentTimeMillis() - it } ?: 0L

        // Accumulate short-form time into the current session
        shortFormStartedAt.remove(pkg)?.let { start ->
            val shortFormElapsed = System.currentTimeMillis() - start
            val db = FocusedDatabase.get(context)
            val session = db.appSessionDao().getOpenSession(pkg)
            if (session != null) {
                session.shortFormMs += shortFormElapsed
                db.appSessionDao().update(session)
            }
        }

        cancelDurationChecker(pkg)
        sessionManager.pauseSession(pkg, duration)
        grayscaleManager.removeTint(pkg)
        overlayManager.dismiss("budget_warn_$pkg")

        // Reflection card if they used the app after overriding
        if (overriddenPackages.remove(pkg)) {
            reflectionManager.onAppExitAfterOverride(pkg, rule.appLabel)
        }
    }

    // -------------------------------------------------------------------------
    // Duration checking
    // -------------------------------------------------------------------------

    private fun startDurationChecker(
        pkg: String, appLabel: String, maxMs: Long, remainingMs: Long, rule: BudgetRule
    ) {
        cancelDurationChecker(pkg)
        lateinit var checker: Runnable
        checker = Runnable {
            scope.launch {
                val elapsed = sessionManager.totalUsageTodayMs(pkg)
                val pct = elapsed.toFloat() / maxMs.toFloat()
                when {
                    pct >= 1.0f -> {
                        cancelDurationChecker(pkg)
                        grayscaleManager.removeTint(pkg)
                        showBlockOverlay(pkg, appLabel,
                            reason = "You've used your ${DateUtil.formatMinutes(maxMs)} of $appLabel today.",
                            subText = "Sessions reset at midnight.",
                            showResetTime = true, showFriction = true)
                        ActivityLogger.log(context, ActivityLogger.APP_BLOCKED, pkg,
                            "Duration limit: ${DateUtil.formatDuration(maxMs)}")
                    }
                    pct >= 0.8f -> {
                        // Grayscale nudge at 80%
                        grayscaleManager.showTint(pkg)
                        val remaining = maxMs - elapsed
                        showWarningBanner(pkg, appLabel, remaining)
                        mainHandler.postDelayed(checker, DURATION_CHECK_INTERVAL_MS)
                    }
                    else -> mainHandler.postDelayed(checker, DURATION_CHECK_INTERVAL_MS)
                }
            }
        }
        durationCheckers[pkg] = checker
        mainHandler.postDelayed(checker, DURATION_CHECK_INTERVAL_MS)
    }

    private fun cancelDurationChecker(pkg: String) {
        durationCheckers.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
    }

    // -------------------------------------------------------------------------
    // Overlays
    // -------------------------------------------------------------------------

    private suspend fun showFocusBlockOverlay(pkg: String, appLabel: String, task: String) =
        withContext(Dispatchers.Main) {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_focus_blocked, null)
            view.findViewById<TextView>(R.id.tv_focus_app_label).text = appLabel
            view.findViewById<TextView>(R.id.tv_focus_task).text = "\"$task\""
            val remaining = DateUtil.formatMinutes(focusSessionManager.remainingMs())
            view.findViewById<TextView>(R.id.tv_focus_remaining).text = "$remaining left in focus"
            view.findViewById<Button>(R.id.btn_end_focus)
                .setOnClickListener { focusSessionManager.end(completed = false) }
            overlayManager.show("focus_block_$pkg", view, overlayManager.buildFullScreenParams())
        }

    private suspend fun showBlockOverlay(
        pkg: String, appLabel: String, reason: String,
        subText: String = "Your sessions reset at midnight.",
        showResetTime: Boolean = false, showFriction: Boolean = true
    ) = withContext(Dispatchers.Main) {
        val view = LayoutInflater.from(overlayManager.themedContext)
            .inflate(R.layout.overlay_budget_blocked, null)
        view.findViewById<TextView>(R.id.tv_block_reason).text = reason
        view.findViewById<TextView>(R.id.tv_block_sub).text = subText
        view.findViewById<Button>(R.id.btn_override).setOnClickListener {
            if (showFriction) frictionManager.start(pkg)
            else overlayManager.dismiss("budget_block_$pkg")
        }
        overlayManager.show("budget_block_$pkg", view, overlayManager.buildFullScreenParams())
    }

    private suspend fun showDowntimeOverlay(pkg: String, appLabel: String, endMin: Int) =
        withContext(Dispatchers.Main) {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_downtime, null)
            val endHour = endMin / 60
            val endMinute = endMin % 60
            view.findViewById<TextView>(R.id.tv_downtime_app).text =
                "$appLabel is off during your downtime window."
            view.findViewById<TextView>(R.id.tv_downtime_until).text =
                "Until ${String.format("%d:%02d", endHour, endMinute)}"
            view.findViewById<Button>(R.id.btn_downtime_override).setOnClickListener {
                frictionManager.start(pkg)
            }
            overlayManager.show("downtime_$pkg", view, overlayManager.buildFullScreenParams())
        }

    private fun showWarningBanner(pkg: String, appLabel: String, remainingMs: Long) {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_budget_warning, null)
            view.findViewById<TextView>(R.id.tv_warn_text).text =
                "${DateUtil.formatDuration(remainingMs)} left on $appLabel today."
            view.findViewById<android.widget.ImageButton>(R.id.btn_warn_dismiss)
                .setOnClickListener { overlayManager.dismiss("budget_warn_$pkg") }
            overlayManager.show("budget_warn_$pkg", view, overlayManager.buildTopBannerParams(72))
        }
    }
}
