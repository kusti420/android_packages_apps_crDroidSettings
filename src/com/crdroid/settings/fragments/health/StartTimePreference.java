/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 IchthysOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.health;

import android.content.Context;
import android.util.AttributeSet;

import com.android.settings.R;

/** Ported from LineageParts (lineage-23.0) health/StartTimePreference. */
public class StartTimePreference extends TimePreference {

    public StartTimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int getSummaryResourceId() {
        return R.string.charging_control_start_time_summary;
    }

    @Override
    protected int getTimeSetting() {
        return mHealthInterface.getStartTime();
    }

    @Override
    protected void setTimeSetting(int secondOfDay) {
        mHealthInterface.setStartTime(secondOfDay);
    }
}
