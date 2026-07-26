/*
 * Copyright (C) 2025 IchthysOS
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
package com.crdroid.settings.fragments.recents;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/**
 * Slim Recents (frameworks/opt/slimrecent) tuning. Only relevant when
 * Recents style = Slim (recents_type = 2). All preferences write to
 * Settings.System keys that RecentController observes at runtime, so they
 * apply the next time the panel is opened.
 */
@SearchIndexable
public class SlimRecents extends SettingsPreferenceFragment {

    public static final String TAG = "SlimRecents";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.slim_recents_settings);
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // Panel — restore RecentController's own coded defaults.
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_GRAVITY, android.view.Gravity.END,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_SCALE_FACTOR, 115, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_EXPANDED_MODE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENTS_MAX_APPS, 15, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SLIM_RECENTS_CORNER_RADIUS, 5, UserHandle.USER_CURRENT);
        // Memory bar.
        Settings.System.putIntForUser(resolver,
                Settings.System.SLIM_RECENTS_MEM_DISPLAY, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SLIM_RECENTS_MEM_DISPLAY_LONG_CLICK_CLEAR, 0,
                UserHandle.USER_CURRENT);
        Settings.System.putInt(resolver, Settings.System.SLIM_MEM_BAR_COLOR, 0x00ffffff);
        Settings.System.putInt(resolver, Settings.System.SLIM_MEM_TEXT_COLOR, 0x00ffffff);
        // Appearance.
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_PANEL_BG_COLOR, 0x00ffffff, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.RECENT_CARD_BG_COLOR, 0x00ffffff, UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.slim_recents_settings);
}
