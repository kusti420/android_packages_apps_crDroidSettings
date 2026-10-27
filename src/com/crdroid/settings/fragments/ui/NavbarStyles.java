/*
 * Copyright (C) 2022-2025 crDroid Android Project
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

package com.crdroid.settings.fragments.ui;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Look &gt; Navigation bar &gt; style: picks one of the IchthysOS code-drawn navigation bar styles
 * (navBar.style in ichthys.nix). The only state is Settings.Secure {@code ichthys_navbar_style};
 * SystemUI's NavigationBarView and Launcher's taskbar both observe it and redraw live, so
 * there are no overlay packages to enable and no SystemUI restart.
 */
public class NavbarStyles extends SettingsPreferenceFragment {

    private static final String KEY_ICHTHYS_STYLE = "ichthys_navbar_style";
    /** What an unset or unknown value means, in SystemUI and Launcher as well. */
    private static final String DEFAULT_STYLE = "shoal";
    /** In list order. "stock" is the plain AOSP bar. */
    private static final String[] STYLES = {"shoal", "lattice", "mono", "gestures", "stock"};

    private RecyclerView mRecyclerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isAdded()) return;
        requireActivity().setTitle(R.string.theme_customization_navbar_title);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.item_view, container, false);
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        mRecyclerView.setAdapter(new Adapter(requireContext()));
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    /** Restores the default style. IchthysOS: and drops the icon pack, which only stock uses. */
    public static void reset(Context context) {
        Settings.Secure.putString(context.getContentResolver(), KEY_ICHTHYS_STYLE, null);
        Settings.Secure.putString(context.getContentResolver(), KEY_ICONS, null);
    }

    /** crDroid's navbar icon packs (IchthysNavIcons), each a variant of the stock style. */
    private static final String[] PACKS = {"android", "asus", "dora", "moto", "nexus", "old",
            "oneplus", "oneui", "sammy", "tecno"};
    private static final String[] PACK_LABELS = {"Android", "Asus", "Dora", "Moto", "Nexus",
            "Old", "OnePlus", "One UI", "Sammy", "Tecno Camon"};
    private static final String KEY_ICONS = "ichthys_navbar_icons";
    private static final String ICONS_PACKAGE = "org.ichthys.navicons";

    /**
     * The picker: IchthysOS's five styles, each drawn as a preview of what it looks like, then
     * the stock style with each of crDroid's icon packs, showing the pack's own glyphs. Choosing
     * a pack sets the stock style and the pack; choosing a style clears the pack.
     */
    private static class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        private final Context mContext;
        private final Context mIcons;
        private String mSelected;

        Adapter(Context context) {
            mContext = context;
            Context icons = null;
            try {
                icons = context.createPackageContext(ICONS_PACKAGE, 0);
            } catch (Exception e) {
                // No packs on this build: only the styles are listed.
            }
            mIcons = icons;
            final String stored = Settings.Secure.getString(context.getContentResolver(),
                    KEY_ICHTHYS_STYLE);
            final String pack = Settings.Secure.getString(context.getContentResolver(),
                    KEY_ICONS);
            final String style = isKnownStyle(stored) ? stored : DEFAULT_STYLE;
            mSelected = "stock".equals(style) && pack != null && !pack.isEmpty()
                    ? "pack:" + pack : style;
        }

        private int packCount() {
            return mIcons == null ? 0 : PACKS.length;
        }

        private String entry(int position) {
            return position < STYLES.length ? STYLES[position]
                    : "pack:" + PACKS[position - STYLES.length];
        }

        @NonNull
        @Override
        public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.navbar_option, parent, false);
            return new CustomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomViewHolder holder, int position) {
            final String entry = entry(position);
            final ImageView[] keys = {holder.image1, holder.image2, holder.image3};
            final String[] names = {"back", "home", "recent"};
            if (entry.startsWith("pack:")) {
                final String pack = entry.substring(5);
                for (int k = 0; k < 3; k++) {
                    keys[k].setBackground(packIcon(pack, names[k]));
                    keys[k].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            textColor()));
                }
                holder.name.setText(mContext.getString(R.string.navbar_style_stock_pack,
                        PACK_LABELS[position - STYLES.length]));
            } else {
                for (int k = 0; k < 3; k++) {
                    keys[k].setBackground(new StylePreview(entry, k, textColor(), accent(),
                            mContext.getResources().getDisplayMetrics().density, mContext));
                    keys[k].setBackgroundTintList(null);
                }
                holder.name.setText(label(entry));
            }
            holder.itemView.setActivated(entry.equals(mSelected));
            holder.itemView.setOnClickListener(view -> {
                if (entry.equals(mSelected)) return;
                mSelected = entry;
                final boolean pack = entry.startsWith("pack:");
                Settings.Secure.putString(mContext.getContentResolver(), KEY_ICONS,
                        pack ? entry.substring(5) : null);
                Settings.Secure.putString(mContext.getContentResolver(), KEY_ICHTHYS_STYLE,
                        pack ? "stock" : entry);
                notifyDataSetChanged();
            });
        }

        private android.graphics.drawable.Drawable packIcon(String pack, String key) {
            final int id = mIcons.getResources().getIdentifier(pack + "_" + key, "drawable",
                    ICONS_PACKAGE);
            return id == 0 ? null : mIcons.getDrawable(id);
        }

        private int textColor() {
            return com.android.settingslib.Utils.getColorAttrDefaultColor(mContext,
                    android.R.attr.textColorPrimary);
        }

        private int accent() {
            return com.android.settingslib.Utils.getColorAttrDefaultColor(mContext,
                    android.R.attr.colorAccent);
        }

        @Override
        public int getItemCount() {
            return STYLES.length + packCount();
        }

        static class CustomViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final ImageView image1;
            final ImageView image2;
            final ImageView image3;

            CustomViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.option_label);
                image1 = itemView.findViewById(R.id.image1);
                image2 = itemView.findViewById(R.id.image2);
                image3 = itemView.findViewById(R.id.image3);
            }
        }

        private static boolean isKnownStyle(String style) {
            for (String s : STYLES) {
                if (s.equals(style)) return true;
            }
            return false;
        }

        private String label(String style) {
            switch (style) {
                case "shoal": return mContext.getString(R.string.navbar_style_shoal);
                case "lattice": return mContext.getString(R.string.navbar_style_lattice);
                case "mono": return mContext.getString(R.string.navbar_style_mono);
                case "gestures": return mContext.getString(R.string.navbar_style_gestures);
                default: return mContext.getString(R.string.navbar_style_stock);
            }
        }
    }

    /**
     * One key of a style, drawn the way the style draws it, so the picker shows what each style
     * looks like: Shoal outlined circles (home in the accent), Lattice small discs, Mono text
     * labels, Gestures only the handle, Stock the plain system glyphs.
     */
    private static final class StylePreview extends android.graphics.drawable.Drawable {
        private final String mStyle;
        private final int mKey; // 0 back, 1 home, 2 recent
        private final int mInk;
        private final int mAccent;
        private final float mDp;
        private final android.graphics.drawable.Drawable mGlyph;
        private final android.graphics.Paint mPaint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        StylePreview(String style, int key, int ink, int accent, float dp, Context context) {
            mStyle = style;
            mKey = key;
            mInk = ink;
            mAccent = accent;
            mDp = dp;
            final int glyph = key == 0 ? R.drawable.ic_sysbar_back
                    : key == 1 ? R.drawable.ic_sysbar_home : R.drawable.ic_sysbar_recent;
            mGlyph = context.getDrawable(glyph).mutate();
            mGlyph.setTint(ink);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas) {
            final android.graphics.Rect b = getBounds();
            final float cx = b.exactCenterX();
            final float cy = b.exactCenterY();
            final float r = Math.min(b.width(), b.height()) / 2f;
            final int color = mKey == 1 ? mAccent : mInk;
            switch (mStyle) {
                case "shoal":
                    mPaint.setStyle(android.graphics.Paint.Style.STROKE);
                    mPaint.setStrokeWidth(1.5f * mDp);
                    mPaint.setColor(color);
                    canvas.drawCircle(cx, cy, r * 0.8f, mPaint);
                    break;
                case "lattice":
                    mPaint.setStyle(android.graphics.Paint.Style.FILL);
                    mPaint.setColor(color);
                    canvas.drawCircle(cx, cy, r * 0.32f, mPaint);
                    break;
                case "mono":
                    mPaint.setStyle(android.graphics.Paint.Style.FILL);
                    mPaint.setColor(color);
                    mPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
                    mPaint.setTextSize(7.5f * mDp);
                    mPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    final String t = mKey == 0 ? "BACK" : mKey == 1 ? "HOME" : "APPS";
                    canvas.drawText(t, cx, cy + 2.5f * mDp, mPaint);
                    break;
                case "gestures":
                    if (mKey != 1) break;
                    mPaint.setStyle(android.graphics.Paint.Style.FILL);
                    mPaint.setColor(mAccent);
                    canvas.drawRoundRect(cx - r * 1.4f, cy - 2f * mDp, cx + r * 1.4f,
                            cy + 2f * mDp, 2f * mDp, 2f * mDp, mPaint);
                    break;
                default:
                    final int inset = Math.round(r * 0.2f);
                    mGlyph.setBounds(b.left + inset, b.top + inset, b.right - inset,
                            b.bottom - inset);
                    mGlyph.draw(canvas);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

}
