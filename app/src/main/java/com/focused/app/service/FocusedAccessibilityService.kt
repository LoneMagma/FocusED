package com.focused.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focused.app.manager.*
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import com.focused.app.util.AppEvent
import com.focused.app.util.AppEventBus
import com.focused.app.util.ShortFormDetector
import com.focused.app.work.MidnightResetWorker

class FocusedAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusedA11y"
        @Volatile var currentForegroundPackage: String = ""; private set
        var instance: FocusedAccessibilityService? = null; private set
    }

    private lateinit var overlayManager: OverlayManager
    private lateinit var sessionManager: SessionManager
    private lateinit var intentionGatekeeper: IntentionGatekeeper
    private lateinit var frictionManager: FrictionManager
    private lateinit var focusSessionManager: FocusSessionManager
    private lateinit var focusBannerManager: FocusBannerManager
    private lateinit var budgetEnforcer: BudgetEnforcer
    private lateinit var scrollTracker: ScrollTracker
    private lateinit var reflectionManager: ReflectionManager
    private lateinit var grayscaleManager: GrayscaleManager
    private var lastWindowPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        serviceInfo = serviceInfo.apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            notificationTimeout = 100L
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        overlayManager      = OverlayManager(this)
        focusBannerManager  = FocusBannerManager(this, overlayManager)
        sessionManager      = SessionManager(this)
        intentionGatekeeper = IntentionGatekeeper(this, overlayManager, sessionManager)
        focusSessionManager = FocusSessionManager(this)
        reflectionManager   = ReflectionManager(this, overlayManager)
        grayscaleManager    = GrayscaleManager(this, overlayManager)

        frictionManager = FrictionManager(this, overlayManager) { disabledPkg ->
            budgetEnforcer.pauseEnforcementFor(disabledPkg)
        }
        budgetEnforcer = BudgetEnforcer(
            this, overlayManager, sessionManager, intentionGatekeeper,
            frictionManager, focusSessionManager, reflectionManager, grayscaleManager
        )

        focusSessionManager.registerOnStart { task, endsAt -> focusBannerManager.show(task, endsAt) }
        focusSessionManager.registerOnEnd { _ ->
            focusBannerManager.dismiss()
            overlayManager.dismissAll()
            grayscaleManager.removeAll()
        }

        budgetEnforcer.start()
        MidnightResetWorker.schedule(this)
        scrollTracker = ScrollTracker(this, overlayManager, focusSessionManager)
        scrollTracker.start()

        instance = this
        FocusedForegroundService.start(this)
        ActivityLogger.log(this, ActivityLogger.SERVICE_STARTED)
    }

    override fun onInterrupt() { Log.w(TAG, "Interrupted") }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        budgetEnforcer.stop()
        scrollTracker.stop()
        overlayManager.dismissAll()
        grayscaleManager.removeAll()
        ActivityLogger.log(this, ActivityLogger.SERVICE_STOPPED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowChange(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED        -> handleScroll(event)
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg == lastWindowPackage) return

        if (lastWindowPackage.isNotEmpty()) {
            AppEventBus.emit(AppEvent(type = AppEvent.Type.APP_BACKGROUND, packageName = lastWindowPackage))
        }
        lastWindowPackage = pkg
        currentForegroundPackage = pkg

        val className = event.className?.toString()
        val windowTitle = event.text?.joinToString(" ")
        val contentType = ShortFormDetector.detectShortForm(pkg, className, windowTitle)

        AppEventBus.emit(AppEvent(type = AppEvent.Type.APP_FOREGROUND, packageName = pkg, contentType = contentType))
    }

    private fun handleScroll(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        AppEventBus.emit(AppEvent(type = AppEvent.Type.SCROLL_DETECTED, packageName = pkg))
    }

    fun getOverlayManager(): OverlayManager = overlayManager
    fun getFocusSessionManager(): FocusSessionManager = focusSessionManager
}
