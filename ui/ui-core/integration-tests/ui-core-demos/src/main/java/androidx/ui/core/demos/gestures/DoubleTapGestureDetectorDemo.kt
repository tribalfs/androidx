/*
 * Copyright 2019 The Android Open Source Project
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
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.foundation.drawBackground
import androidx.ui.foundation.drawBorder
import androidx.ui.layout.Column
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.preferredSize
import androidx.ui.layout.wrapContentSize
import androidx.ui.geometry.Offset
import androidx.ui.unit.dp

/**
 * Simple [doubleTapGestureFilter] demo.
 */
@Composable
fun DoubleTapGestureFilterDemo() {
    val color = state { Colors.random() }

    val onDoubleTap: (Offset) -> Unit = {
        color.value = color.value.anotherRandomColor()
    }

    Column {
        Text("The box changes color when you double tap it.")
        Box(
            Modifier.fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .preferredSize(192.dp)
                .doubleTapGestureFilter(onDoubleTap)
                .drawBorder(2.dp, BorderColor)
                .drawBackground(color.value)
        )
    }
}
