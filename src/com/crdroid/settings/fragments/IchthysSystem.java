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

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import lineageos.health.HealthInterface;

import java.io.File;
import java.util.List;

/**
 * IchthysOS &gt; System: how the phone behaves. Links to Buttons &amp; gestures, Notifications and
 * Sound &amp; haptics, then charging control, the charging animation (style, and whether it
 * turns the screen on), whether a cover magnet wakes the screen, Smart Pixels, the screenshot
 * preview duration, the duress PIN, auto restart when locked, and the About rows (changelog,
 * source, community).
 */
@SearchIndexable
public class IchthysSystem extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysSystem";

    private static final String KEY_CHARGING_CONTROL = "charging_control";
    private static final String KEY_SOURCE = "crdroid_source";
    private static final String KEY_TELEGRAM = "crdroid_telegram";
    private static final String KEY_CHANGELOG = "crdroid_changelog";
    /** IchthysOS: auto reboot (IchthysAutoRebootService), Settings.Global, milliseconds. */
    private static final String KEY_AUTO_REBOOT = "ichthys_auto_reboot_timeout";
    /** IchthysOS: charger-connected animation style and its wake switch (Settings.System). */
    private static final String KEY_CHARGING_ANIM = "ichthys_charging_animation";
    private static final String KEY_CHARGING_ANIM_WAKE = "ichthys_charging_animation_wake";
    private static final String CHARGING_ANIM_DEFAULT = "pulse";
    private static final String CHARGING_ANIM_OFF = "off";
    /** IchthysOS: "Wake with cover magnet", read by PhoneWindowManager (Settings.System). */
    private static final String KEY_LID_WAKE = "ichthys_lid_wake_enabled";
    /** What ChangelogFragment shows. IchthysOS: nothing in the build installs it yet. */
    private static final String CHANGELOG_PATH = "/system/etc/Changelog.txt";

    private com.crdroid.settings.fragments.themes.NixConfigActions mNix;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mNix == null || !mNix.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNix != null) mNix.shutdown();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_system);
        // ichthys.nix import / export / generations (moved from Theme Studio, tracker#59).
        mNix = new com.crdroid.settings.fragments.themes.NixConfigActions(this);
        mNix.setup();

        // Charging control only exists when the Lineage Health binder is published, which needs
        // (a) the internal health sources compiled into org.lineageos.platform, (b) the
        // org.lineageos.health feature XML installed, and (c) the vendor.lineage.health HAL
        // running. Drop the row rather than open a screen that cannot do anything.
        if (!HealthInterface.isChargingControlSupported(getContext())) {
            final Preference chargingControl = findPreference(KEY_CHARGING_CONTROL);
            if (chargingControl != null && chargingControl.getParent() != null) {
                chargingControl.getParent().removePreference(chargingControl);
            }
        }

        // IchthysOS: a timeout that isn't one of the choices (an adb test value) has no entry;
        // say what it is instead of showing an empty summary.
        final ListPreference autoReboot = findPreference(KEY_AUTO_REBOOT);
        if (autoReboot != null) {
            autoReboot.setSummaryProvider(p -> {
                final ListPreference list = (ListPreference) p;
                if (list.getEntry() != null) return list.getEntry();
                // Never empty: GlobalSettingListPreference would then call setSummary(), which
                // throws while a SummaryProvider is set.
                try {
                    final long ms = Long.parseLong(list.getValue());
                    return getString(R.string.ichthys_auto_reboot_custom, (int) (ms / 60000L));
                } catch (NumberFormatException | NullPointerException e) {
                    return String.valueOf(list.getValue());
                }
            });
        }

        // IchthysOS: turning the screen on to play means nothing while the animation is off.
        final ListPreference chargingAnim = findPreference(KEY_CHARGING_ANIM);
        final Preference chargingWake = findPreference(KEY_CHARGING_ANIM_WAKE);
        if (chargingAnim != null && chargingWake != null) {
            chargingWake.setEnabled(!CHARGING_ANIM_OFF.equals(chargingAnim.getValue()));
            chargingAnim.setOnPreferenceChangeListener((p, v) -> {
                chargingWake.setEnabled(!CHARGING_ANIM_OFF.equals(v));
                return true;
            });
        }

        // IchthysOS: the changelog page reads CHANGELOG_PATH and is empty without it, so the row
        // only shows when the build ships the file.
        if (!hasChangelog()) {
            final Preference changelog = findPreference(KEY_CHANGELOG);
            if (changelog != null && changelog.getParent() != null) {
                changelog.getParent().removePreference(changelog);
            }
        }
    }

    private static boolean hasChangelog() {
        final File f = new File(CHANGELOG_PATH);
        return f.isFile() && f.length() > 0;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEY_SOURCE.equals(preference.getKey())) {
            launchUrl("https://github.com/kusti420/ichthysos_manifest");
            return true;
        } else if (KEY_TELEGRAM.equals(preference.getKey())) {
            launchUrl("https://t.me/IchthysOS");
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchUrl(String url) {
        // IchthysOS: the browser (Jelly) can be disabled; don't crash Settings then.
        try {
            getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), getString(R.string.ichthys_no_browser, url),
                    Toast.LENGTH_LONG).show();
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
        Settings.System.putIntForUser(resolver, "ichthys_screenshot_preview_timeout", 0,
                UserHandle.USER_CURRENT);
        Settings.System.putStringForUser(resolver, KEY_CHARGING_ANIM, CHARGING_ANIM_DEFAULT,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, KEY_CHARGING_ANIM_WAKE, 0,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, KEY_LID_WAKE, 1, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.ichthys_system) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    // Do not let search surface charging control when the backend is absent.
                    if (!HealthInterface.isChargingControlSupported(context)) {
                        keys.add(KEY_CHARGING_CONTROL);
                    }
                    if (!hasChangelog()) {
                        keys.add(KEY_CHANGELOG);
                    }
                    return keys;
                }
            };
}
