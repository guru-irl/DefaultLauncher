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
 * Concrete in-process custom widget: the SEARCH pill. Registered via
 * {@code R.array.launcher_custom_widgets} and instantiated by
 * {@link CustomWidgetManager} (reflection, Context constructor).
 *
 * <p>Fixed 2x1 size, NOT resizable (RESIZE_NONE, equal min/max spans).
 */
public class SearchWidgetPlugin implements CustomWidgetPlugin {

    // CustomWidgetManager instantiates plugins reflectively via a (Context)
    // constructor, so the parameter is required even though the view is built
    // from the host view's (activity) context in onViewCreated.
    public SearchWidgetPlugin(Context context) {
    }

    @Override
    public void updateWidgetInfo(AppWidgetProviderInfo info, Context context) {
        LauncherAppWidgetProviderInfo lpi = (LauncherAppWidgetProviderInfo) info;
        lpi.label = context.getString(R.string.search_widget_label);
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
        // A PNG preview rendered with the real font (previewImage is drawn
        // straight to a bitmap, which works for in-process custom widgets).
        lpi.previewImage = R.drawable.widget_preview_search;
        // Fixed 2x1, NOT resizable: equal min/max spans + RESIZE_NONE so the
        // resize frame shows no handles (see AppWidgetResizeFrame.setupForWidget).
        lpi.spanX = 2;
        lpi.spanY = 1;
        lpi.minSpanX = 2;
        lpi.minSpanY = 1;
        lpi.maxSpanX = 2;
        lpi.maxSpanY = 1;
        lpi.resizeMode = AppWidgetProviderInfo.RESIZE_NONE;
    }

    @Override
    public void onViewCreated(AppWidgetHostView parent) {
        // Use the host view's context (the Launcher activity), NOT the plugin's
        // stored application context: SearchWidgetView resolves the Launcher via
        // Launcher.getLauncher(getContext()) to open the all-apps search on tap,
        // which requires an activity context.
        SearchWidgetView view = new SearchWidgetView(parent.getContext());
        parent.removeAllViews();
        parent.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
