/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 IchthysOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.health;

import android.content.Context;
import android.util.AttributeSet;

import com.android.settings.R;

/** Ported from LineageParts (lineage-23.0) health/TargetTimePreference. */
public class TargetTimePreference extends TimePreference {

    public TargetTimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int getSummaryResourceId() {
        return R.string.charging_control_target_time_summary;
    }

    @Override
    protected int getTimeSetting() {
        return mHealthInterface.getTargetTime();
    }

    @Override
    protected void setTimeSetting(int secondOfDay) {
        mHealthInterface.setTargetTime(secondOfDay);
    }
}
