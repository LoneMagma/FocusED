package com.focused.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focused.app.R
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.ActivityLog
import com.focused.app.databinding.ActivityLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ActivityLogActivity
 *
 * Shows every action Focused has taken, newest first.
 * This is the trust screen — the user can verify the app is doing exactly
 * what it claims and nothing else.
 */
class ActivityLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LogAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        observeLogs()
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            FocusedDatabase.get(this@ActivityLogActivity)
                .activityLogDao()
                .getAllFlow()
                .collectLatest { logs ->
                    adapter.submitList(logs)
                    binding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    // -------------------------------------------------------------------------
    // RecyclerView Adapter
    // -------------------------------------------------------------------------

    inner class LogAdapter : ListAdapter<ActivityLog, LogAdapter.ViewHolder>(DiffCallback()) {

        private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEvent: TextView = view.findViewById(R.id.tv_log_event)
            val tvPackage: TextView = view.findViewById(R.id.tv_log_package)
            val tvTime: TextView = view.findViewById(R.id.tv_log_time)
            val tvDetail: TextView = view.findViewById(R.id.tv_log_detail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = getItem(position)

            holder.tvEvent.text = friendlyEventName(log.eventType)
            holder.tvTime.text = dateFormat.format(Date(log.timestamp))

            holder.tvPackage.text = log.packageName?.let { friendlyAppName(it) } ?: ""
            holder.tvPackage.visibility = if (log.packageName != null) View.VISIBLE else View.GONE

            holder.tvDetail.text = log.detail ?: ""
            holder.tvDetail.visibility = if (log.detail != null) View.VISIBLE else View.GONE
        }

        private fun friendlyEventName(type: String): String = when (type) {
            "APP_BLOCKED"        -> "App blocked"
            "FRICTION_STARTED"   -> "Disable attempted"
            "FRICTION_TIER_1"    -> "Passed cooldown"
            "FRICTION_TIER_2"    -> "Passed string check"
            "FRICTION_COMPLETED" -> "Focused disabled"
            "FRICTION_ABANDONED" -> "Disable cancelled"
            "FOCUS_STARTED"      -> "Focus session started"
            "FOCUS_ENDED"        -> "Focus session ended"
            "SCROLL_INTERVENED"  -> "Scroll reminder shown"
            "INTENTION_SET"      -> "Intention selected"
            "SERVICE_STARTED"    -> "Focused started"
            "SERVICE_STOPPED"    -> "Focused stopped"
            else                 -> type
        }

        private fun friendlyAppName(pkg: String): String = when (pkg) {
            "com.instagram.android"    -> "Instagram"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            "com.twitter.android"      -> "X (Twitter)"
            "com.snapchat.android"     -> "Snapchat"
            "com.reddit.frontpage"     -> "Reddit"
            "com.facebook.katana"      -> "Facebook"
            else                       -> pkg
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ActivityLog>() {
        override fun areItemsTheSame(a: ActivityLog, b: ActivityLog) = a.id == b.id
        override fun areContentsTheSame(a: ActivityLog, b: ActivityLog) = a == b
    }
}
