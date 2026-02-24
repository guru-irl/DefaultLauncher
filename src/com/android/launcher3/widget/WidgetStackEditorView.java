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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.anim.M3Durations;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WidgetStackInfo;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.AbstractSlideInView;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Full-screen editor for widget stacks. Allows reordering widgets via drag-and-drop,
 * removing individual widgets, and adding new widgets from the picker.
 *
 * <p>Extends {@link AbstractSlideInView} to match the standard bottom sheet pattern
 * used by {@link WidgetsBottomSheet} and other launcher sheets: scrim, swipe-to-dismiss,
 * and predictive back support are all handled by the base class.
 */
public class WidgetStackEditorView extends AbstractSlideInView<Launcher>
        implements Insettable {

    private static final String TAG = "WidgetStackEditorView";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final int DEFAULT_CLOSE_DURATION = 200;

    private WidgetStackView mStackView;
    private WidgetStackInfo mStackInfo;
    private RecyclerView mRecyclerView;
    private WidgetStackEditorAdapter mAdapter;
    private ItemTouchHelper mItemTouchHelper;
    private final ArrayList<LauncherAppWidgetInfo> mRemovedWidgets = new ArrayList<>();
    private boolean mDirty;

    private AnimatorSet mDragAnimator;
    private float mLargeRadius;
    private float mSmallRadius;

    protected final Rect mInsets = new Rect();
    private int mNavBarScrimHeight;
    private final Paint mNavBarScrimPaint;

    public WidgetStackEditorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetStackEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(Themes.getNavBarScrimColor(mActivityContext));
    }

    @Override
    protected int getScrimColor(Context context) {
        return context.getResources().getColor(R.color.widgets_picker_scrim);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.widget_stack_editor_content);
        // Resolve colors from WidgetContainerTheme so both sheets match exactly.
        setContentBackgroundWithParent(
                createSheetBackground(getWidgetThemeContext()), mContent);
    }

    /** Returns a context themed with {@code widgetsTheme} for consistent color resolution. */
    private Context getWidgetThemeContext() {
        android.content.res.TypedArray ta = mActivityContext.obtainStyledAttributes(
                new int[]{R.attr.widgetsTheme});
        int themeRes = ta.getResourceId(0, 0);
        ta.recycle();
        return themeRes != 0
                ? new ContextThemeWrapper(getContext(), themeRes)
                : getContext();
    }

    /**
     * Creates the sheet background drawable using {@code widgetPickerPrimarySurfaceColor}
     * from the {@code WidgetContainerTheme}. Both the Widgets bottom sheet and the
     * Widget Stack Editor call this with a WidgetContainerTheme-wrapped context so
     * colors resolve identically.
     */
    static GradientDrawable createSheetBackground(Context context) {
        int bgColor = Themes.getAttrColor(context, R.attr.widgetPickerPrimarySurfaceColor);
        float radius = context.getResources().getDimension(R.dimen.m3_shape_extra_large);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(bgColor);
        bg.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return bg;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildWithMargins(mContent, widthMeasureSpec, 0, heightMeasureSpec, 0);
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        int contentWidth = mContent.getMeasuredWidth();
        int contentLeft = (width - contentWidth) / 2;
        mContent.layout(contentLeft, height - mContent.getMeasuredHeight(),
                contentLeft + contentWidth, height);
        setTranslationShift(mTranslationShift);
    }

    // ---- Insets & nav bar scrim (matches BaseWidgetSheet) ----

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        int bottomPadding = Math.max(insets.bottom, mNavBarScrimHeight);

        // Push the list content above the nav bar
        if (mRecyclerView != null) {
            mRecyclerView.setPadding(
                    mRecyclerView.getPaddingLeft(),
                    mRecyclerView.getPaddingTop(),
                    mRecyclerView.getPaddingRight(),
                    bottomPadding + getResources().getDimensionPixelSize(
                            R.dimen.bottom_sheet_handle_margin));
        }

        if (bottomPadding > 0) {
            setupNavBarColor();
        } else {
            clearNavBarColor();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets windowInsets = WindowManagerProxy.INSTANCE.get(getContext())
                .normalizeWindowInsets(getContext(), getRootWindowInsets(), new Rect());
        mNavBarScrimHeight = windowInsets.getTappableElementInsets().bottom;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mNavBarScrimHeight = insets.getTappableElementInsets().bottom;
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mNavBarScrimHeight > 0) {
            float left = (getWidth() - getWidth() / getScaleX()) / 2;
            float top = getHeight() / 2f
                    + (getHeight() / 2f - mNavBarScrimHeight) / getScaleY();
            canvas.drawRect(left, top, getWidth() / getScaleX(),
                    top + mNavBarScrimHeight / getScaleY(), mNavBarScrimPaint);
        }
    }

    private void setupNavBarColor() {
        // Use mActivityContext (Launcher) for isMainColorDark — getContext() is a themed
        // wrapper that doesn't define this launcher-specific attr.
        boolean isNavBarDark = Themes.getAttrBoolean(mActivityContext, R.attr.isMainColorDark);

        // In light mode, landscape phone reverses navbar background color.
        boolean isPhoneLandscape =
                !mActivityContext.getDeviceProfile().isTablet && mInsets.bottom == 0;
        if (!isNavBarDark && isPhoneLandscape) {
            isNavBarDark = true;
        }

        mActivityContext.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isNavBarDark ? SystemUiController.FLAG_DARK_NAV
                        : SystemUiController.FLAG_LIGHT_NAV);
    }

    private void clearNavBarColor() {
        mActivityContext.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    @Override
    protected void onCloseComplete() {
        super.onCloseComplete();
        clearNavBarColor();
    }

    // ---- Show / setup ----

    /**
     * Shows the widget stack editor for the given stack.
     */
    public static void show(Launcher launcher, WidgetStackView stackView) {
        AbstractFloatingView.closeAllOpenViews(launcher);

        // Wrap context with M3 theme + dynamic colors so Material components inflate correctly.
        // The Launcher's own theme is not MaterialComponents-based.
        Context themed = DynamicColors.wrapContextIfAvailable(
                new ContextThemeWrapper(launcher, R.style.HomeSettings_Theme));
        WidgetStackEditorView editor = (WidgetStackEditorView)
                LayoutInflater.from(themed).inflate(
                        R.layout.widget_stack_editor, launcher.getDragLayer(), false);
        editor.setup(stackView);
        editor.attachToContainer();
        editor.animateOpen();
    }

    /**
     * Removes an entire widget stack from the workspace and DB.
     * Called from the "Remove stack" popup action.
     */
    public static void removeEntireStack(Launcher launcher, WidgetStackView stackView) {
        WidgetStackInfo stackInfo = stackView.getStackInfo();
        if (stackInfo == null) return;

        ModelWriter writer = launcher.getModelWriter();

        // Delete all child widgets
        for (ItemInfo item : stackInfo.getContents()) {
            if (item instanceof LauncherAppWidgetInfo wInfo) {
                writer.deleteWidgetInfo(wInfo, launcher.getAppWidgetHolder(),
                        "stack editor remove entire stack");
            }
        }

        // Delete the stack container itself
        writer.deleteItemFromDatabase(stackInfo, "stack editor remove entire stack");

        // Remove the view from workspace
        CellLayout cellLayout = getParentCellLayout(stackView);
        if (cellLayout != null) {
            cellLayout.removeView(stackView);
        }
    }

    private void setup(WidgetStackView stackView) {
        mStackView = stackView;
        mStackInfo = stackView.getStackInfo();

        Resources res = getResources();
        mLargeRadius = res.getDimension(R.dimen.m3_shape_extra_large);
        mSmallRadius = res.getDimension(R.dimen.m3_shape_extra_small);

        mRecyclerView = findViewById(R.id.widget_stack_editor_list);

        // Set up RecyclerView
        mAdapter = new WidgetStackEditorAdapter(mActivityContext, mStackInfo);
        mAdapter.setOnRemoveListener(this::onRemoveWidget);
        mAdapter.setOnAddListener(this::onAddWidget);
        mAdapter.setShowAddRow(
                mStackInfo.getContents().size() < WidgetStackInfo.MAX_STACK_SIZE);

        LinearLayoutManager layoutManager = new LinearLayoutManager(mActivityContext);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        // Card decoration: use WidgetContainerTheme colors to match WidgetsBottomSheet
        mRecyclerView.addItemDecoration(
                new WidgetStackEditorItemDecoration(getWidgetThemeContext()));
        mRecyclerView.setClipChildren(false);

        // ItemTouchHelper for drag reorder (add row excluded)
        mItemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder) {
                // Disable drag for the add row
                if (mAdapter.isAddRow(viewHolder.getBindingAdapterPosition())) {
                    return makeMovementFlags(0, 0);
                }
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public boolean canDropOver(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder current,
                    @NonNull RecyclerView.ViewHolder target) {
                // Prevent dropping onto the add row
                return !mAdapter.isAddRow(target.getBindingAdapterPosition());
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                if (mAdapter.isAddRow(fromPos) || mAdapter.isAddRow(toPos)) return false;
                Collections.swap(mStackInfo.getContents(), fromPos, toPos);
                mAdapter.notifyItemMoved(fromPos, toPos);
                mDirty = true;
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe-to-remove
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder,
                    int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG
                        && viewHolder != null) {
                    animateDragPickup(viewHolder);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                animateDragDrop(recyclerView, viewHolder);
            }
        });
        mItemTouchHelper.attachToRecyclerView(mRecyclerView);
        mAdapter.setItemTouchHelper(mItemTouchHelper);
    }

    // ---- Touch handling ----

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        // Disable swipe-to-dismiss when the RecyclerView is scrolled, matching the
        // WidgetsBottomSheet pattern for scroll vs. swipe conflict resolution.
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            if (getPopupContainer().isEventOverView(mRecyclerView, ev)
                    && mRecyclerView.canScrollVertically(-1)) {
                mNoIntercept = true;
            }
        }
        return super.onControllerInterceptTouchEvent(ev);
    }

    // ---- Remove / add widget ----

    private void onRemoveWidget(int position) {
        if (position < 0 || position >= mStackInfo.getContents().size()) return;

        ItemInfo item = mStackInfo.getContents().remove(position);
        if (item instanceof LauncherAppWidgetInfo wInfo) {
            mRemovedWidgets.add(wInfo);
        }
        mAdapter.notifyItemRemoved(position);
        mDirty = true;

        int remaining = mStackInfo.getContents().size();
        if (remaining <= 1) {
            // Stack dissolution — commit and close
            close(true);
        }
    }

    private void onAddWidget() {
        if (mStackInfo.getContents().size() >= WidgetStackInfo.MAX_STACK_SIZE) {
            if (DEBUG) Log.d(TAG, "Stack is full, cannot add more widgets");
            return;
        }

        // Set the pending target so the widget picker places into this stack
        mActivityContext.getWorkspace().setPendingExternalStackTarget(mStackView);

        // Close the editor first, then open the picker
        close(true);
        WidgetsFullSheet.show(mActivityContext, true);
    }

    // ---- Drag animations ----

    /**
     * Computes position-aware corner radii for the given adapter position.
     * Matches the logic in {@link WidgetStackEditorItemDecoration}.
     */
    private float[] computePositionRadii(int adapterPosition, int itemCount) {
        float topLeft, topRight, bottomLeft, bottomRight;
        boolean isFirst = adapterPosition == 0;
        boolean isLast = adapterPosition == itemCount - 1;

        if (isFirst && isLast) {
            topLeft = topRight = bottomLeft = bottomRight = mLargeRadius;
        } else if (isFirst) {
            topLeft = topRight = mLargeRadius;
            bottomLeft = bottomRight = mSmallRadius;
        } else if (isLast) {
            topLeft = topRight = mSmallRadius;
            bottomLeft = bottomRight = mLargeRadius;
        } else {
            topLeft = topRight = bottomLeft = bottomRight = mSmallRadius;
        }

        return new float[]{
                topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft
        };
    }

    private void animateDragPickup(RecyclerView.ViewHolder viewHolder) {
        if (mDragAnimator != null) {
            mDragAnimator.cancel();
        }

        View itemView = viewHolder.itemView;
        itemView.setTag(R.id.editor_drag_state_tag, Boolean.TRUE);

        int pos = viewHolder.getBindingAdapterPosition();
        int count = mAdapter.getItemCount();
        float[] startRadii = computePositionRadii(pos, count);
        float[] endRadii = new float[8];
        Arrays.fill(endRadii, mLargeRadius);

        float dragElevation = itemView.getResources().getDimension(
                R.dimen.widget_stack_editor_drag_elevation);
        int dragColor = Themes.getAttrColor(itemView.getContext(),
                com.google.android.material.R.attr.colorSurfaceContainerHighest);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(startRadii.clone());
        bg.setColor(dragColor);
        itemView.setBackground(bg);

        ValueAnimator radiiAnimator = ValueAnimator.ofFloat(0f, 1f);
        radiiAnimator.addUpdateListener(anim -> {
            float fraction = anim.getAnimatedFraction();
            float[] current = new float[8];
            for (int i = 0; i < 8; i++) {
                current[i] = startRadii[i] + (endRadii[i] - startRadii[i]) * fraction;
            }
            bg.setCornerRadii(current);
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                radiiAnimator,
                ObjectAnimator.ofFloat(itemView, TRANSLATION_Z, 0f, dragElevation),
                ObjectAnimator.ofFloat(itemView, SCALE_X, 1f, 1.02f),
                ObjectAnimator.ofFloat(itemView, SCALE_Y, 1f, 1.02f)
        );
        set.setDuration(M3Durations.SHORT_2);
        set.setInterpolator(Interpolators.EMPHASIZED_DECELERATE);
        mDragAnimator = set;
        set.start();
    }

    private void animateDragDrop(RecyclerView recyclerView,
            RecyclerView.ViewHolder viewHolder) {
        if (mDragAnimator != null) {
            mDragAnimator.cancel();
        }

        View itemView = viewHolder.itemView;
        int pos = viewHolder.getBindingAdapterPosition();
        int count = mAdapter.getItemCount();
        float[] targetRadii = computePositionRadii(
                pos != RecyclerView.NO_POSITION ? pos : 0, count);
        float[] startRadii = new float[8];
        Arrays.fill(startRadii, mLargeRadius);

        float dragElevation = itemView.getResources().getDimension(
                R.dimen.widget_stack_editor_drag_elevation);

        // Ensure we have a GradientDrawable to animate
        GradientDrawable bg;
        if (itemView.getBackground() instanceof GradientDrawable) {
            bg = (GradientDrawable) itemView.getBackground();
        } else {
            bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadii(startRadii.clone());
            bg.setColor(Themes.getAttrColor(itemView.getContext(),
                    com.google.android.material.R.attr.colorSurfaceContainerHighest));
            itemView.setBackground(bg);
        }

        ValueAnimator radiiAnimator = ValueAnimator.ofFloat(0f, 1f);
        radiiAnimator.addUpdateListener(anim -> {
            float fraction = anim.getAnimatedFraction();
            float[] current = new float[8];
            for (int i = 0; i < 8; i++) {
                current[i] = startRadii[i] + (targetRadii[i] - startRadii[i]) * fraction;
            }
            bg.setCornerRadii(current);
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                radiiAnimator,
                ObjectAnimator.ofFloat(itemView, TRANSLATION_Z, dragElevation, 0f),
                ObjectAnimator.ofFloat(itemView, SCALE_X, 1.02f, 1f),
                ObjectAnimator.ofFloat(itemView, SCALE_Y, 1.02f, 1f)
        );
        set.setDuration(M3Durations.SHORT_2);
        set.setInterpolator(Interpolators.EMPHASIZED_ACCELERATE);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                itemView.setTag(R.id.editor_drag_state_tag, null);
                itemView.setTranslationZ(0f);
                itemView.setBackground(null);
                recyclerView.invalidateItemDecorations();
            }
        });
        mDragAnimator = set;
        set.start();
    }

    // ---- Open / close ----

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            return;
        }
        mIsOpen = true;
        setupNavBarColor();
        setUpDefaultOpenAnimation().start();
    }

    @Override
    protected void handleClose(boolean animate) {
        commitChanges();
        handleClose(animate, DEFAULT_CLOSE_DURATION);
    }

    @Override
    public void addHintCloseAnim(
            float distanceToMove, Interpolator interpolator, PendingAnimation target) {
        target.addAnimatedFloat(mSwipeToDismissProgress, 0f, 1f, interpolator);
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(mContent, getContext().getString(R.string.widget_stack_editor_title));
    }

    // ---- Data persistence ----

    private void commitChanges() {
        if (mStackInfo == null || !mDirty) return;
        mDirty = false;

        ModelWriter writer = mActivityContext.getModelWriter();

        // Delete removed widgets from DB
        for (LauncherAppWidgetInfo removed : mRemovedWidgets) {
            writer.deleteWidgetInfo(removed, mActivityContext.getAppWidgetHolder(),
                    "stack editor remove");
        }

        int remaining = mStackInfo.getContents().size();

        if (remaining == 0) {
            // Stack is empty — remove entirely
            writer.deleteItemFromDatabase(mStackInfo, "stack editor empty");
            CellLayout cellLayout = getParentCellLayout(mStackView);
            if (cellLayout != null) {
                cellLayout.removeView(mStackView);
            }
            return;
        }

        if (remaining == 1) {
            // Dissolve: extract the lone widget to standalone
            dissolveStack(writer);
            return;
        }

        // Normal case: reassign ranks sequentially and persist
        ArrayList<ItemInfo> contents = mStackInfo.getContents();
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).rank = i;
            writer.updateItemInDatabase(contents.get(i));
        }

        // Update stack active index + persist
        mStackInfo.setActiveIndex(Math.min(mStackInfo.getActiveIndex(), contents.size() - 1));
        writer.updateItemInDatabase(mStackInfo);

        // Sync the view
        mStackView.rebuildFromStackInfo();
    }

    /**
     * Dissolves a stack with exactly 1 remaining widget into a standalone widget.
     * Moves the widget to CONTAINER_DESKTOP at the stack's grid position.
     */
    private void dissolveStack(ModelWriter writer) {
        ItemInfo lastItem = mStackInfo.getContents().get(0);
        if (!(lastItem instanceof LauncherAppWidgetInfo widgetInfo)) {
            // Shouldn't happen, but clean up anyway
            writer.deleteItemFromDatabase(mStackInfo, "stack editor dissolve non-widget");
            CellLayout cellLayout = getParentCellLayout(mStackView);
            if (cellLayout != null) {
                cellLayout.removeView(mStackView);
            }
            return;
        }

        // Move widget to standalone position
        writer.modifyItemInDatabase(widgetInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                mStackInfo.screenId,
                mStackInfo.cellX, mStackInfo.cellY,
                mStackInfo.spanX, mStackInfo.spanY);

        // Delete the stack container
        writer.deleteItemFromDatabase(mStackInfo, "stack editor dissolve");

        // Remove WidgetStackView from workspace
        CellLayout cellLayout = getParentCellLayout(mStackView);
        if (cellLayout != null) {
            cellLayout.removeView(mStackView);
        }

        // Inflate the standalone widget at the same grid position
        mActivityContext.bindAppWidget(widgetInfo);
    }

    // ---- AbstractFloatingView type ----

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_WIDGET_STACK_EDITOR) != 0;
    }

    // ---- Utility ----

    /**
     * Finds the parent CellLayout of a view by traversing the view hierarchy.
     * The expected hierarchy is: CellLayout -> ShortcutAndWidgetContainer -> child view.
     */
    private static @Nullable CellLayout getParentCellLayout(View view) {
        ViewParent parent = view.getParent();
        if (parent instanceof ShortcutAndWidgetContainer) {
            ViewParent grandParent = parent.getParent();
            if (grandParent instanceof CellLayout) {
                return (CellLayout) grandParent;
            }
        }
        return null;
    }
}
