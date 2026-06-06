package com.android.launcher3.widget.custom;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.systemui.plugins.CustomWidgetPlugin;

/**
 * Concrete in-process custom widget: the Danfo clock. Registered via
 * {@code R.array.launcher_custom_widgets} and instantiated by
 * {@link CustomWidgetManager} (reflection, Context constructor).
 */
public class DanfoClockWidgetPlugin implements CustomWidgetPlugin {

    private final Context mContext;

    public DanfoClockWidgetPlugin(Context context) {
        mContext = context;
    }

    @Override
    public void updateWidgetInfo(AppWidgetProviderInfo info, Context context) {
        LauncherAppWidgetProviderInfo lpi = (LauncherAppWidgetProviderInfo) info;
        lpi.label = context.getString(R.string.clock_widget_label);
        // The info is cloned from an ARBITRARY installed provider (see
        // CustomWidgetManager.getAndAddInfo), so it inherits all of that
        // provider's fields. That provider differs per device, so any inherited
        // field can cause device-specific breakage. Normalize the ones that
        // affect the picker and preview:
        //  - widgetCategory: must include HOME_SCREEN or the home-screen picker
        //    filters our widget out (the likely cause of it not appearing on
        //    some devices where providers.get(0) is keyguard/search-only).
        //  - widgetFeatures: clear inherited flags like FEATURE_HIDE_FROM_PICKER.
        //  - previewLayout: otherwise wins in the preview loader and renders the
        //    foreign widget ("Can't load widget"); we use previewImage instead.
        //  - descriptionRes/configure: clear foreign description text + config.
        lpi.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
        lpi.widgetFeatures = 0;
        // On Android V+ the preview loader checks generatedPreviewCategories
        // FIRST and loads the cloned provider's generated preview (e.g. Chrome's
        // icon) before ever reaching previewImage. Clear it so our PNG is used.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lpi.generatedPreviewCategories = 0;
        }
        lpi.previewLayout = android.content.res.Resources.ID_NULL;
        lpi.descriptionRes = android.content.res.Resources.ID_NULL;
        lpi.configure = null;
        // A PNG preview rendered with the real fonts (previewImage is drawn
        // straight to a bitmap, which works for in-process custom widgets).
        lpi.previewImage = R.drawable.widget_preview_danfo_clock;
        // Default 2x2, minimum 2x2, no maximum (grid-bounded).
        lpi.spanX = 2;
        lpi.spanY = 2;
        lpi.minSpanX = 2;
        lpi.minSpanY = 2;
        // maxSpan must be > 1 and > minSpan for the resize frame to enable
        // handles (see AppWidgetResizeFrame.setupForWidget); the frame clamps
        // to the real grid anyway.
        com.android.launcher3.InvariantDeviceProfile idp =
                com.android.launcher3.InvariantDeviceProfile.INSTANCE.get(context);
        lpi.maxSpanX = Math.max(3, idp.numColumns);
        lpi.maxSpanY = Math.max(3, idp.numRows);
        lpi.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL
                | AppWidgetProviderInfo.RESIZE_VERTICAL;
    }

    @Override
    public void onViewCreated(AppWidgetHostView parent) {
        DanfoClockView view = new DanfoClockView(mContext);
        parent.removeAllViews();
        parent.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
