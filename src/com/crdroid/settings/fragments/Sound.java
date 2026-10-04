/*
 * Copyright (C) 2016-2025 crDroid Android Project
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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.utils.DeviceUtils;

import java.util.List;
import java.util.ArrayList;

import lineageos.providers.LineageSettings;

/**
 * IchthysOS &gt; System &gt; Sound &amp; haptics: the old Sound tab plus adaptive playback, the
 * media volume steps and the disable-all-vibration switch.
 */
@SearchIndexable
public class Sound extends SettingsPreferenceFragment {

    public static final String TAG = "Sound";

    private static final String KEY_HAPTICS_CATEGORY = "haptics";
    private static final String KEY_VOLUME_PANEL_LEFT = "volume_panel_on_left";
    private static final String KEY_VOLUME_HAPTIC = "volume_dialog_haptic_feedback";
    private static final String KEY_DISABLE_ALL_VIBRATION = "disable_all_vibration";
    private static final String KEY_ADAPTIVE_PLAYBACK_ENABLED = "adaptive_playback_enabled";
    private static final String KEY_ADAPTIVE_PLAYBACK_TIMEOUT = "adaptive_playback_timeout";

    private SwitchPreferenceCompat mVolumePanelLeft;

    private static volatile Boolean sAudioPanelOnLeft = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_sound);

        final Context context = getContext();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        boolean isAudioPanelOnLeft = LineageSettings.Secure.getIntForUser(context.getContentResolver(),
                LineageSettings.Secure.VOLUME_PANEL_ON_LEFT, isAudioPanelOnLeftSide(context) ? 1 : 0,
                UserHandle.USER_CURRENT) != 0;

        mVolumePanelLeft = prefScreen.findPreference(KEY_VOLUME_PANEL_LEFT);
        mVolumePanelLeft.setChecked(isAudioPanelOnLeft);

        // No vibrator: nothing in the haptics category applies.
        if (!DeviceUtils.hasVibrator(context)) {
            final PreferenceCategory haptics = prefScreen.findPreference(KEY_HAPTICS_CATEGORY);
            if (haptics != null) {
                prefScreen.removePreference(haptics);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent.
        ShoalStyle.badgeAll(getPreferenceScreen(), ShoalStyle.accent(view.getContext()));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.VOLUME_PANEL_ON_LEFT, isAudioPanelOnLeftSide(mContext) ? 1 : 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.VOLUME_DIALOG_DISMISS_TIMEOUT, 3000, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.VOLUME_DIALOG_HAPTIC_FEEDBACK, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREENSHOT_SHUTTER_SOUND, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_DISABLE_ALL_VIBRATION, 0, UserHandle.USER_CURRENT);
        // Deleting the key restores the framework's built-in number of media volume steps.
        Settings.Secure.putStringForUser(resolver,
                "custom_volume_steps", null, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_ADAPTIVE_PLAYBACK_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_ADAPTIVE_PLAYBACK_TIMEOUT, 30, UserHandle.USER_CURRENT);
    }

    private static boolean isAudioPanelOnLeftSide(Context context) {
        Boolean cached = sAudioPanelOnLeft;
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        try {
            Context con = context.createPackageContext(
                    "org.lineageos.lineagesettings", 0);
            int id = con.getResources().getIdentifier("def_volume_panel_on_left",
                    "bool", "org.lineageos.lineagesettings");
            if (id > 0) {
                result = con.getResources().getBoolean(id);
            }
        } catch (Exception e) { }
        sAudioPanelOnLeft = result;
        return result;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_sound) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    if (!DeviceUtils.hasVibrator(context)) {
                        keys.add(KEY_VOLUME_HAPTIC);
                        keys.add(KEY_DISABLE_ALL_VIBRATION);
                    }
                    return keys;
                }
            };
}
