/*
 * Copyright (C) 2026 IchthysOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.crdroid.settings.fragments.misc

import android.content.Context
import android.provider.Settings
import com.android.settings.R
import com.crdroid.settings.preferences.BaseAppListSettingsFragment

/**
 * IchthysOS: picks the apps "Unrestricted background" covers in "Selected apps" mode. Stored as a
 * comma list in Settings.Global; UnrestrictedBackgroundController applies changes live.
 */
class UnrestrictedBackgroundApps : BaseAppListSettingsFragment() {

    override fun getTitleResId() = R.string.unrestricted_background_apps_title

    // Users pick their own apps, but e.g. a preinstalled sync app can need it too.
    override fun excludeSystemApps() = false

    override fun restartPackageOnChange() = false

    override fun getInitialCheckedList(): List<String> = read(requireContext())

    override fun onListUpdate(packageName: String, isChecked: Boolean) {
        val packages = read(requireContext()).toMutableSet()
        if (isChecked) packages.add(packageName) else packages.remove(packageName)
        Settings.Global.putString(
            requireContext().contentResolver,
            KEY_PACKAGES,
            packages.joinToString(",")
        )
    }

    companion object {
        const val KEY_PACKAGES = "ichthys_unrestricted_bg_pkgs"

        @JvmStatic
        fun read(context: Context): List<String> =
            Settings.Global.getString(context.contentResolver, KEY_PACKAGES)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
    }
}
