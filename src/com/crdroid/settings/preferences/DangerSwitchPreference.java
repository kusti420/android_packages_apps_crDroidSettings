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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.ichthys.ShoalBadgeDrawable;
import com.android.settingslib.widget.ShoalOwnRowDrawable;

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
 * it, only driving its corner state (top/middle/bottom/single). The card goes on
 * as a ShoalOwnRowDrawable so the adapter's Shoal surface pass (hairline slice,
 * tonal restyle, muted summary) leaves it alone too. White text and a plain white
 * glyph for contrast on red; the page's accent badges (ShoalStyle.badgeAll) are
 * declined in setIcon.
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
        bindDanger(holder);
    }

    @Override
    public void setIcon(Drawable icon) {
        if (!keepsGlyph(icon)) super.setIcon(icon);
    }

    /** Applies the red danger card look; shared with the other Danger* preferences. */
    static void bindDanger(PreferenceViewHolder holder) {
        ShoalOwnRowDrawable.set(holder.itemView, R.drawable.ichthys_danger_bg_stateful);
        final TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(0xFFFFFFFF);
        }
        final TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(0xE6FFFFFF);
        }
        // Plain white glyph, like the power-menu Emergency button's.
        final ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        if (icon != null && icon.getDrawable() != null) {
            final Drawable glyph = icon.getDrawable().mutate();
            glyph.setTint(0xFFFFFFFF);
            icon.setImageDrawable(glyph);
            // IchthysOS: the row's ImageView tint (the accent) won over the drawable's own tint,
            // leaving a pink glyph on the red card.
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        }
    }

    /**
     * True when {@code icon} is a Shoal badge: the danger card keeps its plain glyph instead,
     * so a page's ShoalStyle.badgeAll leaves these rows red with white glyphs.
     */
    static boolean keepsGlyph(Drawable icon) {
        return icon instanceof ShoalBadgeDrawable;
    }
}
