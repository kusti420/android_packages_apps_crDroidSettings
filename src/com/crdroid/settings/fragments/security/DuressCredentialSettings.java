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
package com.crdroid.settings.fragments.security;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.password.ChooseLockSettingsHelper;

/**
 * IchthysOS: "Emergency wipe code" (duress credential).
 *
 * <p>Lets the owner configure a second lockscreen credential which, when entered at a credential
 * prompt instead of the real one, destroys the encryption keys for all user data on the device and
 * reboots into recovery.  This screen only collects the value; every rule about what is acceptable
 * is enforced in {@code LockSettingsService} / {@code DuressCredentialManager}, which is also the
 * only place the value is ever hashed and stored.
 *
 * <p>The typed value is held in a {@link LockscreenCredential}, which is zeroized as soon as it has
 * been handed to the system server.  It is never written to a preference, a setting, or the log.
 */
public class DuressCredentialSettings extends SettingsPreferenceFragment {

    private static final String KEY_SET = "duress_set";
    private static final String KEY_REMOVE = "duress_remove";

    private static final int REQUEST_CONFIRM_FOR_SET = 1001;
    private static final int REQUEST_CONFIRM_FOR_REMOVE = 1002;

    private LockPatternUtils mLockPatternUtils;
    private int mUserId;

    private Preference mSetPref;
    private Preference mRemovePref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.ichthys_duress_settings);

        final Context context = getContext();
        mLockPatternUtils = new LockPatternUtils(context);
        mUserId = Utils.getCredentialOwnerUserId(context);

        mSetPref = findPreference(KEY_SET);
        mRemovePref = findPreference(KEY_REMOVE);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final int credentialType = mLockPatternUtils.getCredentialTypeForUser(mUserId);
        final boolean hasRealCredential =
                credentialType != LockPatternUtils.CREDENTIAL_TYPE_NONE;
        final boolean armed = hasRealCredential && mLockPatternUtils.hasDuressCredential(mUserId);
        // IchthysOS: a pattern lock can't take a code; say so here, not after confirming it.
        final boolean pattern = credentialType == LockPatternUtils.CREDENTIAL_TYPE_PATTERN;

        if (mSetPref != null) {
            mSetPref.setEnabled(hasRealCredential && !pattern);
            mSetPref.setSummary(!hasRealCredential
                    ? R.string.duress_requires_lockscreen
                    : pattern ? R.string.duress_unsupported_type
                    : (armed ? R.string.duress_status_set : R.string.duress_status_not_set));
        }
        if (mRemovePref != null) {
            mRemovePref.setVisible(armed);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (KEY_SET.equals(key)) {
            confirmRealCredential(REQUEST_CONFIRM_FOR_SET);
            return true;
        }
        if (KEY_REMOVE.equals(key)) {
            confirmRealCredential(REQUEST_CONFIRM_FOR_REMOVE);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    /**
     * Always require the real lockscreen credential before touching the emergency code, and ask
     * for it back so the system server can reject an emergency code equal to it.
     */
    private void confirmRealCredential(int requestCode) {
        final ChooseLockSettingsHelper.Builder builder =
                new ChooseLockSettingsHelper.Builder(getActivity(), this);
        builder.setRequestCode(requestCode)
                .setTitle(getString(R.string.duress_credential_title))
                .setReturnCredentials(true)
                .setUserId(mUserId)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CONFIRM_FOR_SET && requestCode != REQUEST_CONFIRM_FOR_REMOVE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        final LockscreenCredential real =
                data.getParcelableExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD,
                        LockscreenCredential.class);
        if (real == null || real.isNone()) {
            return;
        }
        if (requestCode == REQUEST_CONFIRM_FOR_REMOVE) {
            submit(LockscreenCredential.createNone(), real, /* removing= */ true);
        } else {
            showEntryDialog(real);
        }
    }

    /** Two-field "enter + confirm" dialog, using the same input type as the real credential. */
    private void showEntryDialog(LockscreenCredential real) {
        final Context context = getContext();
        final boolean isPin = real.isPin();
        if (!isPin && !real.isPassword()) {
            // Pattern (or anything else) is not supported: there is no sane way to type a second
            // pattern here, and the keyguard would not distinguish them usefully.
            new AlertDialog.Builder(context)
                    .setTitle(R.string.duress_credential_title)
                    .setMessage(R.string.duress_unsupported_type)
                    .setPositiveButton(android.R.string.ok, (d, w) -> real.zeroize())
                    .setOnCancelListener(d -> real.zeroize())
                    .show();
            return;
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        final LinearLayout root = (LinearLayout) inflater.inflate(
                R.layout.ichthys_duress_entry_dialog, null);
        final EditText first = root.findViewById(R.id.duress_entry);
        final EditText second = root.findViewById(R.id.duress_entry_confirm);
        final TextView error = root.findViewById(R.id.duress_error);

        final int inputType = isPin
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        first.setInputType(inputType);
        second.setInputType(inputType);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.duress_set_title)
                .setMessage(R.string.duress_set_dialog_message)
                .setView(root)
                .setPositiveButton(R.string.duress_set_confirm, null)
                .setNegativeButton(android.R.string.cancel, (d, w) -> real.zeroize())
                .setOnCancelListener(d -> real.zeroize())
                .create();

        dialog.setOnShowListener(d -> {
            final View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            final TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    final CharSequence a = first.getText();
                    final CharSequence b = second.getText();
                    final boolean longEnough =
                            a.length() >= LockPatternUtils.MIN_DURESS_CREDENTIAL_LENGTH;
                    final boolean match = TextUtils.equals(a, b);
                    positive.setEnabled(longEnough && match && b.length() > 0);
                    if (!longEnough && a.length() > 0) {
                        error.setText(getString(R.string.duress_error_too_short,
                                LockPatternUtils.MIN_DURESS_CREDENTIAL_LENGTH));
                        error.setVisibility(View.VISIBLE);
                    } else if (b.length() > 0 && !match) {
                        error.setText(R.string.duress_error_mismatch);
                        error.setVisibility(View.VISIBLE);
                    } else {
                        error.setVisibility(View.GONE);
                    }
                }
            };
            first.addTextChangedListener(watcher);
            second.addTextChangedListener(watcher);

            positive.setOnClickListener(v -> {
                final LockscreenCredential duress = isPin
                        ? LockscreenCredential.createPin(first.getText())
                        : LockscreenCredential.createPassword(first.getText());
                first.setText("");
                second.setText("");
                dialog.dismiss();
                confirmIrreversible(duress, real);
            });
        });
        dialog.show();
    }

    /** Final "this destroys everything" confirmation before anything is stored. */
    private void confirmIrreversible(LockscreenCredential duress, LockscreenCredential real) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.duress_final_warning_title)
                .setMessage(R.string.duress_final_warning_message)
                .setPositiveButton(R.string.duress_final_warning_confirm,
                        (d, w) -> submit(duress, real, /* removing= */ false))
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    duress.zeroize();
                    real.zeroize();
                })
                .setOnCancelListener(d -> {
                    duress.zeroize();
                    real.zeroize();
                })
                .show();
    }

    private void submit(LockscreenCredential duress, LockscreenCredential real, boolean removing) {
        // LockPatternUtils.setDuressCredential() must not run on the main thread.
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... unused) {
                try {
                    return mLockPatternUtils.setDuressCredential(duress, real, mUserId);
                } finally {
                    duress.zeroize();
                    real.zeroize();
                }
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (!isAdded()) {
                    return;
                }
                final int msg;
                switch (result) {
                    case LockPatternUtils.SET_DURESS_OK:
                        msg = removing ? R.string.duress_removed : R.string.duress_saved;
                        break;
                    case LockPatternUtils.SET_DURESS_SAME_AS_REAL:
                        msg = R.string.duress_error_same_as_real;
                        break;
                    case LockPatternUtils.SET_DURESS_TOO_SHORT:
                        msg = R.string.duress_error_too_short_generic;
                        break;
                    case LockPatternUtils.SET_DURESS_WRONG_TYPE:
                        msg = R.string.duress_error_wrong_type;
                        break;
                    case LockPatternUtils.SET_DURESS_AUTOCONFIRM_LENGTH_MISMATCH:
                        msg = R.string.duress_error_autoconfirm_length;
                        break;
                    case LockPatternUtils.SET_DURESS_WRONG_CREDENTIAL:
                    default:
                        msg = R.string.duress_error_generic;
                        break;
                }
                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                refresh();
            }
        }.execute();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }
}
