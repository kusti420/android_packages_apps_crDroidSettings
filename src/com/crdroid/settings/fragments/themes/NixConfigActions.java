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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.android.internal.ichthys.IIchthysConfig;
import com.android.settings.R;
import com.android.settings.ichthys.ShoalStyle;
import com.crdroid.settings.utils.SystemUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                // IchthysOS: a current generation that is a rollback says what it restored.
                final Generation current = Generation.parse(gens[0]);
                summary = current.rollbackTo >= 0
                        ? mFragment.getString(R.string.theme_studio_generations_current_rollback,
                                current.number, current.rollbackTo)
                        : mFragment.getString(R.string.theme_studio_generations_current,
                                current.number);
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

    /**
     * IchthysOS: the text of a failure, never "null" (an exception without a message names its
     * type instead).
     */
    static String reason(Throwable e) {
        final String m = e.getMessage();
        return m != null && !m.isEmpty() ? m : e.getClass().getSimpleName();
    }

    private void applyAsync(String config, boolean merge, String source) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            String message;
            boolean applied = false;
            try {
                final int gen = mService.apply(config, merge, source, UserHandle.USER_CURRENT);
                message = app.getString(R.string.theme_studio_applied, gen);
                applied = true;
            } catch (RemoteException | RuntimeException e) {
                message = app.getString(R.string.theme_studio_error, reason(e));
            }
            final String toast = message;
            final boolean restart = applied;
            mMain.post(() -> {
                Toast.makeText(app, toast, Toast.LENGTH_LONG).show();
                if (mFragment.isAdded()) {
                    refreshSummary();
                    // IchthysOS: a restart only helps after something was applied. Callers rule
                    // out a no-op apply (the review diff says "no changes").
                    if (restart) {
                        SystemUtils.INSTANCE.showSystemUiRestartDialog(ctx());
                    }
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
                // IchthysOS: reading or parsing the file failed; nothing was applied.
                final String msg = app.getString(R.string.theme_studio_read_error, reason(e));
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
                        .setPositiveButton(R.string.theme_studio_apply, (d, w) -> {
                            // IchthysOS: like Merge, a Replace that changes nothing is no apply.
                            if (replaceDiff.startsWith("no changes")) {
                                Toast.makeText(app, R.string.theme_studio_no_changes,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                applyAsync(text, false, "import");
                            }
                        })
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
                msg = app.getString(R.string.theme_studio_export_error, reason(e));
            }
            final String toast = msg;
            mMain.post(() -> Toast.makeText(app, toast, Toast.LENGTH_LONG).show());
        });
    }

    /** One line of {@link IIchthysConfig#listGenerations}. */
    private static final class Generation {
        final int number;
        final long time;
        final String summary;
        final String source;
        final boolean current;
        /** The generation this one restored (a rollback), else -1. */
        final int rollbackTo;

        private Generation(String[] f) {
            number = Integer.parseInt(f[0]);
            time = Long.parseLong(f[1]);
            summary = f[2];
            source = f[3];
            current = "1".equals(f[4]);
            rollbackTo = f.length > 5 && !f[5].isEmpty() ? Integer.parseInt(f[5]) : -1;
        }

        /** @return null for a line that isn't one */
        static Generation parse(String line) {
            final String[] f = line.split("\t", -1);
            if (f.length < 5) {
                return null;
            }
            try {
                return new Generation(f);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private String when(long time) {
        final Date date = new Date(time);
        return DateFormat.getMediumDateFormat(ctx()).format(date) + " "
                + DateFormat.getTimeFormat(ctx()).format(date);
    }

    /**
     * IchthysOS: the generations list, newest first, each with what it changed and who applied
     * it. A rollback reads "Rolled back to #N (date)", and generation N itself carries a
     * "Restored by #M" badge; both, like the current one, are in the accent so the eye finds
     * where the history went back to.
     */
    private void showGenerations() {
        final Context ctx = ctx();
        final String[] lines;
        try {
            lines = mService.listGenerations(UserHandle.USER_CURRENT);
        } catch (RemoteException | RuntimeException e) {
            Toast.makeText(ctx, mFragment.getString(R.string.theme_studio_error, reason(e)),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final List<Generation> gens = new ArrayList<>();
        final Map<Integer, Generation> byNumber = new HashMap<>();
        // Which rollback (the newest, if several) restored a generation.
        final Map<Integer, Integer> restoredBy = new HashMap<>();
        for (String line : lines) {
            final Generation g = Generation.parse(line);
            if (g == null) {
                continue;
            }
            gens.add(g);
            byNumber.put(g.number, g);
            if (g.rollbackTo >= 0 && !restoredBy.containsKey(g.rollbackTo)) {
                restoredBy.put(g.rollbackTo, g.number);
            }
        }
        if (gens.isEmpty()) {
            return;
        }
        final int accent = ShoalStyle.accent(ctx);
        final List<CharSequence> labels = new ArrayList<>();
        for (Generation g : gens) {
            final SpannableStringBuilder sb = new SpannableStringBuilder();
            final boolean marked = g.current || g.rollbackTo >= 0
                    || restoredBy.containsKey(g.number);
            appendStyled(sb, (g.current ? "\u25CF " : "") + "#" + g.number + " \u00B7 "
                    + when(g.time), marked ? accent : 0, true);
            if (g.current) {
                sb.append("  ").append(
                        mFragment.getString(R.string.theme_studio_generation_current));
            }
            final Integer restorer = restoredBy.get(g.number);
            if (restorer != null) {
                sb.append("\n");
                appendStyled(sb, "\u25C0 " + mFragment.getString(
                        R.string.theme_studio_generation_restored_by, restorer), accent, true);
            }
            if (g.rollbackTo >= 0) {
                final Generation target = byNumber.get(g.rollbackTo);
                sb.append("\n");
                appendStyled(sb, target != null
                        ? mFragment.getString(R.string.theme_studio_generation_rolled_back_to,
                                g.rollbackTo, when(target.time))
                        : mFragment.getString(R.string.theme_studio_generation_rolled_back_to_gone,
                                g.rollbackTo), accent, false);
            }
            if (g.number == 0) {
                sb.append("\n").append(
                        mFragment.getString(R.string.theme_studio_generation_initial));
            } else {
                if (!g.summary.isEmpty()) {
                    sb.append("\n").append(mFragment.getString(
                            R.string.theme_studio_generation_changed, g.summary));
                }
                if (g.rollbackTo < 0 && !g.source.isEmpty()) {
                    sb.append("\n").append(mFragment.getString(
                            R.string.theme_studio_generation_source, g.source));
                }
            }
            labels.add(sb);
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.theme_studio_generations_title)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    final Generation g = gens.get(which);
                    if (g.current) {
                        return; // already current
                    }
                    new AlertDialog.Builder(ctx)
                            .setTitle(mFragment.getString(R.string.theme_studio_rollback_title,
                                    g.number))
                            .setMessage(mFragment.getString(R.string.theme_studio_rollback_message,
                                    when(g.time), g.number == 0
                                            ? mFragment.getString(
                                                    R.string.theme_studio_generation_initial)
                                            : g.summary))
                            .setPositiveButton(R.string.theme_studio_rollback, (d2, w2) ->
                                    rollbackAsync(g.number))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .show();
    }

    /** Appends {@code text} in {@code color} (0: unchanged), bold when asked. */
    private static void appendStyled(SpannableStringBuilder sb, String text, int color,
            boolean bold) {
        final int start = sb.length();
        sb.append(text);
        if (color != 0) {
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (bold) {
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void rollbackAsync(int gen) {
        final Context app = ctx().getApplicationContext();
        mExecutor.execute(() -> {
            String msg;
            boolean rolledBack = false;
            try {
                final int now = mService.rollback(gen, UserHandle.USER_CURRENT);
                msg = app.getString(R.string.theme_studio_applied, now);
                rolledBack = true;
            } catch (RemoteException | RuntimeException e) {
                msg = app.getString(R.string.theme_studio_error, reason(e));
            }
            final String toast = msg;
            final boolean restart = rolledBack;
            mMain.post(() -> {
                Toast.makeText(app, toast, Toast.LENGTH_LONG).show();
                if (mFragment.isAdded()) {
                    refreshSummary();
                    // IchthysOS: no restart offer after a failed rollback.
                    if (restart) {
                        SystemUtils.INSTANCE.showSystemUiRestartDialog(ctx());
                    }
                }
            });
        });
    }
}
