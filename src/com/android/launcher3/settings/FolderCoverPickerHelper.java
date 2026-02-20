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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.DynamicColors;

import com.android.launcher3.R;
import com.android.launcher3.folder.FolderCoverManager;
import com.android.launcher3.folder.FolderCoverManager.CoverIcon;
import com.android.launcher3.folder.FolderIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows a BottomSheetDialog emoji picker for selecting a folder cover icon.
 * Uses the bundled Noto Emoji monochrome font for consistent monocolor rendering
 * that matches M3 Expressive theming.
 */
public class FolderCoverPickerHelper {

    private static final String TAG = "FolderCoverPicker";
    private static final String EMOJI_PREFIX = "emoji";
    private static final long SEARCH_DEBOUNCE_MS = 250;

    /** Tracks the active sheet via WeakReference to avoid leaking Activity context. */
    private static java.lang.ref.WeakReference<BottomSheetDialog> sActiveSheet;

    /** Dismisses the active cover picker sheet if showing. */
    public static void dismissIfShowing() {
        BottomSheetDialog sheet = sActiveSheet != null ? sActiveSheet.get() : null;
        if (sheet != null && sheet.isShowing()) {
            sheet.dismiss();
        }
        sActiveSheet = null;
    }

    /** An emoji paired with its category name (lowercase) for search matching. */
    private static class EmojiItem {
        final String emoji;
        final String categoryLower;

        EmojiItem(String emoji, String categoryLower) {
            this.emoji = emoji;
            this.categoryLower = categoryLower;
        }
    }

    /** Curated emoji categories and entries. */
    private static final String[][] EMOJI_CATEGORIES = {
        {"Smileys",
            "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06",
            "\uD83D\uDE05", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE0A", "\uD83D\uDE07",
            "\uD83D\uDE42", "\uD83D\uDE43", "\uD83D\uDE09", "\uD83D\uDE0C", "\uD83D\uDE0D",
            "\uD83E\uDD70", "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE19", "\uD83D\uDE1A",
            "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83E\uDD2A", "\uD83D\uDE1D",
            "\uD83E\uDD11", "\uD83E\uDD17", "\uD83E\uDD2D", "\uD83E\uDD2B", "\uD83E\uDD14",
            "\uD83E\uDD10", "\uD83E\uDD28", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE36",
            "\uD83D\uDE0F", "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE0E", "\uD83E\uDD29",
            "\uD83E\uDD73", "\uD83E\uDD20", "\uD83E\uDD2F", "\uD83D\uDE31", "\uD83D\uDE33",
            "\uD83D\uDE30", "\uD83D\uDE28", "\uD83D\uDE25", "\uD83D\uDE22", "\uD83D\uDE2D",
            "\uD83D\uDE24", "\uD83D\uDE21", "\uD83D\uDE20", "\uD83E\uDD2C", "\uD83D\uDE08",
            "\uD83D\uDC7F", "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7D", "\uD83E\uDD16",
            "\uD83D\uDCA9", "\uD83D\uDE3A", "\uD83D\uDE3B", "\uD83D\uDE3D", "\uD83D\uDE40",
            "\uD83D\uDE3F", "\uD83D\uDE3E", "\uD83D\uDE34", "\uD83E\uDD71", "\uD83E\uDD76",
            "\uD83E\uDD75", "\uD83E\uDD74", "\uD83E\uDD78"},
        {"Animals",
            "\uD83D\uDC36", "\uD83D\uDC15", "\uD83D\uDC29", "\uD83D\uDC3A", "\uD83E\uDD8A",
            "\uD83E\uDD9D", "\uD83D\uDC31", "\uD83D\uDC08", "\uD83E\uDD81", "\uD83D\uDC2F",
            "\uD83D\uDC05", "\uD83D\uDC06", "\uD83D\uDC34", "\uD83E\uDD84", "\uD83E\uDD93",
            "\uD83E\uDD8C", "\uD83D\uDC2E", "\uD83D\uDC02", "\uD83D\uDC03", "\uD83D\uDC04",
            "\uD83D\uDC37", "\uD83D\uDC16", "\uD83D\uDC17", "\uD83D\uDC3D", "\uD83D\uDC0F",
            "\uD83D\uDC11", "\uD83D\uDC10", "\uD83D\uDC2A", "\uD83D\uDC2B", "\uD83E\uDD99",
            "\uD83E\uDD92", "\uD83D\uDC18", "\uD83E\uDD8F", "\uD83E\uDD9B", "\uD83D\uDC2D",
            "\uD83D\uDC01", "\uD83D\uDC00", "\uD83D\uDC39", "\uD83D\uDC30", "\uD83D\uDC07",
            "\uD83E\uDD94", "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83E\uDDA5",
            "\uD83D\uDC27", "\uD83D\uDC26", "\uD83D\uDC14", "\uD83D\uDC23", "\uD83D\uDC24",
            "\uD83D\uDC25", "\uD83E\uDD86", "\uD83E\uDD85", "\uD83E\uDD89", "\uD83E\uDDA4",
            "\uD83E\uDDA9", "\uD83E\uDD9A", "\uD83D\uDC38", "\uD83D\uDC0A", "\uD83D\uDC22",
            "\uD83E\uDD8E", "\uD83D\uDC0D", "\uD83D\uDC32", "\uD83D\uDC09", "\uD83E\uDD95",
            "\uD83E\uDD96", "\uD83D\uDC33", "\uD83D\uDC0B", "\uD83D\uDC2C", "\uD83E\uDD88",
            "\uD83D\uDC19", "\uD83D\uDC1A", "\uD83D\uDC1B", "\uD83E\uDD8B", "\uD83D\uDC1D",
            "\uD83D\uDC1E", "\uD83E\uDD97", "\uD83E\uDD82"},
        {"Food",
            "\uD83C\uDF47", "\uD83C\uDF48", "\uD83C\uDF49", "\uD83C\uDF4A", "\uD83C\uDF4B",
            "\uD83C\uDF4C", "\uD83C\uDF4D", "\uD83E\uDD6D", "\uD83C\uDF4E", "\uD83C\uDF4F",
            "\uD83C\uDF50", "\uD83C\uDF51", "\uD83C\uDF52", "\uD83C\uDF53", "\uD83E\uDED0",
            "\uD83C\uDF45", "\uD83E\uDD65", "\uD83E\uDD51", "\uD83C\uDF46", "\uD83E\uDD54",
            "\uD83E\uDD55", "\uD83C\uDF3D", "\uD83E\uDD52", "\uD83E\uDD66",
            "\uD83E\uDDC4", "\uD83E\uDDC5", "\uD83C\uDF44", "\uD83E\uDD5C", "\uD83C\uDF30",
            "\uD83C\uDF5E", "\uD83E\uDD50", "\uD83E\uDD56", "\uD83E\uDDC0", "\uD83E\uDD5A",
            "\uD83C\uDF73", "\uD83E\uDD53", "\uD83E\uDD69", "\uD83C\uDF57", "\uD83C\uDF56",
            "\uD83C\uDF54", "\uD83C\uDF5F", "\uD83C\uDF55", "\uD83C\uDF2D", "\uD83E\uDD6A",
            "\uD83C\uDF2E", "\uD83C\uDF2F", "\uD83E\uDD59", "\uD83E\uDDC6", "\uD83E\uDD57",
            "\uD83C\uDF5C", "\uD83C\uDF63", "\uD83C\uDF69", "\uD83C\uDF70", "\uD83C\uDF82",
            "\uD83C\uDF66", "\uD83C\uDF67", "\uD83C\uDF68", "\uD83C\uDF6A", "\uD83C\uDF6B",
            "\uD83C\uDF6C", "\uD83C\uDF6D", "\uD83C\uDF6E", "\uD83C\uDF6F", "\uD83E\uDDC1",
            "\u2615", "\uD83C\uDF75", "\uD83C\uDF76", "\uD83C\uDF77", "\uD83C\uDF78",
            "\uD83C\uDF79", "\uD83C\uDF7A", "\uD83C\uDF7B", "\uD83E\uDD42", "\uD83E\uDD64",
            "\uD83E\uDDC3", "\uD83E\uDD43", "\uD83E\uDED6"},
        {"Activities",
            "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFC8", "\u26BE", "\uD83E\uDD4E",
            "\uD83C\uDFBE", "\uD83C\uDFD0", "\uD83C\uDFC9", "\uD83E\uDD4F", "\uD83C\uDFB1",
            "\uD83C\uDFD3", "\uD83C\uDFF8", "\uD83C\uDFD2", "\uD83C\uDFD1", "\uD83E\uDD4D",
            "\u26F3", "\uD83C\uDFB3", "\uD83E\uDD4A", "\uD83E\uDD3C", "\uD83E\uDD3A",
            "\uD83E\uDD3D", "\uD83E\uDD3E",
            "\uD83C\uDFBF", "\uD83D\uDEF7",
            "\uD83C\uDFC2", "\uD83C\uDFC4", "\uD83C\uDFCA", "\uD83D\uDEB4",
            "\uD83D\uDEB5", "\uD83C\uDFA8", "\uD83C\uDFAD", "\uD83C\uDFAA", "\uD83C\uDFAC",
            "\uD83C\uDFB5", "\uD83C\uDFB6", "\uD83C\uDFB9", "\uD83C\uDFB7", "\uD83C\uDFBA",
            "\uD83C\uDFB8", "\uD83E\uDE95", "\uD83C\uDFBB", "\uD83C\uDFA4", "\uD83C\uDFA7",
            "\uD83C\uDFAE", "\uD83C\uDFAF", "\uD83C\uDFB2", "\uD83E\uDDE9",
            "\uD83C\uDFB0"},
        {"Travel",
            "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE99", "\uD83D\uDE8C", "\uD83D\uDE8E",
            "\uD83D\uDE93", "\uD83D\uDE91", "\uD83D\uDE92", "\uD83D\uDE90",
            "\uD83D\uDEB2", "\uD83D\uDEF5", "\uD83D\uDEFA", "\uD83D\uDE82", "\uD83D\uDE86",
            "\uD83D\uDE88", "\uD83D\uDE81",
            "\uD83D\uDE80", "\uD83D\uDEF8", "\u26F5", "\uD83D\uDEA4", "\uD83D\uDEA2",
            "\uD83C\uDFE0", "\uD83C\uDFE1", "\uD83C\uDFE2",
            "\uD83C\uDFEB", "\uD83C\uDFE5", "\uD83C\uDFEA", "\uD83C\uDFE8",
            "\uD83D\uDDFC",
            "\uD83D\uDDFD", "\uD83C\uDFF0", "\uD83C\uDFEF", "\uD83C\uDF0D", "\uD83C\uDF0E",
            "\uD83C\uDF0F", "\uD83C\uDF0B", "\uD83C\uDF05", "\uD83C\uDF04", "\uD83C\uDF06",
            "\uD83C\uDF07", "\uD83C\uDF0C", "\u2B50", "\uD83C\uDF1F", "\uD83C\uDF19",
            "\uD83C\uDF1B", "\uD83C\uDF1C",
            "\uD83C\uDF08", "\uD83C\uDF0A"},
        {"Objects",
            "\uD83D\uDCF1", "\uD83D\uDCF2", "\uD83D\uDCBB",
            "\uD83D\uDCBD", "\uD83D\uDCBE", "\uD83D\uDCBF",
            "\uD83D\uDCF7", "\uD83D\uDCF8", "\uD83D\uDCF9",
            "\uD83D\uDCFA", "\uD83D\uDD0A", "\uD83D\uDCE2", "\uD83D\uDCEF", "\uD83D\uDD14",
            "\uD83D\uDCDA", "\uD83D\uDCD6", "\uD83D\uDCD3", "\uD83D\uDCD2", "\uD83D\uDCD5",
            "\uD83D\uDCD7", "\uD83D\uDCD8", "\uD83D\uDCD9", "\uD83D\uDCDD",
            "\uD83D\uDCCE",
            "\uD83D\uDCCC", "\uD83D\uDCC1", "\uD83D\uDCC2",
            "\uD83D\uDCC5", "\uD83D\uDCCA", "\uD83D\uDD12", "\uD83D\uDD13", "\uD83D\uDD11",
            "\uD83D\uDD27", "\uD83D\uDD28",
            "\uD83D\uDD29", "\uD83E\uDDF2", "\uD83D\uDCA1", "\uD83D\uDD26", "\uD83D\uDD0D",
            "\uD83D\uDD2C", "\uD83D\uDD2D", "\uD83D\uDCE6", "\uD83D\uDCBC", "\uD83D\uDCB0",
            "\uD83D\uDC8E", "\uD83D\uDC51", "\uD83C\uDFA9", "\uD83D\uDC53", "\uD83D\uDC54",
            "\uD83C\uDFFA", "\uD83E\uDDF9", "\uD83E\uDDEA",
            "\uD83E\uDDEB", "\uD83E\uDDEC", "\uD83E\uDDED", "\uD83E\uDDEE", "\uD83E\uDDEF"},
        {"Symbols",
            "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99",
            "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83E\uDD0D", "\uD83E\uDD0E", "\uD83D\uDC94",
            "\uD83D\uDC95", "\uD83D\uDC96", "\uD83D\uDC97", "\uD83D\uDC98",
            "\uD83D\uDC9D", "\uD83D\uDC9E", "\uD83D\uDC9F", "\u2728", "\uD83C\uDF1F",
            "\uD83D\uDCAB", "\uD83D\uDD25", "\uD83D\uDCA5", "\uD83D\uDCA2", "\uD83D\uDCAF",
            "\u2705", "\u274C", "\u274E", "\u2757", "\u2753",
            "\u2754", "\u2755",
            "\uD83D\uDD05",
            "\uD83D\uDD06",
            "\u2660\uFE0F", "\u2665\uFE0F", "\u2666\uFE0F", "\u2663\uFE0F", "\uD83C\uDCCF",
            "\uD83D\uDD1F", "\uD83D\uDD20", "\uD83D\uDD21",
            "\uD83D\uDD22", "\uD83D\uDD23", "\uD83D\uDD24",
            "\uD83D\uDD36",
            "\uD83D\uDD37", "\uD83D\uDD38", "\uD83D\uDD39", "\uD83D\uDD3A", "\uD83D\uDD3B"},
    };

    /** String resource IDs for each category header. */
    private static final int[] CATEGORY_STRING_IDS = {
        R.string.folder_emoji_smileys,
        R.string.folder_emoji_animals,
        R.string.folder_emoji_food,
        R.string.folder_emoji_activities,
        R.string.folder_emoji_travel,
        R.string.folder_emoji_objects,
        R.string.folder_emoji_symbols,
    };

    /**
     * Shows the cover emoji picker for a folder.
     * Uses the Noto Emoji monochrome font with M3 Expressive theming.
     * @param folderIcon the folder whose cover to change
     * @param onChanged  callback after cover is set/removed
     */
    public static void showCoverPicker(Context ctx, FolderIcon folderIcon,
            Runnable onChanged) {
        if (ctx == null || folderIcon == null || folderIcon.mInfo == null) return;
        long folderId = folderIcon.mInfo.id;

        // Apply M3 theme first, then DynamicColors (which needs M3's
        // dynamicColorThemeOverlay attribute to apply wallpaper-derived colors)
        Context themed = DynamicColors.wrapContextIfAvailable(
                new ContextThemeWrapper(ctx, R.style.HomeSettings_Theme));
        Resources res = themed.getResources();
        int padH = res.getDimensionPixelSize(R.dimen.settings_card_padding_horizontal);
        int padV = res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical);

        // M3 colors — resolve from BottomSheetDialog's context for proper day/night
        int colorOnSurface = themed.getColor(R.color.materialColorOnSurface);
        int colorOnSurfaceVar = themed.getColor(R.color.materialColorOnSurfaceVariant);
        BottomSheetDialog sheet = new BottomSheetDialog(themed);
        sheet.getBehavior().setPeekHeight(
                res.getDisplayMetrics().heightPixels * 2 / 3);

        LinearLayout root = new LinearLayout(themed);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, padH);

        IconSettingsHelper.addSheetHandle(root, themed, res);

        // Title
        TextView titleView = new TextView(themed);
        titleView.setText(R.string.folder_cover_title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.settings_sheet_title_text_size));
        titleView.setTextColor(colorOnSurface);
        titleView.setPadding(padH, padV, padH, padV);
        root.addView(titleView);

        // Search bar — matches PerAppIconSheet pattern
        EditText searchBar = new EditText(themed);
        searchBar.setHint(R.string.folder_emoji_search_hint);
        searchBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchBar.setMaxLines(1);
        searchBar.setSingleLine(true);
        searchBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchBar.setTextColor(colorOnSurface);
        searchBar.setHintTextColor(colorOnSurfaceVar);
        searchBar.setBackgroundResource(R.drawable.bg_widgets_searchbox);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        int searchPadH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        searchBar.setPadding(searchPadH, 0, searchPadH, 0);
        searchBar.setCompoundDrawablePadding(
                res.getDimensionPixelSize(R.dimen.settings_search_padding));
        searchBar.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);

        Drawable searchIcon = ContextCompat.getDrawable(themed, R.drawable.ic_allapps_search);
        if (searchIcon != null) {
            searchIcon = searchIcon.mutate();
            searchIcon.setTintList(ColorStateList.valueOf(colorOnSurfaceVar));
            int searchIconSize = res.getDimensionPixelSize(R.dimen.settings_search_icon_size);
            searchIcon.setBounds(0, 0, searchIconSize, searchIconSize);
            searchBar.setCompoundDrawablesRelative(searchIcon, null, null, null);
        }

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                res.getDimensionPixelSize(R.dimen.settings_row_min_height));
        int searchMarginH = res.getDimensionPixelSize(R.dimen.settings_search_padding);
        int searchMarginV = res.getDimensionPixelSize(R.dimen.settings_item_spacing);
        searchLp.setMargins(searchMarginH, searchMarginV, searchMarginH, searchMarginV);
        searchBar.setLayoutParams(searchLp);
        root.addView(searchBar);

        // Build flat list of emoji items
        FolderCoverManager coverMgr = FolderCoverManager.getInstance(ctx);
        int emojiSizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 26, res.getDisplayMetrics());

        List<CategoryGridAdapter.ListItem<EmojiItem>> items = new ArrayList<>();
        for (int catIdx = 0; catIdx < EMOJI_CATEGORIES.length; catIdx++) {
            String[] category = EMOJI_CATEGORIES[catIdx];
            String catName = category[0];
            if (catIdx < CATEGORY_STRING_IDS.length) {
                catName = themed.getString(CATEGORY_STRING_IDS[catIdx]);
            }
            String catNameLower = catName.toLowerCase(Locale.ROOT);

            items.add(new CategoryGridAdapter.ListItem<>(catName));
            for (int i = 1; i < category.length; i++) {
                items.add(new CategoryGridAdapter.ListItem<>(
                        new EmojiItem(category[i], catNameLower)));
            }
        }

        // Create binder for emoji items
        final Context finalCtx = ctx;
        CategoryGridAdapter.ItemBinder<EmojiItem> binder =
                new CategoryGridAdapter.ItemBinder<>() {
            @Override
            public void bind(ImageView view, EmojiItem item, int position) {
                Drawable emojiDrawable = coverMgr.renderEmojiSmall(
                        item.emoji, emojiSizePx, colorOnSurface);
                if (emojiDrawable != null) {
                    view.setImageDrawable(emojiDrawable);
                }
                view.setScaleType(ImageView.ScaleType.CENTER);
            }

            @Override
            public void onItemClick(EmojiItem item) {
                CoverIcon cover = new CoverIcon(EMOJI_PREFIX, item.emoji);
                FolderCoverManager.getInstance(finalCtx).setCover(folderId, cover);
                folderIcon.updateCoverDrawable();
                folderIcon.invalidate();
                sheet.dismiss();
                if (onChanged != null) onChanged.run();
            }

            @Override
            public boolean matchesQuery(EmojiItem item, String query) {
                // Match against category name
                if (item.categoryLower.contains(query)) return true;
                // Match against Unicode character names
                return emojiMatchesQuery(item.emoji, query);
            }

            @Override
            public String getContentDescription(EmojiItem item) {
                return null;
            }
        };

        CategoryGridAdapter<EmojiItem> adapter = new CategoryGridAdapter<>(items, binder);

        // RecyclerView emoji grid
        RecyclerView rv = new RecyclerView(themed);
        rv.setClipToPadding(false);
        int rvPadH = res.getDimensionPixelSize(R.dimen.settings_item_spacing);
        rv.setPadding(rvPadH, 0, rvPadH,
                res.getDimensionPixelSize(R.dimen.settings_card_padding_vertical));
        rv.setNestedScrollingEnabled(true);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Set up grid after layout to calculate span count
        rv.post(() -> {
            int cellSizePx = res.getDimensionPixelSize(R.dimen.settings_icon_cell_size);
            rv.setLayoutManager(CategoryGridAdapter.createGridLayoutManager(
                    themed, rv, cellSizePx, adapter));
            rv.setAdapter(adapter);
        });

        // Debounced search filtering
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] pendingFilter = new Runnable[1];
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (pendingFilter[0] != null) handler.removeCallbacks(pendingFilter[0]);
                pendingFilter[0] = () -> adapter.filter(s.toString());
                handler.postDelayed(pendingFilter[0], SEARCH_DEBOUNCE_MS);
            }
        });

        sheet.setContentView(root);
        sActiveSheet = new java.lang.ref.WeakReference<>(sheet);
        sheet.setOnDismissListener(d -> sActiveSheet = null);
        sheet.show();
    }

    /**
     * Checks if an emoji matches a search query by examining its Unicode character names.
     */
    static boolean emojiMatchesQuery(String emoji, String query) {
        if (emoji == null || query.isEmpty()) return false;
        // Check each codepoint's Unicode name
        for (int i = 0; i < emoji.length(); ) {
            int cp = emoji.codePointAt(i);
            String name = Character.getName(cp);
            if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
