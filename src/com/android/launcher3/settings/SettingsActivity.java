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
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.color.DynamicColors;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SettingsCache;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends AppCompatActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    @VisibleForTesting
    static final String DEVELOPER_OPTIONS_KEY = "pref_developer_options";

    public static final String FIXED_LANDSCAPE_MODE = "pref_fixed_landscape_mode";

    private static final String NOTIFICATION_DOTS_PREFERENCE_KEY = "pref_icon_badging";

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";
    public static final String EXTRA_FRAGMENT_CLASS = ":settings:fragment_class";
    public static final String EXTRA_FRAGMENT_TITLE = ":settings:fragment_title";

    // Intent extra to indicate the pref-key to highlighted when opening the settings activity
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    // Intent extra to indicate the pref-key of the root screen when opening the settings activity
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        // Set page background: cards-brighter-than-bg pattern needs different tokens per mode
        // Light: surfaceContainer (darker) vs cards surface (brighter) → ~12 unit diff
        // Dark: surface (darker) vs cards surfaceContainer (brighter) → ~12 unit diff
        boolean isDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bgColor = com.android.launcher3.util.Themes.getAttrColor(this, isDark
                ? com.google.android.material.R.attr.colorSurface
                : com.google.android.material.R.attr.colorSurfaceContainer);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        getWindow().getDecorView().setBackgroundColor(bgColor);

        // Ensure status bar icons are readable against the background
        androidx.core.view.WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDark);
        insetsController.setAppearanceLightNavigationBars(!isDark);

        setSupportActionBar(findViewById(R.id.action_bar));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Prevent M3 AppBarLayout from changing color on scroll (lifted state)
        com.google.android.material.appbar.AppBarLayout appBar = findViewById(R.id.app_bar);
        if (appBar != null) {
            appBar.setLiftable(false);
        }

        Intent intent = getIntent();
        String fragmentClass = intent.getStringExtra(EXTRA_FRAGMENT_CLASS);
        boolean isSubPage = !TextUtils.isEmpty(fragmentClass)
                && !fragmentClass.equals(getString(R.string.settings_fragment_name));

        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);

        Resources res = getResources();

        if (isSubPage) {
            // Sub-page: back button, sub-page title, collapsing toolbar with regular font
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            String subTitle = intent.getStringExtra(EXTRA_FRAGMENT_TITLE);
            if (collapsingToolbar != null) {
                if (!TextUtils.isEmpty(subTitle)) {
                    collapsingToolbar.setTitle(subTitle);
                }
                collapsingToolbar.setCollapsedTitleTypeface(Typeface.DEFAULT);
                collapsingToolbar.setExpandedTitleTypeface(Typeface.DEFAULT);
                // Sub-pages: 140dp height, centered title
                ViewGroup.LayoutParams lp = collapsingToolbar.getLayoutParams();
                lp.height = res.getDimensionPixelSize(R.dimen.settings_toolbar_height_collapsed);
                collapsingToolbar.setLayoutParams(lp);
                collapsingToolbar.setExpandedTitleGravity(
                        Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                collapsingToolbar.setExpandedTitleMarginStart(0);
                collapsingToolbar.setExpandedTitleMarginEnd(0);
            } else {
                if (!TextUtils.isEmpty(subTitle)) {
                    getSupportActionBar().setTitle(subTitle);
                }
            }
        } else {
            // Main settings page: Danfo title, 200dp, centered
            if (collapsingToolbar != null) {
                collapsingToolbar.setTitle(getString(R.string.settings_title));
                collapsingToolbar.setExpandedTitleTextAppearance(
                        R.style.HomeSettings_ExpandedToolbarTitle_Main);
                Typeface danfo = res.getFont(R.font.danfo);
                if (danfo != null) {
                    collapsingToolbar.setCollapsedTitleTypeface(danfo);
                    collapsingToolbar.setExpandedTitleTypeface(danfo);
                }
                // Main page: 200dp height, centered title
                ViewGroup.LayoutParams lp = collapsingToolbar.getLayoutParams();
                lp.height = res.getDimensionPixelSize(R.dimen.settings_toolbar_height_expanded);
                collapsingToolbar.setLayoutParams(lp);
                collapsingToolbar.setExpandedTitleGravity(
                        Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
                collapsingToolbar.setExpandedTitleMarginStart(0);
                collapsingToolbar.setExpandedTitleMarginEnd(0);
            }
            if (intent.hasExtra(EXTRA_FRAGMENT_ROOT_KEY) || intent.hasExtra(EXTRA_FRAGMENT_ARGS)
                    || intent.hasExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY)) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
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

            String resolvedFragment = fragmentClass;
            if (TextUtils.isEmpty(resolvedFragment)) {
                resolvedFragment = getString(R.string.settings_fragment_name);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    resolvedFragment);
            f.setArguments(args);
            // Display the fragment as the main content.
            fm.beginTransaction().replace(R.id.content_frame, f).commit();
        }
    }

    private boolean startPreference(String fragment, Bundle args, String key,
            CharSequence title) {
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
            Intent intent = new Intent(this, SettingsActivity.class)
                    .putExtra(EXTRA_FRAGMENT_CLASS, fragment)
                    .putExtra(EXTRA_FRAGMENT_ARGS, args);
            if (title != null) {
                intent.putExtra(EXTRA_FRAGMENT_TITLE, title.toString());
            }
            startActivity(intent);
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey(),
                pref.getTitle());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.settings_fragment_name), args, pref.getKey(),
                pref.getTitle());
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
     * This fragment shows the launcher preferences (main page).
     * Icon pack/shape/size dialogs have been moved to sub-page fragments
     * (HomeScreenFragment, AppDrawerFragment) via IconSettingsHelper.
     */
    public static class LauncherSettingsFragment extends PreferenceFragmentCompat implements
            SettingsCache.OnChangeListener {

        private static final int REQUEST_CODE_SET_DEFAULT = 1001;

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

            // Show "Set as default" banner if not already the default home app
            RoleManager rm = getContext().getSystemService(RoleManager.class);
            if (rm != null && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                Preference banner = new Preference(getContext());
                banner.setKey("pref_set_default_banner");
                banner.setLayoutResource(R.layout.preference_set_default_banner);
                banner.setOrder(-1);
                banner.setOnPreferenceClickListener(pref -> {
                    Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_HOME);
                    startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT);
                    return true;
                });
                getPreferenceScreen().addPreference(banner);
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

            // Card group decoration for Lawnchair-style sectioned cards
            RecyclerView rv = getListView();
            rv.addItemDecoration(new CardGroupItemDecoration(getContext()));

            // Fix icon centering: equalize card-edge-to-icon and icon-to-text gaps.
            // Library image_frame.xml uses @+id/icon_frame (R.id, NOT android.R.id)
            // with minWidth=56dp + paddingEnd=8dp → 32dp icon-to-text vs 16dp card-to-icon.
            // Fix: strip minWidth, add 4dp breathing room on all sides, 20dp end
            // to balance: card-to-icon = 16dp(list) + 4dp(icon) = 20dp = icon-to-text.
            final Resources res = getResources();
            final int iconPad = res.getDimensionPixelSize(R.dimen.settings_icon_padding);
            final int endPad = res.getDimensionPixelSize(R.dimen.settings_icon_end_padding);
            rv.addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View child) {
                    View iconFrame = child.findViewById(
                            androidx.preference.R.id.icon_frame);
                    if (iconFrame != null) {
                        iconFrame.setMinimumWidth(0);
                        iconFrame.setPaddingRelative(iconPad, iconPad, endPad, iconPad);
                    }
                }
                @Override
                public void onChildViewDetachedFromWindow(View v) { }
            });
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
                        return false;
                    }
                    preference.setDefaultValue(RotationHelper.getAllowRotationDefaultValue(info));
                    return true;
                case DEVELOPER_OPTIONS_KEY:
                    if (IS_STUDIO_BUILD) {
                        preference.setOrder(0);
                    }
                    return mDeveloperOptionsEnabled;
                case FIXED_LANDSCAPE_MODE:
                    if (!Flags.oneGridSpecs()
                            || InvariantDeviceProfile.INSTANCE.get(getContext()).deviceType
                            == TYPE_MULTI_DISPLAY
                            || InvariantDeviceProfile.INSTANCE.get(getContext()).deviceType
                            == TYPE_TABLET) {
                        return false;
                    }
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

            // Remove "Set as default" banner if now the default home app
            RoleManager rm = getContext().getSystemService(RoleManager.class);
            Preference banner = findPreference("pref_set_default_banner");
            if (banner != null && rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                getPreferenceScreen().removePreference(banner);
            }

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
    }
}
