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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.android.launcher3.R;

import java.util.function.Consumer;

/**
 * Fluent builder for settings bottom sheets + static factory methods for
 * common UI elements (cards, search bars, sheet handles).
 *
 * <p>Typical usage:
 * <pre>
 * SheetComponents c = new SettingsSheetBuilder(ctx)
 *         .setTitle(R.string.my_title)
 *         .dismissOnDestroy(fragment)
 *         .build();
 * // populate c.contentArea
 * c.showScrollable();
 * </pre>
 */
public class SettingsSheetBuilder {

    /**
     * Components returned by {@link #build()}. Callers populate
     * {@link #contentArea}, then call {@link #showScrollable()} or add
     * content directly to {@link #root} and call {@link #show()}.
     */
    public static class SheetComponents {
        public final BottomSheetDialog sheet;
        public final LinearLayout root;
        /** Title view (null if no title was set). */
        @Nullable public final TextView titleView;
        /** Empty container for caller content. */
        public final LinearLayout contentArea;

        SheetComponents(BottomSheetDialog sheet, LinearLayout root,
                @Nullable TextView titleView, LinearLayout contentArea) {
            this.sheet = sheet;
            this.root = root;
            this.titleView = titleView;
            this.contentArea = contentArea;
        }

        /** Wraps {@link #contentArea} in a NestedScrollView, sets content, and shows. */
        public void showScrollable() {
            NestedScrollView scroll = new NestedScrollView(root.getContext());
            scroll.setClipToPadding(false);
            scroll.addView(contentArea);
            root.addView(scroll);
            sheet.setContentView(root);
            sheet.show();
        }

        /** Sets content view and shows the sheet (caller added content to root directly). */
        public void show() {
            sheet.setContentView(root);
            sheet.show();
        }
    }

    private final Context mCtx;
    private int mTitleResId;
    private CharSequence mTitleText;
    private PreferenceFragmentCompat mFragment;

    public SettingsSheetBuilder(Context ctx) {
        mCtx = ctx;
    }

    public SettingsSheetBuilder setTitle(int titleResId) {
        mTitleResId = titleResId;
        mTitleText = null;
        return this;
    }

    public SettingsSheetBuilder setTitle(CharSequence title) {
        mTitleText = title;
        mTitleResId = 0;
        return this;
    }

    public SettingsSheetBuilder dismissOnDestroy(PreferenceFragmentCompat fragment) {
        mFragment = fragment;
        return this;
    }

    public SheetComponents build() {
        Resources res = mCtx.getResources();
        BottomSheetDialog sheet = new BottomSheetDialog(mCtx);

        LinearLayout root = new LinearLayout(mCtx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal));

        addSheetHandle(root, mCtx, res);

        // Title
        TextView titleView = null;
        if (mTitleResId != 0 || mTitleText != null) {
            titleView = new TextView(mCtx);
            if (mTitleText != null) {
                titleView.setText(mTitleText);
            } else {
                titleView.setText(mTitleResId);
            }
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    res.getDimension(R.dimen.settings_sheet_title_text_size));
            titleView.setTextColor(mCtx.getColor(R.color.materialColorOnSurface));
            titleView.setPadding(
                    res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                    res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical),
                    res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal),
                    res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical));
            root.addView(titleView);
        }

        LinearLayout contentArea = new LinearLayout(mCtx);
        contentArea.setOrientation(LinearLayout.VERTICAL);

        if (mFragment != null) {
            dismissOnDestroy(mFragment, sheet);
        }

        return new SheetComponents(sheet, root, titleView, contentArea);
    }

    // ---- Static helpers ----

    /** Adds the standard M3 drag handle to the top of a sheet root layout. */
    public static void addSheetHandle(LinearLayout root, Context ctx, Resources res) {
        View handle = new View(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(res.getDimension(R.dimen.settings_stroke_width));
        bg.setColor(ctx.getColor(R.color.materialColorOutline));
        handle.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                res.getDimensionPixelSize(R.dimen.settings_handle_width),
                res.getDimensionPixelSize(R.dimen.settings_icon_padding));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = res.getDimensionPixelSize(R.dimen.settings_icon_margin_end);
        handle.setLayoutParams(lp);
        root.addView(handle);
    }

    /** Dismisses the sheet when the fragment is destroyed (prevents leaked window). */
    public static void dismissOnDestroy(PreferenceFragmentCompat fragment,
            BottomSheetDialog sheet) {
        fragment.getLifecycle().addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source,
                    @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    if (sheet.isShowing()) sheet.dismiss();
                    source.getLifecycle().removeObserver(this);
                }
            }
        });
    }

    // ---- Card factory ----

    /**
     * Creates a standard settings card with optional icon, label, ripple foreground,
     * and click listener. Matches the pattern used by icon pack and per-app dialogs.
     */
    public static LinearLayout createCard(Context ctx, String label,
            @Nullable Drawable icon, int bgColor, int textColor,
            @Nullable View.OnClickListener listener) {
        Resources res = ctx.getResources();
        int cornerPx = res.getDimensionPixelSize(R.dimen.settings_card_corner_radius);
        int marginH = res.getDimensionPixelSize(R.dimen.settings_card_margin_horizontal);
        int marginV = res.getDimensionPixelSize(R.dimen.settings_card_margin_vertical);
        int cardPad = res.getDimensionPixelSize(R.dimen.settings_card_padding);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(cornerPx);
        cardBg.setColor(bgColor);
        card.setBackground(cardBg);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(marginH, marginV, marginH, marginV);
        card.setLayoutParams(cardLp);

        // Ripple foreground
        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true);
        card.setForeground(ctx.getDrawable(ripple.resourceId));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        if (icon != null) {
            ImageView iconView = new ImageView(ctx);
            int iconPx = res.getDimensionPixelSize(R.dimen.settings_pack_icon_size);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.settings_icon_margin_end));
            iconView.setLayoutParams(iconLp);
            iconView.setImageDrawable(icon);
            header.addView(iconView);
        }

        TextView name = new TextView(ctx);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(textColor);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(name);

        card.addView(header);
        if (listener != null) card.setOnClickListener(listener);
        return card;
    }

    // ---- Search bar factory ----

    /**
     * Creates a styled search EditText with debounced filtering.
     * Matches the pattern used by PerAppIconSheet and FolderCoverPickerHelper.
     */
    public static EditText createSearchBar(Context ctx, int hintResId,
            long debounceMs, Consumer<String> onFilter) {
        Resources res = ctx.getResources();
        int colorOnSurface = ctx.getColor(R.color.materialColorOnSurface);
        int hintColor = ctx.getColor(R.color.materialColorOnSurfaceVariant);

        EditText searchBar = new EditText(ctx);
        searchBar.setHint(hintResId);
        searchBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchBar.setMaxLines(1);
        searchBar.setSingleLine(true);
        searchBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchBar.setTextColor(colorOnSurface);
        searchBar.setHintTextColor(hintColor);
        searchBar.setBackgroundResource(R.drawable.bg_widgets_searchbox);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        int searchPadH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        searchBar.setPadding(searchPadH, 0, searchPadH, 0);
        searchBar.setCompoundDrawablePadding(
                res.getDimensionPixelSize(R.dimen.settings_search_padding));
        searchBar.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        Drawable searchIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_allapps_search);
        if (searchIcon != null) {
            searchIcon = searchIcon.mutate();
            searchIcon.setTintList(ColorStateList.valueOf(hintColor));
            int iconSize = res.getDimensionPixelSize(R.dimen.settings_search_icon_size);
            searchIcon.setBounds(0, 0, iconSize, iconSize);
            searchBar.setCompoundDrawablesRelative(searchIcon, null, null, null);
        }

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                res.getDimensionPixelSize(R.dimen.settings_row_min_height));
        int searchMarginH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        int searchMarginV = res.getDimensionPixelSize(R.dimen.settings_item_spacing);
        searchLp.setMargins(searchMarginH, searchMarginV, searchMarginH, searchMarginV);
        searchBar.setLayoutParams(searchLp);

        // Debounced filter
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] pending = new Runnable[1];
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                pending[0] = () -> onFilter.accept(s.toString());
                handler.postDelayed(pending[0], debounceMs);
            }
        });

        return searchBar;
    }

    // ---- Preference view hiding helper ----

    /**
     * Hides all default preference child views and resets minimum height.
     * Used by custom preferences that build their own UI in onBindViewHolder
     * (e.g., {@link M3SliderPreference}, {@link ColorDebugPreference},
     * {@link GridPreviewPreference}).
     */
    public static void hideDefaultViews(PreferenceViewHolder holder) {
        ViewGroup root = (ViewGroup) holder.itemView;
        for (int i = 0; i < root.getChildCount(); i++) {
            root.getChildAt(i).setVisibility(View.GONE);
        }
        root.setMinimumHeight(0);
    }
}
