package com.focused.app.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.ReflectionRecord
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ReflectionManager
 *
 * When the user overrides a block and then later leaves the app,
 * shows a soft reflection card: "You spent X minutes in Instagram
 * after overriding. Was that worth it?"
 *
 * Two taps: Yes or No. Recorded privately. Never shown to anyone.
 * Over time the Activity Log shows override satisfaction rate.
 *
 * The card is non-blocking — it auto-dismisses after 6 seconds.
 */
class ReflectionManager(
    private val context: Context,
    private val overlayManager: OverlayManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track when override happened per package
    private val overrideTimestamps = mutableMapOf<String, Long>()

    /** Call when user completes friction and override starts */
    fun markOverrideStart(pkg: String) {
        overrideTimestamps[pkg] = System.currentTimeMillis()
    }

    /** Call when app goes to background after an override */
    fun onAppExitAfterOverride(pkg: String, appLabel: String) {
        val overrideStart = overrideTimestamps.remove(pkg) ?: return
        val minutesSpent = ((System.currentTimeMillis() - overrideStart) / 60_000).toInt()
        if (minutesSpent < 1) return  // less than 1 min — not worth asking

        val recordId = longArrayOf(-1L)
        scope.launch {
            val record = ReflectionRecord(
                packageName = pkg,
                dayKey = DateUtil.todayKey(),
                minutesSpent = minutesSpent
            )
            recordId[0] = FocusedDatabase.get(context).reflectionRecordDao().insert(record)
            withContext(Dispatchers.Main) {
                showReflectionCard(pkg, appLabel, minutesSpent, recordId[0])
            }
        }
    }

    private fun showReflectionCard(pkg: String, appLabel: String, minutes: Int, recordId: Long) {
        val view = LayoutInflater.from(overlayManager.themedContext)
            .inflate(R.layout.overlay_reflection, null)

        view.findViewById<TextView>(R.id.tv_reflection_body).text =
            "You spent $minutes min in $appLabel after overriding."

        fun answer(worthIt: Boolean) {
            overlayManager.dismiss("reflection")
            scope.launch {
                val dao = FocusedDatabase.get(context).reflectionRecordDao()
                val record = dao.getById(recordId) ?: return@launch
                dao.update(record.copy(wasWorthIt = worthIt))
            }
        }

        view.findViewById<Button>(R.id.btn_reflection_yes).setOnClickListener { answer(true) }
        view.findViewById<Button>(R.id.btn_reflection_no).setOnClickListener { answer(false) }
        view.findViewById<Button>(R.id.btn_reflection_dismiss).setOnClickListener {
            overlayManager.dismiss("reflection")
        }

        overlayManager.show("reflection", view, overlayManager.buildBottomCardParams(220))
        view.postDelayed({ overlayManager.dismiss("reflection") }, 8000L)
    }
}
