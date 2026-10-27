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

package com.crdroid.settings.fragments.themes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.android.internal.ichthys.IIchthysConfig;
import com.android.internal.ichthys.config.NixParser;
import com.android.internal.ichthys.config.NixPrinter;
import com.android.internal.ichthys.config.OptionSchema;
import com.android.settings.R;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IchthysOS (tracker#59): list rows that are ichthys.nix options, on whichever page they belong
 * (the status bar's style on Status bar, the tiles' style on Quick settings). A change is applied
 * in merge mode, so it is a generation that can be rolled back; the row shows the live value.
 * SystemUI follows these options live, so nothing is restarted.
 */
public final class NixOptionRows {
    private static final String TAG = "NixOptionRows";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NixOptionRows() {}

    /**
     * Binds the list row [key] of [fragment] to the ichthys.nix option [path]; [fallback] is the
     * value shown while the config doesn't set it.
     */
    public static void bind(PreferenceFragmentCompat fragment, String key, String path,
            String fallback) {
        final Preference found = fragment.findPreference(key);
        if (!(found instanceof ListPreference)) {
            return;
        }
        final ListPreference row = (ListPreference) found;
        final IIchthysConfig service =
                IIchthysConfig.Stub.asInterface(ServiceManager.getService("ichthys_config"));
        row.setEnabled(service != null);
        if (service == null) {
            return;
        }
        row.setOnPreferenceChangeListener((p, newValue) -> {
            final String config = "{ " + path + " = "
                    + NixPrinter.value(String.valueOf(newValue)) + "; }";
            final Context ctx = fragment.getContext().getApplicationContext();
            EXECUTOR.execute(() -> {
                String message;
                boolean failed = false;
                try {
                    final int gen = service.apply(config, true, "Settings: " + path,
                            UserHandle.USER_CURRENT);
                    message = ctx.getString(R.string.theme_studio_applied, gen);
                } catch (RemoteException | RuntimeException e) {
                    message = ctx.getString(R.string.theme_studio_error,
                            NixConfigActions.reason(e));
                    failed = true;
                }
                final String toast = message;
                // No SystemUI restart: both rows bound here (status bar layout, tiles style) are
                // watched live by SystemUI (StatusIconContainer, rememberShoalQsEnabled).
                MAIN.post(() -> Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show());
                // IchthysOS: the row already shows the new value; after a failed apply, read the
                // live one back instead of leaving a value that isn't in effect.
                if (failed) {
                    refresh(fragment, row, service, path, fallback);
                }
            });
            return true;
        });
        refresh(fragment, row, service, path, fallback);
    }

    private static void refresh(PreferenceFragmentCompat fragment, ListPreference row,
            IIchthysConfig service, String path, String fallback) {
        EXECUTOR.execute(() -> {
            final Map<String, Object> live;
            try {
                live = OptionSchema.create().evaluate(NixParser.parse(
                        service.exportConfig(true, UserHandle.USER_CURRENT)));
            } catch (Exception e) {
                Log.w(TAG, "refresh " + path, e);
                return;
            }
            final Object value = live.get(path);
            final String v = value == null ? fallback : String.valueOf(value);
            MAIN.post(() -> {
                if (fragment.isAdded() && row.findIndexOfValue(v) >= 0) {
                    row.setValue(v);
                }
            });
        });
    }
}
