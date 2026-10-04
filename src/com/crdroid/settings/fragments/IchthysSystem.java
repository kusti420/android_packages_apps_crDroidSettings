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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import lineageos.health.HealthInterface;

import java.util.List;

/**
 * IchthysOS &gt; System: how the phone behaves. Links to Buttons &amp; gestures, Notifications and
 * Sound &amp; haptics, then charging control, Smart Pixels, the screenshot preview duration, the
 * duress PIN, and the About rows (changelog, source, community).
 */
@SearchIndexable
public class IchthysSystem extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysSystem";

    private static final String KEY_CHARGING_CONTROL = "charging_control";
    private static final String KEY_SOURCE = "crdroid_source";
    private static final String KEY_TELEGRAM = "crdroid_telegram";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_system);

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
        getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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
                    return keys;
                }
            };
}
