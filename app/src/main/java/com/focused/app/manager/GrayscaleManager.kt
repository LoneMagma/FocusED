package com.focused.app.manager

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.focused.app.overlay.OverlayManager

/**
 * GrayscaleManager
 *
 * At 80% of daily budget: adds a translucent grey tint over the entire screen.
 * Makes feed content look intentionally muted. Not a block — just desaturation.
 *
 * At 100% (budget exhausted): removes tint (BudgetEnforcer handles full block).
 * On app background: removes tint.
 *
 * The tint is a 15% opaque black overlay — subtle enough to notice but not
 * intrusive. Works by adding a view directly to WindowManager.
 */
class GrayscaleManager(
    private val context: Context,
    private val overlayManager: OverlayManager
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeTints = mutableMapOf<String, View>()

    fun showTint(pkg: String) {
        if (activeTints.containsKey(pkg)) return
        mainHandler.post {
            try {
                val tint = View(context).apply {
                    setBackgroundColor(0x26000000.toInt())  // 15% black
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or  // passes all touches through
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.START }
                windowManager.addView(tint, params)
                activeTints[pkg] = tint
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun removeTint(pkg: String) {
        mainHandler.post {
            activeTints.remove(pkg)?.let {
                try { windowManager.removeView(it) } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    fun removeAll() {
        activeTints.keys.toList().forEach { removeTint(it) }
    }
}
