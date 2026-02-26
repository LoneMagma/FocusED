package com.focused.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.onboardingButton).setOnClickListener {
            // Mark onboarding as seen — never shown again
            getSharedPreferences("focused_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("onboarding_seen", true)
                .apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
