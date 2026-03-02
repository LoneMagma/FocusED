package com.focused.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focused.app.service.FocusedForegroundService
import com.focused.app.util.PermissionHelper
import com.focused.app.work.MidnightResetWorker

/**
 * BootReceiver
 *
 * Restarts the ForegroundService after device reboot so the user doesn't
 * need to manually open Focused every morning for protections to be active.
 *
 * Only starts the service if Accessibility permission is still granted —
 * avoids pointlessly starting a service that can't do anything.
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in the manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            if (PermissionHelper.isAccessibilityServiceEnabled(context)) {
                FocusedForegroundService.start(context)
            }
            MidnightResetWorker.schedule(context)
        }
    }
}
