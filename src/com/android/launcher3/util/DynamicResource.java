/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.util;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.FractionRes;
import androidx.annotation.IntegerRes;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.systemui.plugins.ResourceProvider;

import javax.inject.Inject;

/**
 * Utility class to support customizing resource values
 *
 * To load resources, call
 * DynamicResource.provider(context).getInt(resId) or any other supported methods
 *
 * To allow customization for a particular resource, add them to dynamic_resources.xml
 */
@LauncherAppSingleton
public class DynamicResource implements ResourceProvider {

    private static final DaggerSingletonObject<DynamicResource> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getDynamicResource);

    private final Context mContext;

    @Inject
    public DynamicResource(@ApplicationContext Context context) {
        mContext = context;
    }

    @Override
    public int getInt(@IntegerRes int resId) {
        return mContext.getResources().getInteger(resId);
    }

    @Override
    public float getFraction(@FractionRes int resId) {
        return mContext.getResources().getFraction(resId, 1, 1);
    }

    @Override
    public float getDimension(@DimenRes int resId) {
        return mContext.getResources().getDimension(resId);
    }

    @Override
    public int getColor(@ColorRes int resId) {
        return mContext.getResources().getColor(resId, null);
    }

    @Override
    public float getFloat(@DimenRes int resId) {
        return mContext.getResources().getFloat(resId);
    }

    /**
     * Returns the currently active or default provider
     */
    public static ResourceProvider provider(Context context) {
        return DynamicResource.INSTANCE.get(context);
    }
}
