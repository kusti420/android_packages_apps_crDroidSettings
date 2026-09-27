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
package com.crdroid.settings.fragments.lockscreen;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.crdroid.settings.preferences.SystemSettingListPreference;

/**
 * IchthysOS: fingerprint (UDFPS) press effects, pressed icon, idle icon styles, result effects
 * and haptics. SystemUI's IchthysUdfpsSettings mirrors these keys and defaults.
 */
public class FingerprintEffects extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_ANIM_EFFECT = "ichthys_udfps_anim_effect";
    private static final String KEY_ANIM_COLOR_MODE = "ichthys_udfps_anim_color_mode";
    private static final String KEY_ANIM_COLOR = "ichthys_udfps_anim_color";
    private static final String KEY_PRESSED_STYLE = "ichthys_udfps_pressed_style";
    private static final String KEY_PRESSED_COLOR_MODE = "ichthys_udfps_pressed_color_mode";
    private static final String KEY_PRESSED_COLOR = "ichthys_udfps_pressed_color";
    private static final String KEY_ICON_STYLE = "ichthys_udfps_icon_style";
    private static final String KEY_ICON_COLOR_MODE = "ichthys_udfps_icon_color_mode";
    private static final String KEY_ICON_COLOR = "ichthys_udfps_icon_color";

    private static final String[] ANIM_DEPENDENT = {
        "ichthys_udfps_anim_speed", "ichthys_udfps_anim_scale", KEY_ANIM_COLOR_MODE,
    };
    private static final String[] PRESSED_DEPENDENT = {
        KEY_PRESSED_COLOR_MODE, "ichthys_udfps_pressed_size",
    };
    private static final String[] ICON_DEPENDENT = {
        KEY_ICON_COLOR_MODE, "ichthys_udfps_icon_opacity", "ichthys_udfps_icon_aod",
    };

    private SystemSettingListPreference mAnimEffect;
    private SystemSettingListPreference mAnimColorMode;
    private SystemSettingListPreference mPressedStyle;
    private SystemSettingListPreference mPressedColorMode;
    private SystemSettingListPreference mIconStyle;
    private SystemSettingListPreference mIconColorMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.fingerprint_effects);

        mAnimEffect = bind(KEY_ANIM_EFFECT);
        mAnimColorMode = bind(KEY_ANIM_COLOR_MODE);
        mPressedStyle = bind(KEY_PRESSED_STYLE);
        mPressedColorMode = bind(KEY_PRESSED_COLOR_MODE);
        mIconStyle = bind(KEY_ICON_STYLE);
        mIconColorMode = bind(KEY_ICON_COLOR_MODE);
        updateEnabled();
    }

    private SystemSettingListPreference bind(String key) {
        SystemSettingListPreference pref = (SystemSettingListPreference) findPreference(key);
        pref.setOnPreferenceChangeListener(this);
        return pref;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Values are stored by the preference itself; refresh dependents after it commits.
        getListView().post(this::updateEnabled);
        return true;
    }

    /** Options only make sense when the thing they tune is on. */
    private void updateEnabled() {
        final int effect = mAnimEffect.getIntValue(0);
        // Built-in effects (1..8) use speed/size/colour; the crDroid pack (0) uses size only.
        setEnabled(ANIM_DEPENDENT, effect > 0);
        findPreference("ichthys_udfps_anim_scale").setEnabled(effect >= 0);
        findPreference(KEY_ANIM_COLOR).setEnabled(effect > 0
                && mAnimColorMode.getIntValue(0) == 2);

        final boolean pressed = mPressedStyle.getIntValue(0) != 0;
        setEnabled(PRESSED_DEPENDENT, pressed);
        findPreference(KEY_PRESSED_COLOR).setEnabled(pressed
                && mPressedColorMode.getIntValue(0) == 2);

        final boolean icon = mIconStyle.getIntValue(0) != 0;
        setEnabled(ICON_DEPENDENT, icon);
        findPreference(KEY_ICON_COLOR).setEnabled(icon && mIconColorMode.getIntValue(0) == 2);
    }

    private void setEnabled(String[] keys, boolean enabled) {
        for (String key : keys) {
            Preference pref = findPreference(key);
            if (pref != null) pref.setEnabled(enabled);
        }
    }

    /** Deleting a key restores SystemUI's built-in default. */
    public static void reset(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String[] keys = {
            KEY_ANIM_EFFECT, "ichthys_udfps_anim_speed", "ichthys_udfps_anim_scale",
            KEY_ANIM_COLOR_MODE, KEY_ANIM_COLOR, "ichthys_udfps_anim_offset_x",
            "ichthys_udfps_anim_offset_y", "ichthys_udfps_anim_bp", "ichthys_udfps_anim_enroll",
            KEY_PRESSED_STYLE, KEY_PRESSED_COLOR_MODE, KEY_PRESSED_COLOR,
            "ichthys_udfps_pressed_size", KEY_ICON_STYLE, KEY_ICON_COLOR_MODE, KEY_ICON_COLOR,
            "ichthys_udfps_icon_opacity", "ichthys_udfps_icon_aod",
            "ichthys_udfps_success_effect", "ichthys_udfps_fail_effect",
            "ichthys_udfps_haptic_success", "ichthys_udfps_haptic_error",
        };
        for (String key : keys) {
            Settings.System.putStringForUser(resolver, key, null, UserHandle.USER_CURRENT);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }
}
