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
        Template("Exercise",   45)
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
        val dp = resources.displayMetrics.density
        templates.forEach { tpl ->
            val chip = android.widget.Button(this).apply {
                text = "${tpl.label} · ${tpl.durationMin}m"
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setBackgroundColor(getColor(R.color.bg_card))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (38 * dp).toInt()
                ).apply {
                    marginEnd = (8 * dp).toInt()
                    bottomMargin = (8 * dp).toInt()
                }
                layoutParams = lp
                setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
                setOnClickListener { onSelect(tpl) }
            }
            container.addView(chip)
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
