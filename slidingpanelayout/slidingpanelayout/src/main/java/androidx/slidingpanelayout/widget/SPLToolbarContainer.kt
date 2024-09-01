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
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import androidx.core.content.res.use
import androidx.slidingpanelayout.R

/**
 * **---------------------SESL-----------------------**
 *
 * A container layout that sets on top of the view tree z order
 */
class SPLToolbarContainer @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr) {
    private var mViewStub: ViewStub? = null

    init {
        getContext().obtainStyledAttributes(androidx.appcompat.R.styleable.AppCompatTheme).use {a ->
            if (!a.getBoolean(androidx.appcompat.R.styleable.AppCompatTheme_windowActionModeOverlay, false)) {
                LayoutInflater.from(context).inflate(R.layout.sesl_spl_action_mode_view_stub, this as ViewGroup, true)
                mViewStub = findViewById<View>(androidx.appcompat.R.id.action_mode_bar_stub) as ViewStub
            }
        }
        setWillNotDraw(false)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val viewStubCompat = mViewStub
        if (viewStubCompat != null) {
            viewStubCompat.bringToFront()
            mViewStub!!.invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewStubCompat = mViewStub
        if (viewStubCompat != null) {
            viewStubCompat.bringToFront()
            mViewStub!!.invalidate()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

}