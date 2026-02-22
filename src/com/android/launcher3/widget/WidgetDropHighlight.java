/*
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

package com.android.launcher3.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.launcher3.anim.SpringAnimationBuilder;

/**
 * Shared drop highlight animation for widget stacks.
 * Scales the target ViewGroup's children down while showing a colored background,
 * using spring-based animation matching M3 Expressive bounce tokens.
 *
 * Used by both {@link WidgetStackView} (add-to-stack hover) and
 * {@link com.android.launcher3.Workspace} (create-stack hover).
 */
public class WidgetDropHighlight {

    // Children scale down to this at full highlight
    private static final float SCALE_MIN = 0.85f;
    private static final float SCALE_RANGE = 1f - SCALE_MIN; // 0.15

    // Background alpha at full highlight (~90% of 255)
    private static final int BG_ALPHA = 230;

    private final ViewGroup mTarget;
    private final GradientDrawable mBgDrawable;
    private final float mGentleDamping;
    private final float mGentleStiffness;
    private final float mSmoothDamping;
    private final float mSmoothStiffness;
    private ValueAnimator mAnimator;
    private float mProgress = 0f;

    private static final FloatProperty<WidgetDropHighlight> PROGRESS =
            new FloatProperty<>("dropHighlightProgress") {
                @Override
                public void setValue(WidgetDropHighlight h, float val) {
                    h.applyProgress(val);
                }

                @Override
                public Float get(WidgetDropHighlight h) {
                    return h.mProgress;
                }
            };

    public WidgetDropHighlight(ViewGroup target) {
        mTarget = target;
        Context context = target.getContext();
        Resources res = context.getResources();
        mGentleDamping = res.getFloat(R.dimen.m3_bounce_gentle_damping);
        mGentleStiffness = res.getFloat(R.dimen.m3_bounce_gentle_stiffness);
        mSmoothDamping = res.getFloat(R.dimen.m3_bounce_smooth_damping);
        mSmoothStiffness = res.getFloat(R.dimen.m3_bounce_smooth_stiffness);
        float radius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        int color = ContextCompat.getColor(context,
                R.color.materialColorPrimaryContainer);
        mBgDrawable = new GradientDrawable();
        mBgDrawable.setColor(color);
        mBgDrawable.setCornerRadius(radius);
        mBgDrawable.setAlpha(0);
    }

    /**
     * Shows the drop highlight with a gentle spring (hover feedback).
     */
    public void show() {
        animateTo(1f, mGentleDamping, mGentleStiffness);
    }

    /**
     * Clears the drop highlight with a smooth spring (return to rest).
     */
    public void clear() {
        animateTo(0f, mSmoothDamping, mSmoothStiffness);
    }

    /**
     * Force-cancels the spring animator and resets to rest state immediately.
     * Call when the target view is being detached or recycled.
     */
    public void cancel() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        applyProgress(0f);
    }

    private void animateTo(float target, float damping, float stiffness) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = new SpringAnimationBuilder(mTarget.getContext())
                .setStartValue(mProgress)
                .setEndValue(target)
                .setDampingRatio(damping)
                .setStiffness(stiffness)
                .setMinimumVisibleChange(1f / 100f)
                .build(this, PROGRESS);
        mAnimator.start();
    }

    private void applyProgress(float progress) {
        mProgress = progress;
        float scale = 1f - SCALE_RANGE * progress;
        for (int i = 0; i < mTarget.getChildCount(); i++) {
            View child = mTarget.getChildAt(i);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
        int alpha = (int) (BG_ALPHA * progress);
        mBgDrawable.setAlpha(alpha);
        if (progress > 0f) {
            if (mTarget.getBackground() != mBgDrawable) {
                mTarget.setBackground(mBgDrawable);
            }
        } else {
            mTarget.setBackground(null);
        }
        mTarget.invalidate();
    }
}
