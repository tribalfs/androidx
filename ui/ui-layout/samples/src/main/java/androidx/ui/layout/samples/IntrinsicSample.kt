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
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.IntrinsicSize
import androidx.ui.layout.Row
import androidx.ui.layout.Stack
import androidx.ui.layout.aspectRatio
import androidx.ui.layout.fillMaxHeight
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredWidth
import androidx.ui.unit.dp

@Sampled
@Composable
fun SameWidthBoxes() {
    // Builds a layout containing three Box having the same width as the widest one.
    //
    // Here preferredWidth min intrinsic is adding a width premeasurement pass for the
    // Column, whose minimum intrinsic width will correspond to the preferred width of the largest
    // Box. Then preferredWidth min intrinsic will measure the Column with tight width, the
    // same as the premeasured minimum intrinsic width, which due to fillMaxWidth will force
    // the Box's to use the same width.
    Stack {
        Column(Modifier.preferredWidth(IntrinsicSize.Min).fillMaxHeight()) {
            Box(
                modifier = Modifier.fillMaxWidth().preferredSize(20.dp, 10.dp),
                backgroundColor = Color.Gray
            )
            Box(
                modifier = Modifier.fillMaxWidth().preferredSize(30.dp, 10.dp),
                backgroundColor = Color.Blue
            )
            Box(
                modifier = Modifier.fillMaxWidth().preferredSize(10.dp, 10.dp),
                backgroundColor = Color.Magenta
            )
        }
    }
}

@Sampled
@Composable
fun MatchParentDividerForText() {
    // Builds a layout containing two pieces of text separated by a divider, where the divider
    // is sized according to the height of the longest text.
    //
    // Here preferredHeight min intrinsic is adding a height premeasurement pass for the Row,
    // whose minimum intrinsic height will correspond to the height of the largest Text. Then
    // preferredHeight min intrinsic will measure the Row with tight height, the same as the
    // premeasured minimum intrinsic height, which due to fillMaxHeight will force the Texts and
    // the divider to use the same height.
    Stack {
        Row(Modifier.preferredHeight(IntrinsicSize.Min)) {
            Text(
                text = "This is a really short text",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Box(Modifier.preferredWidth(1.dp).fillMaxHeight(), backgroundColor = Color.Black)
            Text(
                text = "This is a much much much much much much much much much much" +
                        " much much much much much much longer text",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Sampled
@Composable
fun SameWidthTextBoxes() {
    // Builds a layout containing three Text boxes having the same width as the widest one.
    //
    // Here preferredWidth max intrinsic is adding a width premeasurement pass for the Column,
    // whose maximum intrinsic width will correspond to the preferred width of the largest
    // Box. Then preferredWidth max intrinsic will measure the Column with tight width, the
    // same as the premeasured maximum intrinsic width, which due to fillMaxWidth modifiers will
    // force the Boxs to use the same width.

    Stack {
        Column(Modifier.preferredWidth(IntrinsicSize.Max).fillMaxHeight()) {
            Box(Modifier.fillMaxWidth(), backgroundColor = Color.Gray) {
                Text("Short text")
            }
            Box(Modifier.fillMaxWidth(), backgroundColor = Color.Blue) {
                Text("Extremely long text giving the width of its siblings")
            }
            Box(Modifier.fillMaxWidth(), backgroundColor = Color.Magenta) {
                Text("Medium length text")
            }
        }
    }
}

@Sampled
@Composable
fun MatchParentDividerForAspectRatio() {
    // Builds a layout containing two aspectRatios separated by a divider, where the divider
    // is sized according to the height of the taller aspectRatio.
    //
    // Here preferredHeight max intrinsic is adding a height premeasurement pass for the
    // Row, whose maximum intrinsic height will correspond to the height of the taller
    // aspectRatio. Then preferredHeight max intrinsic will measure the Row with tight height,
    // the same as the premeasured maximum intrinsic height, which due to fillMaxHeight modifier
    // will force the aspectRatios and the divider to use the same height.
    //
    Stack {
        Row(Modifier.preferredHeight(IntrinsicSize.Max)) {
            val modifier = Modifier.fillMaxHeight().weight(1f)
            Box(modifier.aspectRatio(2f), backgroundColor = Color.Gray)
            Box(Modifier.preferredWidth(1.dp).fillMaxHeight(), backgroundColor = Color.Black)
            Box(modifier.aspectRatio(1f), backgroundColor = Color.Blue)
        }
    }
}
