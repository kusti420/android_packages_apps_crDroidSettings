/*
 * Copyright (C) 2025 IchthysOS
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

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.utils.SystemUtils;

/**
 * IchthysOS tab: settings that are fully custom to IchthysOS and do NOT exist in stock crDroid.
 * crDroid-inherited features stay in their own tabs; only genuinely-new additions live here.
 */
@SearchIndexable
public class IchthysOS extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysOS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_ichthys);

        // recents_type (Dagger @Provides, read once at start) and custom_pitch_black (applied on
        // theme re-eval) only take effect after a SystemUI restart, so prompt for one on change.
        Preference.OnPreferenceChangeListener restartListener = (pref, val) -> {
            SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
            return true;
        };
        Preference recents = findPreference("recents_type");
        if (recents != null) recents.setOnPreferenceChangeListener(restartListener);
        Preference pitchBlack = findPreference("custom_pitch_black");
        if (pitchBlack != null) pitchBlack.setOnPreferenceChangeListener(restartListener);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver, "disable_all_vibration", 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "recents_type", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_pitch_black", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_volume_horizontal", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_volume_steps", 0, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_ichthys);
}
