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
package com.crdroid.settings.fragments.backstage;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IchthysOS &gt; Apps &gt; Background &gt; Apps in Backstage: pick apps to keep running "in the
 * foreground" on a hidden display. The time limit is on the Background page.
 * Only edits Settings.Secure ichthys_backstage_pkgs; IchthysBackstageService in system_server does
 * the work. An app opened again stays in the list (it goes back to its hidden display when the
 * user leaves it); the service takes it out of the list when it is stopped (notification, time
 * limit), uninstalled, or when the list is cleared at boot or user switch.
 * (kusti420/ichthysos-tracker#38)
 */
public class Backstage extends SettingsPreferenceFragment {

    /** Kept in sync with IchthysBackstageService.KEY_PACKAGES. */
    private static final String KEY_PACKAGES = "ichthys_backstage_pkgs";
    private static final String APPS_CATEGORY = "backstage_apps";
    private static final int MAX_APPS = 3;

    private PreferenceCategory mApps;
    private final ContentObserver mObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            refreshStates();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_backstage);
        mApps = findPreference(APPS_CATEGORY);
        populateApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(KEY_PACKAGES), false, mObserver);
        refreshStates();
    }

    @Override
    public void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    private void populateApps() {
        final Context context = getPrefContext();
        final PackageManager pm = context.getPackageManager();
        final Intent launcher =
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
        final Set<String> seen = new LinkedHashSet<>();
        final List<ResolveInfo> apps = new ArrayList<>();
        // IchthysOS: labels loaded once; the sort used to load them again on every comparison.
        final Map<String, CharSequence> labels = new HashMap<>();
        for (ResolveInfo info : infos) {
            final String pkg = info.activityInfo.packageName;
            if (!pkg.equals(context.getPackageName()) && seen.add(pkg)) {
                apps.add(info);
                labels.put(pkg, info.loadLabel(pm));
            }
        }
        final Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(
                String.valueOf(labels.get(a.activityInfo.packageName)),
                String.valueOf(labels.get(b.activityInfo.packageName))));

        for (ResolveInfo info : apps) {
            final String pkg = info.activityInfo.packageName;
            addAppPreference(context, pkg, labels.get(pkg), info.loadIcon(pm));
        }
    }

    private SwitchPreferenceCompat addAppPreference(Context context, String pkg,
            CharSequence label, Drawable icon) {
        final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(context);
        pref.setKey(pkg);
        pref.setPersistent(false);
        pref.setTitle(label);
        pref.setIcon(icon);
        pref.setOnPreferenceChangeListener((p, value) -> {
            setInBackstage(pkg, (Boolean) value);
            // IchthysOS: refreshStates() already set the switch from the list. Returning true
            // let the framework flip it afterwards, so a pick refused because Backstage is
            // full (e.g. the tile filled it meanwhile) still showed as on.
            return false;
        });
        mApps.addPreference(pref);
        return pref;
    }

    /**
     * IchthysOS: a listed app without a row here (this Settings app, sent by the Backstage tile,
     * or an app whose launcher entry is gone) still holds one of the 3 places. Give it a row so
     * it can be switched off instead of leaving the page "full" with nothing to untick.
     */
    private void addMissingListedApps(List<String> pkgs) {
        final Context context = getPrefContext();
        final PackageManager pm = context.getPackageManager();
        for (String pkg : pkgs) {
            if (mApps.findPreference(pkg) != null) continue;
            CharSequence label = pkg;
            Drawable icon = null;
            try {
                final ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(ai);
                icon = pm.getApplicationIcon(ai);
            } catch (PackageManager.NameNotFoundException e) {
                // Uninstalled meanwhile: the package name is all there is.
            }
            addAppPreference(context, pkg, label, icon);
        }
    }

    /** The apps currently in Backstage. */
    public static List<String> readPackages(Context context) {
        final String value = Settings.Secure.getStringForUser(context.getContentResolver(),
                KEY_PACKAGES, UserHandle.USER_CURRENT);
        final List<String> pkgs = new ArrayList<>();
        if (!TextUtils.isEmpty(value)) {
            for (String p : value.split(",")) {
                if (!p.trim().isEmpty()) pkgs.add(p.trim());
            }
        }
        return pkgs;
    }

    private void setInBackstage(String pkg, boolean on) {
        final List<String> pkgs = readPackages(getPrefContext());
        if (on && !pkgs.contains(pkg) && pkgs.size() < MAX_APPS) {
            pkgs.add(pkg);
        } else if (!on) {
            pkgs.remove(pkg);
        }
        Settings.Secure.putStringForUser(getContentResolver(), KEY_PACKAGES,
                pkgs.isEmpty() ? null : TextUtils.join(",", pkgs), UserHandle.USER_CURRENT);
        refreshStates();
    }

    /** Mirrors the list (which the service also edits) and disables new picks when full. */
    private void refreshStates() {
        if (mApps == null || getPrefContext() == null) return;
        final List<String> pkgs = readPackages(getPrefContext());
        addMissingListedApps(pkgs);
        final boolean full = pkgs.size() >= MAX_APPS;
        for (int i = 0; i < mApps.getPreferenceCount(); i++) {
            final SwitchPreferenceCompat pref = (SwitchPreferenceCompat) mApps.getPreference(i);
            final boolean in = pkgs.contains(pref.getKey());
            pref.setChecked(in);
            pref.setEnabled(in || !full);
            pref.setSummary(in ? getString(R.string.backstage_app_running)
                    : full ? getString(R.string.backstage_full) : null);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }
}
