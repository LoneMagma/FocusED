package com.focused.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var statusLabel: TextView
    private lateinit var statusSub: TextView
    private lateinit var statusDot: View
    private lateinit var actionButton: Button
    private lateinit var instagramStatus: TextView
    private lateinit var youtubeStatus: TextView
    private lateinit var menuButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show onboarding on first launch — never again after that
        val prefs = getSharedPreferences("focused_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_seen", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        statusLabel     = findViewById(R.id.statusLabel)
        statusSub       = findViewById(R.id.statusSub)
        statusDot       = findViewById(R.id.statusDot)
        actionButton    = findViewById(R.id.actionButton)
        instagramStatus = findViewById(R.id.instagramStatus)
        youtubeStatus   = findViewById(R.id.youtubeStatus)
        menuButton      = findViewById(R.id.menuButton)

        actionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        menuButton.setOnClickListener {
            showBottomSheet()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::statusLabel.isInitialized) render(isServiceEnabled())
    }

    private fun showBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_menu, null)

        view.findViewById<View>(R.id.rowVisitSite).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pacify.site")))
            sheet.dismiss()
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun render(enabled: Boolean) {
        if (enabled) {
            statusLabel.text = "Active"
            statusSub.text   = "Reels and Shorts are guarded"
            statusDot.setBackgroundResource(R.drawable.dot_active)
            actionButton.text = "Manage in Settings"
            instagramStatus.text = "Reels  —  guarded"
            youtubeStatus.text   = "Shorts  —  guarded"
            instagramStatus.setTextColor(0xFFFFFFFF.toInt())
            youtubeStatus.setTextColor(0xFFFFFFFF.toInt())
        } else {
            statusLabel.text = "Inactive"
            statusSub.text   = "Tap below to enable"
            statusDot.setBackgroundResource(R.drawable.dot_inactive)
            actionButton.text = "Enable FocusED"
            instagramStatus.text = "Reels  —  not guarded"
            youtubeStatus.text   = "Shorts  —  not guarded"
            instagramStatus.setTextColor(0xFFCCCCCC.toInt())
            youtubeStatus.setTextColor(0xFFCCCCCC.toInt())
        }
    }

    private fun isServiceEnabled(): Boolean {
        val target = "$packageName/${BlockerService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(target, ignoreCase = true)) return true
        }
        return false
    }
}
