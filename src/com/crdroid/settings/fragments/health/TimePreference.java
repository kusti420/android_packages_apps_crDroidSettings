/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 IchthysOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.crdroid.settings.fragments.health;

import static java.time.format.FormatStyle.SHORT;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import lineageos.health.HealthInterface;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Ported from LineageParts (lineage-23.0) src/org/lineageos/lineageparts/health/TimePreference.
 *
 * Upstream subclasses LineageParts' own CustomDialogPreference and inflates a dialog_time
 * layout. LineageParts is not built in this ROM and porting its dialog-preference
 * infrastructure into Settings would drag in several unrelated widgets, so this version is a
 * plain Preference that opens the framework TimePickerDialog on click. Behaviour and the
 * persisted value (seconds-of-day, via HealthInterface) are identical.
 */
public abstract class TimePreference extends Preference {

    private static final String TAG = "TimePreference";

    private static final DateTimeFormatter sFormatter = DateTimeFormatter.ofLocalizedTime(SHORT);

    protected final HealthInterface mHealthInterface;

    private LocalTime mLocalTime;

    public TimePreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // getInstance() throws when the health feature is declared but the binder is missing.
        // ChargingControlSettings already refuses to inflate this XML in that state; tolerate it
        // here too so a stray inflation degrades to a disabled row instead of crashing Settings.
        HealthInterface healthInterface = null;
        try {
            healthInterface = HealthInterface.getInstance(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Lineage Health service unavailable", e);
        }
        mHealthInterface = healthInterface;
        if (mHealthInterface == null) {
            setEnabled(false);
        }
        // Never let the Preference framework own this value: it lives behind HealthInterface.
        setPersistent(false);
        mLocalTime = LocalTime.ofSecondOfDay(0);
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        if (mHealthInterface != null) {
            mLocalTime = LocalTime.ofSecondOfDay(clampToDay(getTimeSetting()));
        }
        super.onBindViewHolder(holder);
    }

    @Override
    protected void onClick() {
        if (mHealthInterface == null) {
            return;
        }
        final Context context = getContext();
        new TimePickerDialog(context, (view, hourOfDay, minute) -> {
            mLocalTime = LocalTime.of(hourOfDay, minute);
            setTimeSetting(mLocalTime.toSecondOfDay());
            setSummary(getSummary());
        }, mLocalTime.getHour(), mLocalTime.getMinute(),
                DateFormat.is24HourFormat(context)).show();
    }

    @Override
    public CharSequence getSummary() {
        return String.format(getContext().getString(getSummaryResourceId()),
                mLocalTime.format(sFormatter));
    }

    public void setValue(final int value) {
        mLocalTime = LocalTime.ofSecondOfDay(clampToDay(value));
        setSummary(getSummary());
    }

    /**
     * HealthInterface returns 0 when the binder service is unavailable and the stored value is
     * user-writable, so guard the LocalTime conversion against out-of-range seconds.
     */
    private static int clampToDay(final int secondOfDay) {
        if (secondOfDay < 0) {
            return 0;
        }
        return Math.min(secondOfDay, 86399);
    }

    protected abstract int getSummaryResourceId();

    protected abstract int getTimeSetting();

    protected abstract void setTimeSetting(int secondOfDay);
}
