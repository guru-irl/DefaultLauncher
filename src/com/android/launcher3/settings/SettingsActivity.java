/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.settings;

import static android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

import static com.android.launcher3.BuildConfig.IS_DEBUG_DEVICE;
import static com.android.launcher3.BuildConfig.IS_STUDIO_BUILD;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET;
import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.pack.IconPack;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.SettingsCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends FragmentActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    @VisibleForTesting
    static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";

    public static final String FIXED_LANDSCAPE_MODE = "pref_fixed_landscape_mode";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";

    // Intent extra to indicate the pref-key to highlighted when opening the settings activity
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    // Intent extra to indicate the pref-key of the root screen when opening the settings activity
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        setActionBar(findViewById(R.id.action_bar));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FRAGMENT_ROOT_KEY) || intent.hasExtra(EXTRA_FRAGMENT_ARGS)
                || intent.hasExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY)) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
            if (args == null) {
                args = new Bundle();
            }

            String highlight = intent.getStringExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            if (!TextUtils.isEmpty(highlight)) {
                args.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, highlight);
            }
            String root = intent.getStringExtra(EXTRA_FRAGMENT_ROOT_KEY);
            if (!TextUtils.isEmpty(root)) {
                args.putString(EXTRA_FRAGMENT_ROOT_KEY, root);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    getString(R.string.settings_fragment_name));
            f.setArguments(args);
            // Display the fragment as the main content.
            fm.beginTransaction().replace(R.id.content_frame, f).commit();
        }
    }

    private boolean startPreference(String fragment, Bundle args, String key) {
        if (getSupportFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new preferences in that case.
            return false;
        }
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(), fragment);
        if (f instanceof DialogFragment) {
            f.setArguments(args);
            ((DialogFragment) f).show(fm, key);
        } else {
            startActivity(new Intent(this, SettingsActivity.class)
                    .putExtra(EXTRA_FRAGMENT_ARGS, args));
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.settings_fragment_name), args, pref.getKey());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragmentCompat implements
            SettingsCache.OnChangeListener {

        protected boolean mDeveloperOptionsEnabled = false;

        private boolean mRestartOnResume = false;

        private String mHighLightKey;

        private boolean mPreferenceHighlighted = false;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            if (BuildConfig.IS_DEBUG_DEVICE) {
                Uri devUri = Settings.Global.getUriFor(DEVELOPMENT_SETTINGS_ENABLED);
                SettingsCache settingsCache = SettingsCache.INSTANCE.get(getContext());
                mDeveloperOptionsEnabled = settingsCache.getValue(devUri);
                settingsCache.register(devUri, this);
            }
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_HIGHLIGHT_KEY);

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.launcher_preferences, rootKey);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                if (!initPreference(preference)) {
                    screen.removePreference(preference);
                }
            }

            // Wire up grid setting change listeners to trigger grid reconfiguration
            Preference.OnPreferenceChangeListener gridChangeListener = (pref, newValue) -> {
                // Post so the value is persisted before reconfiguring
                getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                return true;
            };
            Preference columnsPref = findPreference("pref_grid_columns");
            if (columnsPref != null) {
                columnsPref.setOnPreferenceChangeListener(gridChangeListener);
            }

            // Row gap slider: snap to nearest valid value (16/24/32) on release
            SeekBarPreference rowGapPref = findPreference("pref_allapps_row_gap");
            if (rowGapPref != null) {
                rowGapPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    int raw = (int) newValue;
                    int snapped = (int) InvariantDeviceProfile.snapToNearestGap(raw);
                    if (snapped != raw) {
                        ((SeekBarPreference) pref).setValue(snapped);
                    }
                    getListView().post(() ->
                            InvariantDeviceProfile.INSTANCE.get(getContext())
                                    .onConfigChanged(getContext()));
                    return snapped == raw;
                });
            }

            // Icon pack picker (manual dialog — ListPreference requires AppCompat theme)
            Preference iconPackPref = findPreference("pref_icon_pack");
            if (iconPackPref != null) {
                IconPackManager mgr = LauncherComponentProvider.get(getContext())
                        .getIconPackManager();
                updateIconPackSummary(iconPackPref, mgr);

                iconPackPref.setOnPreferenceClickListener(pref -> {
                    showIconPackDialog(pref, mgr);
                    return true;
                });
            }

            // Icon shape picker
            Preference iconShapePref = findPreference("pref_icon_shape");
            if (iconShapePref != null) {
                updateIconShapeSummary(iconShapePref);
                iconShapePref.setOnPreferenceClickListener(pref -> {
                    showIconShapeDialog(pref);
                    return true;
                });
            }

            // If the target preference is not in the current preference screen, find the parent
            // preference screen that contains the target preference and set it as the preference
            // screen.
            if (Flags.navigateToChildPreference()
                    && mHighLightKey != null
                    && !isKeyInPreferenceGroup(mHighLightKey, screen)) {
                final PreferenceScreen parentPreferenceScreen =
                        findParentPreference(screen, mHighLightKey);
                if (parentPreferenceScreen != null && getActivity() != null) {
                    if (!TextUtils.isEmpty(parentPreferenceScreen.getTitle())) {
                        getActivity().setTitle(parentPreferenceScreen.getTitle());
                    }
                    setPreferenceScreen(parentPreferenceScreen);
                    return;
                }
            }

            if (getActivity() != null && !TextUtils.isEmpty(getPreferenceScreen().getTitle())) {
                getActivity().setTitle(getPreferenceScreen().getTitle());
            }
        }

        private boolean isKeyInPreferenceGroup(String targetKey, PreferenceGroup parent) {
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference pref = parent.getPreference(i);
                if (pref.getKey() != null && pref.getKey().equals(targetKey)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Finds the parent preference screen for the given target key.
         *
         * @param parent    the parent preference screen
         * @param targetKey the key of the preference to find
         * @return the parent preference screen that contains the target preference
         */
        @Nullable
        private PreferenceScreen findParentPreference(PreferenceScreen parent, String targetKey) {
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference pref = parent.getPreference(i);
                if (pref instanceof PreferenceScreen) {
                    PreferenceScreen foundKey = findParentPreference((PreferenceScreen) pref,
                            targetKey);
                    if (foundKey != null) {
                        return foundKey;
                    }
                } else if (pref.getKey() != null && pref.getKey().equals(targetKey)) {
                    return parent;
                }
            }
            return null;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            final int bottomPadding = listView.getPaddingBottom();
            listView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottomPadding + insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });

            // Overriding Text Direction in the Androidx preference library to support RTL
            view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        /**
         * Initializes a preference. This is called for every preference. Returning false here
         * will remove that preference from the list.
         */
        protected boolean initPreference(Preference preference) {
            if (preference.getKey() == null) {
                return true;
            }
            DisplayController.Info info = DisplayController.INSTANCE.get(getContext()).getInfo();
            switch (preference.getKey()) {
                case NOTIFICATION_DOTS_PREFERENCE_KEY:
                    return BuildConfig.NOTIFICATION_DOTS_ENABLED;
                case ALLOW_ROTATION_PREFERENCE_KEY:
                    if (Flags.oneGridSpecs()) {
                        return false;
                    }
                    if (info.isTablet(info.realBounds)) {
                        // Launcher supports rotation by default. No need to show this setting.
                        return false;
                    }
                    // Initialize the UI once
                    preference.setDefaultValue(RotationHelper.getAllowRotationDefaultValue(info));
                    return true;
                case DEVELOPER_OPTIONS_KEY:
                    if (IS_STUDIO_BUILD) {
                        preference.setOrder(0);
                    }
                    return mDeveloperOptionsEnabled;
                case FIXED_LANDSCAPE_MODE:
                    if (!Flags.oneGridSpecs()
                            // adding this condition until fixing b/378972567
                            || InvariantDeviceProfile.INSTANCE.get(getContext()).deviceType
                            == TYPE_MULTI_DISPLAY
                            || InvariantDeviceProfile.INSTANCE.get(getContext()).deviceType
                            == TYPE_TABLET) {
                        return false;
                    }
                    // When the setting changes rotate the screen accordingly to showcase the result
                    // of the setting
                    preference.setOnPreferenceChangeListener(
                            (pref, newValue) -> {
                                getActivity().setRequestedOrientation(
                                        (boolean) newValue
                                                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                : ActivityInfo.SCREEN_ORIENTATION_USER
                                );
                                return true;
                            }
                    );
                    return !info.isTablet(info.realBounds);

            }
            return true;
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }

            if (mRestartOnResume) {
                recreateActivityNow();
            }
        }

        @Override
        public void onSettingsChanged(boolean isEnabled) {
            // Developer options changed, try recreate
            tryRecreateActivity();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (IS_DEBUG_DEVICE) {
                SettingsCache.INSTANCE.get(getContext())
                        .unregister(Settings.Global.getUriFor(DEVELOPMENT_SETTINGS_ENABLED), this);
            }
        }

        /**
         * Tries to recreate the preference
         */
        protected void tryRecreateActivity() {
            if (isResumed()) {
                recreateActivityNow();
            } else {
                mRestartOnResume = true;
            }
        }

        private void recreateActivityNow() {
            Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(
                    list, position, screen.findPreference(mHighLightKey))
                    : null;
        }

        private void updateIconPackSummary(Preference pref, IconPackManager mgr) {
            String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_PACK);
            if (current == null || current.isEmpty()) {
                pref.setSummary(R.string.icon_pack_default);
            } else {
                Map<String, IconPack> packs = mgr.getInstalledPacks();
                IconPack pack = packs.get(current);
                pref.setSummary(pack != null ? pack.label : current);
            }
        }

        private void showIconPackDialog(Preference pref, IconPackManager mgr) {
            Map<String, IconPack> packs = mgr.getInstalledPacks();

            List<CharSequence> labels = new ArrayList<>();
            List<String> pkgs = new ArrayList<>();
            labels.add(getString(R.string.icon_pack_default));
            pkgs.add("");
            for (Map.Entry<String, IconPack> entry : packs.entrySet()) {
                labels.add(entry.getValue().label);
                pkgs.add(entry.getKey());
            }

            String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_PACK);
            int selected = Math.max(0, pkgs.indexOf(current));

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.icon_pack_title)
                    .setSingleChoiceItems(
                            labels.toArray(new CharSequence[0]), selected,
                            (dialog, which) -> {
                                String pkg = pkgs.get(which);
                                LauncherPrefs.get(getContext())
                                        .put(LauncherPrefs.ICON_PACK, pkg);
                                mgr.invalidate();

                                // Show loading state
                                AlertDialog ad = (AlertDialog) dialog;
                                ad.getListView().setEnabled(false);
                                ad.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                                ad.setTitle(R.string.icon_pack_applying);

                                // Pre-parse icon pack and clear caches on background thread
                                LauncherAppState app = LauncherAppState.INSTANCE
                                        .get(getContext());
                                Executors.MODEL_EXECUTOR.execute(() -> {
                                    mgr.getCurrentPack(); // triggers ensureParsed()
                                    // Clear both disk and memory cache so workspace
                                    // binding loads fresh icons from the provider.
                                    app.getIconCache().clearAllIcons();
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        if (getContext() == null) return;
                                        updateIconPackSummary(pref, mgr);
                                        LauncherIcons.clearPool(getContext());
                                        // forceReload submits LoaderTask to MODEL_EXECUTOR
                                        app.getModel().forceReload();
                                        // Queue after LoaderTask — MODEL_EXECUTOR is serial,
                                        // so this runs once icons are fully loaded.
                                        Executors.MODEL_EXECUTOR.execute(() ->
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                if (getContext() != null) dialog.dismiss();
                                            })
                                        );
                                    });
                                });
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void updateIconShapeSummary(Preference pref) {
            String current = LauncherPrefs.get(getContext()).get(ThemeManager.PREF_ICON_SHAPE);
            if (current == null || current.isEmpty()) {
                pref.setSummary(R.string.icon_shape_default);
                return;
            }
            pref.setSummary(getShapeDisplayName(current));
        }

        private CharSequence getShapeDisplayName(String key) {
            if (key == null || key.isEmpty()) return getString(R.string.icon_shape_default);
            switch (key) {
                case ShapesProvider.CIRCLE_KEY: return getString(R.string.icon_shape_circle);
                case ShapesProvider.SQUARE_KEY: return getString(R.string.icon_shape_square);
                case ShapesProvider.FOUR_SIDED_COOKIE_KEY:
                    return getString(R.string.icon_shape_four_sided_cookie);
                case ShapesProvider.SEVEN_SIDED_COOKIE_KEY:
                    return getString(R.string.icon_shape_seven_sided_cookie);
                case ShapesProvider.ARCH_KEY: return getString(R.string.icon_shape_arch);
                case ShapesProvider.NONE_KEY: return getString(R.string.icon_shape_none);
                default: return key;
            }
        }

        private void showIconShapeDialog(Preference pref) {
            IconShapeModel[] shapes = ShapesProvider.INSTANCE.getIconShapes();

            // Build labels: "System default" + all shape names
            List<CharSequence> labels = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            labels.add(getString(R.string.icon_shape_default));
            keys.add("");
            for (IconShapeModel shape : shapes) {
                labels.add(getShapeDisplayName(shape.getKey()));
                keys.add(shape.getKey());
            }

            String current = LauncherPrefs.get(getContext()).get(ThemeManager.PREF_ICON_SHAPE);
            int selected = Math.max(0, keys.indexOf(current));

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.icon_shape_title)
                    .setSingleChoiceItems(
                            labels.toArray(new CharSequence[0]), selected,
                            (dialog, which) -> {
                                String key = keys.get(which);
                                LauncherPrefs.get(getContext())
                                        .put(ThemeManager.PREF_ICON_SHAPE, key);
                                updateIconShapeSummary(pref);
                                dialog.dismiss();
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }
}
