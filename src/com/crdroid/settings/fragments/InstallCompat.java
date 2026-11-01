/*
 * Copyright (C) 2026 IchthysOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.misc.SecureWindowBypassApps;

/**
 * IchthysOS &gt; Apps &gt; Install &amp; compatibility: the owner's bypasses of the blocks Android
 * puts on installing and using apps. Installing: apps built for old Android (the low-target-SDK
 * install block) and downgrades. Screenshots and screen recording: a per-app list and a
 * device-wide switch that stop windows being treated as secure. Every row is read by the
 * platform itself (InstallPackageHelper, WindowManagerService); this page only sets the keys.
 */
@SearchIndexable
public class InstallCompat extends SettingsPreferenceFragment {

    public static final String TAG = "InstallCompat";

    private static final String KEY_LOW_TARGET_SDK = "pm_low_target_sdk_allowed";
    private static final String KEY_DOWNGRADE = "pm_downgrade_allowed";
    private static final String KEY_BYPASS_APPS = "ichthys_secure_window_bypass_apps";
    private static final String KEY_DISABLE_SECURE_WINDOWS = Settings.Secure.DISABLE_SECURE_WINDOWS;

    private Preference mBypassApps;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_install);

        mBypassApps = findPreference(KEY_BYPASS_APPS);
    }

    // The picker row carries the count and, in the same breath, what the list does.
    private void updateBypassApps() {
        if (mBypassApps == null) return;
        final int count = SecureWindowBypassApps.read(getContext()).size();
        final String counted = count == 0
                ? getString(R.string.secure_window_bypass_apps_none)
                : getResources().getQuantityString(
                        R.plurals.secure_window_bypass_apps_count, count, count);
        mBypassApps.setSummary(getString(R.string.secure_window_bypass_apps_summary, counted));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the app picker: refresh the count.
        updateBypassApps();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent. The danger rows
        // (Danger*Preference) decline the badge and keep a white glyph on their red card.
        ShoalStyle.badgeAll(getPreferenceScreen(), ShoalStyle.accent(view.getContext()));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // The install blocks are device-wide (Settings.Global): back on.
        Settings.Global.putInt(resolver, KEY_LOW_TARGET_SDK, 0);
        Settings.Global.putInt(resolver, KEY_DOWNGRADE, 0);
        // Secure windows: every protection back, no apps exempted.
        Settings.Secure.putIntForUser(resolver, KEY_DISABLE_SECURE_WINDOWS, 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putStringForUser(resolver, SecureWindowBypassApps.KEY_PACKAGES, null,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.ichthys_install);
}
