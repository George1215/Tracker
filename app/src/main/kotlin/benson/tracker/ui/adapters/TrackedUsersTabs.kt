package benson.tracker.ui.adapters

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import benson.tracker.ui.fragments.TrackedUsersMonthlyTab
import benson.tracker.ui.fragments.TrackedUsersTodayTab

class TrackedUsersTabs(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {

    override fun getItem(position: Int): Fragment? = when (position) {
        0 -> TrackedUsersTodayTab()
        1 -> TrackedUsersMonthlyTab()
        else -> null
    }

    override fun getCount(): Int = 2

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "Today"
            1 -> "Monthly"
            else -> super.getPageTitle(position)
        }
    }
}