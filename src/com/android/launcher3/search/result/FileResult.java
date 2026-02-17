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
package com.android.launcher3.search.result;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * A search result representing a file on external storage.
 */
public class FileResult implements Launchable {

    public final String name;
    public final String path;
    public final long size;
    public final String mimeType;
    public final long lastModified;

    public FileResult(String name, String path, long size, String mimeType, long lastModified) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.mimeType = mimeType;
        this.lastModified = lastModified;
    }

    @Override
    public boolean launch(Context context) {
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType != null ? mimeType : "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLabel() {
        return name;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null; // Uses mime type icon
    }

    /** Formats file size as human-readable string. */
    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    /** Returns the file extension, or empty string. */
    public String getExtension() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /** Guesses MIME type from file extension if not already set. */
    public static String guessMimeType(String filename) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(
                Uri.fromFile(new File(filename)).toString());
        if (ext != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            if (mime != null) return mime;
        }
        return "*/*";
    }
}
