package com.focused.app.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.AppOpsManager
import android.os.Process
import android.view.accessibility.AccessibilityManager
import com.focused.app.service.FocusedAccessibilityService

/**
 * PermissionHelper
 *
 * Centralises all permission state checks for Focused.
 * Three permissions are required for the app to function:
 *
 *  1. SYSTEM_ALERT_WINDOW   — Draw overlays on top of other apps
 *  2. Accessibility Service — Detect foreground app + scroll events
 *  3. PACKAGE_USAGE_STATS   — Query per-app usage durations (Phase 3)
 *
 * Each check is a pure boolean function. Directing the user to the
 * correct settings screen is handled by the companion intent builders.
 *
 * No permissions are requested silently or automatically. The user
 * is always taken to the official Android settings screen — this is
 * both the Play Protect-compliant approach and the honest one.
 */
object PermissionHelper {

    // -------------------------------------------------------------------------
    // State Checks
    // -------------------------------------------------------------------------

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Pre-M devices don't require this permission
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == FocusedAccessibilityService::class.java.name
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun allPermissionsGranted(context: Context): Boolean {
        return canDrawOverlays(context) &&
               isAccessibilityServiceEnabled(context) &&
               hasUsageStatsPermission(context)
    }

    // -------------------------------------------------------------------------
    // Settings Intents
    // Called from UI — each opens the correct Android settings screen
    // -------------------------------------------------------------------------

    fun overlaySettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun usageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
}
