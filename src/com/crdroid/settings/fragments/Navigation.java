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

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;

import lineageos.providers.LineageSettings;


public class Navigation extends SettingsPreferenceFragment {

    public static final String TAG = "Navigation";

    private static final String KEY_BUTTONS_CATEGORY = "navigation_buttons_category";
    private static final String KEY_GESTURE_CATEGORY = "navigation_gesture_category";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_navigation);

        final boolean gesturalMode = getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode)
                        == NAV_BAR_MODE_GESTURAL;

        // Hide the categories that do not apply to the active navigation mode.
        final PreferenceCategory buttonsCategory = findPreference(KEY_BUTTONS_CATEGORY);
        final PreferenceCategory gestureCategory = findPreference(KEY_GESTURE_CATEGORY);
        if (gesturalMode) {
            if (buttonsCategory != null) {
                getPreferenceScreen().removePreference(buttonsCategory);
            }
        } else {
            if (gestureCategory != null) {
                getPreferenceScreen().removePreference(gestureCategory);
            }
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.System.putIntForUser(resolver,
             LineageSettings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.NAVIGATIONBAR_KEY_ORDER, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.NAVBAR_LAYOUT_MODE, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.BACK_GESTURE_ARROW, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.BACK_GESTURE_HAPTIC, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BACK_GESTURE_HEIGHT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.GESTURE_NAVBAR_LENGTH_MODE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.GESTURE_NAVBAR_HEIGHT_MODE, 3, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }
}
