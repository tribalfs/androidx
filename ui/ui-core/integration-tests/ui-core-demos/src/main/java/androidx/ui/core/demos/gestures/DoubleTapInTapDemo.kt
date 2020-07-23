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

package androidx.ui.core.demos.gestures

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.core.gesture.doubleTapGestureFilter
import androidx.ui.core.gesture.tapGestureFilter
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.drawBorder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.ui.unit.dp

@Composable
fun DoubleTapInTapDemo() {
    val defaultColor = Grey

    val innerColor = state { defaultColor }
    val outerColor = state { defaultColor }

    val onTap: (Offset) -> Unit = {
        outerColor.value = outerColor.value.next()
    }

    val onDoubleTap: (Offset) -> Unit = { _ ->
        innerColor.value = innerColor.value.prev()
    }

    Column {
        Text(
            "Demonstrates interaction between DoubleTapGestureFilter and TapGestureFilter in an " +
                    "edge case that is nevertheless supported (normally regions will be separated" +
                    " by a pressIndicatorGestureFilter, but here they are not)."
        )
        Text(
            "Double tap the inner box to change the inner box color. Tap anywhere in the outer " +
                    "box once (including the inner box) to change the outer box background " +
                    "color. Tap rapidly with one or more fingers anywhere and the colors should" +
                    "change as one would expect."
        )
        Box(
            Modifier

                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .preferredSize(192.dp)
                .tapGestureFilter(onTap)
                .drawBorder(2.dp, BorderColor)
                .background(color = outerColor.value)

                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .preferredSize(96.dp)
                .doubleTapGestureFilter(onDoubleTap)
                .drawBorder(2.dp, BorderColor)
                .background(color = innerColor.value, shape = RectangleShape)
        )
    }
}