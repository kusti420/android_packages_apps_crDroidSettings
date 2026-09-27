/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

import com.crdroid.settings.fragments.*

import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class crDroidSettingsLayout : SettingsPreferenceFragment() {

    private var pager: ViewPager2? = null
    private var tabs: TabLayout? = null

    private val shoalBadger =
        object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: androidx.fragment.app.FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
            ) {
                val page = f as? androidx.preference.PreferenceFragmentCompat ?: return
                val color =
                    if (f is IchthysOS) com.android.settings.ichthys.ShoalStyle.accent(v.context)
                    else com.android.settings.ichthys.ShoalStyle.hue(
                        TAB_FRAGMENTS.indexOf(f.javaClass))
                com.android.settings.ichthys.ShoalStyle.badgeAll(page.preferenceScreen, color)
            }
        }
    private var mediator: TabLayoutMediator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        requireActivity().setTitle(R.string.crdroid_settings_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // IchthysOS: inflate this layout through a MaterialComponents theme.
        // com.google.android.material.tabs.TabLayout runs ThemeEnforcement in its constructor and
        // throws IllegalArgumentException ("requires your app theme to be Theme.AppCompat") unless
        // the context it is constructed with has an AppCompat-descendant theme. Settings' activity
        // theme is a platform theme, and the android:theme attribute on the TabLayout in
        // crdroid_settings.xml is not enough on its own - the host's inflater factory builds the
        // view without applying it, so the constructor still saw the bare activity theme and the
        // whole IchthysOS tab crashed on open. Cloning the inflater onto a themed context fixes it
        // for every view in this layout. The child preference fragments in the pager are created
        // separately and keep the activity's own theme, so nothing else changes appearance.
        val themedInflater = inflater.cloneInContext(
                ContextThemeWrapper(requireContext(), R.style.CrDroidMaterialOverlay))
        val root = themedInflater.inflate(R.layout.crdroid_settings, container, false)

        tabs = root.findViewById(R.id.tabs)
        pager = root.findViewById(R.id.pager)

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = TAB_TITLES.size

            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> StatusBar()
                1 -> QuickSettings()
                2 -> LockScreen()
                3 -> Buttons()
                4 -> Navigation()
                5 -> UserInterface()
                6 -> Notifications()
                7 -> Sound()
                8 -> Miscellaneous()
                9 -> IchthysOS()
                10 -> About()
                else -> StatusBar()
            }
        }

        pager!!.adapter = adapter
        pager!!.offscreenPageLimit = 1

        // IchthysOS Shoal: every tab page's row icons become outlined badges, one hue per tab
        // (the IchthysOS tab takes the accent). Done here, once per page view, instead of in each
        // page fragment.
        childFragmentManager.registerFragmentLifecycleCallbacks(shoalBadger, false)

        mediator = TabLayoutMediator(tabs!!, pager!!) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.also { it.attach() }

        return root
    }

    override fun onDestroyView() {
        childFragmentManager.unregisterFragmentLifecycleCallbacks(shoalBadger)
        mediator?.detach()
        mediator = null
        pager = null
        tabs = null
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset_settings_title)
            .setIcon(R.drawable.ic_reset)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_RESET) {
            showResetAllDialog(requireContext())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showResetAllDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(R.string.ok) { _, _ -> resetAll(context) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resetAll(context: Context) {
        lifecycleScope.launch(Dispatchers.Default) {
            Buttons.reset(context)
            IchthysOS.reset(context)
            LockScreen.reset(context)
            Miscellaneous.reset(context)
            Navigation.reset(context)
            Notifications.reset(context)
            QuickSettings.reset(context)
            Sound.reset(context)
            StatusBar.reset(context)
            UserInterface.reset(context)

            withContext(Dispatchers.Main) {
                requireActivity().finish()
                startActivity(requireActivity().intent)
            }
        }
    }

    override fun getMetricsCategory(): Int =
        MetricsProto.MetricsEvent.VIEW_UNKNOWN

    companion object {
        private const val MENU_RESET = Menu.FIRST

        private val TAB_TITLES = intArrayOf(
            R.string.statusbar_title,
            R.string.quicksettings_title,
            R.string.lockscreen_title,
            R.string.button_title,
            R.string.navigation_title,
            R.string.ui_title,
            R.string.notifications_title,
            R.string.sound_title,
            R.string.misc_title,
            R.string.ichthys_title,
            R.string.about_crdroid
        )

        /** Tab page classes in TAB_TITLES order (for the per-tab badge hue). */
        private val TAB_FRAGMENTS = listOf(
            StatusBar::class.java, QuickSettings::class.java, LockScreen::class.java,
            Buttons::class.java, Navigation::class.java, UserInterface::class.java,
            Notifications::class.java, Sound::class.java, Miscellaneous::class.java,
            IchthysOS::class.java, About::class.java
        )
    }
}
