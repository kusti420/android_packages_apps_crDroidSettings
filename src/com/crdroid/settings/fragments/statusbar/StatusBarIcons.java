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
package com.crdroid.settings.fragments.statusbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the user pick which system icons the status bar draws. SystemUI keeps the hidden slots
 * in the "icon_blacklist" secure setting and watches it through TunerService, so all this
 * screen has to do is maintain that comma separated slot list.
 */
public class StatusBarIcons extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "StatusBarIcons";

    // Secure setting watched by SystemUI's StatusBarIconController (ICON_HIDE_LIST), the
    // battery meter, the clock and the connectivity pipeline. Slots listed here are hidden.
    private static final String ICON_HIDE_LIST = "icon_blacklist";

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String DEFAULT_HIDE_LIST_ARRAY = "config_statusBarIconsToExclude";

    // Used if SystemUI's default array cannot be read; matches its current contents.
    private static final String[] FALLBACK_HIDE_LIST = { "rotate", "headset" };

    // The clock lives in the status bar layout instead of the icon list, so it is not part of
    // config_statusBarIcons even though Clock honours the very same hide list.
    private static final String SLOT_CLOCK = "clock";
    private static final String SLOT_MOBILE = "mobile";

    private final Set<String> mHiddenSlots = new LinkedHashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.status_bar_icons);

        final Context context = getActivity().getApplicationContext();

        loadHiddenSlots();

        // Slot names as SystemUI knows them in this build, so a switch can never survive a
        // slot being renamed or dropped from the framework.
        final List<String> validSlots = new ArrayList<>(Arrays.asList(getResources()
                .getStringArray(com.android.internal.R.array.config_statusBarIcons)));
        validSlots.add(SLOT_CLOCK);

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            validSlots.remove(SLOT_MOBILE);
        }

        updateIconPreferences(getPreferenceScreen(), validSlots);
    }

    private void updateIconPreferences(PreferenceGroup group, List<String> validSlots) {
        for (int i = group.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference preference = group.getPreference(i);

            if (preference instanceof PreferenceGroup) {
                updateIconPreferences((PreferenceGroup) preference, validSlots);
                if (((PreferenceGroup) preference).getPreferenceCount() == 0) {
                    group.removePreference(preference);
                }
                continue;
            }

            if (!(preference instanceof SwitchPreferenceCompat)) {
                continue;
            }

            if (!validSlots.contains(preference.getKey())) {
                group.removePreference(preference);
                continue;
            }

            // Checked means shown, so invert the hide list.
            ((SwitchPreferenceCompat) preference).setChecked(
                    !mHiddenSlots.contains(preference.getKey()));
            preference.setOnPreferenceChangeListener(this);
        }
    }

    private void loadHiddenSlots() {
        mHiddenSlots.clear();

        final String hideList = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(), ICON_HIDE_LIST, UserHandle.USER_CURRENT);
        final String[] slots = hideList == null
                ? getDefaultHiddenSlots() : hideList.split(",");

        for (String slot : slots) {
            if (!TextUtils.isEmpty(slot)) {
                mHiddenSlots.add(slot);
            }
        }
    }

    /**
     * While the setting has never been written SystemUI falls back to its own
     * config_statusBarIconsToExclude array, so read the same array to keep the switches in
     * sync with what is actually drawn.
     */
    private String[] getDefaultHiddenSlots() {
        try {
            final Resources res = getActivity().getPackageManager()
                    .getResourcesForApplication(SYSTEMUI_PACKAGE);
            final int resId = res.getIdentifier(
                    DEFAULT_HIDE_LIST_ARRAY, "array", SYSTEMUI_PACKAGE);
            if (resId != 0) {
                return res.getStringArray(resId);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Fall through to the built in list.
        }
        return FALLBACK_HIDE_LIST;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String slot = preference.getKey();
        if (TextUtils.isEmpty(slot)) {
            return false;
        }

        if ((Boolean) newValue) {
            mHiddenSlots.remove(slot);
        } else {
            mHiddenSlots.add(slot);
        }

        // An empty string is deliberate: it keeps the setting written so SystemUI does not
        // fall back to its default hide list once the user has shown every icon.
        Settings.Secure.putStringForUser(getActivity().getContentResolver(),
                ICON_HIDE_LIST, TextUtils.join(",", mHiddenSlots), UserHandle.USER_CURRENT);
        return true;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();

        // Clearing the setting hands the choice back to SystemUI's default hide list.
        Settings.Secure.putStringForUser(resolver,
                ICON_HIDE_LIST, null, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }
}
