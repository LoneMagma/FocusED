package com.focused.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.OnboardingState
import com.focused.app.databinding.ActivityOnboardingBinding
import com.focused.app.ui.MainActivity
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val totalScreens = 5
    private val dotViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupPager()
        buildDots()
        ensureOnboardingRowExists()
    }

    private fun setupPager() {
        val adapter = OnboardingPagerAdapter(this) { screenIndex ->
            onScreenCompleted(screenIndex)
        }
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun buildDots() {
        val container = binding.dotsIndicator
        val dp = resources.displayMetrics.density
        dotViews.clear()
        container.removeAllViews()

        for (i in 0 until totalScreens) {
            val dot = View(this).apply {
                val size = (6 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * dp).toInt()
                    marginStart = (8 * dp).toInt()
                }
                background = resources.getDrawable(R.drawable.dot_amber, null)
            }
            container.addView(dot)
            dotViews.add(dot)
        }
        updateDots(0)
    }

    private fun updateDots(active: Int) {
        dotViews.forEachIndexed { index, dot ->
            dot.background = resources.getDrawable(
                if (index == active) R.drawable.dot_green else R.drawable.dot_amber,
                null
            )
            val dp = resources.displayMetrics.density
            val size = if (index == active) (8 * dp).toInt() else (5 * dp).toInt()
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).also {
                it.width = size
                it.height = size
                it.marginStart = (8 * dp).toInt()
                it.marginEnd = (8 * dp).toInt()
                it.topMargin = if (index == active) 0 else (1.5 * dp).toInt()
            }
            dot.requestLayout()
        }
    }

    fun goToNextScreen() {
        val next = binding.viewPager.currentItem + 1
        lifecycleScope.launch {
            FocusedDatabase.get(this@OnboardingActivity)
                .onboardingStateDao().saveProgress(next)
        }
        if (next < totalScreens) binding.viewPager.currentItem = next
    }

    private fun onScreenCompleted(screenIndex: Int) {
        when (screenIndex) {
            4 -> {
                lifecycleScope.launch {
                    val dao = FocusedDatabase.get(this@OnboardingActivity).onboardingStateDao()
                    dao.acceptCompact()
                    dao.markComplete()
                }
                startActivity(Intent(this, PermissionSetupActivity::class.java))
                finish()
            }
            else -> goToNextScreen()
        }
    }

    private fun ensureOnboardingRowExists() {
        lifecycleScope.launch {
            val dao = FocusedDatabase.get(this@OnboardingActivity).onboardingStateDao()
            if (dao.get() == null) dao.save(OnboardingState())
        }
    }
}
