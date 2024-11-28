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

package androidx.wear.compose.material3.macrobenchmark.common

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text

object WindowDialogBenchmark : MacrobenchmarkScreen {
    override val content: @Composable (BoxScope.() -> Unit)
        get() = {
            val showDialog = remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { showDialog.value = true },
                    modifier = Modifier.semantics { contentDescription = OPEN_DIALOG }
                ) {
                    Text("Open")
                }
            }
            if (showDialog.value) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showDialog.value = false },
                    properties = DialogProperties()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Button(
                            modifier =
                                Modifier.align(Alignment.Center).semantics {
                                    contentDescription = DIALOG_CONFIRM
                                },
                            onClick = { showDialog.value = false }
                        ) {
                            Text("Close dialog")
                        }
                    }
                }
            }
        }

    override val exercise: MacrobenchmarkScope.() -> Unit
        get() = {
            device.wait(Until.findObject(By.desc(OPEN_DIALOG)), FIND_OBJECT_TIMEOUT_MS).click()
            SystemClock.sleep(500)
            device.findObject(By.desc(DIALOG_CONFIRM)).click()
        }

    private const val OPEN_DIALOG = "OPEN_DIALOG"
    private const val DIALOG_CONFIRM = "DIALOG_CONFIRM"
}
