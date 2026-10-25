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

package com.crdroid.settings.fragments.themes;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * IchthysOS (tracker#59): the palette rows of the Theme page, palette style and colour source.
 * They were the separate Monet settings page; same settings (the theme customization JSON),
 * same behaviour, now beside the accent they belong with.
 */
final class PaletteRows {
    private static final String PREF_THEME_STYLE = "theme_style";
    private static final String PREF_COLOR_SOURCE = "color_source";

    private static final String OVERLAY_CATEGORY_ACCENT_COLOR =
            "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE =
            "android.theme.customization.system_palette";
    private static final String OVERLAY_CATEGORY_THEME_STYLE =
            "android.theme.customization.theme_style";
    private static final String OVERLAY_COLOR_SOURCE = "android.theme.customization.color_source";
    private static final String OVERLAY_COLOR_BOTH = "android.theme.customization.color_both";
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String COLOR_SOURCE_HOME = "home_wallpaper";
    private static final String TIMESTAMP_FIELD = "_applied_timestamp";

    private final Context mContext;
    private final ListPreference mStyle;
    private final ListPreference mSource;

    PaletteRows(PreferenceFragmentCompat fragment) {
        mContext = fragment.getContext();
        mStyle = fragment.findPreference(PREF_THEME_STYLE);
        mSource = fragment.findPreference(PREF_COLOR_SOURCE);
        if (mStyle == null || mSource == null) {
            return;
        }
        mStyle.setOnPreferenceChangeListener((p, v) -> {
            put(OVERLAY_CATEGORY_THEME_STYLE, (String) v);
            show(mStyle, (String) v);
            return true;
        });
        mSource.setOnPreferenceChangeListener((p, v) -> {
            setSource((String) v);
            show(mSource, (String) v);
            return true;
        });
    }

    /** Shows the live values (call on resume: the accent page may have changed them). */
    void refresh() {
        if (mStyle == null || mSource == null) {
            return;
        }
        final JSONObject object = json();
        final String style = object.optString(OVERLAY_CATEGORY_THEME_STYLE, "TONAL_SPOT");
        show(mStyle, mStyle.findIndexOfValue(style) >= 0 ? style
                : mStyle.getEntryValues()[0].toString());
        mStyle.setValue(mStyle.findIndexOfValue(style) >= 0 ? style
                : mStyle.getEntryValues()[0].toString());
        final String source = object.optString(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
        final boolean both = object.optInt(OVERLAY_COLOR_BOTH, 0) == 1;
        final String value = (source.isEmpty() || (COLOR_SOURCE_HOME.equals(source) && both))
                ? "both" : source;
        if (mSource.findIndexOfValue(value) >= 0) {
            mSource.setValue(value);
            show(mSource, value);
        }
    }

    private static void show(ListPreference pref, String value) {
        final int i = pref.findIndexOfValue(value);
        if (i >= 0) {
            pref.setSummary(pref.getEntries()[i]);
        }
    }

    private JSONObject json() {
        final String s = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UserHandle.USER_CURRENT);
        try {
            return s == null || s.isEmpty() ? new JSONObject() : new JSONObject(s);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void write(JSONObject object) {
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, object.toString(),
                UserHandle.USER_CURRENT);
    }

    private void put(String key, String value) {
        try {
            final JSONObject object = json();
            object.putOpt(key, value);
            write(object);
        } catch (JSONException ignored) {
        }
    }

    /** As the Monet page did: "both" is home wallpaper for both; preset keeps the accent. */
    private void setSource(String source) {
        try {
            final JSONObject object = json();
            if ("both".equals(source)) {
                object.putOpt(OVERLAY_COLOR_BOTH, 1);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
            } else {
                object.remove(OVERLAY_COLOR_BOTH);
                object.putOpt(OVERLAY_COLOR_SOURCE, source);
            }
            object.putOpt(TIMESTAMP_FIELD, System.currentTimeMillis());
            if (COLOR_SOURCE_PRESET.equals(source)) {
                if (!object.has(OVERLAY_CATEGORY_SYSTEM_PALETTE)) {
                    final int accent = com.android.settingslib.Utils.getColorAttrDefaultColor(
                            mContext, android.R.attr.colorAccent) | 0xFF000000;
                    final String rgb = ColorPickerPreference.convertToRGB(accent).replace("#", "");
                    object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgb);
                    object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgb);
                }
            } else {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            write(object);
        } catch (JSONException ignored) {
        }
    }
}
