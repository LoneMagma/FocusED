package com.focused.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.CountDownTimer
import android.widget.Button as DialogButton
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.BudgetRule
import com.focused.app.databinding.ActivityMainBinding
import com.focused.app.service.FocusedForegroundService
import com.focused.app.ui.onboarding.OnboardingActivity
import com.focused.app.ui.onboarding.PermissionSetupActivity
import com.focused.app.util.ActivityLogger
import com.focused.app.util.DateUtil
import com.focused.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch { routeOnLaunch() }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private suspend fun routeOnLaunch() {
        val state = FocusedDatabase.get(this).onboardingStateDao().get()
        when {
            state == null || !state.onboardingComplete -> {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            }
            else -> {
                FocusedForegroundService.start(this)
                ActivityLogger.log(this, ActivityLogger.SERVICE_STARTED)
                refreshDashboard()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pause logic
    // -------------------------------------------------------------------------

    private fun isPaused(): Boolean {
        val prefs = getSharedPreferences("focused_prefs", MODE_PRIVATE)
        return System.currentTimeMillis() < prefs.getLong("paused_until_ms", 0L)
    }

    /** Pause enforcement until midnight tonight. */
    private fun pauseUntilMidnight() {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        getSharedPreferences("focused_prefs", MODE_PRIVATE)
            .edit().putLong("paused_until_ms", midnight).commit()
    }

    /** Resume enforcement immediately. */
    private fun resume() {
        getSharedPreferences("focused_prefs", MODE_PRIVATE)
            .edit().putLong("paused_until_ms", 0L).commit()
    }

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

    private fun refreshDashboard() {
        val serviceActive = PermissionHelper.isAccessibilityServiceEnabled(this)
        val allGranted    = PermissionHelper.allPermissionsGranted(this)
        val paused        = isPaused()

        // Status dot
        binding.statusDot.setBackgroundResource(when {
            !serviceActive -> R.drawable.dot_amber
            paused         -> R.drawable.dot_amber
            else           -> R.drawable.dot_green
        })

        // Hero text
        binding.tvServiceStatus.text = when {
            !serviceActive -> "Inactive"
            paused         -> "Paused"
            else           -> "Active"
        }

        // Sub-status
        if (!serviceActive) {
            binding.tvStatusSub.text = "Tap below to enable"
        } else if (paused) {
            binding.tvStatusSub.text = "Enforcement paused until midnight"
        }

        // Primary CTA
        when {
            !serviceActive -> {
                binding.btnPrimaryAction.text = "ENABLE FocusED"
                binding.btnPrimaryAction.setOnClickListener {
                    startActivity(PermissionHelper.accessibilitySettingsIntent())
                }
            }
            paused -> {
                binding.btnPrimaryAction.text = "RESUME ENFORCEMENT"
                binding.btnPrimaryAction.setOnClickListener {
                    resume()
                    refreshDashboard()
                }
            }
            else -> {
                binding.btnPrimaryAction.text = "ACTIVE — TAP TO PAUSE"
                binding.btnPrimaryAction.setOnClickListener {
                    showPauseFrictionDialog()
                }
            }
        }

        // Permissions warning
        binding.btnFixPermissions.visibility = if (!allGranted) View.VISIBLE else View.GONE
        binding.btnFixPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionSetupActivity::class.java))
        }

        // About
        binding.btnAbout.setOnClickListener {
            AboutBottomSheet().show(supportFragmentManager, AboutBottomSheet.TAG)
        }

        // Nav buttons
        binding.btnFocusMode.setOnClickListener  { startActivity(Intent(this, FocusActivity::class.java)) }
        binding.btnManageApps.setOnClickListener { startActivity(Intent(this, com.focused.app.ui.setup.AppSetupActivity::class.java)) }
        binding.btnViewLog.setOnClickListener    { startActivity(Intent(this, ActivityLogActivity::class.java)) }
        binding.btnStats.setOnClickListener      { startActivity(Intent(this, WeeklyHeatmapActivity::class.java)) }

        loadAppRows()
        if (serviceActive && !paused) loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val db     = FocusedDatabase.get(this@MainActivity)
            val dayKey = DateUtil.todayKey()

            val focusDone        = withContext(Dispatchers.IO) { db.focusSessionDao().completedToday(dayKey) }
            val frictionAttempts = withContext(Dispatchers.IO) { db.frictionAttemptDao().attemptsToday(dayKey) }
            val streak           = withContext(Dispatchers.IO) {
                val regretDays   = db.reflectionRecordDao().daysWithRegret()
                val firstSession = db.appSessionDao().firstSessionStart()
                computeStreak(regretDays, firstSession)
            }

            val parts = mutableListOf<String>()
            if (streak > 1)          parts.add("$streak day streak")
            if (focusDone > 0)        parts.add("$focusDone focus${if (focusDone == 1) "" else "es"} today")
            if (frictionAttempts > 0) parts.add("$frictionAttempts override${if (frictionAttempts == 1) "" else "s"} attempted")

            if (parts.isNotEmpty()) {
                binding.tvStatusSub.text = parts.joinToString(" · ")
            }
        }
    }

    /**
     * Count consecutive days from today with no regret overrides.
     * Capped by firstSessionStart so a fresh install shows 0, not 30.
     */
    private fun computeStreak(daysWithRegret: List<String>, firstSessionMs: Long?): Int {
        if (firstSessionMs == null) return 0
        val daysSinceFirst = ((System.currentTimeMillis() - firstSessionMs) / 86_400_000L)
            .toInt().coerceAtLeast(0) + 1
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val regretSet = daysWithRegret.toSet()
        val cal = Calendar.getInstance()
        var streak = 0
        for (i in 0 until daysSinceFirst.coerceAtMost(365)) {
            if (fmt.format(cal.time) in regretSet) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    // -------------------------------------------------------------------------
    // App rows
    // -------------------------------------------------------------------------

    private fun loadAppRows() {
        lifecycleScope.launch {
            val rules = withContext(Dispatchers.IO) {
                FocusedDatabase.get(this@MainActivity).budgetRuleDao().getAll()
            }
            populateAppRows(rules)
        }
    }

    private fun populateAppRows(rules: List<BudgetRule>) {
        val container     = binding.containerAppRows
        val serviceActive = PermissionHelper.isAccessibilityServiceEnabled(this)
        val paused        = isPaused()
        val count         = container.childCount
        if (count > 1) container.removeViews(1, count - 1)

        if (serviceActive && !paused) {
            val active = rules.count { it.isActive }
            if (active > 0 && binding.tvStatusSub.text.toString() == "Tap below to enable") {
                binding.tvStatusSub.text = "Watching $active app${if (active == 1) "" else "s"}"
            }
        }

        if (rules.isEmpty()) { binding.tvNoApps.visibility = View.VISIBLE; return }
        binding.tvNoApps.visibility = View.GONE

        rules.forEach { rule -> container.addView(buildAppRow(rule, serviceActive && !paused)) }
    }

    /**
     * Three-step friction before pausing enforcement:
     * Step 1: 30-second countdown dialog (can cancel)
     * Step 2: 6-character typing challenge
     * Step 3: Pause is granted until midnight
     */
    private fun showPauseFrictionDialog() {
        var timer: CountDownTimer? = null

        val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        // Use AlertDialog with a custom message area
        val builder = AlertDialog.Builder(this, R.style.Theme_Focused)
        builder.setTitle("Pause FocusED?")
        builder.setMessage("Take 30 seconds before disabling your limits.\n\nTake three breaths.")
        builder.setCancelable(true)  // back button cancels — the 30s countdown is friction enough

        var secondsLeft = 30
        var dialog: AlertDialog? = null

        builder.setNegativeButton("Cancel") { d, _ ->
            timer?.cancel()
            d.dismiss()
        }
        builder.setPositiveButton("Pause ($secondsLeft s)") { _, _ -> }

        dialog = builder.create()
        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.isEnabled = false

        timer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(msUntilFinished: Long) {
                secondsLeft = (msUntilFinished / 1_000L).toInt() + 1
                val prompt = when {
                    secondsLeft > 20 -> "Take three breaths."
                    secondsLeft > 10 -> "You can leave the phone down."
                    else             -> "Almost there..."
                }
                dialog?.setMessage("$prompt\n\nWaiting $secondsLeft seconds...")
                positiveBtn.text = "Pause ($secondsLeft s)"
            }
            override fun onFinish() {
                positiveBtn.isEnabled = true
                positiveBtn.text = "Pause enforcement"
                dialog?.setMessage("Type  PAUSE  below to confirm.")
                positiveBtn.setOnClickListener {
                    showPauseTypingChallenge(dialog!!)
                }
            }
        }.start()

        dialog.setOnDismissListener { timer?.cancel() }  // covers back button + cancel tap
    }

    private fun showPauseTypingChallenge(previous: AlertDialog) {
        previous.dismiss()
        val input = android.widget.EditText(this).apply {
            hint = "Type PAUSE"
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_disabled))
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this, R.style.Theme_Focused)
            .setTitle("Confirm pause")
            .setMessage("Type  PAUSE  to disable enforcement until midnight.")
            .setView(input)
            .setCancelable(true)
            .setPositiveButton("Confirm") { d, _ ->
                if (input.text.toString().trim().uppercase() == "PAUSE") {
                    pauseUntilMidnight()
                    refreshDashboard()
                    d.dismiss()
                } else {
                    input.error = "Type PAUSE exactly"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildAppRow(rule: BudgetRule, enforcing: Boolean): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt()
            )
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
        }

        val tvName = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text      = rule.appLabel
            textSize  = 15f
            setTextColor(getColor(R.color.text_secondary))
        }

        val details = mutableListOf<String>()
        val limitMin = rule.maxSessionDurationMs / 60_000
        details.add("${limitMin}m/day")
        if (rule.maxOpensPerDay > 0) details.add("${rule.maxOpensPerDay} opens")
        if (rule.downtimeStartMin >= 0) {
            val s = rule.downtimeStartMin; val e = rule.downtimeEndMin
            details.add("off ${s/60}:${String.format("%02d", s%60)}–${e/60}:${String.format("%02d", e%60)}")
        }

        val tvStatus = TextView(this).apply {
            text      = if (enforcing && rule.isActive) details.joinToString(" · ") else "paused"
            textSize  = 14f
            setTextColor(getColor(
                if (enforcing && rule.isActive) R.color.text_primary else R.color.text_tertiary
            ))
            typeface = if (enforcing && rule.isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        row.addView(tvName); row.addView(tvStatus)

        val wrapper = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(row)
        wrapper.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(getColor(R.color.divider))
        })
        return wrapper
    }
}
