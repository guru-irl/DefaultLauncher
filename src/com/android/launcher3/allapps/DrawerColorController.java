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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;

/**
 * Owns all paint state for the all-apps drawer container:
 *   - bottom-sheet background color + alpha
 *   - header protection color + tabs protection alpha
 *   - nav-bar scrim paint
 *   - custom tab colors
 *
 * Extracted from {@link ActivityAllAppsContainerView} by T3.1 Phase 1
 * (docs/plans/004-drawer-decomposition-v2.md).
 *
 * <p>Construction order: create in the container's constructor, call
 * {@link #onAttach(ActivityAllAppsContainerView, ScrimView)} in
 * {@code onAttachedToWindow}, {@link #onDetach()} in
 * {@code onDetachedFromWindow}. {@link #onScrimViewChanged(ScrimView)}
 * is called from the container's {@code setScrimView()} path.
 *
 * <p>The container retains the actual canvas-drawing code
 * ({@code drawOnScrimWithScaleAndBottomOffset}) and reads state via
 * the getters on this class.
 */
public class DrawerColorController {

    private final Context mContext;
    private final ActivityContext mActivityContext;
    private final int mScrimColor;
    private final int mHeaderProtectionColor;

    private final Paint mHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNavBarScrimPaint;

    private int mHeaderColor;
    private int mBottomSheetBackgroundColor;
    private float mBottomSheetBackgroundAlpha = 1f;
    private int mTabsProtectionAlpha;

    @Nullable private ActivityAllAppsContainerView<?> mHost;
    @Nullable private ScrimView mScrimView;

    public <T extends Context & ActivityContext> DrawerColorController(
            Context context, T activityContext) {
        mContext = context;
        mActivityContext = activityContext; // stored as ActivityContext interface
        mScrimColor = Themes.getAttrColor(context, R.attr.allAppsScrimColor);
        mHeaderProtectionColor = Themes.getAttrColor(
                context, R.attr.allappsHeaderProtectionColor);
        mNavBarScrimPaint = new Paint();
        // getNavBarScrimColor requires T extends Context & ActivityContext; use the typed param.
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(activityContext));
    }

    /** Called from the container's {@code onAttachedToWindow}. */
    public void onAttach(
            ActivityAllAppsContainerView<?> host, @Nullable ScrimView scrimView) {
        mHost = host;
        mScrimView = scrimView;
    }

    /** Called from the container's {@code onDetachedFromWindow}. */
    public void onDetach() {
        mHost = null;
    }

    /** Updates the ScrimView reference when the container's scrim changes. */
    public void onScrimViewChanged(@Nullable ScrimView scrimView) {
        mScrimView = scrimView;
        refresh();
    }

    // -----------------------------------------------------------
    // Color refresh — replaces refreshCustomColors()
    // -----------------------------------------------------------

    /** Recomputes background and scrim colors from current prefs. */
    public void refresh() {
        // Default background (follows the allAppsBlur flag path)
        if (true /*Flags.allAppsBlur()*/) {
            int resId = Utilities.isDarkTheme(mContext)
                    ? android.R.color.system_accent1_800
                    : android.R.color.system_accent1_100;
            int layerAbove = ColorUtils.setAlphaComponent(
                    mContext.getResources().getColor(resId, null), (int) (0.4f * 255));
            int layerBelow = ColorUtils.setAlphaComponent(
                    Color.WHITE, (int) (0.1f * 255));
            mBottomSheetBackgroundColor = ColorUtils.compositeColors(layerAbove, layerBelow);
        } else {
            mBottomSheetBackgroundColor = mContext.getColor(R.color.materialColorSurfaceDim);
        }

        // Custom color override
        String customBgColor = LauncherPrefs.get(mContext).get(LauncherPrefs.DRAWER_BG_COLOR);
        int resolvedBg = AllAppsColorResolver.resolveColorByName(mContext, customBgColor);
        if (resolvedBg != 0) {
            mBottomSheetBackgroundColor = resolvedBg;
        }

        // Custom opacity
        int opacity = LauncherPrefs.get(mContext).get(LauncherPrefs.DRAWER_BG_OPACITY);
        if (opacity < 100) {
            mBottomSheetBackgroundColor = ColorUtils.setAlphaComponent(
                    mBottomSheetBackgroundColor, (int) (opacity / 100f * 255));
        }
        mBottomSheetBackgroundAlpha = Color.alpha(mBottomSheetBackgroundColor) / 255.0f;

        // Phone: update ScrimView background to match custom color
        if (mScrimView != null && !mActivityContext.getDeviceProfile().isTablet) {
            int phoneColor;
            if (resolvedBg != 0) {
                phoneColor = resolvedBg;
            } else {
                phoneColor = Themes.getAttrColor(mContext, R.attr.allAppsScrimColor);
            }
            if (opacity < 100) {
                phoneColor = ColorUtils.setAlphaComponent(
                        phoneColor, (int) (opacity / 100f * 255));
            }
            mScrimView.setBackgroundColor(phoneColor);
        }

        if (mHost != null) {
            mHost.invalidateHeader();
        }
    }

    // -----------------------------------------------------------
    // Tab colors — replaces applyCustomTabColors() + applyTabBackground()
    // -----------------------------------------------------------

    /** Applies custom tab colors to the personal and work tab views. */
    public void applyTabColors(View personalTab, View workTab) {
        if (personalTab == null || workTab == null) return;

        String selectedName = LauncherPrefs.get(mContext)
                .get(LauncherPrefs.DRAWER_TAB_SELECTED_COLOR);
        String unselectedName = LauncherPrefs.get(mContext)
                .get(LauncherPrefs.DRAWER_TAB_UNSELECTED_COLOR);

        int selectedColor = AllAppsColorResolver.resolveColorByName(mContext, selectedName);
        int unselectedColor = AllAppsColorResolver.resolveColorByName(mContext, unselectedName);

        if (selectedColor == 0 && unselectedColor == 0) {
            // Both default — restore original drawable backgrounds
            android.graphics.drawable.Drawable original =
                    mContext.getDrawable(R.drawable.all_apps_tabs_background);
            personalTab.setBackground(original);
            workTab.setBackground(original.getConstantState().newDrawable().mutate());
            return;
        }
        applyTabBackground(personalTab, selectedColor, unselectedColor);
        applyTabBackground(workTab, selectedColor, unselectedColor);
    }

    private void applyTabBackground(View tab, int selectedColor, int unselectedColor) {
        float density = tab.getResources().getDisplayMetrics().density;
        float cornerRadius = tab.getResources()
                .getDimension(R.dimen.all_apps_header_pill_corner_radius);
        int hInset = tab.getResources().getDimensionPixelSize(
                R.dimen.all_apps_tabs_focus_horizontal_inset);
        int vInset = tab.getResources().getDimensionPixelSize(
                R.dimen.all_apps_tabs_focus_vertical_inset);

        int sel = selectedColor != 0 ? selectedColor
                : mContext.getColor(R.color.materialColorPrimary);
        int unsel = unselectedColor != 0 ? unselectedColor
                : mContext.getColor(R.color.materialColorSurfaceBright);

        GradientDrawable selShape = new GradientDrawable();
        selShape.setShape(GradientDrawable.RECTANGLE);
        selShape.setCornerRadius(cornerRadius);
        selShape.setColor(sel);
        InsetDrawable selInset = new InsetDrawable(selShape, hInset, vInset, hInset, vInset);

        GradientDrawable unselShape = new GradientDrawable();
        unselShape.setShape(GradientDrawable.RECTANGLE);
        unselShape.setCornerRadius(cornerRadius);
        unselShape.setColor(unsel);
        unselShape.setStroke((int) (1 * density),
                mContext.getColor(R.color.materialColorOutlineVariant));
        InsetDrawable unselInset = new InsetDrawable(unselShape, hInset, vInset, hInset, vInset);

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_selected}, selInset);
        stateList.addState(new int[]{}, unselInset);
        tab.setBackground(stateList);
    }

    // -----------------------------------------------------------
    // Header color — replaces getHeaderColor() + mHeaderColor update
    // -----------------------------------------------------------

    /**
     * Computes the blended header protection color for the given scroll ratio.
     * Matches the formula previously in ActivityAllAppsContainerView.getHeaderColor().
     */
    public int getHeaderColor(float blendRatio) {
        float searchAlpha = (mHost != null && mHost.mSearchContainer != null)
                ? mHost.mSearchContainer.getAlpha() : 1f;
        return ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(mScrimColor, mHeaderProtectionColor, blendRatio),
                (int) (searchAlpha * 255));
    }

    /**
     * Caches the derived header color and tabs protection alpha.
     *
     * @return true if either value changed (caller should call invalidateHeader).
     */
    public boolean updateHeaderColorState(int headerColor, int tabsAlpha) {
        if (headerColor == mHeaderColor && tabsAlpha == mTabsProtectionAlpha) {
            return false;
        }
        mHeaderColor = headerColor;
        mTabsProtectionAlpha = tabsAlpha;
        return true;
    }

    // -----------------------------------------------------------
    // Getters / setters for draw-time state
    // -----------------------------------------------------------

    public Paint getHeaderPaint() {
        return mHeaderPaint;
    }

    public Paint getNavBarScrimPaint() {
        return mNavBarScrimPaint;
    }

    public int getBottomSheetBackgroundColor() {
        return mBottomSheetBackgroundColor;
    }

    public float getBottomSheetBackgroundAlpha() {
        return mBottomSheetBackgroundAlpha;
    }

    public int getTabsProtectionAlpha() {
        return mTabsProtectionAlpha;
    }

    public void setTabsProtectionAlpha(int alpha) {
        mTabsProtectionAlpha = alpha;
    }

    public int getHeaderColor() {
        return mHeaderColor;
    }

    public int getScrimColor() {
        return mScrimColor;
    }
}
