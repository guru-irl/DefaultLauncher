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
        lpi.previewImage = R.drawable.widget_preview_danfo_clock;
        // Default 2x2, minimum 2x2, no maximum (grid-bounded).
        lpi.spanX = 2;
        lpi.spanY = 2;
        lpi.minSpanX = 2;
        lpi.minSpanY = 2;
        lpi.maxSpanX = 0;
        lpi.maxSpanY = 0;
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
