package com.focused.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focused.app.data.dao.DayUsage
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.AppSession
import com.focused.app.databinding.ActivityWeeklyHeatmapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeeklyHeatmapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyHeatmapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val db = FocusedDatabase.get(this@WeeklyHeatmapActivity)
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

            val sessions = withContext(Dispatchers.IO) {
                db.appSessionDao().sessionsAfter(sevenDaysAgo)
            }
            val reflections = withContext(Dispatchers.IO) {
                val total = db.reflectionRecordDao().totalAnswered()
                val worthIt = db.reflectionRecordDao().totalWorthIt()
                Pair(total, worthIt)
            }
            val streak = withContext(Dispatchers.IO) {
                computeStreak(db.reflectionRecordDao().daysWithRegret())
            }

            buildHeatmap(sessions)
            buildStreak(streak, reflections.first, reflections.second)
            buildAppBreakdown(sessions)
        }
    }

    private fun buildHeatmap(sessions: List<AppSession>) {
        // Build hour-by-day matrix (7 days × 24 hours)
        val hourMatrix = Array(7) { LongArray(24) }  // [dayIndex][hour] = ms

        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)

        sessions.forEach { s ->
            cal.timeInMillis = s.sessionStart
            val daysBefore = ((today.timeInMillis - cal.timeInMillis) / (86400_000L)).toInt()
                .coerceIn(0, 6)
            val dayIdx = 6 - daysBefore  // 0=oldest, 6=today
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourMatrix[dayIdx][hour] += s.durationMs.coerceAtLeast(60_000L)  // min 1 min per session
        }

        binding.heatmapView.setData(hourMatrix)
    }

    private fun buildStreak(streak: Int, totalAnswered: Int, totalWorthIt: Int) {
        binding.tvStreakNumber.text = streak.toString()
        binding.tvStreakLabel.text = if (streak == 1) "day under limits" else "days under limits"

        if (totalAnswered > 0) {
            val pct = (totalWorthIt * 100) / totalAnswered
            binding.tvReflectionScore.text = "$pct%"
            binding.tvReflectionLabel.text = "of overrides felt worth it"
        } else {
            binding.tvReflectionScore.text = "—"
            binding.tvReflectionLabel.text = "no override data yet"
        }
    }

    private fun buildAppBreakdown(sessions: List<AppSession>) {
        val appTotals = sessions.groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { it.durationMs } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val container = binding.containerAppBreakdown
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        appTotals.forEach { (pkg, ms) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (48 * dp).toInt()
                )
                setPadding(0, 0, 0, 0)
            }
            val tvApp = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.gravity = android.view.Gravity.CENTER_VERTICAL }
                text = com.focused.app.util.DateUtil.appLabel(pkg)
                textSize = 15f
                setTextColor(getColor(com.focused.app.R.color.text_secondary))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val tvTime = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                text = formatMs(ms)
                textSize = 15f
                setTextColor(getColor(com.focused.app.R.color.text_primary))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(tvApp)
            row.addView(tvTime)
            container.addView(row)

            val div = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(getColor(com.focused.app.R.color.divider))
            }
            container.addView(div)
        }
    }

    private fun computeStreak(daysWithRegret: List<String>): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val regretSet = daysWithRegret.toSet()
        val cal = Calendar.getInstance()
        var streak = 0
        for (i in 0..30) {
            val dayKey = fmt.format(cal.time)
            if (dayKey in regretSet) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun formatMs(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) "${min}m" else "${min/60}h ${min%60}m"
    }
}

/** Custom view: 7-column (days) × 24-row (hours) heatmap grid */
class HeatmapView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var data: Array<LongArray> = Array(7) { LongArray(24) }
    private val paintCell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141414.toInt() }
    private val days = listOf("7d ago", "6d", "5d", "4d", "3d", "2d", "Today")

    fun setData(matrix: Array<LongArray>) {
        data = matrix
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cols = 7
        val rows = 24
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows

        // Find max value for normalisation
        var maxMs = 1L
        data.forEach { day -> day.forEach { v -> if (v > maxMs) maxMs = v } }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val v = data[col][row]
                val intensity = (v.toFloat() / maxMs).coerceIn(0f, 1f)
                val alpha = (intensity * 220 + 20).toInt().coerceIn(0, 255)
                paintCell.color = (alpha shl 24) or 0xF0F0F0
                val left = col * cellW + 1f
                val top = row * cellH + 1f
                val right = (col + 1) * cellW - 1f
                val bottom = (row + 1) * cellH - 1f
                canvas.drawRoundRect(left, top, right, bottom, 2f, 2f, paintCell)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 24f / 7f).toInt())
    }
}
