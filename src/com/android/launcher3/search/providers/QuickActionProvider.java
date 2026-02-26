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

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.webkit.URLUtil;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.search.result.QuickAction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Detects patterns in the query and creates quick actions (call, email, web search, open URL).
 */
public class QuickActionProvider implements SearchProvider<QuickAction> {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[\\d\\s\\-\\+\\(\\)]{7,}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^\\S+@\\S+\\.\\S+$");
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?://)?[\\w\\-]+(\\.[\\w\\-]+)+(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    private final Context mContext;
    private final Handler mResultHandler;

    public QuickActionProvider(Context context) {
        mContext = context.getApplicationContext();
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<QuickAction>> callback) {
        List<QuickAction> actions = new ArrayList<>();
        String trimmed = query.trim();

        // Phone number detection
        if (PHONE_PATTERN.matcher(trimmed).matches()) {
            String cleaned = trimmed.replaceAll("[\\s\\-\\(\\)]", "");
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleaned));
            actions.add(new QuickAction(
                    QuickAction.Type.CALL,
                    mContext.getString(R.string.search_action_call, trimmed),
                    R.drawable.ic_call,
                    dialIntent));
        }

        // Email detection
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + trimmed));
            actions.add(new QuickAction(
                    QuickAction.Type.EMAIL,
                    mContext.getString(R.string.search_action_email, trimmed),
                    R.drawable.ic_email,
                    emailIntent));
        }

        // URL detection — require a dot and at least 2-char TLD to avoid false positives
        if (looksLikeUrl(trimmed)) {
            String url = trimmed;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            actions.add(new QuickAction(
                    QuickAction.Type.URL,
                    mContext.getString(R.string.search_action_open_url, trimmed),
                    R.drawable.ic_link,
                    urlIntent));
        }

        // Web search (always present)
        Intent webSearchIntent = buildWebSearchIntent(query);
        actions.add(new QuickAction(
                QuickAction.Type.WEB_SEARCH,
                mContext.getString(R.string.search_action_web_search, query),
                R.drawable.ic_web_search,
                webSearchIntent));

        mResultHandler.post(() -> callback.accept(actions));
    }

    @Override
    public void cancel() {
        mResultHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public String category() {
        return "quick_actions";
    }

    private Intent buildWebSearchIntent(String query) {
        String webApp = LauncherPrefs.get(mContext).get(LauncherPrefs.SEARCH_WEB_APP);
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);

        if (!"default".equals(webApp)) {
            try {
                ComponentName cn = ComponentName.unflattenFromString(webApp);
                if (cn != null) {
                    intent.setComponent(cn);
                }
            } catch (Exception ignored) {
                // Fall through to system default
            }
        }
        return intent;
    }

    private static boolean looksLikeUrl(String text) {
        return URL_PATTERN.matcher(text).matches();
    }
}
