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
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.FrictionAttempt
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import com.focused.app.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * FrictionManager — 3-tier override flow
 *
 * Tier 1 — 20s visible countdown with breathing prompt. Cancel returns to app.
 * Tier 2 — 6-char random string typing challenge. Paste blocked. Cancel returns.
 * Tier 3 — 20s silent countdown. No cancel. Handler.postDelayed (not WorkManager).
 *           Completes → enforcement paused for this package.
 *
 * Total maximum friction: ~60 seconds.
 *
 * Critical behaviour: if the user navigates away (HOME press) at any tier,
 * abandonFor() is called by BudgetEnforcer.handleBackground(). All overlays
 * and timers are cancelled. No state leaks to home screen or other apps.
 *
 * Tier 3 uses a plain Handler — NOT WorkManager — to avoid a race condition
 * where the WorkManager job fires after abandon() but before cancelAllWorkByTag()
 * completes, inadvertently pausing enforcement without user completing the flow.
 */
class FrictionManager(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val onDisabled: (packageName: String) -> Unit
) {
    companion object {
        private const val TIER1_DURATION_MS = 20_000L
        private const val TIER3_DURATION_MS = 20_000L
        private const val OVERLAY_KEY = "friction"
        private val CHARSET = ('A'..'Z') + ('0'..'9')  // uppercase + digits, no confusable chars
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentAttemptId: Long = -1L
    private var currentPackage: String = ""
    private var tier1Timer: CountDownTimer? = null
    private var tier3Timer: CountDownTimer? = null
    private var tier3CompleteRunnable: Runnable? = null  // replaces WorkManager
    private var isAbandoned = false

    // -------------------------------------------------------------------------

    fun start(packageName: String) {
        isAbandoned = false
        currentPackage = packageName

        scope.launch {
            val attempt = FrictionAttempt(packageName = packageName, dayKey = DateUtil.todayKey())
            currentAttemptId = FocusedDatabase.get(context).frictionAttemptDao().insert(attempt)
        }

        ActivityLogger.log(context, ActivityLogger.FRICTION_STARTED, packageName)
        showTier1()
    }

    // -------------------------------------------------------------------------
    // Tier 1 — 20s breathing countdown
    // -------------------------------------------------------------------------

    private fun showTier1() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier1, null)

            val tvCountdown = view.findViewById<TextView>(R.id.tv_tier1_countdown)
            val tvPrompt   = view.findViewById<TextView>(R.id.tv_tier1_stats)
            val btnProceed = view.findViewById<Button>(R.id.btn_tier1_continue)
            val btnCancel  = view.findViewById<Button>(R.id.btn_tier1_cancel)

            btnProceed.isEnabled = false
            btnProceed.alpha = 0.35f

            tier1Timer = object : CountDownTimer(TIER1_DURATION_MS, 1000) {
                override fun onTick(ms: Long) {
                    val sec = (ms / 1000) + 1
                    tvCountdown.text = sec.toString()
                    tvPrompt.text = when {
                        ms > 13_000L -> "Take three breaths."
                        ms > 6_000L  -> "You can still put the phone down."
                        else         -> "Almost there..."
                    }
                }
                override fun onFinish() {
                    tvCountdown.text = ""
                    tvPrompt.text = ""
                    btnProceed.isEnabled = true
                    btnProceed.alpha = 1.0f
                }
            }.start()

            btnProceed.setOnClickListener {
                tier1Timer?.cancel()
                updateAttempt(tiersReached = 1)
                ActivityLogger.log(context, ActivityLogger.FRICTION_TIER_1, currentPackage)
                showTier2()
            }
            btnCancel.setOnClickListener { abandon() }

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())
        }
    }

    // -------------------------------------------------------------------------
    // Tier 2 — typing challenge (6 chars, uppercase + digits)
    // -------------------------------------------------------------------------

    private fun showTier2() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier2, null)

            val tvChallenge = view.findViewById<TextView>(R.id.tv_tier2_challenge)
            val etInput     = view.findViewById<EditText>(R.id.et_tier2_input)
            val btnSubmit   = view.findViewById<Button>(R.id.btn_tier2_submit)
            val btnCancel   = view.findViewById<Button>(R.id.btn_tier2_cancel)
            val tvError     = view.findViewById<TextView>(R.id.tv_tier2_error)

            var challenge = generateChallenge()
            tvChallenge.text = challenge

            etInput.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            etInput.filters = arrayOf(InputFilter.LengthFilter(6))
            etInput.isSingleLine = true
            etInput.isLongClickable = false

            var lastLen = 0
            etInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { lastLen = s?.length ?: 0 }
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if ((s?.length ?: 0) - lastLen > 1) {
                        s?.clear()
                        tvError.text = "Type it — no pasting."
                        tvError.visibility = View.VISIBLE
                    } else {
                        tvError.visibility = View.GONE
                    }
                }
            })

            btnSubmit.setOnClickListener {
                if (etInput.text.toString() == challenge) {
                    hideKeyboard(etInput)
                    updateAttempt(tiersReached = 2)
                    ActivityLogger.log(context, ActivityLogger.FRICTION_TIER_2, currentPackage)
                    showTier3()
                } else {
                    challenge = generateChallenge()
                    tvChallenge.text = challenge
                    etInput.text.clear()
                    tvError.text = "Not quite. Try this one."
                    tvError.visibility = View.VISIBLE
                }
            }
            btnCancel.setOnClickListener { hideKeyboard(etInput); abandon() }

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())
            view.post {
                etInput.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tier 3 — 20s silent countdown via Handler (no WorkManager)
    // -------------------------------------------------------------------------

    private fun showTier3() {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_friction_tier3, null)
            val tvCountdown = view.findViewById<TextView>(R.id.tv_tier3_countdown)

            // Schedule completion via plain Handler — synchronous, no cross-process race
            val completeRunnable = Runnable {
                if (!isAbandoned) onTier3Complete()
            }
            tier3CompleteRunnable = completeRunnable
            mainHandler.postDelayed(completeRunnable, TIER3_DURATION_MS)

            tier3Timer = object : CountDownTimer(TIER3_DURATION_MS, 1000) {
                override fun onTick(ms: Long) {
                    val s = ms / 1000
                    tvCountdown.text = String.format("%d:%02d", s / 60, s % 60)
                }
                override fun onFinish() { tvCountdown.text = "0:00" }
            }.start()

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildFullScreenParams())
        }
    }

    private fun onTier3Complete() {
        tier3Timer?.cancel()
        overlayManager.dismiss(OVERLAY_KEY)
        overlayManager.dismiss("budget_block_$currentPackage")
        updateAttempt(tiersReached = 3, completed = true)
        ActivityLogger.log(context, ActivityLogger.FRICTION_COMPLETED, currentPackage)
        mainHandler.post { onDisabled(currentPackage) }
    }

    // -------------------------------------------------------------------------
    // Abandon
    // -------------------------------------------------------------------------

    /** Called by BudgetEnforcer when the app goes to background. Clears everything. */
    fun abandonFor(packageName: String) {
        if (currentPackage == packageName) abandon()
    }

    private fun abandon() {
        isAbandoned = true
        tier1Timer?.cancel()
        tier3Timer?.cancel()
        tier3CompleteRunnable?.let { mainHandler.removeCallbacks(it) }
        tier3CompleteRunnable = null
        overlayManager.dismiss(OVERLAY_KEY)
        ActivityLogger.log(context, ActivityLogger.FRICTION_ABANDONED, currentPackage)
        updateAttempt(completed = false)
    }

    // -------------------------------------------------------------------------

    private fun generateChallenge(): String = (1..6).map { CHARSET.random(Random) }.joinToString("")

    private fun hideKeyboard(view: View) {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun updateAttempt(tiersReached: Int? = null, completed: Boolean? = null) {
        if (currentAttemptId < 0) return
        scope.launch {
            val dao = FocusedDatabase.get(context).frictionAttemptDao()
            val attempt = dao.getById(currentAttemptId) ?: return@launch
            dao.update(attempt.copy(
                tiersReached = tiersReached ?: attempt.tiersReached,
                completed = completed ?: attempt.completed
            ))
        }
    }
}
