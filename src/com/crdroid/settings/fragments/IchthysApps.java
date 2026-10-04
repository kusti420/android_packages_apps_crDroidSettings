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
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.List;

/**
 * IchthysOS &gt; Apps: Ichthys Apps (the app store), the Background page, app downgrades and
 * integrity spoofing (Play Integrity Fix, TrickyStore).
 */
@SearchIndexable
public class IchthysApps extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysApps";

    private static final String KEY_STORE = "ichthys_apps_store";
    private static final String STORE_PACKAGE = "org.ichthys.apps";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_apps);

        // Ichthys Apps is an app of its own: open its launcher activity, or drop the row when it
        // is not installed.
        final Preference store = findPreference(KEY_STORE);
        if (store != null) {
            final Intent launch = storeIntent(getContext());
            if (launch != null) {
                store.setIntent(launch);
            } else {
                getPreferenceScreen().removePreference(store);
            }
        }
    }

    private static Intent storeIntent(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(STORE_PACKAGE);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent.
        ShoalStyle.badgeAll(getPreferenceScreen(), ShoalStyle.accent(view.getContext()));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Global.putInt(resolver, "pm_downgrade_allowed", 0);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.ichthys_apps) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    if (storeIntent(context) == null) {
                        keys.add(KEY_STORE);
                    }
                    return keys;
                }
            };
}
