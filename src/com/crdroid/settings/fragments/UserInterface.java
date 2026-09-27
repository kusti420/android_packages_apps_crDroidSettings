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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.ui.DozeSettings;
import com.crdroid.settings.fragments.ui.EdgeLightSettings;
import com.crdroid.settings.fragments.ui.GradientSettings;
import com.crdroid.settings.fragments.ui.MonetSettings;
import com.crdroid.settings.fragments.ui.SettingsIcon;
import com.crdroid.settings.fragments.ui.SmartPixels;

import java.util.List;

@SearchIndexable
public class UserInterface extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "UserInterface";



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_ui);

        Context mContext = getActivity().getApplicationContext();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        // IchthysOS: "force fullscreen for display cutout" was removed from the XML. Its
        // activity was never declared (tapping it crashed Settings) and nothing in the window
        // manager reads force_fullscreen_cutout_apps in this tree, so it could not do anything.
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // IchthysOS: screen on/off animation and screenshot preview duration defaults.
        Settings.System.putIntForUser(resolver, "ichthys_screen_reveal_style", 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_screenshot_preview_timeout", 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.PULSE_ON_NEW_TRACKS, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.DOZE_ALWAYS_ON_WALLPAPER_ENABLED, mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_dozeSupportsAodWallpaper) ? 1 : 0,
                UserHandle.USER_CURRENT);

        DozeSettings.reset(mContext);
        EdgeLightSettings.Companion.reset(mContext);
        GradientSettings.Companion.reset(mContext);
        MonetSettings.reset(mContext);
        SettingsIcon.Companion.reset(mContext);
        SmartPixels.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_ui) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);


                    return keys;
                }
            };
}
