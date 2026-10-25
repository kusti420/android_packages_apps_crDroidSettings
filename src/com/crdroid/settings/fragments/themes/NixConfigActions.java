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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.text.format.DateFormat;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.android.internal.ichthys.IIchthysConfig;
import com.android.settings.R;
import com.crdroid.settings.utils.SystemUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IchthysOS (tracker#59): the whole-config rows of ichthys.nix (Import, Export, Generations) for
 * the page that hosts them (System). Import shows what would change and applies it whole or
 * merged; Export writes the live config; Generations lists them and rolls back.
 *
 * The host forwards {@link #onActivityResult} for the document pickers.
 */
public final class NixConfigActions {
    public static final String KEY_IMPORT = "theme_studio_import";
    public static final String KEY_EXPORT = "theme_studio_export";
    public static final String KEY_GENERATIONS = "theme_studio_generations";

    private static final int REQUEST_IMPORT = 41;
    private static final int REQUEST_EXPORT = 42;

    private final PreferenceFragmentCompat mFragment;
    private final IIchthysConfig mService;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public NixConfigActions(PreferenceFragmentCompat fragment) {
        mFragment = fragment;
        mService = IIchthysConfig.Stub.asInterface(ServiceManager.getService("ichthys_config"));
    }

    /** Wires the three rows; they are disabled without the config service. */
    public void setup() {
        final Preference imp = mFragment.findPreference(KEY_IMPORT);
        final Preference exp = mFragment.findPreference(KEY_EXPORT);
        final Preference gens = mFragment.findPreference(KEY_GENERATIONS);
        if (imp == null || exp == null || gens == null) {
            return;
        }
        if (mService == null) {
            imp.setEnabled(false);
            exp.setEnabled(false);
            gens.setEnabled(false);
            return;
        }
        imp.setOnPreferenceClickListener(p -> {
            mFragment.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), REQUEST_IMPORT);
            return true;
        });
        exp.setOnPreferenceClickListener(p -> {
            mFragment.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TITLE, "ichthys.nix"), REQUEST_EXPORT);
            return true;
        });
        gens.setOnPreferenceClickListener(p -> {
            showGenerations();
            return true;
        });
        refreshSummary();
    }

    public void shutdown() {
        mExecutor.shutdown();
    }

    /** Returns true when it handled the result. */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT && requestCode != REQUEST_EXPORT) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null
                || mService == null) {
            return true;
        }
        final Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            importFrom(uri);
        } else {
            exportTo(uri);
        }
        return true;
    }

    private Context ctx() {
        return mFragment.getContext();
    }

    private void refreshSummary() {
        mExecutor.execute(() -> {
            final String summary;
            try {
                final String[] gens = mService.listGenerations(UserHandle.USER_CURRENT);
                if (gens.length == 0) {
                    return;
                }
                summary = mFragment.getString(R.string.theme_studio_generations_current,
                        Integer.parseInt(gens[0].split("\t")[0]));
            } catch (Exception e) {
                return;
            }
            mMain.post(() -> {
                final Preference p = mFragment.findPreference(KEY_GENERATIONS);
                if (mFragment.isAdded() && p != null) {
                    p.setSummary(summary);
                }
            });
        });
    }

    private void applyAsync(String config, boolean merge, String source) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            String message;
            try {
                final int gen = mService.apply(config, merge, source, UserHandle.USER_CURRENT);
                message = app.getString(R.string.theme_studio_applied, gen);
            } catch (RemoteException | RuntimeException e) {
                message = app.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = message;
            mMain.post(() -> {
                Toast.makeText(app, toast, Toast.LENGTH_LONG).show();
                if (mFragment.isAdded()) {
                    refreshSummary();
                    SystemUtils.INSTANCE.showSystemUiRestartDialog(ctx());
                }
            });
        });
    }

    private void importFrom(Uri uri) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            final String text;
            final String replaceDiff;
            final String mergeDiff;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
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
                final String msg = app.getString(R.string.theme_studio_error, e.getMessage());
                mMain.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
                return;
            }
            mMain.post(() -> {
                if (!mFragment.isAdded()) {
                    return;
                }
                new AlertDialog.Builder(ctx())
                        .setTitle(R.string.theme_studio_review_title)
                        .setMessage(mFragment.getString(R.string.theme_studio_review_hint)
                                + "\n\n" + replaceDiff)
                        .setPositiveButton(R.string.theme_studio_apply, (d, w) ->
                                applyAsync(text, false, "import"))
                        .setNeutralButton(R.string.theme_studio_merge, (d, w) -> {
                            if (mergeDiff.startsWith("no changes")) {
                                Toast.makeText(app, R.string.theme_studio_no_changes,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                applyAsync(text, true, "import (merge)");
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void exportTo(Uri uri) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            String msg;
            try (OutputStream out = app.getContentResolver().openOutputStream(uri, "wt")) {
                out.write(mService.exportConfig(false, UserHandle.USER_CURRENT)
                        .getBytes(StandardCharsets.UTF_8));
                msg = app.getString(R.string.theme_studio_exported);
            } catch (Exception e) {
                msg = app.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = msg;
            mMain.post(() -> Toast.makeText(app, toast, Toast.LENGTH_LONG).show());
        });
    }

    private void showGenerations() {
        final Context ctx = ctx();
        final String[] gens;
        try {
            gens = mService.listGenerations(UserHandle.USER_CURRENT);
        } catch (RemoteException | RuntimeException e) {
            Toast.makeText(ctx, mFragment.getString(R.string.theme_studio_error, e.getMessage()),
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
            final Date date = new Date(Long.parseLong(f[1]));
            final String when = DateFormat.getMediumDateFormat(ctx).format(date) + " "
                    + DateFormat.getTimeFormat(ctx).format(date);
            labels.add(("1".equals(f[4]) ? "● " : "") + n + " · " + when + "\n" + f[2]);
            numbers.add(n);
        }
        if (labels.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.theme_studio_generations_title)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    if (which == 0) {
                        return; // already current
                    }
                    final int gen = numbers.get(which);
                    new AlertDialog.Builder(ctx)
                            .setTitle(mFragment.getString(R.string.theme_studio_rollback_title,
                                    gen))
                            .setPositiveButton(R.string.theme_studio_rollback, (d2, w2) ->
                                    rollbackAsync(gen))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    private void rollbackAsync(int gen) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            String msg;
            try {
                final int now = mService.rollback(gen, UserHandle.USER_CURRENT);
                msg = app.getString(R.string.theme_studio_applied, now);
            } catch (RemoteException | RuntimeException e) {
                msg = app.getString(R.string.theme_studio_error, e.getMessage());
            }
            final String toast = msg;
            mMain.post(() -> {
                Toast.makeText(app, toast, Toast.LENGTH_LONG).show();
                if (mFragment.isAdded()) {
                    refreshSummary();
                    SystemUtils.INSTANCE.showSystemUiRestartDialog(ctx());
                }
            });
        });
    }
}
