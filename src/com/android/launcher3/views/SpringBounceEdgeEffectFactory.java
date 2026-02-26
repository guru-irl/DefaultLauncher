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
package com.android.launcher3.views;

import android.graphics.Canvas;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

/**
 * EdgeEffectFactory that replaces the default overscroll stretch with M3 Expressive
 * spring-physics translationY bounce on the RecyclerView.
 */
public class SpringBounceEdgeEffectFactory extends RecyclerView.EdgeEffectFactory {

    private static final float MAX_OVERSCROLL_DP = 36f;
    private static final float SPRING_STIFFNESS = 300f;
    private static final float SPRING_DAMPING_RATIO = 0.9f;

    private final RecyclerView mRecyclerView;
    private final float mMaxOverscrollPx;

    public SpringBounceEdgeEffectFactory(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        mMaxOverscrollPx = MAX_OVERSCROLL_DP * recyclerView.getResources()
                .getDisplayMetrics().density;
    }

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
        return new SpringBounceEdgeEffect(direction);
    }

    /**
     * Applies cubic damping: large pulls have diminishing returns.
     */
    private float dampedOverscroll(float totalDistance, int direction) {
        float clamped = Math.min(Math.abs(totalDistance), 1f);
        float damped = (1f - (1f - clamped) * (1f - clamped) * (1f - clamped));
        return damped * mMaxOverscrollPx * direction;
    }

    private class SpringBounceEdgeEffect extends EdgeEffect {

        private final int mDirection; // -1 for top (translate down), +1 for bottom (translate up)
        private float mPullDistance;
        private float mCurrentOverscroll;
        private final SpringAnimation mSpringAnimation;

        SpringBounceEdgeEffect(int factoryDirection) {
            super(mRecyclerView.getContext());
            // DIRECTION_TOP = 0 → translate positive (down), DIRECTION_BOTTOM = 2 → translate negative (up)
            mDirection = (factoryDirection == DIRECTION_TOP) ? 1 : -1;
            mSpringAnimation = createSpringAnimation();
        }

        private SpringAnimation createSpringAnimation() {
            SpringAnimation anim = new SpringAnimation(
                    mRecyclerView,
                    new FloatPropertyCompat<RecyclerView>("overscrollTranslationY") {
                        @Override
                        public float getValue(RecyclerView view) {
                            return mCurrentOverscroll;
                        }

                        @Override
                        public void setValue(RecyclerView view, float value) {
                            mCurrentOverscroll = value;
                            view.setTranslationY(value);
                        }
                    },
                    0f);
            anim.setMinimumVisibleChange(0.5f);
            anim.getSpring()
                    .setStiffness(SPRING_STIFFNESS)
                    .setDampingRatio(SPRING_DAMPING_RATIO)
                    .setFinalPosition(0f);
            return anim;
        }

        @Override
        public void onPull(float deltaDistance) {
            onPull(deltaDistance, 0.5f);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            if (mSpringAnimation.isRunning()) {
                mSpringAnimation.cancel();
            }
            mPullDistance += deltaDistance;
            mCurrentOverscroll = dampedOverscroll(mPullDistance, mDirection);
            mRecyclerView.setTranslationY(mCurrentOverscroll);
        }

        @Override
        public void onRelease() {
            mPullDistance = 0f;
            if (mCurrentOverscroll != 0f) {
                mSpringAnimation.setStartValue(mCurrentOverscroll);
                mSpringAnimation.start();
            }
        }

        @Override
        public void onAbsorb(int velocity) {
            float startVelocity = velocity * mDirection;
            mSpringAnimation.setStartVelocity(startVelocity);
            mSpringAnimation.setStartValue(mCurrentOverscroll);
            mSpringAnimation.start();
        }

        @Override
        public boolean draw(Canvas canvas) {
            // No canvas-based rendering — visual feedback is via translationY
            return false;
        }

        @Override
        public boolean isFinished() {
            return !mSpringAnimation.isRunning() && mCurrentOverscroll == 0f;
        }

        @Override
        public void finish() {
            mPullDistance = 0f;
            if (mSpringAnimation.isRunning()) {
                mSpringAnimation.cancel();
            }
            mCurrentOverscroll = 0f;
            mRecyclerView.setTranslationY(0f);
        }
    }
}
