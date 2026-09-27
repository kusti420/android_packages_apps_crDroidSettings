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
package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.utils.SystemUtils;

import lineageos.health.HealthInterface;

import java.util.List;

/**
 * IchthysOS tab: settings that are fully custom to IchthysOS and do NOT exist in stock crDroid.
 * crDroid-inherited features stay in their own tabs; only genuinely-new additions live here.
 */
@SearchIndexable
public class IchthysOS extends SettingsPreferenceFragment {

    public static final String TAG = "IchthysOS";

    private static final String CHARGING_CONTROL_PREF = "charging_control";
    private static final String BATTERY_CATEGORY = "ichthys_battery_category";
    private static final String CONTACTS_LOCKDOWN_PREF = "ichthys_contacts_lockdown";

    /**
     * Asks PermissionController to strip contacts from apps that already hold it. Kept in sync with
     * ContactsLockdownReceiver.ACTION_SWEEP, which lives in the permission APEX and so cannot be
     * referenced from here.
     */
    private static final String ACTION_SWEEP_CONTACTS_LOCKDOWN =
            "com.android.permissioncontroller.action.SWEEP_CONTACTS_LOCKDOWN";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.crdroid_settings_ichthys);

        // recents_type (Dagger @Provides, read once at start) and custom_pitch_black (applied on
        // theme re-eval) only take effect after a SystemUI restart, so prompt for one on change.
        Preference.OnPreferenceChangeListener restartListener = (pref, val) -> {
            SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
            return true;
        };
        Preference recents = findPreference("recents_type");
        if (recents != null) recents.setOnPreferenceChangeListener(restartListener);
        Preference pitchBlack = findPreference("custom_pitch_black");
        if (pitchBlack != null) pitchBlack.setOnPreferenceChangeListener(restartListener);

        // Switching the contacts lockdown on has to reach back and revoke what apps already hold;
        // without that the toggle would only ever affect the next request.
        Preference contactsLockdown = findPreference(CONTACTS_LOCKDOWN_PREF);
        if (contactsLockdown != null) {
            final Context appContext = getContext().getApplicationContext();
            contactsLockdown.setOnPreferenceChangeListener((pref, val) -> {
                if (Boolean.TRUE.equals(val)) {
                    // The preference persists the new value only once this listener has returned,
                    // and the sweep reads it straight back out of Settings.Global, so hand the
                    // broadcast to the next pass of the looper instead of sending it here.
                    new Handler(Looper.getMainLooper()).post(
                            () -> requestContactsLockdownSweep(appContext));
                }
                return true;
            });
        }

        // Charging control only exists when the Lineage Health binder is published, which needs
        // (a) the internal health sources compiled into org.lineageos.platform, (b) the
        // org.lineageos.health feature XML installed, and (c) the vendor.lineage.health HAL
        // running. Drop the whole category rather than open a screen that cannot do anything.
        if (!HealthInterface.isChargingControlSupported(getContext())) {
            // charging_control is the category's only child, so drop the whole category.
            Preference batteryCategory = findPreference(BATTERY_CATEGORY);
            if (batteryCategory != null) {
                getPreferenceScreen().removePreference(batteryCategory);
            }
        }
    }

    /**
     * Package-targeted so the manifest receiver is actually woken — a plain implicit broadcast
     * would be dropped by the background execution limits.
     */
    private static void requestContactsLockdownSweep(Context context) {
        try {
            final String permissionController =
                    context.getPackageManager().getPermissionControllerPackageName();
            if (permissionController == null) {
                return;
            }
            Intent sweep = new Intent(ACTION_SWEEP_CONTACTS_LOCKDOWN);
            sweep.setPackage(permissionController);
            context.sendBroadcast(sweep);
        } catch (RuntimeException e) {
            // The lockdown itself is already in force; only the clean-up of existing grants is
            // lost, and the next boot sweeps them anyway.
            Log.w(TAG, "Could not request the contacts lockdown sweep", e);
        }
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver, "disable_all_vibration", 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "recents_type", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_pitch_black", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_volume_horizontal", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_volume_steps", 0, UserHandle.USER_CURRENT);
        // Global, not per-user: the contacts lockdown is one switch for the whole device. Clearing
        // it does not hand back the permissions the sweep took away — those stay denied until the
        // user grants them again per app.
        Settings.Global.putInt(resolver, CONTACTS_LOCKDOWN_PREF, 0);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_ichthys) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    // Do not let search surface charging control when the backend is absent.
                    if (!HealthInterface.isChargingControlSupported(context)) {
                        keys.add(CHARGING_CONTROL_PREF);
                    }
                    return keys;
                }
            };
}
