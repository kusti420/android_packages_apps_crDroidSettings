/*
 * Copyright (C) 2026 IchthysOS Project
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

package com.crdroid.settings.preferences;

import android.content.Context;
import android.provider.DeviceConfig;
import android.util.AttributeSet;

import lineageos.preference.SelfRemovingSwitchPreference;

/**
 * A switch preference backed by a DeviceConfig flag (some master switches — e.g. app hibernation
 * / "manage app if unused" — are read from DeviceConfig, NOT Settings.Global/System/Secure, so a
 * normal *SettingSwitchPreference would be a dead toggle).
 *
 * android:key must be "namespace/flag" (e.g. "app_hibernation/app_hibernation_enabled").
 * android:defaultValue is the value shown when the flag is unset.
 * Requires the host app to hold android.permission.WRITE_DEVICE_CONFIG.
 */
public class DeviceConfigSwitchPreference extends SelfRemovingSwitchPreference {

    public DeviceConfigSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public DeviceConfigSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DeviceConfigSwitchPreference(Context context) {
        super(context);
    }

    private String namespace() {
        final String key = getKey();
        final int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    private String flag() {
        final String key = getKey();
        final int slash = key.indexOf('/');
        return slash > 0 ? key.substring(slash + 1) : key;
    }

    @Override
    protected boolean isPersisted() {
        return DeviceConfig.getProperty(namespace(), flag()) != null;
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        // makeDefault=false: a runtime override that persists until changed (survives reboot on a
        // ROM without a remote config sync). Requires WRITE_DEVICE_CONFIG.
        DeviceConfig.setProperty(namespace(), flag(), Boolean.toString(value), /* makeDefault= */ false);
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultReturnValue) {
        return DeviceConfig.getBoolean(namespace(), flag(), defaultReturnValue);
    }
}
