package com.focused.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.focused.app.BuildConfig
import com.focused.app.R

/**
 * About bottom sheet — accessible via the ≡ button in the top bar.
 *
 * Contains:
 *   - App name + version
 *   - Short description of what the app does / doesn't do
 *   - GitHub source link
 *   - Bug report link (GitHub issues)
 *   - Privacy note (no data leaves the device)
 */
class AboutBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "AboutBottomSheet"
        private const val GITHUB_URL = "https://github.com/LoneMagma/focused"
        private const val ISSUES_URL = "https://github.com/LoneMagma/focused/issues/new?template=bug_report.md"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = requireContext().resources.displayMetrics.density
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (40 * dp).toInt())
            setBackgroundColor(requireContext().getColor(R.color.bg_card))
        }

        // Drag handle
        val handle = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt()).also {
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
                it.bottomMargin = (20 * dp).toInt()
            }
            setBackgroundColor(requireContext().getColor(R.color.divider))
            background = requireContext().getDrawable(R.drawable.bg_drag_handle)
        }
        root.addView(handle)

        // App name + version
        root.addView(makeText(
            "FocusED",
            20f, R.color.text_primary, bold = true,
            bottomMargin = 2
        ))
        root.addView(makeText(
            "Version ${BuildConfig.VERSION_NAME}",
            13f, R.color.text_tertiary,
            bottomMargin = 20
        ))

        // Divider
        root.addView(divider())

        // Description
        root.addView(makeText(
            "FocusED enforces your own limits on social apps using an Accessibility Service that only reads which app is in the foreground. It cannot see messages, photos, or any content inside apps.",
            14f, R.color.text_secondary,
            topMargin = 16, bottomMargin = 16
        ))

        root.addView(makeText(
            "No data leaves your device. No account required. No analytics.",
            14f, R.color.text_secondary,
            bottomMargin = 20
        ))

        root.addView(divider())

        // Links
        root.addView(makeLink("View source on GitHub →", GITHUB_URL, topMargin = 16))
        root.addView(makeLink("Report a bug or issue →", ISSUES_URL, topMargin = 12))

        return root
    }

    private fun makeText(
        text: String,
        size: Float,
        colorRes: Int,
        bold: Boolean = false,
        topMargin: Int = 0,
        bottomMargin: Int = 0
    ) = TextView(requireContext()).apply {
        this.text = text
        textSize = size
        setTextColor(requireContext().getColor(colorRes))
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = (topMargin * requireContext().resources.displayMetrics.density).toInt()
            it.bottomMargin = (bottomMargin * requireContext().resources.displayMetrics.density).toInt()
        }
        setLineSpacing(0f, 1.4f)
    }

    private fun makeLink(text: String, url: String, topMargin: Int = 0) =
        TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTextColor(requireContext().getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * requireContext().resources.displayMetrics.density).toInt()
            ).also {
                it.topMargin = (topMargin * requireContext().resources.displayMetrics.density).toInt()
            }
            gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        setBackgroundColor(requireContext().getColor(R.color.divider))
    }
}
