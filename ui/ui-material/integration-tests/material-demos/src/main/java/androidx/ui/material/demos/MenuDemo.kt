/*
 * Copyright 2020 The Android expanded Source Project
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

package androidx.ui.material.demos

import androidx.compose.Composable
import androidx.compose.getValue
import androidx.compose.setValue
import androidx.compose.state
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.compose.foundation.Icon
import androidx.compose.foundation.Text
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.wrapContentSize
import androidx.ui.core.LayoutDirection
import androidx.ui.layout.Stack
import androidx.ui.layout.ltr
import androidx.ui.material.DropdownMenu
import androidx.ui.material.DropdownMenuItem
import androidx.ui.material.IconButton
import androidx.ui.material.icons.Icons
import androidx.ui.material.icons.filled.MoreVert
import androidx.ui.unit.IntOffset
import androidx.ui.unit.IntSize
import androidx.ui.unit.Position
import androidx.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun MenuDemo() {
    Stack(Modifier.ltr) {
        for (i in 0..10) {
            for (j in 0..10) {
                MenuInstance(
                    Modifier.fillMaxSize().wrapContentSize(
                        object : Alignment {
                            override fun align(
                                size: IntSize,
                                layoutDirection: LayoutDirection
                            ) = IntOffset(
                                (size.width * i / 10f).roundToInt(),
                                (size.height * j / 10f).roundToInt()
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun MenuInstance(modifier: Modifier = Modifier) {
    val options = listOf(
        "Refresh",
        "Settings",
        "Send Feedback",
        "Help",
        "Signout"
    )

    var expanded by state { false }

    val iconButton = @Composable {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert)
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        toggle = iconButton,
        dropdownOffset = Position(-12.dp, -12.dp),
        toggleModifier = modifier
    ) {
        options.forEach {
            DropdownMenuItem(onClick = {}) {
                Text(it)
            }
        }
    }
}