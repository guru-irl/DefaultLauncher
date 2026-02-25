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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.app.animation.Interpolators;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.anim.M3Durations;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A FrameLayout that holds multiple widget views in a stack, showing one at a time.
 * Supports horizontal swipe to cycle through widgets and draws page indicator dots.
 * Implements {@link DraggableView} so the whole stack can be dragged as a single unit.
 */
public class WidgetStackView extends FrameLayout implements DraggableView, Reorderable {

    private static final String TAG = "WidgetStackView";
    private static final int FLING_THRESHOLD_VELOCITY = 500; // dp/s
    private static final float SNAP_THRESHOLD = 0.5f;

    // Dot indicator constants — uniform size, M3 colors
    private static final float DOT_RADIUS_DP = 3f;
    private static final float DOT_SPACING_DP = 8f;
    private static final float DOT_BOTTOM_MARGIN_DP = 8f;
    private static final int INACTIVE_DOT_ALPHA = 102; // ~40% of 255
    private static final long DOT_AUTO_HIDE_DELAY_MS = 1500;

    /** Maximum number of widgets that can be stacked together. */
    public static final int MAX_STACK_SIZE = WidgetStackInfo.MAX_STACK_SIZE;

    // Swipe zoom: active widget scales down to this during a full-width swipe
    private static final float SWIPE_SCALE_MIN = 0.92f;
    private static final float SWIPE_SCALE_RANGE = 1f - SWIPE_SCALE_MIN; // 0.08

    // Drop highlight shared helper — scales children + draws background
    private WidgetDropHighlight mDropHighlight;

    // Reorderable support for smooth workspace reorder animations
    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;

    private final ArrayList<View> mWidgetViews = new ArrayList<>();
    private int mActiveIndex = 0;

    private final int mTouchSlop;
    private final float mDensity;
    private final Paint mDotPaintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mDotActiveColor;
    private final int mDotInactiveColor;
    private final CheckLongPressHelper mLongPressHelper;

    private float mDownX;
    private float mDownY;
    private boolean mIntercepting = false;
    private boolean mSwiping = false;
    private float mSwipeOffset = 0f;
    private VelocityTracker mVelocityTracker;
    private ValueAnimator mSnapAnimator;

    // Dot auto-hide animation state
    private float mDotAlpha = 0f;
    private ValueAnimator mDotFadeAnimator;
    private Runnable mDotHideRunnable;

    /** Functional interface for accumulating a value from provider info across all children. */
    @FunctionalInterface
    private interface ProviderInfoAccumulator {
        int apply(int current, LauncherAppWidgetProviderInfo pInfo);
    }

    public WidgetStackView(Context context) {
        this(context, null);
    }

    public WidgetStackView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetStackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(true);

        // Rounded corners matching widget enforcement radius
        setClipToOutline(true);
        float enforcedRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), enforcedRadius);
            }
        });

        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mDensity = context.getResources().getDisplayMetrics().density;

        // Long-press helper for initiating workspace drag on the whole stack.
        // Uses the same 0.75x timeout factor as individual widgets.
        mLongPressHelper = new CheckLongPressHelper(this);

        mDotActiveColor = ContextCompat.getColor(context, R.color.materialColorPrimary);
        mDotInactiveColor = ContextCompat.getColor(context, R.color.materialColorOutline);

        mDotPaintActive.setColor(mDotActiveColor);
        mDotPaintActive.setStyle(Paint.Style.FILL);
        mDotPaintInactive.setColor(mDotInactiveColor);
        mDotPaintInactive.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateChildVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLongPressHelper.cancelLongPress();
        cancelSnapAnimator();
        recycleVelocityTracker();
        cancelDotHide();
        if (mDotFadeAnimator != null) {
            mDotFadeAnimator.cancel();
            mDotFadeAnimator = null;
        }
        if (mDropHighlight != null) {
            mDropHighlight.cancel();
            mDropHighlight = null;
        }
    }

    // --- Reorderable implementation ---

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        cancelSnapAnimator();
        resetChildTransforms();
        updateChildVisibility();
        mScaleForReorderBounce = scale;
        setScaleX(scale);
        setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    // --- DraggableView implementation ---

    @Override
    public int getViewType() {
        return DRAGGABLE_WIDGET;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        bounds.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        // Hide non-active children so only the active widget is drawn into the drag bitmap.
        // Also cancel any running swipe animation to get a clean snapshot.
        cancelSnapAnimator();
        for (int i = 0; i < mWidgetViews.size(); i++) {
            View child = mWidgetViews.get(i);
            child.setTranslationX(0f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setVisibility(i == mActiveIndex ? VISIBLE : INVISIBLE);
        }
        return () -> updateChildVisibility();
    }

    /**
     * Adds a widget host view to this stack. The view is added as a child but made
     * INVISIBLE unless it's the active widget. Individual widget long-press is disabled
     * to prevent child widgets from starting their own drags.
     */
    public void addWidgetView(AppWidgetHostView hostView, LauncherAppWidgetInfo info) {
        hostView.setTag(info);

        // Disable individual widget long-press — the stack handles drag as a unit
        hostView.setOnLongClickListener(null);
        hostView.setLongClickable(false);

        // Reset any stale layout state from previous parent
        hostView.setTranslationX(0);
        hostView.setTranslationY(0);
        hostView.setScaleX(1f);
        hostView.setScaleY(1f);
        hostView.setAlpha(1f);
        hostView.setVisibility(View.VISIBLE);

        mWidgetViews.add(hostView);
        addView(hostView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        updateChildVisibility();
    }

    /**
     * Sets which widget is currently visible.
     */
    public void setActiveIndex(int index) {
        if (mWidgetViews.isEmpty()) return;
        mActiveIndex = Math.max(0, Math.min(index, mWidgetViews.size() - 1));
        WidgetStackInfo info = getStackInfo();
        if (info != null) {
            info.setActiveIndex(mActiveIndex);
        }
        updateChildVisibility();
        invalidate();
    }

    public int getActiveIndex() {
        return mActiveIndex;
    }

    public int getWidgetCount() {
        return mWidgetViews.size();
    }

    /**
     * Rebuilds the child view list to match the current contents of the associated
     * {@link WidgetStackInfo}. Removes stale child views whose tag no longer appears
     * in the stack info, then clamps the active index and updates visibility.
     */
    public void rebuildFromStackInfo() {
        WidgetStackInfo info = getStackInfo();
        if (info == null) return;

        // Build map of ItemInfo ID -> child view for quick lookup
        java.util.HashMap<Integer, View> viewById = new java.util.HashMap<>();
        for (View child : mWidgetViews) {
            Object tag = child.getTag();
            if (tag instanceof ItemInfo itemInfo) {
                viewById.put(itemInfo.id, child);
            }
        }

        // Build set of valid IDs from the current contents
        Set<Integer> validIds = new HashSet<>();
        for (ItemInfo item : info.getContents()) {
            validIds.add(item.id);
        }

        // Remove child views whose backing data was removed
        for (int i = mWidgetViews.size() - 1; i >= 0; i--) {
            View child = mWidgetViews.get(i);
            Object tag = child.getTag();
            if (tag instanceof ItemInfo itemInfo && !validIds.contains(itemInfo.id)) {
                mWidgetViews.remove(i);
                removeView(child);
            }
        }

        // Reorder mWidgetViews to match the order in info.getContents()
        ArrayList<View> reordered = new ArrayList<>();
        for (ItemInfo item : info.getContents()) {
            View v = viewById.get(item.id);
            if (v != null && validIds.contains(item.id)) {
                reordered.add(v);
            }
        }
        // Only replace if sizes match (all views accounted for)
        if (reordered.size() == mWidgetViews.size()) {
            mWidgetViews.clear();
            mWidgetViews.addAll(reordered);
        }

        // Clamp and sync active index
        if (!mWidgetViews.isEmpty()) {
            mActiveIndex = Math.max(0, Math.min(info.getActiveIndex(),
                    mWidgetViews.size() - 1));
        } else {
            mActiveIndex = 0;
        }
        updateChildVisibility();
        invalidate();
    }

    @Nullable
    public WidgetStackInfo getStackInfo() {
        Object tag = getTag();
        return tag instanceof WidgetStackInfo ? (WidgetStackInfo) tag : null;
    }

    /**
     * Shows the widget stack popup and returns a PreDragCondition so the drag system
     * can start a deferred drag (same pattern as FolderIcon.startLongPressAction).
     */
    public DragOptions.PreDragCondition startLongPressAction() {
        Launcher launcher = Launcher.getLauncher(getContext());

        PopupContainerWithArrow<Launcher> container =
                WidgetStackPopupHelper.showForWidgetStackWithDrag(this, launcher);
        if (container != null) {
            DragOptions.PreDragCondition inner = container.createPreDragCondition(false);
            final WidgetStackView self = this;
            return new DragOptions.PreDragCondition() {
                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return inner.shouldStartDrag(distanceDragged);
                }
                @Override
                public void onPreDragStart(DragObject dragObject) {
                    inner.onPreDragStart(dragObject);
                    // Undo the INVISIBLE set by Workspace.startDrag().
                    // The popup covers the stack during pre-drag.
                    self.setVisibility(VISIBLE);
                }
                @Override
                public void onPreDragEnd(DragObject dragObject, boolean dragStarted) {
                    inner.onPreDragEnd(dragObject, dragStarted);
                    if (dragStarted) {
                        // Actual drag in progress — hide stack (DragView shows shadow)
                        self.setVisibility(INVISIBLE);
                    }
                    // If !dragStarted: stack already VISIBLE from onPreDragStart
                }
            };
        }
        return null;
    }

    // --- Drop highlight (delegates to shared WidgetDropHighlight) ---

    /**
     * Shows a visual highlight when a widget is being dragged over this stack.
     * Delegates to {@link WidgetDropHighlight} for spring-based animation.
     */
    public void showDropHighlight() {
        ensureDropHighlight();
        mDropHighlight.show();
    }

    /**
     * Clears the drop highlight visual with a smooth spring back to rest.
     */
    public void clearDropHighlight() {
        if (mDropHighlight != null) {
            mDropHighlight.clear();
        }
    }

    private void ensureDropHighlight() {
        if (mDropHighlight == null) {
            mDropHighlight = new WidgetDropHighlight(this);
        }
    }

    private void updateChildVisibility() {
        for (int i = 0; i < mWidgetViews.size(); i++) {
            mWidgetViews.get(i).setVisibility(i == mActiveIndex ? VISIBLE : INVISIBLE);
        }
    }

    /**
     * Attaches all child AppWidgetHostViews to the given holder.
     * Required after async workspace inflation so child widgets receive remote view updates.
     */
    public void attachChildWidgetsToHost(LauncherWidgetHolder holder) {
        for (int i = 0; i < mWidgetViews.size(); i++) {
            View child = mWidgetViews.get(i);
            if (child instanceof LauncherAppWidgetHostView lv) {
                AppWidgetHostView attached = holder.attachViewToHostAndGetAttachedView(lv);
                if (attached != lv) {
                    // Host returned a recycled view; swap it into the stack
                    Object tag = lv.getTag();
                    removeView(lv);
                    attached.setTag(tag);
                    attached.setOnLongClickListener(null);
                    attached.setLongClickable(false);
                    mWidgetViews.set(i, attached);
                    addView(attached, i, new LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                }
            }
        }
        updateChildVisibility();
    }

    // --- Resize support ---

    /**
     * Iterates over all child widgets, looks up their {@link LauncherAppWidgetProviderInfo},
     * and accumulates a value using the given function.
     *
     * @param context   context for looking up widget provider info
     * @param identity  the starting value (e.g. 1 for min-span, Integer.MAX_VALUE for max-span)
     * @param accumulator function that combines the current result with info from each provider
     * @return the accumulated result, or {@code identity} if no valid provider info was found
     */
    private int accumulateFromProviderInfo(Context context, int identity,
            ProviderInfoAccumulator accumulator) {
        WidgetManagerHelper wmh = new WidgetManagerHelper(context);
        int result = identity;
        for (View child : mWidgetViews) {
            if (child.getTag() instanceof LauncherAppWidgetInfo wInfo) {
                LauncherAppWidgetProviderInfo pInfo = wmh.getLauncherAppWidgetInfo(
                        wInfo.appWidgetId, wInfo.providerName);
                if (pInfo != null) {
                    result = accumulator.apply(result, pInfo);
                }
            }
        }
        return result;
    }

    /**
     * Returns the minimum horizontal span for this stack, which is the most restrictive
     * (largest) minSpanX across all child widgets.
     */
    public int getMinSpanX(Context context) {
        return accumulateFromProviderInfo(context, 1,
                (cur, pInfo) -> Math.max(cur, pInfo.minSpanX));
    }

    /**
     * Returns the minimum vertical span for this stack.
     */
    public int getMinSpanY(Context context) {
        return accumulateFromProviderInfo(context, 1,
                (cur, pInfo) -> Math.max(cur, pInfo.minSpanY));
    }

    /**
     * Returns the maximum horizontal span for this stack, which is the most restrictive
     * (smallest) maxSpanX across all child widgets.
     */
    public int getMaxSpanX(Context context) {
        int result = accumulateFromProviderInfo(context, Integer.MAX_VALUE,
                (cur, pInfo) -> Math.min(cur, pInfo.maxSpanX));
        return result == Integer.MAX_VALUE ? 1 : result;
    }

    /**
     * Returns the maximum vertical span for this stack.
     */
    public int getMaxSpanY(Context context) {
        int result = accumulateFromProviderInfo(context, Integer.MAX_VALUE,
                (cur, pInfo) -> Math.min(cur, pInfo.maxSpanY));
        return result == Integer.MAX_VALUE ? 1 : result;
    }

    /**
     * Returns the combined resize mode for this stack (AND of all children's modes).
     */
    public int getResizeMode(Context context) {
        return accumulateFromProviderInfo(context,
                AppWidgetProviderInfo.RESIZE_HORIZONTAL | AppWidgetProviderInfo.RESIZE_VERTICAL,
                (cur, pInfo) -> cur & pInfo.resizeMode);
    }

    /**
     * Updates all child widgets' size ranges and span info after a resize.
     *
     * @param writer if non-null, persists each child widget's updated span to the database
     */
    public void updateChildWidgetSizes(Context context, int spanX, int spanY,
            @Nullable ModelWriter writer) {
        for (View child : mWidgetViews) {
            if (child instanceof AppWidgetHostView ahv) {
                WidgetSizes.updateWidgetSizeRanges(ahv, context, spanX, spanY);
            }
            if (child.getTag() instanceof LauncherAppWidgetInfo wInfo) {
                wInfo.spanX = spanX;
                wInfo.spanY = spanY;
                if (writer != null) {
                    writer.updateItemInDatabase(wInfo);
                }
            }
        }
        WidgetStackInfo stackInfo = getStackInfo();
        if (stackInfo != null) {
            stackInfo.spanX = spanX;
            stackInfo.spanY = spanY;
        }
    }

    /**
     * Updates all child widgets' size ranges and span info after a resize.
     * Does not persist child changes to the database.
     */
    public void updateChildWidgetSizes(Context context, int spanX, int spanY) {
        updateChildWidgetSizes(context, spanX, spanY, null);
    }

    /**
     * Expands the stack's span to accommodate a new widget if it's larger than the current span.
     * Clamps to the grid dimensions. Updates LayoutParams, child widget sizes, and persists to DB.
     */
    public void expandSpanIfNeeded(LauncherAppWidgetInfo newWidget, ModelWriter writer,
            Context context) {
        WidgetStackInfo stackInfo = getStackInfo();
        if (stackInfo == null) return;
        com.android.launcher3.InvariantDeviceProfile idp =
                com.android.launcher3.InvariantDeviceProfile.INSTANCE.get(context);
        int newSpanX = Math.min(Math.max(stackInfo.spanX, newWidget.spanX), idp.numColumns);
        int newSpanY = Math.min(Math.max(stackInfo.spanY, newWidget.spanY), idp.numRows);
        if (newSpanX != stackInfo.spanX || newSpanY != stackInfo.spanY) {
            stackInfo.spanX = newSpanX;
            stackInfo.spanY = newSpanY;
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
            lp.cellHSpan = newSpanX;
            lp.cellVSpan = newSpanY;
            updateChildWidgetSizes(context, newSpanX, newSpanY);
            writer.modifyItemInDatabase(stackInfo,
                    stackInfo.container, stackInfo.screenId,
                    stackInfo.cellX, stackInfo.cellY, newSpanX, newSpanY);
        }
    }

    // --- Accessibility ---

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        int count = mWidgetViews.size();
        if (count > 1) {
            info.setContentDescription(getContext().getString(
                    R.string.widget_stack_page_description,
                    mActiveIndex + 1, count));
            if (mActiveIndex < count - 1) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            }
            if (mActiveIndex > 0) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                && mActiveIndex < mWidgetViews.size() - 1) {
            snapToIndex(mActiveIndex + 1);
            return true;
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                && mActiveIndex > 0) {
            snapToIndex(mActiveIndex - 1);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    // --- Touch handling for horizontal swipe + long-press ---

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Always track long-press for drag support (even single-widget stacks)
        mLongPressHelper.onTouchEvent(ev);

        boolean hasMultipleWidgets = mWidgetViews.size() > 1;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Cancel any in-flight snap animation and reset transforms so a new
                // swipe starts from a clean visual state.
                cancelSnapAnimator();
                resetChildTransforms();
                updateChildVisibility();

                mDownX = ev.getX();
                mDownY = ev.getY();
                mIntercepting = false;
                mSwiping = false;
                mSwipeOffset = 0f;
                if (hasMultipleWidgets) {
                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mVelocityTracker.clear();
                    }
                    mVelocityTracker.addMovement(ev);
                    // Preemptively claim touch so Workspace/PagedView doesn't intercept
                    // our horizontal swipes
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (hasMultipleWidgets) {
                    float dx = ev.getX() - mDownX;
                    float dy = ev.getY() - mDownY;
                    if (Math.abs(dy) > mTouchSlop && Math.abs(dy) > Math.abs(dx)) {
                        // Vertical gesture — cancel long-press and let the child widget
                        // handle it. Don't touch requestDisallowInterceptTouchEvent: the
                        // child's LauncherAppWidgetHostView already blocks DragLayer for
                        // scrollable widgets (set on ACTION_DOWN), matching standalone
                        // widget behavior.
                        mLongPressHelper.cancelLongPress();
                    } else if (!mIntercepting && Math.abs(dx) > mTouchSlop
                            && Math.abs(dx) > Math.abs(dy)) {
                        mIntercepting = true;
                        mSwiping = true;
                        showDots();
                        // Swipe cancels long-press
                        mLongPressHelper.cancelLongPress();
                        // Cancel the current touch on children
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(cancel);
                        cancel.recycle();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIntercepting = false;
                if (hasMultipleWidgets) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }

        if (mLongPressHelper.hasPerformedLongPress()) {
            // Long-press triggered workspace drag (pre-drag mode). Allow the DragLayer
            // to intercept so the DragController receives ACTION_UP and properly ends
            // the pre-drag. Without this, the stale DragController state eats the first
            // tap on the popup menu.
            if (hasMultipleWidgets) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return mIntercepting;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mSwiping || mWidgetViews.size() <= 1) return super.onTouchEvent(ev);

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                mSwipeOffset = ev.getX() - mDownX;
                applySwipeTranslation();
                break;

            case MotionEvent.ACTION_UP:
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float velocityX = mVelocityTracker.getXVelocity();
                    float flingThreshold = FLING_THRESHOLD_VELOCITY * mDensity;

                    int width = getWidth();
                    if (width == 0) width = 1;
                    float fraction = mSwipeOffset / width;

                    if (velocityX < -flingThreshold || (fraction < -SNAP_THRESHOLD && velocityX <= 0)) {
                        snapToIndex(mActiveIndex + 1);
                    } else if (velocityX > flingThreshold || (fraction > SNAP_THRESHOLD && velocityX >= 0)) {
                        snapToIndex(mActiveIndex - 1);
                    } else {
                        snapBack();
                    }
                }
                mSwiping = false;
                recycleVelocityTracker();
                getParent().requestDisallowInterceptTouchEvent(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                snapBack();
                mSwiping = false;
                recycleVelocityTracker();
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return true;
    }

    /**
     * Applies zoom + translation during a live swipe gesture.
     * The current widget scales down from 1.0 to 0.92 as it slides out,
     * while the next widget scales up from 0.92 to 1.0 as it slides in.
     */
    private void applySwipeTranslation() {
        float progress = Math.abs(mSwipeOffset) / Math.max(1, getWidth());
        float clampedProgress = Math.min(1f, progress);

        View current = mWidgetViews.get(mActiveIndex);
        float currentScale = 1f - SWIPE_SCALE_RANGE * clampedProgress;
        current.setScaleX(currentScale);
        current.setScaleY(currentScale);
        current.setTranslationX(mSwipeOffset);
        current.setVisibility(VISIBLE);

        // Show next/prev widget sliding in with scale
        int peekIndex = mSwipeOffset < 0 ? mActiveIndex + 1 : mActiveIndex - 1;
        if (peekIndex >= 0 && peekIndex < mWidgetViews.size()) {
            View next = mWidgetViews.get(peekIndex);
            float direction = mSwipeOffset < 0 ? 1f : -1f;
            float nextScale = SWIPE_SCALE_MIN + SWIPE_SCALE_RANGE * clampedProgress;
            next.setScaleX(nextScale);
            next.setScaleY(nextScale);
            next.setTranslationX(mSwipeOffset + direction * getWidth());
            next.setVisibility(VISIBLE);
        }

        // Hide all other views
        for (int i = 0; i < mWidgetViews.size(); i++) {
            if (i != mActiveIndex && i != peekIndex) {
                mWidgetViews.get(i).setVisibility(INVISIBLE);
            }
        }
    }

    /**
     * Animates to the target widget index with zoom out/slide/zoom in card stack effect.
     */
    private void snapToIndex(int targetIndex) {
        targetIndex = Math.max(0, Math.min(targetIndex, mWidgetViews.size() - 1));
        if (targetIndex == mActiveIndex) {
            snapBack();
            return;
        }

        final int newIndex = targetIndex;
        View cur = mWidgetViews.get(mActiveIndex);
        View next = mWidgetViews.get(newIndex);

        final float startOffset = mSwipeOffset;
        final float direction = newIndex > mActiveIndex ? -1f : 1f;
        final float targetOffset = direction * getWidth();

        // Capture starting scales
        final float curStartScale = cur.getScaleX();
        final float nextStartScale = next.getScaleX();

        // Ensure target view is visible for the animation
        next.setTranslationX(startOffset + (-direction) * getWidth());
        next.setVisibility(VISIBLE);

        animateSnap(Interpolators.EMPHASIZED, (p) -> {
            float currentOffset = startOffset + (targetOffset - startOffset) * p;

            // Current widget: scale down to SWIPE_SCALE_MIN, slide out
            float curScale = curStartScale + (SWIPE_SCALE_MIN - curStartScale) * p;
            cur.setScaleX(curScale);
            cur.setScaleY(curScale);
            cur.setTranslationX(currentOffset);

            // Next widget: scale up to 1.0, slide in
            float nScale = nextStartScale + (1f - nextStartScale) * p;
            next.setScaleX(nScale);
            next.setScaleY(nScale);
            next.setTranslationX(currentOffset - targetOffset);
        }, () -> {
            resetChildTransforms();
            mActiveIndex = newIndex;
            WidgetStackInfo info = getStackInfo();
            if (info != null) {
                info.setActiveIndex(mActiveIndex);
            }
            updateChildVisibility();
            invalidate();
            mSwipeOffset = 0f;
            scheduleDotHide();
        });
    }

    /**
     * Snaps back to the current widget when the swipe doesn't reach threshold.
     */
    private void snapBack() {
        final float startOffset = mSwipeOffset;

        // Capture starting scales for all visible views
        View cur = mWidgetViews.get(mActiveIndex);
        final float curStartScale = cur.getScaleX();

        int peekIndex = startOffset < 0 ? mActiveIndex + 1 : mActiveIndex - 1;
        final View peekView;
        final float peekStartScale;
        if (peekIndex >= 0 && peekIndex < mWidgetViews.size()) {
            peekView = mWidgetViews.get(peekIndex);
            peekStartScale = peekView.getScaleX();
        } else {
            peekView = null;
            peekStartScale = 1f;
        }

        animateSnap(Interpolators.EMPHASIZED_DECELERATE, (p) -> {
            mSwipeOffset = startOffset * (1f - p);

            // Current widget: scale back to 1.0
            float curScale = curStartScale + (1f - curStartScale) * p;
            cur.setScaleX(curScale);
            cur.setScaleY(curScale);
            cur.setTranslationX(mSwipeOffset);

            // Peek widget: scale back to SWIPE_SCALE_MIN and slide off
            if (peekView != null) {
                float pScale = peekStartScale + (SWIPE_SCALE_MIN - peekStartScale) * p;
                peekView.setScaleX(pScale);
                peekView.setScaleY(pScale);
                float dir = startOffset < 0 ? 1f : -1f;
                peekView.setTranslationX(mSwipeOffset + dir * getWidth());
            }
        }, () -> {
            mSwipeOffset = 0f;
            resetChildTransforms();
            updateChildVisibility();
            scheduleDotHide();
        });
    }

    /** Functional interface for per-frame animation updates. */
    @FunctionalInterface
    private interface SnapUpdateCallback {
        void onUpdate(float progress);
    }

    /**
     * Shared animation helper used by both {@link #snapToIndex} and {@link #snapBack}.
     * Creates a ValueAnimator with M3 LONG_2 duration, the given interpolator,
     * per-frame update callback, and end-action.
     */
    private void animateSnap(android.view.animation.Interpolator interpolator,
            SnapUpdateCallback updateCallback, Runnable endAction) {
        cancelSnapAnimator();
        mSnapAnimator = ValueAnimator.ofFloat(0f, 1f);
        mSnapAnimator.setDuration(M3Durations.LONG_2);
        mSnapAnimator.setInterpolator(interpolator);
        mSnapAnimator.addUpdateListener(anim -> {
            float p = (float) anim.getAnimatedValue();
            updateCallback.onUpdate(p);
        });
        mSnapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endAction.run();
            }
        });
        mSnapAnimator.start();
    }

    /** Resets translation and scale on all child views. */
    private void resetChildTransforms() {
        for (View v : mWidgetViews) {
            v.setTranslationX(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
        }
    }

    private void cancelSnapAnimator() {
        if (mSnapAnimator != null) {
            if (mSnapAnimator.isRunning()) {
                mSnapAnimator.cancel();
            }
            mSnapAnimator = null;
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    // --- Dot auto-show/hide ---

    private void showDots() {
        cancelDotHide();
        if (mDotFadeAnimator != null) mDotFadeAnimator.cancel();
        mDotFadeAnimator = ValueAnimator.ofFloat(mDotAlpha, 1f);
        mDotFadeAnimator.setDuration(M3Durations.SHORT_3);
        mDotFadeAnimator.setInterpolator(Interpolators.STANDARD);
        mDotFadeAnimator.addUpdateListener(a -> {
            mDotAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        mDotFadeAnimator.start();
    }

    private void scheduleDotHide() {
        cancelDotHide();
        mDotHideRunnable = () -> {
            if (mDotFadeAnimator != null) mDotFadeAnimator.cancel();
            mDotFadeAnimator = ValueAnimator.ofFloat(mDotAlpha, 0f);
            mDotFadeAnimator.setDuration(M3Durations.SHORT_4);
            mDotFadeAnimator.setInterpolator(Interpolators.STANDARD);
            mDotFadeAnimator.addUpdateListener(a -> {
                mDotAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            mDotFadeAnimator.start();
        };
        postDelayed(mDotHideRunnable, DOT_AUTO_HIDE_DELAY_MS);
    }

    private void cancelDotHide() {
        if (mDotHideRunnable != null) {
            removeCallbacks(mDotHideRunnable);
            mDotHideRunnable = null;
        }
    }

    // --- Page indicator dots ---

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Draw page indicator dots (only visible during/after swipe)
        int count = mWidgetViews.size();
        if (count <= 1 || mDotAlpha <= 0f) return;

        float r = DOT_RADIUS_DP * mDensity;
        float spacing = DOT_SPACING_DP * mDensity;
        float bottomMargin = DOT_BOTTOM_MARGIN_DP * mDensity;
        float totalWidth = count * r * 2 + (count - 1) * spacing;

        float startX = (getWidth() - totalWidth) / 2f;
        float centerY = getHeight() - bottomMargin - r;

        float x = startX;
        for (int i = 0; i < count; i++) {
            if (i == mActiveIndex) {
                mDotPaintActive.setAlpha((int) (255 * mDotAlpha));
                canvas.drawCircle(x + r, centerY, r, mDotPaintActive);
            } else {
                mDotPaintInactive.setAlpha((int) (INACTIVE_DOT_ALPHA * mDotAlpha));
                canvas.drawCircle(x + r, centerY, r, mDotPaintInactive);
            }
            x += r * 2 + spacing;
        }
    }
}
