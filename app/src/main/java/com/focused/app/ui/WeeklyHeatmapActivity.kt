package com.focused.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.AppSession
import com.focused.app.databinding.ActivityWeeklyHeatmapBinding
import com.focused.app.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

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

            val sessions = withContext(Dispatchers.IO) { db.appSessionDao().sessionsAfter(sevenDaysAgo) }

            // Streak: days where no app went over limit (accurate definition)
            val streak = withContext(Dispatchers.IO) {
                val daysOver     = db.appSessionDao().daysOverLimit()
                val firstSession = db.appSessionDao().firstSessionStart()
                computeStreak(daysOver, firstSession)
            }

            // Reflection data
            val totalAnswered = withContext(Dispatchers.IO) { db.reflectionRecordDao().totalAnswered() }
            val totalWorthIt  = withContext(Dispatchers.IO) { db.reflectionRecordDao().totalWorthIt() }

            // Focus sessions this week
            val focusThisWeek = withContext(Dispatchers.IO) {
                db.focusSessionDao().getRecentFlow()
            }

            // Friction data
            val dayKey = DateUtil.todayKey()
            val frictionToday     = withContext(Dispatchers.IO) { db.frictionAttemptDao().attemptsToday(dayKey) }
            val frictionCompleted = withContext(Dispatchers.IO) { db.frictionAttemptDao().completedToday(dayKey) }

            buildHeatmap(sessions)
            buildStreak(streak)
            buildReflectionStat(totalAnswered, totalWorthIt)
            buildAppBreakdown(sessions)
            buildFrictionStat(frictionToday, frictionCompleted)
        }
    }

    // -------------------------------------------------------------------------
    // Heatmap
    // -------------------------------------------------------------------------

    private fun buildHeatmap(sessions: List<AppSession>) {
        val hourMatrix = Array(7) { LongArray(24) }
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val cal = Calendar.getInstance()
        sessions.forEach { s ->
            cal.timeInMillis = s.sessionStart
            val daysAgo = ((todayMidnight.timeInMillis - cal.timeInMillis) / 86_400_000L)
                .toInt().coerceIn(0, 6)
            val colIdx = 6 - daysAgo
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourMatrix[colIdx][hour] += s.durationMs.coerceAtLeast(60_000L)
        }
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val labels = Array(7) { i ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -(6 - i))
            if (i == 6) "Today" else dayFmt.format(c.time)
        }
        binding.heatmapView.setData(hourMatrix, labels)
    }

    // -------------------------------------------------------------------------
    // Streak (correct: based on session data, not reflection records)
    // -------------------------------------------------------------------------

    private fun buildStreak(streak: Int) {
        binding.tvStreakNumber.text = streak.toString()
        binding.tvStreakLabel.text = if (streak == 1) "day within limits" else "days within limits"
    }

    private fun computeStreak(daysOverLimit: List<String>, firstSessionMs: Long?): Int {
        if (firstSessionMs == null) return 0
        val daysSinceFirst = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - firstSessionMs
        ).toInt().coerceAtLeast(0) + 1

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val overSet = daysOverLimit.toSet()
        val cal = Calendar.getInstance()
        var streak = 0
        for (i in 0 until daysSinceFirst.coerceAtMost(365)) {
            if (fmt.format(cal.time) in overSet) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    // -------------------------------------------------------------------------
    // Reflection score
    // -------------------------------------------------------------------------

    private fun buildReflectionStat(totalAnswered: Int, totalWorthIt: Int) {
        if (totalAnswered > 0) {
            val pct = (totalWorthIt * 100) / totalAnswered
            binding.tvReflectionScore.text = "$pct%"
            binding.tvReflectionLabel.text = "of overrides felt worth it"
        } else {
            binding.tvReflectionScore.text = "—"
            binding.tvReflectionLabel.text = "no override data yet"
        }
    }

    // -------------------------------------------------------------------------
    // Friction stats row (new)
    // -------------------------------------------------------------------------

    private fun buildFrictionStat(attempts: Int, completed: Int) {
        val container = binding.containerAppBreakdown
        if (attempts == 0) return

        addSectionLabel(container, "TODAY'S OVERRIDES")

        val abandoned = attempts - completed
        val row = makeStatRow(
            label = "Override attempts",
            value = attempts.toString()
        )
        container.addView(row)
        container.addView(divider())

        if (completed > 0) {
            container.addView(makeStatRow("Completed friction", completed.toString()))
            container.addView(divider())
        }
        if (abandoned > 0) {
            container.addView(makeStatRow("Abandoned (changed mind)", abandoned.toString()))
            container.addView(divider())
        }
    }

    // -------------------------------------------------------------------------
    // App breakdown
    // -------------------------------------------------------------------------

    private fun buildAppBreakdown(sessions: List<AppSession>) {
        val appTotals = sessions
            .groupBy { it.packageName }
            .mapValues { (_, v) -> v.sumOf { it.durationMs } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val container = binding.containerAppBreakdown
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        if (appTotals.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No usage recorded this week."
                textSize = 14f
                setTextColor(getColor(R.color.text_tertiary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (8 * dp).toInt() }
            }
            container.addView(tv)
            return
        }

        addSectionLabel(container, "TOP APPS THIS WEEK")

        appTotals.forEach { (pkg, ms) ->
            container.addView(makeStatRow(DateUtil.appLabel(pkg), formatMs(ms)))
            container.addView(divider())
        }

        // Total screen time this week
        val totalMs = sessions.sumOf { it.durationMs }
        if (totalMs > 0) {
            addSectionLabel(container, "TOTAL THIS WEEK")
            container.addView(makeStatRow("Screen time", formatMs(totalMs)))
            container.addView(divider())
            val avgMs = totalMs / 7
            container.addView(makeStatRow("Daily average", formatMs(avgMs)))
            container.addView(divider())
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun addSectionLabel(container: LinearLayout, text: String) {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(getColor(R.color.text_disabled))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (20 * dp).toInt(); it.bottomMargin = (8 * dp).toInt() }
        }
        container.addView(tv)
    }

    private fun makeStatRow(label: String, value: String): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = label; textSize = 15f
            setTextColor(getColor(R.color.text_secondary))
        }
        val tvValue = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = value; textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(tvLabel); row.addView(tvValue)
        return row
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(getColor(R.color.divider))
    }

    private fun formatMs(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m"
    }
}

/** 7-column (days) × 24-row (hours) heatmap. */
class HeatmapView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var data: Array<LongArray> = Array(7) { LongArray(24) }
    private var dayLabels: Array<String> = Array(7) { "" }

    private val paintEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1C.toInt() }
    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintDimLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt(); textAlign = Paint.Align.CENTER
    }
    private val paintBrightLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt(); textAlign = Paint.Align.CENTER
    }
    private val cellRect = RectF()

    fun setData(matrix: Array<LongArray>, labels: Array<String> = Array(7) { "" }) {
        data = matrix; dayLabels = labels; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cols = 7; val rows = 24
        val labelH = 26f
        val cellW = width.toFloat() / cols
        val cellH = (height.toFloat() - labelH) / rows
        val gap = 1.5f; val cornerR = 2f
        val labelTextSize = (cellW * 0.30f).coerceIn(9f, 13f)
        paintDimLabel.textSize = labelTextSize
        paintBrightLabel.textSize = labelTextSize

        var maxMs = 1L
        data.forEach { day -> day.forEach { v -> if (v > maxMs) maxMs = v } }

        for (col in 0 until cols) {
            val cx = col * cellW + cellW / 2f
            val paint = if (col == cols - 1) paintBrightLabel else paintDimLabel
            canvas.drawText(dayLabels.getOrElse(col) { "" }, cx, labelH * 0.82f, paint)
        }

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                cellRect.set(
                    col * cellW + gap, labelH + row * cellH + gap,
                    (col + 1) * cellW - gap, labelH + (row + 1) * cellH - gap
                )
                val v = data[col][row]
                if (v == 0L) {
                    canvas.drawRoundRect(cellRect, cornerR, cornerR, paintEmpty)
                } else {
                    val intensity = (v.toFloat() / maxMs).coerceIn(0f, 1f)
                    val b = (0x38 + (intensity * (0xE8 - 0x38)).toInt()).coerceIn(0x38, 0xE8)
                    paintActive.color = (0xFF shl 24) or (b shl 16) or (b shl 8) or b
                    canvas.drawRoundRect(cellRect, cornerR, cornerR, paintActive)
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val totalH = (28f + (w.toFloat() / 7f) * 0.85f * 24f).toInt()
        setMeasuredDimension(w, totalH)
    }
}
