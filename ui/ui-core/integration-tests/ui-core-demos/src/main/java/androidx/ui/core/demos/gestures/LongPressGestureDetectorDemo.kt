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
import androidx.ui.core.gesture.longPressGestureFilter
import androidx.compose.foundation.Border
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.ui.geometry.Offset
import androidx.ui.unit.dp

/**
 * Simple [longPressGestureFilter] demo.
 */
@Composable
fun LongPressGestureDetectorDemo() {
    val color = state { Colors.random() }

    val onLongPress = { _: Offset ->
        color.value = color.value.anotherRandomColor()
    }

    Column {
        Text("The box changes color when you long press on it.")
        Box(
            Modifier.fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .preferredSize(192.dp)
                .longPressGestureFilter(onLongPress),
            backgroundColor = color.value,
            border = Border(2.dp, BorderColor)
        )
    }
}
