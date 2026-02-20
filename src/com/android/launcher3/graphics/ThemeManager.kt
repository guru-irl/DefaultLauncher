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

package com.android.launcher3.graphics

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.android.launcher3.EncryptionType
import com.android.launcher3.Item
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SimpleBroadcastReceiver
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/** Centralized class for managing Launcher icon theming */
@LauncherAppSingleton
class ThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LauncherPrefs,
    private val iconControllerFactory: IconControllerFactory,
    lifecycle: DaggerSingletonTracker,
) {

    /** Representation of the current icon state */
    var iconState = parseIconState(null)
        private set

    var isMonoThemeEnabled
        set(value) = prefs.put(THEMED_ICONS, value)
        get() = prefs.get(THEMED_ICONS)

    val themeController
        get() = iconState.themeController

    val isIconThemeEnabled
        get() = themeController != null

    val iconShape
        get() = iconState.iconShape

    val folderShape
        get() = iconState.folderShape

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()

    init {
        val receiver = SimpleBroadcastReceiver(context, MAIN_EXECUTOR) { verifyIconState() }
        receiver.registerPkgActions("android", ACTION_OVERLAY_CHANGED)

        val keys = (iconControllerFactory.prefKeys + PREF_ICON_SHAPE + LauncherPrefs.ICON_SIZE_SCALE
            + PREF_ICON_SHAPE_DRAWER + LauncherPrefs.ICON_SIZE_SCALE_DRAWER
            + LauncherPrefs.APPLY_ADAPTIVE_SHAPE + LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER)

        val keysArray = keys.toTypedArray()
        val prefKeySet = keys.map { it.sharedPrefKey }
        val prefListener = LauncherPrefChangeListener { key ->
            if (prefKeySet.contains(key)) verifyIconState()
        }
        prefs.addListener(prefListener, *keysArray)

        // Detect dark mode and wallpaper color changes via configuration callbacks
        val configCallbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = verifyIconState()
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        }
        context.registerComponentCallbacks(configCallbacks)

        lifecycle.addCloseable {
            receiver.unregisterReceiverSafely()
            prefs.removeListener(prefListener, *keysArray)
            context.unregisterComponentCallbacks(configCallbacks)
        }
    }

    private fun verifyIconState() {
        val newState = parseIconState(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    /** Called when system configuration changes (e.g., dark mode, wallpaper colors). */
    fun onConfigurationChanged() = verifyIconState()

    fun addChangeListener(listener: ThemeChangeListener) = listeners.add(listener)

    fun removeChangeListener(listener: ThemeChangeListener) = listeners.remove(listener)

    private fun parseIconState(oldState: IconState?): IconState {
        // Migrate "none" shape to adaptive-off + clear shape pref
        migrateNoneShape(PREF_ICON_SHAPE, LauncherPrefs.APPLY_ADAPTIVE_SHAPE)
        migrateNoneShape(PREF_ICON_SHAPE_DRAWER, LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER)

        val applyAdaptive = prefs.get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE)
        val applyAdaptiveDrawer = prefs.get(LauncherPrefs.APPLY_ADAPTIVE_SHAPE_DRAWER)

        // Home shape: if adaptive is off, use NONE shape; otherwise use the selected shape
        val shapeModel = if (!applyAdaptive) {
            ShapesProvider.iconShapes.firstOrNull { it.key == ShapesProvider.NONE_KEY }
        } else {
            prefs.get(PREF_ICON_SHAPE).let { shapeOverride ->
                ShapesProvider.iconShapes.firstOrNull { it.key == shapeOverride }
            }
        }
        val iconMask =
            when {
                shapeModel != null -> shapeModel.pathString
                CONFIG_ICON_MASK_RES_ID == Resources.ID_NULL -> ""
                else -> context.resources.getString(CONFIG_ICON_MASK_RES_ID)
            }

        val iconShape =
            if (oldState != null && oldState.iconMask == iconMask) oldState.iconShape
            else pickBestShape(iconMask)

        val folderShapeMask = shapeModel?.pathString ?: iconMask
        val folderShape =
            when {
                oldState != null && oldState.folderShapeMask == folderShapeMask ->
                    oldState.folderShape
                folderShapeMask == iconMask || folderShapeMask.isEmpty() -> iconShape
                else -> pickBestShape(folderShapeMask)
            }

        val iconSizeScale = (prefs.get(LauncherPrefs.ICON_SIZE_SCALE).toFloatOrNull() ?: 1f)
            .coerceIn(0.5f, 1.0f)

        // Drawer-specific shape/size
        val drawerShapeModel = if (!applyAdaptiveDrawer) {
            ShapesProvider.iconShapes.firstOrNull { it.key == ShapesProvider.NONE_KEY }
        } else {
            prefs.get(PREF_ICON_SHAPE_DRAWER).let { shapeOverride ->
                ShapesProvider.iconShapes.firstOrNull { it.key == shapeOverride }
            }
        }
        val iconMaskDrawer =
            when {
                drawerShapeModel != null -> drawerShapeModel.pathString
                CONFIG_ICON_MASK_RES_ID == Resources.ID_NULL -> ""
                else -> context.resources.getString(CONFIG_ICON_MASK_RES_ID)
            }
        val iconShapeDrawer =
            if (oldState != null && oldState.iconMaskDrawer == iconMaskDrawer) oldState.iconShapeDrawer
            else pickBestShape(iconMaskDrawer)

        val iconSizeScaleDrawer = (prefs.get(LauncherPrefs.ICON_SIZE_SCALE_DRAWER).toFloatOrNull() ?: 1f)
            .coerceIn(0.5f, 1.0f)

        val nightMode = (context.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        return IconState(
            iconMask = iconMask,
            folderShapeMask = folderShapeMask,
            themeController = iconControllerFactory.createThemeController(),
            iconScale = shapeModel?.iconScale ?: 1f,
            iconSizeScale = iconSizeScale,
            nightMode = nightMode,
            iconShape = iconShape,
            folderShape = folderShape,
            iconMaskDrawer = iconMaskDrawer,
            iconScaleDrawer = drawerShapeModel?.iconScale ?: 1f,
            iconSizeScaleDrawer = iconSizeScaleDrawer,
            iconShapeDrawer = iconShapeDrawer,
            applyAdaptiveShape = applyAdaptive,
            applyAdaptiveShapeDrawer = applyAdaptiveDrawer,
        )
    }

    /** Migrate "none" shape to adaptive-off + clear shape pref (one-time). */
    private fun migrateNoneShape(shapePref: com.android.launcher3.ConstantItem<String>,
                                  adaptivePref: com.android.launcher3.ConstantItem<Boolean>) {
        val currentShape = prefs.get(shapePref)
        if (currentShape == ShapesProvider.NONE_KEY) {
            prefs.put(adaptivePref, false)
            prefs.put(shapePref, "")
        }
    }

    data class IconState(
        val iconMask: String,
        val folderShapeMask: String,
        val themeController: IconThemeController?,
        val themeCode: String = themeController?.themeID ?: "no-theme",
        val iconScale: Float = 1f,
        val iconSizeScale: Float = 1f,
        val nightMode: Boolean = false,
        val iconShape: ShapeDelegate,
        val folderShape: ShapeDelegate,
        // Drawer-specific fields
        val iconMaskDrawer: String = "",
        val iconScaleDrawer: Float = 1f,
        val iconSizeScaleDrawer: Float = 1f,
        val iconShapeDrawer: ShapeDelegate = iconShape,
        // Adaptive shape flags
        val applyAdaptiveShape: Boolean = true,
        val applyAdaptiveShapeDrawer: Boolean = true,
    ) {
        fun toUniqueId() = "${iconMask.hashCode()},$themeCode,$iconSizeScale,$nightMode,$applyAdaptiveShape,$applyAdaptiveShapeDrawer"
    }

    /** Interface for receiving theme change events */
    fun interface ThemeChangeListener {
        fun onThemeChanged()
    }

    open class IconControllerFactory @Inject constructor(protected val prefs: LauncherPrefs) {

        open val prefKeys: List<Item> = listOf(THEMED_ICONS)

        open fun createThemeController(): IconThemeController? {
            return if (prefs.get(THEMED_ICONS)) MONO_THEME_CONTROLLER else null
        }
    }

    companion object {

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeManager)
        const val KEY_ICON_SHAPE = "icon_shape_model"

        const val KEY_ICON_SHAPE_DRAWER = "icon_shape_model_drawer"

        const val KEY_THEMED_ICONS = "themed_icons"
        @JvmField val THEMED_ICONS = backedUpItem(KEY_THEMED_ICONS, false, EncryptionType.ENCRYPTED)
        @JvmField val PREF_ICON_SHAPE = backedUpItem(KEY_ICON_SHAPE, "", EncryptionType.ENCRYPTED)
        @JvmField val PREF_ICON_SHAPE_DRAWER = backedUpItem(KEY_ICON_SHAPE_DRAWER, "", EncryptionType.ENCRYPTED)

        private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
        private val CONFIG_ICON_MASK_RES_ID: Int =
            Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

        // Use a constant to allow equality check in verifyIconState
        private val MONO_THEME_CONTROLLER = MonoIconThemeController()
    }
}
