package com.focused.app.ui

import android.content.Intent
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

    private fun refreshDashboard() {
        val serviceActive = PermissionHelper.isAccessibilityServiceEnabled(this)
        val allGranted    = PermissionHelper.allPermissionsGranted(this)

        binding.statusDot.setBackgroundResource(
            if (serviceActive) R.drawable.dot_green else R.drawable.dot_amber
        )

        if (serviceActive) {
            binding.tvServiceStatus.text = "Active"
        } else {
            binding.tvServiceStatus.text = "Inactive"
            binding.tvStatusSub.text = "Tap below to enable"
        }

        if (serviceActive) {
            binding.btnPrimaryAction.text = "MANAGE IN SETTINGS"
            binding.btnPrimaryAction.setOnClickListener {
                startActivity(Intent(this, PermissionSetupActivity::class.java))
            }
        } else {
            binding.btnPrimaryAction.text = "ENABLE FocusED"
            binding.btnPrimaryAction.setOnClickListener {
                startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }

        binding.btnFixPermissions.visibility = if (!allGranted) View.VISIBLE else View.GONE
        binding.btnFixPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionSetupActivity::class.java))
        }

        binding.btnFocusMode.setOnClickListener { startActivity(Intent(this, FocusActivity::class.java)) }
        binding.btnManageApps.setOnClickListener { startActivity(Intent(this, com.focused.app.ui.setup.AppSetupActivity::class.java)) }
        binding.btnViewLog.setOnClickListener { startActivity(Intent(this, ActivityLogActivity::class.java)) }
        binding.btnStats.setOnClickListener { startActivity(Intent(this, WeeklyHeatmapActivity::class.java)) }

        loadAppRows()
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val db = FocusedDatabase.get(this@MainActivity)
            val dayKey = DateUtil.todayKey()

            val focusDone = withContext(Dispatchers.IO) { db.focusSessionDao().completedToday(dayKey) }
            val frictionAttempts = withContext(Dispatchers.IO) { db.frictionAttemptDao().attemptsToday(dayKey) }
            val streak = withContext(Dispatchers.IO) {
                val regretDays = db.reflectionRecordDao().daysWithRegret()
                computeStreak(regretDays)
            }

            val parts = mutableListOf<String>()
            if (streak > 1) parts.add("$streak day streak")
            if (focusDone > 0) parts.add("$focusDone focus${if (focusDone == 1) "" else "s"} today")
            if (frictionAttempts > 0) parts.add("$frictionAttempts override${if (frictionAttempts == 1) "" else "s"} attempted")

            if (parts.isNotEmpty() && PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)) {
                binding.tvStatusSub.text = parts.joinToString(" · ")
            }
        }
    }

    private fun computeStreak(daysWithRegret: List<String>): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val regretSet = daysWithRegret.toSet()
        val cal = Calendar.getInstance()
        var streak = 0
        for (i in 0..30) {
            if (fmt.format(cal.time) in regretSet) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun loadAppRows() {
        lifecycleScope.launch {
            val rules = withContext(Dispatchers.IO) {
                FocusedDatabase.get(this@MainActivity).budgetRuleDao().getAll()
            }
            populateAppRows(rules)
        }
    }

    private fun populateAppRows(rules: List<BudgetRule>) {
        val container = binding.containerAppRows
        val serviceActive = PermissionHelper.isAccessibilityServiceEnabled(this)
        val count = container.childCount
        if (count > 1) container.removeViews(1, count - 1)

        if (serviceActive) {
            val active = rules.count { it.isActive }
            if (active > 0 && binding.tvStatusSub.text.toString() == "Tap below to enable") {
                binding.tvStatusSub.text = "Watching $active app${if (active == 1) "" else "s"}"
            }
        }

        if (rules.isEmpty()) { binding.tvNoApps.visibility = View.VISIBLE; return }
        binding.tvNoApps.visibility = View.GONE

        rules.forEach { rule ->
            container.addView(buildAppRow(rule, serviceActive))
        }
    }

    private fun buildAppRow(rule: BudgetRule, serviceActive: Boolean): View {
        val dp = resources.displayMetrics.density
        val isBlocked = rule.isActive && serviceActive

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt())
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
        }

        val tvName = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = rule.appLabel
            textSize = 15f
            setTextColor(getColor(R.color.text_secondary))
        }

        // Build status detail: daily limit + opens + downtime
        val details = mutableListOf<String>()
        val limitMin = rule.maxSessionDurationMs / 60_000
        details.add("${limitMin}m/day")
        if (rule.maxOpensPerDay > 0) details.add("${rule.maxOpensPerDay} opens")
        if (rule.downtimeStartMin >= 0) {
            val s = rule.downtimeStartMin; val e = rule.downtimeEndMin
            details.add("off ${s/60}:${String.format("%02d",s%60)}–${e/60}:${String.format("%02d",e%60)}")
        }
        val statusStr = if (isBlocked) details.joinToString(" · ") else "paused"

        val tvStatus = TextView(this).apply {
            text = statusStr
            textSize = 14f
            setTextColor(if (isBlocked) getColor(R.color.text_primary) else getColor(R.color.text_tertiary))
            typeface = if (isBlocked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        row.addView(tvName); row.addView(tvStatus)

        val wrapper = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(getColor(R.color.divider))
        }
        wrapper.addView(row); wrapper.addView(divider)
        return wrapper
    }
}
