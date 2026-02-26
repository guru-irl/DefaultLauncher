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
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.DrawerIconResolver;
import com.android.launcher3.icons.IconPackDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.PerAppHomeIconResolver;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager;
import com.android.launcher3.icons.pack.PerAppIconOverrideManager.IconOverride;
import com.android.launcher3.util.Executors;

/**
 * Per-app icon customization screen. Two sections: Home screen and App drawer.
 * Each section has: icon picker, match global/home toggle, adaptive shape switch,
 * icon shape picker, icon size toggle, and a reset button.
 */
public class AppCustomizeFragment extends SettingsBaseFragment {

    public static final String EXTRA_COMPONENT_NAME = "customize_component_name";
    public static final String EXTRA_APP_LABEL = "customize_app_label";

    // Home section keys
    private static final String KEY_HOME_CATEGORY = "customize_home_category";
    private static final String KEY_HOME_ICON = "customize_home_icon";
    private static final String KEY_HOME_MATCH_GLOBAL = "customize_home_match_global";
    private static final String KEY_HOME_ADAPTIVE = "customize_home_adaptive";
    private static final String KEY_HOME_SHAPE = "customize_home_shape";
    private static final String KEY_HOME_SIZE = "customize_home_size";
    private static final String KEY_HOME_RESET = "customize_home_reset";

    // Drawer section keys
    private static final String KEY_DRAWER_CATEGORY = "customize_drawer_category";
    private static final String KEY_DRAWER_ICON = "customize_drawer_icon";
    private static final String KEY_DRAWER_MATCH_HOME = "customize_drawer_match_home";
    private static final String KEY_DRAWER_ADAPTIVE = "customize_drawer_adaptive";
    private static final String KEY_DRAWER_SHAPE = "customize_drawer_shape";
    private static final String KEY_DRAWER_SIZE = "customize_drawer_size";
    private static final String KEY_DRAWER_RESET = "customize_drawer_reset";

    private static final String KEY_FOOTER = "customize_footer";

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private ComponentName mComponentName;
    private String mAppLabel;

    private boolean mHomeSizeBound = false;
    private boolean mDrawerSizeBound = false;
    private int mHomeLastPresetId = View.NO_ID;
    private int mDrawerLastPresetId = View.NO_ID;

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

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));

        PerAppIconOverrideManager overrideMgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride homeOverride = overrideMgr.getHomeOverride(mComponentName);
        IconOverride drawerOverride = overrideMgr.getDrawerOverride(mComponentName);

        // ── Home screen section ──
        PreferenceCategory homeCat = new PreferenceCategory(ctx);
        homeCat.setKey(KEY_HOME_CATEGORY);
        homeCat.setTitle(R.string.customize_home_category);
        homeCat.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(homeCat);

        // Home icon picker
        Preference homeIconPref = new Preference(ctx);
        homeIconPref.setKey(KEY_HOME_ICON);
        homeIconPref.setTitle(R.string.customize_home_icon);
        homeIconPref.setWidgetLayoutResource(R.layout.preference_icon_widget);
        homeIconPref.setIconSpaceReserved(false);
        homeCat.addPreference(homeIconPref);

        // Match global shape (home)
        boolean homeHasRenderOverride = homeOverride != null
                && homeOverride.hasAnyRenderOverride();
        SwitchPreferenceCompat homeMatchGlobal = new SwitchPreferenceCompat(ctx);
        homeMatchGlobal.setKey(KEY_HOME_MATCH_GLOBAL);
        homeMatchGlobal.setTitle(R.string.customize_match_global_shape);
        homeMatchGlobal.setChecked(!homeHasRenderOverride);
        homeMatchGlobal.setIconSpaceReserved(false);
        homeCat.addPreference(homeMatchGlobal);

        // Apply adaptive shape (home per-app)
        boolean homeAdaptiveOn = getEffectiveAdaptive(homeOverride, true);
        SwitchPreferenceCompat homeAdaptivePref = new SwitchPreferenceCompat(ctx);
        homeAdaptivePref.setKey(KEY_HOME_ADAPTIVE);
        homeAdaptivePref.setTitle(R.string.apply_adaptive_shape_title);
        homeAdaptivePref.setSummary(R.string.apply_adaptive_shape_summary);
        homeAdaptivePref.setChecked(homeAdaptiveOn);
        homeAdaptivePref.setVisible(!homeMatchGlobal.isChecked());
        homeAdaptivePref.setIconSpaceReserved(false);
        homeCat.addPreference(homeAdaptivePref);

        // Icon shape (home per-app)
        Preference homeShapePref = new Preference(ctx);
        homeShapePref.setKey(KEY_HOME_SHAPE);
        homeShapePref.setTitle(R.string.icon_shape_title);
        homeShapePref.setSummary(getShapeSummary(ctx, homeOverride, true));
        homeShapePref.setVisible(!homeMatchGlobal.isChecked() && homeAdaptiveOn);
        homeShapePref.setIconSpaceReserved(false);
        homeCat.addPreference(homeShapePref);

        // Icon size (home per-app)
        Preference homeSizePref = new Preference(ctx);
        homeSizePref.setKey(KEY_HOME_SIZE);
        homeSizePref.setTitle(R.string.icon_size_title);
        homeSizePref.setSummary(getSizeSummary(ctx, homeOverride, true));
        homeSizePref.setLayoutResource(R.layout.preference_icon_size);
        homeSizePref.setVisible(!homeMatchGlobal.isChecked());
        homeSizePref.setIconSpaceReserved(false);
        homeCat.addPreference(homeSizePref);

        // Reset home
        Preference homeResetPref = new Preference(ctx);
        homeResetPref.setKey(KEY_HOME_RESET);
        homeResetPref.setTitle(R.string.customize_reset_home);
        homeResetPref.setVisible(homeOverride != null);
        homeResetPref.setIconSpaceReserved(false);
        homeCat.addPreference(homeResetPref);

        // ── App drawer section ──
        PreferenceCategory drawerCat = new PreferenceCategory(ctx);
        drawerCat.setKey(KEY_DRAWER_CATEGORY);
        drawerCat.setTitle(R.string.customize_drawer_category);
        drawerCat.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(drawerCat);

        // Drawer icon picker
        Preference drawerIconPref = new Preference(ctx);
        drawerIconPref.setKey(KEY_DRAWER_ICON);
        drawerIconPref.setTitle(R.string.customize_drawer_icon);
        drawerIconPref.setWidgetLayoutResource(R.layout.preference_icon_widget);
        drawerIconPref.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerIconPref);

        // Match home shape (drawer)
        boolean drawerHasRenderOverride = drawerOverride != null
                && drawerOverride.hasAnyRenderOverride();
        SwitchPreferenceCompat drawerMatchHome = new SwitchPreferenceCompat(ctx);
        drawerMatchHome.setKey(KEY_DRAWER_MATCH_HOME);
        drawerMatchHome.setTitle(R.string.customize_match_home_shape);
        drawerMatchHome.setChecked(!drawerHasRenderOverride);
        drawerMatchHome.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerMatchHome);

        // Apply adaptive shape (drawer per-app)
        boolean drawerAdaptiveOn = getEffectiveAdaptive(drawerOverride, false);
        SwitchPreferenceCompat drawerAdaptivePref = new SwitchPreferenceCompat(ctx);
        drawerAdaptivePref.setKey(KEY_DRAWER_ADAPTIVE);
        drawerAdaptivePref.setTitle(R.string.apply_adaptive_shape_title);
        drawerAdaptivePref.setSummary(R.string.apply_adaptive_shape_summary);
        drawerAdaptivePref.setChecked(drawerAdaptiveOn);
        drawerAdaptivePref.setVisible(!drawerMatchHome.isChecked());
        drawerAdaptivePref.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerAdaptivePref);

        // Icon shape (drawer per-app)
        Preference drawerShapePref = new Preference(ctx);
        drawerShapePref.setKey(KEY_DRAWER_SHAPE);
        drawerShapePref.setTitle(R.string.icon_shape_title);
        drawerShapePref.setSummary(getShapeSummary(ctx, drawerOverride, false));
        drawerShapePref.setVisible(!drawerMatchHome.isChecked() && drawerAdaptiveOn);
        drawerShapePref.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerShapePref);

        // Icon size (drawer per-app)
        Preference drawerSizePref = new Preference(ctx);
        drawerSizePref.setKey(KEY_DRAWER_SIZE);
        drawerSizePref.setTitle(R.string.icon_size_title);
        drawerSizePref.setSummary(getSizeSummary(ctx, drawerOverride, false));
        drawerSizePref.setLayoutResource(R.layout.preference_icon_size);
        drawerSizePref.setVisible(!drawerMatchHome.isChecked());
        drawerSizePref.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerSizePref);

        // Reset drawer
        Preference drawerResetPref = new Preference(ctx);
        drawerResetPref.setKey(KEY_DRAWER_RESET);
        drawerResetPref.setTitle(R.string.customize_reset_drawer);
        drawerResetPref.setVisible(drawerOverride != null);
        drawerResetPref.setIconSpaceReserved(false);
        drawerCat.addPreference(drawerResetPref);

        // ── Footer ──
        Preference footerPref = new Preference(ctx);
        footerPref.setKey(KEY_FOOTER);
        footerPref.setSummary(mComponentName.flattenToShortString());
        footerPref.setSelectable(false);
        footerPref.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(footerPref);

        // Update icon summaries
        updateIconSummaries();

        // ── Click handlers ──

        // Home icon picker
        homeIconPref.setOnPreferenceClickListener(p -> {
            showPerAppPackFlow(true);
            return true;
        });

        // Home match global toggle
        homeMatchGlobal.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean matching = (boolean) newValue;
            if (!matching) {
                // Initialize override from current global home settings
                initializeRenderFromGlobal(true);
                boolean globalAdaptive = LauncherPrefs.get(ctx)
                        .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE);
                homeAdaptivePref.setChecked(globalAdaptive);
                String globalShape = getGlobalShapeKey(true);
                homeShapePref.setSummary(IconSettingsHelper.getShapeDisplayName(
                        ctx, globalShape));
                String globalSize = LauncherPrefs.get(ctx)
                        .get(LauncherPrefs.ICON_SIZE_SCALE);
                homeSizePref.setSummary(
                        IconSettingsHelper.getIconSizeSummary(ctx, globalSize));
            } else {
                clearRenderOverride(true);
            }
            homeAdaptivePref.setVisible(!matching);
            homeShapePref.setVisible(!matching && homeAdaptivePref.isChecked());
            homeSizePref.setVisible(!matching);
            mHomeSizeBound = false;
            updateResetVisibility(true);
            invalidateCardDecorations();
            return true;
        });

        // Home adaptive toggle
        homeAdaptivePref.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean on = (boolean) newValue;
            homeShapePref.setVisible(on);
            saveRenderOverride(true, on);
            invalidateCardDecorations();
            return true;
        });

        // Home shape picker
        homeShapePref.setOnPreferenceClickListener(pref -> {
            showPerAppShapeDialog(true, pref);
            return true;
        });

        // Home reset
        homeResetPref.setOnPreferenceClickListener(pref -> {
            PerAppIconOverrideManager.getInstance(ctx)
                    .setHomeOverride(mComponentName, null);
            homeMatchGlobal.setChecked(true);
            homeAdaptivePref.setVisible(false);
            homeShapePref.setVisible(false);
            homeSizePref.setVisible(false);
            pref.setVisible(false);
            applyOverrideChange();
            updateIconSummaries();
            refreshAllIconPreviews();
            invalidateCardDecorations();
            return true;
        });

        // Drawer icon picker
        drawerIconPref.setOnPreferenceClickListener(p -> {
            showPerAppPackFlow(false);
            return true;
        });

        // Drawer match home toggle
        drawerMatchHome.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean matching = (boolean) newValue;
            if (!matching) {
                // Initialize override from current global drawer settings
                initializeRenderFromGlobal(false);
                boolean globalAdaptive = LauncherPrefs.get(ctx)
                        .get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
                drawerAdaptivePref.setChecked(globalAdaptive);
                String globalShape = getGlobalShapeKey(false);
                drawerShapePref.setSummary(IconSettingsHelper.getShapeDisplayName(
                        ctx, globalShape));
                String globalSize = LauncherPrefs.get(ctx)
                        .get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER);
                drawerSizePref.setSummary(
                        IconSettingsHelper.getIconSizeSummary(ctx, globalSize));
            } else {
                clearRenderOverride(false);
            }
            drawerAdaptivePref.setVisible(!matching);
            drawerShapePref.setVisible(!matching && drawerAdaptivePref.isChecked());
            drawerSizePref.setVisible(!matching);
            mDrawerSizeBound = false;
            updateResetVisibility(false);
            invalidateCardDecorations();
            return true;
        });

        // Drawer adaptive toggle
        drawerAdaptivePref.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean on = (boolean) newValue;
            drawerShapePref.setVisible(on);
            saveRenderOverride(false, on);
            invalidateCardDecorations();
            return true;
        });

        // Drawer shape picker
        drawerShapePref.setOnPreferenceClickListener(pref -> {
            showPerAppShapeDialog(false, pref);
            return true;
        });

        // Drawer reset
        drawerResetPref.setOnPreferenceClickListener(pref -> {
            PerAppIconOverrideManager.getInstance(ctx)
                    .setDrawerOverride(mComponentName, null);
            drawerMatchHome.setChecked(true);
            drawerAdaptivePref.setVisible(false);
            drawerShapePref.setVisible(false);
            drawerSizePref.setVisible(false);
            pref.setVisible(false);
            applyOverrideChange();
            updateIconSummaries();
            refreshAllIconPreviews();
            invalidateCardDecorations();
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = getListView();
        rv.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                // Load icon previews
                ImageView preview = child.findViewById(R.id.app_icon_preview);
                if (preview != null) {
                    loadIconPreviewForRow(child);
                    return;
                }
                // Bind icon size toggles
                if (child.findViewById(R.id.size_toggle_group) != null) {
                    bindSizeToggleForRow(child);
                    return;
                }
                // Style footer
                styleFooterIfNeeded(child);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.setTag(null);
                view.setTag(R.id.app_icon_preview, null);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateIconSummaries();
        refreshAllIconPreviews();
    }

    // ── Size toggle binding ──

    private void bindSizeToggleForRow(View child) {
        RecyclerView rv = getListView();
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(child);
        int adapterPos = holder.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;

        Preference pref = findPreferenceAtPosition(adapterPos);
        if (pref == null) return;

        if (KEY_HOME_SIZE.equals(pref.getKey()) && !mHomeSizeBound) {
            mHomeSizeBound = true;
            bindIconSizeInline(child, true, pref);
        } else if (KEY_DRAWER_SIZE.equals(pref.getKey()) && !mDrawerSizeBound) {
            mDrawerSizeBound = true;
            bindIconSizeInline(child, false, pref);
        }
    }

    private void bindIconSizeInline(View child, boolean isHome, Preference sizePref) {
        Context ctx = getContext();
        if (ctx == null) return;

        String currentValue = getEffectiveSize(
                isHome ? getHomeOverride() : getDrawerOverride(), isHome);

        int lastId = IconSettingsHelper.bindIconSizeToggle(child, currentValue,
                value -> {
                    savePerAppSize(isHome, value);
                    sizePref.setSummary(IconSettingsHelper.getIconSizeSummary(ctx, value));
                    applyOverrideChange();
                    refreshAllIconPreviews();
                },
                () -> {
                    String currentVal = getEffectiveSize(
                            isHome ? getHomeOverride() : getDrawerOverride(), isHome);
                    IconSettingsHelper.showCustomIconSizeDialog(
                            getContext(),
                            child.findViewById(R.id.size_toggle_group),
                            currentVal,
                            isHome ? mHomeLastPresetId : mDrawerLastPresetId,
                            value -> {
                                savePerAppSize(isHome, value);
                                sizePref.setSummary(IconSettingsHelper.getIconSizeSummary(
                                        getContext(), value));
                                applyOverrideChange();
                                refreshAllIconPreviews();
                            });
                });

        if (isHome) {
            mHomeLastPresetId = lastId;
        } else {
            mDrawerLastPresetId = lastId;
        }
    }

    // ── Per-app shape picker ──

    private void showPerAppShapeDialog(boolean isHome, Preference shapePref) {
        IconSettingsHelper.showPerAppShapeDialog(this, mComponentName, isHome,
                shapeKey -> {
                    savePerAppShape(isHome, shapeKey);
                    shapePref.setSummary(IconSettingsHelper.getShapeDisplayName(
                            getContext(), shapeKey));
                    applyOverrideChange();
                    refreshAllIconPreviews();
                });
    }

    // ── Render override save/clear ──

    /** Save the current UI state of adaptive + shape + size into the override. */
    private void saveRenderOverride(boolean isHome, boolean newAdaptiveValue) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        SwitchPreferenceCompat matchPref = findPreference(
                isHome ? KEY_HOME_MATCH_GLOBAL : KEY_DRAWER_MATCH_HOME);
        if (matchPref != null && matchPref.isChecked()) return;

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride current = isHome
                ? mgr.getHomeOverride(mComponentName)
                : mgr.getDrawerOverride(mComponentName);

        String packPkg = current != null ? current.packPackage
                : IconOverride.PackSource.FOLLOW_GLOBAL.key;
        String drawable = current != null ? current.drawableName : IconOverride.FOLLOW_GLOBAL;
        String adaptive = String.valueOf(newAdaptiveValue);
        String shape = current != null ? current.shapeKey : IconOverride.FOLLOW_GLOBAL;
        String size = current != null ? current.sizeScale : IconOverride.FOLLOW_GLOBAL;

        IconOverride updated = new IconOverride(packPkg, drawable, shape, size, adaptive);
        if (isHome) {
            mgr.setHomeOverride(mComponentName, updated);
        } else {
            mgr.setDrawerOverride(mComponentName, updated);
        }

        Preference resetPref = findPreference(
                isHome ? KEY_HOME_RESET : KEY_DRAWER_RESET);
        if (resetPref != null) resetPref.setVisible(true);

        applyOverrideChange();
        refreshAllIconPreviews();
    }

    private void savePerAppShape(boolean isHome, String shapeKey) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride current = isHome
                ? mgr.getHomeOverride(mComponentName)
                : mgr.getDrawerOverride(mComponentName);

        String packPkg = current != null ? current.packPackage
                : IconOverride.PackSource.FOLLOW_GLOBAL.key;
        String drawable = current != null ? current.drawableName : IconOverride.FOLLOW_GLOBAL;
        String adaptive = current != null ? current.adaptiveShape
                : IconOverride.AdaptiveOverride.ON.key;
        String size = current != null ? current.sizeScale : IconOverride.FOLLOW_GLOBAL;

        IconOverride updated = new IconOverride(packPkg, drawable, shapeKey, size, adaptive);
        if (isHome) {
            mgr.setHomeOverride(mComponentName, updated);
        } else {
            mgr.setDrawerOverride(mComponentName, updated);
        }

        Preference resetPref = findPreference(
                isHome ? KEY_HOME_RESET : KEY_DRAWER_RESET);
        if (resetPref != null) resetPref.setVisible(true);
    }

    private void savePerAppSize(boolean isHome, String sizeValue) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride current = isHome
                ? mgr.getHomeOverride(mComponentName)
                : mgr.getDrawerOverride(mComponentName);

        String packPkg = current != null ? current.packPackage
                : IconOverride.PackSource.FOLLOW_GLOBAL.key;
        String drawable = current != null ? current.drawableName : IconOverride.FOLLOW_GLOBAL;
        String adaptive = current != null ? current.adaptiveShape
                : IconOverride.AdaptiveOverride.FOLLOW_GLOBAL.key;
        String shape = current != null ? current.shapeKey : IconOverride.FOLLOW_GLOBAL;

        IconOverride updated = new IconOverride(packPkg, drawable, shape, sizeValue, adaptive);
        if (isHome) {
            mgr.setHomeOverride(mComponentName, updated);
        } else {
            mgr.setDrawerOverride(mComponentName, updated);
        }

        Preference resetPref = findPreference(
                isHome ? KEY_HOME_RESET : KEY_DRAWER_RESET);
        if (resetPref != null) resetPref.setVisible(true);
    }

    /** Clear render overrides but preserve the pack/drawable override. */
    private void clearRenderOverride(boolean isHome) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride current = isHome
                ? mgr.getHomeOverride(mComponentName)
                : mgr.getDrawerOverride(mComponentName);

        if (current == null) return;

        if (current.isFollowGlobalPack() && !current.hasSpecificDrawable()) {
            // No pack override either — remove entirely
            if (isHome) {
                mgr.setHomeOverride(mComponentName, null);
            } else {
                mgr.setDrawerOverride(mComponentName, null);
            }
        } else {
            IconOverride cleaned = current.withoutRenderOverrides();
            if (isHome) {
                mgr.setHomeOverride(mComponentName, cleaned);
            } else {
                mgr.setDrawerOverride(mComponentName, cleaned);
            }
        }

        applyOverrideChange();
        refreshAllIconPreviews();
    }

    // ── Helpers ──

    @Nullable
    private IconOverride getHomeOverride() {
        if (mComponentName == null) return null;
        return PerAppIconOverrideManager.getInstance(getContext())
                .getHomeOverride(mComponentName);
    }

    @Nullable
    private IconOverride getDrawerOverride() {
        if (mComponentName == null) return null;
        return PerAppIconOverrideManager.getInstance(getContext())
                .getDrawerOverride(mComponentName);
    }

    private boolean getEffectiveAdaptive(@Nullable IconOverride override, boolean isHome) {
        if (override != null && override.hasAdaptiveOverride()) {
            Boolean val = override.getAdaptiveShapeBool();
            return val != null ? val : true;
        }
        // Fall back to global setting
        return LauncherPrefs.get(getContext()).get(
                isHome ? LauncherPrefs.APPLY_ADAPTIVE_SHAPE
                       : LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
    }

    private String getEffectiveSize(@Nullable IconOverride override, boolean isHome) {
        if (override != null && override.hasSizeOverride()) {
            return override.sizeScale;
        }
        return LauncherPrefs.get(getContext()).get(
                isHome ? LauncherPrefs.ICON_SIZE_SCALE
                       : LauncherPrefs.ICON_SIZE_SCALE_DRAWER);
    }

    private String getShapeSummary(Context ctx, @Nullable IconOverride override,
            boolean isHome) {
        if (override != null && override.hasShapeOverride()) {
            return IconSettingsHelper.getShapeDisplayName(ctx, override.shapeKey).toString();
        }
        return ctx.getString(R.string.icon_shape_default);
    }

    private String getSizeSummary(Context ctx, @Nullable IconOverride override,
            boolean isHome) {
        String value = getEffectiveSize(override, isHome);
        return IconSettingsHelper.getIconSizeSummary(ctx, value);
    }

    /** Create a per-app override initialized with the current global settings. */
    private void initializeRenderFromGlobal(boolean isHome) {
        Context ctx = getContext();
        if (ctx == null || mComponentName == null) return;

        LauncherPrefs prefs = LauncherPrefs.get(ctx);
        boolean globalAdaptive = prefs.get(
                isHome ? LauncherPrefs.APPLY_ADAPTIVE_SHAPE
                       : LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER);
        String globalShape = getGlobalShapeKey(isHome);
        String globalSize = prefs.get(
                isHome ? LauncherPrefs.ICON_SIZE_SCALE
                       : LauncherPrefs.ICON_SIZE_SCALE_DRAWER);

        PerAppIconOverrideManager mgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride current = isHome
                ? mgr.getHomeOverride(mComponentName)
                : mgr.getDrawerOverride(mComponentName);

        String packPkg = current != null ? current.packPackage
                : IconOverride.PackSource.FOLLOW_GLOBAL.key;
        String drawable = current != null ? current.drawableName : IconOverride.FOLLOW_GLOBAL;
        String adaptiveStr = globalAdaptive
                ? IconOverride.AdaptiveOverride.ON.key
                : IconOverride.AdaptiveOverride.OFF.key;

        IconOverride override = new IconOverride(packPkg, drawable,
                globalShape, globalSize, adaptiveStr);
        if (isHome) {
            mgr.setHomeOverride(mComponentName, override);
        } else {
            mgr.setDrawerOverride(mComponentName, override);
        }
    }

    private String getGlobalShapeKey(boolean isHome) {
        Context ctx = getContext();
        if (ctx == null) return "";
        return LauncherPrefs.get(ctx).get(
                isHome ? ThemeManager.PREF_ICON_SHAPE
                       : ThemeManager.PREF_ICON_SHAPE_DRAWER);
    }

    @Nullable
    private Preference findPreferenceAtPosition(int position) {
        // Walk visible preferences only — the adapter skips invisible ones,
        // so adapter position N maps to the Nth VISIBLE preference.
        int count = 0;
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (!pref.isVisible()) continue;
            if (pref instanceof PreferenceCategory) {
                if (count == position) return pref;
                count++;
                PreferenceCategory cat = (PreferenceCategory) pref;
                for (int j = 0; j < cat.getPreferenceCount(); j++) {
                    Preference child = cat.getPreference(j);
                    if (!child.isVisible()) continue;
                    if (count == position) return child;
                    count++;
                }
            } else {
                if (count == position) return pref;
                count++;
            }
        }
        return null;
    }

    // ── Footer styling ──

    private void styleFooterIfNeeded(View row) {
        RecyclerView rv = getListView();
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(row);
        int adapterPos = holder.getBindingAdapterPosition();
        if (adapterPos == RecyclerView.NO_POSITION) return;

        Preference pref = findPreferenceAtPosition(adapterPos);
        if (pref == null || !KEY_FOOTER.equals(pref.getKey())) return;

        row.setTag("no_card");

        View titleView = row.findViewById(android.R.id.title);
        if (titleView != null) titleView.setVisibility(View.GONE);

        TextView summaryView = row.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            summaryView.setTextColor(
                    getContext().getColor(R.color.materialColorOnSurfaceVariant));
            summaryView.setAlpha(0.5f);
            summaryView.setGravity(Gravity.CENTER);
            int pad16 = getResources().getDimensionPixelSize(R.dimen.settings_card_padding);
            summaryView.setPaddingRelative(pad16, 0, pad16, 0);
            ViewGroup.LayoutParams lp = summaryView.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
            }
        }
    }

    // ── Icon previews ──

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

    private void invalidateCardDecorations() {
        RecyclerView rv = getListView();
        if (rv != null) rv.invalidateItemDecorations();
    }

    private void loadIconPreviewForRow(View row) {
        if (mComponentName == null) return;
        Context ctx = getContext();
        if (ctx == null) return;

        ImageView preview = row.findViewById(R.id.app_icon_preview);
        if (preview == null) return;

        // Use cached tag first (survives adapter position shifts after visibility changes).
        // Fall back to adapter position lookup on first bind.
        boolean isHome;
        Object isHomeTag = row.getTag(R.id.app_icon_preview);
        if (isHomeTag instanceof Boolean) {
            isHome = (Boolean) isHomeTag;
        } else {
            RecyclerView rv = getListView();
            RecyclerView.ViewHolder holder = rv.getChildViewHolder(row);
            int adapterPos = holder.getBindingAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;

            Preference pref = findPreferenceAtPosition(adapterPos);
            if (pref == null) return;
            String key = pref.getKey();
            if (key == null) return;

            if (KEY_HOME_ICON.equals(key)) {
                isHome = true;
            } else if (KEY_DRAWER_ICON.equals(key)) {
                isHome = false;
            } else {
                return;
            }
            row.setTag(R.id.app_icon_preview, isHome);
        }

        ProgressBar loading = row.findViewById(R.id.app_icon_loading);

        if (loading != null) loading.setVisibility(View.VISIBLE);
        preview.setVisibility(View.GONE);

        PerAppIconOverrideManager overrideMgr = PerAppIconOverrideManager.getInstance(ctx);
        IconOverride override = isHome
                ? overrideMgr.getHomeOverride(mComponentName)
                : overrideMgr.getDrawerOverride(mComponentName);

        Executors.MODEL_EXECUTOR.execute(() -> {
            Drawable icon = resolvePreviewIcon(ctx, override, isHome);
            sMainHandler.post(() -> {
                if (isAdded() && preview.isAttachedToWindow()) {
                    preview.setImageDrawable(icon);
                    preview.setVisibility(View.VISIBLE);
                    if (loading != null) loading.setVisibility(View.GONE);
                }
            });
        });
    }

    private void updateIconSummaries() {
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
            IconPack effectivePack = getEffectivePack(packMgr, isHome, ctx);
            String packName = effectivePack != null
                    ? effectivePack.label.toString()
                    : ctx.getString(R.string.icon_pack_default);
            return String.format(ctx.getString(R.string.customize_icon_global_summary), packName);
        }
        if (override.isSystemDefault()) {
            return ctx.getString(R.string.customize_icon_system_summary);
        }
        if (override.isFollowGlobalPack()) {
            IconPack effectivePack = getEffectivePack(packMgr, isHome, ctx);
            String packName = effectivePack != null
                    ? effectivePack.label.toString()
                    : ctx.getString(R.string.icon_pack_default);
            return String.format(ctx.getString(R.string.customize_icon_global_summary), packName);
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
                        updateResetVisibility(isHome);
                        applyOverrideChange();
                        updateIconSummaries();
                        refreshAllIconPreviews();
                    }

                    @Override
                    public void onSystemDefault() {
                        PerAppIconOverrideManager overrideMgr =
                                PerAppIconOverrideManager.getInstance(ctx);
                        IconOverride existing = isHome
                                ? overrideMgr.getHomeOverride(mComponentName)
                                : overrideMgr.getDrawerOverride(mComponentName);
                        // Preserve render overrides if any
                        IconOverride override = existing != null
                                ? new IconOverride(
                                        IconOverride.PackSource.SYSTEM_DEFAULT.key,
                                        IconOverride.FOLLOW_GLOBAL,
                                        existing.shapeKey, existing.sizeScale,
                                        existing.adaptiveShape)
                                : new IconOverride(IconOverride.PackSource.SYSTEM_DEFAULT);
                        if (isHome) {
                            overrideMgr.setHomeOverride(mComponentName, override);
                        } else {
                            overrideMgr.setDrawerOverride(mComponentName, override);
                        }
                        updateResetVisibility(isHome);
                        applyOverrideChange();
                        updateIconSummaries();
                        refreshAllIconPreviews();
                    }

                    @Override
                    public void onIconSelected(String packPackage, String drawableName) {
                        PerAppIconOverrideManager overrideMgr =
                                PerAppIconOverrideManager.getInstance(ctx);
                        IconOverride existing = isHome
                                ? overrideMgr.getHomeOverride(mComponentName)
                                : overrideMgr.getDrawerOverride(mComponentName);
                        // Preserve render overrides if any
                        IconOverride override = existing != null
                                ? new IconOverride(packPackage, drawableName,
                                        existing.shapeKey, existing.sizeScale,
                                        existing.adaptiveShape)
                                : new IconOverride(packPackage, drawableName);
                        if (isHome) {
                            overrideMgr.setHomeOverride(mComponentName, override);
                        } else {
                            overrideMgr.setDrawerOverride(mComponentName, override);
                        }
                        updateResetVisibility(isHome);
                        applyOverrideChange();
                        updateIconSummaries();
                        refreshAllIconPreviews();
                    }
                });
    }

    private void updateResetVisibility(boolean isHome) {
        Preference resetPref = findPreference(
                isHome ? KEY_HOME_RESET : KEY_DRAWER_RESET);
        if (resetPref != null) {
            PerAppIconOverrideManager mgr =
                    PerAppIconOverrideManager.getInstance(getContext());
            IconOverride override = isHome
                    ? mgr.getHomeOverride(mComponentName)
                    : mgr.getDrawerOverride(mComponentName);
            resetPref.setVisible(override != null);
        }
    }

    @Nullable
    private IconPack getEffectivePack(IconPackManager packMgr, boolean isHome, Context ctx) {
        if (isHome) {
            return packMgr.getCurrentPack();
        }
        boolean matchHome = LauncherPrefs.get(ctx).get(LauncherPrefs.DRAWER_MATCH_HOME);
        if (matchHome) {
            return packMgr.getCurrentPack();
        }
        if (packMgr.hasDistinctDrawerPack()) {
            return packMgr.getDrawerPack();
        }
        return packMgr.getCurrentPack();
    }

    private Drawable resolvePreviewIcon(Context ctx, IconOverride override, boolean isHome) {
        Drawable rawIcon = resolveRawIcon(ctx, override, isHome);
        if (rawIcon == null) return null;

        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(ctx);
        ThemeManager.IconState state = ThemeManager.INSTANCE.get(ctx).getIconState();
        IconOverride effectiveOverride = override != null
                ? override : new IconOverride(IconOverride.PackSource.FOLLOW_GLOBAL);

        BitmapInfo bitmapInfo;
        if (isHome) {
            try (PerAppHomeIconResolver.PerAppIconFactory factory =
                    new PerAppHomeIconResolver.PerAppIconFactory(
                            ctx, idp.fillResIconDpi, idp.iconBitmapSize,
                            effectiveOverride, state)) {
                bitmapInfo = factory.createBadgedIconBitmap(rawIcon);
            }
        } else {
            try (DrawerIconResolver.PerAppDrawerIconFactory factory =
                    new DrawerIconResolver.PerAppDrawerIconFactory(
                            ctx, idp.fillResIconDpi, idp.iconBitmapSize,
                            effectiveOverride, state)) {
                bitmapInfo = factory.createBadgedIconBitmap(rawIcon);
            }
        }
        return bitmapInfo.newIcon(ctx);
    }

    private Drawable resolveRawIcon(Context ctx, IconOverride override, boolean isHome) {
        PackageManager pm = ctx.getPackageManager();
        IconPackManager packMgr = LauncherComponentProvider.get(ctx).getIconPackManager();

        if (override != null) {
            switch (override.getPackSource()) {
                case SYSTEM_DEFAULT:
                    try { return pm.getActivityIcon(mComponentName); }
                    catch (PackageManager.NameNotFoundException e) { return null; }

                case CUSTOM:
                    IconPack pack = packMgr.getPack(override.packPackage);
                    if (pack != null) {
                        if (override.hasSpecificDrawable()) {
                            Drawable d = pack.getDrawableForEntry(
                                    override.drawableName, pm);
                            if (d != null) return IconPackDrawable.wrap(d);
                        }
                        Drawable d = pack.getIconForComponent(mComponentName, pm);
                        if (d != null) return IconPackDrawable.wrap(d);
                    }
                    break;

                case FOLLOW_GLOBAL:
                    break;
            }
        }

        // Global pack resolution
        IconPack effectivePack = getEffectivePack(packMgr, isHome, ctx);
        if (effectivePack != null) {
            Drawable d = effectivePack.getIconForComponent(mComponentName, pm);
            if (d != null) return IconPackDrawable.wrap(d);
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
            PerAppHomeIconResolver.getInstance().invalidate();
            LauncherIcons.clearPool(ctx);
            sMainHandler.post(() -> {
                if (isAdded()) app.getModel().forceReload();
            });
        });
    }

    @Override
    public void onDestroyView() {
        mHomeSizeBound = false;
        mDrawerSizeBound = false;
        sMainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
