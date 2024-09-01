/*
 * Copyright 2024 The Android Open Source Project
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
@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.appcompat.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.R
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

@RequiresApi(23)
class SeslIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    fun interface OnItemClickListener {
        fun onItemClick(view: View?, i: Int)
    }

    var defaultCircle: Drawable? = null
        set(value) {
            val it = indicator.iterator()
            while (it.hasNext()) {
                it.next().defaultCircle = value
            }
            field = value
        }

    var selectCircle: Drawable? = null
        set(value) {
            val it = indicator.iterator()
            while (it.hasNext()) {
                it.next().selectCircle = value
            }
            field = value
        }

    var selectedPosition: Int = -1
        set(value) {
            var i = value
            if (i < 0) {
                i = 0
            } else if (i >= indicator.size) {
                i = indicator.size - 1
            }
            field = i
            invalidateIndicator()
        }


    private var itemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(itemClickListener: OnItemClickListener) {
        this.itemClickListener = itemClickListener
        val it = indicator.iterator()
        while (it.hasNext()) {
            it.next().setOnClickListener { view: View? ->
                val list: List<PageIndicatorMarker> = indicator
                itemClickListener.onItemClick(this, list.indexOf(view))
            }
        }
    }

    private val indicator: MutableList<PageIndicatorMarker> = ArrayList()

    fun removeIndicator(i: Int) {
        if (i < 0 || i >= indicator.size) {
            return
        }
        removeView(indicator.removeAt(i))
        if (this.selectedPosition >= indicator.size) {
            selectedPosition -= 1
        } else {
            invalidateIndicator()
        }
    }

    val size: Int
        get() = indicator.size

    private fun generateDotIndicator(): PageIndicatorMarker {
        val context = context
        return PageIndicatorMarker(context).apply {
            this.defaultCircle = this@SeslIndicator.defaultCircle
            this.selectCircle = this@SeslIndicator.selectCircle
        }
    }

    private fun invalidateIndicator() {
        val size = indicator.size
        var i = 0
        while (i < size) {
            indicator[i].isActive = (i == this.selectedPosition)
            i++
        }
    }

    fun addIndicator() {
        val dotIndicator = generateDotIndicator().apply{
            this.setOnClickListener { view: View? ->
                itemClickListener?.onItemClick(view, indicator.indexOf(view))
            }
            this.accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo
                ) {
                    //var list: List<PageIndicatorMarker?>
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val infoCompat = AccessibilityNodeInfoCompat.wrap(info)
                    infoCompat.contentDescription = resources.getString(
                        R.string.sesl_appbar_suggest_pagination,
                        indicator.indexOf(this@apply) + 1,
                        size
                    )
                }
            }
        }
        indicator.add(dotIndicator)

        val lp = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            val sidePadding = context.resources.getDimensionPixelSize(R.dimen.sesl_viewpager_indicator_horizontal_padding)
            setMargins(sidePadding, 0, sidePadding, 0)
        }
        addView(dotIndicator, lp)

        if (this.selectedPosition == -1) {
            this.selectedPosition = 0
        }
    }


    init {
        this.defaultCircle = context.getDrawable(R.drawable.sesl_viewpager_indicator_off)?.mutate()
        this.selectCircle = context.getDrawable(R.drawable.sesl_viewpager_indicator_on)?.mutate()
        this.selectedPosition = -1
    }


    class PageIndicatorMarker @JvmOverloads constructor(
        context: Context,
        attributeSet: AttributeSet? = null
    ) : FrameLayout(context, attributeSet) {

        var isActive: Boolean = false
            set(value) {
                imageView.setImageDrawable(if (value) selectCircle else defaultCircle)
                isSelected = value
                field = value
            }

        var defaultCircle: Drawable? = null
            set(value) {
                field = value
                isActive = isActive
            }

        var selectCircle: Drawable? = null
            set(value) {
                field = value
                isActive = isActive
            }

        private val imageView: ImageView = ImageView(context)

        init {
            imageView.setImageDrawable(selectCircle)
            addView(imageView)
        }
    }
}