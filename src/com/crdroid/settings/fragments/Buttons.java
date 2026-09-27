/*
 * Copyright (C) 2016-2024 crDroid Android Project
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

package com.crdroid.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.buttons.ButtonBacklightBrightness;
import com.crdroid.settings.fragments.buttons.PowerMenuActions;
import com.crdroid.settings.preferences.SystemSettingSeekBarPreference;
import com.crdroid.settings.utils.DeviceUtils;
import com.crdroid.settings.utils.TelephonyUtils;

import static org.lineageos.internal.util.DeviceKeysConstants.*;

import java.util.List;
import java.util.Set;

import lineageos.hardware.LineageHardwareManager;
import lineageos.providers.LineageSettings;

@SearchIndexable
public class Buttons extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "Buttons";

    private static final String HWKEYS_DISABLED = "hardware_keys_disable";
    private static final String KEY_SWAP_CAPACITIVE_KEYS = "swap_capacitive_keys";
    private static final String KEY_ANBI = "anbi_enabled";
    private static final String KEY_BUTTON_BACKLIGHT = "button_backlight";
    private static final String KEY_BACK_LONG_PRESS = "hardware_keys_back_long_press";
    private static final String KEY_BACK_WAKE_SCREEN = "back_wake_screen";
    private static final String KEY_CAMERA_LAUNCH = "camera_launch";
    private static final String KEY_CAMERA_SLEEP_ON_RELEASE = "camera_sleep_on_release";
    private static final String KEY_CAMERA_WAKE_SCREEN = "camera_wake_screen";
    private static final String KEY_HOME_LONG_PRESS = "hardware_keys_home_long_press";
    private static final String KEY_HOME_DOUBLE_TAP = "hardware_keys_home_double_tap";
    private static final String KEY_HOME_WAKE_SCREEN = "home_wake_screen";
    private static final String KEY_MENU_PRESS = "hardware_keys_menu_press";
    private static final String KEY_MENU_LONG_PRESS = "hardware_keys_menu_long_press";
    private static final String KEY_MENU_WAKE_SCREEN = "menu_wake_screen";
    private static final String KEY_ASSIST_PRESS = "hardware_keys_assist_press";
    private static final String KEY_ASSIST_LONG_PRESS = "hardware_keys_assist_long_press";
    private static final String KEY_ASSIST_WAKE_SCREEN = "assist_wake_screen";
    private static final String KEY_APP_SWITCH_PRESS = "hardware_keys_app_switch_press";
    private static final String KEY_APP_SWITCH_LONG_PRESS = "hardware_keys_app_switch_long_press";
    private static final String KEY_APP_SWITCH_DOUBLE_TAP = "hardware_keys_app_switch_double_tap";
    private static final String KEY_APP_SWITCH_WAKE_SCREEN = "app_switch_wake_screen";
    private static final String KEY_VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";
    private static final String KEY_SWAP_VOLUME_BUTTONS = "swap_volume_buttons";
    private static final String KEY_VOLUME_WAKE_SCREEN = "volume_wake_screen";
    private static final String KEY_VOLUME_ANSWER_CALL = "volume_answer_call";
    private static final String KEY_POWER_END_CALL = "power_end_call";
    private static final String KEY_HOME_ANSWER_CALL = "home_answer_call";
    private static final String KEY_VOLUME_MUSIC_CONTROLS = "volbtn_music_controls";
    private static final String KEY_TORCH_LONG_PRESS_POWER_GESTURE =
            "torch_long_press_power_gesture";
    private static final String KEY_TORCH_LONG_PRESS_POWER_TIMEOUT =
            "torch_long_press_power_timeout";
    private static final String KEY_CLICK_PARTIAL_SCREENSHOT =
            "click_partial_screenshot";
    private static final String KEY_ADDITIONAL_BUTTONS = "additional_buttons";
    private static final String KEY_POWER_MENU = "power_menu";

    private static final String CATEGORY_POWER = "power_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_BACK = "back_key";
    private static final String CATEGORY_MENU = "menu_key";
    private static final String CATEGORY_ASSIST = "assist_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_CAMERA = "camera_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_EXTRAS = "extras_category";

    private SwitchPreferenceCompat mHardwareKeysDisable;
    private SwitchPreferenceCompat mSwapCapacitiveKeys;
    private SwitchPreferenceCompat mAnbi;
    private ButtonBacklightBrightness backlight;
    private ListPreference mHomeLongPressAction;
    private ListPreference mHomeDoubleTapAction;
    private ListPreference mBackLongPressAction;
    private ListPreference mMenuPressAction;
    private ListPreference mMenuLongPressAction;
    private ListPreference mAssistPressAction;
    private ListPreference mAssistLongPressAction;
    private ListPreference mAppSwitchPressAction;
    private ListPreference mAppSwitchLongPressAction;
    private ListPreference mAppSwitchDoubleTapAction;
    private SwitchPreferenceCompat mCameraWakeScreen;
    private SwitchPreferenceCompat mCameraSleepOnRelease;
    private ListPreference mVolumeKeyCursorControl;
    private SwitchPreferenceCompat mSwapVolumeButtons;
    private SwitchPreferenceCompat mPowerEndCall;
    private SwitchPreferenceCompat mHomeAnswerCall;
    private SwitchPreferenceCompat mTorchLongPressPowerGesture;
    private SystemSettingSeekBarPreference mTorchLongPressPowerTimeout;

    private LineageHardwareManager mHardware;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHardware = LineageHardwareManager.getInstance(getActivity());

        addPreferencesFromResource(R.xml.crdroid_settings_button);

        final Resources res = getResources();
        final ContentResolver resolver = getActivity().getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final boolean hasPowerKey = DeviceUtils.hasPowerKey();
        final boolean hasHomeKey = DeviceUtils.hasHomeKey(getActivity());
        final boolean hasBackKey = DeviceUtils.hasBackKey(getActivity());
        final boolean hasMenuKey = DeviceUtils.hasMenuKey(getActivity());
        final boolean hasAssistKey = DeviceUtils.hasAssistKey(getActivity());
        final boolean hasAppSwitchKey = DeviceUtils.hasAppSwitchKey(getActivity());
        final boolean hasCameraKey = DeviceUtils.hasCameraKey(getActivity());
        final boolean hasVolumeKeys = DeviceUtils.hasVolumeKeys(getActivity());

        final boolean showHomeWake = DeviceUtils.canWakeUsingHomeKey(getActivity());
        final boolean showBackWake = DeviceUtils.canWakeUsingBackKey(getActivity());
        final boolean showMenuWake = DeviceUtils.canWakeUsingMenuKey(getActivity());
        final boolean showAssistWake = DeviceUtils.canWakeUsingAssistKey(getActivity());
        final boolean showAppSwitchWake = DeviceUtils.canWakeUsingAppSwitchKey(getActivity());
        final boolean showCameraWake = DeviceUtils.canWakeUsingCameraKey(getActivity());
        final boolean showVolumeWake = DeviceUtils.canWakeUsingVolumeKeys(getActivity());

        // IchthysOS: config_deviceHardwareKeys only describes PHYSICAL keys (it is 64, "volume
        // only", on a52sxq), which is why the home/recents action lists used to be removed here
        // and the screen looked empty. The on-screen navigation buttons are a separate thing:
        // they live in the Launcher taskbar and their long-press / double-tap actions are
        // consumed by TaskbarNavButtonController, so gate those two lists on the navigation bar
        // instead. Everything that really does need a physical key (wake-on-key, answer-call)
        // stays gated on the hardware mask below.
        final boolean hasNavButtons = hasOnScreenNavButtons(getActivity());
        final boolean showHomeActions = hasHomeKey || hasNavButtons;
        final boolean showAppSwitchActions = hasAppSwitchKey || hasNavButtons;

        final PreferenceCategory powerCategory = prefScreen.findPreference(CATEGORY_POWER);
        final PreferenceCategory homeCategory = prefScreen.findPreference(CATEGORY_HOME);
        final PreferenceCategory backCategory = prefScreen.findPreference(CATEGORY_BACK);
        final PreferenceCategory menuCategory = prefScreen.findPreference(CATEGORY_MENU);
        final PreferenceCategory assistCategory = prefScreen.findPreference(CATEGORY_ASSIST);
        final PreferenceCategory appSwitchCategory = prefScreen.findPreference(CATEGORY_APPSWITCH);
        final PreferenceCategory volumeCategory = prefScreen.findPreference(CATEGORY_VOLUME);
        final PreferenceCategory cameraCategory = prefScreen.findPreference(CATEGORY_CAMERA);
        final PreferenceCategory extrasCategory = prefScreen.findPreference(CATEGORY_EXTRAS);

        // NOTE: every findPreference() below can legitimately return null - this XML has whole
        // blocks commented out for hardware this device does not have - and
        // PreferenceGroup.removePreference(null) throws. Removals therefore all go through
        // removePref(key), which is a no-op when the preference isn't there.
        mHardwareKeysDisable = findPreference(HWKEYS_DISABLED);
        mSwapCapacitiveKeys = findPreference(KEY_SWAP_CAPACITIVE_KEYS);
        mAnbi = findPreference(KEY_ANBI);

        // Power button ends calls.
        mPowerEndCall = findPreference(KEY_POWER_END_CALL);

        // Long press power while display is off to activate torchlight
        mTorchLongPressPowerGesture = findPreference(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
        mTorchLongPressPowerTimeout = findPreference(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);

        // Home button answers calls.
        mHomeAnswerCall = findPreference(KEY_HOME_ANSWER_CALL);

        // IchthysOS: stock would default the on-screen home button's long press to the assistant
        // (VOICE_SEARCH), but this ROM has no assistant, so TaskbarNavButtonController's
        // DEFAULT_HOME_LONG_PRESS_ACTION is NOTHING. Keep the two in agreement or the list would
        // show the wrong thing. (VOICE_SEARCH is also no longer offered in
        // navbar_keys_action_entries at all.)
        Action defaultHomeLongPressAction = hasHomeKey
                ? Action.fromIntSafe(DeviceUtils.getLineageInteger(getActivity(),
                        org.lineageos.platform.internal.R.integer.config_longPressOnHomeBehavior, 3))
                : Action.NOTHING;
        Action defaultHomeDoubleTapAction = Action.fromIntSafe(DeviceUtils.getLineageInteger(
                getActivity(), org.lineageos.platform.internal.R.integer.config_doubleTapOnHomeBehavior, 0));
        Action defaultAppSwitchLongPressAction = Action.fromIntSafe(DeviceUtils.getLineageInteger(
                getActivity(), org.lineageos.platform.internal.R.integer.config_longPressOnAppSwitchBehavior, 0));
        // IchthysOS: was VOICE_SEARCH upstream; no assistant on this ROM. (Dead on a52sxq anyway
        // - there is no capacitive assist key, so the whole assist_key category is removed.)
        Action defaultAssistLongPressAction = Action.NOTHING;
        Action homeLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                defaultHomeLongPressAction);
        Action defaultBackLongPressAction = Action.fromIntSafe(DeviceUtils.getLineageInteger(
                getActivity(), org.lineageos.platform.internal.R.integer.config_longPressOnBackBehavior, 0));
        Action homeDoubleTapAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                defaultHomeDoubleTapAction);
        Action appSwitchLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                defaultAppSwitchLongPressAction);
        // IchthysOS: default NOTHING, not LAST_APP. TaskbarNavButtonController has to hold a
        // recents tap back for the double-tap timeout whenever a double-tap action is set, so a
        // non-NOTHING default would put ~300ms of latency on every recents tap out of the box.
        Action appSwitchDoubleTapAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION,
                Action.NOTHING);
        Action assistLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION,
                defaultAssistLongPressAction);
        Action backLongPressAction = Action.fromSettings(resolver,
                LineageSettings.System.KEY_BACK_LONG_PRESS_ACTION,
                defaultBackLongPressAction);

        if (isKeyDisablerSupported(getActivity()) && mHardwareKeysDisable != null) {
            mHardwareKeysDisable.setOnPreferenceChangeListener(this);
        } else {
            removePref(HWKEYS_DISABLED);
            mHardwareKeysDisable = null;
        }

        if (!isKeySwapperSupported(getActivity())) {
            removePref(KEY_SWAP_CAPACITIVE_KEYS);
            mSwapCapacitiveKeys = null;
        }

        if (mAnbi == null
                || (!hasHomeKey && !hasBackKey && !hasMenuKey && !hasAssistKey && !hasAppSwitchKey)) {
            removePref(KEY_ANBI);
            mAnbi = null;
        } else if (isKeyDisablerSupported(getActivity())) {
            mAnbi.setEnabled(!(Settings.System.getIntForUser(resolver,
                    Settings.System.HARDWARE_KEYS_DISABLE, 0,
                    UserHandle.USER_CURRENT) == 1));
        }

        if (hasPowerKey) {
            if (!TelephonyUtils.isVoiceCapable(getActivity())) {
                removePref(KEY_POWER_END_CALL);
                mPowerEndCall = null;
            }
            if (!DeviceUtils.deviceSupportsFlashLight(getActivity())) {
                removePref(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                removePref(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                mTorchLongPressPowerGesture = null;
                mTorchLongPressPowerTimeout = null;
            }
        }
        if (!hasPowerKey || isEmpty(powerCategory)) {
            removePref(CATEGORY_POWER);
        }

        // Home. The wake / answer-call toggles need a physical key; the action lists also work
        // for the on-screen home button (see hasNavButtons above).
        if (!hasHomeKey || !showHomeWake) {
            removePref(KEY_HOME_WAKE_SCREEN);
        }
        if (!hasHomeKey || !TelephonyUtils.isVoiceCapable(getActivity())) {
            removePref(KEY_HOME_ANSWER_CALL);
            mHomeAnswerCall = null;
        }
        if (showHomeActions) {
            mHomeLongPressAction = initList(KEY_HOME_LONG_PRESS, homeLongPressAction);
            mHomeDoubleTapAction = initList(KEY_HOME_DOUBLE_TAP, homeDoubleTapAction);
        } else {
            removePref(KEY_HOME_LONG_PRESS);
            removePref(KEY_HOME_DOUBLE_TAP);
        }
        if (isEmpty(homeCategory)) {
            removePref(CATEGORY_HOME);
        }

        // Back. Note that back long-press is intentionally NOT offered for the on-screen back
        // button: it uses a bespoke touch listener feeding the predictive-back animation, and
        // binding its long press races that. So this stays gated on a physical back key.
        if (hasBackKey) {
            if (!showBackWake) {
                removePref(KEY_BACK_WAKE_SCREEN);
            }

            mBackLongPressAction = initList(KEY_BACK_LONG_PRESS, backLongPressAction);
        }
        if (!hasBackKey || isEmpty(backCategory)) {
            removePref(CATEGORY_BACK);
        }

        if (hasMenuKey) {
            if (!showMenuWake) {
                removePref(KEY_MENU_WAKE_SCREEN);
            }

            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_MENU_ACTION, Action.MENU);
            mMenuPressAction = initList(KEY_MENU_PRESS, pressAction);

            Action longPressAction = Action.fromSettings(resolver,
                        LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION,
                        hasAssistKey ? Action.NOTHING : Action.APP_SWITCH);
            mMenuLongPressAction = initList(KEY_MENU_LONG_PRESS, longPressAction);
        }
        if (!hasMenuKey || isEmpty(menuCategory)) {
            removePref(CATEGORY_MENU);
        }

        if (hasAssistKey) {
            if (!showAssistWake) {
                removePref(KEY_ASSIST_WAKE_SCREEN);
            }

            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_ASSIST_ACTION, Action.SEARCH);
            mAssistPressAction = initList(KEY_ASSIST_PRESS, pressAction);

            mAssistLongPressAction = initList(KEY_ASSIST_LONG_PRESS, assistLongPressAction);
        }
        if (!hasAssistKey || isEmpty(assistCategory)) {
            removePref(CATEGORY_ASSIST);
        }

        // Recents / app switch. Same split as home: the wake toggle and the SHORT press need a
        // physical key, the long-press and double-tap lists also drive the on-screen button.
        if (!hasAppSwitchKey || !showAppSwitchWake) {
            removePref(KEY_APP_SWITCH_WAKE_SCREEN);
        }
        if (hasAppSwitchKey) {
            Action pressAction = Action.fromSettings(resolver,
                    LineageSettings.System.KEY_APP_SWITCH_ACTION, Action.APP_SWITCH);
            mAppSwitchPressAction = initList(KEY_APP_SWITCH_PRESS, pressAction);
        } else {
            removePref(KEY_APP_SWITCH_PRESS);
        }
        if (showAppSwitchActions) {
            mAppSwitchLongPressAction = initList(KEY_APP_SWITCH_LONG_PRESS, appSwitchLongPressAction);
            mAppSwitchDoubleTapAction = initList(KEY_APP_SWITCH_DOUBLE_TAP, appSwitchDoubleTapAction);
        } else {
            removePref(KEY_APP_SWITCH_LONG_PRESS);
            removePref(KEY_APP_SWITCH_DOUBLE_TAP);
        }
        if (isEmpty(appSwitchCategory)) {
            removePref(CATEGORY_APPSWITCH);
        }

        if (hasCameraKey) {
            mCameraWakeScreen = findPreference(KEY_CAMERA_WAKE_SCREEN);
            mCameraSleepOnRelease = findPreference(KEY_CAMERA_SLEEP_ON_RELEASE);

            if (!showCameraWake) {
                removePref(KEY_CAMERA_WAKE_SCREEN);
                mCameraWakeScreen = null;
            }
            // Only show 'Camera sleep on release' if the device has a focus key
            if (DeviceUtils.getLineageBoolean(getActivity(), org.lineageos.platform.internal.R.bool.config_singleStageCameraKey, false)) {
                removePref(KEY_CAMERA_SLEEP_ON_RELEASE);
                mCameraSleepOnRelease = null;
            }
        }
        if (!hasCameraKey || isEmpty(cameraCategory)) {
            removePref(CATEGORY_CAMERA);
        }

        if (hasVolumeKeys) {
            if (!showVolumeWake) {
                removePref(KEY_VOLUME_WAKE_SCREEN);
            }

            if (!TelephonyUtils.isVoiceCapable(getActivity())) {
                removePref(KEY_VOLUME_ANSWER_CALL);
            }

            int cursorControlAction = Settings.System.getInt(resolver,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0);
            mVolumeKeyCursorControl = initList(KEY_VOLUME_KEY_CURSOR_CONTROL,
                    cursorControlAction);

            int swapVolumeKeys = LineageSettings.System.getInt(getContentResolver(),
                    LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, 0);
            mSwapVolumeButtons = prefScreen.findPreference(KEY_SWAP_VOLUME_BUTTONS);
            if (mSwapVolumeButtons != null) {
                mSwapVolumeButtons.setChecked(swapVolumeKeys > 0);
            }
        } else {
            removePref(KEY_CLICK_PARTIAL_SCREENSHOT);
        }
        if (!hasVolumeKeys || isEmpty(volumeCategory)) {
            removePref(CATEGORY_VOLUME);
        }

        backlight = extrasCategory == null
                ? null : extrasCategory.findPreference(KEY_BUTTON_BACKLIGHT);
        if (backlight == null
                || (!DeviceUtils.hasButtonBacklightSupport(getActivity())
                        && !DeviceUtils.hasKeyboardBacklightSupport(getActivity()))) {
            removePref(KEY_BUTTON_BACKLIGHT);
            backlight = null;
        } else if (isKeyDisablerSupported(getActivity())) {
            backlight.setEnabled(!(Settings.System.getIntForUser(resolver,
                    Settings.System.HARDWARE_KEYS_DISABLE, 0,
                    UserHandle.USER_CURRENT) == 1));
        }

        if (mCameraWakeScreen != null) {
            if (mCameraSleepOnRelease != null && !DeviceUtils.getLineageBoolean(getActivity(),
                    org.lineageos.platform.internal.R.bool.config_singleStageCameraKey, false)) {
                mCameraSleepOnRelease.setDependency(KEY_CAMERA_WAKE_SCREEN);
            }
        }

        SwitchPreferenceCompat volumeWakeScreen = findPreference(KEY_VOLUME_WAKE_SCREEN);
        SwitchPreferenceCompat volumeMusicControls = findPreference(KEY_VOLUME_MUSIC_CONTROLS);

        if (volumeWakeScreen != null) {
            if (volumeMusicControls != null) {
                volumeMusicControls.setDependency(KEY_VOLUME_WAKE_SCREEN);
                volumeWakeScreen.setDisableDependentsState(true);
            }
        }

        if (isEmpty(extrasCategory)) {
            removePref(CATEGORY_EXTRAS);
        }
    }

    /**
     * Removes a preference (or a whole category) by key, anywhere in the tree. A no-op when the
     * key isn't present, which is the point: crDroidSettings' button XML has entire blocks
     * commented out for hardware a52sxq does not have, so findPreference() returns null for them
     * and the old {@code group.removePreference(findPreference(key))} pattern threw an NPE and
     * took the whole screen down.
     */
    private void removePref(String key) {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            screen.removePreferenceRecursively(key);
        }
    }

    /** True if the category is absent or has nothing left in it. */
    private static boolean isEmpty(PreferenceCategory category) {
        return category == null || category.getPreferenceCount() == 0;
    }

    /**
     * Whether this device has on-screen navigation buttons whose long press / double tap can be
     * bound. These are drawn by the Launcher taskbar and exist on any handheld regardless of
     * config_deviceHardwareKeys, which only describes physical keys. Kept deliberately
     * independent of the current navigation_mode: in gesture navigation there is simply nothing
     * to long press, and having the preferences vanish and reappear as the user switches modes
     * is worse than leaving them idle.
     */
    private static boolean hasOnScreenNavButtons(Context context) {
        final PackageManager pm = context.getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Power button ends calls.
        if (mPowerEndCall != null) {
            final int incallPowerBehavior = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_DEFAULT);
            final boolean powerButtonEndsCall =
                    (incallPowerBehavior == Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP);
            mPowerEndCall.setChecked(powerButtonEndsCall);
        }

        // Home button answers calls.
        if (mHomeAnswerCall != null) {
            final int incallHomeBehavior = LineageSettings.Secure.getInt(getContentResolver(),
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR,
                    LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DEFAULT);
            final boolean homeButtonAnswersCall =
                (incallHomeBehavior == LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER);
            mHomeAnswerCall.setChecked(homeButtonAnswersCall);
        }
    }

    private ListPreference initList(String key, Action value) {
        return initList(key, value.ordinal());
    }

    private ListPreference initList(String key, int value) {
        ListPreference list = getPreferenceScreen().findPreference(key);
        if (list == null) return null;
        String stringValue = Integer.toString(value);
        if (list.findIndexOfValue(stringValue) < 0) {
            // The stored action is not one this list offers. That is expected: the on-screen
            // nav button lists use navbar_keys_action_values, a subset of the full action set,
            // so a value carried over from a hardware-key config (or from an older build) can
            // land here. Show the first entry ("nothing") rather than an empty summary and a
            // dialog with nothing selected.
            final CharSequence[] entryValues = list.getEntryValues();
            if (entryValues != null && entryValues.length > 0) {
                stringValue = entryValues[0].toString();
            }
        }
        list.setValue(stringValue);
        list.setSummary(list.getEntry());
        list.setOnPreferenceChangeListener(this);
        return list;
    }

    private void handleListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        // USER_CURRENT to match Action.fromSettings(), which reads with USER_CURRENT.
        LineageSettings.System.putIntForUser(getContentResolver(), setting,
                Integer.valueOf(value), UserHandle.USER_CURRENT);
    }

    private void handleSystemListChange(ListPreference pref, Object newValue, String setting) {
        String value = (String) newValue;
        int index = pref.findIndexOfValue(value);
        pref.setSummary(pref.getEntries()[index]);
        Settings.System.putInt(getContentResolver(), setting, Integer.valueOf(value));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHardwareKeysDisable) {
            boolean value = (Boolean) newValue;
            if (mAnbi != null) {
                mAnbi.setEnabled(!value);
            }
            if (backlight != null) {
                backlight.setEnabled(!value);
            }
            return true;
        } else if (preference == mHomeLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mHomeDoubleTapAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mBackLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_BACK_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mMenuPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_MENU_ACTION);
            return true;
        } else if (preference == mMenuLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_MENU_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAssistPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_ASSIST_ACTION);
            return true;
        } else if (preference == mAssistLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_ASSIST_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAppSwitchPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_ACTION);
            return true;
        } else if (preference == mAppSwitchLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAppSwitchDoubleTapAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mVolumeKeyCursorControl) {
            handleSystemListChange((ListPreference) preference, newValue,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL);
            return true;
        }
        return false;
    }

    private static boolean isKeyDisablerSupported(Context context) {
        final LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
        return hardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE);
    }

    private static boolean isKeySwapperSupported(Context context) {
        final LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
        return hardware.isSupported(LineageHardwareManager.FEATURE_KEY_SWAP);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mSwapVolumeButtons) {
            int value;

            if (mSwapVolumeButtons.isChecked()) {
                /* The native inputflinger service uses the same logic of:
                 *   1 - the volume rocker is on one the sides, relative to the natural
                 *       orientation of the display (true for all phones and most tablets)
                 *   2 - the volume rocker is on the top or bottom, relative to the
                 *       natural orientation of the display (true for some tablets)
                 */
                value = getResources().getInteger(
                        R.integer.config_volumeRockerVsDisplayOrientation);
            } else {
                /* Disable the re-orient functionality */
                value = 0;
            }
            LineageSettings.System.putInt(getActivity().getContentResolver(),
                    LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, value);
        } else if (preference == mPowerEndCall) {
            handleTogglePowerButtonEndsCallPreferenceClick();
            return true;
        } else if (preference == mHomeAnswerCall) {
            handleToggleHomeButtonAnswersCallPreferenceClick();
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void handleTogglePowerButtonEndsCallPreferenceClick() {
        Settings.Secure.putInt(getContentResolver(),
                Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR, (mPowerEndCall.isChecked()
                        ? Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP
                        : Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF));
    }

    private void handleToggleHomeButtonAnswersCallPreferenceClick() {
        LineageSettings.Secure.putInt(getContentResolver(),
                LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR, (mHomeAnswerCall.isChecked()
                        ? LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_ANSWER
                        : LineageSettings.Secure.RING_HOME_BUTTON_BEHAVIOR_DO_NOTHING));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.CLICK_PARTIAL_SCREENSHOT, 0, UserHandle.USER_CURRENT);
        // IchthysOS: on-screen navigation button actions (consumed by Launcher3's
        // TaskbarNavButtonController). Reset to the stock behaviour: assistant on home long
        // press, nothing else bound.
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                Action.VOICE_SEARCH.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        // IchthysOS: torch-on-power is stored in Settings.System so PhoneWindowManager (which has
        // no lineage-sdk dependency) can read it directly.
        Settings.System.putIntForUser(resolver,
                "torch_long_press_power_gesture", 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                "torch_long_press_power_timeout", 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.HARDWARE_KEYS_DISABLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.SWAP_CAPACITIVE_KEYS, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ANBI_ENABLED, 0, UserHandle.USER_CURRENT);
        ButtonBacklightBrightness.reset(mContext);
        PowerMenuActions.reset(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.crdroid_settings_button) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    LineageHardwareManager mLineageHardware = LineageHardwareManager.getInstance(context);

                    final boolean hasHomeKey = DeviceUtils.hasHomeKey(context);
                    final boolean hasBackKey = DeviceUtils.hasBackKey(context);
                    final boolean hasMenuKey = DeviceUtils.hasMenuKey(context);
                    final boolean hasAssistKey = DeviceUtils.hasAssistKey(context);
                    final boolean hasAppSwitchKey = DeviceUtils.hasAppSwitchKey(context);

                    if (!isKeyDisablerSupported(context))
                        keys.add(HWKEYS_DISABLED);

                    if (!isKeySwapperSupported(context))
                        keys.add(KEY_SWAP_CAPACITIVE_KEYS);

                    if (!hasHomeKey && !hasBackKey && !hasMenuKey && !hasAssistKey
                            && !hasAppSwitchKey)
                        keys.add(KEY_ANBI);

                    if (!DeviceUtils.hasPowerKey()) {
                        keys.add(KEY_POWER_MENU);
                        keys.add(KEY_POWER_END_CALL);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                        keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                    } else {
                        if (!TelephonyUtils.isVoiceCapable(context)) {
                            keys.add(KEY_POWER_END_CALL);
                        }
                        if (!DeviceUtils.deviceSupportsFlashLight(context)) {
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                            keys.add(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
                        }
                    }

                    // IchthysOS: keep this in step with onCreate(). The home/recents long-press
                    // and double-tap actions also drive the ON-SCREEN navigation buttons, so
                    // they stay searchable on a device with no physical keys; everything that
                    // genuinely needs a physical key does not.
                    final boolean hasNavButtons = hasOnScreenNavButtons(context);

                    keys.add(KEY_HOME_WAKE_SCREEN);
                    if (!hasHomeKey && !hasNavButtons) {
                        keys.add(KEY_HOME_LONG_PRESS);
                        keys.add(KEY_HOME_DOUBLE_TAP);
                    }

                    if (!DeviceUtils.hasHomeKey(context) || !TelephonyUtils.isVoiceCapable(context)) {
                        keys.add(KEY_HOME_ANSWER_CALL);
                    }

                    keys.add(KEY_BACK_WAKE_SCREEN);
                    keys.add(KEY_BACK_LONG_PRESS);
                    keys.add(KEY_MENU_WAKE_SCREEN);
                    keys.add(KEY_MENU_PRESS);
                    keys.add(KEY_MENU_LONG_PRESS);
                    keys.add(KEY_ASSIST_WAKE_SCREEN);
                    keys.add(KEY_ASSIST_PRESS);
                    keys.add(KEY_ASSIST_LONG_PRESS);
                    keys.add(KEY_APP_SWITCH_WAKE_SCREEN);
                    keys.add(KEY_APP_SWITCH_PRESS);
                    if (!hasAppSwitchKey && !hasNavButtons) {
                        keys.add(KEY_APP_SWITCH_LONG_PRESS);
                        keys.add(KEY_APP_SWITCH_DOUBLE_TAP);
                    }
                    keys.add(KEY_CAMERA_WAKE_SCREEN);

                    if (!DeviceUtils.hasCameraKey(context)) {
                        keys.add(KEY_CAMERA_SLEEP_ON_RELEASE);
                        keys.add(KEY_CAMERA_LAUNCH);
                    }

                    keys.add(KEY_VOLUME_WAKE_SCREEN);

                    if (!DeviceUtils.hasVolumeKeys(context)) {
                        keys.add(KEY_VOLUME_ANSWER_CALL);
                        keys.add(KEY_VOLUME_MUSIC_CONTROLS);
                        keys.add(KEY_VOLUME_KEY_CURSOR_CONTROL);
                        keys.add(KEY_SWAP_VOLUME_BUTTONS);
                        keys.add(KEY_CLICK_PARTIAL_SCREENSHOT);
                    } else {
                        if (!TelephonyUtils.isVoiceCapable(context)) {
                            keys.add(KEY_VOLUME_ANSWER_CALL);
                        }
                    }

                    if (!DeviceUtils.hasButtonBacklightSupport(context)
                            && !DeviceUtils.hasKeyboardBacklightSupport(context)) {
                        keys.add(KEY_BUTTON_BACKLIGHT);
                    }

                    keys.add(KEY_ADDITIONAL_BUTTONS);

                    return keys;
                }
            };
}
