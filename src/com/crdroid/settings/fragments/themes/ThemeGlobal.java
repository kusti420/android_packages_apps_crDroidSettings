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
package com.crdroid.settings.fragments.themes;

import android.os.Bundle;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.utils.SystemUtils;

/** Themes &gt; Global theme: system-wide appearance (pitch black, ...). */
@SearchIndexable
public class ThemeGlobal extends SettingsPreferenceFragment {

    public static final String TAG = "ThemeGlobal";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme_global);

        // custom_pitch_black is applied on a theme re-eval, so prompt a SystemUI restart on change.
        Preference pitchBlack = findPreference("custom_pitch_black");
        if (pitchBlack != null) {
            pitchBlack.setOnPreferenceChangeListener((pref, val) -> {
                SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
                return true;
            });
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.theme_global);
}
