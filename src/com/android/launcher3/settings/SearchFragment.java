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
package com.android.launcher3.settings;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings sub-page for configuring universal search.
 * Provides per-category toggles and web search app chooser.
 */
public class SearchFragment extends SettingsBaseFragment {

    private static final int REQUEST_READ_CONTACTS = 100;
    private static final int REQUEST_READ_CALENDAR = 101;

    private SwitchPreferenceCompat mContactsPref;
    private SwitchPreferenceCompat mCalendarPref;
    private SwitchPreferenceCompat mFilesPref;

    private ActivityResultLauncher<String> mContactsPermLauncher;
    private ActivityResultLauncher<String> mCalendarPermLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContactsPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        LauncherPrefs.get(requireContext()).put(LauncherPrefs.SEARCH_CONTACTS, true);
                        if (mContactsPref != null) mContactsPref.setChecked(true);
                    } else {
                        if (mContactsPref != null) mContactsPref.setChecked(false);
                    }
                });

        mCalendarPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        LauncherPrefs.get(requireContext()).put(LauncherPrefs.SEARCH_CALENDAR, true);
                        if (mCalendarPref != null) mCalendarPref.setChecked(true);
                    } else {
                        if (mCalendarPref != null) mCalendarPref.setChecked(false);
                    }
                });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
        setPreferencesFromResource(R.xml.search_preferences, rootKey);

        mContactsPref = findPreference("pref_search_contacts");
        mCalendarPref = findPreference("pref_search_calendar");
        mFilesPref = findPreference("pref_search_files");

        // Permission-gated toggles
        if (mContactsPref != null) {
            mContactsPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((boolean) newValue) {
                    if (requireContext().checkSelfPermission(Manifest.permission.READ_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED) {
                        mContactsPermLauncher.launch(Manifest.permission.READ_CONTACTS);
                        return false; // Don't toggle yet; wait for permission result
                    }
                }
                return true;
            });
        }

        if (mCalendarPref != null) {
            mCalendarPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((boolean) newValue) {
                    if (requireContext().checkSelfPermission(Manifest.permission.READ_CALENDAR)
                            != PackageManager.PERMISSION_GRANTED) {
                        mCalendarPermLauncher.launch(Manifest.permission.READ_CALENDAR);
                        return false;
                    }
                }
                return true;
            });
        }

        if (mFilesPref != null) {
            mFilesPref.setOnPreferenceChangeListener((pref, newValue) -> {
                if ((boolean) newValue) {
                    if (!Environment.isExternalStorageManager()) {
                        // Redirect to system settings for MANAGE_EXTERNAL_STORAGE
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                        return false;
                    }
                }
                return true;
            });
        }

        // Web search app picker
        Preference webAppPref = findPreference("pref_search_web_app");
        if (webAppPref != null) {
            updateWebAppSummary(webAppPref);
            webAppPref.setOnPreferenceClickListener(pref -> {
                showWebSearchAppChooser(pref);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-check file access permission when returning from system settings
        if (mFilesPref != null) {
            if (Environment.isExternalStorageManager()) {
                mFilesPref.setChecked(true);
                LauncherPrefs.get(requireContext()).put(LauncherPrefs.SEARCH_FILES, true);
            } else {
                mFilesPref.setChecked(false);
                LauncherPrefs.get(requireContext()).put(LauncherPrefs.SEARCH_FILES, false);
            }
        }
    }

    private void updateWebAppSummary(Preference pref) {
        String value = LauncherPrefs.get(requireContext()).get(LauncherPrefs.SEARCH_WEB_APP);
        if ("default".equals(value)) {
            pref.setSummary(R.string.search_web_app_system_default);
        } else {
            try {
                ComponentName cn = ComponentName.unflattenFromString(value);
                if (cn != null) {
                    PackageManager pm = requireContext().getPackageManager();
                    pref.setSummary(pm.getApplicationLabel(
                            pm.getApplicationInfo(cn.getPackageName(), 0)));
                    return;
                }
            } catch (Exception ignored) {}
            pref.setSummary(R.string.search_web_app_summary);
        }
    }

    private void showWebSearchAppChooser(Preference pref) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View contentView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_web_search_chooser, null);
        dialog.setContentView(contentView);

        android.widget.LinearLayout container = contentView.findViewById(R.id.web_app_list);
        PackageManager pm = requireContext().getPackageManager();
        String currentValue = LauncherPrefs.get(requireContext()).get(LauncherPrefs.SEARCH_WEB_APP);

        // Add "System default" option
        addWebAppOption(container, null, getString(R.string.search_web_app_system_default),
                null, "default".equals(currentValue), "default", pref, dialog);

        // Find apps that handle ACTION_WEB_SEARCH
        Intent webSearchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
        List<ResolveInfo> webSearchApps = pm.queryIntentActivities(webSearchIntent, 0);
        List<String> addedPackages = new ArrayList<>();

        for (ResolveInfo ri : webSearchApps) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || addedPackages.contains(ai.packageName)) continue;
            String componentStr = new ComponentName(ai.packageName, ai.name).flattenToString();
            Drawable icon = ai.loadIcon(pm);
            String label = ai.loadLabel(pm).toString();
            addWebAppOption(container, icon, label, null,
                    componentStr.equals(currentValue), componentStr, pref, dialog);
            addedPackages.add(ai.packageName);
        }

        // Find browsers as fallback
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        List<ResolveInfo> browsers = pm.queryIntentActivities(browserIntent, 0);
        for (ResolveInfo ri : browsers) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || addedPackages.contains(ai.packageName)) continue;
            String componentStr = new ComponentName(ai.packageName, ai.name).flattenToString();
            Drawable icon = ai.loadIcon(pm);
            String label = ai.loadLabel(pm).toString();
            addWebAppOption(container, icon, label, null,
                    componentStr.equals(currentValue), componentStr, pref, dialog);
            addedPackages.add(ai.packageName);
        }

        dialog.show();
    }

    private void addWebAppOption(android.widget.LinearLayout container, Drawable icon,
            String label, String subtitle, boolean isSelected, String value,
            Preference pref, BottomSheetDialog dialog) {
        View item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_web_search_app, container, false);

        ImageView iconView = item.findViewById(R.id.web_app_icon);
        TextView labelView = item.findViewById(R.id.web_app_label);
        RadioButton radio = item.findViewById(R.id.web_app_radio);

        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            iconView.setImageResource(R.drawable.ic_web_search);
        }
        labelView.setText(label);
        radio.setChecked(isSelected);

        item.setOnClickListener(v -> {
            LauncherPrefs.get(requireContext()).put(LauncherPrefs.SEARCH_WEB_APP, value);
            updateWebAppSummary(pref);
            dialog.dismiss();
        });

        container.addView(item);
    }
}
