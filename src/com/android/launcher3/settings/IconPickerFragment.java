/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DefaultLauncher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DefaultLauncher. If not, see <https://www.gnu.org/licenses/>.
 */
package com.android.launcher3.settings;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable grid browser for all icons in an icon pack.
 * Step 2 of the per-app icon customization flow.
 */
public class IconPickerFragment extends Fragment {

    public static final String EXTRA_PACK_PACKAGE = "icon_picker_pack_package";
    public static final String EXTRA_COMPONENT_NAME = "icon_picker_component_name";
    public static final String EXTRA_IS_HOME = "icon_picker_is_home";

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ICON = 1;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    private String mPackPackage;
    private ComponentName mComponentName;
    private boolean mIsHome;

    private RecyclerView mRecyclerView;
    private IconGridAdapter mAdapter;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Runnable mPendingSearch;

    private List<ListItem> mAllItems = new ArrayList<>();
    private List<ListItem> mFilteredItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mPackPackage = args.getString(EXTRA_PACK_PACKAGE, "");
            String cnStr = args.getString(EXTRA_COMPONENT_NAME, "");
            mComponentName = ComponentName.unflattenFromString(cnStr);
            mIsHome = args.getBoolean(EXTRA_IS_HOME, true);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_icon_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText searchInput = view.findViewById(R.id.icon_search_input);
        mRecyclerView = view.findViewById(R.id.icon_grid);

        // Tint the search icon to match M3 on-surface-variant
        int hintColor = requireContext().getColor(R.color.materialColorOnSurfaceVariant);
        Drawable[] drawables = searchInput.getCompoundDrawablesRelative();
        if (drawables[0] != null) {
            drawables[0].setTintList(ColorStateList.valueOf(hintColor));
        }

        // Edge-to-edge: add bottom nav bar inset padding to the RecyclerView
        mRecyclerView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(),
                    (int) (16 * getResources().getDisplayMetrics().density)
                            + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        // Calculate span count based on available width
        mRecyclerView.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            int cellSizePx = (int) (64 * density);
            int width = mRecyclerView.getWidth();
            int spanCount = Math.max(4, width / cellSizePx);

            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position < mFilteredItems.size()
                            && mFilteredItems.get(position).type == VIEW_TYPE_HEADER) {
                        return spanCount;
                    }
                    return 1;
                }
            });
            mRecyclerView.setLayoutManager(layoutManager);

            mAdapter = new IconGridAdapter();
            mRecyclerView.setAdapter(mAdapter);

            loadIcons();
        });

        // Search with debounce
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mPendingSearch != null) {
                    mMainHandler.removeCallbacks(mPendingSearch);
                }
                mPendingSearch = () -> filterItems(s.toString());
                mMainHandler.postDelayed(mPendingSearch, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void loadIcons() {
        Executors.MODEL_EXECUTOR.execute(() -> {
            IconPackManager mgr = LauncherComponentProvider.get(requireContext())
                    .getIconPackManager();
            IconPack pack = mgr.getPack(mPackPackage);
            if (pack == null) return;

            PackageManager pm = requireContext().getPackageManager();
            List<IconPack.IconCategory> categories = pack.getAllIcons(pm);

            List<ListItem> items = new ArrayList<>();
            for (IconPack.IconCategory cat : categories) {
                items.add(new ListItem(cat.title));
                for (IconPack.IconEntry entry : cat.items) {
                    items.add(new ListItem(entry));
                }
            }

            mMainHandler.post(() -> {
                mAllItems = items;
                mFilteredItems = new ArrayList<>(items);
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    private void filterItems(String query) {
        if (query == null || query.trim().isEmpty()) {
            mFilteredItems = new ArrayList<>(mAllItems);
        } else {
            String lowerQuery = query.toLowerCase();
            mFilteredItems = new ArrayList<>();
            String lastHeader = null;
            List<ListItem> pendingIcons = new ArrayList<>();

            for (ListItem item : mAllItems) {
                if (item.type == VIEW_TYPE_HEADER) {
                    if (lastHeader != null && !pendingIcons.isEmpty()) {
                        mFilteredItems.add(new ListItem(lastHeader));
                        mFilteredItems.addAll(pendingIcons);
                    }
                    lastHeader = item.headerTitle;
                    pendingIcons = new ArrayList<>();
                } else {
                    String matchText = item.entry.label.toLowerCase();
                    String matchDrawable = item.entry.drawableName.toLowerCase();
                    if (matchText.contains(lowerQuery)
                            || matchDrawable.contains(lowerQuery)) {
                        pendingIcons.add(item);
                    }
                }
            }
            // Flush last category
            if (lastHeader != null && !pendingIcons.isEmpty()) {
                mFilteredItems.add(new ListItem(lastHeader));
                mFilteredItems.addAll(pendingIcons);
            }
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void onIconSelected(IconPack.IconEntry entry) {
        if (mComponentName == null) return;

        PerAppIconOverrideManager overrideMgr =
                PerAppIconOverrideManager.getInstance(requireContext());
        IconOverride override = new IconOverride(mPackPackage, entry.drawableName);

        if (mIsHome) {
            overrideMgr.setHomeOverride(mComponentName, override);
        } else {
            overrideMgr.setDrawerOverride(mComponentName, override);
        }

        // Refresh icons
        applyOverrideChange();

        // Finish activity
        requireActivity().finish();
    }

    private void applyOverrideChange() {
        LauncherAppState app = LauncherAppState.INSTANCE.get(requireContext());
        Executors.MODEL_EXECUTOR.execute(() -> {
            app.getIconCache().clearAllIcons();
            DrawerIconResolver.getInstance().invalidate();
            LauncherIcons.clearPool(requireContext());
            mMainHandler.post(() -> app.getModel().forceReload());
        });
    }

    // ---- Data model ----

    private static class ListItem {
        final int type;
        final String headerTitle;
        final IconPack.IconEntry entry;

        ListItem(String headerTitle) {
            this.type = VIEW_TYPE_HEADER;
            this.headerTitle = headerTitle;
            this.entry = null;
        }

        ListItem(IconPack.IconEntry entry) {
            this.type = VIEW_TYPE_ICON;
            this.headerTitle = null;
            this.entry = entry;
        }
    }

    // ---- Adapter ----

    private class IconGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return mFilteredItems.get(position).type;
        }

        @Override
        public int getItemCount() {
            return mFilteredItems.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_icon_picker_header, parent, false);
                return new HeaderHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_icon_picker, parent, false);
                return new IconHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = mFilteredItems.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).title.setText(item.headerTitle);
            } else if (holder instanceof IconHolder) {
                IconHolder iconHolder = (IconHolder) holder;
                iconHolder.image.setImageDrawable(null);
                iconHolder.image.setTag(item.entry.drawableName);

                iconHolder.itemView.setOnClickListener(v -> onIconSelected(item.entry));
                iconHolder.itemView.setContentDescription(item.entry.label);

                // Load icon async
                String drawableName = item.entry.drawableName;
                Executors.MODEL_EXECUTOR.execute(() -> {
                    IconPackManager mgr = LauncherComponentProvider.get(requireContext())
                            .getIconPackManager();
                    IconPack pack = mgr.getPack(mPackPackage);
                    if (pack == null) return;

                    Drawable d = pack.getDrawableForEntry(drawableName,
                            requireContext().getPackageManager());
                    mMainHandler.post(() -> {
                        // Tag-based cancellation: only set if still the same item
                        if (drawableName.equals(iconHolder.image.getTag())) {
                            iconHolder.image.setImageDrawable(d);
                        }
                    });
                });
            }
        }
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.category_title);
        }
    }

    private static class IconHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        IconHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.icon_image);
        }
    }
}
