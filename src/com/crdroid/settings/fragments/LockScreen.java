/*
 * Copyright (C) 2016-2026 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.OmniJawsClient;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.lockscreen.FingerprintEffects;
import com.crdroid.settings.fragments.lockscreen.MediaArtSettings;
import com.crdroid.settings.fragments.lockscreen.PulseSettings;
import com.crdroid.settings.utils.SystemUtils;

/**
 * Look &gt; Lock screen &gt; Lock screen and ambient display: what the lock screen shows (music
 * visualizer, media art, charging info, weather) and the links to ambient display and edge
 * light.
 *
 * <p>{@link #reset} is the lock screen part of "reset all": it also restores the lock screen
 * gesture, the notification limits and the fingerprint settings, whose rows are in System &gt;
 * Buttons &amp; gestures ({@link Buttons}), System &gt; Notifications ({@link Notifications})
 * and on the fingerprint page ({@link FingerprintEffects}).
 */
@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    public static final String TAG = "LockScreen";

    private static final String KEY_WEATHER = "lockscreen_weather_enabled";

    private SwitchPreferenceCompat mWeather;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_lockscreen);

        // IchthysOS (tracker#59): the weather rows moved to Weather (WeatherSettings); the
        // fingerprint page needs a sensor.
        if (!com.crdroid.settings.utils.DeviceUtils.hasFingerprint(getContext())) {
            final Preference fp = findPreference("ichthys_fingerprint_effects");
            if (fp != null && fp.getParent() != null) fp.getParent().removePreference(fp);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (mWeather != null && preference == mWeather) {
            // The lock screen weather view is only created when SystemUI starts.
            mWeather.setChecked((Boolean) newValue);
            SystemUtils.showSystemUiRestartDialog(getContext());
            return true;
        }

        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        FingerprintEffects.reset(mContext);
        // No row any more (removed with the dead smartspace switch); this only clears a value
        // an older build may have left behind.
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.LOCKSCREEN_SMARTSPACE_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ENABLE_RIPPLE_EFFECT, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_ERROR_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_ENABLE_POWER_MENU, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_LOCATION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_TEXT, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_WIND_INFO, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_HUMIDITY_INFO, 0, UserHandle.USER_CURRENT);
        // IchthysOS: options of the drawn lock screen clock faces.
        Settings.Secure.putIntForUser(resolver, "ichthys_clock_show_date", 1,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "ichthys_clock_dot_shape", 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "ichthys_clock_unlit_dots", 1,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "ichthys_lockscreen_hide_statusbar", 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_lockscreen_max_notifs", -1,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_aod_notif_icons", -1,
                UserHandle.USER_CURRENT);
        PulseSettings.reset(mContext);
        MediaArtSettings.reset(mContext);
    }

    private void updateWeatherSettings() {
        if (mWeather == null) return;

        boolean weatherEnabled = OmniJawsClient.get().isOmniJawsEnabled(getContext());
        mWeather.setEnabled(weatherEnabled);
        mWeather.setSummary(weatherEnabled ? R.string.lockscreen_weather_summary :
            R.string.lockscreen_weather_enabled_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWeatherSettings();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_lockscreen);
}
