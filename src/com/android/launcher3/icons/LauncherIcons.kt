/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.icons

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.UserIconInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Wrapper class to provide access to [BaseIconFactory] and also to provide pool of this class that
 * are threadsafe.
 */
class LauncherIcons
@AssistedInject
internal constructor(
    @ApplicationContext context: Context,
    idp: InvariantDeviceProfile,
    private var themeManager: ThemeManager,
    private var userCache: UserCache,
    @Assisted private val pool: ConcurrentLinkedQueue<LauncherIcons>,
) : BaseIconFactory(context, idp.fillResIconDpi, idp.iconBitmapSize), AutoCloseable {

    private val iconScale = themeManager.iconState.iconScale
    private val iconSizeScale = themeManager.iconState.iconSizeScale
    private val isNoneShape = themeManager.iconState.iconMask == ShapesProvider.NONE_PATH
    private val skipWrapNonAdaptive = themeManager.iconState.skipWrapNonAdaptive
    private val useOemForNative = themeManager.iconState.useOemForNative
    private val wrapperBgColorInt = themeManager.iconState.wrapperBgColor
    private var mUseOemShape = false

    init {
        mThemeController = themeManager.themeController
    }

    override fun normalizeAndWrapToAdaptiveIcon(
        icon: Drawable?,
        outScale: FloatArray,
    ): AdaptiveIconDrawable? {
        if (icon == null) return null
        outScale[0] = iconSizeScale
        return wrapToAdaptiveIcon(icon)
    }

    override fun createBadgedIconBitmap(icon: Drawable, options: IconOptions?): BitmapInfo {
        val isPackIcon = IconPackDrawable.isFromPack(icon)
        val renderIcon = IconPackDrawable.unwrap(icon)

        // For "none" shape or skipWrapNonAdaptive with pack icons,
        // bypass the adaptive wrapping entirely — draw the raw icon at the user's size
        // scale without shadow or shape.
        if ((isNoneShape || skipWrapNonAdaptive) && isPackIcon) {
            val bitmap = createIconBitmap(renderIcon, iconSizeScale, MODE_DEFAULT)
            val color = ColorExtractor.findDominantColorByHue(bitmap)
            return BitmapInfo.of(bitmap, color).withFlags(getBitmapFlagOp(options))
        }
        // Set OEM flag for native system icons when useOemForNative is active
        mUseOemShape = useOemForNative && !isPackIcon
        val result = super.createBadgedIconBitmap(renderIcon, options)
        mUseOemShape = false
        return result
    }

    /** Recycles a LauncherIcons that may be in-use. */
    fun recycle() {
        clear()
        pool.add(this)
    }

    override fun getUserInfo(user: UserHandle): UserIconInfo {
        return userCache.getUserInfo(user)
    }

    override fun getShapePath(drawable: AdaptiveIconDrawable, iconBounds: Rect): Path {
        if (!Flags.enableLauncherIconShapes() || mUseOemShape) return super.getShapePath(drawable, iconBounds)
        return themeManager.iconShape.getPath(iconBounds)
    }

    override fun getIconScale(): Float {
        if (!Flags.enableLauncherIconShapes() || mUseOemShape) return super.getIconScale()
        return themeManager.iconState.iconScale
    }

    override fun wrapToAdaptiveIcon(icon: Drawable): AdaptiveIconDrawable {
        // Use the user's chosen wrapper BG color (defaults to transparent) so icon
        // pack icons can optionally get a colored fill behind the shape.
        mWrapperBackgroundColor = wrapperBgColorInt
        return super.wrapToAdaptiveIcon(icon)
    }

    override fun drawAdaptiveIcon(
        canvas: Canvas,
        drawable: AdaptiveIconDrawable,
        overridePath: Path,
    ) {
        if (!Flags.enableLauncherIconShapes() || mUseOemShape) {
            super.drawAdaptiveIcon(canvas, drawable, overridePath)
            return
        }
        if (isNoneShape) {
            // Clear the path shadow that was drawn before this method was called
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Draw fg/bg layers without clipping to any shape
            if (drawable.background != null) {
                drawable.background.draw(canvas)
            }
            if (drawable.foreground != null) {
                drawable.foreground.draw(canvas)
            }
            return
        }

        // The private drawIconBitmap() computes:
        //   offset = max(ceil(BLUR_FACTOR * size), round(size * (1 - scale) / 2))
        // BLUR_FACTOR (~0.035) creates a minimum offset of ~4px even at scale=1.0,
        // capping the effective icon size at ~92.6%. Compensate when the user's
        // iconSizeScale would produce a smaller offset than the floor allows.
        val bounds = drawable.bounds
        val currentSize = bounds.width()
        val size = mIconBitmapSize
        val actualOffset = (size - currentSize) / 2
        val desiredOffset = Math.round(size * (1 - iconSizeScale) / 2f)

        if (desiredOffset < actualOffset) {
            // BLUR_FACTOR floor is limiting us — redraw at the user's desired size.
            // Undo the excess offset so the icon fills the intended area.
            val compensation = actualOffset - desiredOffset
            val newSize = size - desiredOffset * 2

            canvas.save()
            canvas.translate(-compensation.toFloat(), -compensation.toFloat())
            drawable.setBounds(0, 0, newSize, newSize)
            val compensatedPath = getShapePath(drawable, drawable.bounds)

            canvas.clipPath(compensatedPath)
            canvas.drawColor(Color.TRANSPARENT)
            canvas.save()
            canvas.scale(iconScale, iconScale, canvas.width / 2f, canvas.height / 2f)
            drawable.background?.draw(canvas)
            drawable.foreground?.draw(canvas)
            canvas.restore()

            drawable.setBounds(bounds)
            canvas.restore()
            return
        }

        canvas.clipPath(overridePath)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.save()
        canvas.scale(iconScale, iconScale, canvas.width / 2f, canvas.height / 2f)
        if (drawable.background != null) {
            drawable.background.draw(canvas)
        }
        if (drawable.foreground != null) {
            drawable.foreground.draw(canvas)
        }
        canvas.restore()
    }

    override fun close() {
        recycle()
    }

    @AssistedFactory
    internal interface LauncherIconsFactory {
        fun create(pool: ConcurrentLinkedQueue<LauncherIcons>): LauncherIcons
    }

    @LauncherAppSingleton
    class IconPool @Inject internal constructor(private val factory: LauncherIconsFactory) {
        private var pool = ConcurrentLinkedQueue<LauncherIcons>()

        fun obtain(): LauncherIcons = pool.let { it.poll() ?: factory.create(it) }

        fun clear() {
            pool = ConcurrentLinkedQueue()
        }
    }

    companion object {

        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons = context.appComponent.iconPool.obtain()

        @JvmStatic fun clearPool(context: Context) = context.appComponent.iconPool.clear()
    }
}
