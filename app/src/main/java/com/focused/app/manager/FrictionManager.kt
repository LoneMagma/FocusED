package com.focused.app.manager

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.FrictionAttempt
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import com.focused.app.util.DateUtil
import com.focused.app.util.FrictionBus
import com.focused.app.work.FrictionUnlockWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * FrictionManager
 *
 * Manages the 3-tier disable flow. Called when the user taps
 * "Disable Focused" on the block overlay.
 *
 * Tier 1 — 10-second greyed-out countdown. Button activates at 0.
 * Tier 2 — 8-character random alphanumeric string. Must type exactly.
 *           No paste. Regenerates on wrong answer.
 * Tier 3 — 2min 30sec silent countdown. No cancel button.
 *           When it expires, enforcement stops for the rest of the session.
 *
 * Tone throughout: calm, neutral, never preachy. No red. No guilt language.
 * The friction is the message — the UI doesn't need to add to it.
 *
 * Each attempt is recorded in FrictionAttempt table.
 * If the user backs out at any tier, the attempt is marked abandoned.
 */
class FrictionManager(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val onDisabled: (packageName: String) -> Unit   // called when Tier 3 completes
) {
    companion object {
        private const val TIER1_DURATION_MS = 30_000L
        private const val TIER3_DURATION_MS = 150_000L     // 2min 30sec
        private const val OVERLAY_KEY = "friction"
        private val CHARSET = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Active attempt tracking
    private var currentAttemptId: Long = -1L
    private var currentPackage: String = ""
    private var tier1Timer: CountDownTimer? = null
    private var tier3Timer: CountDownTimer? = null

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Called when user taps "Disable Focused".
     * Logs the attempt and starts Tier 1.
     */
    fun start(packageName: String) {
        currentPackage = packageName

        scope.launch {
            val attempt = FrictionAttempt(
                packageName = packageName,
                dayKey = DateUtil.todayKey()
            )
            currentAttemptId = FocusedDatabase.get(context)
                .frictionAttemptDao().insert(attempt)
        }

        ActivityLogger.log(context, ActivityLogger.FRICTION_STARTED, packageName)
        showTier1()
    }

    // -------------------------------------------------------------------------
    // Tier 1 — 10 second cooldown
    // -------------------------------------------------------------------------

    private fun showTier1() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier1, null)

            val tvCountdown = view.findViewById<TextView>(R.id.tv_tier1_countdown)
            val tvStats = view.findViewById<TextView>(R.id.tv_tier1_stats)
            val btnProceed = view.findViewById<Button>(R.id.btn_tier1_continue)
            val btnCancel = view.findViewById<Button>(R.id.btn_tier1_cancel)

            btnProceed.isEnabled = false
            btnProceed.alpha = 0.4f

            tier1Timer = object : CountDownTimer(TIER1_DURATION_MS, 1000) {
                override fun onTick(ms: Long) {
                    val sec = (ms / 1000) + 1
                    tvCountdown.text = sec.toString()
                    // Show breathing prompt in first 10 seconds
                    if (ms > 20_000L) {
                        tvStats.text = "Take three breaths."
                    } else if (ms > 10_000L) {
                        tvStats.text = "You can still put the phone down."
                    } else {
                        tvStats.text = "Almost there..."
                    }
                }
                override fun onFinish() {
                    tvCountdown.text = ""
                    tvStats.text = ""
                    btnProceed.isEnabled = true
                    btnProceed.alpha = 1.0f
                }
            }.start()

            btnProceed.setOnClickListener {
                tier1Timer?.cancel()
                ActivityLogger.log(context, ActivityLogger.FRICTION_TIER_1, currentPackage)
                updateAttempt(tiersReached = 1)
                showTier2()
            }

            btnCancel.setOnClickListener { abandon() }

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())
        }
    }

    // -------------------------------------------------------------------------
    // Tier 2 — 8-character random string
    // -------------------------------------------------------------------------

    private fun showTier2() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier2, null)

            val tvChallenge = view.findViewById<TextView>(R.id.tv_tier2_challenge)
            val etInput = view.findViewById<EditText>(R.id.et_tier2_input)
            val btnSubmit = view.findViewById<Button>(R.id.btn_tier2_submit)
            val btnCancel = view.findViewById<Button>(R.id.btn_tier2_cancel)
            val tvError = view.findViewById<TextView>(R.id.tv_tier2_error)

            var challengeString = generateChallenge()
            tvChallenge.text = challengeString

            // Block paste — only manual typing allowed
            etInput.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            etInput.filters = arrayOf(InputFilter.LengthFilter(8))
            etInput.isSingleLine = true
            etInput.isLongClickable = false  // disables long-press paste menu

            // Intercept paste via TextWatcher — if text appears faster than typing, clear it
            var lastLength = 0
            etInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                    lastLength = s?.length ?: 0
                }
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val current = s?.length ?: 0
                    // If more than 1 char appeared at once it was pasted — clear it
                    if (current - lastLength > 1) {
                        s?.clear()
                        tvError.text = "Type it — no pasting."
                        tvError.visibility = View.VISIBLE
                    } else {
                        tvError.visibility = View.GONE
                    }
                }
            })

            btnSubmit.setOnClickListener {
                val typed = etInput.text.toString()
                if (typed == challengeString) {
                    // Correct — advance to Tier 3
                    hideKeyboard(etInput)
                    ActivityLogger.log(context, ActivityLogger.FRICTION_TIER_2, currentPackage)
                    updateAttempt(tiersReached = 2)
                    showTier3()
                } else {
                    // Wrong — regenerate string, clear input, show error
                    challengeString = generateChallenge()
                    tvChallenge.text = challengeString
                    etInput.text.clear()
                    tvError.text = "Not quite. Try this one."
                    tvError.visibility = View.VISIBLE
                }
            }

            btnCancel.setOnClickListener {
                hideKeyboard(etInput)
                abandon()
            }

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())

            // Auto-show keyboard
            view.post {
                etInput.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tier 3 — 2min 30sec silent countdown
    // -------------------------------------------------------------------------

    private fun showTier3() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier3, null)

            val tvCountdown = view.findViewById<TextView>(R.id.tv_tier3_countdown)

            // Register unlock listener before scheduling work
            FrictionBus.registerUnlockListener(currentPackage) {
                onTier3Complete()
            }

            // Schedule WorkManager job — fires in 150 seconds regardless of app state
            val workData = Data.Builder()
                .putString(FrictionUnlockWorker.KEY_PACKAGE, currentPackage)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<FrictionUnlockWorker>()
                .setInitialDelay(TIER3_DURATION_MS, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .addTag("${FrictionUnlockWorker.WORK_TAG_PREFIX}$currentPackage")
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)

            // Visual countdown — purely cosmetic, WorkManager is the actual timer
            tier3Timer = object : CountDownTimer(TIER3_DURATION_MS, 1000) {
                override fun onTick(ms: Long) {
                    val totalSec = ms / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    tvCountdown.text = String.format("%d:%02d", min, sec)
                }
                override fun onFinish() {
                    tvCountdown.text = "0:00"
                }
            }.start()

            // No cancel button on Tier 3 — they committed this far
            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())
        }
    }

    private fun onTier3Complete() {
        tier3Timer?.cancel()
        overlayManager.dismiss(OVERLAY_KEY)
        overlayManager.dismiss("budget_block_$currentPackage")

        updateAttempt(tiersReached = 3, completed = true)
        ActivityLogger.log(context, ActivityLogger.FRICTION_COMPLETED, currentPackage)

        mainHandler.post {
            onDisabled(currentPackage)
        }
    }

    // -------------------------------------------------------------------------
    // Abandon
    // -------------------------------------------------------------------------

    private fun abandon() {
        tier1Timer?.cancel()
        tier3Timer?.cancel()
        FrictionBus.unregisterUnlockListener(currentPackage)
        WorkManager.getInstance(context)
            .cancelAllWorkByTag("${FrictionUnlockWorker.WORK_TAG_PREFIX}$currentPackage")

        overlayManager.dismiss(OVERLAY_KEY)
        ActivityLogger.log(context, ActivityLogger.FRICTION_ABANDONED, currentPackage)
        updateAttempt(completed = false)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun generateChallenge(): String {
        return (1..8).map { CHARSET.random(Random) }.joinToString("")
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun updateAttempt(tiersReached: Int? = null, completed: Boolean? = null) {
        if (currentAttemptId < 0) return
        scope.launch {
            val dao = FocusedDatabase.get(context).frictionAttemptDao()
            val attempt = dao.getById(currentAttemptId) ?: return@launch
            dao.update(
                attempt.copy(
                    tiersReached = tiersReached ?: attempt.tiersReached,
                    completed = completed ?: attempt.completed
                )
            )
        }
    }
}
