package com.focused.app.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * OnboardingPagerAdapter
 *
 * Creates the 5 onboarding screen fragments.
 * The onComplete callback is passed to each fragment so they can signal
 * when their CTA button has been tapped.
 */
class OnboardingPagerAdapter(
    activity: FragmentActivity,
    private val onComplete: (screenIndex: Int) -> Unit
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 5

    override fun createFragment(position: Int): Fragment {
        return OnboardingScreenFragment.newInstance(position, onComplete)
    }
}
