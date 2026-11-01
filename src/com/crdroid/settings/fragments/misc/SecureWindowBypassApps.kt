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
import android.os.UserHandle
import android.provider.Settings
import com.android.settings.R
import com.crdroid.settings.preferences.BaseAppListSettingsFragment

/**
 * IchthysOS: picks the apps whose own FLAG_SECURE is ignored ("Allow screenshots in selected
 * apps"). Stored as a comma list in Settings.Secure for the current user; WindowManagerService
 * watches the key and refreshes the windows' secure state at once, so no app restart is needed.
 */
class SecureWindowBypassApps : BaseAppListSettingsFragment() {

    override fun getTitleResId() = R.string.secure_window_bypass_apps_title

    // Protected screens live in user apps and preinstalled ones (a bank, a wallet) alike.
    override fun excludeSystemApps() = false

    // WindowManagerService applies the change to the live windows.
    override fun restartPackageOnChange() = false

    override fun getInitialCheckedList(): List<String> = read(requireContext())

    override fun onListUpdate(packageName: String, isChecked: Boolean) {
        val packages = read(requireContext()).toMutableSet()
        if (isChecked) packages.add(packageName) else packages.remove(packageName)
        Settings.Secure.putStringForUser(
            requireContext().contentResolver,
            KEY_PACKAGES,
            packages.joinToString(","),
            UserHandle.USER_CURRENT
        )
    }

    companion object {
        /** Repeats WindowManagerService.SECURE_WINDOW_BYPASS_PKGS (a hidden constant). */
        const val KEY_PACKAGES = "ichthys_secure_window_bypass_pkgs"

        @JvmStatic
        fun read(context: Context): List<String> =
            Settings.Secure.getStringForUser(
                    context.contentResolver, KEY_PACKAGES, UserHandle.USER_CURRENT)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
    }
}
