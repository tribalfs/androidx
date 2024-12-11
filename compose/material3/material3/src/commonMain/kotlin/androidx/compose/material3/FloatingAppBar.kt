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

package androidx.compose.material3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingAppBarDefaults.horizontalEnterTransition
import androidx.compose.material3.FloatingAppBarDefaults.horizontalExitTransition
import androidx.compose.material3.FloatingAppBarDefaults.verticalEnterTransition
import androidx.compose.material3.FloatingAppBarDefaults.verticalExitTransition
import androidx.compose.material3.FloatingAppBarExitDirection.Companion.Bottom
import androidx.compose.material3.FloatingAppBarExitDirection.Companion.End
import androidx.compose.material3.FloatingAppBarExitDirection.Companion.Start
import androidx.compose.material3.FloatingAppBarExitDirection.Companion.Top
import androidx.compose.material3.tokens.ColorSchemeKeyTokens
import androidx.compose.material3.tokens.ElevationTokens
import androidx.compose.material3.tokens.FabBaselineTokens
import androidx.compose.material3.tokens.FabMediumTokens
import androidx.compose.material3.tokens.FloatingToolbarTokens
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A horizontal floating app bar displays navigation and key actions in a [Row]. It can be
 * positioned anywhere on the screen and floats over the rest of the content.
 *
 * @sample androidx.compose.material3.samples.ExpandableHorizontalFloatingAppBarSample
 * @sample androidx.compose.material3.samples.ScrollableHorizontalFloatingAppBarSample
 * @param expanded whether the FloatingAppBar is in expanded mode, i.e. showing [leadingContent] and
 *   [trailingContent].
 * @param modifier the [Modifier] to be applied to this FloatingAppBar.
 * @param containerColor the color used for the background of this FloatingAppBar. Use
 *   [Color.Transparent] to have no color.
 * @param contentPadding the padding applied to the content of this FloatingAppBar.
 * @param scrollBehavior a [FloatingAppBarScrollBehavior]. If null, this FloatingAppBar will not
 *   automatically react to scrolling.
 * @param shape the shape used for this FloatingAppBar.
 * @param leadingContent the leading content of this FloatingAppBar. The default layout here is a
 *   [Row], so content inside will be placed horizontally. Only showing if [expanded] is true.
 * @param trailingContent the trailing content of this FloatingAppBar. The default layout here is a
 *   [Row], so content inside will be placed horizontally. Only showing if [expanded] is true.
 * @param content the main content of this FloatingAppBar. The default layout here is a [Row], so
 *   content inside will be placed horizontally.
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalFloatingAppBar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingAppBarDefaults.ContainerColor,
    contentPadding: PaddingValues = FloatingAppBarDefaults.ContentPadding,
    scrollBehavior: FloatingAppBarScrollBehavior? = null,
    shape: Shape = FloatingAppBarDefaults.ContainerShape,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier =
            modifier
                .then(
                    scrollBehavior?.let { with(it) { Modifier.floatingScrollBehavior() } }
                        ?: Modifier
                )
                .height(FloatingAppBarDefaults.ContainerSize)
                .background(color = containerColor, shape = shape)
                .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.let {
            val alignment = if (isRtl) Alignment.Start else Alignment.End
            AnimatedVisibility(
                visible = expanded,
                enter = horizontalEnterTransition(expandFrom = alignment),
                exit = horizontalExitTransition(shrinkTowards = alignment),
            ) {
                Row(content = it)
            }
        }
        content()
        trailingContent?.let {
            val alignment = if (isRtl) Alignment.End else Alignment.Start
            AnimatedVisibility(
                visible = expanded,
                enter = horizontalEnterTransition(expandFrom = alignment),
                exit = horizontalExitTransition(shrinkTowards = alignment),
            ) {
                Row(content = it)
            }
        }
    }
}

/**
 * A floating toolbar that displays horizontally. The bar features its content within a [Row], and
 * an adjacent floating icon button. It can be positioned anywhere on the screen, floating above
 * other content, and even in a `Scaffold`'s floating action button slot. Its [expanded] flag
 * controls the visibility of the actions with a slide animations.
 *
 * Note that if your app uses a `Snackbar`, it's best to position the toolbar in a `Scaffold`'s FAB
 * slot. This ensures the `Snackbar` appears above the toolbar, preventing any visual overlap or
 * interference.
 *
 * @sample androidx.compose.material3.samples.HorizontalFloatingToolbarWithFabSample
 * @sample androidx.compose.material3.samples.HorizontalFloatingToolbarAsScaffoldFabSample
 * @param expanded whether the floating toolbar is expanded or not. In its expanded state, the FAB
 *   and the toolbar content are organized horizontally. Otherwise, only the FAB is visible.
 * @param floatingActionButton a floating action button to be displayed by the toolbar. It's
 *   recommended to use a [FloatingAppBarDefaults.VibrantFloatingActionButton] or
 *   [FloatingAppBarDefaults.StandardFloatingActionButton] that is styled to match the [colors].
 * @param modifier the [Modifier] to be applied to this floating toolbar.
 * @param colors the colors used for this floating toolbar. There are two predefined
 *   [FloatingToolbarColors] at [FloatingAppBarDefaults.standardFloatingToolbarColors] and
 *   [FloatingAppBarDefaults.vibrantFloatingToolbarColors] which you can use or modify. See also
 *   [floatingActionButton] for more information on the right FAB to use for proper styling.
 * @param contentPadding the padding applied to the content of this floating toolbar.
 * @param shape the shape used for this floating toolbar content.
 * @param floatingActionButtonPosition the position of the floating toolbar's floating action
 *   button. By default, the FAB is placed at the end of the toolbar (i.e. aligned to the right in
 *   left-to-right layout, or to the left in right-to-left layout).
 * @param animationSpec the animation spec to use for this floating toolbar expand and collapse
 *   animation.
 * @param content the main content of this floating toolbar. The default layout here is a [Row], so
 *   content inside will be placed horizontally.
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun HorizontalFloatingToolbar(
    expanded: Boolean,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingAppBarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues =
        PaddingValues(horizontal = FloatingAppBarDefaults.ContentPaddingWithFloatingActionButton),
    shape: Shape = FloatingAppBarDefaults.ContainerShape,
    floatingActionButtonPosition: FloatingToolbarHorizontalFabPosition =
        FloatingToolbarHorizontalFabPosition.End,
    animationSpec: FiniteAnimationSpec<Float> = FloatingAppBarDefaults.animationSpec(),
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbarWithFabLayout(
        modifier = modifier,
        expanded = expanded,
        colors = colors,
        toolbarToFabGap = FloatingAppBarDefaults.ToolbarToFabGap,
        toolbarContentPadding = contentPadding,
        toolbarShape = shape,
        animationSpec = animationSpec,
        fab = floatingActionButton,
        fabPosition = floatingActionButtonPosition,
        toolbar = content
    )
}

/**
 * A vertical floating app bar displays navigation and key actions in a [Column]. It can be
 * positioned anywhere on the screen and floats over the rest of the content.
 *
 * @sample androidx.compose.material3.samples.ExpandableVerticalFloatingAppBarSample
 * @sample androidx.compose.material3.samples.ScrollableVerticalFloatingAppBarSample
 * @param expanded whether the FloatingAppBar is in expanded mode, i.e. showing [leadingContent] and
 *   [trailingContent].
 * @param modifier the [Modifier] to be applied to this FloatingAppBar.
 * @param containerColor the color used for the background of this FloatingAppBar. Use
 *   Color.Transparent] to have no color.
 * @param contentPadding the padding applied to the content of this FloatingAppBar.
 * @param scrollBehavior a [FloatingAppBarScrollBehavior]. If null, this FloatingAppBar will not
 *   automatically react to scrolling.
 * @param shape the shape used for this FloatingAppBar.
 * @param leadingContent the leading content of this FloatingAppBar. The default layout here is a
 *   [Column], so content inside will be placed vertically. Only showing if [expanded] is true.
 * @param trailingContent the trailing content of this FloatingAppBar. The default layout here is a
 *   [Column], so content inside will be placed vertically. Only showing if [expanded] is true.
 * @param content the main content of this FloatingAppBar. The default layout here is a [Column], so
 *   content inside will be placed vertically.
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalFloatingAppBar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = FloatingAppBarDefaults.ContainerColor,
    contentPadding: PaddingValues = FloatingAppBarDefaults.ContentPadding,
    scrollBehavior: FloatingAppBarScrollBehavior? = null,
    shape: Shape = FloatingAppBarDefaults.ContainerShape,
    leadingContent: @Composable (ColumnScope.() -> Unit)? = null,
    trailingContent: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            modifier
                .then(
                    scrollBehavior?.let { with(it) { Modifier.floatingScrollBehavior() } }
                        ?: Modifier
                )
                .width(FloatingAppBarDefaults.ContainerSize)
                .background(color = containerColor, shape = shape)
                .padding(contentPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        leadingContent?.let {
            AnimatedVisibility(
                visible = expanded,
                enter = verticalEnterTransition(expandFrom = Alignment.Bottom),
                exit = verticalExitTransition(shrinkTowards = Alignment.Bottom),
            ) {
                Column(content = it)
            }
        }
        content()
        trailingContent?.let {
            AnimatedVisibility(
                visible = expanded,
                enter = verticalEnterTransition(expandFrom = Alignment.Top),
                exit = verticalExitTransition(shrinkTowards = Alignment.Top),
            ) {
                Column(content = it)
            }
        }
    }
}

/**
 * A floating toolbar that displays vertically. The bar features its content within a [Column], and
 * an adjacent floating icon button. It can be positioned anywhere on the screen, floating above
 * other content, and its [expanded] flag controls the visibility of the actions with a slide
 * animations.
 *
 * @sample androidx.compose.material3.samples.VerticalFloatingToolbarWithFabSample
 * @param expanded whether the floating toolbar is expanded or not. In its expanded state, the FAB
 *   and the toolbar content are organized vertically. Otherwise, only the FAB is visible.
 * @param floatingActionButton a floating action button to be displayed by the toolbar. It's
 *   recommended to use a [FloatingAppBarDefaults.VibrantFloatingActionButton] or
 *   [FloatingAppBarDefaults.StandardFloatingActionButton] that is styled to match the [colors].
 * @param modifier the [Modifier] to be applied to this floating toolbar.
 * @param colors the colors used for this floating toolbar. There are two predefined
 *   [FloatingToolbarColors] at [FloatingAppBarDefaults.standardFloatingToolbarColors] and
 *   [FloatingAppBarDefaults.vibrantFloatingToolbarColors] which you can use or modify. See also
 *   [floatingActionButton] for more information on the right FAB to use for proper styling.
 * @param contentPadding the padding applied to the content of this floating toolbar.
 * @param shape the shape used for this floating toolbar content.
 * @param floatingActionButtonPosition the position of the floating toolbar's floating action
 *   button. By default, the FAB is placed at the bottom of the toolbar (i.e. aligned to the
 *   bottom).
 * @param animationSpec the animation spec to use for this floating toolbar expand and collapse
 *   animation.
 * @param content the main content of this floating toolbar. The default layout here is a [Column],
 *   so content inside will be placed vertically.
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingAppBarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues =
        PaddingValues(vertical = FloatingAppBarDefaults.ContentPaddingWithFloatingActionButton),
    shape: Shape = FloatingAppBarDefaults.ContainerShape,
    floatingActionButtonPosition: FloatingToolbarVerticalFabPosition =
        FloatingToolbarVerticalFabPosition.Bottom,
    animationSpec: FiniteAnimationSpec<Float> = FloatingAppBarDefaults.animationSpec(),
    content: @Composable ColumnScope.() -> Unit
) {
    VerticalFloatingToolbarWithFabLayout(
        modifier = modifier,
        expanded = expanded,
        colors = colors,
        toolbarToFabGap = FloatingAppBarDefaults.ToolbarToFabGap,
        toolbarContentPadding = contentPadding,
        toolbarShape = shape,
        animationSpec = animationSpec,
        fab = floatingActionButton,
        fabPosition = floatingActionButtonPosition,
        toolbar = content
    )
}

/**
 * A FloatingAppBarScrollBehavior defines how a floating app bar should behave when the content
 * under it is scrolled.
 *
 * @see [FloatingAppBarDefaults.exitAlwaysScrollBehavior]
 */
@ExperimentalMaterial3ExpressiveApi
@Stable
sealed interface FloatingAppBarScrollBehavior : NestedScrollConnection {

    /** Indicates the direction towards which the floating app bar exits the screen. */
    val exitDirection: FloatingAppBarExitDirection

    /**
     * A [FloatingAppBarState] that is attached to this behavior and is read and updated when
     * scrolling happens.
     */
    val state: FloatingAppBarState

    /**
     * An [AnimationSpec] that defines how the floating app bar snaps to either fully collapsed or
     * fully extended state when a fling or a drag scrolled it into an intermediate position.
     */
    val snapAnimationSpec: AnimationSpec<Float>

    /**
     * An [DecayAnimationSpec] that defines how to fling the floating app bar when the user flings
     * the app bar itself, or the content below it.
     */
    val flingAnimationSpec: DecayAnimationSpec<Float>

    /** A [Modifier] that is attached to this behavior. */
    fun Modifier.floatingScrollBehavior(): Modifier
}

/**
 * A [FloatingAppBarScrollBehavior] that adjusts its properties to affect the size of a floating app
 * bar.
 *
 * A floating app bar that is set up with this [FloatingAppBarScrollBehavior] will immediately
 * collapse when the nested content is pulled up, and will immediately appear when the content is
 * pulled down.
 *
 * @param exitDirection indicates the direction towards which the floating app bar exits the screen
 * @param state a [FloatingAppBarState]
 * @param snapAnimationSpec an [AnimationSpec] that defines how the floating app bar snaps to either
 *   fully collapsed or fully extended state when a fling or a drag scrolled it into an intermediate
 *   position
 * @param flingAnimationSpec an [DecayAnimationSpec] that defines how to fling the floating app bar
 *   when the user flings the app bar itself, or the content below it
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class ExitAlwaysFloatingAppBarScrollBehavior(
    override val exitDirection: FloatingAppBarExitDirection,
    override val state: FloatingAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>,
    override val flingAnimationSpec: DecayAnimationSpec<Float>,
) : FloatingAppBarScrollBehavior {

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        state.contentOffset += consumed.y
        state.offset += consumed.y
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y > 0f && (state.offset == 0f || state.offset == state.offsetLimit)) {
            // Reset the total content offset to zero when scrolling all the way down.
            // This will eliminate some float precision inaccuracies.
            state.contentOffset = 0f
        }
        val superConsumed = super.onPostFling(consumed, available)
        return superConsumed +
            settleFloatingAppBar(state, available.y, snapAnimationSpec, flingAnimationSpec)
    }

    override fun Modifier.floatingScrollBehavior(): Modifier {
        var isRtl = false
        val orientation =
            when (exitDirection) {
                Start,
                End -> Orientation.Horizontal
                else -> Orientation.Vertical
            }
        val draggableState = DraggableState { delta ->
            val offset = if (exitDirection in listOf(Start, End) && isRtl) -delta else delta
            when (exitDirection) {
                Start,
                Top -> state.offset += offset
                End,
                Bottom -> state.offset -= offset
            }
        }

        return this.layout { measurable, constraints ->
                isRtl = layoutDirection == LayoutDirection.Rtl

                // Sets the app bar's offset to collapse the entire bar's when content scrolled.
                val placeable = measurable.measure(constraints)
                val offset =
                    if (exitDirection in listOf(Start, End) && isRtl) -state.offset
                    else state.offset
                layout(placeable.width, placeable.height) {
                    when (exitDirection) {
                        Start -> placeable.placeWithLayer(offset.roundToInt(), 0)
                        End -> placeable.placeWithLayer(-offset.roundToInt(), 0)
                        Top -> placeable.placeWithLayer(0, offset.roundToInt())
                        Bottom -> placeable.placeWithLayer(0, -offset.roundToInt())
                    }
                }
            }
            .draggable(
                orientation = orientation,
                state = draggableState,
                onDragStopped = { velocity ->
                    settleFloatingAppBar(state, velocity, snapAnimationSpec, flingAnimationSpec)
                }
            )
            .onGloballyPositioned { coordinates ->
                // Updates the app bar's offsetLimit relative to the parent.
                val parentOffset = coordinates.positionInParent()
                val parentSize = coordinates.parentLayoutCoordinates?.size ?: IntSize.Zero
                val width = coordinates.size.width
                val height = coordinates.size.height
                val limit =
                    when (exitDirection) {
                        Start ->
                            if (isRtl) parentSize.width - parentOffset.x else width + parentOffset.x
                        End ->
                            if (isRtl) width + parentOffset.x else parentSize.width - parentOffset.x
                        Top -> height + parentOffset.y
                        else -> parentSize.height - parentOffset.y
                    }
                state.offsetLimit = -(limit - state.offset)
            }
    }
}

// TODO tokens
/** Contains default values used for the floating app bar implementations. */
@ExperimentalMaterial3ExpressiveApi
object FloatingAppBarDefaults {

    /** Default size used for [HorizontalFloatingAppBar] and [VerticalFloatingAppBar] container */
    val ContainerSize: Dp = FloatingToolbarTokens.ContainerHeight

    /** Default color used for [HorizontalFloatingAppBar] and [VerticalFloatingAppBar] container */
    val ContainerColor: Color
        @Composable get() = ColorSchemeKeyTokens.PrimaryContainer.value

    /** Default elevation used for [HorizontalFloatingAppBar] and [VerticalFloatingAppBar] */
    val ContainerElevation: Dp = ElevationTokens.Level0

    /** Default shape used for [HorizontalFloatingAppBar] and [VerticalFloatingAppBar] */
    val ContainerShape: Shape
        @Composable get() = FloatingToolbarTokens.ContainerShape.value

    /**
     * Default padding used for [HorizontalFloatingAppBar] and [VerticalFloatingAppBar] when content
     * are default size (24dp) icons in [IconButton] that meet the minimum touch target (48.dp).
     */
    val ContentPadding = PaddingValues(12.dp)

    /**
     * Default toolbar's content padding used for [HorizontalFloatingToolbar] and
     * [VerticalFloatingToolbar] with a floating action button. The direction that this padding is
     * applied is determined by the layout the toolbar is in.
     */
    val ContentPaddingWithFloatingActionButton = 12.dp

    /**
     * Default offset from the edge of the screen used for [HorizontalFloatingAppBar] and
     * [VerticalFloatingAppBar].
     */
    val ScreenOffset = FloatingToolbarTokens.ContainerExternalPadding

    /**
     * Returns a default animation spec used for [HorizontalFloatingToolbar]s and
     * [VerticalFloatingToolbar]s.
     */
    @Composable
    fun <T> animationSpec(): FiniteAnimationSpec<T> {
        // TODO Load the motionScheme tokens from the component tokens file
        return MotionSchemeKeyTokens.FastSpatial.value()
    }

    // TODO: note that this scroll behavior may impact assistive technologies making the component
    //  inaccessible.
    //  See @sample androidx.compose.material3.samples.ScrollableHorizontalFloatingAppBar on how
    //  to disable scrolling when touch exploration is enabled.
    /**
     * Returns a [FloatingAppBarScrollBehavior]. A floating app bar that is set up with this
     * [FloatingAppBarScrollBehavior] will immediately collapse when the content is pulled up, and
     * will immediately appear when the content is pulled down.
     *
     * @param exitDirection indicates the direction towards which the floating app bar exits the
     *   screen
     * @param state the state object to be used to control or observe the floating app bar's scroll
     *   state. See [rememberFloatingAppBarState] for a state that is remembered across
     *   compositions.
     * @param snapAnimationSpec an [AnimationSpec] that defines how the floating app bar snaps to
     *   either fully collapsed or fully extended state when a fling or a drag scrolled it into an
     *   intermediate position
     * @param flingAnimationSpec an [DecayAnimationSpec] that defines how to fling the floating app
     *   bar when the user flings the app bar itself, or the content below it
     */
    // TODO Load the motionScheme tokens from the component tokens file
    @ExperimentalMaterial3ExpressiveApi
    @Composable
    fun exitAlwaysScrollBehavior(
        exitDirection: FloatingAppBarExitDirection,
        state: FloatingAppBarState = rememberFloatingAppBarState(),
        snapAnimationSpec: AnimationSpec<Float> = MotionSchemeKeyTokens.DefaultEffects.value(),
        flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay()
    ): FloatingAppBarScrollBehavior =
        remember(exitDirection, state, snapAnimationSpec, flingAnimationSpec) {
            ExitAlwaysFloatingAppBarScrollBehavior(
                exitDirection = exitDirection,
                state = state,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec
            )
        }

    /** Default enter transition used for [HorizontalFloatingAppBar] when expanding */
    @Composable
    fun horizontalEnterTransition(expandFrom: Alignment.Horizontal) =
        expandHorizontally(
            animationSpec = animationSpec(),
            expandFrom = expandFrom,
        )

    /** Default enter transition used for [VerticalFloatingAppBar] when expanding */
    @Composable
    fun verticalEnterTransition(expandFrom: Alignment.Vertical) =
        expandVertically(
            animationSpec = animationSpec(),
            expandFrom = expandFrom,
        )

    /** Default exit transition used for [HorizontalFloatingAppBar] when shrinking */
    @Composable
    fun horizontalExitTransition(shrinkTowards: Alignment.Horizontal) =
        shrinkHorizontally(
            animationSpec = animationSpec(),
            shrinkTowards = shrinkTowards,
        )

    /** Default exit transition used for [VerticalFloatingAppBar] when shrinking */
    @Composable
    fun verticalExitTransition(shrinkTowards: Alignment.Vertical) =
        shrinkVertically(
            animationSpec = animationSpec(),
            shrinkTowards = shrinkTowards,
        )

    /**
     * Creates a [FloatingToolbarColors] that represents the default standard colors used in the
     * various floating toolbars.
     */
    @Composable
    fun standardFloatingToolbarColors(): FloatingToolbarColors =
        MaterialTheme.colorScheme.defaultFloatingToolbarStandardColors

    /**
     * Creates a [FloatingToolbarColors] that represents the default vibrant colors used in the
     * various floating toolbars.
     */
    @Composable
    fun vibrantFloatingToolbarColors(): FloatingToolbarColors =
        MaterialTheme.colorScheme.defaultFloatingToolbarVibrantColors

    /**
     * Creates a [FloatingToolbarColors] that represents the default standard colors used in the
     * various floating toolbars.
     *
     * @param toolbarContainerColor the container color for the floating toolbar.
     * @param toolbarContentColor the content color for the floating toolbar.
     * @param fabContainerColor the container color for an adjacent floating action button.
     * @param fabContentColor the content color for an adjacent floating action button.
     */
    @Composable
    fun standardFloatingToolbarColors(
        toolbarContainerColor: Color = Color.Unspecified,
        toolbarContentColor: Color = Color.Unspecified,
        fabContainerColor: Color = Color.Unspecified,
        fabContentColor: Color = Color.Unspecified
    ): FloatingToolbarColors =
        MaterialTheme.colorScheme.defaultFloatingToolbarStandardColors.copy(
            toolbarContainerColor = toolbarContainerColor,
            toolbarContentColor = toolbarContentColor,
            fabContainerColor = fabContainerColor,
            fabContentColor = fabContentColor
        )

    /**
     * Creates a [FloatingToolbarColors] that represents the default vibrant colors used in the
     * various floating toolbars.
     *
     * @param toolbarContainerColor the container color for the floating toolbar.
     * @param toolbarContentColor the content color for the floating toolbar.
     * @param fabContainerColor the container color for an adjacent floating action button.
     * @param fabContentColor the content color for an adjacent floating action button.
     */
    @Composable
    fun vibrantFloatingToolbarColors(
        toolbarContainerColor: Color = Color.Unspecified,
        toolbarContentColor: Color = Color.Unspecified,
        fabContainerColor: Color = Color.Unspecified,
        fabContentColor: Color = Color.Unspecified
    ): FloatingToolbarColors =
        MaterialTheme.colorScheme.defaultFloatingToolbarVibrantColors.copy(
            toolbarContainerColor = toolbarContainerColor,
            toolbarContentColor = toolbarContentColor,
            fabContainerColor = fabContainerColor,
            fabContentColor = fabContentColor
        )

    /**
     * Creates a [FloatingActionButton] that represents a toolbar floating action button with
     * vibrant colors.
     *
     * The FAB's elevation will be controlled by the floating toolbar.
     *
     * @param onClick called when this FAB is clicked
     * @param modifier the [Modifier] to be applied to this FAB
     * @param shape defines the shape of this FAB's container and shadow
     * @param containerColor the color used for the background of this FAB. Defaults to the
     *   [FloatingToolbarColors.fabContainerColor] from the [vibrantFloatingToolbarColors].
     * @param contentColor the preferred color for content inside this FAB. Defaults to the
     *   [FloatingToolbarColors.fabContentColor] from the [vibrantFloatingToolbarColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this FAB. You can use this to change the FAB's appearance or
     *   preview the FAB in different states. Note that if `null` is provided, interactions will
     *   still happen internally.
     * @param content the content of this FAB, typically an [Icon]
     */
    @Composable
    fun VibrantFloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        shape: Shape = FloatingActionButtonDefaults.shape,
        containerColor: Color = vibrantFloatingToolbarColors().fabContainerColor,
        contentColor: Color = vibrantFloatingToolbarColors().fabContentColor,
        interactionSource: MutableInteractionSource? = null,
        content: @Composable () -> Unit,
    ) =
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource,
            content = content
        )

    /**
     * Creates a [FloatingActionButton] that represents a toolbar floating action button with
     * standard colors.
     *
     * The FAB's elevation will be controlled by the floating toolbar.
     *
     * @param onClick called when this FAB is clicked
     * @param modifier the [Modifier] to be applied to this FAB
     * @param shape defines the shape of this FAB's container and shadow
     * @param containerColor the color used for the background of this FAB. Defaults to the
     *   [FloatingToolbarColors.fabContainerColor] from the [standardFloatingToolbarColors].
     * @param contentColor the preferred color for content inside this FAB. Defaults to the
     *   [FloatingToolbarColors.fabContentColor] from the [standardFloatingToolbarColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this FAB. You can use this to change the FAB's appearance or
     *   preview the FAB in different states. Note that if `null` is provided, interactions will
     *   still happen internally.
     * @param content the content of this FAB, typically an [Icon]
     */
    @Composable
    fun StandardFloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        shape: Shape = FloatingActionButtonDefaults.shape,
        containerColor: Color = standardFloatingToolbarColors().fabContainerColor,
        contentColor: Color = standardFloatingToolbarColors().fabContentColor,
        interactionSource: MutableInteractionSource? = null,
        content: @Composable () -> Unit,
    ) =
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource,
            content = content
        )

    /**
     * This [Modifier] tracks vertical scroll events on the scrolling container that a floating
     * toolbar appears above. It then calls [onExpand] and [onCollapse] to adjust the toolbar's
     * state based on the scroll direction and distance.
     *
     * Essentially, it expands the toolbar when you scroll down past a certain threshold and
     * collapses it when you scroll back up. You can customize the expand and collapse thresholds
     * through the [expandScrollDistanceThreshold] and [collapseScrollDistanceThreshold].
     *
     * @param expanded the current expanded state of the floating toolbar
     * @param onExpand callback to be invoked when the toolbar should expand
     * @param onCollapse callback to be invoked when the toolbar should collapse
     * @param expandScrollDistanceThreshold the scroll distance (in dp) required to trigger an
     *   [onExpand]
     * @param collapseScrollDistanceThreshold the scroll distance (in dp) required to trigger an
     *   [onCollapse]
     * @param reverseLayout indicates that the scrollable content has a reversed scrolling direction
     */
    fun Modifier.floatingToolbarVerticalNestedScroll(
        expanded: Boolean,
        onExpand: () -> Unit,
        onCollapse: () -> Unit,
        expandScrollDistanceThreshold: Dp = ScrollDistanceThreshold,
        collapseScrollDistanceThreshold: Dp = ScrollDistanceThreshold,
        reverseLayout: Boolean = false
    ): Modifier =
        this then
            VerticalNestedScrollExpansionElement(
                expanded = expanded,
                onExpand = onExpand,
                onCollapse = onCollapse,
                reverseLayout = reverseLayout,
                expandScrollThreshold = expandScrollDistanceThreshold,
                collapseScrollThreshold = collapseScrollDistanceThreshold
            )

    internal data class VerticalNestedScrollExpansionElement(
        val expanded: Boolean,
        val onExpand: () -> Unit,
        val onCollapse: () -> Unit,
        val reverseLayout: Boolean,
        val expandScrollThreshold: Dp,
        val collapseScrollThreshold: Dp
    ) : ModifierNodeElement<VerticalNestedScrollExpansionNode>() {
        override fun create() =
            VerticalNestedScrollExpansionNode(
                expanded = expanded,
                onExpand = onExpand,
                onCollapse = onCollapse,
                reverseLayout = reverseLayout,
                expandScrollThreshold = expandScrollThreshold,
                collapseScrollThreshold = collapseScrollThreshold
            )

        override fun update(node: VerticalNestedScrollExpansionNode) {
            node.updateNode(
                expanded,
                onExpand,
                onCollapse,
                reverseLayout,
                expandScrollThreshold,
                collapseScrollThreshold
            )
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "floatingToolbarVerticalNestedScroll"
            properties["expanded"] = expanded
            properties["expandScrollThreshold"] = expandScrollThreshold
            properties["collapseScrollThreshold"] = collapseScrollThreshold
            properties["reverseLayout"] = reverseLayout
            properties["onExpand"] = onExpand
            properties["onCollapse"] = onCollapse
        }
    }

    internal class VerticalNestedScrollExpansionNode(
        var expanded: Boolean,
        var onExpand: () -> Unit,
        var onCollapse: () -> Unit,
        var reverseLayout: Boolean,
        var expandScrollThreshold: Dp,
        var collapseScrollThreshold: Dp,
    ) : DelegatingNode(), CompositionLocalConsumerModifierNode, NestedScrollConnection {
        private var expandScrollThresholdPx = 0f
        private var collapseScrollThresholdPx = 0f
        private var contentOffset = 0f
        private var threshold = 0f

        // In reverse layouts, scrolling direction is flipped. We will use this factor to flip some
        // of the values we read on the onPostScroll to ensure consistent behavior regardless of
        // scroll direction.
        private var reverseLayoutFactor = if (reverseLayout) -1 else 1

        override val shouldAutoInvalidate: Boolean
            get() = false

        private var nestedScrollNode: DelegatableNode =
            nestedScrollModifierNode(
                connection = this,
                dispatcher = null,
            )

        override fun onAttach() {
            delegate(nestedScrollNode)
            with(nestedScrollNode.requireDensity()) {
                expandScrollThresholdPx = expandScrollThreshold.toPx()
                collapseScrollThresholdPx = collapseScrollThreshold.toPx()
            }
            updateThreshold()
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val scrollDelta = consumed.y * reverseLayoutFactor
            contentOffset += scrollDelta

            if (scrollDelta < 0 && contentOffset <= threshold) {
                threshold = contentOffset + expandScrollThresholdPx
                onCollapse()
            } else if (scrollDelta > 0 && contentOffset >= threshold) {
                threshold = contentOffset - collapseScrollThresholdPx
                onExpand()
            }
            return Offset.Zero
        }

        fun updateNode(
            expanded: Boolean,
            onExpand: () -> Unit,
            onCollapse: () -> Unit,
            reverseLayout: Boolean,
            expandScrollThreshold: Dp,
            collapseScrollThreshold: Dp
        ) {
            if (
                this.expandScrollThreshold != expandScrollThreshold ||
                    this.collapseScrollThreshold != collapseScrollThreshold
            ) {
                this.expandScrollThreshold = expandScrollThreshold
                this.collapseScrollThreshold = collapseScrollThreshold
                with(nestedScrollNode.requireDensity()) {
                    expandScrollThresholdPx = expandScrollThreshold.toPx()
                    collapseScrollThresholdPx = collapseScrollThreshold.toPx()
                }
                updateThreshold()
            }
            if (this.reverseLayout != reverseLayout) {
                this.reverseLayout = reverseLayout
                reverseLayoutFactor = if (this.reverseLayout) -1 else 1
            }

            this.onExpand = onExpand
            this.onCollapse = onCollapse

            if (this.expanded != expanded) {
                this.expanded = expanded
                updateThreshold()
            }
        }

        private fun updateThreshold() {
            threshold =
                if (expanded) {
                    contentOffset - collapseScrollThresholdPx
                } else {
                    contentOffset + expandScrollThresholdPx
                }
        }
    }

    internal val ColorScheme.defaultFloatingToolbarStandardColors: FloatingToolbarColors
        get() {
            return defaultFloatingToolbarStandardColorsCached
                ?: FloatingToolbarColors(
                        // TODO load colors from the toolbar tokens. If possible, remove the usage
                        //  of contentColorFor.
                        toolbarContainerColor =
                            fromToken(FloatingToolbarTokens.StandardContainerColor),
                        toolbarContentColor =
                            contentColorFor(
                                fromToken(FloatingToolbarTokens.StandardContainerColor)
                            ),
                        fabContainerColor = fromToken(ColorSchemeKeyTokens.PrimaryContainer),
                        fabContentColor =
                            contentColorFor(fromToken(ColorSchemeKeyTokens.PrimaryContainer)),
                    )
                    .also { defaultFloatingToolbarStandardColorsCached = it }
        }

    internal val ColorScheme.defaultFloatingToolbarVibrantColors: FloatingToolbarColors
        get() {
            return defaultFloatingToolbarVibrantColorsCached
                ?: FloatingToolbarColors(
                        // TODO load colors from the toolbar tokens. If possible, remove the usage
                        //  of contentColorFor.
                        toolbarContainerColor =
                            fromToken(FloatingToolbarTokens.VibrantContainerColor),
                        toolbarContentColor =
                            contentColorFor(fromToken(FloatingToolbarTokens.VibrantContainerColor)),
                        fabContainerColor = fromToken(ColorSchemeKeyTokens.TertiaryContainer),
                        fabContentColor =
                            contentColorFor(fromToken(ColorSchemeKeyTokens.TertiaryContainer)),
                    )
                    .also { defaultFloatingToolbarVibrantColorsCached = it }
        }

    /**
     * A default threshold in [Dp] for the content's scrolling that defines when the toolbar should
     * be collapsed or expanded.
     */
    val ScrollDistanceThreshold: Dp = 40.dp

    /**
     * Default elevation used for the toolbar container of the [HorizontalFloatingToolbar] and
     * [VerticalFloatingToolbar] when displayed with an adjacent FAB.
     */
    internal val ToolbarElevationWithFab: Dp = ElevationTokens.Level1

    /**
     * Size range used for a FAB size in [HorizontalFloatingToolbar] and [VerticalFloatingToolbar].
     */
    internal val FabSizeRange = FabBaselineTokens.ContainerWidth..FabMediumTokens.ContainerWidth

    /**
     * Elevation range used for a FAB size in [HorizontalFloatingToolbar] and
     * [VerticalFloatingToolbar].
     */
    internal val FabElevationRange = ElevationTokens.Level1..ElevationTokens.Level2

    /**
     * Default gap between the [HorizontalFloatingToolbar] or [VerticalFloatingToolbar] toolbar
     * content and its adjacent FAB.
     */
    // TODO Load this from the component tokens?
    internal val ToolbarToFabGap = 8.dp
}

/**
 * Represents the container and content colors used in a the various floating toolbars.
 *
 * @param toolbarContainerColor the container color for the floating toolbar.
 * @param toolbarContentColor the content color for the floating toolbar
 * @param fabContainerColor the container color for an adjacent floating action button.
 * @param fabContentColor the content color for an adjacent floating action button
 */
@ExperimentalMaterial3ExpressiveApi
@Immutable
class FloatingToolbarColors(
    val toolbarContainerColor: Color,
    val toolbarContentColor: Color,
    val fabContainerColor: Color,
    val fabContentColor: Color,
) {

    /**
     * Returns a copy of this IconToggleButtonColors, optionally overriding some of the values. This
     * uses the Color.Unspecified to mean “use the value from the source”
     */
    fun copy(
        toolbarContainerColor: Color = this.toolbarContainerColor,
        toolbarContentColor: Color = this.toolbarContentColor,
        fabContainerColor: Color = this.fabContainerColor,
        fabContentColor: Color = this.fabContentColor,
    ) =
        FloatingToolbarColors(
            toolbarContainerColor.takeOrElse { this.toolbarContainerColor },
            toolbarContentColor.takeOrElse { this.toolbarContentColor },
            fabContainerColor.takeOrElse { this.fabContainerColor },
            fabContentColor.takeOrElse { this.fabContentColor },
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is FloatingToolbarColors) return false

        if (toolbarContainerColor != other.toolbarContainerColor) return false
        if (toolbarContentColor != other.toolbarContentColor) return false
        if (fabContainerColor != other.fabContainerColor) return false
        if (fabContentColor != other.fabContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = toolbarContainerColor.hashCode()
        result = 31 * result + toolbarContentColor.hashCode()
        result = 31 * result + fabContainerColor.hashCode()
        result = 31 * result + fabContentColor.hashCode()

        return result
    }
}

/**
 * The possible positions for a [FloatingActionButton] attached to a [HorizontalFloatingToolbar]
 *
 * @see FloatingAppBarDefaults.StandardFloatingActionButton
 * @see FloatingAppBarDefaults.VibrantFloatingActionButton
 */
@ExperimentalMaterial3ExpressiveApi
@JvmInline
value class FloatingToolbarHorizontalFabPosition
internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /** Position FAB at the start of the toolbar. */
        val Start = FloatingToolbarHorizontalFabPosition(0)

        /** Position FAB at the end of the toolbar. */
        val End = FloatingToolbarHorizontalFabPosition(1)
    }

    override fun toString(): String {
        return when (this) {
            Start -> "FloatingToolbarHorizontalFabPosition.Start"
            else -> "FloatingToolbarHorizontalFabPosition.End"
        }
    }
}

/**
 * The possible positions for a [FloatingActionButton] attached to a [VerticalFloatingToolbar]
 *
 * @see FloatingAppBarDefaults.StandardFloatingActionButton
 * @see FloatingAppBarDefaults.VibrantFloatingActionButton
 */
@ExperimentalMaterial3ExpressiveApi
@JvmInline
value class FloatingToolbarVerticalFabPosition
internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /** Position FAB at the top of the toolbar. */
        val Top = FloatingToolbarVerticalFabPosition(0)

        /** Position FAB at the bottom of the toolbar. */
        val Bottom = FloatingToolbarVerticalFabPosition(1)
    }

    override fun toString(): String {
        return when (this) {
            Top -> "FloatingToolbarVerticalFabPosition.Top"
            else -> "FloatingToolbarVerticalFabPosition.Bottom"
        }
    }
}

/**
 * Creates a [FloatingAppBarState] that is remembered across compositions.
 *
 * @param initialOffsetLimit the initial value for [FloatingAppBarState.offsetLimit], which
 *   represents the pixel limit that a floating app bar is allowed to collapse when the scrollable
 *   content is scrolled.
 * @param initialOffset the initial value for [FloatingAppBarState.offset]. The initial offset
 *   should be between zero and [initialOffsetLimit].
 * @param initialContentOffset the initial value for [FloatingAppBarState.contentOffset]
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun rememberFloatingAppBarState(
    initialOffsetLimit: Float = -Float.MAX_VALUE,
    initialOffset: Float = 0f,
    initialContentOffset: Float = 0f
): FloatingAppBarState {
    return rememberSaveable(saver = FloatingAppBarState.Saver) {
        FloatingAppBarState(initialOffsetLimit, initialOffset, initialContentOffset)
    }
}

/**
 * A state object that can be hoisted to control and observe the floating app bar state. The state
 * is read and updated by a [FloatingAppBarScrollBehavior] implementation.
 *
 * In most cases, this state will be created via [rememberFloatingAppBarState].
 */
@ExperimentalMaterial3ExpressiveApi
interface FloatingAppBarState {

    /**
     * The floating app bar's offset limit in pixels, which represents the limit that a floating app
     * bar is allowed to collapse to.
     *
     * Use this limit to coerce the [offset] value when it's updated.
     */
    var offsetLimit: Float

    /**
     * The floating app bar's current offset in pixels. This offset is applied to the fixed size of
     * the app bar to control the displayed size when content is being scrolled.
     *
     * Updates to the [offset] value are coerced between zero and [offsetLimit].
     */
    var offset: Float

    /**
     * The total offset of the content scrolled under the floating app bar.
     *
     * This value is updated by a [FloatingAppBarScrollBehavior] whenever a nested scroll connection
     * consumes scroll events. A common implementation would update the value to be the sum of all
     * [NestedScrollConnection.onPostScroll] `consumed` values.
     */
    var contentOffset: Float

    companion object {
        /** The default [Saver] implementation for [FloatingAppBarState]. */
        internal val Saver: Saver<FloatingAppBarState, *> =
            listSaver(
                save = { listOf(it.offsetLimit, it.offset, it.contentOffset) },
                restore = {
                    FloatingAppBarState(
                        initialOffsetLimit = it[0],
                        initialOffset = it[1],
                        initialContentOffset = it[2]
                    )
                }
            )
    }
}

/**
 * Creates a [FloatingAppBarState].
 *
 * @param initialOffsetLimit the initial value for [FloatingAppBarState.offsetLimit], which
 *   represents the pixel limit that a floating app bar is allowed to collapse when the scrollable
 *   content is scrolled.
 * @param initialOffset the initial value for [FloatingAppBarState.offset]. The initial offset
 *   should be between zero and [initialOffsetLimit].
 * @param initialContentOffset the initial value for [FloatingAppBarState.contentOffset]
 */
@ExperimentalMaterial3ExpressiveApi
fun FloatingAppBarState(
    initialOffsetLimit: Float,
    initialOffset: Float,
    initialContentOffset: Float
): FloatingAppBarState =
    FloatingAppBarStateImpl(initialOffsetLimit, initialOffset, initialContentOffset)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Stable
private class FloatingAppBarStateImpl(
    initialOffsetLimit: Float,
    initialOffset: Float,
    initialContentOffset: Float
) : FloatingAppBarState {

    override var offsetLimit by mutableFloatStateOf(initialOffsetLimit)

    override var offset: Float
        get() = _offset.floatValue
        set(newOffset) {
            _offset.floatValue = newOffset.coerceIn(minimumValue = offsetLimit, maximumValue = 0f)
        }

    override var contentOffset by mutableFloatStateOf(initialContentOffset)

    private var _offset = mutableFloatStateOf(initialOffset)
}

/**
 * Settles the app bar by flinging, in case the given velocity is greater than zero, and snapping
 * after the fling settles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private suspend fun settleFloatingAppBar(
    state: FloatingAppBarState,
    velocity: Float,
    snapAnimationSpec: AnimationSpec<Float>,
    flingAnimationSpec: DecayAnimationSpec<Float>
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    val collapsedFraction = state.collapsedFraction()
    if (collapsedFraction < 0.01f || collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    // In case there is an initial velocity that was left after a previous user fling, animate to
    // continue the motion to expand or collapse the app bar.
    if (abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(
                initialValue = 0f,
                initialVelocity = velocity,
            )
            .animateDecay(flingAnimationSpec) {
                val delta = value - lastValue
                val initialOffset = state.offset
                state.offset = initialOffset + delta
                val consumed = abs(initialOffset - state.offset)
                lastValue = value
                remainingVelocity = this.velocity
                // avoid rounding errors and stop if anything is unconsumed
                if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
            }
    }

    if (state.offset < 0 && state.offset > state.offsetLimit) {
        AnimationState(initialValue = state.offset).animateTo(
            if (state.collapsedFraction() < 0.5f) {
                0f
            } else {
                state.offsetLimit
            },
            animationSpec = snapAnimationSpec
        ) {
            state.offset = value
        }
    }

    return Velocity(0f, remainingVelocity)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FloatingAppBarState.collapsedFraction() =
    if (offsetLimit != 0f) {
        offset / offsetLimit
    } else {
        0f
    }

/**
 * The possible directions for a [HorizontalFloatingAppBar] or [VerticalFloatingAppBar], used to
 * determine the exit direction when a [FloatingAppBarScrollBehavior] is attached.
 */
@ExperimentalMaterial3ExpressiveApi
@JvmInline
value class FloatingAppBarExitDirection
internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /** FloatingAppBar exits towards the bottom of the screen */
        val Bottom = FloatingAppBarExitDirection(0)

        /** FloatingAppBar exits towards the top of the screen */
        val Top = FloatingAppBarExitDirection(1)

        /** FloatingAppBar exits towards the start of the screen */
        val Start = FloatingAppBarExitDirection(2)

        /** FloatingAppBar exits towards the end of the screen */
        val End = FloatingAppBarExitDirection(3)
    }

    override fun toString(): String {
        return when (this) {
            Bottom -> "FloatingAppBarExitDirection.Bottom"
            Top -> "FloatingAppBarExitDirection.Top"
            Start -> "FloatingAppBarExitDirection.Start"
            else -> "FloatingAppBarExitDirection.End"
        }
    }
}

/** A layout for a floating toolbar that has a FAB next to it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HorizontalFloatingToolbarWithFabLayout(
    modifier: Modifier,
    expanded: Boolean,
    colors: FloatingToolbarColors,
    toolbarToFabGap: Dp,
    toolbarContentPadding: PaddingValues,
    toolbarShape: Shape,
    animationSpec: FiniteAnimationSpec<Float>,
    fab: @Composable () -> Unit,
    fabPosition: FloatingToolbarHorizontalFabPosition,
    toolbar: @Composable RowScope.() -> Unit
) {
    val fabShape = FloatingActionButtonDefaults.shape
    val expandTransition = updateTransition(if (expanded) 1f else 0f, label = "expanded state")
    val expandedProgress = expandTransition.animateFloat(transitionSpec = { animationSpec }) { it }
    Layout(
        {
            Row(
                modifier =
                    Modifier.background(colors.toolbarContainerColor)
                        .padding(toolbarContentPadding)
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.toolbarContentColor) {
                    toolbar()
                }
            }
            fab()
        },
        modifier =
            modifier.defaultMinSize(minHeight = FloatingAppBarDefaults.FabSizeRange.endInclusive)
    ) { measurables, constraints ->
        val toolbarMeasurable = measurables[0]
        val fabMeasurable = measurables[1]

        // The FAB is in its smallest size when the expanded progress is 1f.
        val fabSizeConstraint =
            FloatingAppBarDefaults.FabSizeRange.lerp(1f - expandedProgress.value).roundToPx()
        val fabPlaceable =
            fabMeasurable.measure(
                constraints.copy(minWidth = fabSizeConstraint, minHeight = fabSizeConstraint)
            )

        // Compute the toolbar's max intrinsic width. We will use it as a base to determine the
        // actual width with the animation progress and the total layout width.
        val maxToolbarPlaceableWidth =
            toolbarMeasurable.maxIntrinsicWidth(
                height = FloatingAppBarDefaults.ContainerSize.roundToPx()
            )
        // Constraint the toolbar to the available width while taking into account the FAB width.
        val toolbarPlaceable =
            toolbarMeasurable.measure(
                constraints.copy(
                    maxWidth =
                        (maxToolbarPlaceableWidth * expandedProgress.value)
                            .coerceAtLeast(0f)
                            .toInt(),
                    minHeight = FloatingAppBarDefaults.ContainerSize.roundToPx()
                )
            )

        val width =
            maxToolbarPlaceableWidth +
                toolbarToFabGap.roundToPx() +
                FloatingAppBarDefaults.FabSizeRange.start.roundToPx()
        val height = constraints.minHeight

        val toolbarTopOffset = (height - toolbarPlaceable.height) / 2
        val fapTopOffset = (height - fabPlaceable.height) / 2

        val fabX =
            if (fabPosition == FloatingToolbarHorizontalFabPosition.End) {
                width - fabPlaceable.width
            } else {
                0
            }
        val toolbarX =
            if (fabPosition == FloatingToolbarHorizontalFabPosition.End) {
                maxToolbarPlaceableWidth - toolbarPlaceable.width
            } else {
                width - maxToolbarPlaceableWidth
            }

        layout(width, height) {
            toolbarPlaceable.placeRelativeWithLayer(x = toolbarX, y = toolbarTopOffset) {
                shadowElevation = FloatingAppBarDefaults.ToolbarElevationWithFab.toPx()
                shape = toolbarShape
                clip = true
            }
            val fabElevation =
                FloatingAppBarDefaults.FabElevationRange.lerp(
                        1f - expandedProgress.value.coerceAtMost(1f)
                    )
                    .toPx()
            fabPlaceable.placeRelativeWithLayer(x = fabX, y = fapTopOffset) {
                shape = fabShape
                shadowElevation = fabElevation
                clip = true
            }
        }
    }
}

/** A layout for a floating toolbar that has a FAB above or below it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerticalFloatingToolbarWithFabLayout(
    modifier: Modifier,
    expanded: Boolean,
    colors: FloatingToolbarColors,
    toolbarToFabGap: Dp,
    toolbarContentPadding: PaddingValues,
    toolbarShape: Shape,
    animationSpec: FiniteAnimationSpec<Float>,
    fab: @Composable () -> Unit,
    fabPosition: FloatingToolbarVerticalFabPosition,
    toolbar: @Composable ColumnScope.() -> Unit
) {
    val fabShape = FloatingActionButtonDefaults.shape
    val expandTransition = updateTransition(if (expanded) 1f else 0f, label = "expanded state")
    val expandedProgress = expandTransition.animateFloat(transitionSpec = { animationSpec }) { it }
    Layout(
        {
            Column(
                modifier =
                    Modifier.background(colors.toolbarContainerColor)
                        .padding(toolbarContentPadding)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.toolbarContentColor) {
                    toolbar()
                }
            }
            fab()
        },
        modifier =
            modifier.defaultMinSize(minWidth = FloatingAppBarDefaults.FabSizeRange.endInclusive)
    ) { measurables, constraints ->
        val toolbarMeasurable = measurables[0]
        val fabMeasurable = measurables[1]

        // The FAB is in its smallest size when the expanded progress is 1f.
        val fabSizeConstraint =
            FloatingAppBarDefaults.FabSizeRange.lerp(1f - expandedProgress.value).roundToPx()
        val fabPlaceable =
            fabMeasurable.measure(
                constraints.copy(minWidth = fabSizeConstraint, minHeight = fabSizeConstraint)
            )

        // Compute the toolbar's max intrinsic height. We will use it as a base to determine the
        // actual height with the animation progress and the total layout height.
        val maxToolbarPlaceableHeight =
            toolbarMeasurable.maxIntrinsicHeight(
                width = FloatingAppBarDefaults.ContainerSize.roundToPx()
            )
        // Constraint the toolbar to the available height while taking into account the FAB height.
        val toolbarPlaceable =
            toolbarMeasurable.measure(
                constraints.copy(
                    maxHeight =
                        (maxToolbarPlaceableHeight * expandedProgress.value)
                            .coerceAtLeast(0f)
                            .toInt(),
                    minWidth = FloatingAppBarDefaults.ContainerSize.roundToPx()
                )
            )

        val width = constraints.minWidth
        val height =
            maxToolbarPlaceableHeight +
                toolbarToFabGap.roundToPx() +
                FloatingAppBarDefaults.FabSizeRange.start.roundToPx()

        val toolbarEdgeOffset = (width - toolbarPlaceable.width) / 2
        val fapEdgeOffset = (width - fabPlaceable.width) / 2

        val fabY =
            if (fabPosition == FloatingToolbarVerticalFabPosition.Bottom) {
                height - fabPlaceable.height
            } else {
                0
            }
        val toolbarY =
            if (fabPosition == FloatingToolbarVerticalFabPosition.Bottom) {
                maxToolbarPlaceableHeight - toolbarPlaceable.height
            } else {
                height - maxToolbarPlaceableHeight
            }

        layout(width, height) {
            toolbarPlaceable.placeRelativeWithLayer(
                x = toolbarEdgeOffset,
                y = toolbarY,
            ) {
                shadowElevation = FloatingAppBarDefaults.ToolbarElevationWithFab.toPx()
                shape = toolbarShape
                clip = true
            }
            val fabElevation =
                FloatingAppBarDefaults.FabElevationRange.lerp(
                        1f - expandedProgress.value.coerceAtMost(1f)
                    )
                    .toPx()
            fabPlaceable.placeRelativeWithLayer(x = fapEdgeOffset, y = fabY) {
                shape = fabShape
                shadowElevation = fabElevation
                clip = true
            }
        }
    }
}

private fun ClosedRange<Dp>.lerp(progress: Float): Dp =
    androidx.compose.ui.unit.lerp(start, endInclusive, progress)
