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
package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.DesktopOwner
import androidx.compose.ui.platform.DesktopOwnersAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round

/**
 * Opens a popup with the given content.
 *
 * The popup is positioned relative to its parent, using the [alignment] and [offset].
 * The popup is visible as long as it is part of the composition hierarchy.
 *
 * @sample androidx.compose.ui.samples.PopupSample
 *
 * @param alignment The alignment relative to the parent.
 * @param offset An offset from the original aligned position of the popup. Offset respects the
 * Ltr/Rtl context, thus in Ltr it will be added to the original aligned position and in Rtl it
 * will be subtracted from it.
 * @param isFocusable Indicates if the popup can grab the focus.
 * @param onDismissRequest Executes when the user clicks outside of the popup.
 * @param content The content to be displayed inside the popup.
 */
@Composable
fun Popup(
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset(0, 0),
    isFocusable: Boolean = false,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val popupPositioner = remember(alignment, offset) {
        AlignmentOffsetPositionProvider(
            alignment,
            offset
        )
    }

    Popup(
        popupPositionProvider = popupPositioner,
        isFocusable = isFocusable,
        onDismissRequest = onDismissRequest,
        content = content
    )
}

/**
 * Opens a popup with the given content.
 *
 * The popup is positioned using a custom [popupPositionProvider].
 *
 * @sample androidx.compose.ui.samples.PopupSample
 *
 * @param popupPositionProvider Provides the screen position of the popup.
 * @param isFocusable Indicates if the popup can grab the focus.
 * @param onDismissRequest Executes when the user clicks outside of the popup.
 * @param content The content to be displayed inside the popup.
 */
@Composable
fun Popup(
    popupPositionProvider: PopupPositionProvider,
    isFocusable: Boolean = false,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    PopupLayout(popupPositionProvider, isFocusable, onDismissRequest, content)
}

@Composable
private fun PopupLayout(
    popupPositionProvider: PopupPositionProvider,
    isFocusable: Boolean,
    onDismissRequest: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val owners = DesktopOwnersAmbient.current
    val density = LocalDensity.current

    val parentBounds = remember { mutableStateOf(IntRect.Zero) }
    val popupBounds = remember { mutableStateOf(IntRect.Zero) }

    // getting parent bounds
    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val coordinates = childCoordinates.parentCoordinates!!
            parentBounds.value = IntRect(
                coordinates.localToWindow(Offset.Zero).round(),
                coordinates.size
            )
        },
        measurePolicy = { _, _ ->
            layout(0, 0) {}
        }
    )

    val parentComposition = rememberCompositionContext()
    val (owner, composition) = remember {
        val owner = DesktopOwner(owners, density)
        val composition = owner.setContent(parent = parentComposition) {
            Layout(
                content = content,
                modifier = Modifier.pointerInput(isFocusable, onDismissRequest) {
                    detectDown(
                        onDown = { offset ->
                            if (isFocusable && isOutsideRectTap(popupBounds.value, offset)) {
                                onDismissRequest?.invoke()
                            }
                        }
                    )
                },
                measurePolicy = { measurables, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight

                    val windowSize = IntSize(
                        width = width,
                        height = height
                    )

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        measurables.forEach {
                            val placeable = it.measure(constraints)
                            val offset = popupPositionProvider.calculatePosition(
                                anchorBounds = parentBounds.value,
                                windowSize = windowSize,
                                layoutDirection = layoutDirection,
                                popupContentSize = IntSize(placeable.width, placeable.height)
                            )
                            popupBounds.value = IntRect(
                                offset,
                                IntSize(placeable.width, placeable.height)
                            )
                            placeable.place(offset.x, offset.y)
                        }
                    }
                }
            )
        }
        owner to composition
    }
    owner.density = density
    DisposableEffect(Unit) {
        onDispose {
            composition.dispose()
            owner.dispose()
        }
    }
}

private fun isOutsideRectTap(rect: IntRect, point: Offset): Boolean {
    return !rect.contains(IntOffset(point.x.toInt(), point.y.toInt()))
}

private suspend fun PointerInputScope.detectDown(onDown: (Offset) -> Unit) {
    while (true) {
        awaitPointerEventScope {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.find { it.changedToDown() }
            if (down != null) {
                onDown(down.position)
            }
        }
    }
}
