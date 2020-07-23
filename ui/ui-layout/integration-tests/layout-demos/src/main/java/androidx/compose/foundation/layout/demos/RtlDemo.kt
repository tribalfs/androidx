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

package androidx.compose.foundation.layout.demos

import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.Layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.ui.core.Modifier
import androidx.ui.core.WithConstraints
import androidx.compose.foundation.Text
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ltr
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.rtl
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
@Composable
fun RtlDemo() {
    Column(verticalArrangement = Arrangement.SpaceEvenly) {
        Text("TEXT", Modifier.gravity(Alignment.CenterHorizontally))
        testText()
        Text("ROW", Modifier.gravity(Alignment.CenterHorizontally))
        testRow()
        Text("ROW WITH LTR MODIFIER", Modifier.gravity(Alignment.CenterHorizontally))
        testRow_modifier()
        Text("RELATIVE TO SIBLINGS", Modifier.gravity(Alignment.CenterHorizontally))
        testSiblings()
        Text(
            "PLACE WITH AUTO RTL SUPPORT IN CUSTOM LAYOUT",
            Modifier.gravity(Alignment.CenterHorizontally)
        )
        CustomLayout(true)
        Text(
            "PLACE WITHOUT RTL SUPPORT IN CUSTOM LAYOUT",
            Modifier.gravity(Alignment.CenterHorizontally)
        )
        CustomLayout(false)
        Text("WITH CONSTRAINTS", Modifier.gravity(Alignment.CenterHorizontally))
        LayoutWithConstraints(Modifier.ltr, "LD: LTR modifier")
        LayoutWithConstraints(Modifier.rtl, "LD: RTL modifier")
        LayoutWithConstraints(text = "LD: locale")
    }
}

private val boxSize = Modifier.preferredSize(50.dp, 30.dp)
private val size = Modifier.preferredSize(10.dp, 10.dp)

@Composable
private fun testRow() {
    Row {
        Stack(boxSize.background(color = Color.Red)) {}
        Stack(boxSize.background(color = Color.Green)) {}
        Row {
            Stack(boxSize.background(color = Color.Magenta)) {}
            Stack(boxSize.background(color = Color.Yellow)) {}
            Stack(boxSize.background(color = Color.Cyan)) {}
        }
        Stack(boxSize.background(color = Color.Blue)) {}
    }
}

@Composable
private fun testRow_modifier() {
    Row {
        Stack(boxSize.background(color = Color.Red)) {}
        Stack(boxSize.background(color = Color.Green)) {}
        Row(Modifier.ltr) {
            Stack(boxSize.background(color = Color.Magenta)) {}
            Stack(boxSize.background(color = Color.Yellow)) {}
            Stack(boxSize.background(color = Color.Cyan)) {}
        }
        Stack(boxSize.background(color = Color.Blue)) {}
    }
}

@Composable
private fun testText() {
    Column {
        Row {
            Stack(size.background(color = Color.Red)) {}
            Stack(size.background(color = Color.Green)) {}
            Stack(size.background(color = Color.Blue)) {}
        }
        Text("Text.")
        Text("Width filled text.", Modifier.fillMaxWidth())
        Text("שלום!")
        Text("שלום!", Modifier.fillMaxWidth())
        Text("-->")
        Text("-->", Modifier.fillMaxWidth())
    }
}

@Composable
private fun testSiblings() {
    Column {
        Stack(boxSize.background(color = Color.Red).alignWithSiblings { p -> p.width }
        ) {}
        Stack(boxSize.background(color = Color.Green).alignWithSiblings { p -> p.width / 2 }
        ) {}
        Stack(boxSize.background(color = Color.Blue).alignWithSiblings { p -> p.width / 4 }
        ) {}
    }
}

@Composable
private fun CustomLayout(rtlSupport: Boolean) {
    Layout(children = @Composable {
        Stack(boxSize.background(color = Color.Red)) {}
        Stack(boxSize.background(color = Color.Green)) {}
        Stack(boxSize.background(color = Color.Blue)) {}
    }) { measurables, constraints ->
        val p = measurables.map { e ->
            e.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val w = p.fold(0) { sum, e -> sum + e.width }
        val h = p.maxByOrNull { it.height }!!.height
        layout(w, h) {
            var xPosition = 0
            for (child in p) {
                child.placeAbsolute(IntOffset(xPosition, 0))
                if (rtlSupport) {
                    child.place(IntOffset(xPosition, 0))
                } else {
                    child.placeAbsolute(IntOffset(xPosition, 0))
                }
                xPosition += child.width
            }
        }
    }
}

@Composable
private fun LayoutWithConstraints(modifier: Modifier = Modifier, text: String) {
    WithConstraints(modifier) {
        val w = maxWidth / 3
        val h = maxHeight / 2
        val color = if (layoutDirection == LayoutDirection.Ltr) Color.Red else Color.Magenta
        Stack(Modifier.preferredSize(w, h).background(color = color)) {
            Text(text, Modifier.gravity(Alignment.Center))
        }
    }
}
