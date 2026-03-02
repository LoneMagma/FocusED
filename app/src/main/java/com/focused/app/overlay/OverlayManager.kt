package com.focused.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.view.ContextThemeWrapper
import com.focused.app.R

class OverlayManager(private val context: Context) {

    companion object { private const val TAG = "OverlayManager" }

    /**
     * Themed context — service context wrapped with the app Material theme.
     * ALL overlay inflation MUST use this context, never the raw service context.
     * MaterialButton, ChipGroup, and all Material widgets require a themed context.
     */
    val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_Focused)

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeOverlays = mutableMapOf<String, View>()

    fun show(key: String, view: View, params: WindowManager.LayoutParams) {
        dismiss(key)
        try {
            windowManager.addView(view, params)
            activeOverlays[key] = view
            Log.d(TAG, "Overlay shown: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay [$key]: ${e.message}")
        }
    }

    fun dismiss(key: String) {
        activeOverlays.remove(key)?.let { view ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Overlay dismissed: $key")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss [$key]: ${e.message}")
            }
        }
    }

    fun dismissAll() { activeOverlays.keys.toList().forEach { dismiss(it) } }

    fun isShowing(key: String): Boolean = activeOverlays.containsKey(key)

    fun buildFullScreenParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    fun buildBottomCardParams(heightDp: Int = 200): WindowManager.LayoutParams {
        val heightPx = (heightDp * context.resources.displayMetrics.density).toInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
    }

    fun buildTopBannerParams(heightDp: Int = 56): WindowManager.LayoutParams {
        val heightPx = (heightDp * context.resources.displayMetrics.density).toInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
    }

    fun showTestOverlay(autoDismissMs: Long = 3000L) {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_test, null)
        val params = buildBottomCardParams(120)
        show("test", view, params)
        view.postDelayed({ dismiss("test") }, autoDismissMs)
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }
}
