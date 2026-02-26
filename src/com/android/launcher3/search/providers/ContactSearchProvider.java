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

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;

import com.android.launcher3.search.result.ContactResult;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Searches contacts via ContentResolver and ContactsContract.
 * Requires READ_CONTACTS permission (requested in Search Settings).
 */
public class ContactSearchProvider implements SearchProvider<ContactResult> {

    private static final String TAG = "ContactSearchProvider";
    private static final int MAX_RESULTS = 3;

    private final Context mContext;
    private final Handler mResultHandler;
    private volatile boolean mCancelled;

    public ContactSearchProvider(Context context) {
        mContext = context.getApplicationContext();
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void search(String query, Consumer<List<ContactResult>> callback) {
        if (mContext.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            mResultHandler.post(() -> callback.accept(Collections.emptyList()));
            return;
        }

        mCancelled = false;
        Executors.THREAD_POOL_EXECUTOR.execute(() -> {
            List<ContactResult> results = queryContacts(query);
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
        return "contacts";
    }

    @Override
    public int minQueryLength() {
        return 2;
    }

    private List<ContactResult> queryContacts(String query) {
        List<ContactResult> results = new ArrayList<>();
        ContentResolver resolver = mContext.getContentResolver();

        try (Cursor cursor = resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                },
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?",
                new String[]{"%" + query + "%"},
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {

            if (cursor == null) return results;

            while (cursor.moveToNext() && results.size() < MAX_RESULTS) {
                if (mCancelled) break;

                long contactId = cursor.getLong(0);
                String lookupKey = cursor.getString(1);
                String displayName = cursor.getString(2);
                String photoUriStr = cursor.getString(3);
                int hasPhone = cursor.getInt(4);

                Uri photoUri = photoUriStr != null ? Uri.parse(photoUriStr) : null;

                // Get phone numbers
                String[] phones = hasPhone > 0
                        ? getPhoneNumbers(resolver, contactId) : new String[0];

                // Get email addresses
                String[] emails = getEmailAddresses(resolver, contactId);

                results.add(new ContactResult(
                        contactId, lookupKey, displayName, photoUri, phones, emails));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error searching contacts", e);
        }

        return results;
    }

    private String[] getPhoneNumbers(ContentResolver resolver, long contactId) {
        List<String> phones = new ArrayList<>();
        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{String.valueOf(contactId)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext() && phones.size() < 3) {
                    phones.add(cursor.getString(0));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting phone numbers", e);
        }
        return phones.toArray(new String[0]);
    }

    private String[] getEmailAddresses(ContentResolver resolver, long contactId) {
        List<String> emails = new ArrayList<>();
        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS},
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                new String[]{String.valueOf(contactId)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext() && emails.size() < 3) {
                    emails.add(cursor.getString(0));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting email addresses", e);
        }
        return emails.toArray(new String[0]);
    }
}
