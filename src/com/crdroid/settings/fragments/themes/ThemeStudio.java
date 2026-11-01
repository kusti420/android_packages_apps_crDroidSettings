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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.ichthys.IIchthysConfig;
import com.android.internal.ichthys.config.NixParser;
import com.android.internal.ichthys.config.NixPrinter;
import com.android.internal.ichthys.config.OptionSchema;
import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.utils.DeviceUtils;
import com.crdroid.settings.utils.SystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IchthysOS Theme Studio: the one place for look and feel, and the Settings front end of the
 * declarative OS config (ichthys.nix, IchthysConfigService).
 *
 * <p>The Theme section of the IchthysOS hub (tracker#59): colours (preset, accent, palette style
 * and colour source), surfaces and shape, type, icons, motion. The {@code theme_studio_*} rows
 * apply one-option configs in merge mode, so each change is a generation that can be rolled back
 * (System > ichthys.nix). Everything else that used to hang off this page (status bar, quick
 * settings, navigation, lock screen, volume, recents) has its own hub section now.
 */
@SearchIndexable
public class ThemeStudio extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ThemeStudio";

    private static final String KEY_PRESET = "theme_studio_preset";
    private static final String KEY_RADIUS = "theme_studio_radius";
    private static final String KEY_SHAPE = "theme_studio_shape";
    private static final String KEY_FONT = "theme_studio_font";
    private static final String KEY_STATUSBAR = "theme_studio_statusbar";
    private static final String KEY_QS = "theme_studio_qs";
    private static final String KEY_SURFACE = "theme_studio_surface";
    /** IchthysOS: theme.style, the Monet palette style; applied live, no SystemUI restart. */
    private static final String KEY_STYLE = "theme_style";
    private static final String KEY_BADGES = "theme_studio_badges";
    private static final String KEY_ICON_STYLE = "theme_studio_icon_style";
    private static final String KEY_IMPORT = "theme_studio_import";
    private static final String KEY_EXPORT = "theme_studio_export";
    private static final String KEY_GENERATIONS = "theme_studio_generations";

    // Lock screen link that depends on the hardware.
    private static final String KEY_FP_EFFECTS = "ichthys_fingerprint_effects";
    // Recents style, and the options page of its Slim style.
    private static final String KEY_RECENTS_TYPE = "recents_type";
    private static final String KEY_SLIM_RECENTS = "slim_recents";
    private static final int RECENTS_TYPE_SLIM = 2;

    private static final String FONT_CATEGORY = "android.theme.customization.font";
    private static final String NULL = "null";

    private static final int REQUEST_IMPORT = 1;
    private static final int REQUEST_EXPORT = 2;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private IIchthysConfig mService;
    private PaletteRows mPalette;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme_studio);
        mService = IIchthysConfig.Stub.asInterface(ServiceManager.getService("ichthys_config"));

        for (String key : new String[] {KEY_PRESET, KEY_RADIUS, KEY_SHAPE, KEY_FONT,
                KEY_SURFACE, KEY_STYLE, KEY_BADGES, KEY_ICON_STYLE}) {
            final Preference p = findPreference(key);
            if (p != null) {
                p.setOnPreferenceChangeListener(this);
                p.setEnabled(mService != null);
            }
        }
        setupPresets();
        setupFonts();
        setupLinkedRows();
        mPalette = new PaletteRows(this);
        if (mService == null) {
            Toast.makeText(getContext(), R.string.theme_studio_unavailable, Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        mPalette.refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    // ---------------------------------------------------------------------------------------------
    // Rows

    /** Rows that aren't ichthys.nix options: hardware-dependent links and plain settings. */
    private void setupLinkedRows() {
        final Context context = getContext();
        for (String key : unavailableLockScreenKeys(context)) {
            final Preference p = findPreference(key);
            if (p != null && p.getParent() != null) {
                p.getParent().removePreference(p);
            }
        }
    }

    /** Lock screen links to hide: no fingerprint sensor. */
    private static List<String> unavailableLockScreenKeys(Context context) {
        final List<String> keys = new ArrayList<>();
        if (!DeviceUtils.hasFingerprint(context)) {
            keys.add(KEY_FP_EFFECTS);
        }
        return keys;
    }

    /**
     * The look part of "reset all": the self-persisting rows of this page and of Accent colour.
     * The ichthys.nix rows (theme_studio_*) are left alone; they are rolled back with
     * generations.
     */
    public static void reset(Context context) {
        final android.content.ContentResolver resolver = context.getContentResolver();
        Settings.System.putIntForUser(resolver, KEY_RECENTS_TYPE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver, "ichthys_screen_reveal_style", 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_pitch_black", 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, "custom_volume_horizontal", 0,
                UserHandle.USER_CURRENT);
    }

    private void setupPresets() {
        final ListPreference pref = findPreference(KEY_PRESET);
        final List<CharSequence> entries = new ArrayList<>();
        final List<CharSequence> values = new ArrayList<>();
        entries.add(getString(R.string.theme_studio_preset_none));
        values.add("none");
        if (mService != null) {
            try {
                for (String name : mService.listPresets()) {
                    entries.add(name.substring(0, 1).toUpperCase() + name.substring(1));
                    values.add(name);
                }
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "listPresets", e);
            }
        }
        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));
    }

    private void setupFonts() {
        final ListPreference pref = findPreference(KEY_FONT);
        final List<CharSequence> entries = new ArrayList<>();
        final List<CharSequence> values = new ArrayList<>();
        entries.add(getString(R.string.theme_studio_font_default));
        values.add(NULL);
        final Context ctx = getContext();
        final OverlayManager om = ctx.getSystemService(OverlayManager.class);
        if (om != null) {
            final PackageManager pm = ctx.getPackageManager();
            // IchthysOS: one row per font overlay package (packages/overlays/Font*Overlay), by
            // label, so the list reads A-Z whatever order OverlayManager returns.
            final Map<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try {
                for (OverlayInfo oi : om.getOverlayInfosForTarget("android",
                        UserHandle.of(UserHandle.myUserId()))) {
                    if (!FONT_CATEGORY.equals(oi.getCategory())) {
                        continue;
                    }
                    String label;
                    try {
                        label = pm.getApplicationLabel(pm.getApplicationInfo(
                                oi.getPackageName(), 0)).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        label = oi.getPackageName();
                    }
                    if (byLabel.containsKey(label)) {
                        label = label + " (" + oi.getPackageName() + ")";
                    }
                    byLabel.put(label, oi.getPackageName());
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "font overlays", e);
            }
            for (Map.Entry<String, String> e : byLabel.entrySet()) {
                entries.add(e.getKey());
                values.add(e.getValue());
            }
        }
        pref.setEntries(entries.toArray(new CharSequence[0]));
        pref.setEntryValues(values.toArray(new CharSequence[0]));
    }

    /** Reflects the live config in the rows (background read, UI update on main). */
    private void refresh() {
        if (mService == null) {
            return;
        }
        mExecutor.execute(() -> {
            final Map<String, Object> live;
            final Map<String, Object> changed;
            try {
                final OptionSchema schema = OptionSchema.create();
                live = schema.evaluate(NixParser.parse(
                        mService.exportConfig(true, UserHandle.USER_CURRENT)));
                // IchthysOS: the full export fills in defaults, so an unset radius came back as
                // "22" and the Default row could never be shown. Rows with a Default entry read
                // only what differs from the defaults and presets.
                changed = schema.evaluate(NixParser.parse(
                        mService.exportConfig(false, UserHandle.USER_CURRENT)));
            } catch (Exception e) {
                Log.w(TAG, "refresh", e);
                return;
            }
            mMain.post(() -> {
                if (!isAdded()) {
                    return;
                }
                setValue(KEY_PRESET, live.get("theme.preset"), "koi");
                setValue(KEY_SURFACE, live.get("theme.surface"), "hairline");
                setValue(KEY_STYLE, live.get("theme.style"), "tonalSpot");
                setValue(KEY_ICON_STYLE, live.get("theme.iconStyle"), "mono");
                final androidx.preference.TwoStatePreference badges = findPreference(KEY_BADGES);
                if (badges != null) {
                    badges.setChecked(!Boolean.FALSE.equals(live.get("theme.iconBadges")));
                }
                setValue(KEY_RADIUS, changed.get("theme.shape.radius"), NULL);
                setValue(KEY_SHAPE, live.get("theme.shape.style"), "pill");
                setValue(KEY_FONT, live.get("theme.font.body"), NULL);

            });
        });
    }

    private void setValue(String key, Object value, String fallback) {
        final Preference found = findPreference(key);
        if (!(found instanceof ListPreference)) {
            return;
        }
        final ListPreference p = (ListPreference) found;
        if (p == null) {
            return;
        }
        final String v = value == null ? fallback : String.valueOf(value);
        if (p.findIndexOfValue(v) >= 0) {
            p.setValue(v);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String v = String.valueOf(newValue);
        final String path;
        final Object value;
        switch (preference.getKey()) {
            case KEY_PRESET:
                path = "theme.preset";
                value = v;
                break;
            case KEY_RADIUS:
                path = "theme.shape.radius";
                value = NULL.equals(v) ? null : (Object) Long.valueOf(v);
                break;
            case KEY_SHAPE:
                path = "theme.shape.style";
                value = v;
                break;
            case KEY_FONT:
                path = "theme.font.body";
                value = NULL.equals(v) ? null : v;
                break;
            case KEY_STATUSBAR:
                path = "statusBar.layout";
                value = v;
                break;
            case KEY_QS:
                path = "quickSettings.style";
                value = v;
                break;
            case KEY_SURFACE:
                path = "theme.surface";
                value = v;
                break;
            case KEY_STYLE:
                path = "theme.style";
                value = v;
                break;
            case KEY_ICON_STYLE:
                path = "theme.iconStyle";
                value = v;
                break;
            case KEY_BADGES:
                path = "theme.iconBadges";
                value = (Boolean) newValue;
                break;
            default:
                return false;
        }
        final String config = "{ " + path + " = " + NixPrinter.value(value) + "; }";
        // Only the options that rebuild the token overlays (preset, surfaces, shape, font) need
        // SystemUI to start over; icon style and badges are Settings' own.
        final boolean restart = !KEY_BADGES.equals(preference.getKey())
                && !KEY_ICON_STYLE.equals(preference.getKey())
                && !KEY_STYLE.equals(preference.getKey());
        applyAsync(config, true, "Theme Studio: " + path, restart);
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Apply / import / export / generations

    private void applyAsync(String config, boolean merge, String source, boolean restartHint) {
        final Context ctx = getContext().getApplicationContext();
        mExecutor.execute(() -> {
            String message;
            boolean applied = false;
            try {
                // IchthysOS: picking the value that is already set changes nothing; say so
                // instead of recording an apply and offering a SystemUI restart.
                final String diff = mService.diff(config, merge, UserHandle.USER_CURRENT);
                if (diff.startsWith("no changes")) {
                    message = ctx.getString(R.string.theme_studio_no_changes);
                } else {
                    final int gen =
                            mService.apply(config, merge, source, UserHandle.USER_CURRENT);
                    message = ctx.getString(R.string.theme_studio_applied, gen);
                    applied = true;
                }
            } catch (RemoteException | RuntimeException e) {
                message = ctx.getString(R.string.theme_studio_error, NixConfigActions.reason(e));
            }
            final String toast = message;
            final boolean restart = restartHint && applied;
            mMain.post(() -> {
                Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show();
                if (isAdded()) {
                    refresh();
                    if (restart) {
                        SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
                    }
                }
            });
        });
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.theme_studio) {
                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = super.getNonIndexableKeys(context);
                    keys.addAll(unavailableLockScreenKeys(context));
                    return keys;
                }
            };
}
