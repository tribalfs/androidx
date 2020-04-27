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

package androidx.ui.text.demos

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.FocusManagerAmbient
import androidx.ui.core.input.FocusNode
import androidx.ui.foundation.TextField
import androidx.ui.foundation.VerticalScroller
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.layout.Column
import androidx.ui.foundation.TextFieldValue
import androidx.ui.text.TextStyle
import androidx.ui.unit.sp

val FOCUS_NODES = List(6) { FocusNode() }

@Composable
fun TextFieldFocusTransition() {
    VerticalScroller {
        Column {
            TextFieldWithFocusId(0, 1)
            TextFieldWithFocusId(1, 2)
            TextFieldWithFocusId(2, 3)
            TextFieldWithFocusId(3, 4)
            TextFieldWithFocusId(4, 5)
            TextFieldWithFocusId(5, 0)
        }
    }
}

@Composable
private fun TextFieldWithFocusId(focusID: Int, nextFocus: Int) {
    val focusManager = FocusManagerAmbient.current
    val state = state { TextFieldValue("Focus ID: $focusID") }
    val focused = state { false }
    val color = if (focused.value) {
        Color.Red
    } else {
        Color.Black
    }
    TextField(
        value = state.value,
        textColor = color,
        textStyle = TextStyle(fontSize = 32.sp),
        onValueChange = {
            state.value = it
        },
        onFocusChange = { focused.value = it },
        imeAction = ImeAction.Next,
        focusNode = FOCUS_NODES[focusID],
        onImeActionPerformed = {
            if (it == ImeAction.Next)
                focusManager.requestFocus(FOCUS_NODES[nextFocus])
        }
    )
}
