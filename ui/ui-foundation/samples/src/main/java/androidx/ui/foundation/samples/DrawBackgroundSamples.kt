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

package androidx.ui.foundation.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.foundation.drawBackground
import androidx.ui.foundation.shape.corner.CutCornerShape
import androidx.ui.graphics.Color
import androidx.ui.graphics.HorizontalGradient
import androidx.ui.graphics.RectangleShape
import androidx.ui.layout.padding
import androidx.ui.unit.dp
import androidx.ui.unit.px

@Composable
@Sampled
fun DrawBackgroundColor() {
    Text(
        "Text with background",
        Modifier.drawBackground(Color.Magenta, RectangleShape).padding(10.dp)
    )
}

@Composable
@Sampled
fun DrawBackgroundShapedBrush() {
    val gradientBrush = HorizontalGradient(
        colors = listOf(Color.Red, Color.Blue, Color.Green),
        startX = 0.px,
        endX = 500.px
    )
    Text(
        "Text with gradient back",
        Modifier.drawBackground(brush = gradientBrush, shape = CutCornerShape(8.dp))
            .padding(10.dp)
    )
}