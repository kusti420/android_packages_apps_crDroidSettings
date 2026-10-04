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

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.ui.NavbarStyles;

import lineageos.providers.LineageSettings;

/**
 * Look &gt; Navigation bar &gt; Gesture pill: width, thickness and opacity of the gesture handle.
 *
 * <p>{@link #reset} is the navigation part of "reset all": it also restores the navigation
 * behaviour settings, whose rows are in System &gt; Buttons &amp; gestures ({@link Buttons}).
 */
@SearchIndexable
public class Navigation extends SettingsPreferenceFragment {

    public static final String TAG = "Navigation";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_navigation);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // IchthysOS: gesture pill styling.
        Settings.System.putIntForUser(resolver, "ichthys_nav_handle_hide", 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_nav_handle_width", 100,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_nav_handle_thickness", 100,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_nav_handle_opacity", 100,
                UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
             LineageSettings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.NAVIGATIONBAR_KEY_ORDER, 1, UserHandle.USER_CURRENT);
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
        NavbarStyles.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_navigation);
}
