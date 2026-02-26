/*
 * Copyright (C) 2025 DefaultLauncher Contributors
 *
 * This file is part of DefaultLauncher.
 *
 * DefaultLauncher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.android.launcher3.search;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CALCULATOR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CALENDAR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_CONTACT;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILE;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILTER_BAR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_QUICK_ACTION;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SECTION_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SHORTCUT;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_TIMEZONE;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_UNIT_CONVERTER;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.search.result.CalendarResult;
import com.android.launcher3.search.result.CalculatorResult;
import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.search.result.Launchable;
import com.android.launcher3.search.result.QuickAction;
import com.android.launcher3.search.result.ShortcutResult;
import com.android.launcher3.search.result.TimezoneResult;
import com.android.launcher3.search.result.UnitConversion;
import com.android.launcher3.views.ActivityContext;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import java.util.List;

/**
 * Adapter provider for universal search results. Creates and binds ViewHolders
 * for all search result types (filter bar, section headers, shortcuts, contacts,
 * calendar, files, quick actions, calculator, unit converter).
 */
public class UniversalSearchAdapterProvider extends SearchAdapterProvider<ActivityContext> {

    private Launchable mBestMatch;
    private View mHighlightedView;
    private int mBestMatchPriority = Integer.MAX_VALUE;
    private AlphabeticalAppsList<?> mAppsList;

    public UniversalSearchAdapterProvider(ActivityContext launcher) {
        super(launcher);
    }

    /** Sets the apps list reference so we can access adapter items during onBindView. */
    public void setAppsList(AlphabeticalAppsList<?> appsList) {
        mAppsList = appsList;
    }

    @Override
    public boolean isViewSupported(int viewType) {
        return viewType == VIEW_TYPE_SEARCH_FILTER_BAR
                || viewType == VIEW_TYPE_SEARCH_SECTION_HEADER
                || viewType == VIEW_TYPE_SEARCH_SHORTCUT
                || viewType == VIEW_TYPE_SEARCH_CONTACT
                || viewType == VIEW_TYPE_SEARCH_CALENDAR
                || viewType == VIEW_TYPE_SEARCH_FILE
                || viewType == VIEW_TYPE_SEARCH_QUICK_ACTION
                || viewType == VIEW_TYPE_SEARCH_CALCULATOR
                || viewType == VIEW_TYPE_SEARCH_UNIT_CONVERTER
                || viewType == VIEW_TYPE_SEARCH_TIMEZONE;
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater inflater,
            ViewGroup parent, int viewType) {
        // Material Components (Chip, MaterialCardView) require an AppCompat theme.
        // The Launcher activity uses a platform theme, so wrap with M3 + dynamic colors.
        Context themed = new ContextThemeWrapper(
                parent.getContext(), R.style.HomeSettings_Theme);
        themed = DynamicColors.wrapContextIfAvailable(themed);
        LayoutInflater themedInflater = inflater.cloneInContext(themed);

        switch (viewType) {
            case VIEW_TYPE_SEARCH_FILTER_BAR:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_filter_bar, parent, false));
            case VIEW_TYPE_SEARCH_SECTION_HEADER:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_section_header, parent, false));
            case VIEW_TYPE_SEARCH_SHORTCUT:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_shortcut, parent, false));
            case VIEW_TYPE_SEARCH_CONTACT:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_contact, parent, false));
            case VIEW_TYPE_SEARCH_CALENDAR:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_calendar, parent, false));
            case VIEW_TYPE_SEARCH_FILE:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_file, parent, false));
            case VIEW_TYPE_SEARCH_QUICK_ACTION:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_quick_action, parent, false));
            case VIEW_TYPE_SEARCH_CALCULATOR:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_calculator, parent, false));
            case VIEW_TYPE_SEARCH_UNIT_CONVERTER:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_unit_converter, parent, false));
            case VIEW_TYPE_SEARCH_TIMEZONE:
                return new AllAppsGridAdapter.ViewHolder(
                        themedInflater.inflate(R.layout.search_result_timezone, parent, false));
            default:
                return null;
        }
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        int viewType = holder.getItemViewType();

        // Get the SearchResultAdapterItem from the list
        if (mAppsList == null) return;
        List<AdapterItem> items = mAppsList.getAdapterItems();
        if (position < 0 || position >= items.size()) return;
        AdapterItem adapterItem = items.get(position);
        if (!(adapterItem instanceof SearchResultAdapterItem item)) return;

        switch (viewType) {
            case VIEW_TYPE_SEARCH_FILTER_BAR:
                bindFilterBar(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_SECTION_HEADER:
                bindSectionHeader(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_SHORTCUT:
                bindShortcut(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_CONTACT:
                bindContact(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_CALENDAR:
                bindCalendar(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_FILE:
                bindFile(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_QUICK_ACTION:
                bindQuickAction(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_CALCULATOR:
                bindCalculator(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_UNIT_CONVERTER:
                bindUnitConverter(holder.itemView, item);
                break;
            case VIEW_TYPE_SEARCH_TIMEZONE:
                bindTimezone(holder.itemView, item);
                break;
        }
    }

    private void bindFilterBar(View view, SearchResultAdapterItem item) {
        if (item.filters == null) return;
        SearchFilters filters = item.filters;
        Context ctx = view.getContext();
        LauncherPrefs prefs = LauncherPrefs.get(ctx);

        Chip chipAll = view.findViewById(R.id.chip_all);
        Chip chipApps = view.findViewById(R.id.chip_apps);
        Chip chipShortcuts = view.findViewById(R.id.chip_shortcuts);
        Chip chipContacts = view.findViewById(R.id.chip_contacts);
        Chip chipCalendar = view.findViewById(R.id.chip_calendar);
        Chip chipFiles = view.findViewById(R.id.chip_files);
        Chip chipTools = view.findViewById(R.id.chip_tools);

        // Set chip background: transparent when unchecked, primaryContainer when checked.
        int checkedColor = MaterialColors.getColor(
                view, com.google.android.material.R.attr.colorPrimaryContainer,
                Color.TRANSPARENT);
        ColorStateList chipBg = new ColorStateList(
                new int[][] { { android.R.attr.state_checked }, {} },
                new int[] { checkedColor, Color.TRANSPARENT });
        for (Chip chip : new Chip[] { chipAll, chipApps, chipShortcuts,
                chipContacts, chipCalendar, chipFiles, chipTools }) {
            chip.setChipBackgroundColor(chipBg);
        }

        // Only show chips for enabled categories
        chipContacts.setVisibility(
                prefs.get(LauncherPrefs.SEARCH_CONTACTS) ? View.VISIBLE : View.GONE);
        chipCalendar.setVisibility(
                prefs.get(LauncherPrefs.SEARCH_CALENDAR) ? View.VISIBLE : View.GONE);
        chipFiles.setVisibility(
                prefs.get(LauncherPrefs.SEARCH_FILES) ? View.VISIBLE : View.GONE);

        chipAll.setChecked(filters.isShowAll());
        chipApps.setChecked(filters.isCategorySelected(SearchFilters.Category.APPS)
                && !filters.isShowAll());
        chipShortcuts.setChecked(filters.isCategorySelected(SearchFilters.Category.SHORTCUTS)
                && !filters.isShowAll());
        chipContacts.setChecked(filters.isCategorySelected(SearchFilters.Category.CONTACTS)
                && !filters.isShowAll());
        chipCalendar.setChecked(filters.isCategorySelected(SearchFilters.Category.CALENDAR)
                && !filters.isShowAll());
        chipFiles.setChecked(filters.isCategorySelected(SearchFilters.Category.FILES)
                && !filters.isShowAll());
        chipTools.setChecked(filters.isCategorySelected(SearchFilters.Category.TOOLS)
                && !filters.isShowAll());

        chipAll.setOnClickListener(v -> filters.setShowAll(true));
        chipApps.setOnClickListener(v -> filters.toggleCategory(SearchFilters.Category.APPS));
        chipShortcuts.setOnClickListener(
                v -> filters.toggleCategory(SearchFilters.Category.SHORTCUTS));
        chipContacts.setOnClickListener(
                v -> filters.toggleCategory(SearchFilters.Category.CONTACTS));
        chipCalendar.setOnClickListener(
                v -> filters.toggleCategory(SearchFilters.Category.CALENDAR));
        chipFiles.setOnClickListener(v -> filters.toggleCategory(SearchFilters.Category.FILES));
        chipTools.setOnClickListener(v -> filters.toggleCategory(SearchFilters.Category.TOOLS));
    }

    private void bindSectionHeader(View view, SearchResultAdapterItem item) {
        ((TextView) view.findViewById(R.id.section_title)).setText(item.sectionTitle);
    }

    private void bindShortcut(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof ShortcutResult shortcut)) return;
        Context ctx = view.getContext();

        TextView title = view.findViewById(R.id.shortcut_title);
        TextView subtitle = view.findViewById(R.id.shortcut_app_name);
        ImageView icon = view.findViewById(R.id.shortcut_icon);

        title.setText(shortcut.getLabel());
        subtitle.setText(shortcut.appName);

        android.graphics.drawable.Drawable iconDrawable = shortcut.getIcon(ctx);
        if (iconDrawable != null) {
            icon.setImageDrawable(iconDrawable);
        } else {
            icon.setImageResource(R.drawable.ic_shortcut);
        }

        getCard(view).setOnClickListener(v -> shortcut.launch(ctx));
        trackBestMatch(shortcut, 2);
    }

    private void bindContact(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof ContactResult contact)) return;
        Context ctx = view.getContext();

        TextView name = view.findViewById(R.id.contact_name);
        TextView phone = view.findViewById(R.id.contact_phone);
        ImageView avatar = view.findViewById(R.id.contact_avatar);
        ImageView callBtn = view.findViewById(R.id.contact_call_button);
        ImageView msgBtn = view.findViewById(R.id.contact_message_button);

        name.setText(contact.displayName);
        String primaryPhone = contact.getPrimaryPhone();
        phone.setText(primaryPhone != null ? primaryPhone : "");
        phone.setVisibility(primaryPhone != null ? View.VISIBLE : View.GONE);

        if (contact.photoUri != null) {
            avatar.setImageURI(contact.photoUri);
        } else {
            avatar.setImageResource(R.drawable.ic_contact);
        }

        getCard(view).setOnClickListener(v -> contact.launch(ctx));

        if (primaryPhone != null) {
            callBtn.setVisibility(View.VISIBLE);
            callBtn.setOnClickListener(v -> {
                android.content.Intent dialIntent = new android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        Uri.parse("tel:" + primaryPhone));
                dialIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(dialIntent);
            });
        } else {
            callBtn.setVisibility(View.GONE);
        }

        String primaryEmail = contact.getPrimaryEmail();
        if (primaryEmail != null || primaryPhone != null) {
            msgBtn.setVisibility(View.VISIBLE);
            msgBtn.setOnClickListener(v -> {
                if (primaryEmail != null) {
                    android.content.Intent emailIntent = new android.content.Intent(
                            android.content.Intent.ACTION_SENDTO,
                            Uri.parse("mailto:" + primaryEmail));
                    emailIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(emailIntent);
                } else {
                    android.content.Intent smsIntent = new android.content.Intent(
                            android.content.Intent.ACTION_SENDTO,
                            Uri.parse("smsto:" + primaryPhone));
                    smsIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(smsIntent);
                }
            });
        } else {
            msgBtn.setVisibility(View.GONE);
        }

        trackBestMatch(contact, 3);
    }

    private void bindCalendar(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof CalendarResult event)) return;
        Context ctx = view.getContext();

        TextView title = view.findViewById(R.id.calendar_title);
        TextView time = view.findViewById(R.id.calendar_time);
        TextView location = view.findViewById(R.id.calendar_location);
        View colorDot = view.findViewById(R.id.calendar_color_dot);

        title.setText(event.title);

        if (event.allDay) {
            time.setText(DateUtils.formatDateTime(ctx, event.startTime,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH));
        } else {
            time.setText(DateUtils.formatDateRange(ctx, event.startTime, event.endTime,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                            | DateUtils.FORMAT_ABBREV_MONTH));
        }

        if (event.location != null && !event.location.isEmpty()) {
            location.setVisibility(View.VISIBLE);
            location.setText(event.location);
        } else {
            location.setVisibility(View.GONE);
        }

        if (event.color != 0 && colorDot.getBackground() != null) {
            colorDot.getBackground().setTint(event.color);
        }

        getCard(view).setOnClickListener(v -> event.launch(ctx));
        trackBestMatch(event, 4);
    }

    private void bindFile(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof FileResult file)) return;
        Context ctx = view.getContext();

        TextView name = view.findViewById(R.id.file_name);
        TextView info = view.findViewById(R.id.file_info);
        ImageView icon = view.findViewById(R.id.file_icon);

        name.setText(file.name);
        info.setText(file.getFormattedSize() + " \u2022 " + file.path);
        icon.setImageResource(R.drawable.ic_file);

        getCard(view).setOnClickListener(v -> file.launch(ctx));
        trackBestMatch(file, 5);
    }

    private void bindQuickAction(View view, SearchResultAdapterItem item) {
        if (item.quickAction == null) return;
        QuickAction action = item.quickAction;
        Context ctx = view.getContext();

        TextView label = view.findViewById(R.id.quick_action_label);
        ImageView icon = view.findViewById(R.id.quick_action_icon);

        label.setText(action.label);
        icon.setImageResource(action.iconRes);

        getCard(view).setOnClickListener(v -> action.launch(ctx));
    }

    private void bindCalculator(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof CalculatorResult calc)) return;
        Context ctx = view.getContext();

        TextView expression = view.findViewById(R.id.calc_expression);
        TextView result = view.findViewById(R.id.calc_result);
        TextView altFormats = view.findViewById(R.id.calc_alt_formats);

        expression.setText(calc.expression);
        result.setText("= " + calc.formattedResult);

        StringBuilder alt = new StringBuilder();
        String hex = calc.getHex();
        String oct = calc.getOctal();
        if (hex != null) alt.append(hex);
        if (oct != null) {
            if (alt.length() > 0) alt.append("  \u2022  ");
            alt.append(oct);
        }
        if (alt.length() > 0) {
            altFormats.setVisibility(View.VISIBLE);
            altFormats.setText(alt.toString());
        } else {
            altFormats.setVisibility(View.GONE);
        }

        getCard(view).setOnClickListener(v -> calc.launch(ctx));
    }

    private void bindUnitConverter(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof UnitConversion conversion)) return;

        TextView input = view.findViewById(R.id.unit_input);
        TextView conversions = view.findViewById(R.id.unit_conversions);

        input.setText(UnitConversion.formatValue(conversion.inputValue)
                + " " + conversion.inputUnit);

        StringBuilder sb = new StringBuilder();
        for (UnitConversion.ConvertedValue cv : conversion.conversions) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(cv.formatted());
        }
        conversions.setText(sb.toString());

        getCard(view).setOnClickListener(v -> conversion.launch(view.getContext()));
    }

    private void bindTimezone(View view, SearchResultAdapterItem item) {
        if (!(item.resultData instanceof TimezoneResult tz)) return;

        View sourceRow = view.findViewById(R.id.tz_source_row);
        TextView sourceTime = view.findViewById(R.id.tz_source_time);
        TextView sourceZone = view.findViewById(R.id.tz_source_zone);
        TextView targetTime = view.findViewById(R.id.tz_target_time);
        TextView targetZone = view.findViewById(R.id.tz_target_zone);
        TextView targetDate = view.findViewById(R.id.tz_target_date);

        if (tz.isCurrentTimeQuery) {
            sourceRow.setVisibility(View.GONE);
        } else {
            sourceRow.setVisibility(View.VISIBLE);
            sourceTime.setText(tz.sourceTimeFormatted);
            sourceZone.setText(tz.sourceZoneName);
        }

        targetTime.setText(tz.targetTimeFormatted);
        targetZone.setText(tz.targetZoneName);

        if (tz.targetDate != null) {
            targetDate.setVisibility(View.VISIBLE);
            targetDate.setText(tz.targetDate);
        } else {
            targetDate.setVisibility(View.GONE);
        }

        getCard(view).setOnClickListener(v -> tz.launch(view.getContext()));
    }

    /** Returns the MaterialCardView child inside the FrameLayout wrapper. */
    private static View getCard(View rootView) {
        return rootView instanceof ViewGroup ? ((ViewGroup) rootView).getChildAt(0) : rootView;
    }

    private void trackBestMatch(Launchable result, int priority) {
        if (priority < mBestMatchPriority) {
            mBestMatchPriority = priority;
            mBestMatch = result;
        }
    }

    @Override
    public boolean launchHighlightedItem() {
        if (mAppsList == null) return false;

        // Priority 1: first app result
        for (AdapterItem item : mAppsList.getAdapterItems()) {
            if (item.viewType == VIEW_TYPE_ICON && item.itemInfo != null) {
                Context ctx = (Context) mLauncher;
                android.content.Intent intent = item.itemInfo.getIntent();
                if (intent != null) {
                    return mLauncher.startActivitySafely(null, intent, item.itemInfo) != null;
                }
            }
        }
        // Priority 2+: best match from other categories
        if (mBestMatch != null) {
            return mBestMatch.launch((Context) mLauncher);
        }
        return false;
    }

    @Override
    public View getHighlightedItem() {
        return mHighlightedView;
    }

    @Override
    public void clearHighlightedItem() {
        mHighlightedView = null;
        mBestMatch = null;
        mBestMatchPriority = Integer.MAX_VALUE;
    }

    @Override
    public int[] getSupportedItemsPerRowArray() {
        return new int[]{};
    }

    @Override
    public int getItemsPerRow(int viewType, int appsPerRow) {
        // Return 1 = one item per row = full width (span = totalSpans / 1 = totalSpans).
        // VIEW_TYPE_ICON is handled by isIconViewType() in GridSpanSizer before reaching here.
        return 1;
    }
}
