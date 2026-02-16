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
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.util.Executors;

/**
 * Per-app icon customization screen. Shows current home/drawer icon overrides
 * and allows the user to pick a specific icon from any installed pack.
 */
public class AppCustomizeFragment extends PreferenceFragmentCompat {

    public static final String EXTRA_COMPONENT_NAME = "customize_component_name";
    public static final String EXTRA_APP_LABEL = "customize_app_label";

    private static final String KEY_HOME_ICON = "customize_home_icon";
    private static final String KEY_DRAWER_ICON = "customize_drawer_icon";
    private static final String KEY_RESET = "customize_reset";
    private static final String KEY_FOOTER = "customize_footer";

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private ComponentName mComponentName;
    private String mAppLabel;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context ctx = requireContext();
        Bundle args = getArguments();
        if (args != null) {
            String cnStr = args.getString(EXTRA_COMPONENT_NAME);
            if (cnStr != null) {
                mComponentName = ComponentName.unflattenFromString(cnStr);
            }
            mAppLabel = args.getString(EXTRA_APP_LABEL, "");
        }

        if (mComponentName == null) {
            requireActivity().finish();
            return;
        }

        // Build preferences programmatically
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));

        // Home screen icon
        Preference homePref = new Preference(ctx);
        homePref.setKey(KEY_HOME_ICON);
        homePref.setTitle(R.string.customize_home_icon);
        homePref.setWidgetLayoutResource(R.layout.preference_icon_widget);
        homePref.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(homePref);

        // App drawer icon — always visible; per-app overrides take priority
        // over the global "match home" setting.
        Preference drawerPref = new Preference(ctx);
        drawerPref.setKey(KEY_DRAWER_ICON);
        drawerPref.setTitle(R.string.customize_drawer_icon);
        drawerPref.setWidgetLayoutResource(R.layout.preference_icon_widget);
        drawerPref.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(drawerPref);

        // Reset
        Preference resetPref = new Preference(ctx);
        resetPref.setKey(KEY_RESET);
        resetPref.setTitle(R.string.customize_reset);
        resetPref.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(resetPref);

        // Component ID footer (sits outside card groups)
        Preference footerPref = new Preference(ctx);
        footerPref.setKey(KEY_FOOTER);
        footerPref.setSummary(mComponentName.flattenToShortString());
        footerPref.setSelectable(false);
        footerPref.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(footerPref);

        // Update summaries
        updateSummaries();

        // Click handlers
        homePref.setOnPreferenceClickListener(p -> {
            showPerAppPackFlow(true);
            return true;
        });

        drawerPref.setOnPreferenceClickListener(p -> {
            showPerAppPackFlow(false);
            return true;
        });

        resetPref.setOnPreferenceClickListener(p -> {
            PerAppIconOverrideManager.getInstance(ctx).clearOverrides(mComponentName);
            applyOverrideChange();
            updateSummaries();
            refreshAllIconPreviews();
            Toast.makeText(ctx, R.string.customize_icon_cleared, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);

        RecyclerView rv = getListView();
        rv.addItemDecoration(new CardGroupItemDecoration(getContext()));

        // Load icon previews and style footer when views attach
        rv.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                ImageView preview = child.findViewById(R.id.app_icon_preview);
                if (preview != null) {
                    loadIconPreviewForRow(child);
                    return;
                }
                // Style the footer row: tag as no_card, style the summary text
                styleFooterIfNeeded(child);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) { }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always re-read current state: global pack may have changed,
        // or user returned from icon picker
        updateSummaries();
        refreshAllIconPreviews();
    }

    /**
     * If this row is the component ID footer, tag it as "no_card" so
     * CardGroupItemDecoration skips it, and restyle the text.
     */
    private void styleFooterIfNeeded(View row) {
        RecyclerView rv = getListView();
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(row);
        int adapterPos = holder.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;

        Preference pref = getPreferenceScreen().getPreference(adapterPos);
        if (pref == null || !KEY_FOOTER.equals(pref.getKey())) return;

        // Exclude from card grouping
        row.setTag("no_card");

        // Hide the title row (it's empty) and restyle summary as tiny centered text
        View titleView = row.findViewById(android.R.id.title);
        if (titleView != null) titleView.setVisibility(View.GONE);

        TextView summaryView = row.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            summaryView.setTextColor(
                    getContext().getColor(R.color.materialColorOnSurfaceVariant));
            summaryView.setAlpha(0.5f);
            summaryView.setGravity(Gravity.CENTER);
            float d = getResources().getDisplayMetrics().density;
            int pad16 = (int) (16 * d);
            summaryView.setPaddingRelative(pad16, 0, pad16, 0);
            // Remove default padding constraints
            ViewGroup.LayoutParams lp = summaryView.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
            }
        }
    }

    /**
     * Walk all visible RecyclerView children and reload any icon preview widgets.
     */
    private void refreshAllIconPreviews() {
        RecyclerView rv = getListView();
        if (rv == null) return;
        rv.post(() -> {
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                if (child.findViewById(R.id.app_icon_preview) != null) {
                    loadIconPreviewForRow(child);
                }
            }
        });
    }

    /**
     * Determine which preference a row belongs to by matching the preference key
     * through the ViewHolder's adapter position, then load the correct icon preview.
     */
    private void loadIconPreviewForRow(View row) {
        if (mComponentName == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        RecyclerView rv = getListView();
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(row);
        int adapterPos = holder.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;

        // Map adapter position to preference key
        Preference pref = getPreferenceScreen().getPreference(adapterPos);
        if (pref == null) return;
        String key = pref.getKey();
        if (key == null) return;

        boolean isHome;
        if (KEY_HOME_ICON.equals(key)) {
            isHome = true;
        } else if (KEY_DRAWER_ICON.equals(key)) {
            isHome = false;
        } else {
            return; // not an icon preview row (e.g. reset, footer)
        }

        ImageView preview = row.findViewById(R.id.app_icon_preview);
        ProgressBar loading = row.findViewById(R.id.app_icon_loading);
        if (preview == null) return;

        // Show loading spinner, hide stale preview
        if (loading != null) loading.setVisibility(View.VISIBLE);
        preview.setVisibility(View.GONE);

        PerAppIconOverrideManager overrideMgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride override = isHome
                ? overrideMgr.getHomeOverride(mComponentName)
                : overrideMgr.getDrawerOverride(mComponentName);

        Executors.MODEL_EXECUTOR.execute(() -> {
            Drawable icon = resolvePreviewIcon(ctx, override, isHome);
            sMainHandler.post(() -> {
                if (preview.isAttachedToWindow()) {
                    preview.setImageDrawable(icon);
                    preview.setVisibility(View.VISIBLE);
                    if (loading != null) loading.setVisibility(View.GONE);
                }
            });
        });
    }

    private void updateSummaries() {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconPackManager packMgr = LauncherComponentProvider.get(ctx).getIconPackManager();

        Preference homePref = findPreference(KEY_HOME_ICON);
        if (homePref != null) {
            homePref.setSummary(getOverrideSummary(ctx, mgr.getHomeOverride(mComponentName),
                    packMgr, true));
        }

        Preference drawerPref = findPreference(KEY_DRAWER_ICON);
        if (drawerPref != null) {
            drawerPref.setSummary(getOverrideSummary(ctx, mgr.getDrawerOverride(mComponentName),
                    packMgr, false));
        }
    }

    private String getOverrideSummary(Context ctx, IconOverride override,
            IconPackManager packMgr, boolean isHome) {
        if (override == null) {
            // No override — show the pack that would actually be used
            IconPack effectivePack = getEffectivePack(packMgr, isHome, ctx);
            String packName = effectivePack != null
                    ? effectivePack.label.toString()
                    : ctx.getString(R.string.icon_pack_default);
            return String.format(ctx.getString(R.string.customize_icon_global_summary), packName);
        }
        if (override.isSystemDefault()) {
            return ctx.getString(R.string.customize_icon_system_summary);
        }
        IconPack pack = packMgr.getPack(override.packPackage);
        String packName = pack != null ? pack.label.toString() : override.packPackage;
        if (override.hasSpecificDrawable()) {
            return String.format(ctx.getString(R.string.customize_icon_pack_summary),
                    packName, override.drawableName);
        }
        return packName;
    }

    private void showPerAppPackFlow(boolean isHome) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        IconPackManager packMgr = LauncherComponentProvider.get(ctx).getIconPackManager();

        PerAppIconSheet.show(this, mComponentName, packMgr,
                new PerAppIconSheet.Callback() {
                    @Override
                    public void onFollowGlobal() {
                        PerAppIconOverrideManager overrideMgr =
                                PerAppIconOverrideManager.getInstance(ctx);
                        if (isHome) {
                            overrideMgr.setHomeOverride(mComponentName, null);
                        } else {
                            overrideMgr.setDrawerOverride(mComponentName, null);
                        }
                        applyOverrideChange();
                        updateSummaries();
                        refreshAllIconPreviews();
                    }

                    @Override
                    public void onSystemDefault() {
                        PerAppIconOverrideManager overrideMgr =
                                PerAppIconOverrideManager.getInstance(ctx);
                        IconOverride override = new IconOverride("", "");
                        if (isHome) {
                            overrideMgr.setHomeOverride(mComponentName, override);
                        } else {
                            overrideMgr.setDrawerOverride(mComponentName, override);
                        }
                        applyOverrideChange();
                        updateSummaries();
                        refreshAllIconPreviews();
                    }

                    @Override
                    public void onIconSelected(String packPackage, String drawableName) {
                        PerAppIconOverrideManager overrideMgr =
                                PerAppIconOverrideManager.getInstance(ctx);
                        IconOverride override = new IconOverride(packPackage, drawableName);
                        if (isHome) {
                            overrideMgr.setHomeOverride(mComponentName, override);
                        } else {
                            overrideMgr.setDrawerOverride(mComponentName, override);
                        }
                        applyOverrideChange();
                        updateSummaries();
                        refreshAllIconPreviews();
                    }
                });
    }

    /**
     * Get the icon pack that would actually be used for this context.
     * Uses the same methods as the icon resolution pipeline for consistency.
     */
    @Nullable
    private IconPack getEffectivePack(IconPackManager packMgr, boolean isHome, Context ctx) {
        if (isHome) {
            return packMgr.getCurrentPack();
        }
        // Drawer: if match-home is on, use home pack
        boolean matchHome = LauncherPrefs.get(ctx).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (matchHome) {
            return packMgr.getCurrentPack();
        }
        // Mirror actual drawer resolution: if drawer has a distinct pack setting
        // (even if empty = system default), use it; otherwise use home pack
        if (packMgr.hasDistinctDrawerPack()) {
            return packMgr.getDrawerPack(); // may be null = system default
        }
        return packMgr.getCurrentPack();
    }

    private Drawable resolvePreviewIcon(Context ctx, IconOverride override, boolean isHome) {
        PackageManager pm = ctx.getPackageManager();
        IconPackManager packMgr = LauncherComponentProvider.get(ctx).getIconPackManager();

        if (override != null) {
            if (override.isSystemDefault()) {
                try {
                    return pm.getActivityIcon(mComponentName);
                } catch (PackageManager.NameNotFoundException e) {
                    return null;
                }
            }
            IconPack pack = packMgr.getPack(override.packPackage);
            if (pack != null) {
                if (override.hasSpecificDrawable()) {
                    Drawable d = pack.getDrawableForEntry(override.drawableName, pm);
                    if (d != null) return d;
                }
                Drawable d = pack.getIconForComponent(mComponentName, pm);
                if (d != null) return d;
            }
        }

        // No override — use the effective pack for this context
        IconPack effectivePack = getEffectivePack(packMgr, isHome, ctx);
        if (effectivePack != null) {
            Drawable d = effectivePack.getIconForComponent(mComponentName, pm);
            if (d != null) return d;
        }
        try {
            return pm.getActivityIcon(mComponentName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void applyOverrideChange() {
        Context ctx = getContext();
        if (ctx == null) return;
        LauncherAppState app = LauncherAppState.INSTANCE.get(ctx);
        Executors.MODEL_EXECUTOR.execute(() -> {
            app.getIconCache().clearAllIcons();
            DrawerIconResolver.getInstance().invalidate();
            LauncherIcons.clearPool(ctx);
            sMainHandler.post(() -> app.getModel().forceReload());
        });
    }
}
