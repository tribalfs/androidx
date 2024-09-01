/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.slidingpanelayout.widget

import androidx.appcompat.R as appCompatR
import androidx.core.content.res.ResourcesCompat as ResComp
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.util.TypedValue.TYPE_FIRST_COLOR_INT
import android.util.TypedValue.TYPE_LAST_COLOR_INT
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.RestrictTo
import androidx.appcompat.util.SeslMisc
import androidx.appcompat.util.SeslRoundedCorner
import kotlin.math.roundToInt

/**
 * **---------------------SESL-----------------------**
 *
 */
internal class SlidingPaneRoundedCorner (private val mContext: Context) {
    private var mEndBottomDrawable: Drawable? = null
    private var mEndTopDrawable: Drawable? = null
    private val mRes: Resources = mContext.resources
    private var mRoundedCornerMode: Int = MODE_START
    private var mStartBottomDrawable: Drawable? = null

    @ColorInt
    private var mStartBottomDrawableColor = 0xffffff //white
    private var mStartTopDrawable: Drawable? = null

    @ColorInt
    private var mStartTopDrawableColor = 0xffffff

    @Px
    private var mRoundRadius = -1

    private val mRoundedCornerBounds = Rect()
    private var mMarginTop = 0
    private var mMarginBottom = 0
    private val mTmpRect = Rect()

    init {
        initRoundedCorner()
    }

    private fun initRoundedCorner() {
        val roundRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            RADIUS,
            mRes.displayMetrics
        ).toInt()

        val typedValue = TypedValue()
        mContext.theme.resolveAttribute(
            appCompatR.attr.roundedCornerColor,
            typedValue, true
        )

        val roundColor = if (typedValue.resourceId > 0 && isColorType(typedValue.type)) {
            ResComp.getColor(mRes, typedValue.resourceId, mContext.theme)
        } else if (typedValue.data > 0 && isColorType(typedValue.type)) {
            typedValue.data
        } else {
            if (SeslMisc.isLightTheme(mContext)) {
                ResComp.getColor(mRes, appCompatR.color.sesl_round_and_bgcolor_light , null)
            } else {
                ResComp.getColor(mRes, appCompatR.color.sesl_round_and_bgcolor_dark, null)
            }
        }

        val paint = Paint().apply {
            style = Paint.Style.FILL
            this.color = roundColor
        }

        mStartTopDrawable = SeslRoundedCorner.SeslRoundedChunkingDrawable(roundRadius, paint, 90.0f)
        mStartBottomDrawable = SeslRoundedCorner.SeslRoundedChunkingDrawable(roundRadius, paint, 180.0f)
        mEndTopDrawable = SeslRoundedCorner.SeslRoundedChunkingDrawable(roundRadius, paint, 0.0f)//Note: incorrectly set to 180f in vanilla sesl
        mEndBottomDrawable = SeslRoundedCorner.SeslRoundedChunkingDrawable(roundRadius, paint, 270.0f)

        mStartBottomDrawableColor = roundColor
        mStartTopDrawableColor = roundColor
    }

    private fun isColorType(i: Int): Boolean {
        return i in TYPE_FIRST_COLOR_INT..TYPE_LAST_COLOR_INT
    }

    private fun isLayoutRtlSupport(view: View): Boolean {
        return view.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    internal fun removeRoundedCorner(layoutDirection: Int) {
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            mStartTopDrawable = null
            mStartBottomDrawable = null
        }else {
            mEndTopDrawable = null
            mEndBottomDrawable = null
        }
    }

    internal fun drawRoundedCorner(canvas: Canvas) {
        canvas.getClipBounds(mRoundedCornerBounds)
        drawRoundedCornerInternal(canvas)
    }

    internal fun drawRoundedCorner(view: View, canvas: Canvas) {
        val left: Int
        val top: Int
        mRoundedCornerMode = if (isLayoutRtlSupport(view)) {
            MODE_END
        } else {
            MODE_START
        }
        if (view.translationY != 0.0f) {
            left = view.x.roundToInt()
            top = view.y.roundToInt()
        } else {
            left = view.left
            top = view.top
        }
        val finalTop = mMarginTop + top
        val width = view.width + left + mRoundRadius
        val height = top + view.height - mMarginBottom
        canvas.getClipBounds(mTmpRect)
        val rect = mTmpRect
        rect.right = rect.left.coerceAtLeast(view.right + mRoundRadius)
        canvas.clipRect(mTmpRect)
        mRoundedCornerBounds[left, finalTop, width] = height
        drawRoundedCornerInternal(canvas)
    }

    private fun drawRoundedCornerInternal(canvas: Canvas) {
        val rect = mRoundedCornerBounds
        val l = rect.left
        val r = rect.right
        val t = rect.top
        val b = rect.bottom

        if (mRoundedCornerMode == MODE_START) {
            mStartTopDrawable!!.apply {
                setBounds(l - mRoundRadius, t, l, mRoundRadius + t)
                draw(canvas)
            }
            mStartBottomDrawable!!.apply {
                setBounds(l - mRoundRadius, b - mRoundRadius, l, b)
                draw(canvas)
            }
        }else{
            mEndTopDrawable!!.apply {
                setBounds(r - mRoundRadius, t, r, mRoundRadius + t)
                draw(canvas)
            }
            mEndBottomDrawable!!.apply {
                setBounds(r - mRoundRadius, b - mRoundRadius, r, b)
                draw(canvas)
            }
        }
    }

    var roundedCorners: Int
        get() = mRoundedCornerMode
        set(roundedCornerMode) {
            mRoundedCornerMode = roundedCornerMode
            ensureInited()
        }

    fun setRoundedCornerColor(@ColorInt color: Int) {
        ensureInited()
        mStartTopDrawableColor = color
        mStartBottomDrawableColor = color
        PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).let {
            mStartTopDrawable!!.colorFilter = it
            mEndTopDrawable!!.colorFilter = it
            mEndBottomDrawable!!.colorFilter = it
            mStartBottomDrawable!!.colorFilter = it
        }
    }

    private inline fun ensureInited(){
        if (mStartTopDrawable == null || mStartBottomDrawable == null || mEndTopDrawable == null || mEndBottomDrawable == null) {
            initRoundedCorner()
        }
    }

    fun setMarginBottom(bottomMargin: Int) {
        mMarginBottom = bottomMargin
    }

    fun setMarginTop(topMargin: Int) {
        mMarginTop = topMargin
    }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    var roundedCornerRadius: Int
        get() = mRoundRadius
        set(@Px radius) {
            mRoundRadius = radius
        }

    companion object {
        private const val RADIUS = 16f
        const val MODE_START = 0
        const val MODE_END = 1
        const val TAG = "SeslPaneRoundedCorner"
    }
}