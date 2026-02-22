/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingIconViewCompanion;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.settings.FolderSettingsHelper;
import com.android.launcher3.touch.ItemClickHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FloatingIconViewCompanion,
        DraggableView, Reorderable {

    private static final String TAG = "FolderIcon";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    // Layout constants
    private static final float COVER_ICON_SIZE_RATIO = 0.6f;
    private static final float OPEN_INDICATOR_SIZE_RATIO = 0.3f;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    private CheckLongPressHelper mLongPressHelper;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;
    // Shorter delay for expanded/covered folders (user expects quicker response)
    private static final int ON_OPEN_DELAY_EXPANDED = 300;

    @Thunk BubbleTextView mFolderName;

    PreviewBackground mBackground = new PreviewBackground(getContext());
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private Alarm mOpenAlarm = new Alarm(Looper.getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    // Expanded folder state (2x2 grid showing 3 apps + open indicator)
    private boolean mIsExpanded;
    private final Paint mExpandedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Custom cover icon (replaces mini-icon previews)
    @Nullable Drawable mCoverDrawable;

    // Cached open-folder indicator drawable (expand_content icon)
    private Drawable mOpenIndicatorDrawable;

    // Per-folder expanded shape key (null = use default rounded square)
    @Nullable private String mExpandedShapeKey;

    // Per-folder icon shape key (applies to both 1x1 and expanded modes)
    @Nullable private String mPerFolderShapeKey;

    // Cache for expanded folder icon drawables, keyed by item index.
    // Cleared on content change, cover change, or expanded state change.
    private final SparseArray<Drawable> mExpandedIconCache = new SparseArray<>();

    // Cached values to avoid per-frame pref reads and object allocation
    private int mCachedCoverBgColor;
    private int mCachedFolderBgColor;
    private int mCachedOnSurfaceVariantColor;
    private float mCachedExpandedCornerPx;
    private ShapeDelegate mCachedExpandedShape;
    private ExpandedGridParams mCachedGridParams;

    // Track if a long-press (popup) fired so ACTION_UP skips cell tap handling
    private boolean mLongPressHandled;

    private float mScaleForReorderBounce = 1f;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
            folderIcon.invalidate();
        }
    };

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
    }

    /**
     * Refreshes cached colors and shape used during draw. Called once during inflation;
     * safe because onConfigChanged recreates the activity and re-inflates all views.
     */
    private void refreshCachedState() {
        Context ctx = getContext();
        mCachedCoverBgColor = FolderSettingsHelper.getEffectiveCoverBgColor(ctx);
        mCachedFolderBgColor = FolderSettingsHelper.getEffectiveFolderBgColor(ctx);
        mCachedOnSurfaceVariantColor = ctx.getColor(R.color.materialColorOnSurfaceVariant);
        mCachedExpandedCornerPx = getResources().getDimension(R.dimen.m3_shape_large);
    }

    public static <T extends Context & ActivityContext> FolderIcon inflateFolderAndIcon(int resId,
            T activityContext, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = Folder.fromXml(activityContext);

        FolderIcon icon = inflateIcon(resId, activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);

        icon.setFolder(folder);
        return icon;
    }

    /**
     * Builds a FolderIcon to be added to the activity.
     * This method doesn't add any listeners to the FolderInfo, and hence any changes to the info
     * will not be reflected in the folder.
     */
    public static FolderIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        FolderIcon icon = (FolderIcon) inflater.inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;

        icon.setTag(folderInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = folderInfo;
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));
        icon.updateDotInfo();

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);

        // Set expanded state from persisted flags (with validation + auto-fix)
        icon.updateExpandedState();

        // Load custom cover icon if set
        icon.mCoverDrawable = FolderCoverManager.getInstance(
                icon.getContext().getApplicationContext()).loadCoverDrawable(folderInfo.id);

        // Load per-folder expanded shape key if set
        icon.mExpandedShapeKey = FolderCoverManager.getInstance(
                icon.getContext().getApplicationContext()).getExpandedShape(folderInfo.id);

        // Load per-folder icon shape key if set
        icon.mPerFolderShapeKey = FolderCoverManager.getInstance(
                icon.getContext().getApplicationContext()).getIconShape(folderInfo.id);
        icon.refreshCachedState();

        // Push fully resolved shape to PreviewBackground (single source of truth)
        icon.mBackground.setResolvedShape(icon.resolveCurrentShape());

        if (DEBUG) Log.d(TAG, "inflateIcon: id=" + folderInfo.id
                + " title=" + folderInfo.title
                + " spanX=" + folderInfo.spanX + " spanY=" + folderInfo.spanY
                + " options=" + folderInfo.options
                + " isExpanded=" + icon.mIsExpanded
                + " hasCover=" + (icon.mCoverDrawable != null)
                + " expandedShapeKey=" + icon.mExpandedShapeKey
                + " perFolderShapeKey=" + icon.mPerFolderShapeKey);

        return icon;
    }

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public BubbleTextView getFolderName() {
        return mFolderName;
    }

    /**
     * Shows the folder popup and returns a PreDragCondition so the drag system
     * can start a deferred drag (same pattern as BubbleTextView.startLongPressAction).
     */
    public DragOptions.PreDragCondition startLongPressAction() {
        Launcher launcher = Launcher.getLauncher(getContext());

        PopupContainerWithArrow<Launcher> container =
                FolderPopupHelper.showForFolderWithDrag(this, launcher);
        if (container != null) {
            DragOptions.PreDragCondition inner = container.createPreDragCondition(false);
            final FolderIcon self = this;
            return new DragOptions.PreDragCondition() {
                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return inner.shouldStartDrag(distanceDragged);
                }
                @Override
                public void onPreDragStart(DragObject dragObject) {
                    inner.onPreDragStart(dragObject);
                    // Undo the INVISIBLE set by Workspace.startDrag().
                    // The popup covers the icon during pre-drag.
                    self.setVisibility(VISIBLE);
                }
                @Override
                public void onPreDragEnd(DragObject dragObject, boolean dragStarted) {
                    inner.onPreDragEnd(dragObject, dragStarted);
                    if (dragStarted) {
                        // Actual drag in progress — hide icon (DragView shows shadow)
                        self.setVisibility(INVISIBLE);
                    }
                    // If !dragStarted: icon already VISIBLE from onPreDragStart
                }
            };
        }
        return null;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds,
                ClippedFolderIconLayoutRule.getIconOverlapFactor());
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
    }

    /** Called after expand/collapse to refresh the rendering mode. */
    public void updateExpandedState() {
        mExpandedIconCache.clear();
        mCachedGridParams = null;
        boolean flagSet = mInfo != null && mInfo.hasOption(FolderInfo.FLAG_EXPANDED);
        // Only consider expanded if span is actually > 1x1 and square
        boolean spanValid = mInfo != null && mInfo.spanX > 1 && mInfo.spanY > 1
                && mInfo.spanX == mInfo.spanY;
        mIsExpanded = flagSet && spanValid;

        if (DEBUG) Log.d(TAG, "updateExpandedState: id=" + (mInfo != null ? mInfo.id : -1)
                + " flagSet=" + flagSet + " spanValid=" + spanValid
                + " spanX=" + (mInfo != null ? mInfo.spanX : -1)
                + " spanY=" + (mInfo != null ? mInfo.spanY : -1)
                + " mIsExpanded=" + mIsExpanded);

        // Don't clear FLAG_EXPANDED from mInfo.options — the flag should survive in the
        // database for when spans are properly restored (e.g. after config change).
        // mIsExpanded=false is sufficient to suppress expanded rendering.

        // When entering expanded state, ensure all item icons are loaded at full resolution.
        // The model loader only pre-loads "preview" items (first ~4); expanded folders show all.
        if (mIsExpanded && mInfo != null) {
            requestHighResIcons();
        }

        invalidate();
    }

    /**
     * Proactively loads high-res icons for all folder items using the bulk IconCache API.
     * Called when entering expanded state, since the model loader only pre-loads preview items.
     */
    private void requestHighResIcons() {
        IconCache iconCache = LauncherAppState.getInstance(getContext()).getIconCache();
        List<IconRequestInfo<WorkspaceItemInfo>> requests = new ArrayList<>();
        for (ItemInfo item : mInfo.getContents()) {
            if (item instanceof WorkspaceItemInfo wii && wii.bitmap.isNullOrLowRes()) {
                requests.add(new IconRequestInfo<>(wii, null, false));
            }
        }
        if (!requests.isEmpty()) {
            Executors.MODEL_EXECUTOR.post(() -> {
                iconCache.getTitlesAndIconsInBulk(requests);
                post(() -> invalidate());
            });
        }
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Returns the cached expanded shape (M3 Large token rounded square).
     * Used by FolderAnimationManager for the reveal animation of expanded uncovered folders.
     * May return null if the shape hasn't been computed yet (before first draw).
     */
    @Nullable
    ShapeDelegate getCachedExpandedShape() {
        return mCachedExpandedShape;
    }

    /** Reloads the cover drawable from the cover manager. */
    public void updateCoverDrawable() {
        mExpandedIconCache.clear();
        if (mInfo != null) {
            mCoverDrawable = FolderCoverManager.getInstance(
                    getContext().getApplicationContext()).loadCoverDrawable(mInfo.id);
        } else {
            mCoverDrawable = null;
        }
        if (DEBUG) Log.d(TAG, "updateCoverDrawable: id=" + (mInfo != null ? mInfo.id : -1)
                + " hasCover=" + (mCoverDrawable != null));
    }

    /** Updates the per-folder expanded shape key and triggers redraw. */
    public void updateExpandedShape(@Nullable String shapeKey) {
        mExpandedShapeKey = shapeKey;
    }

    /** Updates the per-folder icon shape and triggers redraw. */
    public void updatePerFolderShape(@Nullable String shapeKey) {
        mPerFolderShapeKey = shapeKey;
        mBackground.setResolvedShape(resolveCurrentShape());
        invalidate();
    }

    /**
     * Resolves the current shape for this folder, checking per-folder override first,
     * then global setting, then theme default.
     */
    ShapeDelegate resolveCurrentShape() {
        // 1. Per-folder shape (highest priority)
        if (!TextUtils.isEmpty(mPerFolderShapeKey)) {
            ShapeDelegate s = FolderSettingsHelper.resolveShapeKey(mPerFolderShapeKey);
            if (s != null) return s;
        }
        // 2. Global folder shape pref
        ShapeDelegate global = FolderSettingsHelper.resolveFolderIconShape(getContext());
        if (global != null) return global;
        // 3. Theme default
        return ThemeManager.INSTANCE.get(getContext()).getFolderShape();
    }

    private boolean willAcceptItem(ItemInfo item) {
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        // Skip the preview background accept animation for expanded/covered folders —
        // it draws a 1x1 circle behind the expanded folder background, causing artifacts
        if (!mIsExpanded && mCoverDrawable == null) {
            mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        }
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof WorkspaceItemFactory)
                        || (dragInfo instanceof PendingAddShortcutInfo)
                        || Folder.willAccept(dragInfo))) {
            int delay = (mIsExpanded || mCoverDrawable != null)
                    ? ON_OPEN_DELAY_EXPANDED : ON_OPEN_DELAY;
            mOpenAlarm.setAlarm(delay);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final ItemInfo destInfo, final View destView,
            final ItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        getFolder().addFolderContent(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        mPreviewItemManager.createFirstItemAnimation(true /* reverse */, onCompleteRunnable)
                .start();
    }

    public void onDragExit() {
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform();
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform();
            }

            int numItemsInPreview = Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= MAX_NUM_ITEMS_IN_PREVIEW) {
                List<ItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                getFolder().addFolderContent(item, index, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    int newIndex = mCurrentPreviewItems.indexOf(item);
                    if (newIndex >= 0) {
                        // If the item dropped is going to be in the preview, we update the
                        // index here to reflect its position in the preview.
                        index = newIndex;
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    getFolder().removeFolderContent(false, item);
                }
            }

            if (!itemAdded) {
                getFolder().addFolderContent(item, index, true);
            }

            int[] center = new int[2];
            float scale;
            if (mIsExpanded || mCoverDrawable != null) {
                // Animate to center of the folder view (not preview-circle center)
                center[0] = getWidth() / 2;
                center[1] = getHeight() / 2;
                scale = 0.5f;
            } else {
                scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            }
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;

            // Account for potentially different icon sizes with non-default grid settings
            if (d.dragSource instanceof ActivityAllAppsContainerView) {
                DeviceProfile grid = mActivity.getDeviceProfile();
                float containerScale = (1f * grid.iconSizePx / grid.allAppsIconSizePx);
                finalScale *= containerScale;
            }

            final int finalIndex = index;
            dragLayer.animateView(animateView, to, finalAlpha,
                    finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DECELERATE_2,
                    () -> {
                        mPreviewItemManager.hidePreviewItem(finalIndex, false);
                        mFolder.showItem(item);
                    },
                    DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);

            FolderNameInfos nameInfos = new FolderNameInfos();
            Executors.MODEL_EXECUTOR.post(() -> {
                d.folderNameProvider.getSuggestedFolderName(
                        getContext(), mInfo.getAppContents(), nameInfos);
                postDelayed(() -> {
                    setLabelSuggestion(nameInfos, d.logInstanceId);
                    invalidate();
                }, DROP_IN_ANIMATION_DURATION);
            });
        } else {
            getFolder().addFolderContent(item);
        }
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
        if (!mInfo.getLabelState().equals(LabelState.UNLABELED)) {
            return;
        }
        if (nameInfos == null || !nameInfos.hasSuggestions()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS);
            return;
        }
        if (!nameInfos.hasPrimary()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY);
            return;
        }
        CharSequence newTitle = nameInfos.getLabels()[0];
        FromState fromState = mInfo.getFromLabelState();

        mInfo.setTitle(newTitle, mFolder.mLauncherDelegate.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.getFolderName().setText(mInfo.title);

        // Logging for folder creation flow
        StatsLogManager.newInstance(getContext()).logger()
                .withInstanceId(instanceId)
                .withItemInfo(mInfo)
                .withFromState(fromState)
                .withToState(ToState.TO_SUGGESTION0)
                // When LAUNCHER_FOLDER_LABEL_UPDATED event.edit_text does not have delimiter,
                // event is assumed to be folder creation on the server side.
                .withEditText(newTitle.toString())
                .log(LAUNCHER_FOLDER_AUTO_LABELED);
    }


    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        ItemInfo item;
        if (d.dragInfo instanceof WorkspaceItemFactory) {
            // Came from all apps -- make a copy
            item = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(getContext());
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            if (d.dragInfo instanceof AppPairInfo) {
                // dragged item is app pair
                item = new AppPairInfo((AppPairInfo) d.dragInfo);
            } else {
                // dragged item is WorkspaceItemInfo
                item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
            }
        } else {
            item = d.dragInfo;
        }
        mFolder.notifyDrop();

        // For covered or expanded folders: add the item, animate the DragView, and open
        if (mIsExpanded || mCoverDrawable != null) {
            getFolder().addFolderContent(item);
            // Animate the DragView into the folder center before opening
            if (d.dragView != null && mActivity instanceof Launcher launcher) {
                DragLayer dragLayer = launcher.getDragLayer();
                Rect to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
                workspace.setFinalTransitionTransform();
                float scaleRelative = dragLayer.getDescendantRectRelativeToSelf(this, to);
                workspace.resetTransitionTransform();
                int centerX = Math.round(scaleRelative * getWidth() / 2);
                int centerY = Math.round(scaleRelative * getHeight() / 2);
                to.offset(centerX - d.dragView.getMeasuredWidth() / 2,
                          centerY - d.dragView.getMeasuredHeight() / 2);
                float finalScale = 0.5f * scaleRelative;
                dragLayer.animateView(d.dragView, to, 0f, finalScale, finalScale,
                        DROP_IN_ANIMATION_DURATION, Interpolators.DECELERATE_2,
                        null, DragLayer.ANIMATION_END_DISAPPEAR, null);
            }
            postDelayed(() -> mFolder.animateOpen(), DROP_IN_ANIMATION_DURATION / 2);
            return;
        }

        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size(),
                itemReturnedOnFailedDrop
        );
    }

    /** Keep the notification dot up to date with the sum of all the content's dots. */
    public void updateDotInfo() {
        boolean hadDot = mDotInfo.hasDot();
        mDotInfo.reset();
        for (ItemInfo si : mInfo.getContents()) {
            mDotInfo.addDotInfo(mActivity.getDotInfoForItem(si));
        }
        boolean isDotted = mDotInfo.hasDot();
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((hadDot ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.basePreviewOffsetX;
        mTmpParams.transY += mBackground.basePreviewOffsetY;

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    @Override
    public void invalidate() {
        mDebugLogPending = true;
        super.invalidate();
    }

    @Override
    public void setIconVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        invalidate();
    }

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Debug logging BEFORE visibility check so we can see if the method runs at all
        if (DEBUG && mDebugLogPending) {
            mDebugLogPending = false;
            Log.d(TAG, "dispatchDraw: id=" + (mInfo != null ? mInfo.id : -1)
                    + " w=" + getWidth() + " h=" + getHeight()
                    + " hasCover=" + (mCoverDrawable != null)
                    + " isExpanded=" + mIsExpanded
                    + " bgVisible=" + mBackgroundIsVisible
                    + " spanX=" + (mInfo != null ? mInfo.spanX : -1)
                    + " spanY=" + (mInfo != null ? mInfo.spanY : -1)
                    + " previewItems=" + mCurrentPreviewItems.size());
        }

        if (!mBackgroundIsVisible) return;

        if (mCoverDrawable != null) {
            // Cover icon takes precedence over both normal and expanded modes
            drawCoverIcon(canvas);
        } else if (mIsExpanded) {
            drawExpandedFolder(canvas);
        } else {
            mPreviewItemManager.recomputePreviewDrawingParams();

            if (!mBackground.drawingDelegated()) {
                mBackground.drawBackground(canvas);
            }

            if (mCurrentPreviewItems.isEmpty() && !mAnimating) return;

            mPreviewItemManager.draw(canvas);

            if (!mBackground.drawingDelegated()) {
                mBackground.drawBackgroundStroke(canvas);
            }

            drawDot(canvas);
        }
    }

    // Flag to avoid spamming logcat on every frame
    private boolean mDebugLogPending = true;

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;

            if (mIsExpanded) {
                // Expanded NxN folder: use the shape area (square centered in view)
                int minDim = Math.min(getWidth(), getHeight());
                iconBounds.set(
                        (getWidth() - minDim) / 2, (getHeight() - minDim) / 2,
                        (getWidth() + minDim) / 2, (getHeight() + minDim) / 2);
            } else if (mCoverDrawable != null) {
                // Covered folder: use the preview background area
                iconBounds.set(
                        mBackground.basePreviewOffsetX,
                        mBackground.basePreviewOffsetY,
                        mBackground.basePreviewOffsetX + mBackground.previewSize,
                        mBackground.basePreviewOffsetY + mBackground.previewSize);
            } else {
                // Default 1x1 uncovered folder: original logic
                int iconSize = mActivity.getDeviceProfile().iconSizePx;
                iconBounds.left = (getWidth() - iconSize) / 2;
                iconBounds.right = iconBounds.left + iconSize;
                iconBounds.top = getPaddingTop();
                iconBounds.bottom = iconBounds.top + iconSize;

                float iconScale = (float) mBackground.previewSize / iconSize;
                Utilities.scaleRectAboutCenter(iconBounds, iconScale);
            }

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            // PreviewBackground.setup() may not have run for expanded/covered folders,
            // leaving getDotColor() at 0 (transparent). Resolve from theme as fallback.
            int dotColor = mBackground.getDotColor();
            if (dotColor == 0) {
                dotColor = Themes.getAttrColor(getContext(), R.attr.notificationDotColor);
            }
            mDotParams.dotColor = dotColor;
            mDotRenderer.draw(canvas, mDotParams);
        }
    }

    /**
     * Draws the folder background + a single cover icon centered at ~60% of preview size.
     * When expanded (span > 1), uses actual view dimensions instead of 1x1 previewSize.
     */
    private void drawCoverIcon(Canvas canvas) {
        if (DEBUG && mDebugLogPending) {
            Log.d(TAG, "drawCoverIcon: isExpanded=" + mIsExpanded
                    + " w=" + getWidth() + " h=" + getHeight()
                    + " previewSize=" + mBackground.previewSize);
        }
        if (mIsExpanded) {
            // Expanded mode: draw shape filling entire view, cover at 60% of view size
            int w = getWidth();
            int h = getHeight();
            mExpandedPaint.setStyle(Paint.Style.FILL);
            mExpandedPaint.setColor(mCachedCoverBgColor);

            // Use per-folder shape if set; otherwise M3 Large (16dp) default
            ShapeDelegate shape = resolveCurrentShape();
            int minDim = Math.min(w, h);
            float halfEdge = minDim / 2f;
            float shapeOffsetX = (w - minDim) / 2f;
            float shapeOffsetY = (h - minDim) / 2f;
            shape.drawShape(canvas, shapeOffsetX, shapeOffsetY, halfEdge, mExpandedPaint);

            // Cover icon at 60% of the view size, centered
            int previewSize = Math.min(w, h);
            int coverSize = (int) (previewSize * COVER_ICON_SIZE_RATIO);
            int offsetX = (w - coverSize) / 2;
            int offsetY = (h - coverSize) / 2;
            mCoverDrawable.setBounds(offsetX, offsetY, offsetX + coverSize, offsetY + coverSize);
            mCoverDrawable.draw(canvas);
        } else {
            // Normal 1x1 mode: use PreviewBackground dimensions with cover bg color
            mPreviewItemManager.recomputePreviewDrawingParams();

            if (!mBackground.drawingDelegated()) {
                mBackground.drawBackground(canvas, mCachedCoverBgColor);
            }

            int previewSize = mBackground.previewSize;
            int coverSize = (int) (previewSize * COVER_ICON_SIZE_RATIO);
            int offsetX = mBackground.basePreviewOffsetX + (previewSize - coverSize) / 2;
            int offsetY = mBackground.basePreviewOffsetY + (previewSize - coverSize) / 2;
            mCoverDrawable.setBounds(offsetX, offsetY, offsetX + coverSize, offsetY + coverSize);
            mCoverDrawable.draw(canvas);

            if (!mBackground.drawingDelegated()) {
                mBackground.drawBackgroundStroke(canvas);
            }
        }

        drawDot(canvas);
    }

    /**
     * Draws the expanded NxN folder view: app icons in a grid + open indicator in last cell.
     * Only called when mIsExpanded is true (square span >= 2x2).
     */
    private void drawExpandedFolder(Canvas canvas) {
        ExpandedGridParams g = computeExpandedGridParams();
        if (g == null) return;

        if (DEBUG && mDebugLogPending) {
            Log.d(TAG, "drawExpandedFolder: w=" + getWidth() + " h=" + getHeight()
                    + " span=" + g.spanX + "x" + g.spanY
                    + " cellW=" + g.cellW + " cellH=" + g.cellH
                    + " borderSpace=" + g.borderSpace + " iconSize=" + g.iconSize
                    + " items=" + (mInfo != null ? mInfo.getContents().size() : 0));
        }

        int w = getWidth();
        int h = getHeight();

        // Background shape fills the whole view (uses cached folder bg color + opacity)
        mExpandedPaint.setStyle(Paint.Style.FILL);
        mExpandedPaint.setColor(mCachedFolderBgColor);

        // M3 Large (16dp) rounded square for expanded folders (cached)
        int minDim = Math.min(w, h);
        float halfEdge = minDim / 2f;
        if (mCachedExpandedShape == null) {
            float ratio = Math.min(mCachedExpandedCornerPx / halfEdge, 1f);
            mCachedExpandedShape = new ShapeDelegate.RoundedSquare(ratio);
        }
        ShapeDelegate shape = mCachedExpandedShape;
        float shapeOffsetX = (w - minDim) / 2f;
        float shapeOffsetY = (h - minDim) / 2f;
        shape.drawShape(canvas, shapeOffsetX, shapeOffsetY, halfEdge, mExpandedPaint);

        List<ItemInfo> contents = mInfo.getContents();
        int totalCells = g.spanX * g.spanY;
        int maxIcons = totalCells - 1;

        // Draw app icons in row-major order, up to maxIcons.
        // Icons are cached in mExpandedIconCache to avoid per-frame allocation.
        int iconIdx = 0;
        for (int row = 0; row < g.spanY && iconIdx < maxIcons; row++) {
            for (int col = 0; col < g.spanX && iconIdx < maxIcons; col++) {
                if (iconIdx < contents.size()) {
                    ItemInfo item = contents.get(iconIdx);
                    int cellLeft = g.startX + col * (g.cellW + g.borderSpace);
                    int cellTop = g.startY + row * (g.cellH + g.borderSpace);
                    int iconLeft = cellLeft + (g.cellW - g.iconSize) / 2;
                    int iconTop = cellTop + (g.cellH - g.iconSize) / 2;

                    Drawable icon = mExpandedIconCache.get(iconIdx);
                    if (icon == null && item instanceof WorkspaceItemInfo wii) {
                        if (!wii.bitmap.isNullOrLowRes()) {
                            icon = wii.newIcon(getContext());
                            mExpandedIconCache.put(iconIdx, icon);
                        }
                    }
                    if (icon != null) {
                        icon.setBounds(iconLeft, iconTop,
                                iconLeft + g.iconSize, iconTop + g.iconSize);
                        icon.draw(canvas);
                    }
                }
                iconIdx++;
            }
        }

        // Always draw open indicator in the last cell (bottom-right)
        int lastCol = g.spanX - 1;
        int lastRow = g.spanY - 1;
        int indCellLeft = g.startX + lastCol * (g.cellW + g.borderSpace);
        int indCellTop = g.startY + lastRow * (g.cellH + g.borderSpace);
        drawOpenIndicator(canvas, indCellLeft, indCellTop, g.cellW, g.cellH);

        drawDot(canvas);
    }

    /**
     * Draws the expand_content Material icon as the open-folder indicator in the last cell.
     */
    private void drawOpenIndicator(Canvas canvas, int left, int top, int cellW, int cellH) {
        if (mOpenIndicatorDrawable == null) {
            mOpenIndicatorDrawable = getContext().getDrawable(R.drawable.ic_expand_content);
        }
        if (mOpenIndicatorDrawable == null) return;

        // Tint with M3 onSurfaceVariant for secondary icons on surface (cached)
        mOpenIndicatorDrawable.setTint(mCachedOnSurfaceVariantColor);

        // Size: 30% of cell dimension, centered in the cell
        int size = (int) (Math.min(cellW, cellH) * OPEN_INDICATOR_SIZE_RATIO);
        int cx = left + cellW / 2;
        int cy = top + cellH / 2;
        mOpenIndicatorDrawable.setBounds(
                cx - size / 2, cy - size / 2,
                cx + size / 2, cy + size / 2);
        mOpenIndicatorDrawable.draw(canvas);
    }

    /**
     * For expanded folders: determines which sub-cell was tapped.
     * @return cell index in row-major order: 0..N-2 for app icons, N-1 for open indicator, -1 if outside.
     */
    int getExpandedCellIndex(float x, float y) {
        ExpandedGridParams g = computeExpandedGridParams();
        if (g == null) return -1;

        float relX = x - g.startX;
        float relY = y - g.startY;
        if (relX < 0 || relY < 0) return -1;
        int col = Math.min((int) (relX / (g.cellW + g.borderSpace)), g.spanX - 1);
        int row = Math.min((int) (relY / (g.cellH + g.borderSpace)), g.spanY - 1);
        return row * g.spanX + col;
    }

    /** Pre-computed grid layout parameters for the expanded NxN folder view. */
    private static class ExpandedGridParams {
        final int spanX, spanY;
        final int cellW, cellH;
        final int borderSpace, iconSize;
        final int startX, startY;

        ExpandedGridParams(int spanX, int spanY, int cellW, int cellH,
                int borderSpace, int iconSize, int startX, int startY) {
            this.spanX = spanX;
            this.spanY = spanY;
            this.cellW = cellW;
            this.cellH = cellH;
            this.borderSpace = borderSpace;
            this.iconSize = iconSize;
            this.startX = startX;
            this.startY = startY;
        }
    }

    /**
     * Computes grid layout parameters for the expanded folder from DeviceProfile.
     * Shared by both {@link #drawExpandedFolder} and {@link #getExpandedCellIndex}.
     * @return params, or null if dimensions are invalid (zero cell/icon sizes).
     */
    private ExpandedGridParams computeExpandedGridParams() {
        if (mCachedGridParams != null) return mCachedGridParams;

        DeviceProfile dp = mActivity.getDeviceProfile();
        int w = getWidth();
        int h = getHeight();
        int spanX = mInfo != null ? mInfo.spanX : 2;
        int spanY = mInfo != null ? mInfo.spanY : 2;
        int cellW = dp.cellWidthPx;
        int cellH = dp.cellHeightPx;
        int borderSpace = dp.cellLayoutBorderSpacePx.x;
        int iconSize = dp.iconSizePx;

        if (iconSize <= 0 || cellW <= 0 || cellH <= 0) {
            if (DEBUG) Log.w(TAG, "computeExpandedGridParams: invalid dims, iconSize=" + iconSize
                    + " cellW=" + cellW + " cellH=" + cellH);
            return null;
        }

        int contentW = spanX * cellW + (spanX - 1) * borderSpace;
        int contentH = spanY * cellH + (spanY - 1) * borderSpace;
        int startX = (w - contentW) / 2;
        int startY = (h - contentH) / 2;

        mCachedGridParams = new ExpandedGridParams(spanX, spanY, cellW, cellH,
                borderSpace, iconSize, startX, startY);
        return mCachedGridParams;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Invalidate cached shape and grid params since they depend on view dimensions
        mCachedExpandedShape = null;
        mCachedGridParams = null;
        if (DEBUG && (w != oldw || h != oldh)) {
            Log.d(TAG, "onSizeChanged: id=" + (mInfo != null ? mInfo.id : -1)
                    + " " + oldw + "x" + oldh + " -> " + w + "x" + h
                    + " isExpanded=" + mIsExpanded
                    + " spanX=" + (mInfo != null ? mInfo.spanX : -1)
                    + " spanY=" + (mInfo != null ? mInfo.spanY : -1));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean shouldCenterIcon = mActivity.getDeviceProfile().iconCenterVertically;
        if (shouldCenterIcon) {
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
            int cellHeightPx = iconSize + mFolderName.getCompoundDrawablePadding()
                    + (int) Math.ceil(fm.bottom - fm.top);
            setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                    - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Sets the visibility of the icon's title text */
    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    /**
     * Returns the list of items which should be visible in the preview
     */
    public List<ItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    public void onItemsChanged(boolean animate) {
        mExpandedIconCache.clear();
        updatePreviewItems(false);
        updateDotInfo();
        setContentDescription(getAccessiblityTitle(mInfo.title));
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title);
        setContentDescription(getAccessiblityTitle(title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mLongPressHandled = false;
        }

        // FIX (BUG-D): Always forward events to super + longPressHelper FIRST.
        // This ensures ACTION_UP cancels pending long-press timers before we
        // process cell taps. Previously, the expanded folder ACTION_UP path
        // returned early, skipping these calls and leaving ghost long-press alive.
        super.onTouchEvent(event);
        mLongPressHelper.onTouchEvent(event);

        // Handle expanded folder touch: tap on sub-icon launches app directly
        // Only when no cover is set — cover hides the grid, so taps should open the folder
        if (mIsExpanded && mCoverDrawable == null && event.getAction() == MotionEvent.ACTION_UP) {
            // If a long-press fired (popup shown), consume the UP and don't also tap
            if (mLongPressHandled) {
                mLongPressHandled = false;
                return true;
            }

            int cellIndex = getExpandedCellIndex(event.getX(), event.getY());
            int spanX = mInfo != null ? mInfo.spanX : 2;
            int spanY = mInfo != null ? mInfo.spanY : 2;
            int totalCells = spanX * spanY;
            int lastCellIndex = totalCells - 1;
            List<ItemInfo> contents = mInfo.getContents();

            if (cellIndex == lastCellIndex) {
                // Last cell is always the open-folder indicator
                mFolder.animateOpen();
                return true;
            } else if (cellIndex >= 0 && cellIndex < lastCellIndex
                    && cellIndex < contents.size()) {
                // Launch the app via ItemClickHandler (handles disabled apps,
                // promise icons, work profile, safe mode, analytics, etc.)
                ItemInfo item = contents.get(cellIndex);
                if (item instanceof WorkspaceItemInfo wii) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    ItemClickHandler.onClickAppShortcut(this, wii, launcher);
                    return true;
                }
            }
        }

        // Keep receiving the rest of the events
        return true;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        mTouchArea.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
    }

    @Override
    public boolean performLongClick() {
        boolean result = super.performLongClick();
        if (result) {
            // Long-press was handled (popup shown) — prevent ACTION_UP from also
            // triggering a cell tap (app launch or folder open)
            mLongPressHandled = true;
        }
        return result;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return mIsExpanded ? DRAGGABLE_WIDGET : DRAGGABLE_ICON;
    }

    @NonNull
    @Override
    public SafeCloseable prepareDrawDragView() {
        // Ensure background draws during drag preview capture (it may be hidden
        // if the folder was opened/closed recently). Also hide the folder name
        // for expanded/covered folders so only the grid/cover content shows.
        boolean wasVisible = mBackgroundIsVisible;
        mBackgroundIsVisible = true;
        int oldNameVis = mFolderName.getVisibility();
        if (mIsExpanded || mCoverDrawable != null) {
            mFolderName.setVisibility(INVISIBLE);
        }
        // Safety: cancel animations and reset scale to ensure clean bitmap draw.
        // Uses super.setScaleX() to avoid going through setReorderBounceScale().
        animate().cancel();
        float oldScaleX = getScaleX();
        float oldScaleY = getScaleY();
        super.setScaleX(1f);
        super.setScaleY(1f);
        return () -> {
            mBackgroundIsVisible = wasVisible;
            mFolderName.setVisibility(oldNameVis);
            super.setScaleX(oldScaleX);
            super.setScaleY(oldScaleY);
        };
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        if (mIsExpanded) {
            // For expanded folders, use the full view bounds (the entire NxN grid)
            bounds.set(0, 0, getWidth(), getHeight());
        } else {
            getPreviewBounds(bounds);
        }
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        if (title == null) {
            // Avoids "Talkback -> Folder: null" announcement.
            title = getContext().getString(R.string.unnamed_folder);
        }
        int size = mInfo.getContents().size();
        if (size < MAX_NUM_ITEMS_IN_PREVIEW) {
            return getContext().getString(R.string.folder_name_format_exact, title, size);
        } else {
            return getContext().getString(R.string.folder_name_format_overflow, title,
                    MAX_NUM_ITEMS_IN_PREVIEW);
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        if (enableCursorHoverStates()) {
            mBackground.setHovered(hovered);
        }
    }

    /**
     * Interface that provides callbacks to a parent ViewGroup that hosts this FolderIcon.
     */
    public interface FolderIconParent {
        /**
         * Tells the FolderIconParent to draw a "leave-behind" when the Folder is open and leaving a
         * gap where the FolderIcon would be when the Folder is closed.
         */
        void drawFolderLeaveBehindForIcon(FolderIcon child);
        /**
         * Tells the FolderIconParent to stop drawing the "leave-behind" as the Folder is closed.
         */
        void clearFolderLeaveBehind(FolderIcon child);
    }
}
