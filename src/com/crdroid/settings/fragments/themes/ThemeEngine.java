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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.crdroid.settings.preferences.colorpicker.ColorPickerDialog;
import com.crdroid.settings.preferences.colorpicker.ColorPickerPreference;
import com.crdroid.settings.utils.SystemUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * IchthysOS Theme Engine - an 8.1/Substratum-inspired accent picker.
 *
 * The whole screen is a single {@link RecyclerView} grid (5 swatches per row).
 * The palette-style / pitch-black controls and the section labels are full-span
 * grid items, so there are no sibling header views for the PreferenceFragment
 * recycler management to fight with (that was overlapping the grid onto the
 * header). Below the controls: a "Recent custom colors" row (last 10 custom
 * colors), then the premade palette plus a "Custom" chip that opens the
 * {@link ColorPickerDialog}.
 */
@SearchIndexable
public class ThemeEngine extends SettingsPreferenceFragment {

    private static final String TAG = "ThemeEngine";

    /**
     * Registered so R8/resource shrinking keeps this fragment (it is only
     * referenced from theme_global.xml by name).
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.theme_engine_index);

    private static final String OVERLAY_CATEGORY_ACCENT_COLOR =
            "android.theme.customization.accent_color";
    private static final String OVERLAY_CATEGORY_SYSTEM_PALETTE =
            "android.theme.customization.system_palette";
    private static final String OVERLAY_COLOR_SOURCE =
            "android.theme.customization.color_source";
    private static final String OVERLAY_SEED_COLOR_LIST =
            "android.theme.customization.seed_color_list";
    private static final String OVERLAY_CATEGORY_THEME_STYLE =
            "android.theme.customization.theme_style";
    private static final String COLOR_SOURCE_PRESET = "preset";
    private static final String TIMESTAMP_FIELD = "_applied_timestamp";

    private static final String[] LEGACY_TONAL_KEYS = {
            "android.theme.customization.chroma_factor",
            "android.theme.customization.luminance_factor",
            "android.theme.customization.whole_palette",
            "android.theme.customization.tint_background",
            "android.theme.customization.bg_color",
            "android.theme.customization.color_both",
    };

    private static final String STYLE_SUBSTRATUM = "VIBRANT";
    private static final String STYLE_MONET = "TONAL_SPOT";

    private static final String KEY_PITCH_BLACK = "custom_pitch_black";
    private static final String KEY_STYLE_MODE = "theme_engine_style_mode";
    // Our own persisted MRU list of custom colors (comma-separated 6-hex).
    private static final String KEY_RECENT_COLORS = "theme_engine_recent_colors";
    private static final int MAX_RECENT = 10;
    private static final int SPAN = 5;

    private static final int MODE_MONET = 0;
    private static final int MODE_SUBSTRATUM = 1;

    private static final int RGB_MASK = 0x00FFFFFF;
    private static final int CUSTOM_CHIP_COLOR = 0xFF9E9E9E;
    private static final int DEFAULT_CUSTOM_COLOR = 0xFF1B6EF3;
    private static final int DEFAULT_SUBSTRATUM_COLOR = 0xFFF31BDE;

    private RecyclerView mRecyclerView;
    private AccentAdapter mAdapter;
    private boolean mBindingUi;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isAdded()) {
            requireActivity().setTitle(R.string.theme_engine_title);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.theme_engine, container, false);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mAdapter = new AccentAdapter();
        final GridLayoutManager glm = new GridLayoutManager(requireContext(), SPAN);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (mAdapter != null && mAdapter.isFullSpan(position)) ? SPAN : 1;
            }
        });
        mRecyclerView.setLayoutManager(glm);
        mRecyclerView.setAdapter(mAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGrid();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
        mAdapter = null;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    private void refreshGrid() {
        if (mAdapter != null) {
            mAdapter.rebuild();
            mAdapter.notifyDataSetChanged();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Palette style mode (Substratum vs Monet) + pitch black              */
    /* ------------------------------------------------------------------ */

    private int getCurrentMode() {
        return Settings.Secure.getIntForUser(getContentResolver(),
                KEY_STYLE_MODE, MODE_MONET, UserHandle.USER_CURRENT);
    }

    private boolean isPitchBlack() {
        return Settings.Secure.getIntForUser(getContentResolver(),
                KEY_PITCH_BLACK, 0, UserHandle.USER_CURRENT) == 1;
    }

    private void setPitchBlack(boolean enabled) {
        Settings.Secure.putIntForUser(getContentResolver(),
                KEY_PITCH_BLACK, enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private void applyMode(int mode) {
        try {
            final JSONObject object = getSettingsJson();
            if (mode == MODE_SUBSTRATUM) {
                object.putOpt(OVERLAY_CATEGORY_THEME_STYLE, STYLE_SUBSTRATUM);
                object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_PRESET);
                for (String key : LEGACY_TONAL_KEYS) {
                    object.remove(key);
                }
                if (!object.has(OVERLAY_CATEGORY_SYSTEM_PALETTE)) {
                    final String rgb = ColorPickerPreference
                            .convertToRGB(DEFAULT_SUBSTRATUM_COLOR).replace("#", "");
                    object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgb);
                    object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgb);
                    final JSONArray seeds = new JSONArray();
                    seeds.put("FF" + rgb.toUpperCase());
                    object.put(OVERLAY_SEED_COLOR_LIST, seeds);
                }
            } else {
                object.putOpt(OVERLAY_CATEGORY_THEME_STYLE, STYLE_MONET);
            }
            object.putOpt(TIMESTAMP_FIELD, System.currentTimeMillis());
            putSettingsJson(object);

            Settings.Secure.putIntForUser(getContentResolver(),
                    KEY_STYLE_MODE, mode, UserHandle.USER_CURRENT);
            setPitchBlack(mode == MODE_SUBSTRATUM);
            refreshGrid();
            Toast.makeText(requireContext(), R.string.theme_engine_mode_applied,
                    Toast.LENGTH_SHORT).show();
            SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
        } catch (JSONException | IllegalArgumentException ignored) {
        }
    }

    /* ------------------------------------------------------------------ */
    /* Apply mechanism                                                     */
    /* ------------------------------------------------------------------ */

    private JSONObject getSettingsJson() throws JSONException {
        final String json = Settings.Secure.getStringForUser(
                getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                UserHandle.USER_CURRENT);
        if (json == null || json.isEmpty()) return new JSONObject();
        return new JSONObject(json);
    }

    private void putSettingsJson(JSONObject object) {
        Settings.Secure.putStringForUser(
                getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                object.toString(), UserHandle.USER_CURRENT);
    }

    private void applyAccent(int color) {
        try {
            final JSONObject object = getSettingsJson();
            final String rgb = ColorPickerPreference.convertToRGB(color).replace("#", "");
            object.putOpt(OVERLAY_CATEGORY_ACCENT_COLOR, rgb);
            object.putOpt(OVERLAY_CATEGORY_SYSTEM_PALETTE, rgb);
            object.putOpt(OVERLAY_COLOR_SOURCE, COLOR_SOURCE_PRESET);
            final JSONArray seeds = new JSONArray();
            seeds.put("FF" + rgb.toUpperCase());
            object.put(OVERLAY_SEED_COLOR_LIST, seeds);
            object.putOpt(TIMESTAMP_FIELD, System.currentTimeMillis());
            putSettingsJson(object);
            Toast.makeText(requireContext(), R.string.theme_engine_applied,
                    Toast.LENGTH_SHORT).show();
        } catch (JSONException | IllegalArgumentException ignored) {
        }
    }

    private int getCurrentAccent() {
        try {
            final JSONObject object = getSettingsJson();
            if (object.has(OVERLAY_CATEGORY_SYSTEM_PALETTE)) {
                final String c = object.optString(OVERLAY_CATEGORY_SYSTEM_PALETTE);
                if (c != null && !c.isEmpty()) {
                    return ColorPickerPreference.convertToColorInt(c) | 0xFF000000;
                }
            }
        } catch (JSONException | IllegalArgumentException ignored) {
        }
        return 0;
    }

    /* ------------------------------------------------------------------ */
    /* Recent custom colors (MRU, persisted)                               */
    /* ------------------------------------------------------------------ */

    private List<Integer> getRecentColors() {
        final List<Integer> out = new ArrayList<>();
        final String stored = Settings.Secure.getStringForUser(getContentResolver(),
                KEY_RECENT_COLORS, UserHandle.USER_CURRENT);
        if (stored == null || stored.isEmpty()) return out;
        for (String part : stored.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                out.add(Color.parseColor("#" + part) | 0xFF000000);
            } catch (IllegalArgumentException ignored) {
            }
            if (out.size() >= MAX_RECENT) break;
        }
        return out;
    }

    /** Insert a just-used custom color at the front of the MRU list (deduped). */
    private void addRecentColor(int color) {
        final int rgb = color & RGB_MASK;
        final List<String> hexes = new ArrayList<>();
        hexes.add(String.format("%06X", rgb));
        for (Integer c : getRecentColors()) {
            if ((c & RGB_MASK) == rgb) continue; // dedupe
            hexes.add(String.format("%06X", c & RGB_MASK));
            if (hexes.size() >= MAX_RECENT) break;
        }
        Settings.Secure.putStringForUser(getContentResolver(),
                KEY_RECENT_COLORS, TextUtils.join(",", hexes), UserHandle.USER_CURRENT);
    }

    /* ------------------------------------------------------------------ */
    /* Grid adapter (header + labels + swatches)                           */
    /* ------------------------------------------------------------------ */

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_LABEL = 1;
    private static final int TYPE_SWATCH = 2;

    private static final class Cell {
        final int type;
        final int color;
        final String label;
        final boolean customChip;

        Cell(int type, int color, String label, boolean customChip) {
            this.type = type;
            this.color = color;
            this.label = label;
            this.customChip = customChip;
        }
    }

    private final class AccentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Integer> mPremadeColors = new ArrayList<>();
        private final List<String> mPremadeNames = new ArrayList<>();
        private final List<Cell> mCells = new ArrayList<>();

        AccentAdapter() {
            final String[] hex = getResources().getStringArray(R.array.theme_engine_accent_hex);
            final String[] names = getResources().getStringArray(R.array.theme_engine_accent_names);
            for (int i = 0; i < hex.length; i++) {
                try {
                    mPremadeColors.add(Color.parseColor("#" + hex[i]) | 0xFF000000);
                    mPremadeNames.add(i < names.length ? names[i] : ("#" + hex[i]));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rebuild();
        }

        void rebuild() {
            mCells.clear();
            mCells.add(new Cell(TYPE_HEADER, 0, null, false));
            final List<Integer> recents = getRecentColors();
            if (!recents.isEmpty()) {
                mCells.add(new Cell(TYPE_LABEL, 0,
                        getString(R.string.theme_engine_recent_label), false));
                for (int c : recents) {
                    mCells.add(new Cell(TYPE_SWATCH, c,
                            String.format("#%06X", c & RGB_MASK), false));
                }
            }
            mCells.add(new Cell(TYPE_LABEL, 0,
                    getString(R.string.theme_engine_header), false));
            for (int i = 0; i < mPremadeColors.size(); i++) {
                mCells.add(new Cell(TYPE_SWATCH, mPremadeColors.get(i),
                        mPremadeNames.get(i), false));
            }
            mCells.add(new Cell(TYPE_SWATCH, 0, null, true)); // custom chip
        }

        boolean isFullSpan(int position) {
            return position >= 0 && position < mCells.size()
                    && mCells.get(position).type != TYPE_SWATCH;
        }

        @Override
        public int getItemViewType(int position) {
            return mCells.get(position).type;
        }

        @Override
        public int getItemCount() {
            return mCells.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final LayoutInflater inf = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case TYPE_HEADER:
                    return new HeaderHolder(
                            inf.inflate(R.layout.theme_engine_header, parent, false));
                case TYPE_LABEL:
                    return new LabelHolder(
                            inf.inflate(R.layout.theme_engine_section_label, parent, false));
                default:
                    return new SwatchHolder(
                            inf.inflate(R.layout.theme_engine_swatch, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Cell cell = mCells.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).bind();
            } else if (holder instanceof LabelHolder) {
                ((LabelHolder) holder).label.setText(cell.label);
            } else {
                ((SwatchHolder) holder).bind(cell);
            }
        }

        private boolean matchesAnyPremade(int color) {
            for (int c : mPremadeColors) {
                if ((c & RGB_MASK) == (color & RGB_MASK)) return true;
            }
            return false;
        }

        private void openCustomPicker(int current) {
            final int initial = current != 0 ? current : DEFAULT_CUSTOM_COLOR;
            final ColorPickerDialog dialog = new ColorPickerDialog(requireContext(), initial);
            dialog.setAlphaSliderVisible(false);
            dialog.setOnColorChangedListener(color -> {
                final int opaque = color | 0xFF000000;
                applyAccent(opaque);
                if (!matchesAnyPremade(opaque)) {
                    addRecentColor(opaque);
                }
                refreshGrid();
            });
            dialog.show();
        }

        final class HeaderHolder extends RecyclerView.ViewHolder {
            final RadioGroup modeGroup;
            final Switch pitchSwitch;

            HeaderHolder(View v) {
                super(v);
                modeGroup = v.findViewById(R.id.theme_engine_mode_group);
                pitchSwitch = v.findViewById(R.id.theme_engine_pitch_black_switch);
                final View pitchRow = v.findViewById(R.id.theme_engine_pitch_black_row);
                modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                    if (mBindingUi) return;
                    applyMode(checkedId == R.id.theme_engine_mode_substratum
                            ? MODE_SUBSTRATUM : MODE_MONET);
                });
                pitchRow.setOnClickListener(x -> pitchSwitch.toggle());
                pitchSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
                    if (mBindingUi) return;
                    setPitchBlack(checked);
                    SystemUtils.INSTANCE.showSystemUiRestartDialog(getContext());
                });
            }

            void bind() {
                mBindingUi = true;
                try {
                    final int mode = getCurrentMode();
                    modeGroup.check(mode == MODE_SUBSTRATUM
                            ? R.id.theme_engine_mode_substratum
                            : R.id.theme_engine_mode_monet);
                    pitchSwitch.setChecked(isPitchBlack());
                } finally {
                    mBindingUi = false;
                }
            }
        }

        final class LabelHolder extends RecyclerView.ViewHolder {
            final TextView label;

            LabelHolder(View v) {
                super(v);
                label = v.findViewById(R.id.theme_engine_label);
            }
        }

        final class SwatchHolder extends RecyclerView.ViewHolder {
            final View chip;
            final View ring;
            final ImageView check;
            final ImageView customIcon;
            final TextView label;

            SwatchHolder(View itemView) {
                super(itemView);
                chip = itemView.findViewById(R.id.swatch_chip);
                ring = itemView.findViewById(R.id.swatch_ring);
                check = itemView.findViewById(R.id.swatch_check);
                customIcon = itemView.findViewById(R.id.swatch_custom_icon);
                label = itemView.findViewById(R.id.swatch_label);
            }

            void bind(Cell cell) {
                final int current = getCurrentAccent();

                if (cell.customChip) {
                    chip.setBackgroundTintList(ColorStateList.valueOf(CUSTOM_CHIP_COLOR));
                    customIcon.setVisibility(View.VISIBLE);
                    check.setVisibility(View.GONE);
                    label.setText(R.string.theme_engine_custom);
                    final boolean active = current != 0 && !matchesAnyPremade(current);
                    ring.setVisibility(active ? View.VISIBLE : View.GONE);
                    itemView.setOnClickListener(v -> openCustomPicker(current));
                    return;
                }

                chip.setBackgroundTintList(ColorStateList.valueOf(cell.color));
                customIcon.setVisibility(View.GONE);
                label.setText(cell.label != null ? cell.label : "");

                final boolean selected = current != 0
                        && (current & RGB_MASK) == (cell.color & RGB_MASK);
                check.setVisibility(selected ? View.VISIBLE : View.GONE);
                ring.setVisibility(selected ? View.VISIBLE : View.GONE);

                itemView.setOnClickListener(v -> {
                    applyAccent(cell.color);
                    if (!matchesAnyPremade(cell.color)) {
                        addRecentColor(cell.color);
                    }
                    refreshGrid();
                });
            }
        }
    }
}
