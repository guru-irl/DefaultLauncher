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

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.CornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
public class SettingsActivity extends AppCompatActivity
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
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        setSupportActionBar(findViewById(R.id.action_bar));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Set toolbar title with Dancing Script font
        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        if (collapsingToolbar != null) {
            collapsingToolbar.setTitle(getString(R.string.settings_title));
            Typeface dancingScript = getResources().getFont(R.font.dancing_script);
            if (dancingScript != null) {
                collapsingToolbar.setCollapsedTitleTypeface(dancingScript);
                collapsingToolbar.setExpandedTitleTypeface(dancingScript);
            }
        }

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FRAGMENT_ROOT_KEY) || intent.hasExtra(EXTRA_FRAGMENT_ARGS)
                || intent.hasExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY)) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
            M3SliderPreference rowGapPref = findPreference("pref_allapps_row_gap");
            if (rowGapPref != null) {
                rowGapPref.setStepSize(8f);
                rowGapPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    int raw = (int) newValue;
                    int snapped = (int) InvariantDeviceProfile.snapToNearestGap(raw);
                    if (snapped != raw) {
                        ((M3SliderPreference) pref).setValue(snapped);
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

            // Icon size picker — inline binding happens in onViewCreated
            Preference iconSizePref = findPreference("pref_icon_size_scale");
            if (iconSizePref != null) {
                updateIconSizeSummary(iconSizePref);
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

            // Card group decoration for Lawnchair-style sectioned cards
            RecyclerView rv = getListView();
            rv.addItemDecoration(new CardGroupItemDecoration(getContext()));

            // Bind custom preference layouts when their views attach to RecyclerView
            rv.addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View child) {
                    if (child.findViewById(R.id.size_toggle_group) != null) {
                        bindIconSizeInline(child);
                    }
                }
                @Override
                public void onChildViewDetachedFromWindow(View view) { }
            });
        }

        private boolean mIconSizeBound = false;
        private int mLastPresetButtonId = View.NO_ID;
        private static final long CORNER_ANIM_DURATION = 250L;

        private void animateButtonCorners(MaterialButton btn, boolean toPill) {
            float density = getResources().getDisplayMetrics().density;
            float innerRadius = 8 * density;

            // Run after the group finishes its shape calculation
            btn.post(() -> {
                float pillRadius = btn.getHeight() / 2f;
                if (pillRadius <= 0) pillRadius = 20 * density;

                float startRadius = toPill ? innerRadius : pillRadius;
                float endRadius = toPill ? pillRadius : innerRadius;

                ValueAnimator anim = ValueAnimator.ofFloat(startRadius, endRadius);
                anim.setDuration(CORNER_ANIM_DURATION);
                anim.setInterpolator(
                        new androidx.interpolator.view.animation
                                .FastOutSlowInInterpolator());
                anim.addUpdateListener(a -> {
                    float r = (float) a.getAnimatedValue();
                    btn.setShapeAppearanceModel(
                            btn.getShapeAppearanceModel().toBuilder()
                                    .setAllCornerSizes(r)
                                    .build());
                });
                anim.start();
            });
        }

        private void bindIconSizeInline(View child) {
            if (mIconSizeBound) return;
            mIconSizeBound = true;

            Preference iconSizePref = findPreference("pref_icon_size_scale");
            if (iconSizePref == null) return;

            MaterialButtonToggleGroup toggleGroup =
                    child.findViewById(R.id.size_toggle_group);

            String current = LauncherPrefs.get(getContext())
                    .get(LauncherPrefs.ICON_SIZE_SCALE);

            // Pre-select the matching preset button, or select custom star
            int[] btnIds = {R.id.btn_size_s, R.id.btn_size_m,
                    R.id.btn_size_l, R.id.btn_size_xl};
            boolean isPreset = false;
            for (int j = 0; j < SIZE_PRESETS.length; j++) {
                if (SIZE_PRESETS[j].equals(current)) {
                    toggleGroup.check(btnIds[j]);
                    mLastPresetButtonId = btnIds[j];
                    isPreset = true;
                    break;
                }
            }
            if (!isPreset) {
                toggleGroup.check(R.id.btn_size_custom);
            }

            // Set pill shape on initial selection (no animation on first load)
            toggleGroup.post(() -> {
                for (int i = 0; i < toggleGroup.getChildCount(); i++) {
                    View c = toggleGroup.getChildAt(i);
                    if (c instanceof MaterialButton && ((MaterialButton) c).isChecked()) {
                        float pill = c.getHeight() / 2f;
                        if (pill > 0) {
                            ((MaterialButton) c).setShapeAppearanceModel(
                                    ShapeAppearanceModel.builder()
                                            .setAllCornerSizes(pill)
                                            .build());
                        }
                    }
                }
            });

            toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                MaterialButton btn = group.findViewById(checkedId);
                if (btn != null) {
                    // Animate to pill when checked, back to group shape when unchecked
                    animateButtonCorners(btn, isChecked);
                }

                if (!isChecked) return;

                if (checkedId == R.id.btn_size_custom) {
                    showCustomIconSizeDialog(toggleGroup, iconSizePref);
                    return;
                }

                // Remember last preset selection for cancel-revert
                mLastPresetButtonId = checkedId;

                if (btn != null) {
                    String value = (String) btn.getTag();
                    LauncherPrefs.get(getContext())
                            .put(LauncherPrefs.ICON_SIZE_SCALE, value);
                    updateIconSizeSummary(iconSizePref);
                    getListView().post(() ->
                        InvariantDeviceProfile.INSTANCE.get(getContext())
                                .onConfigChanged(getContext()));
                }
            });
        }

        private void showCustomIconSizeDialog(
                MaterialButtonToggleGroup toggleGroup, Preference iconSizePref) {
            Context ctx = getContext();
            float density = ctx.getResources().getDisplayMetrics().density;

            TextInputLayout inputLayout = new TextInputLayout(ctx,
                    null, com.google.android.material.R.attr.textInputOutlinedStyle);
            inputLayout.setHint("Icon size (50\u2013100%)");
            int hPad = (int) (24 * density);
            int tPad = (int) (16 * density);
            inputLayout.setPadding(hPad, tPad, hPad, 0);

            TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
            editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            inputLayout.addView(editText);

            // Pre-fill with current value as percentage
            try {
                String cur = LauncherPrefs.get(ctx)
                        .get(LauncherPrefs.ICON_SIZE_SCALE);
                float pct = Float.parseFloat(cur) * 100f;
                editText.setText(String.format("%.0f", pct));
            } catch (NumberFormatException ignored) { }

            Runnable revertSelection = () -> {
                if (mLastPresetButtonId != View.NO_ID) {
                    toggleGroup.check(mLastPresetButtonId);
                } else {
                    toggleGroup.clearChecked();
                }
            };

            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.icon_size_custom)
                    .setView(inputLayout)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String text = editText.getText() != null
                                ? editText.getText().toString().trim() : "";
                        try {
                            float pct = Float.parseFloat(text);
                            pct = Math.max(50f, Math.min(100f, pct));
                            String value = String.valueOf(pct / 100f);
                            LauncherPrefs.get(ctx)
                                    .put(LauncherPrefs.ICON_SIZE_SCALE, value);
                            updateIconSizeSummary(iconSizePref);
                            getListView().post(() ->
                                InvariantDeviceProfile.INSTANCE.get(ctx)
                                        .onConfigChanged(ctx));
                        } catch (NumberFormatException ignored) {
                            revertSelection.run();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> revertSelection.run())
                    .setOnCancelListener(dialog -> revertSelection.run())
                    .show();
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

            Context ctx = getContext();
            float density = ctx.getResources().getDisplayMetrics().density;
            BottomSheetDialog sheet = new BottomSheetDialog(ctx);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(0, 0, 0, (int) (24 * density));

            addSheetHandle(root, ctx, density);

            TextView titleView = new TextView(ctx);
            titleView.setText(R.string.icon_pack_title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            titleView.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
            titleView.setPadding(
                    (int) (24 * density), (int) (16 * density),
                    (int) (24 * density), (int) (16 * density));
            root.addView(titleView);

            LinearLayout itemsContainer = new LinearLayout(ctx);
            itemsContainer.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                TextView item = createSheetItem(ctx, density,
                        labels.get(i), i == selected);
                item.setOnClickListener(v -> {
                    String pkg = pkgs.get(idx);
                    LauncherPrefs.get(ctx).put(LauncherPrefs.ICON_PACK, pkg);
                    mgr.invalidate();

                    // Show loading state
                    titleView.setText(R.string.icon_pack_applying);
                    for (int j = 0; j < itemsContainer.getChildCount(); j++) {
                        itemsContainer.getChildAt(j).setEnabled(false);
                        itemsContainer.getChildAt(j).setAlpha(0.38f);
                    }

                    LauncherAppState app = LauncherAppState.INSTANCE.get(ctx);
                    Executors.MODEL_EXECUTOR.execute(() -> {
                        mgr.getCurrentPack();
                        app.getIconCache().clearAllIcons();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (getContext() == null) return;
                            updateIconPackSummary(pref, mgr);
                            LauncherIcons.clearPool(ctx);
                            app.getModel().forceReload();
                            Executors.MODEL_EXECUTOR.execute(() ->
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (getContext() != null) sheet.dismiss();
                                })
                            );
                        });
                    });
                });
                itemsContainer.addView(item);
            }

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(itemsContainer);
            root.addView(scroll);

            sheet.setContentView(root);
            sheet.show();
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

            Context ctx = getContext();
            float density = ctx.getResources().getDisplayMetrics().density;
            BottomSheetDialog sheet = new BottomSheetDialog(ctx);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(0, 0, 0, (int) (24 * density));

            addSheetHandle(root, ctx, density);

            TextView titleView = new TextView(ctx);
            titleView.setText(R.string.icon_shape_title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            titleView.setTextColor(ctx.getColor(R.color.materialColorOnSurface));
            titleView.setPadding(
                    (int) (24 * density), (int) (16 * density),
                    (int) (24 * density), (int) (16 * density));
            root.addView(titleView);

            LinearLayout itemsContainer = new LinearLayout(ctx);
            itemsContainer.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                TextView item = createSheetItem(ctx, density,
                        labels.get(i), i == selected);
                item.setOnClickListener(v -> {
                    String key = keys.get(idx);
                    LauncherPrefs.get(ctx)
                            .put(ThemeManager.PREF_ICON_SHAPE, key);
                    updateIconShapeSummary(pref);
                    sheet.dismiss();
                });
                itemsContainer.addView(item);
            }

            ScrollView scroll = new ScrollView(ctx);
            scroll.addView(itemsContainer);
            root.addView(scroll);

            sheet.setContentView(root);
            sheet.show();
        }

        private void addSheetHandle(LinearLayout root, Context ctx, float density) {
            View handle = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(2 * density);
            bg.setColor(ctx.getColor(R.color.materialColorOutline));
            handle.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (32 * density), (int) (4 * density));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = (int) (12 * density);
            handle.setLayoutParams(lp);
            root.addView(handle);
        }

        private TextView createSheetItem(Context ctx, float density,
                CharSequence text, boolean selected) {
            TextView item = new TextView(ctx);
            item.setText(text);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            item.setTextColor(selected
                    ? ctx.getColor(R.color.materialColorPrimary)
                    : ctx.getColor(R.color.materialColorOnSurface));
            item.setMinHeight((int) (56 * density));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(
                    (int) (24 * density), 0,
                    (int) (24 * density), 0);
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true);
            item.setBackgroundResource(tv.resourceId);
            return item;
        }

        private static final String[] SIZE_PRESETS = {"0.8", "0.863", "0.92", "1.0"};
        private static final String[] SIZE_LABELS = {"S (80%)", "M (86%)", "L (92%)", "XL (100%)"};

        private void updateIconSizeSummary(Preference pref) {
            String current = LauncherPrefs.get(getContext()).get(LauncherPrefs.ICON_SIZE_SCALE);
            for (int i = 0; i < SIZE_PRESETS.length; i++) {
                if (SIZE_PRESETS[i].equals(current)) {
                    pref.setSummary(SIZE_LABELS[i]);
                    return;
                }
            }
            // Custom value — show as percentage
            try {
                float pct = Float.parseFloat(current) * 100f;
                pref.setSummary(getString(R.string.icon_size_custom)
                        + " (" + String.format("%.0f%%", pct) + ")");
            } catch (NumberFormatException e) {
                pref.setSummary(getString(R.string.icon_size_custom) + " (" + current + ")");
            }
        }
    }
}
