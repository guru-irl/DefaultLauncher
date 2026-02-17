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

import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.search.result.FileResult;
import com.android.launcher3.util.Executors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searches files on external storage via java.io.File walk.
 * Requires MANAGE_EXTERNAL_STORAGE permission (requested in Search Settings).
 * Walks common directories to limit scope.
 */
public class FileSearchProvider implements SearchProvider<FileResult> {

    private static final String TAG = "FileSearchProvider";
    private static final int MAX_RESULTS = 5;
    private static final int MAX_DEPTH = 4;

    private static final String[] SEARCH_DIRS = {
            "Download", "Downloads", "Documents", "DCIM", "Pictures",
            "Music", "Movies", "Ringtones", "Podcasts"
    };

    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    public FileSearchProvider() {
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<FileResult>> callback) {
        if (!Environment.isExternalStorageManager()) {
            mResultHandler.post(() -> callback.accept(Collections.emptyList()));
            return;
        }

        mCancelled = false;
        Executors.MODEL_EXECUTOR.execute(() -> {
            List<FileResult> results = searchFiles(query);
            if (!mCancelled) {
                mResultHandler.post(() -> callback.accept(results));
            }
        });
    }

    @Override
    public void cancel() {
        mCancelled = true;
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "files";
    }

    @Override
    public int minQueryLength() {
        return 3;
    }

    private List<FileResult> searchFiles(String query) {
        List<FileResult> results = new ArrayList<>();
        String queryLower = query.toLowerCase();
        File storage = Environment.getExternalStorageDirectory();

        for (String dirName : SEARCH_DIRS) {
            if (mCancelled || results.size() >= MAX_RESULTS) break;
            File dir = new File(storage, dirName);
            if (dir.exists() && dir.isDirectory()) {
                searchDirectory(dir, queryLower, results, 0);
            }
        }

        return results;
    }

    private void searchDirectory(File dir, String queryLower,
            List<FileResult> results, int depth) {
        if (mCancelled || results.size() >= MAX_RESULTS || depth > MAX_DEPTH) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (mCancelled || results.size() >= MAX_RESULTS) break;

            if (file.isDirectory()) {
                // Skip hidden directories
                if (!file.getName().startsWith(".")) {
                    searchDirectory(file, queryLower, results, depth + 1);
                }
            } else {
                if (file.getName().toLowerCase().contains(queryLower)) {
                    String mimeType = FileResult.guessMimeType(file.getName());
                    results.add(new FileResult(
                            file.getName(),
                            file.getAbsolutePath(),
                            file.length(),
                            mimeType,
                            file.lastModified()));
                }
            }
        }
    }
}
