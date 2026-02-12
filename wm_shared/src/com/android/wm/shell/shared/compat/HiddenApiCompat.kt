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

package com.android.wm.shell.shared.compat

import android.app.TaskInfo
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.view.Choreographer
import android.view.SurfaceControl
import android.view.SurfaceView

/**
 * Reflection-based access to hidden Android framework APIs.
 * These APIs are available at runtime on device but not in the compile SDK stubs.
 */
object HiddenApiCompat {

    /** SurfaceControl.Transaction.setFrameTimeline(vsyncId) */
    fun setFrameTimelineVsyncId(transaction: SurfaceControl.Transaction): SurfaceControl.Transaction {
        try {
            val vsyncId = Choreographer::class.java.getMethod("getVsyncId")
                .invoke(Choreographer.getInstance()) as Long
            SurfaceControl.Transaction::class.java
                .getMethod("setFrameTimeline", Long::class.javaPrimitiveType)
                .invoke(transaction, vsyncId)
        } catch (_: Exception) {
            // Fallback: no-op if API not available
        }
        return transaction
    }

    /** TaskInfo.isTopActivityNoDisplay */
    fun isTopActivityNoDisplay(task: TaskInfo): Boolean {
        return try {
            TaskInfo::class.java.getField("isTopActivityNoDisplay").getBoolean(task)
        } catch (_: Exception) { false }
    }

    /** TaskInfo.isActivityStackTransparent */
    fun isActivityStackTransparent(task: TaskInfo): Boolean {
        return try {
            TaskInfo::class.java.getField("isActivityStackTransparent").getBoolean(task)
        } catch (_: Exception) { false }
    }

    /** TaskInfo.userId */
    fun getUserId(task: TaskInfo): Int {
        return try {
            TaskInfo::class.java.getField("userId").getInt(task)
        } catch (_: Exception) { 0 }
    }

    /** TaskInfo.topActivityInfo */
    fun getTopActivityInfo(task: TaskInfo): ActivityInfo? {
        return try {
            TaskInfo::class.java.getField("topActivityInfo").get(task) as? ActivityInfo
        } catch (_: Exception) { null }
    }

    /** TaskInfo.isResizeable */
    fun isResizeable(task: TaskInfo): Boolean {
        return try {
            TaskInfo::class.java.getField("isResizeable").getBoolean(task)
        } catch (_: Exception) { false }
    }

    /** TaskInfo.appCompatTaskInfo */
    fun getAppCompatTaskInfo(task: TaskInfo): Any? {
        return try {
            TaskInfo::class.java.getField("appCompatTaskInfo").get(task)
        } catch (_: Exception) { null }
    }

    /** AppCompatTaskInfo.hasOptOutEdgeToEdge() */
    fun hasOptOutEdgeToEdge(appCompatTaskInfo: Any?): Boolean {
        if (appCompatTaskInfo == null) return false
        return try {
            appCompatTaskInfo.javaClass.getMethod("hasOptOutEdgeToEdge")
                .invoke(appCompatTaskInfo) as Boolean
        } catch (_: Exception) { false }
    }

    /** PackageManager.getHomeActivities(list) */
    fun getHomeActivities(pm: PackageManager): android.content.ComponentName? {
        return try {
            val list = ArrayList<Any>()
            PackageManager::class.java.getMethod("getHomeActivities", java.util.List::class.java)
                .invoke(pm, list) as? android.content.ComponentName
        } catch (_: Exception) { null }
    }

    /** PackageManager.getPackageInfoAsUser(packageName, flags, userId) */
    fun getPackageInfoAsUser(
        pm: PackageManager,
        packageName: String,
        flags: Int,
        userId: Int
    ): PackageInfo? {
        return try {
            PackageManager::class.java.getMethod(
                "getPackageInfoAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(pm, packageName, flags, userId) as? PackageInfo
        } catch (_: Exception) { null }
    }

    /** SurfaceView.setCornerRadius(radius) */
    fun setCornerRadius(view: SurfaceView, radius: Float) {
        try {
            SurfaceView::class.java.getMethod("setCornerRadius", Float::class.javaPrimitiveType)
                .invoke(view, radius)
        } catch (_: Exception) {
            // Fallback: no-op
        }
    }

    /** Surface.attachAndQueueBufferWithColorSpace(buffer, colorSpace) */
    fun attachAndQueueBufferWithColorSpace(
        surface: android.view.Surface,
        buffer: HardwareBuffer?,
        colorSpace: ColorSpace?
    ) {
        try {
            android.view.Surface::class.java.getMethod(
                "attachAndQueueBufferWithColorSpace",
                HardwareBuffer::class.java,
                ColorSpace::class.java
            ).invoke(surface, buffer, colorSpace)
        } catch (_: Exception) {
            // Fallback: no-op
        }
    }

    /** TransitionInfo.Change.leash */
    fun getLeash(change: Any): SurfaceControl? {
        return try {
            change.javaClass.getMethod("getLeash").invoke(change) as? SurfaceControl
        } catch (_: Exception) { null }
    }

    /** TransitionInfo.Change.endAbsBounds */
    fun getEndAbsBounds(change: Any): android.graphics.Rect? {
        return try {
            change.javaClass.getMethod("getEndAbsBounds").invoke(change) as? android.graphics.Rect
        } catch (_: Exception) { null }
    }
}
