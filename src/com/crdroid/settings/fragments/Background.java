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
import android.graphics.drawable.Drawable;
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

    private ListPreference mUnrestrictedMode;
    private Preference mUnrestrictedApps;
    private Preference mUnrestrictedPersist;
    private Preference mBackstage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_background);

        mBackstage = findPreference(KEY_BACKSTAGE);
        setupUnrestrictedBackground();
    }

    // The mode row explains what the chosen mode does; the app picker and keep-after-reboot rows
    // only apply while a mode is on.
    private void setupUnrestrictedBackground() {
        mUnrestrictedMode = findPreference(KEY_UNRESTRICTED_MODE);
        mUnrestrictedApps = findPreference(KEY_UNRESTRICTED_APPS);
        mUnrestrictedPersist = findPreference(KEY_UNRESTRICTED_PERSIST);
        if (mUnrestrictedMode == null) return;

        // Both danger rows render on a solid red card, like the power-menu Emergency button, so
        // it's obvious these aren't everyday toggles. Whiten the glyphs to stay legible on red.
        whitenIcon(mUnrestrictedMode);
        whitenIcon(mUnrestrictedPersist);

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

    // Tints the preference's glyph white so it stays legible on the red danger pill,
    // matching the power-menu Emergency button's plain white glyph on solid red.
    private void whitenIcon(Preference pref) {
        if (pref == null || pref.getIcon() == null) return;
        final Drawable glyph = pref.getIcon().mutate();
        glyph.setTint(0xFFFFFFFF);
        pref.setIcon(glyph);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent.
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
        // Backstage: no time limit. The apps in Backstage are running state, not a setting,
        // and are left alone.
        Settings.Secure.putIntForUser(resolver, KEY_BACKSTAGE_MAX_HOURS, 0,
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
