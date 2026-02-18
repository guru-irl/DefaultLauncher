/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.launcher3.anim;

/**
 * M3 Expressive motion duration tokens.
 * <p>
 * Values follow the Material Design 3 motion specification. Use these constants
 * instead of hardcoded millisecond values for animation durations.
 */
public final class M3Durations {

    private M3Durations() {}

    public static final int SHORT_1 = 50;
    public static final int SHORT_2 = 100;
    public static final int SHORT_3 = 150;
    public static final int SHORT_4 = 200;

    public static final int MEDIUM_1 = 250;
    public static final int MEDIUM_2 = 300;
    public static final int MEDIUM_3 = 350;
    public static final int MEDIUM_4 = 400;

    public static final int LONG_1 = 450;
    public static final int LONG_2 = 500;
    public static final int LONG_3 = 550;
    public static final int LONG_4 = 600;

    public static final int EXTRA_LONG_1 = 700;
    public static final int EXTRA_LONG_2 = 800;
    public static final int EXTRA_LONG_3 = 900;
    public static final int EXTRA_LONG_4 = 1000;
}
