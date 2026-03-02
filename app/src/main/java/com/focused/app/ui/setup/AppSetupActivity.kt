package com.focused.app.ui.setup

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.BudgetRule
import com.focused.app.data.model.IntentionOption
import com.focused.app.databinding.ActivityAppSetupBinding
import com.focused.app.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSetupBinding

    private val commonPackages = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.facebook.katana"
    )

    private val limitSteps = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
    // 0 = unlimited, then 1..10 opens
    private val opensSteps = listOf(0, 1, 2, 3, 4, 5, 7, 10, 15, 20)

    data class AppItem(
        val packageName: String,
        val label: String,
        var isMonitored: Boolean,
        var dailyLimitMin: Int = 20,
        var maxOpens: Int = 0,
        var shortFormLimitMin: Int = 0,
        var downtimeStartMin: Int = -1,
        var downtimeEndMin: Int = -1
    )

    private val items = mutableListOf<AppItem>()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnAddApp.setOnClickListener { showAppPicker() }
        adapter = AppListAdapter(items, limitSteps, opensSteps) { item, field, value ->
            onItemChanged(item, field, value)
        }
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter
        loadApps()
    }

    private fun showAppPicker() {
        lifecycleScope.launch {
            val pm = packageManager
            val installedApps = withContext(Dispatchers.IO) {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { info -> Pair(info.packageName, pm.getApplicationLabel(info).toString()) }
                    .sortedBy { it.second }
            }
            val currentPkgs = items.map { it.packageName }.toSet()
            val available = installedApps.filter { it.first !in currentPkgs }
            if (available.isEmpty()) {
                android.widget.Toast.makeText(this@AppSetupActivity, "No more apps to add", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = available.map { it.second }.toTypedArray()
            AlertDialog.Builder(this@AppSetupActivity)
                .setTitle("Add an app")
                .setItems(names) { _, idx ->
                    val (pkg, label) = available[idx]
                    items.add(AppItem(packageName = pkg, label = label, isMonitored = false))
                    adapter.notifyItemInserted(items.size - 1)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val db = FocusedDatabase.get(this@AppSetupActivity)
            val existingRules = withContext(Dispatchers.IO) {
                db.budgetRuleDao().getAll().associateBy { it.packageName }
            }
            val pm = packageManager
            val appItems = mutableListOf<AppItem>()
            withContext(Dispatchers.IO) {
                commonPackages.forEach { pkg ->
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(info).toString()
                        val rule = existingRules[pkg]
                        appItems.add(AppItem(
                            packageName = pkg,
                            label = label,
                            isMonitored = rule != null && rule.isActive,
                            dailyLimitMin = rule?.maxSessionDurationMs?.div(60_000L)?.toInt()?.coerceIn(5, 120) ?: 20,
                            maxOpens = rule?.maxOpensPerDay ?: 0,
                            shortFormLimitMin = rule?.shortFormLimitMs?.div(60_000L)?.toInt() ?: 0,
                            downtimeStartMin = rule?.downtimeStartMin ?: -1,
                            downtimeEndMin = rule?.downtimeEndMin ?: -1
                        ))
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                // Also include non-common apps that have rules
                existingRules.values
                    .filter { it.packageName !in commonPackages }
                    .forEach { rule ->
                        try {
                            val info = pm.getApplicationInfo(rule.packageName, 0)
                            val label = pm.getApplicationLabel(info).toString()
                            appItems.add(AppItem(
                                packageName = rule.packageName,
                                label = label,
                                isMonitored = rule.isActive,
                                dailyLimitMin = rule.maxSessionDurationMs.div(60_000L).toInt().coerceIn(5, 120),
                                maxOpens = rule.maxOpensPerDay,
                                shortFormLimitMin = rule.shortFormLimitMs.div(60_000L).toInt(),
                                downtimeStartMin = rule.downtimeStartMin,
                                downtimeEndMin = rule.downtimeEndMin
                            ))
                        } catch (_: PackageManager.NameNotFoundException) {}
                    }
            }
            items.clear()
            items.addAll(appItems)
            adapter.notifyDataSetChanged()
        }
    }

    private fun onItemChanged(item: AppItem, field: String, value: Any) {
        lifecycleScope.launch {
            val db = FocusedDatabase.get(this@AppSetupActivity)
            when (field) {
                "toggle" -> {
                    item.isMonitored = value as Boolean
                    if (item.isMonitored) {
                        db.budgetRuleDao().insert(BudgetRule(
                            packageName = item.packageName,
                            appLabel = item.label,
                            maxSessionsPerDay = 999,
                            maxSessionDurationMs = item.dailyLimitMin * 60_000L,
                            maxOpensPerDay = item.maxOpens,
                            shortFormLimitMs = item.shortFormLimitMin * 60_000L,
                            downtimeStartMin = item.downtimeStartMin,
                            downtimeEndMin = item.downtimeEndMin,
                            intentionEnabled = true
                        ))
                        val opts = DateUtil.defaultIntentionsFor(item.packageName)
                            .mapIndexed { i, lbl -> IntentionOption(packageName = item.packageName, label = lbl, sortOrder = i) }
                        db.intentionOptionDao().insertAll(opts)
                    } else {
                        db.budgetRuleDao().setActive(item.packageName, false)
                    }
                }
                "limit" -> {
                    item.dailyLimitMin = value as Int
                    saveRule(item, db)
                }
                "opens" -> {
                    item.maxOpens = value as Int
                    saveRule(item, db)
                }
                "shortform" -> {
                    item.shortFormLimitMin = value as Int
                    saveRule(item, db)
                }
                "downtime" -> {
                    val pair = value as Pair<*, *>
                    item.downtimeStartMin = pair.first as Int
                    item.downtimeEndMin = pair.second as Int
                    saveRule(item, db)
                }
            }
        }
    }

    private suspend fun saveRule(item: AppItem, db: FocusedDatabase) {
        val existing = db.budgetRuleDao().getByPackage(item.packageName) ?: return
        db.budgetRuleDao().update(existing.copy(
            maxSessionDurationMs = item.dailyLimitMin * 60_000L,
            maxOpensPerDay = item.maxOpens,
            shortFormLimitMs = item.shortFormLimitMin * 60_000L,
            downtimeStartMin = item.downtimeStartMin,
            downtimeEndMin = item.downtimeEndMin
        ))
    }

    // -------------------------------------------------------------------------

    inner class AppListAdapter(
        private val data: List<AppItem>,
        private val steps: List<Int>,
        private val opensSteps: List<Int>,
        private val onChange: (AppItem, String, Any) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvLabel: TextView = view.findViewById(R.id.tv_app_label)
            val switchMonitor: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switch_monitor)
            val layoutControls: View = view.findViewById(R.id.layout_controls)
            val tvLimitValue: TextView = view.findViewById(R.id.tv_sessions_value)
            val seekLimit: SeekBar = view.findViewById(R.id.seek_sessions)
            val tvOpensValue: TextView = view.findViewById(R.id.tv_opens_value)
            val btnOpensMinus: android.widget.Button = view.findViewById(R.id.btn_opens_minus)
            val btnOpensPlus: android.widget.Button = view.findViewById(R.id.btn_opens_plus)
            val tvShortformLabel: TextView = view.findViewById(R.id.tv_shortform_label)
            val tvShortformValue: TextView = view.findViewById(R.id.tv_shortform_value)
            val seekShortform: SeekBar = view.findViewById(R.id.seek_shortform)
            val tvDowntimeValue: TextView = view.findViewById(R.id.tv_downtime_value)
            val btnSetDowntime: android.widget.Button = view.findViewById(R.id.btn_set_downtime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_setup, parent, false)
            return VH(v)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = data[pos]
            h.tvLabel.text = item.label
            h.switchMonitor.isChecked = item.isMonitored
            h.layoutControls.visibility = if (item.isMonitored) View.VISIBLE else View.GONE

            // Short-form label by app
            val shortFormName = when (item.packageName) {
                "com.instagram.android", "com.facebook.katana" -> "REELS LIMIT"
                "com.google.android.youtube" -> "SHORTS LIMIT"
                "com.zhiliaoapp.musically" -> "FEED LIMIT"
                else -> "SHORT-FORM LIMIT"
            }
            h.tvShortformLabel.text = shortFormName

            // Daily limit seekbar
            h.seekLimit.max = steps.size - 1
            val stepIdx = steps.indexOfFirst { it >= item.dailyLimitMin }.takeIf { it >= 0 } ?: steps.size - 1
            h.seekLimit.progress = stepIdx
            h.tvLimitValue.text = "${item.dailyLimitMin} min / day"

            // Opens counter
            h.tvOpensValue.text = if (item.maxOpens == 0) "Unlimited" else "${item.maxOpens} opens / day"
            h.btnOpensMinus.setOnClickListener {
                if (item.maxOpens > 0) {
                    item.maxOpens--
                    h.tvOpensValue.text = if (item.maxOpens == 0) "Unlimited" else "${item.maxOpens} opens / day"
                    onChange(item, "opens", item.maxOpens)
                }
            }
            h.btnOpensPlus.setOnClickListener {
                if (item.maxOpens < 20) {
                    item.maxOpens++
                    h.tvOpensValue.text = "${item.maxOpens} opens / day"
                    onChange(item, "opens", item.maxOpens)
                }
            }

            // Short-form limit seekbar (same steps as daily, 0 = same as daily)
            val sfSteps = listOf(0) + steps  // 0 = same as daily
            h.seekShortform.max = sfSteps.size - 1
            val sfIdx = sfSteps.indexOfFirst { it >= item.shortFormLimitMin }.takeIf { it >= 0 } ?: 0
            h.seekShortform.progress = sfIdx
            h.tvShortformValue.text = if (item.shortFormLimitMin == 0) "Same as daily limit"
                                      else "${item.shortFormLimitMin} min / day"

            // Downtime
            h.tvDowntimeValue.text = if (item.downtimeStartMin < 0) "Off"
            else "${minToTime(item.downtimeStartMin)} – ${minToTime(item.downtimeEndMin)}"

            // Listeners
            h.switchMonitor.setOnCheckedChangeListener { _, checked ->
                h.layoutControls.visibility = if (checked) View.VISIBLE else View.GONE
                onChange(item, "toggle", checked)
            }
            h.seekLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val mins = steps.getOrElse(p) { steps.last() }
                    h.tvLimitValue.text = "$mins min / day"
                    if (fromUser) onChange(item, "limit", mins)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
            h.seekShortform.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val mins = sfSteps.getOrElse(p) { 0 }
                    h.tvShortformValue.text = if (mins == 0) "Same as daily limit" else "$mins min / day"
                    if (fromUser) onChange(item, "shortform", mins)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
            h.btnSetDowntime.setOnClickListener {
                showDowntimePicker(h, item)
            }
        }

        private fun showDowntimePicker(h: VH, item: AppItem) {
            // First pick: start time
            val startHour = if (item.downtimeStartMin > 0) item.downtimeStartMin / 60 else 22
            val startMin = if (item.downtimeStartMin > 0) item.downtimeStartMin % 60 else 0
            TimePickerDialog(this@AppSetupActivity, { _, startH, startM ->
                val startTotal = startH * 60 + startM
                // Second pick: end time
                TimePickerDialog(this@AppSetupActivity, { _, endH, endM ->
                    val endTotal = endH * 60 + endM
                    item.downtimeStartMin = startTotal
                    item.downtimeEndMin = endTotal
                    h.tvDowntimeValue.text = "${minToTime(startTotal)} – ${minToTime(endTotal)}"
                    onChange(item, "downtime", Pair(startTotal, endTotal))
                }, 8, 0, true).show()
            }, startHour, startMin, true).show()
        }

        private fun minToTime(min: Int): String {
            val h = min / 60
            val m = min % 60
            return String.format("%d:%02d", h, m)
        }
    }
}
