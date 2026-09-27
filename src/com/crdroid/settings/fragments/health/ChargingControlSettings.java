/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 IchthysOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.health;

import static lineageos.health.HealthInterface.MODE_AUTO;
import static lineageos.health.HealthInterface.MODE_LIMIT;
import static lineageos.health.HealthInterface.MODE_MANUAL;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.preferences.CustomSeekBarPreference;

import lineageos.health.HealthInterface;
import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.preference.LineageSystemSettingMainSwitchPreference;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Charging control (charge limiting / smart charging).
 *
 * Ported from LineageParts (packages/apps/LineageParts, lineage-23.0)
 * src/org/lineageos/lineageparts/health/ChargingControlSettings.java. LineageParts is not built
 * in this ROM, so the screen lives in crDroidSettings and is reached from the IchthysOS tab.
 *
 * Backend chain (all verified present on a52sxq):
 *   this fragment
 *     -> lineageos.health.HealthInterface  (SDK facade, binder "lineagehealth")
 *     -> org.lineageos.platform.internal.health.HealthInterfaceService (LineageSystemServer)
 *     -> vendor.lineage.health.IChargingControl/default  (vendor.lineage.health-service.default)
 *     -> /sys/class/power_supply/battery/batt_slate_mode
 *
 * The service only publishes its binder when the org.lineageos.health feature XML is installed
 * (device/samsung/a52sxq/lineage_a52sxq.mk) AND the internal health sources are compiled into
 * org.lineageos.platform (lineage-sdk/Android.bp). Both were missing before this change; if
 * either regresses, isChargingControlSupported() goes false and the IchthysOS tab hides the
 * entry rather than showing a dead screen.
 */
@SearchIndexable
public class ChargingControlSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "ChargingControlSettings";

    public static final String CHARGING_CONTROL_PREF = "charging_control";

    private static final String CHARGING_CONTROL_ENABLED_PREF = "charging_control_enabled";
    private static final String CHARGING_CONTROL_MODE_PREF = "charging_control_mode";
    private static final String CHARGING_CONTROL_START_TIME_PREF = "charging_control_start_time";
    private static final String CHARGING_CONTROL_TARGET_TIME_PREF = "charging_control_target_time";
    private static final String CHARGING_CONTROL_LIMIT_PREF = "charging_control_charging_limit";

    private LineageSystemSettingMainSwitchPreference mChargingControlEnabledPref;
    private LineageSystemSettingListPreference mChargingControlModePref;
    private StartTimePreference mChargingControlStartTimePref;
    private TargetTimePreference mChargingControlTargetTimePref;
    private CustomSeekBarPreference mChargingControlLimitPref;

    private HealthInterface mHealthInterface;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // HealthInterface.getInstance() throws when the org.lineageos.health feature is declared
        // but the binder never published. The static form swallows that, so probe with it first
        // and bail out before inflating anything. This also covers deep links from search or a
        // stale shortcut, which bypass the IchthysOS tab's own guard.
        if (!HealthInterface.isChargingControlSupported(getContext())) {
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
            if (getActivity() != null) {
                getActivity().finish();
            }
            return;
        }

        addPreferencesFromResource(R.xml.charging_control_settings);

        mHealthInterface = HealthInterface.getInstance(getContext());

        final PreferenceScreen prefSet = getPreferenceScreen();

        mChargingControlEnabledPref = prefSet.findPreference(CHARGING_CONTROL_ENABLED_PREF);
        mChargingControlModePref = prefSet.findPreference(CHARGING_CONTROL_MODE_PREF);
        mChargingControlStartTimePref = prefSet.findPreference(CHARGING_CONTROL_START_TIME_PREF);
        mChargingControlTargetTimePref = prefSet.findPreference(CHARGING_CONTROL_TARGET_TIME_PREF);
        mChargingControlLimitPref = prefSet.findPreference(CHARGING_CONTROL_LIMIT_PREF);

        if (mChargingControlEnabledPref != null) {
            mChargingControlEnabledPref.setOnPreferenceChangeListener(this);
        }
        if (mChargingControlModePref != null) {
            mChargingControlModePref.setOnPreferenceChangeListener(this);
        }
        if (mChargingControlLimitPref != null) {
            mChargingControlLimitPref.setOnPreferenceChangeListener(this);
        }

        // "Limit charging" is only offered when the HAL reports a mode that can actually hold a
        // percentage (TOGGLE or LIMIT). On a52sxq the HAL is configured with
        // charging_control_supports_bypass=false and a toggle node, so this is expected true.
        if (mChargingControlModePref != null && mChargingControlLimitPref != null
                && mHealthInterface.allowFineGrainedSettings()) {
            final Resources res = getResources();
            mChargingControlModePref.setEntries(concatStringArrays(
                    mChargingControlModePref.getEntries(),
                    res.getStringArray(
                            R.array.charging_control_mode_entries_fine_grained_control)));
            mChargingControlModePref.setEntryValues(concatStringArrays(
                    mChargingControlModePref.getEntryValues(),
                    res.getStringArray(
                            R.array.charging_control_mode_values_fine_grained_control)));
        }

        refreshValues();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHealthInterface != null) {
            refreshValues();
        }
    }

    private void refreshValues() {
        if (mChargingControlEnabledPref != null) {
            mChargingControlEnabledPref.setChecked(mHealthInterface.getEnabled());
        }
        if (mChargingControlModePref != null) {
            mChargingControlModePref.setValue(Integer.toString(mHealthInterface.getMode()));
        }
        if (mChargingControlStartTimePref != null) {
            mChargingControlStartTimePref.setValue(mHealthInterface.getStartTime());
        }
        if (mChargingControlTargetTimePref != null) {
            mChargingControlTargetTimePref.setValue(mHealthInterface.getTargetTime());
        }
        if (mChargingControlLimitPref != null) {
            mChargingControlLimitPref.setValue(mHealthInterface.getLimit());
        }
        refreshUi(mHealthInterface.getMode());
    }

    private void refreshUi(final int chargingControlMode) {
        final String summary;
        boolean showStartTime = false;
        boolean showTargetTime = false;
        boolean showLimit = false;

        final Resources res = getResources();

        switch (chargingControlMode) {
            case MODE_AUTO:
                summary = res.getString(R.string.charging_control_mode_auto_summary);
                break;
            case MODE_MANUAL:
                summary = res.getString(R.string.charging_control_mode_custom_summary);
                showStartTime = true;
                showTargetTime = true;
                break;
            case MODE_LIMIT:
                summary = res.getString(R.string.charging_control_mode_limit_summary);
                showLimit = true;
                break;
            default:
                return;
        }

        if (mChargingControlModePref != null) {
            mChargingControlModePref.setSummary(summary);
        }
        if (mChargingControlStartTimePref != null) {
            mChargingControlStartTimePref.setVisible(showStartTime);
        }
        if (mChargingControlTargetTimePref != null) {
            mChargingControlTargetTimePref.setVisible(showTargetTime);
        }
        if (mChargingControlLimitPref != null) {
            mChargingControlLimitPref.setVisible(showLimit);
        }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object objValue) {
        if (preference == mChargingControlEnabledPref) {
            mHealthInterface.setEnabled((Boolean) objValue);
        } else if (preference == mChargingControlModePref) {
            final int chargingControlMode = Integer.parseInt((String) objValue);
            mHealthInterface.setMode(chargingControlMode);
            refreshUi(chargingControlMode);
        } else if (preference == mChargingControlLimitPref) {
            try { mHealthInterface.setLimit((Integer) objValue); } catch (IllegalArgumentException e) { android.util.Log.e(TAG, "Invalid charging limit", e); return false; }
        }
        return true;
    }

    private static CharSequence[] concatStringArrays(CharSequence[] a, CharSequence[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Stream.concat(Arrays.stream(a), Arrays.stream(b))
                .toArray(size -> (CharSequence[]) Array.newInstance(CharSequence.class, size));
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.charging_control_settings) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    // Keep every key out of the index when the backend is absent, so search
                    // cannot surface a screen that would do nothing.
                    if (!HealthInterface.isChargingControlSupported(context)) {
                        keys.add(CHARGING_CONTROL_PREF);
                        keys.add(CHARGING_CONTROL_ENABLED_PREF);
                        keys.add(CHARGING_CONTROL_MODE_PREF);
                        keys.add(CHARGING_CONTROL_START_TIME_PREF);
                        keys.add(CHARGING_CONTROL_TARGET_TIME_PREF);
                        keys.add(CHARGING_CONTROL_LIMIT_PREF);
                    }
                    return keys;
                }
            };
}
