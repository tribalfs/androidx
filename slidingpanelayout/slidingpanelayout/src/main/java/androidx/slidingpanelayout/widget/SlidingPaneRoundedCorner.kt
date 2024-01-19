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

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.appcompat.util.SeslMisc
import androidx.core.content.res.ResourcesCompat as ResComp
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
    var roundedCornerRadius = -1

    private val mRoundedCornerBounds = Rect()
    private var mMarginTop = 0
    private var mMarginBottom = 0
    private val mTmpRect = Rect()

    init {
        initRoundedCorner()
    }

    private fun initRoundedCorner() {
        roundedCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, RADIUS, mRes.displayMetrics)
                .toInt()
        val theme = mContext.theme
        mStartTopDrawable = ResComp.getDrawable(mRes, androidx.appcompat.R.drawable.sesl_top_right_round, theme)
        mStartBottomDrawable = ResComp.getDrawable(mRes, androidx.appcompat.R.drawable.sesl_bottom_right_round, theme)
        mEndTopDrawable = ResComp.getDrawable(mRes, androidx.appcompat.R.drawable.sesl_top_left_round, theme)
        mEndBottomDrawable = ResComp.getDrawable(mRes, androidx.appcompat.R.drawable.sesl_bottom_left_round, theme)
        val color = if (SeslMisc.isLightTheme(mContext)) {
            ResComp.getColor(mRes, androidx.appcompat.R.color.sesl_round_and_bgcolor_light, null)
        }else {
            ResComp.getColor(mRes, androidx.appcompat.R.color.sesl_round_and_bgcolor_dark, null)
        }
        mStartBottomDrawableColor = color
        mStartTopDrawableColor = color
    }

    private fun isLayoutRtlSupport(view: View): Boolean {
        return view.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    internal fun removeRoundedCorner(layoutDirection: Int) {
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            mStartTopDrawable = null
            mStartBottomDrawable = null
            return
        }
        mEndTopDrawable = null
        mEndBottomDrawable = null
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
        val width = view.width + left + roundedCornerRadius
        val height = top + view.height - mMarginBottom
        canvas.getClipBounds(mTmpRect)
        val rect = mTmpRect
        rect.right = rect.left.coerceAtLeast(view.right + roundedCornerRadius)
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
            mStartTopDrawable!!.setBounds(l - roundedCornerRadius, t, l, roundedCornerRadius + t)
            mStartTopDrawable!!.draw(canvas)
            mStartBottomDrawable!!.setBounds(l - roundedCornerRadius, b - roundedCornerRadius, l, b)
            mStartBottomDrawable!!.draw(canvas)
            return
        }

        mEndTopDrawable!!.setBounds(r - roundedCornerRadius, t, r, roundedCornerRadius + t)
        mEndTopDrawable!!.draw(canvas)
        mEndBottomDrawable!!.setBounds(r - roundedCornerRadius, b - roundedCornerRadius, r, b)
        mEndBottomDrawable!!.draw(canvas)
    }

    var roundedCorners: Int
        get() = mRoundedCornerMode
        set(roundedCornerMode) {
            mRoundedCornerMode = roundedCornerMode
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

    fun setRoundedCornerColor(@ColorInt color: Int) {
        if (mStartTopDrawable == null || mStartBottomDrawable == null || mEndTopDrawable == null || mEndBottomDrawable == null) {
            initRoundedCorner()
        }
        val porterDuffColorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        mStartTopDrawableColor = color
        mStartTopDrawable!!.colorFilter = porterDuffColorFilter
        mEndTopDrawable!!.colorFilter = porterDuffColorFilter
        mEndBottomDrawable!!.colorFilter = porterDuffColorFilter
        mStartBottomDrawableColor = color
        mStartBottomDrawable!!.colorFilter = porterDuffColorFilter
    }

    companion object {
        private const val RADIUS = 16f
        const val MODE_START = 0
        const val MODE_END = 1
        const val TAG = "SeslPaneRoundedCorner"
    }
}