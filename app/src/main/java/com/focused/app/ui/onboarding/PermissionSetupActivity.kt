package com.focused.app.ui.onboarding

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.focused.app.databinding.ActivityPermissionSetupBinding
import com.focused.app.service.FocusedAccessibilityService
import com.focused.app.ui.MainActivity
import com.focused.app.util.PermissionHelper

class PermissionSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionSetupBinding
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkAndAdvance()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("focused_perm_prefs", MODE_PRIVATE)
        renderCurrentStep()
    }

    override fun onResume() {
        super.onResume()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    // -------------------------------------------------------------------------

    private fun isAccessibilityGranted(): Boolean {
        // Primary: check live accessibility manager list
        if (PermissionHelper.isAccessibilityServiceEnabled(this)) {
            // Use commit() not apply() — synchronous write so the 500ms poll sees it immediately
            prefs.edit().putBoolean("accessibility_granted", true).commit()
            return true
        }
        // Secondary: service singleton is alive (reliable even if manager list has a brief lag)
        if (FocusedAccessibilityService.instance != null) {
            prefs.edit().putBoolean("accessibility_granted", true).commit()
            return true
        }
        // Tertiary: was ever granted in a previous session (covers service restart gap)
        return prefs.getBoolean("accessibility_granted", false)
    }

    private fun currentStep(): Int {
        return when {
            !PermissionHelper.canDrawOverlays(this)         -> 1
            !isAccessibilityGranted()                        -> 2
            !PermissionHelper.hasUsageStatsPermission(this) -> 3
            else                                             -> 4
        }
    }

    private fun checkAndAdvance() {
        if (currentStep() == 4) {
            handler.removeCallbacks(pollRunnable)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else {
            renderCurrentStep()
        }
    }

    private fun renderCurrentStep() {
        val step = currentStep()
        if (step == 4) return
        val content = stepContent(step)
        binding.tvStepCounter.text = "Step $step of 3"
        binding.tvPermTitle.text = content.title
        binding.tvPermReason.text = content.reason
        binding.tvPermDetail.text = content.detail
        binding.btnGrant.text = "Grant access"
        binding.progressBar.progress = (step - 1) * 33
        binding.btnGrant.setOnClickListener {
            val intent = when (step) {
                1 -> PermissionHelper.overlaySettingsIntent(this)
                2 -> PermissionHelper.accessibilitySettingsIntent()
                3 -> PermissionHelper.usageStatsSettingsIntent()
                else -> return@setOnClickListener
            }
            startActivity(intent)
        }
    }

    data class StepContent(val title: String, val reason: String, val detail: String)

    private fun stepContent(step: Int): StepContent = when (step) {
        1 -> StepContent(
            "Draw over other apps",
            "This lets Focused show your session limits on top of Instagram, YouTube, and other apps you're managing.",
            "Android will open a settings screen. Find \"Focused\" in the list and toggle it on. Then come back here."
        )
        2 -> StepContent(
            "Accessibility access",
            "This lets Focused detect which app is open so it can apply the limits you set. Focused cannot read messages, photos, or anything inside other apps — only app names.",
            "Android will open Accessibility settings. Find \"Focused\" under Installed Services and enable it. You'll see a confirmation dialog — this is normal."
        )
        3 -> StepContent(
            "App usage access",
            "This lets Focused see how long you've used each app today so your session budgets work correctly.",
            "Android will open Usage Access settings. Find \"Focused\" in the list and allow it."
        )
        else -> StepContent("", "", "")
    }
}
