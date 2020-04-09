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

package androidx.ui.layout.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.fillMaxHeight
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredSizeIn
import androidx.ui.layout.wrapContentHeight
import androidx.ui.layout.wrapContentSize
import androidx.ui.unit.dp

@Sampled
@Composable
fun SimpleAlignedModifier() {
    // Here, the blue rectangle prefers to have a 20.dp size, subject to the incoming constraints.
    // Because of the LayoutSize.Min modifier, if LayoutAlign was not present, the blue rectangle
    // would actually be 40.dp x 40.dp to satisfy the min size set by the modifier. However,
    // because we also provide LayoutAlign, the blue rectangle is allowed to be smaller than the min
    // constraints, and it will be aligned in the 40.dp x 40.dp space. Note the example would not
    // work if LayoutAlign was specified before LayoutSize in the modifier chain.
    Box(
        Modifier.preferredSize(20.dp)
            .preferredSizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .wrapContentSize(Alignment.TopCenter),
        backgroundColor = Color.Blue
    )
}

@Sampled
@Composable
fun SimpleVerticallyAlignedModifier() {
    // Here, the blue rectangle prefers to have a 50.dp height, subject to the incoming constraints.
    // However, because we also wrapContentHeight, the blue rectangle is allowed to be smaller
    // than the min height, and it will be centered vertically in the available height.
    // The width of the rectangle will still fill the available width, because the
    // wrapContentHeight(Alignment.Center) modifier is only concerned with vertical alignment.
    // Note the example would not work if wrapContentHeight was specified before preferredSize
    // in the modifier chain.
    Box(
        Modifier.preferredSize(50.dp)
            .wrapContentHeight(Alignment.CenterVertically),
        backgroundColor = Color.Blue
    )
}

@Sampled
@Composable
fun SimpleGravityInRow() {
    Row(Modifier.fillMaxHeight()) {
        // The child with no gravity modifier is positioned by default so that its top edge is
        // aligned to the top of the vertical axis.
        Box(Modifier.preferredSize(80.dp, 40.dp), backgroundColor = Color.Magenta)
        // Gravity.Top, the child will be positioned so that its top edge is aligned to the top
        // of the vertical axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.Top),
            backgroundColor = Color.Red
        )
        // Gravity.Center, the child will be positioned so that its center is in the middle of
        // the vertical axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.CenterVertically),
            backgroundColor = Color.Yellow
        )
        // Gravity.Bottom, the child will be positioned so that its bottom edge is aligned to the
        // bottom of the vertical axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.Bottom),
            backgroundColor = Color.Green
        )
    }
}

@Sampled
@Composable
fun SimpleGravityInColumn() {
    Column(Modifier.fillMaxWidth()) {
        // The child with no gravity modifier is positioned by default so that its start edge
        // aligned with the start edge of the horizontal axis.
        Box(Modifier.preferredSize(80.dp, 40.dp), backgroundColor = Color.Magenta)
        // Gravity.Start, the child will be positioned so that its start edge is aligned with
        // the start edge of the horizontal axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.Start),
            backgroundColor = Color.Red
        )
        // Gravity.Center, the child will be positioned so that its center is in the middle of
        // the horizontal axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.CenterHorizontally),
            backgroundColor = Color.Yellow
        )
        // Gravity.End, the child will be positioned so that its end edge aligned to the end of
        // the horizontal axis.
        Box(
            Modifier.preferredSize(80.dp, 40.dp)
                .gravity(Alignment.End),
            backgroundColor = Color.Green
        )
    }
}