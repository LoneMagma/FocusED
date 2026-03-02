package com.focused.app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.focused.app.databinding.FragmentOnboardingScreenBinding
import android.widget.Button

/**
 * OnboardingScreenFragment
 *
 * One fragment handles all 5 onboarding screens.
 * Content is driven by the screen index — no separate fragment class per screen.
 *
 * Each screen has:
 *   - A large icon (vector drawable)
 *   - A title
 *   - A body text (multi-line, plain language)
 *   - A CTA button ("Continue" or "I agree — let's go" on screen 4)
 *
 * Screen 4 (The Compact) also shows a subtle "what you're agreeing to" summary
 * above the button.
 */
class OnboardingScreenFragment : Fragment() {

    companion object {
        private const val ARG_SCREEN = "screen_index"

        fun newInstance(
            screenIndex: Int,
            onComplete: (Int) -> Unit
        ): OnboardingScreenFragment {
            return OnboardingScreenFragment().apply {
                arguments = Bundle().apply { putInt(ARG_SCREEN, screenIndex) }
                this.onComplete = onComplete
            }
        }
    }

    private var _binding: FragmentOnboardingScreenBinding? = null
    private val binding get() = _binding!!

    private var onComplete: ((Int) -> Unit)? = null

    data class ScreenContent(
        val iconRes: Int,
        val title: String,
        val body: String,
        val ctaText: String,
        val compactSummary: String? = null   // only on screen 4
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenIndex = arguments?.getInt(ARG_SCREEN) ?: 0
        val content = contentFor(screenIndex)

        binding.ivIcon.setImageResource(content.iconRes)
        binding.tvTitle.text = content.title
        binding.tvBody.text = content.body
        binding.btnCta.text = content.ctaText

        // Compact summary only on screen 4
        if (content.compactSummary != null) {
            binding.tvCompactSummary.visibility = View.VISIBLE
            binding.tvCompactSummary.text = content.compactSummary
        } else {
            binding.tvCompactSummary.visibility = View.GONE
        }

        binding.btnCta.setOnClickListener {
            onComplete?.invoke(screenIndex)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // -------------------------------------------------------------------------
    // Screen Content
    // All copy lives here — easy to edit without touching layout or logic.
    // -------------------------------------------------------------------------

    private fun contentFor(index: Int): ScreenContent {
        return when (index) {
            0 -> ScreenContent(
                iconRes = com.focused.app.R.drawable.ic_onboard_what,
                title = "FocusED helps you use your phone on purpose",
                body = "It gently interrupts doom scrolling, lets you set session limits for apps like Instagram and YouTube, and keeps a Focus mode so you can actually finish things.\n\nNo content from other apps is ever read. No data leaves your device.",
                ctaText = "Continue"
            )

            1 -> ScreenContent(
                iconRes = com.focused.app.R.drawable.ic_onboard_cansee,
                title = "Here's what Focused can see",
                body = "• Which app is open right now\nFocused uses this to know when to apply your limits.\n\n• How long you've used each app today\nFocused uses this to enforce session budgets you set.\n\n• When you start scrolling and for how long\nFocused uses this to offer a break when you've been scrolling a while.\n\nThat's the complete list.",
                ctaText = "Continue"
            )

            2 -> ScreenContent(
                iconRes = com.focused.app.R.drawable.ic_onboard_cannotsee,
                title = "Here's what Focused cannot see",
                body = "FocusED has no access to:\n\n• Your messages, DMs, or emails\n• Your photos or camera\n• Your passwords or payment details\n• Anything you type in any app\n• Any content inside other apps\n\nIt only sees app names — never what's inside them. This is a hard technical limit, not a promise.",
                ctaText = "Continue"
            )

            3 -> ScreenContent(
                iconRes = com.focused.app.R.drawable.ic_onboard_device,
                title = "Your data never leaves your device",
                body = "FocusED has no servers, no account, and no analytics.\n\nEverything — your limits, your session history, your activity log — lives in a local database on this phone only.\n\nFocused doesn't even request internet permission.",
                ctaText = "Continue"
            )

            4 -> ScreenContent(
                iconRes = com.focused.app.R.drawable.ic_onboard_compact,
                title = "The compact",
                body = "FocusED does exactly what you configure and nothing else. You're always in control — every limit, every rule, every restriction is set by you and can be changed by you.\n\nYou can review every action Focused has ever taken in the Activity Log inside the app.",
                ctaText = "I understand — let's go",
                compactSummary = "By continuing you confirm you've read what Focused can and cannot access."
            )

            else -> contentFor(0)
        }
    }
}
