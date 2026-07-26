/*
 * Copyright (C) 2024 IchthysOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.crdroid.settings.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/**
 * A switch preference rendered as a solid RED "danger" card, echoing the
 * power-menu Emergency/SOS button.
 *
 * Why this (and not just android:layout / android:background): A17's
 * SettingsPreferenceGroupAdapter forces the Expressive row layout and, in
 * updateBackground(), overwrites a normal row's itemView background with the
 * surface card. But its "branch 1" only overrides the background when it is
 * null (the Expressive row root is a DrawableStateLayout). So we set our red
 * stateful background HERE in onBindViewHolder, which runs BEFORE the adapter's
 * updateBackground -> the adapter then sees a non-null background and PRESERVES
 * it, only driving its corner state (top/middle/bottom/single). White text for
 * contrast on red (the icon is tinted white in Miscellaneous.java).
 */
public class DangerSwitchPreference extends SecureSettingSwitchPreference {

    public DangerSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public DangerSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DangerSwitchPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackgroundResource(R.drawable.ichthys_danger_bg_stateful);
        final TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(0xFFFFFFFF);
        }
        final TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(0xE6FFFFFF);
        }
    }
}
