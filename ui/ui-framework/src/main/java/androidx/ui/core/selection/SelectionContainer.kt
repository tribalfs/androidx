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

package androidx.ui.core.selection

import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.remember
import androidx.compose.state
import androidx.ui.core.Alignment
import androidx.ui.core.ClipboardManagerAmbient
import androidx.ui.core.Constraints
import androidx.ui.core.HapticFeedBackAmbient
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.PassThroughLayout
import androidx.ui.core.Placeable
import androidx.ui.core.Popup
import androidx.ui.core.enforce
import androidx.ui.core.gesture.longPressDragGestureFilter
import androidx.ui.core.gesture.tapGestureFilter
import androidx.ui.core.gesture.dragGestureFilter
import androidx.ui.core.hasFixedHeight
import androidx.ui.core.hasFixedWidth
import androidx.ui.core.onPositioned
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxPosition
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.ipx
import androidx.ui.unit.isFinite
import androidx.ui.unit.max
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Default SelectionContainer to be used in order to make composables selectable by default.
 */
@Composable
internal fun SelectionContainer(children: @Composable() () -> Unit) {
    val selection = state<Selection?> { null }
    SelectionContainer(
        selection = selection.value,
        onSelectionChange = { selection.value = it },
        children = children
    )
}

/**
 * Selection Composable.
 *
 * The selection composable wraps composables and let them to be selectable. It paints the selection
 * area with start and end handles.
 */
@Composable
fun SelectionContainer(
    /** Current Selection status.*/
    selection: Selection?,
    /** A function containing customized behaviour when selection changes. */
    onSelectionChange: (Selection?) -> Unit,
    children: @Composable() () -> Unit
) {
    val registrarImpl = remember { SelectionRegistrarImpl() }
    val manager = remember { SelectionManager(registrarImpl) }

    manager.hapticFeedBack = HapticFeedBackAmbient.current
    manager.clipboardManager = ClipboardManagerAmbient.current
    manager.onSelectionChange = onSelectionChange
    manager.selection = selection

    val gestureModifiers =
        Modifier
            .tapGestureFilter({ manager.onRelease() })
            .longPressDragGestureFilter(manager.longPressDragObserver)

    Providers(SelectionRegistrarAmbient provides registrarImpl) {
        // Get the layout coordinates of the selection container. This is for hit test of
        // cross-composable selection.
        Wrap(gestureModifiers.onPositioned { manager.containerLayoutCoordinates = it }) {
            children()
            addHandles(
                manager = manager,
                selection = selection,
                startHandle = { StartSelectionHandle(selection = selection) },
                endHandle = { EndSelectionHandle(selection = selection) })
        }
    }
}

@Composable
private fun addHandles(
    manager: SelectionManager,
    selection: Selection?,
    startHandle: @Composable() () -> Unit,
    endHandle: @Composable() () -> Unit
) {
    if (selection == null) return
    selection.let {
        val startLayoutCoordinates = it.start.selectable.getLayoutCoordinates() ?: return
        val endLayoutCoordinates = it.end.selectable.getLayoutCoordinates() ?: return

        val startOffset = manager.containerLayoutCoordinates.childToLocal(
            startLayoutCoordinates,
            selection.start.selectable.getHandlePosition(
                selection = selection,
                isStartHandle = true
            )
        )
        val endOffset = manager.containerLayoutCoordinates.childToLocal(
            endLayoutCoordinates,
            selection.end.selectable.getHandlePosition(
                selection = selection,
                isStartHandle = false
            )
        )

        Wrap {
            Popup(
                alignment =
                if (isHandleLtrDirection(selection.start.direction, selection.handlesCrossed)) {
                    Alignment.TopEnd
                } else {
                    Alignment.TopStart
                },
                offset = IntPxPosition(startOffset.x.value.toIntPx(), startOffset.y.value.toIntPx())
            ) {
                val drag = Modifier.dragGestureFilter(
                    dragObserver = manager.handleDragObserver(isStartHandle = true)
                )
                // TODO(b/150706555): This layout is temporary and should be removed once Semantics
                //  is implemented with modifiers.
                @Suppress("DEPRECATION")
                PassThroughLayout(drag, startHandle)
            }
        }

        Wrap {
            Popup(
                alignment =
                if (isHandleLtrDirection(selection.end.direction, selection.handlesCrossed)) {
                    Alignment.TopStart
                } else {
                    Alignment.TopEnd
                },
                offset = IntPxPosition(endOffset.x.value.toIntPx(), endOffset.y.value.toIntPx())
            ) {
                val drag = Modifier.dragGestureFilter(
                    dragObserver = manager.handleDragObserver(isStartHandle = false)
                )
                // TODO(b/150706555): This layout is temporary and should be removed once Semantics
                //  is implemented with modifiers.
                @Suppress("DEPRECATION")
                PassThroughLayout(drag, endHandle)
            }
        }
    }
}

private fun Float.toIntPx(): IntPx = ceil(this).roundToInt().ipx

/**
 * Selection is transparent in terms of measurement and layout and passes the same constraints to
 * the children.
 */
@Composable
private fun Wrap(modifier: Modifier = Modifier, children: @Composable() () -> Unit) {
    Layout(modifier = modifier, children = children) { measurables, constraints, _ ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        val width = placeables.fold(0.ipx) { maxWidth, placeable ->
            max(maxWidth, (placeable.width))
        }

        val height = placeables.fold(0.ipx) { minWidth, placeable ->
            max(minWidth, (placeable.height))
        }

        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeAbsolute(0.ipx, 0.ipx)
            }
        }
    }
}

/**
 * A Container Box implementation used for selection children and handle layout
 */
@Composable
internal fun SimpleContainer(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    children: @Composable() () -> Unit
) {
    Layout(children, modifier) { measurables, incomingConstraints, _ ->
        val containerConstraints = Constraints()
            .copy(
                width?.toIntPx() ?: 0.ipx,
                width?.toIntPx() ?: IntPx.Infinity,
                height?.toIntPx() ?: 0.ipx,
                height?.toIntPx() ?: IntPx.Infinity
            )
            .enforce(incomingConstraints)
        val childConstraints = containerConstraints.copy(minWidth = 0.ipx, minHeight = 0.ipx)
        var placeable: Placeable? = null
        val containerWidth = if (
            containerConstraints.hasFixedWidth &&
            containerConstraints.maxWidth.isFinite()
        ) {
            containerConstraints.maxWidth
        } else {
            placeable = measurables.firstOrNull()?.measure(childConstraints)
            max((placeable?.width ?: 0.ipx), containerConstraints.minWidth)
        }
        val containerHeight = if (
            containerConstraints.hasFixedHeight &&
            containerConstraints.maxHeight.isFinite()
        ) {
            containerConstraints.maxHeight
        } else {
            if (placeable == null) {
                placeable = measurables.firstOrNull()?.measure(childConstraints)
            }
            max((placeable?.height ?: 0.ipx), containerConstraints.minHeight)
        }
        layout(containerWidth, containerHeight) {
            val p = placeable ?: measurables.firstOrNull()?.measure(childConstraints)
            p?.let {
                val position = Alignment.Center.align(
                    IntPxSize(
                        containerWidth - it.width,
                        containerHeight - it.height
                    )
                )
                it.place(
                    position.x,
                    position.y
                )
            }
        }
    }
}
