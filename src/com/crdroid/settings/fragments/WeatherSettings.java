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

import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.OmniJawsClient;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.guard.ui.GuardActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.crdroid.settings.utils.SystemUtils;

/**
 * IchthysOS > Weather (tracker#59): where the weather comes from (OmniJaws) and every place it
 * shows, the status bar, the quick settings header and the lock screen, in one page.
 */
@SearchIndexable
public class WeatherSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_WEATHER = "lockscreen_weather_enabled";
    private static final String KEY_LOCATION = "weather_location";
    /** IchthysOS: "lat,lon|name|kind" (kind "home" or "trip"), from IchthysPlacesService. */
    private static final String SHARED_LOCATION_ACTIVE = "ichthys_location_active";

    private SwitchPreferenceCompat mWeather;
    private Preference mLocation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.weather_settings);
        mWeather = findPreference(KEY_WEATHER);
        if (mWeather != null) {
            mWeather.setOnPreferenceChangeListener(this);
        }
        // IchthysOS: the place the weather is for is a shared value (Guard > Rules > Shared
        // values); this row shows it and opens that screen.
        mLocation = findPreference(KEY_LOCATION);
        if (mLocation != null) {
            mLocation.setIntent(GuardActivity.sharedValuesIntent(getContext()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLocation != null) {
            mLocation.setSummary(locationSummary());
        }
        if (mWeather == null) return;
        final boolean enabled = OmniJawsClient.get().isOmniJawsEnabled(getContext());
        mWeather.setEnabled(enabled);
        mWeather.setSummary(enabled ? R.string.lockscreen_weather_summary
                : R.string.lockscreen_weather_enabled_info);
    }

    /** The active place as "Tallinn · home city ..." or the device-location fallback. */
    private CharSequence locationSummary() {
        final String active = Settings.Secure.getString(getContentResolver(),
                SHARED_LOCATION_ACTIVE);
        if (active != null) {
            final String[] parts = active.split("\\|", -1);
            if (parts.length > 1 && !TextUtils.isEmpty(parts[1])) {
                final boolean trip = parts.length > 2 && "trip".equals(parts[2]);
                return getString(trip ? R.string.weather_location_trip
                        : R.string.weather_location_home, parts[1]);
            }
        }
        return getString(R.string.weather_location_device);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mWeather) {
            // The lock screen weather view is only created when SystemUI starts.
            mWeather.setChecked((Boolean) newValue);
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.weather_settings);
}
