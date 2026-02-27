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
package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_FILTER_BAR;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SECTION_HEADER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item animator for search results.
 *
 * <p>New and moved result items fade in + slide up together per category batch
 * (as each search provider resolves). Structural items (filter bar, section headers)
 * are never animated. Removes and changes are instant to prevent items from
 * overlapping during fast typing.
 *
 * <ul>
 *   <li>ADD: fade-in + slide-up (200ms, EMPHASIZED_DECELERATE)</li>
 *   <li>MOVE: fade-in + slide-up (same as ADD — items re-enter smoothly)</li>
 *   <li>REMOVE: instant — prevents departing items from overlapping incoming ones</li>
 *   <li>CHANGE: instant — content rebinds without crossfade</li>
 * </ul>
 */
public class SearchItemAnimator extends DefaultItemAnimator {

    private static final long ANIM_DURATION_MS = 200;
    private static final float TRANSLATE_Y_DP = 16f;

    /** Tracks whether a pending animation is an add or a move for dispatch. */
    private static class PendingEntry {
        final RecyclerView.ViewHolder holder;
        final boolean isMove;

        PendingEntry(RecyclerView.ViewHolder holder, boolean isMove) {
            this.holder = holder;
            this.isMove = isMove;
        }
    }

    /** Tracks a running animation and its type for proper finish dispatch. */
    private static class RunningEntry {
        final Animator animator;
        final boolean isMove;

        RunningEntry(Animator animator, boolean isMove) {
            this.animator = animator;
            this.isMove = isMove;
        }
    }

    private final List<PendingEntry> mPending = new ArrayList<>();
    private final Map<RecyclerView.ViewHolder, RunningEntry> mRunning = new LinkedHashMap<>();
    private final float mTranslateYPx;

    /**
     * When false, all add/move animations are instant. Used to suppress animations
     * for intermediate (partial) results so only the final batch animates.
     */
    private boolean mAnimationsEnabled = true;

    public SearchItemAnimator(float density) {
        mTranslateYPx = TRANSLATE_Y_DP * density;
        setAddDuration(ANIM_DURATION_MS);
        setMoveDuration(ANIM_DURATION_MS);
    }

    /** Enable or disable add animations. Moves always animate; removes and changes are always instant. */
    public void setAnimationsEnabled(boolean enabled) {
        mAnimationsEnabled = enabled;
    }

    /** Structural items never animate; content items respect {@link #mAnimationsEnabled}. */
    private boolean shouldSkipAnimation(int viewType) {
        return !mAnimationsEnabled
                || viewType == VIEW_TYPE_SEARCH_FILTER_BAR
                || viewType == VIEW_TYPE_SEARCH_SECTION_HEADER;
    }

    /** Structural items never animate; moves always animate (items already exist on screen). */
    private boolean shouldSkipMoveAnimation(int viewType) {
        return viewType == VIEW_TYPE_SEARCH_FILTER_BAR
                || viewType == VIEW_TYPE_SEARCH_SECTION_HEADER;
    }

    // ------------------------------------------------------------------
    // Remove / change — always instant to prevent overlay glitches
    // ------------------------------------------------------------------

    @Override
    public boolean animateRemove(RecyclerView.ViewHolder holder) {
        dispatchRemoveFinished(holder);
        return false;
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder,
            RecyclerView.ViewHolder newHolder,
            int fromLeft, int fromTop, int toLeft, int toTop) {
        if (oldHolder != null) {
            oldHolder.itemView.setAlpha(1f);
            dispatchChangeFinished(oldHolder, true);
        }
        if (newHolder != null && newHolder != oldHolder) {
            newHolder.itemView.setAlpha(1f);
            dispatchChangeFinished(newHolder, false);
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Add — fade+slide for content items, instant for structural items
    // ------------------------------------------------------------------

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        if (shouldSkipAnimation(holder.getItemViewType())) {
            holder.itemView.setAlpha(1f);
            dispatchAddFinished(holder);
            return false;
        }

        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(mTranslateYPx);
        mPending.add(new PendingEntry(holder, false));
        return true;
    }

    // ------------------------------------------------------------------
    // Move — fade+slide (same visual as add) so items don't teleport
    // ------------------------------------------------------------------

    @Override
    public boolean animateMove(RecyclerView.ViewHolder holder,
            int fromX, int fromY, int toX, int toY) {
        // Clear any residual translation from the default move setup
        holder.itemView.setTranslationX(0f);

        // Moves always animate regardless of mAnimationsEnabled — the item already
        // exists on screen, so teleporting it looks wrong even during intermediate
        // result delivery. Only structural items (filter bar, headers) skip.
        if (shouldSkipMoveAnimation(holder.getItemViewType())) {
            holder.itemView.setAlpha(1f);
            holder.itemView.setTranslationY(0f);
            dispatchMoveFinished(holder);
            return false;
        }

        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(mTranslateYPx);
        mPending.add(new PendingEntry(holder, true));
        return true;
    }

    // ------------------------------------------------------------------
    // Run pending — animate all queued adds and moves together
    // ------------------------------------------------------------------

    @Override
    public void runPendingAnimations() {
        if (mPending.isEmpty()) return;

        List<PendingEntry> batch = new ArrayList<>(mPending);
        mPending.clear();

        for (PendingEntry entry : batch) {
            RecyclerView.ViewHolder holder = entry.holder;
            View view = holder.itemView;

            if (entry.isMove) {
                dispatchMoveStarting(holder);
            } else {
                dispatchAddStarting(holder);
            }

            ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
            ObjectAnimator translateY = ObjectAnimator.ofFloat(
                    view, View.TRANSLATION_Y, mTranslateYPx, 0f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, translateY);
            set.setDuration(ANIM_DURATION_MS);
            set.setInterpolator(Interpolators.EMPHASIZED_DECELERATE);

            boolean isMove = entry.isMove;
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                    if (mRunning.remove(holder) != null) {
                        if (isMove) {
                            dispatchMoveFinished(holder);
                        } else {
                            dispatchAddFinished(holder);
                        }
                        if (!isRunning()) {
                            dispatchAnimationsFinished();
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                }
            });
            mRunning.put(holder, new RunningEntry(set, isMove));
            set.start();
        }
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    @Override
    public void endAnimation(RecyclerView.ViewHolder item) {
        // Remove from pending
        for (int i = mPending.size() - 1; i >= 0; i--) {
            if (mPending.get(i).holder == item) {
                mPending.remove(i);
                break;
            }
        }
        // Cancel running
        RunningEntry running = mRunning.remove(item);
        if (running != null) {
            running.animator.cancel();
        }
        item.itemView.setAlpha(1f);
        item.itemView.setTranslationY(0f);
        super.endAnimation(item);
    }

    @Override
    public void endAnimations() {
        // Finish all pending
        for (int i = mPending.size() - 1; i >= 0; i--) {
            PendingEntry entry = mPending.get(i);
            entry.holder.itemView.setAlpha(1f);
            entry.holder.itemView.setTranslationY(0f);
            if (entry.isMove) {
                dispatchMoveFinished(entry.holder);
            } else {
                dispatchAddFinished(entry.holder);
            }
        }
        mPending.clear();

        // Clear map first so cancel() -> onAnimationEnd callbacks skip double-dispatch.
        List<Map.Entry<RecyclerView.ViewHolder, RunningEntry>> running =
                new ArrayList<>(mRunning.entrySet());
        mRunning.clear();
        for (Map.Entry<RecyclerView.ViewHolder, RunningEntry> e : running) {
            e.getValue().animator.cancel();
            e.getKey().itemView.setAlpha(1f);
            e.getKey().itemView.setTranslationY(0f);
            if (e.getValue().isMove) {
                dispatchMoveFinished(e.getKey());
            } else {
                dispatchAddFinished(e.getKey());
            }
        }

        super.endAnimations();
    }

    @Override
    public boolean isRunning() {
        return super.isRunning() || !mPending.isEmpty() || !mRunning.isEmpty();
    }
}
