package com.focused.app.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.focused.app.R
import com.focused.app.databinding.ActivityFocusBinding
import com.focused.app.service.FocusedAccessibilityService

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding
    private var liveCountdown: CountDownTimer? = null

    // Templates: label → default duration in minutes
    private val templates = listOf(
        Template("Deep work",  50),
        Template("Study",      30),
        Template("Writing",    45),
        Template("Planning",   20),
        Template("Exercise",   45),
        Template("Reading",    40),
        Template("Coding",     60),
        Template("Meditation", 15),
        Template("Meeting",    30),
        Template("Research",   45)
    )

    data class Template(val label: String, val durationMin: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

        val fsm = FocusedAccessibilityService.instance?.getFocusSessionManager()
        if (fsm != null && fsm.isActive) showActiveState(fsm)
        else showSetupState(fsm)
    }

    override fun onPause() { super.onPause(); liveCountdown?.cancel() }

    override fun onResume() {
        super.onResume()
        val fsm = FocusedAccessibilityService.instance?.getFocusSessionManager()
        if (fsm != null && fsm.isActive) showActiveState(fsm)
    }

    private fun showSetupState(fsm: com.focused.app.manager.FocusSessionManager?) {
        binding.layoutSetup.visibility  = View.VISIBLE
        binding.layoutActive.visibility = View.GONE

        var durationMin = 25
        updateDurationLabel(durationMin)

        binding.seekDuration.max = 21
        binding.seekDuration.progress = 3
        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                durationMin = 10 + (p * 5)
                updateDurationLabel(durationMin)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Build template chips
        buildTemplates(binding.containerTemplates, durationMin) { tpl ->
            binding.etTask.setText(tpl.label)
            durationMin = tpl.durationMin
            updateDurationLabel(durationMin)
            // Sync seekbar
            val seekPos = ((tpl.durationMin - 10) / 5).coerceIn(0, 21)
            binding.seekDuration.progress = seekPos
        }

        binding.btnStartFocus.setOnClickListener {
            val task = binding.etTask.text.toString().trim()
            if (task.isEmpty()) { binding.etTask.error = "What are you working on?"; return@setOnClickListener }
            if (fsm == null) { binding.etTask.error = "Enable Accessibility first"; return@setOnClickListener }
            fsm.start(task, durationMin * 60_000L)
            finish()
        }
    }

    private fun buildTemplates(
        container: LinearLayout,
        currentMin: Int,
        onSelect: (Template) -> Unit
    ) {
        container.removeAllViews()
        container.orientation = LinearLayout.VERTICAL
        val dp = resources.displayMetrics.density
        val rowGap = (8 * dp).toInt()
        val chipGap = (8 * dp).toInt()
        val chipH = (38 * dp).toInt()

        // Build rows of chips with wrapping — simple greedy row packer
        var currentRow: LinearLayout? = null
        var rowWidth = 0
        val maxWidth = resources.displayMetrics.widthPixels - (48 * dp).toInt()

        templates.forEachIndexed { idx, tpl ->
            val chipText = "${tpl.label} · ${tpl.durationMin}m"
            // Estimate chip width (13sp ≈ 13px/sp × density, +32dp padding)
            val textPx = (chipText.length * 8 * dp).toInt() + (32 * dp).toInt()

            if (currentRow == null || rowWidth + textPx + chipGap > maxWidth) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { if (idx > 0) it.topMargin = rowGap }
                }
                container.addView(currentRow)
                rowWidth = 0
            }

            val chip = android.widget.Button(this).apply {
                text = chipText
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                background = getDrawable(R.drawable.bg_btn_secondary)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, chipH
                ).also { if (rowWidth > 0) it.marginStart = chipGap }
                layoutParams = lp
                setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
                android.view.ViewOutlineProvider.BACKGROUND
                stateListAnimator = null
                setOnClickListener { onSelect(tpl) }
            }
            currentRow?.addView(chip)
            rowWidth += textPx + chipGap
        }
    }

    private fun updateDurationLabel(min: Int) {
        binding.tvDurationLabel.text = when {
            min < 60  -> "$min min"
            min == 60 -> "1 hour"
            else -> { val h = min/60; val m = min%60; if (m==0) "$h hr" else "$h hr $m min" }
        }
    }

    private fun showActiveState(fsm: com.focused.app.manager.FocusSessionManager) {
        binding.layoutSetup.visibility  = View.GONE
        binding.layoutActive.visibility = View.VISIBLE
        binding.tvActiveTask.text = fsm.currentTask

        liveCountdown?.cancel()
        liveCountdown = object : CountDownTimer(fsm.remainingMs(), 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                binding.tvActiveRemaining.text = String.format("%d:%02d remaining", s/60, s%60)
            }
            override fun onFinish() {
                binding.tvActiveRemaining.text = "0:00 remaining"
                finish()
            }
        }.start()

        binding.btnEndFocusEarly.setOnClickListener {
            liveCountdown?.cancel()
            fsm.end(completed = false)
            finish()
        }
    }
}
