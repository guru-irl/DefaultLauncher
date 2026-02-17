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
import android.provider.ContactsContract;

/**
 * A search result representing a contact.
 */
public class ContactResult implements Launchable {

    public final long contactId;
    public final String lookupKey;
    public final String displayName;
    public final Uri photoUri;
    public final String[] phoneNumbers;
    public final String[] emailAddresses;

    public ContactResult(long contactId, String lookupKey, String displayName,
            Uri photoUri, String[] phoneNumbers, String[] emailAddresses) {
        this.contactId = contactId;
        this.lookupKey = lookupKey;
        this.displayName = displayName;
        this.photoUri = photoUri;
        this.phoneNumbers = phoneNumbers;
        this.emailAddresses = emailAddresses;
    }

    @Override
    public boolean launch(Context context) {
        try {
            Uri contactUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLabel() {
        return displayName;
    }

    @Override
    public Drawable getIcon(Context context) {
        return null; // Photo loaded separately via photoUri
    }

    /** Returns the primary phone number, or null. */
    public String getPrimaryPhone() {
        return phoneNumbers != null && phoneNumbers.length > 0 ? phoneNumbers[0] : null;
    }

    /** Returns the primary email, or null. */
    public String getPrimaryEmail() {
        return emailAddresses != null && emailAddresses.length > 0 ? emailAddresses[0] : null;
    }
}
