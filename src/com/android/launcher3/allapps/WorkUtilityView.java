/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.views.ActivityContext;

/**
 * Work profile FAB shown at the bottom of AllApps when a work profile exists.
 * Always shows icon + text (no expand/shrink animation).
 * Translates up to stay above the keyboard when it opens.
 */
public class WorkUtilityView extends LinearLayout implements Insettable,
        KeyboardInsetAnimationCallback.KeyboardInsetListener {

    private final Rect mInsets = new Rect();
    private final Rect mImeInsets = new Rect();
    private final ActivityContext mActivityContext;

    private View mWorkFAB;
    private TextView mPauseText;
    private ImageView mWorkIcon;

    public WorkUtilityView(@NonNull Context context) {
        this(context, null, 0);
    }

    public WorkUtilityView(@NonNull Context context, @NonNull AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkUtilityView(@NonNull Context context, @NonNull AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPauseText = findViewById(R.id.pause_text);
        mWorkIcon = findViewById(R.id.work_icon);
        mWorkFAB = findViewById(R.id.work_mode_toggle);
        setWindowInsetsAnimationCallback(new KeyboardInsetAnimationCallback(this));
        setInsets(mActivityContext.getDeviceProfile().getInsets());
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateTranslationY();
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        if (lp != null) {
            int bottomMargin = getResources().getDimensionPixelSize(R.dimen.work_fab_margin_bottom);
            DeviceProfile dp = mActivityContext.getDeviceProfile();
            if (mActivityContext.getAppsView().isSearchBarFloating()) {
                bottomMargin += dp.hotseatQsbHeight;
            }
            if (!dp.isGestureMode && dp.isTaskbarPresent) {
                bottomMargin += dp.taskbarHeight;
            }
            lp.bottomMargin = bottomMargin;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        boolean isRtl = Utilities.isRtl(getResources());
        int shift = mActivityContext.getDeviceProfile().getAllAppsIconStartMargin(getContext());
        setTranslationX(isRtl ? shift : -shift);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && getVisibility() == VISIBLE;
    }

    /** Fade the entire FAB in or out. */
    public void animateVisibility(boolean visible) {
        clearAnimation();
        if (visible) {
            setVisibility(VISIBLE);
            animate().alpha(1).start();
        } else if (getVisibility() != GONE) {
            animate().alpha(0).withEndAction(() -> setVisibility(GONE)).start();
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this);
        if (compat.isVisible(WindowInsetsCompat.Type.ime())) {
            Insets ime = compat.getInsets(WindowInsetsCompat.Type.ime());
            mImeInsets.set(ime.left, ime.top, ime.right, ime.bottom);
        } else {
            mImeInsets.setEmpty();
        }
        updateTranslationY();
        return super.onApplyWindowInsets(insets);
    }

    void updateTranslationY() {
        setTranslationY(-mImeInsets.bottom);
    }

    @Override
    public void setTranslationY(float translationY) {
        // Always translate at least enough for nav bar insets.
        super.setTranslationY(Math.min(translationY, -mInsets.bottom));
    }

    @Override
    public void onTranslationStart() { }

    @Override
    public void onTranslationEnd() { }

    public Rect getImeInsets() {
        return mImeInsets;
    }

    public View getWorkFAB() {
        return mWorkFAB;
    }



    /**
     * Updates the FAB text and icon for the given work profile state.
     * Click handler is managed by {@link WorkProfileManager}.
     */
    public void updateForState(@UserProfileManager.UserProfileState int state) {
        if (state == UserProfileManager.STATE_DISABLED) {
            mPauseText.setText(R.string.work_apps_enable_btn_text);
            mWorkIcon.setImageResource(R.drawable.ic_corp_off);
        } else {
            mPauseText.setText(R.string.work_apps_pause_btn_text);
            mWorkIcon.setImageResource(R.drawable.ic_corp);
        }
    }
}
