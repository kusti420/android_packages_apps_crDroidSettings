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
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.statusbar.BatteryIcon;
import com.crdroid.settings.fragments.statusbar.Clock;
import com.crdroid.settings.fragments.statusbar.NetworkTrafficSettings;
import com.crdroid.settings.fragments.statusbar.StatusBarIcons;
import com.crdroid.settings.utils.DeviceUtils;

import lineageos.providers.LineageSettings;

import java.util.List;

/**
 * Look &gt; Status bar: the status bar's look and content (clock, battery, logo, icons,
 * notification dot, privacy indicators, weather, spacing).
 *
 * <p>{@link #reset} is the status bar part of "reset all": it also restores the status bar
 * gestures and the notification count, whose rows are in System &gt; Buttons &amp; gestures
 * ({@link Buttons}) and System &gt; Notifications ({@link Notifications}).
 */
@SearchIndexable
public class StatusBar extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "StatusBar";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    // IchthysOS: notification dot and privacy indicator (Settings.System, read by SystemUI).
    private static final String NOTIF_DOT_ENABLED = "ichthys_notif_dot_enabled";
    private static final String NOTIF_DOT_LOGIC = "ichthys_notif_dot_logic";
    private static final String NOTIF_DOT_AOD = "ichthys_notif_dot_aod";
    private static final String NOTIF_DOT_COLOR = "ichthys_notif_dot_color";
    private static final String NOTIF_DOT_REPLACE_ICONS = "ichthys_notif_dot_replace_icons";
    private static final String PRIVACY_INDICATOR_STYLE = "ichthys_privacy_indicator_style";
    private static final String PRIVACY_INDICATOR_COLOR_BY_TYPE =
            "ichthys_privacy_indicator_color_by_type";
    private static final String PRIVACY_INDICATOR_RING_WIDTH =
            "ichthys_privacy_indicator_ring_width";
    private static final int PRIVACY_INDICATOR_STYLE_RING = 0;

    /** Status bar icon slots shown as switches in the Indicators section. */
    // IchthysOS: only sensors_off. The location, microphone and camera slots are never drawn
    // (PhoneStatusBarPolicy leaves them off, as stock; the privacy chip shows that use), so
    // switches for them would do nothing.
    private static final String[] PRIVACY_SLOTS = {
        "sensors_off",
    };

    private StatusBarIcons.SlotSwitches mPrivacySlots;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_statusbar);
        // IchthysOS (tracker#59): the status bar's style, an ichthys.nix option, lives here now.
        com.crdroid.settings.fragments.themes.NixOptionRows.bind(this, "theme_studio_statusbar",
                "statusBar.layout", "zigzag");

        final Context context = getActivity().getApplicationContext();

        // Adjust the clock position choices for RTL and a centered cutout.
        final ListPreference statusBarClock = findPreference(STATUS_BAR_CLOCK_STYLE);
        if (statusBarClock != null) {
            if (getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL) {
                if (DeviceUtils.hasCenteredCutout(context)) {
                    statusBarClock.setEntries(
                            R.array.status_bar_clock_position_entries_notch_rtl);
                    statusBarClock.setEntryValues(
                            R.array.status_bar_clock_position_values_notch_rtl);
                } else {
                    statusBarClock.setEntries(R.array.status_bar_clock_position_entries_rtl);
                    statusBarClock.setEntryValues(R.array.status_bar_clock_position_values_rtl);
                }
            } else if (DeviceUtils.hasCenteredCutout(context)) {
                statusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch);
                statusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
            }
        }

        // IchthysOS: the ring's colour and width only apply to the ring style.
        final Preference privacyStyle = findPreference(PRIVACY_INDICATOR_STYLE);
        if (privacyStyle != null) {
            privacyStyle.setOnPreferenceChangeListener(this);
            updatePrivacyRingPreferences(Settings.System.getIntForUser(
                    context.getContentResolver(), PRIVACY_INDICATOR_STYLE,
                    PRIVACY_INDICATOR_STYLE_RING, UserHandle.USER_CURRENT));
        }

        // The privacy icon slots mirror the status bar hide list, like the System icons page.
        mPrivacySlots = new StatusBarIcons.SlotSwitches(context);
        for (String slot : PRIVACY_SLOTS) {
            final SwitchPreferenceCompat preference = findPreference(slot);
            if (preference != null && !mPrivacySlots.bind(preference)
                    && preference.getParent() != null) {
                preference.getParent().removePreference(preference);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPrivacySlots.refresh();
    }

    private void updatePrivacyRingPreferences(int style) {
        final boolean ring = style == PRIVACY_INDICATOR_STYLE_RING;
        final Preference colorByType = findPreference(PRIVACY_INDICATOR_COLOR_BY_TYPE);
        if (colorByType != null) {
            colorByType.setEnabled(ring);
        }
        final Preference ringWidth = findPreference(PRIVACY_INDICATOR_RING_WIDTH);
        if (ringWidth != null) {
            ringWidth.setEnabled(ring);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PRIVACY_INDICATOR_STYLE.equals(preference.getKey())) {
            updatePrivacyRingPreferences(Integer.parseInt((String) newValue));
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // IchthysOS: notification icon limit back to the device default.
        Settings.System.putIntForUser(resolver, "ichthys_statusbar_notif_icons", -1,
                UserHandle.USER_CURRENT);
        // IchthysOS: notification dot and privacy indicator back to their defaults.
        Settings.System.putIntForUser(resolver, NOTIF_DOT_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, NOTIF_DOT_LOGIC, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, NOTIF_DOT_AOD, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, NOTIF_DOT_COLOR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, NOTIF_DOT_REPLACE_ICONS, 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, PRIVACY_INDICATOR_STYLE,
                PRIVACY_INDICATOR_STYLE_RING, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, PRIVACY_INDICATOR_COLOR_BY_TYPE, 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, PRIVACY_INDICATOR_RING_WIDTH, 2,
                UserHandle.USER_CURRENT);

        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_CLOCK, 2, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_BATTERY_STYLE, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ENABLE_CAMERA_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ENABLE_LOCATION_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.ENABLE_PROJECTION_PRIVACY_INDICATOR, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.BLUETOOTH_SHOW_BATTERY, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_COLORED_ICONS, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_START, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_TOP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_EXTRA_PADDING_END, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUSBAR_NOTIF_COUNT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_BRIGHTNESS_CONTROL_LOCKSCREEN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_POSITION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.STATUS_BAR_LOGO_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DATA_DISABLED_ICON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.WIFI_STANDARD_ICON, 0, UserHandle.USER_CURRENT);
        // IchthysOS: status bar weather.
        Settings.System.putIntForUser(resolver, "status_bar_weather", 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_statusbar_weather_anchor_cutout", 1,
                UserHandle.USER_CURRENT);

        BatteryIcon.reset(mContext);
        Clock.reset(mContext);
        NetworkTrafficSettings.reset(mContext);
        StatusBarIcons.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_statusbar) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    // Keep in step with onCreate(): slots this build does not have.
                    final List<String> validSlots = StatusBarIcons.validSlots(context);
                    for (String slot : PRIVACY_SLOTS) {
                        if (!validSlots.contains(slot)) {
                            keys.add(slot);
                        }
                    }
                    return keys;
                }
            };

}
