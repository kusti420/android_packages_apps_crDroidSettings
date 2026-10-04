/*
 * Copyright (C) 2026 IchthysOS
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
package com.crdroid.settings.fragments.statusbar;

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

import com.crdroid.settings.preferences.SystemSettingListPreference;

/**
 * Look &gt; Status bar &gt; Battery: the one page for how the battery is shown. Icon style and
 * percentage (crDroid's LineageSettings keys), the IchthysOS icon options
 * (kusti420/ichthysos-tracker#37; SystemUI observes every key, so changes apply immediately), the
 * quick settings header icon, and the battery bar.
 */
@SearchIndexable
public class BatteryIcon extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_FILL_MODE = "ichthys_battery_fill_mode";
    private static final String KEY_FILL_COLOR = "ichthys_battery_fill_color";
    private static final String KEY_STATE_COLORS = "ichthys_battery_state_colors";
    private static final String KEY_CHARGING_COLOR = "ichthys_battery_charging_color";
    private static final String KEY_POWERSAVE_COLOR = "ichthys_battery_powersave_color";
    private static final String KEY_LOW_COLOR = "ichthys_battery_low_color";

    // Values of the list preferences; mirror SystemUI's BatteryCustomization constants.
    private static final int FILL_MODE_TINT = 0;
    private static final int FILL_MODE_CUSTOM = 1;
    private static final int STATE_COLORS_THEME = 1;
    private static final int STATE_COLORS_CUSTOM = 2;

    private SystemSettingListPreference mFillMode;
    private SystemSettingListPreference mStateColors;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_battery);

        mFillMode = (SystemSettingListPreference) findPreference(KEY_FILL_MODE);
        mFillMode.setOnPreferenceChangeListener(this);
        mStateColors = (SystemSettingListPreference) findPreference(KEY_STATE_COLORS);
        mStateColors.setOnPreferenceChangeListener(this);

        updateColorPrefs(mFillMode.getIntValue(FILL_MODE_TINT),
                mStateColors.getIntValue(STATE_COLORS_THEME));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int value = Integer.parseInt((String) newValue);
        if (preference == mFillMode) {
            updateColorPrefs(value, mStateColors.getIntValue(STATE_COLORS_THEME));
            return true;
        } else if (preference == mStateColors) {
            updateColorPrefs(mFillMode.getIntValue(FILL_MODE_TINT), value);
            return true;
        }
        return false;
    }

    /** Color pickers are only enabled when the matching mode uses them. */
    private void updateColorPrefs(int fillMode, int stateColors) {
        findPreference(KEY_FILL_COLOR).setEnabled(fillMode == FILL_MODE_CUSTOM);
        boolean custom = stateColors == STATE_COLORS_CUSTOM;
        findPreference(KEY_CHARGING_COLOR).setEnabled(custom);
        findPreference(KEY_POWERSAVE_COLOR).setEnabled(custom);
        findPreference(KEY_LOW_COLOR).setEnabled(custom);
    }

    public static void reset(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String[] keys = {
            "ichthys_battery_scale",
            "ichthys_battery_frame_thickness",
            "ichthys_battery_charging_indicator",
            "ichthys_battery_powersave_indicator",
            "ichthys_battery_percent_charging_only",
            "ichthys_battery_percent_bold",
            KEY_FILL_MODE,
            KEY_FILL_COLOR,
            KEY_STATE_COLORS,
            KEY_CHARGING_COLOR,
            KEY_POWERSAVE_COLOR,
            KEY_LOW_COLOR,
            "ichthys_battery_low_level",
        };
        // Deleting a key restores SystemUI's built-in default for it.
        for (String key : keys) {
            Settings.System.putStringForUser(resolver, key, null, UserHandle.USER_CURRENT);
        }
        Settings.System.putIntForUser(resolver,
                "status_bar_battery_text_charging", 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "qs_battery_style", -1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                "qs_show_battery_percent", -1, UserHandle.USER_CURRENT);
        resetBatteryBar(resolver);
    }

    private static void resetBatteryBar(ContentResolver resolver) {
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR, 0, UserHandle.USER_CURRENT);
        // No stored colour: SystemUI then draws the bar in the theme accent.
        Settings.System.putStringForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_COLOR, null, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_THICKNESS, 2, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ANIMATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_CHARGING_COLOR, 0xffffc90f,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR, 0xfff90028,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR, 1,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_BATTERY_BAR_BLEND_COLOR_REVERSE, 0,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.status_bar_battery);
}
