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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.misc.SensorBlock;

import java.util.List;

import lineageos.providers.LineageSettings;

import static org.lineageos.internal.util.DeviceKeysConstants.*;

@SearchIndexable
public class Miscellaneous extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "Miscellaneous";

    private static final String POCKET_JUDGE = "pocket_judge";
    private static final String KEY_GMS_CERT_SPOOF = "pi_gms_cert_chain";
    private static final String KEY_THREE_FINGERS_SWIPE = "three_fingers_swipe";

    private Preference mPocketJudge;
    private ListPreference mThreeFingersSwipeAction;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_misc);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = getResources();

        mPocketJudge = (Preference) prefScreen.findPreference(POCKET_JUDGE);
        boolean mPocketJudgeSupported = res.getBoolean(
                com.android.internal.R.bool.config_pocketModeSupported);
        if (!mPocketJudgeSupported)
            prefScreen.removePreference(mPocketJudge);

        Action threeFingersSwipeAction = Action.fromSettings(getContentResolver(),
                LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION,
                Action.NOTHING);
        mThreeFingersSwipeAction = initList(KEY_THREE_FINGERS_SWIPE, threeFingersSwipeAction);

        setupUnrestrictedBackground(prefScreen);
    }

    // IchthysOS: "Unrestricted background" caution styling + mutual exclusion with
    // "Screen-on memory reclaim" (the two do opposite things, so only one may be on).
    private void setupUnrestrictedBackground(PreferenceScreen prefScreen) {
        final SwitchPreferenceCompat reclaim =
                (SwitchPreferenceCompat) prefScreen.findPreference("screen_on_memory_reclaim");
        final SwitchPreferenceCompat unrestricted =
                (SwitchPreferenceCompat) prefScreen.findPreference("unrestricted_background");
        if (unrestricted == null) return;
        final SwitchPreferenceCompat persist =
                (SwitchPreferenceCompat) prefScreen.findPreference("unrestricted_background_persist");

        // Both rows render on a solid red "danger" pill (@layout/preference_danger_background),
        // like the power-menu Emergency button, so it's obvious these aren't everyday toggles.
        // Title/summary are already white via the layout; whiten the glyph so it stays legible
        // on the red background.
        whitenIcon(unrestricted);
        whitenIcon(persist);

        if (reclaim == null) return;
        unrestricted.setEnabled(!reclaim.isChecked());
        reclaim.setOnPreferenceChangeListener((p, v) -> {
            final boolean on = (Boolean) v;
            if (on && unrestricted.isChecked()) {
                unrestricted.setChecked(false); // persists 0 -> backend reverts
            }
            unrestricted.setEnabled(!on);
            return true;
        });
        unrestricted.setOnPreferenceChangeListener((p, v) -> {
            final boolean on = (Boolean) v;
            if (on && reclaim.isChecked()) {
                reclaim.setChecked(false);
            }
            return true;
        });
    }

    // Tints the preference's glyph white so it stays legible on the red danger pill,
    // matching the power-menu Emergency button's plain white glyph on solid red.
    private void whitenIcon(Preference pref) {
        if (pref == null || pref.getIcon() == null) return;
        final Drawable glyph = pref.getIcon().mutate();
        glyph.setTint(0xFFFFFFFF);
        pref.setIcon(glyph);
    }

    private ListPreference initList(String key, Action value) {
        return initList(key, value.ordinal());
    }

    private ListPreference initList(String key, int value) {
        ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
        if (list == null) return null;
        list.setValue(Integer.toString(value));
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private void handleListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        LineageSettings.System.putIntForUser(getContentResolver(), setting, Integer.valueOf(value), UserHandle.USER_CURRENT);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mThreeFingersSwipeAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.ENABLE_ROTATION_BUTTON, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.POCKET_JUDGE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SCREEN_ON_MEMORY_RECLAIM, 1, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.AUTO_BRIGHTNESS_ONE_SHOT, 0, UserHandle.USER_CURRENT);
        SensorBlock.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_misc) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    final Resources res = context.getResources();

                    boolean mPocketJudgeSupported = res.getBoolean(
                            com.android.internal.R.bool.config_pocketModeSupported);
                    if (!mPocketJudgeSupported)
                        keys.add(POCKET_JUDGE);

                    return keys;
                }
            };
}
