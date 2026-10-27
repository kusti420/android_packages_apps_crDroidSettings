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

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import static org.lineageos.internal.util.DeviceKeysConstants.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.ichthys.ShoalStyle;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.fragments.buttons.PowerMenuActions;
import com.crdroid.settings.utils.DeviceUtils;
import com.crdroid.settings.utils.TelephonyUtils;

import java.util.List;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

/**
 * IchthysOS &gt; System &gt; Buttons &amp; gestures: the power and volume keys, the on-screen
 * navigation buttons, gesture navigation, and the screen, status bar and lock screen gestures.
 *
 * <p>The gestures came from the old Navigation, Status bar, Lock screen and Miscellaneous tabs.
 * Their defaults are still restored by the reset() of the area they belong to
 * ({@link Navigation#reset}, {@link StatusBar#reset}, {@link LockScreen#reset}); {@link #reset}
 * covers the buttons, the three finger swipe and the rotation button.
 */
@SearchIndexable
public class Buttons extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "Buttons";

    private static final String KEY_HOME_LONG_PRESS = "hardware_keys_home_long_press";
    private static final String KEY_HOME_DOUBLE_TAP = "hardware_keys_home_double_tap";
    private static final String KEY_APP_SWITCH_LONG_PRESS = "hardware_keys_app_switch_long_press";
    private static final String KEY_APP_SWITCH_DOUBLE_TAP = "hardware_keys_app_switch_double_tap";
    private static final String KEY_VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";
    private static final String KEY_SWAP_VOLUME_BUTTONS = "swap_volume_buttons";
    private static final String KEY_VOLUME_WAKE_SCREEN = "volume_wake_screen";
    private static final String KEY_VOLUME_ANSWER_CALL = "volume_answer_call";
    private static final String KEY_VOLUME_MUSIC_CONTROLS = "volbtn_music_controls";
    private static final String KEY_VOLUME_MUSIC_CONTROLS_SCREEN_ON =
            "volume_music_controls_screen_on";
    private static final String KEY_VOLUME_KEY_ONE_STEP = "volume_key_one_step";
    private static final String KEY_POWER_END_CALL = "power_end_call";
    private static final String KEY_TORCH_LONG_PRESS_POWER_GESTURE =
            "torch_long_press_power_gesture";
    private static final String KEY_TORCH_LONG_PRESS_POWER_TIMEOUT =
            "torch_long_press_power_timeout";
    private static final String KEY_POWER_MENU = "power_menu";
    private static final String KEY_NAVBAR_KEY_ORDER = "navigationbar_key_order";
    private static final String KEY_BACK_GESTURE_ARROW = "back_gesture_arrow";
    private static final String KEY_BACK_GESTURE_HAPTIC = "back_gesture_haptic";
    private static final String KEY_BACK_GESTURE_HEIGHT = "back_gesture_height";
    private static final String KEY_THREE_FINGERS_SWIPE = "three_fingers_swipe";

    private static final String CATEGORY_POWER = "power_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_NAV_GESTURE = "navigation_gesture_category";


    private ListPreference mHomeLongPressAction;
    private ListPreference mHomeDoubleTapAction;
    private ListPreference mAppSwitchLongPressAction;
    private ListPreference mAppSwitchDoubleTapAction;
    private ListPreference mVolumeKeyCursorControl;
    private ListPreference mThreeFingersSwipeAction;
    private SwitchPreferenceCompat mSwapVolumeButtons;
    private SwitchPreferenceCompat mPowerEndCall;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.crdroid_settings_button);

        final Context context = getActivity();
        final ContentResolver resolver = context.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        final PreferenceCategory powerCategory = prefScreen.findPreference(CATEGORY_POWER);
        final PreferenceCategory homeCategory = prefScreen.findPreference(CATEGORY_HOME);
        final PreferenceCategory appSwitchCategory = prefScreen.findPreference(CATEGORY_APPSWITCH);
        final PreferenceCategory volumeCategory = prefScreen.findPreference(CATEGORY_VOLUME);

        // Power button.
        mPowerEndCall = findPreference(KEY_POWER_END_CALL);
        if (DeviceUtils.hasPowerKey()) {
            if (!TelephonyUtils.isVoiceCapable(context)) {
                removePref(KEY_POWER_END_CALL);
                mPowerEndCall = null;
            }
            if (!DeviceUtils.deviceSupportsFlashLight(context)) {
                removePref(KEY_TORCH_LONG_PRESS_POWER_GESTURE);
                removePref(KEY_TORCH_LONG_PRESS_POWER_TIMEOUT);
            }
        }
        if (!DeviceUtils.hasPowerKey() || isEmpty(powerCategory)) {
            removePref(CATEGORY_POWER);
            mPowerEndCall = null;
        }

        // Home and recents. config_deviceHardwareKeys only describes PHYSICAL keys; the long
        // press / double tap lists drive the on-screen navigation buttons, which live in the
        // Launcher taskbar and are consumed by TaskbarNavButtonController, so they are gated on
        // the navigation bar rather than on the hardware mask.
        if (hasOnScreenNavButtons(context)) {
            // No assistant on this ROM, so the home long press defaults to nothing, the same as
            // TaskbarNavButtonController's DEFAULT_HOME_LONG_PRESS_ACTION.
            mHomeLongPressAction = initList(KEY_HOME_LONG_PRESS, Action.fromSettings(resolver,
                    LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION, Action.NOTHING));
            mHomeDoubleTapAction = initList(KEY_HOME_DOUBLE_TAP, Action.fromSettings(resolver,
                    LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                    Action.fromIntSafe(DeviceUtils.getLineageInteger(context,
                            org.lineageos.platform.internal.R.integer
                                    .config_doubleTapOnHomeBehavior, 0))));
            mAppSwitchLongPressAction = initList(KEY_APP_SWITCH_LONG_PRESS,
                    Action.fromSettings(resolver,
                            LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                            Action.fromIntSafe(DeviceUtils.getLineageInteger(context,
                                    org.lineageos.platform.internal.R.integer
                                            .config_longPressOnAppSwitchBehavior, 0))));
            // Default NOTHING, not LAST_APP. TaskbarNavButtonController has to hold a recents tap
            // back for the double-tap timeout whenever a double-tap action is set, so a
            // non-NOTHING default would put ~300ms of latency on every recents tap.
            mAppSwitchDoubleTapAction = initList(KEY_APP_SWITCH_DOUBLE_TAP,
                    Action.fromSettings(resolver,
                            LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION,
                            Action.NOTHING));
        } else {
            removePref(CATEGORY_HOME);
            removePref(CATEGORY_APPSWITCH);
        }

        // Volume buttons.
        if (DeviceUtils.hasVolumeKeys(context)) {
            if (!DeviceUtils.canWakeUsingVolumeKeys(context)) {
                removePref(KEY_VOLUME_WAKE_SCREEN);
            }
            if (!TelephonyUtils.isVoiceCapable(context)) {
                removePref(KEY_VOLUME_ANSWER_CALL);
            }

            mVolumeKeyCursorControl = initList(KEY_VOLUME_KEY_CURSOR_CONTROL,
                    Settings.System.getInt(resolver,
                            Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0));

            mSwapVolumeButtons = prefScreen.findPreference(KEY_SWAP_VOLUME_BUTTONS);
            if (mSwapVolumeButtons != null) {
                mSwapVolumeButtons.setChecked(LineageSettings.System.getInt(resolver,
                        LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, 0) > 0);
            }

            // Waking with the volume keys and using them as music keys exclude each other.
            final SwitchPreferenceCompat volumeWakeScreen = findPreference(KEY_VOLUME_WAKE_SCREEN);
            final SwitchPreferenceCompat volumeMusicControls =
                    findPreference(KEY_VOLUME_MUSIC_CONTROLS);
            if (volumeWakeScreen != null && volumeMusicControls != null) {
                volumeMusicControls.setDependency(KEY_VOLUME_WAKE_SCREEN);
                volumeWakeScreen.setDisableDependentsState(true);
            }
        }
        if (!DeviceUtils.hasVolumeKeys(context) || isEmpty(volumeCategory)) {
            removePref(CATEGORY_VOLUME);
        }

        // Navigation bar: the button order only applies to 3-button navigation, the back gesture
        // options only to gesture navigation.
        if (isGesturalMode(context)) {
            removePref(KEY_NAVBAR_KEY_ORDER);
        } else {
            removePref(CATEGORY_NAV_GESTURE);
        }

        // Recents style; the Slim options only apply to the Slim style (moved from Theme
        // Studio with their rows, tracker#59).
        final Preference recentsType = findPreference("recents_type");
        if (recentsType != null) {
            recentsType.setOnPreferenceChangeListener((pref, val) -> {
                updateSlimRecents(Integer.parseInt((String) val));
                return true;
            });
            updateSlimRecents(Settings.System.getIntForUser(resolver, "recents_type", 0,
                    UserHandle.USER_CURRENT));
            // IchthysOS: in gesture navigation the custom styles now open from swipe-up-and-hold
            // both over an app (AbsSwipeUpHandler) and on the home screen
            // (NoButtonNavbarToOverviewTouchController), so the "home screen still shows the
            // stock overview" summary (recents_type_summary_gestures) is no longer shown.
        }
        // Three fingers swipe.
        mThreeFingersSwipeAction = initList(KEY_THREE_FINGERS_SWIPE, Action.fromSettings(resolver,
                LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION, Action.NOTHING));
    }

    private void updateSlimRecents(int recentsType) {
        final Preference slim = findPreference("slim_recents");
        if (slim != null) {
            slim.setEnabled(recentsType == 2 /* Slim */);
        }
    }

    /** Removes a preference (or a whole category) by key, anywhere in the tree. */
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

    private static boolean isGesturalMode(Context context) {
        return context.getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode)
                        == NAV_BAR_MODE_GESTURAL;
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
            mPowerEndCall.setChecked(incallPowerBehavior
                    == Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP);
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
        if (preference == mHomeLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mHomeDoubleTapAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mAppSwitchLongPressAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION);
            return true;
        } else if (preference == mAppSwitchDoubleTapAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION);
            return true;
        } else if (preference == mThreeFingersSwipeAction) {
            handleListChange((ListPreference) preference, newValue,
                    LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION);
            return true;
        } else if (preference == mVolumeKeyCursorControl) {
            handleSystemListChange((ListPreference) preference, newValue,
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (mSwapVolumeButtons != null && preference == mSwapVolumeButtons) {
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
        } else if (mPowerEndCall != null && preference == mPowerEndCall) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR, (mPowerEndCall.isChecked()
                            ? Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_HANGUP
                            : Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR_SCREEN_OFF));
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // IchthysOS Shoal: the row icons become outlined badges in the accent.
        ShoalStyle.badgeAll(getPreferenceScreen(), ShoalStyle.accent(view.getContext()));
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        // On-screen navigation button actions (consumed by Launcher3's
        // TaskbarNavButtonController): nothing bound, as on a fresh install.
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_HOME_LONG_PRESS_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_APP_SWITCH_DOUBLE_TAP_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.KEY_THREE_FINGERS_SWIPE_ACTION,
                Action.NOTHING.ordinal(), UserHandle.USER_CURRENT);
        // Torch-on-power is stored in Settings.System so PhoneWindowManager (which has no
        // lineage-sdk dependency) can read it directly.
        Settings.System.putIntForUser(resolver,
                KEY_TORCH_LONG_PRESS_POWER_GESTURE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_TORCH_LONG_PRESS_POWER_TIMEOUT, 0, UserHandle.USER_CURRENT);
        // Volume buttons.
        Settings.System.putIntForUser(resolver,
                KEY_VOLUME_WAKE_SCREEN, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_VOLUME_ANSWER_CALL, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_VOLUME_MUSIC_CONTROLS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_VOLUME_MUSIC_CONTROLS_SCREEN_ON, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                KEY_VOLUME_KEY_ONE_STEP, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0, UserHandle.USER_CURRENT);
        LineageSettings.System.putIntForUser(resolver,
                LineageSettings.System.SWAP_VOLUME_KEYS_ON_ROTATION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ENABLE_ROTATION_BUTTON, 1, UserHandle.USER_CURRENT);
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

                    // Keep this in step with onCreate().
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

                    if (!hasOnScreenNavButtons(context)) {
                        keys.add(KEY_HOME_LONG_PRESS);
                        keys.add(KEY_HOME_DOUBLE_TAP);
                        keys.add(KEY_APP_SWITCH_LONG_PRESS);
                        keys.add(KEY_APP_SWITCH_DOUBLE_TAP);
                    }

                    if (!DeviceUtils.hasVolumeKeys(context)) {
                        keys.add(KEY_VOLUME_WAKE_SCREEN);
                        keys.add(KEY_VOLUME_ANSWER_CALL);
                        keys.add(KEY_VOLUME_MUSIC_CONTROLS);
                        keys.add(KEY_VOLUME_MUSIC_CONTROLS_SCREEN_ON);
                        keys.add(KEY_VOLUME_KEY_ONE_STEP);
                        keys.add(KEY_VOLUME_KEY_CURSOR_CONTROL);
                        keys.add(KEY_SWAP_VOLUME_BUTTONS);
                    } else {
                        if (!DeviceUtils.canWakeUsingVolumeKeys(context)) {
                            keys.add(KEY_VOLUME_WAKE_SCREEN);
                        }
                        if (!TelephonyUtils.isVoiceCapable(context)) {
                            keys.add(KEY_VOLUME_ANSWER_CALL);
                        }
                    }

                    if (isGesturalMode(context)) {
                        keys.add(KEY_NAVBAR_KEY_ORDER);
                    } else {
                        keys.add(KEY_BACK_GESTURE_ARROW);
                        keys.add(KEY_BACK_GESTURE_HAPTIC);
                        keys.add(KEY_BACK_GESTURE_HEIGHT);
                    }

                    return keys;
                }
            };
}
