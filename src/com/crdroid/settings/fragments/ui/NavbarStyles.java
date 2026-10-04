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

    /** Restores the default style. */
    public static void reset(Context context) {
        Settings.Secure.putString(context.getContentResolver(), KEY_ICHTHYS_STYLE, null);
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {
        private final Context mContext;
        private String mSelected;

        Adapter(Context context) {
            mContext = context;
            final String stored = Settings.Secure.getString(context.getContentResolver(),
                    KEY_ICHTHYS_STYLE);
            mSelected = isKnownStyle(stored) ? stored : DEFAULT_STYLE;
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
            final String style = STYLES[position];
            // The styles are drawn in code, so there is nothing to preview: show the plain
            // glyphs and say what the style looks like in the label.
            holder.image1.setBackgroundResource(R.drawable.ic_sysbar_back);
            holder.image2.setBackgroundResource(R.drawable.ic_sysbar_home);
            holder.image3.setBackgroundResource(R.drawable.ic_sysbar_recent);
            holder.name.setText(label(style));
            holder.itemView.setActivated(style.equals(mSelected));
            holder.itemView.setOnClickListener(view -> {
                if (!style.equals(mSelected)) {
                    mSelected = style;
                    Settings.Secure.putString(mContext.getContentResolver(),
                            KEY_ICHTHYS_STYLE, style);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return STYLES.length;
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
}
