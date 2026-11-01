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

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.backstage.Backstage;
import com.crdroid.settings.fragments.misc.UnrestrictedBackgroundApps;
import com.crdroid.settings.preferences.CustomSeekBarPreference;

/**
 * IchthysOS &gt; Apps &gt; Background: what apps may do while they are not on screen. Unrestricted
 * background (mode, apps, keep after reboot; tracker#39), Backstage, screen-on memory reclaim
 * and the app hibernation master switch, which used to be on four different pages.
 */
@SearchIndexable
public class Background extends SettingsPreferenceFragment {

    public static final String TAG = "Background";

    private static final String KEY_UNRESTRICTED_MODE = "ichthys_unrestricted_bg_mode";
    private static final String KEY_UNRESTRICTED_APPS = "ichthys_unrestricted_bg_apps";
    private static final String KEY_UNRESTRICTED_PERSIST = "ichthys_unrestricted_bg_persist";
    private static final String KEY_BACKSTAGE = "ichthys_backstage";
    private static final String KEY_BACKSTAGE_MAX_HOURS = "ichthys_backstage_max_hours";
    private static final String KEY_BACKSTAGE_MAX_MINUTES = "ichthys_backstage_max_minutes";
    /** KEY_BACKSTAGE_MAX_HOURS value meaning "the custom duration in KEY_BACKSTAGE_MAX_MINUTES". */
    private static final int MAX_HOURS_CUSTOM = -1;
    private static final int DEFAULT_MAX_MINUTES = 90;

    private ListPreference mUnrestrictedMode;
    private Preference mUnrestrictedApps;
    private Preference mUnrestrictedPersist;
    private Preference mBackstage;
    private ListPreference mBackstageMaxHours;
    private CustomSeekBarPreference mBackstageMaxMinutes;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_background);

        mBackstage = findPreference(KEY_BACKSTAGE);
        setupUnrestrictedBackground();
        setupBackstageLimit();
    }

    // The custom duration row only applies while the list says "Custom"; its summary reads in
    // hours and minutes (the slider itself counts minutes).
    private void setupBackstageLimit() {
        mBackstageMaxHours = findPreference(KEY_BACKSTAGE_MAX_HOURS);
        mBackstageMaxMinutes = findPreference(KEY_BACKSTAGE_MAX_MINUTES);
        if (mBackstageMaxHours == null || mBackstageMaxMinutes == null) return;

        mBackstageMaxHours.setOnPreferenceChangeListener((p, v) -> {
            updateBackstageLimit(Integer.parseInt((String) v), mBackstageMaxMinutes.getValue());
            return true;
        });
        mBackstageMaxMinutes.setOnPreferenceChangeListener((p, v) -> {
            updateBackstageLimit(backstageMaxHours(), (Integer) v);
            return true;
        });
        updateBackstageLimit(backstageMaxHours(), mBackstageMaxMinutes.getValue());
    }

    private int backstageMaxHours() {
        return mBackstageMaxHours == null || mBackstageMaxHours.getValue() == null
                ? 0 : Integer.parseInt(mBackstageMaxHours.getValue());
    }

    private void updateBackstageLimit(int hours, int minutes) {
        if (mBackstageMaxMinutes == null) return;
        mBackstageMaxMinutes.setVisible(hours == MAX_HOURS_CUSTOM);
        // CustomSeekBarPreference prefixes the value ("390 min"); the user summary adds the
        // readable form once it is an hour or more.
        if (minutes < 60) {
            mBackstageMaxMinutes.setSummary(null);
        } else if (minutes % 60 == 0) {
            mBackstageMaxMinutes.setSummary(
                    getString(R.string.backstage_max_minutes_hours, minutes / 60));
        } else {
            mBackstageMaxMinutes.setSummary(getString(
                    R.string.backstage_max_minutes_hours_minutes, minutes / 60, minutes % 60));
        }
    }

    // The mode row explains what the chosen mode does; the app picker and keep-after-reboot rows
    // only apply while a mode is on.
    private void setupUnrestrictedBackground() {
        mUnrestrictedMode = findPreference(KEY_UNRESTRICTED_MODE);
        mUnrestrictedApps = findPreference(KEY_UNRESTRICTED_APPS);
        mUnrestrictedPersist = findPreference(KEY_UNRESTRICTED_PERSIST);
        if (mUnrestrictedMode == null) return;


        mUnrestrictedMode.setOnPreferenceChangeListener((p, v) -> {
            updateUnrestrictedBackground(Integer.parseInt((String) v));
            return true;
        });
    }

    private int unrestrictedMode() {
        return mUnrestrictedMode == null || mUnrestrictedMode.getValue() == null
                ? 0 : Integer.parseInt(mUnrestrictedMode.getValue());
    }

    private void updateUnrestrictedBackground(int mode) {
        if (mUnrestrictedMode == null) return;
        final int summary;
        switch (mode) {
            case 1:
                summary = R.string.unrestricted_background_summary_selected;
                break;
            case 2:
                summary = R.string.unrestricted_background_summary_everything;
                break;
            default:
                summary = R.string.unrestricted_background_summary_off;
                break;
        }
        mUnrestrictedMode.setSummary(summary);
        if (mUnrestrictedApps != null) {
            mUnrestrictedApps.setEnabled(mode != 0);
            final int count = UnrestrictedBackgroundApps.read(getContext()).size();
            mUnrestrictedApps.setSummary(count == 0
                    ? getString(R.string.unrestricted_background_apps_none)
                    : getResources().getQuantityString(
                            R.plurals.unrestricted_background_apps_count, count, count));
        }
        if (mUnrestrictedPersist != null) {
            mUnrestrictedPersist.setEnabled(mode != 0);
        }
    }

    private void updateBackstage() {
        if (mBackstage == null) return;
        final int count = Backstage.readPackages(getContext()).size();
        mBackstage.setSummary(count == 0
                ? getString(R.string.ichthys_backstage_apps_none)
                : getResources().getQuantityString(
                        R.plurals.ichthys_backstage_apps_count, count, count));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from one of the app pickers: refresh the counts.
        updateUnrestrictedBackground(unrestrictedMode());
        updateBackstage();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent. The two danger
        // rows (Danger*Preference) decline the badge and keep a white glyph on their red card.
        ShoalStyle.badgeAll(getPreferenceScreen(), ShoalStyle.accent(view.getContext()));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREEN_ON_MEMORY_RECLAIM, 0, UserHandle.USER_CURRENT);
        // Unrestricted background is device-wide (Settings.Global): off, not kept, no apps.
        Settings.Global.putInt(resolver, KEY_UNRESTRICTED_MODE, 0);
        Settings.Global.putInt(resolver, KEY_UNRESTRICTED_PERSIST, 0);
        Settings.Global.putString(resolver, UnrestrictedBackgroundApps.KEY_PACKAGES, null);
        // Backstage: no time limit, the custom duration back at its default. The apps in
        // Backstage are running state, not a setting, and are left alone.
        Settings.Secure.putIntForUser(resolver, KEY_BACKSTAGE_MAX_HOURS, 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, KEY_BACKSTAGE_MAX_MINUTES, DEFAULT_MAX_MINUTES,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.ichthys_background);
}
