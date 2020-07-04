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

package androidx.ui.material

import androidx.animation.SpringSpec
import androidx.compose.Composable
import androidx.compose.State
import androidx.compose.state
import androidx.ui.core.DensityAmbient
import androidx.ui.core.LayoutDirection
import androidx.ui.core.Modifier
import androidx.ui.core.WithConstraints
import androidx.ui.core.gesture.tapGestureFilter
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.graphics.Shape
import androidx.ui.layout.DpConstraints
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.offsetPx
import androidx.ui.layout.padding
import androidx.ui.layout.preferredSizeIn
import androidx.ui.material.internal.stateDraggable
import androidx.ui.unit.Dp
import androidx.ui.unit.dp
import androidx.ui.util.lerp

/**
 * Possible states of the drawer
 */
enum class DrawerState {
    /**
     * Constant to indicate the state of the drawer when it's closed
     */
    Closed,
    /**
     * Constant to indicate the state of the drawer when it's opened
     */
    Opened,
    // Expanded
}

/**
 * Navigation drawers provide access to destinations in your app.
 *
 * Modal navigation drawers block interaction with the rest of an app’s content with a scrim.
 * They are elevated above most of the app’s UI and don’t affect the screen’s layout grid.
 *
 * See [BottomDrawerLayout] for a layout that introduces a bottom drawer, suitable when
 * using bottom navigation.
 *
 * @sample androidx.ui.material.samples.ModalDrawerSample
 *
 * @param drawerState state of the drawer
 * @param onStateChange lambda to be invoked when the drawer requests to change its state,
 * e.g. when the drawer is being swiped to the new state or when the scrim is clicked
 * @param gesturesEnabled whether or not drawer can be interacted by gestures
 * @param drawerShape shape of the drawer sheet
 * @param drawerElevation drawer sheet elevation. This controls the size of the shadow below the
 * drawer sheet
 * @param drawerContent composable that represents content inside the drawer
 * @param bodyContent content of the rest of the UI
 *
 * @throws IllegalStateException when parent has [Float.POSITIVE_INFINITY] width
 */
@Composable
fun ModalDrawerLayout(
    drawerState: DrawerState,
    onStateChange: (DrawerState) -> Unit,
    gesturesEnabled: Boolean = true,
    drawerShape: Shape = MaterialTheme.shapes.large,
    drawerElevation: Dp = DrawerConstants.DefaultElevation,
    drawerContent: @Composable () -> Unit,
    bodyContent: @Composable () -> Unit
) {
    WithConstraints(Modifier.fillMaxSize()) {
        // TODO : think about Infinite max bounds case
        if (!constraints.hasBoundedWidth) {
            throw IllegalStateException("Drawer shouldn't have infinite width")
        }
        val dpConstraints = with(DensityAmbient.current) {
            DpConstraints(constraints)
        }
        val minValue = -constraints.maxWidth.toFloat()
        val maxValue = 0f

        val anchors = listOf(minValue to DrawerState.Closed, maxValue to DrawerState.Opened)
        val drawerPosition = state { maxValue }
        val isRtl = layoutDirection == LayoutDirection.Rtl
        Stack(Modifier.stateDraggable(
            state = drawerState,
            onStateChange = onStateChange,
            anchorsToState = anchors,
            animationSpec = AnimationSpec,
            dragDirection =
            if (isRtl) DragDirection.ReversedHorizontal else DragDirection.Horizontal,
            minValue = minValue,
            maxValue = maxValue,
            enabled = gesturesEnabled,
            onNewValue = { drawerPosition.value = it }
        )) {
            Stack {
                bodyContent()
            }
            Scrim(drawerState, onStateChange, fraction = {
                calculateFraction(minValue, maxValue, drawerPosition.value)
            })
            DrawerContent(
                drawerPosition, dpConstraints, drawerShape, drawerElevation, drawerContent
            )
        }
    }
}

/**
 * Navigation drawers provide access to destinations in your app.
 *
 * Bottom navigation drawers are modal drawers that are anchored
 * to the bottom of the screen instead of the left or right edge.
 * They are only used with bottom app bars.
 *
 * These drawers open upon tapping the navigation menu icon in the bottom app bar.
 * They are only for use on mobile.
 *
 * See [ModalDrawerLayout] for a layout that introduces a classic from-the-side drawer.
 *
 * @sample androidx.ui.material.samples.BottomDrawerSample
 *
 * @param drawerState state of the drawer
 * @param onStateChange lambda to be invoked when the drawer requests to change its state,
 * e.g. when the drawer is being swiped to the new state or when the scrim is clicked
 * @param gesturesEnabled whether or not drawer can be interacted by gestures
 * @param drawerShape shape of the drawer sheet
 * @param drawerElevation drawer sheet elevation. This controls the size of the shadow below the
 * drawer sheet
 * @param drawerContent composable that represents content inside the drawer
 * @param bodyContent content of the rest of the UI
 *
 * @throws IllegalStateException when parent has [Float.POSITIVE_INFINITY] height
 */
@Composable
fun BottomDrawerLayout(
    drawerState: DrawerState,
    onStateChange: (DrawerState) -> Unit,
    gesturesEnabled: Boolean = true,
    drawerShape: Shape = MaterialTheme.shapes.large,
    drawerElevation: Dp = DrawerConstants.DefaultElevation,
    drawerContent: @Composable () -> Unit,
    bodyContent: @Composable () -> Unit
) {
    WithConstraints(Modifier.fillMaxSize()) {
        // TODO : think about Infinite max bounds case
        if (!constraints.hasBoundedHeight) {
            throw IllegalStateException("Drawer shouldn't have infinite height")
        }
        val dpConstraints = with(DensityAmbient.current) {
            DpConstraints(constraints)
        }
        val minValue = 0f
        val maxValue = constraints.maxHeight.toFloat()

        // TODO: add proper landscape support
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        val openedValue = if (isLandscape) minValue else lerp(
            minValue,
            maxValue,
            BottomDrawerOpenFraction
        )
        val anchors =
            if (isLandscape) {
                listOf(maxValue to DrawerState.Closed, minValue to DrawerState.Opened)
            } else {
                listOf(
                    maxValue to DrawerState.Closed,
                    openedValue to DrawerState.Opened,
                    minValue to DrawerState.Opened
                )
            }
        val drawerPosition = state { maxValue }
        Stack(
            Modifier.stateDraggable(
                state = drawerState,
                onStateChange = onStateChange,
                anchorsToState = anchors,
                animationSpec = AnimationSpec,
                dragDirection = DragDirection.Vertical,
                minValue = minValue,
                maxValue = maxValue,
                enabled = gesturesEnabled,
                onNewValue = { drawerPosition.value = it }
            )
        ) {
            Stack {
                bodyContent()
            }
            Scrim(drawerState, onStateChange, fraction = {
                // as we scroll "from height to 0" , need to reverse fraction
                1 - calculateFraction(openedValue, maxValue, drawerPosition.value)
            })
            BottomDrawerContent(
                drawerPosition, dpConstraints, drawerShape, drawerElevation, drawerContent
            )
        }
    }
}

/**
 * Object to hold default values for [ModalDrawerLayout] and [BottomDrawerLayout]
 */
object DrawerConstants {

    /**
     * Default Elevation for drawer sheet as specified in material specs
     */
    val DefaultElevation = 16.dp
}

@Composable
private fun DrawerContent(
    xOffset: State<Float>,
    constraints: DpConstraints,
    shape: Shape,
    elevation: Dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier =
        Modifier
            .preferredSizeIn(constraints)
            .offsetPx(x = xOffset)
            .padding(end = VerticalDrawerPadding),
        shape = shape,
        elevation = elevation
    ) {
        Box(Modifier.fillMaxSize(), children = content)
    }
}

@Composable
private fun BottomDrawerContent(
    yOffset: State<Float>,
    constraints: DpConstraints,
    shape: Shape,
    elevation: Dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .preferredSizeIn(constraints)
            .offsetPx(y = yOffset),
        shape = shape,
        elevation = elevation
    ) {
        Box(Modifier.fillMaxSize(), children = content)
    }
}

private fun calculateFraction(a: Float, b: Float, pos: Float) =
    ((pos - a) / (b - a)).coerceIn(0f, 1f)

@Composable
private fun Scrim(
    state: DrawerState,
    onStateChange: (DrawerState) -> Unit,
    fraction: () -> Float
) {
    val color = MaterialTheme.colors.onSurface
    val dismissDrawer = if (state == DrawerState.Opened) {
        Modifier.tapGestureFilter { _ -> onStateChange(DrawerState.Closed) }
    } else {
        Modifier
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .plus(dismissDrawer)
    ) {
        drawRect(color, alpha = fraction() * ScrimDefaultOpacity)
    }
}

private const val ScrimDefaultOpacity = 0.32f
private val VerticalDrawerPadding = 56.dp

private const val DrawerStiffness = 1000f

private val AnimationSpec = SpringSpec<Float>(stiffness = DrawerStiffness)

internal const val BottomDrawerOpenFraction = 0.5f
