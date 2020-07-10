/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.ui.core.demos.viewinterop

import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.state
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.Ref
import androidx.ui.core.demos.R
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.foundation.drawBackground
import androidx.ui.graphics.Color
import androidx.ui.graphics.toArgb
import androidx.ui.layout.Column
import androidx.ui.layout.size
import androidx.ui.material.Button
import androidx.ui.unit.dp
import androidx.ui.viewinterop.AndroidView
import androidx.ui.viewinterop.emitView

@Composable
fun ViewInteropDemo() {
    Column {
        // This is a collection of multiple ways to include Android Views in Compose UI hierarchies
        // and Compose in Android ViewGroups. Note that these APIs are subject to change.

        // Include Android View.
        emitView(::TextView) {
            it.text = "This is a text in a TextView"
        }
        val ref = Ref<View>()
        emitView(::FrameLayout, { it.setRef(ref) }) {
            emitView(::TextView) {
                it.text = "This is a very long very long text"
            }
        }
        Text("This is a second text")
        ref.value!!.layoutParams = ViewGroup.LayoutParams(100, WRAP_CONTENT)

        // Include an Android ViewGroup and add Compose to it.
        emitView(::LinearLayout, { it.orientation = LinearLayout.VERTICAL }) {
            Box(Modifier.size(50.dp).drawBackground(Color.Blue))
            Box(Modifier.size(50.dp).drawBackground(Color.Gray))
        }

        // Inflate AndroidView from XML.
        AndroidView(R.layout.test_layout)

        // Inflate AndroidView from XML with style set.
        val context = ContextAmbient.current
        Providers(
            ContextAmbient provides ContextThemeWrapper(context, R.style.TestLayoutStyle)
        ) {
            AndroidView(R.layout.test_layout)
        }

        // Compose custom Android View and do remeasurements and invalidates.
        val squareRef = Ref<ColoredSquareView>()
        emitView(::FrameLayout, {}) {
            emitView(::ColoredSquareView) {
                it.size = 200
                it.color = Color.Cyan
                it.setRef(squareRef)
            }
        }
        Button(onClick = { squareRef.value!!.size += 50 }) {
            Text("Increase size of Android view")
        }
        val colorIndex = state { 0 }
        Button(onClick = {
            colorIndex.value = (colorIndex.value + 1) % 4
            squareRef.value!!.color = arrayOf(
                Color.Blue, Color.LightGray, Color.Yellow, Color.Cyan
            )[colorIndex.value]
        }) {
            Text("Change color of Android view")
        }

        // Inflate AndroidView from XML and change it in callback post inflation.
        AndroidView(R.layout.test_layout) { view ->
            view as RelativeLayout
            view.setVerticalGravity(Gravity.BOTTOM)
            view.layoutParams.width = 700
            view.setBackgroundColor(android.graphics.Color.BLUE)
        }
    }
}

private class ColoredSquareView(context: Context) : View(context) {
    var size: Int = 100
        set(value) {
            if (value != field) {
                field = value
                requestLayout()
            }
        }

    var color: Color = Color.Blue
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(size, size)
    }
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(color.toArgb())
    }
}
