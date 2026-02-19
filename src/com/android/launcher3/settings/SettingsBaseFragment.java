package com.android.launcher3.settings;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Base class for all settings fragments. Provides:
 * <ul>
 *     <li>Edge-to-edge window insets handling (bottom padding for nav bar)</li>
 *     <li>RTL text direction support</li>
 *     <li>Lawnchair-style card group decoration with proper lifecycle management</li>
 * </ul>
 */
public abstract class SettingsBaseFragment extends PreferenceFragmentCompat {

    private CardGroupItemDecoration mCardDecoration;

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Edge-to-edge window insets
        View listView = getListView();
        final int bottomPadding = listView.getPaddingBottom();
        listView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    bottomPadding + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        // Overriding Text Direction in the Androidx preference library to support RTL
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);

        // Card group decoration for Lawnchair-style sectioned cards
        RecyclerView rv = getListView();
        mCardDecoration = new CardGroupItemDecoration(getContext());
        rv.addItemDecoration(mCardDecoration);
    }

    @Override
    public void onResume() {
        super.onResume();
        RecyclerView rv = getListView();
        for (int i = 0; i < rv.getChildCount(); i++) {
            rv.getChildAt(i).jumpDrawablesToCurrentState();
        }
    }

    @Override
    public void onDestroyView() {
        if (mCardDecoration != null) {
            getListView().removeItemDecoration(mCardDecoration);
            mCardDecoration = null;
        }
        super.onDestroyView();
    }
}
