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

package androidx.ui.material.studies.rally

import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.layout.Column
import androidx.ui.layout.EdgeInsets
import androidx.ui.layout.LayoutExpandedWidth
import androidx.ui.layout.LayoutPadding
import androidx.ui.material.AlertDialog
import androidx.ui.material.Button
import androidx.ui.material.Divider
import androidx.ui.material.MaterialTheme
import androidx.ui.material.TextButtonStyle

@Composable
fun RallyAlertDialog(
    onDismiss: () -> Unit,
    bodyText: String,
    buttonText: String
) {
    RallyDialogThemeOverlay {
        AlertDialog(
            onCloseRequest = onDismiss,
            text = { Text(bodyText) },
            buttons = {
                val style = TextButtonStyle(RectangleShape).copy(paddings = EdgeInsets(16.dp))
                Column {
                    Divider(
                        LayoutPadding(left = 12.dp, right = 12.dp),
                        color = MaterialTheme.colors().onSurface.copy(alpha = 0.2f)
                    )
                    Button(
                        text = buttonText,
                        onClick = onDismiss,
                        style = style,
                        modifier = LayoutExpandedWidth
                    )
                }
            }
        )
    }
}
