/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.getString
import androidx.compose.material3.tokens.DialogTokens
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * <a href="https://m3.material.io/components/dialogs/overview" class="external" target="_blank">Material Design basic dialog</a>.
 *
 * Dialogs provide important prompts in a user flow. They can require an action, communicate
 * information, or help users accomplish a task.
 *
 * ![Basic dialog image](https://developer.android.com/images/reference/androidx/compose/material3/basic-dialog.png)
 *
 * The dialog will position its buttons, typically [TextButton]s, based on the available space.
 * By default it will try to place them horizontally next to each other and fallback to horizontal
 * placement if not enough space is available.
 *
 * Simple usage:
 * @sample androidx.compose.material3.samples.AlertDialogSample
 *
 * Usage with a "Hero" icon:
 * @sample androidx.compose.material3.samples.AlertDialogWithIconSample
 *
 * @param onDismissRequest called when the user tries to dismiss the Dialog by clicking outside
 * or pressing the back button. This is not called when the dismiss button is clicked.
 * @param confirmButton button which is meant to confirm a proposed action, thus resolving what
 * triggered the dialog. The dialog does not set up any events for this button so they need to be
 * set up by the caller.
 * @param modifier the [Modifier] to be applied to this dialog
 * @param dismissButton button which is meant to dismiss the dialog. The dialog does not set up any
 * events for this button so they need to be set up by the caller.
 * @param icon optional icon that will appear above the [title] or above the [text], in case a
 * title was not provided.
 * @param title title which should specify the purpose of the dialog. The title is not mandatory,
 * because there may be sufficient information inside the [text].
 * @param text text which presents the details regarding the dialog's purpose.
 * @param shape defines the shape of this dialog's container
 * @param containerColor the color used for the background of this dialog. Use [Color.Transparent]
 * to have no color.
 * @param iconContentColor the content color used for the icon.
 * @param titleContentColor the content color used for the title.
 * @param textContentColor the content color used for the text.
 * @param tonalElevation when [containerColor] is [ColorScheme.surface], a translucent primary color
 * overlay is applied on top of the container. A higher tonal elevation value will result in a
 * darker color in light theme and lighter color in dark theme. See also: [Surface].
 * @param properties typically platform specific properties to further configure the dialog.
 * @see BasicAlertDialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = BasicAlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    properties = properties
) {
    AlertDialogContent(
        buttons = {
            AlertDialogFlowRow(
                mainAxisSpacing = ButtonsMainAxisSpacing,
                crossAxisSpacing = ButtonsCrossAxisSpacing
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        },
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        // Note that a button content color is provided here from the dialog's token, but in
        // most cases, TextButtons should be used for dismiss and confirm buttons.
        // TextButtons will not consume this provided content color value, and will used their
        // own defined or default colors.
        buttonContentColor = DialogTokens.ActionLabelTextColor.value,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
    )
}

/**
 * <a href="https://m3.material.io/components/dialogs/overview" class="external" target="_blank">Basic alert dialog dialog</a>.
 *
 * Dialogs provide important prompts in a user flow. They can require an action, communicate
 * information, or help users accomplish a task.
 *
 * ![Basic dialog image](https://developer.android.com/images/reference/androidx/compose/material3/basic-dialog.png)
 *
 * This basic alert dialog expects an arbitrary content that is defined by the caller. Note that
 * your content will need to define its own styling.
 *
 * By default, the displayed dialog has the minimum height and width that the Material Design spec
 * defines. If required, these constraints can be overwritten by providing a `width` or `height`
 * [Modifier]s.
 *
 * Basic alert dialog usage with custom content:
 * @sample androidx.compose.material3.samples.BasicAlertDialogSample
 *
 * @param onDismissRequest called when the user tries to dismiss the Dialog by clicking outside
 * or pressing the back button. This is not called when the dismiss button is clicked.
 * @param modifier the [Modifier] to be applied to this dialog's content.
 * @param properties typically platform specific properties to further configure the dialog.
 * @param content the content of the dialog
 */
@ExperimentalMaterial3Api
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val dialogPaneDescription = getString(Strings.Dialog)
        Box(
            modifier = modifier
                .sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth)
                .then(Modifier.semantics { paneTitle = dialogPaneDescription }),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

/**
 * <a href="https://m3.material.io/components/dialogs/overview" class="external" target="_blank">Basic alert dialog dialog</a>.
 *
 * Dialogs provide important prompts in a user flow. They can require an action, communicate
 * information, or help users accomplish a task.
 *
 * ![Basic dialog image](https://developer.android.com/images/reference/androidx/compose/material3/basic-dialog.png)
 *
 * This basic alert dialog expects an arbitrary content that is defined by the caller. Note that
 * your content will need to define its own styling.
 *
 * By default, the displayed dialog has the minimum height and width that the Material Design spec
 * defines. If required, these constraints can be overwritten by providing a `width` or `height`
 * [Modifier]s.
 *
 * Basic alert dialog usage with custom content:
 * @sample androidx.compose.material3.samples.BasicAlertDialogSample
 *
 * @param onDismissRequest called when the user tries to dismiss the Dialog by clicking outside
 * or pressing the back button. This is not called when the dismiss button is clicked.
 * @param modifier the [Modifier] to be applied to this dialog's content.
 * @param properties typically platform specific properties to further configure the dialog.
 * @param content the content of the dialog
 */
@Deprecated(
    "Use BasicAlertDialog instead",
    replaceWith = ReplaceWith(
        "BasicAlertDialog(onDismissRequest, modifier, properties, content)"
    )
)
@ExperimentalMaterial3Api
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) = BasicAlertDialog(onDismissRequest, modifier, properties, content)

/**
 * Contains default values used for [AlertDialog] and [BasicAlertDialog].
 */
object AlertDialogDefaults {
    /** The default shape for alert dialogs */
    val shape: Shape @Composable get() = DialogTokens.ContainerShape.value

    /** The default container color for alert dialogs */
    val containerColor: Color @Composable get() = DialogTokens.ContainerColor.value

    /** The default icon color for alert dialogs */
    val iconContentColor: Color @Composable get() = DialogTokens.IconColor.value

    /** The default title color for alert dialogs */
    val titleContentColor: Color @Composable get() = DialogTokens.HeadlineColor.value

    /** The default text color for alert dialogs */
    val textContentColor: Color @Composable get() = DialogTokens.SupportingTextColor.value

    /** The default tonal elevation for alert dialogs */
    val TonalElevation: Dp = 0.dp
}

private val ButtonsMainAxisSpacing = 8.dp
private val ButtonsCrossAxisSpacing = 12.dp
