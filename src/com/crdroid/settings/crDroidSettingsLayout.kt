/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: 2026 IchthysOS
 * SPDX-License-Identifier: GPL-3.0
 */

package com.crdroid.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View

import androidx.lifecycle.lifecycleScope

import com.android.internal.logging.nano.MetricsProto
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.ichthys.ShoalStyle

import com.crdroid.settings.fragments.Background
import com.crdroid.settings.fragments.Buttons
import com.crdroid.settings.fragments.IchthysApps
import com.crdroid.settings.fragments.IchthysSystem
import com.crdroid.settings.fragments.InstallCompat
import com.crdroid.settings.fragments.LockScreen
import com.crdroid.settings.fragments.Navigation
import com.crdroid.settings.fragments.Notifications
import com.crdroid.settings.fragments.QuickSettings
import com.crdroid.settings.fragments.Sound
import com.crdroid.settings.fragments.StatusBar
import com.crdroid.settings.fragments.recents.SlimRecents
import com.crdroid.settings.fragments.themes.ThemeStudio
import com.crdroid.settings.fragments.ui.DozeSettings
import com.crdroid.settings.fragments.ui.EdgeLightSettings
import com.crdroid.settings.fragments.ui.GradientSettings
import com.crdroid.settings.fragments.ui.MonetSettings
import com.crdroid.settings.fragments.ui.SmartPixels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one IchthysOS entry on the Settings homepage (ichthys_hub.xml): the Protected card as the
 * header, which opens Guard, and four rows.
 *
 *  - Look: Theme Studio and the pages it links to;
 *  - Privacy: Guard;
 *  - Apps: [IchthysApps];
 *  - System: [IchthysSystem].
 *
 * It replaces the Protected card, the Guard row and the eleven-tab pager this class used to be.
 * The class keeps its name because the homepage XMLs and SettingsGateway refer to it.
 */
class crDroidSettingsLayout : SettingsPreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        addPreferencesFromResource(R.xml.ichthys_hub)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // IchthysOS Shoal: the row icons become outlined badges in the accent.
        ShoalStyle.badgeAll(preferenceScreen, ShoalStyle.accent(view.context))
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

    /**
     * Restores the defaults of the Look, Apps and System settings. Guard's privacy settings and
     * the ichthys.nix generations are not touched.
     */
    private fun resetAll(context: Context) {
        lifecycleScope.launch(Dispatchers.Default) {
            // Look
            ThemeStudio.reset(context)
            StatusBar.reset(context)
            QuickSettings.reset(context)
            Navigation.reset(context)
            LockScreen.reset(context)
            DozeSettings.reset(context)
            EdgeLightSettings.reset(context)
            GradientSettings.reset(context)
            MonetSettings.reset(context)
            SlimRecents.reset(context)
            // Apps
            Background.reset(context)
            InstallCompat.reset(context)
            // System
            Buttons.reset(context)
            Notifications.reset(context)
            Sound.reset(context)
            SmartPixels.reset(context)
            IchthysSystem.reset(context)

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
    }
}
