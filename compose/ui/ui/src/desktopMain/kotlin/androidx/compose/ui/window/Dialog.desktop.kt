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

package androidx.compose.ui.window

import androidx.compose.desktop.ComposeDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.ComponentUpdater
import androidx.compose.ui.util.setPositionSafely
import androidx.compose.ui.util.setSizeSafely
import androidx.compose.ui.util.setUndecoratedSafely
import java.awt.Dialog.ModalityType
import java.awt.Image
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog

/**
 * Composes platform dialog in the current composition. When Dialog enters the composition,
 * a new platform dialog will be created and receives the focus. When Dialog leaves the
 * composition, dialog will be disposed and closed.
 *
 * Dialog is a modal window. It means it blocks the parent [Window] / [Dialog] in which composition
 * context it was created.
 *
 * Usage:
 * ```
 * @Composable
 * fun main() = application {
 *     val isDialogOpen by remember { mutableStateOf(true) }
 *     if (isDialogOpen) {
 *         Dialog(onCloseRequest = { isDialogOpen = false })
 *     }
 * }
 * ```
 * @param onCloseRequest Callback that will be called when the user closes the dialog.
 * Usually in this callback we need to manually tell Compose what to do:
 * - change `isOpen` state of the dialog (which is manually defined)
 * - close the whole application (`onCloseRequest = ::exitApplication` in [ApplicationScope])
 * - don't close the dialog on close request (`onCloseRequest = {}`)
 * @param state The state object to be used to control or observe the dialog's state
 * When size/position is changed by the user, state will be updated.
 * When size/position of the dialog is changed by the application (changing state),
 * the native dialog will update its corresponding properties.
 * If [DialogState.position] is not [WindowPosition.isSpecified], then after the first show on the
 * screen [DialogState.position] will be set to the absolute values.
 * @param visible Is [Dialog] visible to user.
 * If `false`:
 * - internal state of [Dialog] is preserved and will be restored next time the dialog
 * will be visible;
 * - native resources will not be released. They will be released only when [Dialog]
 * will leave the composition.
 * @param title Title in the titlebar of the dialog
 * @param icon Icon in the titlebar of the dialog (for platforms which support this)
 * @param resizable Can dialog be resized by the user (application still can resize the dialog
 * changing [state])
 * @param enabled Can dialog react to input events
 * @param focusable Can dialog receive focus
 * @param content content of the dialog
 *
 * This API is experimental and will eventually replace [androidx.compose.ui.window.v1.Dialog]
 */
@ExperimentalComposeUiApi
@Composable
fun OwnerWindowScope.Dialog(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Untitled",
    // TODO(demin): can we replace this by icon: Painter? What to do with different densities?
    icon: Image? = null,
    undecorated: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    content: @Composable DialogScope.() -> Unit
) {
    val owner = this.ownerWindow

    val currentState by rememberUpdatedState(state)
    val currentTitle by rememberUpdatedState(title)
    val currentIcon by rememberUpdatedState(icon)
    val currentUndecorated by rememberUpdatedState(undecorated)
    val currentResizable by rememberUpdatedState(resizable)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentFocusable by rememberUpdatedState(focusable)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)

    val updater = remember(::ComponentUpdater)

    Dialog(
        visible = visible,
        create = {
            ComposeDialog(owner, ModalityType.DOCUMENT_MODAL).apply {
                // close state is controlled by DialogState.isOpen
                defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent?) {
                        currentOnCloseRequest()
                    }
                })
                addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        currentState.size = WindowSize(width.dp, height.dp)
                    }

                    override fun componentMoved(e: ComponentEvent) {
                        currentState.position = WindowPosition(x.dp, y.dp)
                    }
                })
            }
        },
        dispose = ComposeDialog::dispose,
        update = { dialog ->
            updater.update {
                set(currentTitle, dialog::setTitle)
                set(currentIcon, dialog::setIconImage)
                set(currentUndecorated, dialog::setUndecoratedSafely)
                set(currentResizable, dialog::setResizable)
                set(currentEnabled, dialog::setEnabled)
                set(currentFocusable, dialog::setFocusable)
                set(state.size, dialog::setSizeSafely)
                set(state.position, dialog::setPositionSafely)
            }
        },
        content = content
    )
}

// TODO(demin): fix mouse hover after opening a dialog.
//  When we open a modal dialog, ComposeLayer/mouseExited will
//  never be called for the parent window. See ./gradlew run3
/**
 * Compose [ComposeDialog] obtained from [create]. The [create] block will be called
 * exactly once to obtain the [ComposeDialog] to be composed, and it is also guaranteed to
 * be invoked on the UI thread (Event Dispatch Thread).
 *
 * Once Dialog leaves the composition, [dispose] will be called to free resources that
 * obtained by the [ComposeDialog].
 *
 * Dialog is a modal window. It means it blocks the parent [Window] / [Dialog] in which composition
 * context it was created.
 *
 * The [update] block can be run multiple times (on the UI thread as well) due to recomposition,
 * and it is the right place to set [ComposeDialog] properties depending on state.
 * When state changes, the block will be reexecuted to set the new properties.
 * Note the block will also be ran once right after the [create] block completes.
 *
 * Dialog is needed for creating dialog's that still can't be created with
 * the default Compose function [androidx.compose.ui.window.Dialog]
 *
 * This API is experimental and will eventually replace [androidx.compose.ui.window.v1.Dialog].
 *
 * @param visible Is [ComposeDialog] visible to user.
 * If `false`:
 * - internal state of [ComposeDialog] is preserved and will be restored next time the dialog
 * will be visible;
 * - native resources will not be released. They will be released only when [Dialog]
 * will leave the composition.
 * @param create The block creating the [ComposeDialog] to be composed.
 * @param dispose The block to dispose [ComposeDialog] and free native resources.
 * Usually it is simple `ComposeDialog::dispose`
 * @param update The callback to be invoked after the layout is inflated.
 * @param content Composable content of the creating dialog.
 */
@Suppress("unused")
@ExperimentalComposeUiApi
@Composable
fun OwnerWindowScope.Dialog(
    visible: Boolean = true,
    create: () -> ComposeDialog,
    dispose: (ComposeDialog) -> Unit,
    update: (ComposeDialog) -> Unit = {},
    content: @Composable DialogScope.() -> Unit
) {
    val composition = rememberCompositionContext()
    AwtWindow(
        visible = visible,
        create = {
            create().apply {
                val scope = object : DialogScope {
                    override val dialog: ComposeDialog get() = this@apply
                }
                setContent(composition) {
                    scope.content()
                }
            }
        },
        dispose = dispose,
        update = update
    )
}

/**
 * Receiver scope which is used by [androidx.compose.ui.window.Dialog].
 */
interface DialogScope : OwnerWindowScope {
    /**
     * [ComposeDialog] that was created inside [androidx.compose.ui.window.Dialog].
     */
    val dialog: ComposeDialog

    override val ownerWindow: Window get() = dialog
}