/*
 * Copyright (C) 2026 IchthysOS
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

import androidx.preference.PreferenceViewHolder;

/** A Settings.Global list preference on the red "danger" card (see DangerSwitchPreference). */
public class DangerGlobalListPreference extends GlobalSettingListPreference {

    public DangerGlobalListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public DangerGlobalListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DangerGlobalListPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        DangerSwitchPreference.bindDanger(holder);
    }

    @Override
    public void setIcon(Drawable icon) {
        if (!DangerSwitchPreference.keepsGlyph(icon)) super.setIcon(icon);
    }
}
