/*
 * Copyright (C) 2021-2024 crDroid Android Project
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

package com.crdroid.settings.fragments.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;

import java.lang.CharSequence;

import lineageos.providers.LineageSettings;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Theme Studio &gt; Palette: the Monet style and the colour source (wallpaper or preset colour),
 * stored in the theme customization JSON that SystemUI's ThemeOverlayController reads.
 *
 * <p>The accent colour itself is picked in Theme Studio &gt; Accent colour
 * ({@link com.crdroid.settings.fragments.themes.ThemeEngine}); with the "preset" source this page
 * keeps whatever is set there. crDroid's other extras (background colour, luminance and chroma
 * factors, whole palette, tint background) were removed, because nothing in this tree reads them.
 */
@SearchIndexable
public class MonetSettings extends DashboardFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "MonetSettings";
    private static final String OVERLAY_CATEGORY_ACCENT_COLOR =
            "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE =
            "android.theme.customization.system_palette";
    private static final String OVERLAY_CATEGORY_THEME_STYLE =
            "android.theme.customization.theme_style";
    private static final String OVERLAY_COLOR_SOURCE =
            "android.theme.customization.color_source";
    private static final String OVERLAY_COLOR_BOTH =
            "android.theme.customization.color_both";
    /** Fields the removed crDroid extras used to write; dropped whenever this page writes. */
    private static final String[] REMOVED_KEYS = {
            "android.theme.customization.bg_color",
            "android.theme.customization.luminance_factor",
            "android.theme.customization.chroma_factor",
            "android.theme.customization.whole_palette",
            "android.theme.customization.tint_background",
    };
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String COLOR_SOURCE_HOME = "home_wallpaper";
    private static final String TIMESTAMP_FIELD = "_applied_timestamp";

    private static final String PREF_THEME_STYLE = "theme_style";
    private static final String PREF_COLOR_SOURCE = "color_source";
    /** SharedPreferences key the removed accent picker used; still the fallback preset colour. */
    private static final String PREF_ACCENT_COLOR = "accent_color";

    private ListPreference mThemeStylePref;
    private ListPreference mColorSourcePref;

    private int mAccentColorValue;

    private SharedPreferences mSharedPreferences;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.monet_engine;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mThemeStylePref = findPreference(PREF_THEME_STYLE);
        mColorSourcePref = findPreference(PREF_COLOR_SOURCE);
        mSharedPreferences = getActivity().getSharedPreferences(TAG, Context.MODE_PRIVATE);
        mAccentColorValue = mSharedPreferences.getInt(PREF_ACCENT_COLOR, currentAccent());

        updatePreferences();

        mThemeStylePref.setOnPreferenceChangeListener(this);
        mColorSourcePref.setOnPreferenceChangeListener(this);
    }

    /**
     * The fallback preset colour when neither the theme JSON nor an earlier pick has one: the
     * accent the system is showing right now (the theme token), not a hard-coded colour.
     */
    private int currentAccent() {
        return com.android.settingslib.Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorAccent) | 0xFF000000;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.BERRY_BLACK_THEME, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    private void updatePreferences() {
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson != null && !overlayPackageJson.isEmpty()) {
            try {
                final JSONObject object = new JSONObject(overlayPackageJson);
                final String style = object.optString(OVERLAY_CATEGORY_THEME_STYLE, "TONAL_SPOT");
                final String source = object.optString(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
                if (object.has(OVERLAY_CATEGORY_SYSTEM_PALETTE)) {
                    mAccentColorValue = ColorPickerPreference.convertToColorInt(
                            object.optString(OVERLAY_CATEGORY_SYSTEM_PALETTE));
                } else {
                    mAccentColorValue = mSharedPreferences.getInt(PREF_ACCENT_COLOR, currentAccent());
                }
                boolean both;
                if (object.has(OVERLAY_COLOR_BOTH)) {
                    both = object.optInt(OVERLAY_COLOR_BOTH) == 1;
                } else {
                    both = false;
                }
                // style handling
                boolean styleUpdated = false;
                if (style != null && !style.isEmpty()) {
                    for (CharSequence value : mThemeStylePref.getEntryValues()) {
                        if (value.toString().equals(style)) {
                            styleUpdated = true;
                            break;
                        }
                    }
                    if (styleUpdated) {
                        updateListByValue(mThemeStylePref, style);
                    }
                }
                if (!styleUpdated) {
                    updateListByValue(mThemeStylePref,
                            mThemeStylePref.getEntryValues()[0].toString());
                }
                // color handling
                final String sourceVal = (source == null || source.isEmpty() ||
                        (source.equals(COLOR_SOURCE_HOME) && both)) ? "both" : source;
                updateListByValue(mColorSourcePref, sourceVal);
                setColorValue();
            } catch (JSONException | IllegalArgumentException ignored) {}
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mThemeStylePref) {
            String value = (String) newValue;
            setStyleValue(value);
            updateListByValue(mThemeStylePref, value, false);
            return true;
        } else if (preference == mColorSourcePref) {
            String value = (String) newValue;
            setSourceValue(value);
            updateListByValue(mColorSourcePref, value, false);
            setColorValue(value);
            return true;
        }
        return false;
    }

    private void updateListByValue(ListPreference pref, String value) {
        updateListByValue(pref, value, true);
    }

    private void updateListByValue(ListPreference pref, String value, boolean set) {
        if (set) pref.setValue(value);
        final int index = pref.findIndexOfValue(value);
        if (index >= 0) {
            pref.setSummary(pref.getEntries()[index]);
        }
    }

    private JSONObject getSettingsJson() throws JSONException {
        final String overlayPackageJson = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (overlayPackageJson == null || overlayPackageJson.isEmpty())
            return new JSONObject();
        return new JSONObject(overlayPackageJson);
    }

    private void putSettingsJson(JSONObject object) {
        for (String key : REMOVED_KEYS) {
            object.remove(key);
        }
        final String current = Settings.Secure.getStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (object.toString().equals(current)) {
            return; // nothing changed: don't make SystemUI re-evaluate the theme
        }
        Settings.Secure.putStringForUser(
                getActivity().getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                object.toString(), UserHandle.USER_CURRENT);
    }

    private void setStyleValue(String style) {
        try {
            JSONObject object = getSettingsJson();
            object.putOpt(OVERLAY_CATEGORY_THEME_STYLE, style);
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setSourceValue(String source) {
        try {
            JSONObject object = getSettingsJson();
            if (source.equals("both")) {
                object.putOpt(OVERLAY_COLOR_BOTH, 1);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_HOME);
            } else {
                object.remove(OVERLAY_COLOR_BOTH);
                object.putOpt(OVERLAY_COLOR_SOURCE, source);
            }
            object.putOpt(TIMESTAMP_FIELD, System.currentTimeMillis());
            if (!source.equals(COLOR_SOURCE_PRESET)) {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    private void setColorValue() {
        setColorValue(mColorSourcePref.getValue());
    }

    /** Keeps the preset accent in the JSON while the source is "preset", and out of it otherwise. */
    private void setColorValue(String source) {
        try {
            JSONObject object = getSettingsJson();

            if (COLOR_SOURCE_PRESET.equals(source)) {
                final String rgbColor = ColorPickerPreference.convertToRGB(mAccentColorValue).replace("#", "");
                object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgbColor);
                object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgbColor);
            } else {
                object.remove(OVERLAY_CATEGORY_ACCENT_COLOR);
                object.remove(OVERLAY_CATEGORY_SYSTEM_PALETTE);
            }
            putSettingsJson(object);
        } catch (JSONException | IllegalArgumentException ignored) {}
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.monet_engine);
}
