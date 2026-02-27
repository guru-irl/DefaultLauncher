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
package com.android.launcher3.search.providers;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Handler;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.SearchScorer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searches installed apps by title. Extracted from DefaultAppSearchAlgorithm.
 */
public class AppSearchProvider implements SearchProvider<AppInfo> {

    private static final int MAX_RESULTS = 5;

    private final LauncherAppState mAppState;
    private final Handler mResultHandler;

    public AppSearchProvider(Context context) {
        mAppState = LauncherAppState.getInstance(context);
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<AppInfo>> callback) {
        mAppState.getModel().enqueueModelUpdateTask((taskController, dataModel, apps) -> {
            List<AppInfo> result = getTitleMatchResult(apps.data, query);
            mResultHandler.post(() -> callback.accept(result));
        });
    }

    @Override
    public void cancel() {
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "apps";
    }

    private static List<AppInfo> getTitleMatchResult(List<AppInfo> apps, String query) {
        // Score every app using Jaro-Winkler with prefix/substring bonuses
        List<ScoredApp> scored = new ArrayList<>();
        int total = apps.size();
        for (int i = 0; i < total; i++) {
            AppInfo info = apps.get(i);
            float s = SearchScorer.score(query, info.title.toString());
            if (s > 0f) {
                scored.add(new ScoredApp(info, s));
            }
        }

        // Sort by score descending (best match first)
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        List<AppInfo> result = new ArrayList<>();
        int cap = Math.min(scored.size(), MAX_RESULTS);
        for (int i = 0; i < cap; i++) {
            result.add(scored.get(i).app);
        }
        return result;
    }

    private static class ScoredApp {
        final AppInfo app;
        final float score;

        ScoredApp(AppInfo app, float score) {
            this.app = app;
            this.score = score;
        }
    }
}
