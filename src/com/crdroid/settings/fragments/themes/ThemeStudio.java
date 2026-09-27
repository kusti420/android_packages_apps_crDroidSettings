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

import com.crdroid.settings.utils.SystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IchthysOS Theme Studio: the Settings front end of the declarative OS config (ichthys.nix,
 * IchthysConfigService). Rows apply one-option configs in merge mode, so each change is a
 * generation that can be rolled back; Import/Export move whole configs as files.
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
    private static final String KEY_BADGES = "theme_studio_badges";
    private static final String KEY_ICON_STYLE = "theme_studio_icon_style";
    private static final String KEY_IMPORT = "theme_studio_import";
    private static final String KEY_EXPORT = "theme_studio_export";
    private static final String KEY_GENERATIONS = "theme_studio_generations";

    private static final String FONT_CATEGORY = "android.theme.customization.font";
    private static final String NULL = "null";

    private static final int REQUEST_IMPORT = 1;
    private static final int REQUEST_EXPORT = 2;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private IIchthysConfig mService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme_studio);
        mService = IIchthysConfig.Stub.asInterface(ServiceManager.getService("ichthys_config"));

        for (String key : new String[] {KEY_PRESET, KEY_RADIUS, KEY_SHAPE, KEY_FONT,
                KEY_STATUSBAR, KEY_QS, KEY_SURFACE, KEY_BADGES, KEY_ICON_STYLE}) {
            final Preference p = findPreference(key);
            if (p != null) {
                p.setOnPreferenceChangeListener(this);
                p.setEnabled(mService != null);
            }
        }
        setupPresets();
        setupFonts();
        findPreference(KEY_IMPORT).setOnPreferenceClickListener(p -> {
            final Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*");
            startActivityForResult(i, REQUEST_IMPORT);
            return true;
        });
        findPreference(KEY_EXPORT).setOnPreferenceClickListener(p -> {
            final Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, "ichthys.nix");
            startActivityForResult(i, REQUEST_EXPORT);
            return true;
        });
        findPreference(KEY_GENERATIONS).setOnPreferenceClickListener(p -> {
            showGenerations();
            return true;
        });
        if (mService == null) {
            findPreference(KEY_IMPORT).setEnabled(false);
            findPreference(KEY_EXPORT).setEnabled(false);
            findPreference(KEY_GENERATIONS).setEnabled(false);
            Toast.makeText(getContext(), R.string.theme_studio_unavailable, Toast.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    // ---------------------------------------------------------------------------------------------
    // Rows

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
            try {
                for (OverlayInfo oi : om.getOverlayInfosForTarget("android",
                        UserHandle.of(UserHandle.myUserId()))) {
                    if (!FONT_CATEGORY.equals(oi.getCategory())) {
                        continue;
                    }
                    CharSequence label;
                    try {
                        label = pm.getApplicationLabel(pm.getApplicationInfo(
                                oi.getPackageName(), 0));
                    } catch (PackageManager.NameNotFoundException e) {
                        label = oi.getPackageName();
                    }
                    entries.add(label);
                    values.add(oi.getPackageName());
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "font overlays", e);
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
            final String genSummary;
            try {
                live = OptionSchema.create().evaluate(NixParser.parse(
                        mService.exportConfig(true, UserHandle.USER_CURRENT)));
                final String[] gens = mService.listGenerations(UserHandle.USER_CURRENT);
                genSummary = gens.length == 0 ? null
                        : getString(R.string.theme_studio_generations_current,
                                Integer.parseInt(gens[0].split("\t")[0]));
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
                setValue(KEY_ICON_STYLE, live.get("theme.iconStyle"), "mono");
                final androidx.preference.TwoStatePreference badges = findPreference(KEY_BADGES);
                if (badges != null) {
                    badges.setChecked(!Boolean.FALSE.equals(live.get("theme.iconBadges")));
                }
                setValue(KEY_RADIUS, live.get("theme.shape.radius"), NULL);
                setValue(KEY_SHAPE, live.get("theme.shape.style"), "rounded");
                setValue(KEY_FONT, live.get("theme.font.body"), NULL);
                setValue(KEY_STATUSBAR, live.get("statusBar.layout"), "single");
                setValue(KEY_QS, live.get("quickSettings.style"), "grid");
                if (genSummary != null) {
                    findPreference(KEY_GENERATIONS).setSummary(genSummary);
                }
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
        applyAsync(config, true, "Theme Studio: " + path, true);
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Apply / import / export / generations

    private void applyAsync(String config, boolean merge, String source, boolean restartHint) {
        final Context ctx = getContext().getApplicationContext();
        mExecutor.execute(() -> {
            String message;
            try {
                final int gen = mService.apply(config, merge, source, UserHandle.USER_CURRENT);
                message = ctx.getString(R.string.theme_studio_applied, gen);
            } catch (RemoteException | RuntimeException e) {
                message = ctx.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = message;
            mMain.post(() -> {
                Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show();
                if (isAdded()) {
                    refresh();
                    if (restartHint) {
                        SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
                    }
                }
            });
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null
                || mService == null) {
            return;
        }
        final Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            importFrom(uri);
        } else if (requestCode == REQUEST_EXPORT) {
            exportTo(uri);
        }
    }

    private void importFrom(Uri uri) {
        final Context ctx = getContext().getApplicationContext();
        mExecutor.execute(() -> {
            final String text;
            final String replaceDiff;
            final String mergeDiff;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 1024 * 1024) {
                        throw new IllegalArgumentException("file larger than 1 MiB");
                    }
                }
                text = out.toString(StandardCharsets.UTF_8.name());
                replaceDiff = mService.diff(text, false, UserHandle.USER_CURRENT);
                mergeDiff = mService.diff(text, true, UserHandle.USER_CURRENT);
            } catch (Exception e) {
                final String msg = ctx.getString(R.string.theme_studio_error, e.getMessage());
                mMain.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
                return;
            }
            mMain.post(() -> {
                if (!isAdded()) {
                    return;
                }
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.theme_studio_review_title)
                        .setMessage(getString(R.string.theme_studio_review_hint)
                                + "\n\n" + replaceDiff)
                        .setPositiveButton(R.string.theme_studio_apply, (d, w) ->
                                applyAsync(text, false, "import", true))
                        .setNeutralButton(R.string.theme_studio_merge, (d, w) -> {
                            if (mergeDiff.startsWith("no changes")) {
                                Toast.makeText(ctx, R.string.theme_studio_no_changes,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                applyAsync(text, true, "import (merge)", true);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void exportTo(Uri uri) {
        final Context ctx = getContext().getApplicationContext();
        mExecutor.execute(() -> {
            String msg;
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt")) {
                out.write(mService.exportConfig(false, UserHandle.USER_CURRENT)
                        .getBytes(StandardCharsets.UTF_8));
                msg = ctx.getString(R.string.theme_studio_exported);
            } catch (Exception e) {
                msg = ctx.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = msg;
            mMain.post(() -> Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show());
        });
    }

    private void showGenerations() {
        final Context ctx = getContext();
        final String[] gens;
        try {
            gens = mService.listGenerations(UserHandle.USER_CURRENT);
        } catch (RemoteException | RuntimeException e) {
            Toast.makeText(ctx, getString(R.string.theme_studio_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> labels = new ArrayList<>();
        final List<Integer> numbers = new ArrayList<>();
        for (String line : gens) {
            final String[] f = line.split("\t", -1);
            if (f.length < 5) {
                continue;
            }
            final int n = Integer.parseInt(f[0]);
            final String when = DateFormat.getMediumDateFormat(ctx).format(
                    new Date(Long.parseLong(f[1]))) + " "
                    + DateFormat.getTimeFormat(ctx).format(new Date(Long.parseLong(f[1])));
            labels.add(("1".equals(f[4]) ? "● " : "") + n + " · " + when + "\n" + f[2]);
            numbers.add(n);
        }
        if (labels.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.theme_studio_generations_title)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    final int gen = numbers.get(which);
                    if (which == 0) {
                        return; // already current
                    }
                    new AlertDialog.Builder(ctx)
                            .setTitle(getString(R.string.theme_studio_rollback_title, gen))
                            .setPositiveButton(R.string.theme_studio_rollback, (d2, w2) ->
                                    rollbackAsync(gen))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    private void rollbackAsync(int gen) {
        final Context ctx = getContext().getApplicationContext();
        mExecutor.execute(() -> {
            String msg;
            try {
                final int now = mService.rollback(gen, UserHandle.USER_CURRENT);
                msg = ctx.getString(R.string.theme_studio_applied, now);
            } catch (RemoteException | RuntimeException e) {
                msg = ctx.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = msg;
            mMain.post(() -> {
                Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show();
                if (isAdded()) {
                    refresh();
                    SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
                }
            });
        });
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.theme_studio);
}
