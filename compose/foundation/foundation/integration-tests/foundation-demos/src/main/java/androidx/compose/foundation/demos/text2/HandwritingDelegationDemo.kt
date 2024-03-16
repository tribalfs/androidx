/*
 * Copyright 2024 The Android Open Source Project
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

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)

package androidx.compose.foundation.demos.text2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.handwriting.handwritingDelegate
import androidx.compose.foundation.text.handwriting.handwritingDelegator
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun HandwritingDelegationDemo() {
    var openDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        Modifier
            .imePadding()
            .requiredWidth(300.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "This is not an actual text field, but it is a handwriting delegator so you can use " +
                "a stylus to write here."
        )
        Spacer(Modifier.size(16.dp))
        Text("Fake text field",
            Modifier
                .fillMaxWidth()
                .handwritingDelegator { openDialog = !openDialog }
                .padding(4.dp)
                .border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled),
                    RoundedCornerShape(4.dp)
                )
                .padding(16.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium))
    }

    if (openDialog) {
        Dialog(onDismissRequest = { openDialog = false }) {
            Card(
                modifier = Modifier.width(300.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("This text field is a handwriting delegate.")
                    Spacer(Modifier.size(16.dp))
                    OutlinedBasicTextField(
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .handwritingDelegate()
                    )
                }
            }

            val windowInfo = LocalWindowInfo.current
            LaunchedEffect(windowInfo) {
                snapshotFlow { windowInfo.isWindowFocused }.collect { isWindowFocused ->
                    if (isWindowFocused) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
    }
}
