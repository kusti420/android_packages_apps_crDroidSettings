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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
 * IchthysOS &gt; Apps: Ichthys Apps (the app store), the Background and Install &amp;
 * compatibility pages, and integrity spoofing (Play Integrity Fix, TrickyStore, the Google
 * Photos spoof).
 */
@SearchIndexable
public class IchthysApps extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysApps";

    private static final String KEY_STORE = "ichthys_apps_store";
    private static final String STORE_PACKAGE = "org.ichthys.apps";
    /** IchthysOS: the Google Photos spoof (moved here from the Play Integrity Fix page). */
    private static final String KEY_SPOOF_PHOTOS = "spoof_pif_photos";
    private static final String PHOTOS_PACKAGE = "com.google.android.apps.photos";
    private static final String VENDING_PACKAGE = "com.android.vending";

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

        // IchthysOS: the spoof is read when Photos starts (and Play checks it), so both restart
        // to pick a change up, as the Play Integrity Fix page did.
        final Preference photos = findPreference(KEY_SPOOF_PHOTOS);
        if (photos != null) {
            photos.setOnPreferenceChangeListener((p, v) -> {
                forceStop(PHOTOS_PACKAGE);
                forceStop(VENDING_PACKAGE);
                return true;
            });
        }
    }

    private void forceStop(String pkg) {
        try {
            getContext().getSystemService(ActivityManager.class).forceStopPackage(pkg);
        } catch (RuntimeException ignored) {
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
