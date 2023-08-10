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
@file:OptIn(InternalAnimationApi::class, ExperimentalAnimationApi::class)

package androidx.compose.animation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Down
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.End
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Left
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Right
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Start
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Up
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InternalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.createDeferredAnimation
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

/**
 * [AnimatedContent] is a container that automatically animates its content when [targetState]
 * changes. Its [content] for different target states is defined in a mapping between a target
 * state and a composable function.
 *
 * **IMPORTANT**: The targetState parameter for the [content] lambda should *always* be
 * taken into account in deciding what composable function to return as the content for that state.
 * This is critical to ensure a successful lookup of all the incoming and outgoing content during
 * content transform.
 *
 * When [targetState] changes, content for both new and previous targetState will be
 * looked up through the [content] lambda. They will go through a [ContentTransform] so that
 * the new target content can be animated in while the initial content animates out. Meanwhile the
 * container will animate its size as needed to accommodate the new content, unless
 * [SizeTransform] is set to `null`. Once the [ContentTransform] is finished, the
 * outgoing content will be disposed.
 *
 * If [targetState] is expected to mutate frequently and not all mutations should be treated as
 * target state change, consider defining a mapping between [targetState] and a key in [contentKey].
 * As a result, transitions will be triggered when the resulting key changes. In other words,
 * there will be no animation when switching between [targetState]s that share the same same key.
 * By default, the key will be the same as the targetState object.
 *
 * By default, the [ContentTransform] will be a delayed [fadeIn] of the target content and a delayed
 * [scaleIn] [togetherWith] a [fadeOut] of the initial content, using a [SizeTransform] to
 * animate any size change of the content. This behavior can be customized using [transitionSpec].
 * If desired, different [ContentTransform]s can be defined for different pairs of initial content
 * and target content.
 *
 * [AnimatedContent] displays only the content for [targetState] when not animating. However,
 * during the transient content transform, there will be more than one set of content present in
 * the [AnimatedContent] container. It may be sometimes desired to define the positional
 * relationship among the different content and the overlap. This can be achieved by defining
 * [contentAlignment] and [zOrder][ContentTransform.targetContentZIndex]. By default,
 * [contentAlignment] aligns all content to [Alignment.TopStart], and the `zIndex` for all
 * the content is 0f. __Note__: The target content will always be placed last, therefore it will be
 * on top of all the other content unless zIndex is specified.
 *
 * Different content in [AnimatedContent] will have access to their own
 * [AnimatedContentScope]. This allows content to define more local enter/exit transitions
 * via [AnimatedContentScope.animateEnterExit] and [AnimatedContentScope.transition]. These
 * custom enter/exit animations will be triggered as the content enters/leaves the container.
 *
 * [label] is an optional parameter to differentiate from other animations in Android Studio.
 *
 * @sample androidx.compose.animation.samples.SimpleAnimatedContentSample
 *
 * Below is an example of customizing [transitionSpec] to imply a spatial relationship between
 * the content for different states:
 *
 * @sample androidx.compose.animation.samples.AnimateIncrementDecrementSample
 *
 * @see ContentTransform
 * @see AnimatedContentScope
 */
@Composable
fun <S> AnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
            .togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "AnimatedContent",
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable() AnimatedContentScope.(targetState: S) -> Unit
) {
    val transition = updateTransition(targetState = targetState, label = label)
    transition.AnimatedContent(
        modifier,
        transitionSpec,
        contentAlignment,
        contentKey,
        content = content
    )
}

/**
 * [ContentTransform] defines how the target content (i.e. content associated with target state)
 * enters [AnimatedContent] and how the initial content disappears.
 *
 * [targetContentEnter] defines the enter transition for the content associated with the new
 * target state. It can be a combination of [fadeIn], [slideIn]/[slideInHorizontally]
 * /[slideInVertically]/[AnimatedContentTransitionScope.slideIntoContainer], and expand. Similarly,
 * [initialContentExit] supports a combination of [ExitTransition] for animating out the initial
 * content (i.e. outgoing content). If the initial content and target content are of different
 * size, the [sizeTransform] will be triggered unless it's explicitly set to `null`.
 * [AnimatedContentTransitionScope.slideIntoContainer] and
 * [AnimatedContentTransitionScope.slideOutOfContainer] can
 * provide container-size-aware sliding in from the edge of the container, or sliding out to the
 * edge of the container.
 *
 * [ContentTransform] supports the zIndex definition when the content enters the
 * [AnimatedContent] container via [targetContentZIndex]. By default, all content has a `0f`
 * zIndex. Among content with the same zIndex, the incoming target content will be on top, as it
 * will be placed last. However, this may not always be desired. zIndex can be specified to change
 * that order. The content with higher zIndex guarantee to be placed on top of content with lower
 * zIndex.
 *
 * [sizeTransform] manages the expanding and shrinking of the container if there is any size
 * change as new content enters the [AnimatedContent] and old content leaves. Unlike
 * [AnimatedVisibility], for [AnimatedContent] it is generally
 * more predictable to manage the size of the container using [SizeTransform] than influencing the
 * size using [expandIn]/[expandHorizontally]/[shrinkOut], etc for each content.
 * By default, [spring] will be used to animate any size change, and [AnimatedContent] will be
 * clipped to the animated size. Both can be customized by supplying a different [SizeTransform].
 * If no size animation is desired, [sizeTransform] can be set to `null`.
 *
 * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
 *
 * @see SizeTransform
 * @see EnterTransition
 * @see ExitTransition
 * @see AnimatedContent
 */
class ContentTransform(
    val targetContentEnter: EnterTransition,
    val initialContentExit: ExitTransition,
    targetContentZIndex: Float = 0f,
    sizeTransform: SizeTransform? = SizeTransform()
) {
    /**
     * This describes the zIndex of the new target content as it enters the container. It defaults
     * to 0f. Content with higher zIndex will be drawn over lower `zIndex`ed content. Among
     * content with the same index, the target content will be placed on top.
     */
    var targetContentZIndex by mutableFloatStateOf(targetContentZIndex)

    /**
     * [sizeTransform] manages the expanding and shrinking of the container if there is any size
     * change as new content enters the [AnimatedContent] and old content leaves.
     * By default, [spring] will be used to animate any size change, and [AnimatedContent] will be
     * clipped to the animated size. Both can be customized by supplying a different [SizeTransform].
     * If no size animation is desired, [sizeTransform] can be set to `null`.
     */
    var sizeTransform: SizeTransform? = sizeTransform
        internal set
}

/**
 * This creates a [SizeTransform] with the provided [clip] and [sizeAnimationSpec]. By default,
 * [clip] will be true. This means during the size animation, the content will be clipped to the
 * animated size. [sizeAnimationSpec] defaults to return a [spring] animation.
 *
 * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
 */
fun SizeTransform(
    clip: Boolean = true,
    sizeAnimationSpec: (initialSize: IntSize, targetSize: IntSize) -> FiniteAnimationSpec<IntSize> =
        { _, _ ->
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntSize.VisibilityThreshold
            )
        }
): SizeTransform = SizeTransformImpl(clip, sizeAnimationSpec)

/**
 * [SizeTransform] defines how to transform from one size to another when the size of the content
 * changes. When [clip] is true, the content will be clipped to the animation size.
 * [createAnimationSpec] specifies the animation spec for the size animation based on the initial
 * and target size.
 *
 * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
 */
interface SizeTransform {
    /**
     * Whether the content should be clipped using the animated size.
     */
    val clip: Boolean

    /**
     * This allows [FiniteAnimationSpec] to be defined based on the [initialSize] before the size
     * animation and the [targetSize] of the animation.
     */
    fun createAnimationSpec(initialSize: IntSize, targetSize: IntSize): FiniteAnimationSpec<IntSize>
}

/**
 * Private implementation of SizeTransform interface.
 */
private class SizeTransformImpl(
    override val clip: Boolean = true,
    val sizeAnimationSpec:
        (initialSize: IntSize, targetSize: IntSize) -> FiniteAnimationSpec<IntSize>
) : SizeTransform {
    override fun createAnimationSpec(
        initialSize: IntSize,
        targetSize: IntSize
    ): FiniteAnimationSpec<IntSize> = sizeAnimationSpec(initialSize, targetSize)
}

/**
 * This creates a [ContentTransform] using the provided [EnterTransition] and [exit], where the
 * enter and exit transition will be running simultaneously.
 * For example:
 *
 * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
 */
infix fun EnterTransition.togetherWith(exit: ExitTransition) = ContentTransform(this, exit)

@ExperimentalAnimationApi
@Deprecated(
    "Infix fun EnterTransition.with(ExitTransition) has been renamed to" +
        " togetherWith", ReplaceWith("togetherWith(exit)")
)
infix fun EnterTransition.with(exit: ExitTransition) = ContentTransform(this, exit)

/**
 * [AnimatedContentTransitionScope] provides functions that are convenient and only applicable in the
 * context of [AnimatedContent], such as [slideIntoContainer] and [slideOutOfContainer].
 */
sealed interface AnimatedContentTransitionScope<S> : Transition.Segment<S> {
    /**
     * Customizes the [SizeTransform] of a given [ContentTransform]. For example:
     *
     * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
     */
    infix fun ContentTransform.using(sizeTransform: SizeTransform?): ContentTransform

    /**
     * [SlideDirection] defines the direction of the slide in/out for [slideIntoContainer] and
     * [slideOutOfContainer]. The supported directions are: [Left], [Right], [Up] and [Down].
     */
    @Immutable
    @kotlin.jvm.JvmInline
    value class SlideDirection internal constructor(private val value: Int) {
        companion object {
            val Left = SlideDirection(0)
            val Right = SlideDirection(1)
            val Up = SlideDirection(2)
            val Down = SlideDirection(3)
            val Start = SlideDirection(4)
            val End = SlideDirection(5)
        }

        override fun toString(): String {
            return when (this) {
                Left -> "Left"
                Right -> "Right"
                Up -> "Up"
                Down -> "Down"
                Start -> "Start"
                End -> "End"
                else -> "Invalid"
            }
        }
    }

    /**
     * This defines a horizontal/vertical slide-in that is specific to [AnimatedContent] from the
     * edge of the container. The offset amount is dynamically calculated based on the current
     * size of the [AnimatedContent] and its content alignment. This offset (may be positive or
     * negative based on the direction of the slide) is then passed to [initialOffset]. By default,
     * [initialOffset] will be using the offset calculated from the system to slide the content in.
     * [slideIntoContainer] is a convenient alternative to [slideInHorizontally] and
     * [slideInVertically] when the incoming and outgoing content
     * differ in size. Otherwise, it would be equivalent to [slideInHorizontally] and
     * [slideInVertically] with an offset of the full width/height.
     *
     * [towards] specifies the slide direction. Content can be slided into the container towards
     * [SlideDirection.Left], [SlideDirection.Right], [SlideDirection.Up] and [SlideDirection.Down].
     *
     * [animationSpec] defines the animation that will be used to animate the slide-in.
     *
     * @sample androidx.compose.animation.samples.SlideIntoContainerSample
     *
     * @see AnimatedContent
     * @see slideInHorizontally
     * @see slideInVertically
     */
    fun slideIntoContainer(
        towards: SlideDirection,
        animationSpec: FiniteAnimationSpec<IntOffset> = spring(
            visibilityThreshold = IntOffset.VisibilityThreshold
        ),
        initialOffset: (offsetForFullSlide: Int) -> Int = { it }
    ): EnterTransition

    /**
     * This defines a horizontal/vertical exit transition to completely slide out of the
     * [AnimatedContent] container. The offset amount is dynamically calculated based on the current
     * size of the [AnimatedContent] and the new target size. This offset gets passed
     * to [targetOffset] lambda. By default, [targetOffset] uses this offset as is, but it can be
     * customized to slide a distance based on the offset. [slideOutOfContainer] is a
     * convenient alternative to [slideOutHorizontally] and [slideOutVertically] when the incoming
     * and outgoing content differ in size. Otherwise, it would be equivalent to
     * [slideOutHorizontally] and [slideOutVertically] with an offset of the full width/height.
     *
     * [towards] specifies the slide direction. Content can be slided out of the container towards
     * [SlideDirection.Left], [SlideDirection.Right], [SlideDirection.Up] and [SlideDirection.Down].
     *
     * [animationSpec] defines the animation that will be used to animate the slide-out.
     *
     * @sample androidx.compose.animation.samples.SlideIntoContainerSample
     *
     * @see AnimatedContent
     * @see slideOutHorizontally
     * @see slideOutVertically
     */
    fun slideOutOfContainer(
        towards: SlideDirection,
        animationSpec: FiniteAnimationSpec<IntOffset> = spring(
            visibilityThreshold = IntOffset.VisibilityThreshold
        ),
        targetOffset: (offsetForFullSlide: Int) -> Int = { it }
    ): ExitTransition

    /**
     * [ExitTransition.Hold] defers the disposal of the exiting content till both enter and
     * exit animations have finished. It can be combined with other [ExitTransition]s using
     * [+][ExitTransition.plus].
     *
     * **Important**: [ExitTransition.Hold] works the best when the
     * [zIndex][ContentTransform.targetContentZIndex] for the incoming and outgoing content are
     * specified. Otherwise, if the content gets interrupted from entering and switching to exiting
     * using [ExitTransition.Hold], the holding pattern may render exiting content on top of the
     * entering content, unless the z-order is specified.
     *
     * @sample androidx.compose.animation.samples.SlideIntoContainerSample
     */
    val ExitTransition.Companion.Hold: ExitTransition get() = Hold

    /**
     * This returns the [Alignment] specified on [AnimatedContent].
     */
    val contentAlignment: Alignment

    /**
     * [scaleInToFitContainer] defines an [EnterTransition] that scales the incoming content
     * based on the (potentially animating) container (i.e. [AnimatedContent]) size. [contentScale]
     * defines the scaling function. By default, the incoming content will be scaled based on its
     * width (i.e. [ContentScale.FillWidth]), so that the content fills the container's width.
     * [alignment] can be used to specify the alignment of the scaled content
     * within the container of AnimatedContent.
     *
     * [scaleInToFitContainer] will measure the content using the final (i.e. lookahead)
     * constraints, in order to obtain the final layout and apply scaling to that final layout
     * while the container is resizing.
     *
     * @sample androidx.compose.animation.samples.ScaleInToFitContainerSample
     */
    @ExperimentalAnimationApi
    fun scaleInToFitContainer(
        alignment: Alignment = contentAlignment,
        contentScale: ContentScale = ContentScale.FillWidth
    ): EnterTransition

    /**
     * [scaleOutToFitContainer] defines an [ExitTransition] that scales the outgoing content
     * based on the (potentially animating) container (i.e. [AnimatedContent]) size.
     * [contentScale] defines the scaling function. By default, the outgoing content will be scaled
     * using [ContentScale.FillWidth], so that it fits the container's width.
     * [alignment] can be used to specify the alignment of the scaled content
     * within the container of AnimatedContent.
     *
     * [scaleOutToFitContainer] will measure the content using the constraints cached
     * at the beginning of the exit animation so that the content does not get re-laid out during
     * the exit animation, and instead only scaling will be applied as the container resizes.
     *
     * **IMPORTANT**: [scaleOutToFitContainer] does NOT keep the exiting content from being
     * disposed. Therefore it relies on other ExitTransitions such as [fadeOut] to define a
     * timeframe for when should be active.
     *
     * @sample androidx.compose.animation.samples.ScaleInToFitContainerSample
     */
    @ExperimentalAnimationApi
    fun scaleOutToFitContainer(
        alignment: Alignment = contentAlignment,
        contentScale: ContentScale = ContentScale.FillWidth,
    ): ExitTransition
}

internal class AnimatedContentRootScope<S> internal constructor(
    internal val transition: Transition<S>,
    lookaheadScope: LookaheadScope,
    internal val coroutineScope: CoroutineScope,
    override var contentAlignment: Alignment,
    internal var layoutDirection: LayoutDirection
) : AnimatedContentTransitionScope<S>, LookaheadScope by lookaheadScope {
    lateinit var rootCoords: LayoutCoordinates
    lateinit var rootLookaheadCoords: LayoutCoordinates

    /**
     * Initial state of a Transition Segment. This is the state that transition starts from.
     */
    override val initialState: S
        @Suppress("UnknownNullness")
        get() = transition.segment.initialState

    /**
     * Target state of a Transition Segment. This is the state that transition will end on.
     */
    override val targetState: S
        @Suppress("UnknownNullness")
        get() = transition.segment.targetState

    /**
     * Customizes the [SizeTransform] of a given [ContentTransform]. For example:
     *
     * @sample androidx.compose.animation.samples.AnimatedContentTransitionSpecSample
     */
    override infix fun ContentTransform.using(sizeTransform: SizeTransform?) = this.apply {
        this.sizeTransform = sizeTransform
    }

    /**
     * This defines a horizontal/vertical slide-in that is specific to [AnimatedContent] from the
     * edge of the container. The offset amount is dynamically calculated based on the current
     * size of the [AnimatedContent] and its content alignment. This offset (may be positive or
     * negative based on the direction of the slide) is then passed to [initialOffset]. By default,
     * [initialOffset] will be using the offset calculated from the system to slide the content in.
     * [slideIntoContainer] is a convenient alternative to [slideInHorizontally] and
     * [slideInVertically] when the incoming and outgoing content
     * differ in size. Otherwise, it would be equivalent to [slideInHorizontally] and
     * [slideInVertically] with an offset of the full width/height.
     *
     * [towards] specifies the slide direction. Content can be slided into the container towards
     * [AnimatedContentTransitionScope.SlideDirection.Left],
     * [AnimatedContentTransitionScope.SlideDirection.Right],
     * [AnimatedContentTransitionScope.SlideDirection.Up]
     * and [AnimatedContentTransitionScope.SlideDirection.Down].
     *
     * [animationSpec] defines the animation that will be used to animate the slide-in.
     *
     * @sample androidx.compose.animation.samples.SlideIntoContainerSample
     *
     * @see AnimatedContent
     * @see slideInHorizontally
     * @see slideInVertically
     */
    override fun slideIntoContainer(
        towards: AnimatedContentTransitionScope.SlideDirection,
        animationSpec: FiniteAnimationSpec<IntOffset>,
        initialOffset: (offsetForFullSlide: Int) -> Int
    ): EnterTransition =
        when {
            towards.isLeft -> slideInHorizontally(animationSpec) {
                initialOffset.invoke(
                    currentSize.width - calculateOffset(IntSize(it, it), currentSize).x
                )
            }

            towards.isRight -> slideInHorizontally(animationSpec) {
                initialOffset.invoke(-calculateOffset(IntSize(it, it), currentSize).x - it)
            }

            towards == Up -> slideInVertically(animationSpec) {
                initialOffset.invoke(
                    currentSize.height - calculateOffset(IntSize(it, it), currentSize).y
                )
            }

            towards == Down -> slideInVertically(animationSpec) {
                initialOffset.invoke(-calculateOffset(IntSize(it, it), currentSize).y - it)
            }

            else -> EnterTransition.None
        }

    private val AnimatedContentTransitionScope.SlideDirection.isLeft: Boolean
        get() {
            return this == Left || this == Start && layoutDirection == LayoutDirection.Ltr ||
                this == End && layoutDirection == LayoutDirection.Rtl
        }
    private val AnimatedContentTransitionScope.SlideDirection.isRight: Boolean
        get() {
            return this == Right || this == Start && layoutDirection == LayoutDirection.Rtl ||
                this == End && layoutDirection == LayoutDirection.Ltr
        }

    private fun calculateOffset(fullSize: IntSize, currentSize: IntSize): IntOffset {
        return contentAlignment.align(fullSize, currentSize, LayoutDirection.Ltr)
    }

    /**
     * This defines a horizontal/vertical exit transition to completely slide out of the
     * [AnimatedContent] container. The offset amount is dynamically calculated based on the current
     * size of the [AnimatedContent] and the new target size. This offset gets passed
     * to [targetOffset] lambda. By default, [targetOffset] uses this offset as is, but it can be
     * customized to slide a distance based on the offset. [slideOutOfContainer] is a
     * convenient alternative to [slideOutHorizontally] and [slideOutVertically] when the incoming
     * and outgoing content differ in size. Otherwise, it would be equivalent to
     * [slideOutHorizontally] and [slideOutVertically] with an offset of the full width/height.
     *
     * [towards] specifies the slide direction. Content can be slided out of the container towards
     * [AnimatedContentTransitionScope.SlideDirection.Left],
     * [AnimatedContentTransitionScope.SlideDirection.Right],
     * [AnimatedContentTransitionScope.SlideDirection.Up]
     * and [AnimatedContentTransitionScope.SlideDirection.Down].
     *
     * [animationSpec] defines the animation that will be used to animate the slide-out.
     *
     * @sample androidx.compose.animation.samples.SlideIntoContainerSample
     *
     * @see AnimatedContent
     * @see slideOutHorizontally
     * @see slideOutVertically
     */
    override fun slideOutOfContainer(
        towards: AnimatedContentTransitionScope.SlideDirection,
        animationSpec: FiniteAnimationSpec<IntOffset>,
        targetOffset: (offsetForFullSlide: Int) -> Int
    ): ExitTransition {
        return when {
            // Note: targetSize could be 0 for empty composables
            towards.isLeft -> slideOutHorizontally(animationSpec) {
                val targetSize = targetSizeMap[transition.targetState]?.value ?: IntSize.Zero
                targetOffset.invoke(-calculateOffset(IntSize(it, it), targetSize).x - it)
            }

            towards.isRight -> slideOutHorizontally(animationSpec) {
                val targetSize = targetSizeMap[transition.targetState]?.value ?: IntSize.Zero
                targetOffset.invoke(
                    -calculateOffset(IntSize(it, it), targetSize).x + targetSize.width
                )
            }

            towards == Up -> slideOutVertically(animationSpec) {
                val targetSize = targetSizeMap[transition.targetState]?.value ?: IntSize.Zero
                targetOffset.invoke(-calculateOffset(IntSize(it, it), targetSize).y - it)
            }

            towards == Down -> slideOutVertically(animationSpec) {
                val targetSize = targetSizeMap[transition.targetState]?.value ?: IntSize.Zero
                targetOffset.invoke(
                    -calculateOffset(IntSize(it, it), targetSize).y + targetSize.height
                )
            }

            else -> ExitTransition.None
        }
    }

    @ExperimentalAnimationApi
    override fun scaleInToFitContainer(
        alignment: Alignment,
        contentScale: ContentScale
    ): EnterTransition = EnterTransition(
        ScaleToFitTransitionKey, ScaleToFitInLookaheadElement(
            this@AnimatedContentRootScope,
            contentScale,
            alignment
        )
    )

    @ExperimentalAnimationApi
    override fun scaleOutToFitContainer(
        alignment: Alignment,
        contentScale: ContentScale
    ): ExitTransition = ExitTransition(
        ScaleToFitTransitionKey,
        ScaleToFitInLookaheadElement(
            this@AnimatedContentRootScope,
            contentScale,
            alignment
        )
    )

    internal var measuredSize: IntSize by mutableStateOf(IntSize.Zero)
    internal val targetSizeMap = mutableMapOf<S, MutableState<IntSize>>()
    internal var animatedSize: State<IntSize>? = null

    // Current size of the container. If there's any size animation, the current size will be
    // read from the animation value, otherwise we'll use the current
    internal val currentSize: IntSize
        get() = animatedSize?.value ?: measuredSize

    internal val targetSize: IntSize
        get() = requireNotNull(targetSizeMap[targetState]) {
            "Error: Target size for AnimatedContent has not been set."
        }.value

    @Suppress("ComposableModifierFactory", "ModifierFactoryExtensionFunction")
    @Composable
    internal fun createSizeAnimationModifier(
        contentTransform: ContentTransform
    ): Modifier {
        var shouldAnimateSize by remember(this) { mutableStateOf(false) }
        val sizeTransform = rememberUpdatedState(contentTransform.sizeTransform)
        if (transition.currentState == transition.targetState) {
            shouldAnimateSize = false
        } else if (sizeTransform.value != null) {
            shouldAnimateSize = true
        }

        return if (shouldAnimateSize) {
            val sizeAnimation =
                transition.createDeferredAnimation(IntSize.VectorConverter, "sizeTransform")
            remember(sizeAnimation) {
                (if (sizeTransform.value?.clip == false) Modifier else Modifier.clipToBounds())
                    .then(
                        SizeModifierInLookaheadElement(
                            this, sizeAnimation, sizeTransform
                        )
                    )
            }
        } else {
            animatedSize = null
            Modifier
        }
    }

    // This helps track the target measurable without affecting the placement order. Target
    // measurable needs to be measured first but placed last.
    internal data class ChildData<T>(var targetState: T) : ParentDataModifier {
        override fun Density.modifyParentData(parentData: Any?): Any {
            return this@ChildData
        }
    }
}

/**
 * Receiver scope for content lambda for AnimatedContent. In this scope,
 * [transition][AnimatedVisibilityScope.transition] can be used to observe the state of the
 * transition, or to add more enter/exit transition for the content.
 */
sealed interface AnimatedContentScope : AnimatedVisibilityScope

private class AnimatedContentScopeImpl internal constructor(
    animatedVisibilityScope: AnimatedVisibilityScope
) : AnimatedContentScope, AnimatedVisibilityScope by animatedVisibilityScope

/**
 * [AnimatedContent] is a container that automatically animates its content when
 * [Transition.targetState] changes. Its [content] for different target states is defined in a
 * mapping between a target state and a composable function.
 *
 * **IMPORTANT**: The targetState parameter for the [content] lambda should *always* be
 * taken into account in deciding what composable function to return as the content for that state.
 * This is critical to ensure a successful lookup of all the incoming and outgoing content during
 * content transform.
 *
 * When [Transition.targetState] changes, content for both new and previous targetState will be
 * looked up through the [content] lambda. They will go through a [ContentTransform] so that
 * the new target content can be animated in while the initial content animates out. Meanwhile the
 * container will animate its size as needed to accommodate the new content, unless
 * [SizeTransform] is set to `null`. Once the [ContentTransform] is finished, the
 * outgoing content will be disposed.
 *
 * If [Transition.targetState] is expected to mutate frequently and not all mutations should be
 * treated as target state change, consider defining a mapping between [Transition.targetState]
 * and a key in [contentKey]. As a result, transitions will be triggered when the resulting key
 * changes. In other words, there will be no animation when switching between
 * [Transition.targetState]s that share the same same key. By default, the key will be the same as
 * the targetState object.
 *
 * By default, the [ContentTransform] will be a delayed [fadeIn] of the target content and a delayed
 * [scaleIn] [togetherWith] a [fadeOut] of the initial content, using a [SizeTransform] to
 * animate any size change of the content. This behavior can be customized using [transitionSpec].
 * If desired, different [ContentTransform]s can be defined for different pairs of initial content
 * and target content.
 *
 * [AnimatedContent] displays only the content for [Transition.targetState] when not animating.
 * However, during the transient content transform, there will be more than one sets of content
 * present in the [AnimatedContent] container. It may be sometimes desired to define the positional
 * relationship among different content and the style of overlap. This can be achieved by defining
 * [contentAlignment] and [zOrder][ContentTransform.targetContentZIndex]. By default,
 * [contentAlignment] aligns all content to [Alignment.TopStart], and the `zIndex` for all
 * the content is 0f. __Note__: The target content will always be placed last, therefore it will be
 * on top of all the other content unless zIndex is specified.
 *
 * Different content in [AnimatedContent] will have access to their own
 * [AnimatedContentScope]. This allows content to define more local enter/exit transitions
 * via [AnimatedContentScope.animateEnterExit] and [AnimatedContentScope.transition]. These
 * custom enter/exit animations will be triggered as the content enters/leaves the container.
 *
 * @sample androidx.compose.animation.samples.TransitionExtensionAnimatedContentSample
 *
 * @see ContentTransform
 * @see AnimatedContentScope
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <S> Transition<S>.AnimatedContent(
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
            .togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable() AnimatedContentScope.(targetState: S) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    LookaheadScope {
        val rootScope = remember(this@AnimatedContent) {
            AnimatedContentRootScope(
                this@AnimatedContent, this@LookaheadScope,
                coroutineScope, contentAlignment, layoutDirection
            )
        }
        val currentlyVisible = remember(this) { mutableStateListOf(currentState) }
        val contentMap = remember(this) { mutableMapOf<S, @Composable() () -> Unit>() }
        val constraintsMap = remember { mutableMapOf<S, Constraints>() }

        // This is needed for tooling because it could change currentState directly,
        // as opposed to changing target only. When that happens we need to clear all the
        // visible content and only display the content for the new current state and target state.
        if (!currentlyVisible.contains(currentState)) {
            currentlyVisible.clear()
            currentlyVisible.add(currentState)
        }

        if (currentState == targetState) {
            if (currentlyVisible.size != 1 || currentlyVisible[0] != currentState) {
                currentlyVisible.clear()
                currentlyVisible.add(currentState)
            }
            if (contentMap.size != 1 || contentMap.containsKey(currentState)) {
                contentMap.clear()
            }
            val targetConstraints = constraintsMap[targetState]
            constraintsMap.clear()
            targetConstraints?.let { constraintsMap[targetState] = it }
            // TODO: Do we want to support changing contentAlignment amid animation?
            rootScope.contentAlignment = contentAlignment
            rootScope.layoutDirection = layoutDirection
        } else if (!currentlyVisible.contains(targetState)) {
            // Currently visible list always keeps the targetState at the end of the list, unless
            // it's already in the list in the case of interruption. This makes the composable
            // associated with the targetState get placed last, so the target composable will be
            // displayed on top of content associated with other states, unless zIndex is specified.
            // Replace the target with the same key if any.
            val id = currentlyVisible.indexOfFirst { contentKey(it) == contentKey(targetState) }
            if (id == -1) {
                currentlyVisible.add(targetState)
            } else {
                currentlyVisible[id] = targetState
            }
        }
        if (!contentMap.containsKey(targetState) || !contentMap.containsKey(currentState)) {
            contentMap.clear()
            val enter = transitionSpec(rootScope).targetContentEnter
            val exit = rootScope.transitionSpec().initialContentExit
            val zIndex = transitionSpec(rootScope).targetContentZIndex
            currentlyVisible.fastForEach { stateForContent ->
                contentMap[stateForContent] = {
                    PopulateContentFor(
                        stateForContent, rootScope, enter, exit, zIndex, currentlyVisible, content
                    )
                }
            }
        }
        val contentTransform = remember(rootScope, segment) { transitionSpec(rootScope) }
        val sizeModifier = rootScope.createSizeAnimationModifier(contentTransform)
        Layout(
            modifier = modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        coordinates?.let {
                            if (isLookingAhead) {
                                rootScope.rootLookaheadCoords = it
                            } else {
                                rootScope.rootCoords = it
                            }
                        }
                        placeable.place(0, 0)
                    }
                }
                .then(sizeModifier),
            content = {
                currentlyVisible.forEach {
                    key(contentKey(it)) { contentMap[it]?.invoke() }
                }
            },
            measurePolicy = remember {
                AnimatedContentMeasurePolicy(
                    rootScope, constraintsMap
                )
            }
        )
    }
}

/**
 * Creates content for a specific state based on the current Transition, enter/exit and the content
 * lookup lambda.
 */
@Composable
private inline fun <S> Transition<S>.PopulateContentFor(
    stateForContent: S,
    rootScope: AnimatedContentRootScope<S>,
    enter: EnterTransition,
    exit: ExitTransition,
    zIndex: Float,
    currentlyVisible: SnapshotStateList<S>,
    crossinline content: @Composable() AnimatedContentScope.(targetState: S) -> Unit
) {
    var activeEnter by remember { mutableStateOf(enter) }
    var activeExit by remember { mutableStateOf(ExitTransition.None) }
    val targetZIndex = remember { zIndex }

    val isEntering = targetState == stateForContent
    if (targetState == currentState) {
        // Transition finished, reset active enter & exit.
        activeEnter = androidx.compose.animation.EnterTransition.None
        activeExit = androidx.compose.animation.ExitTransition.None
    } else if (isEntering) {
        // If the previous enter transition never finishes when multiple
        // interruptions happen, avoid adding new enter transitions for simplicity.
        if (activeEnter == androidx.compose.animation.EnterTransition.None)
            activeEnter += enter
    } else {
        // If the previous exit transition never finishes when multiple
        // interruptions happen, avoid adding new enter transitions for simplicity.
        if (activeExit == androidx.compose.animation.ExitTransition.None) {
            activeExit += exit
        }
    }
    val childData = remember { AnimatedContentRootScope.ChildData(stateForContent) }
    AnimatedEnterExitImpl(
        this,
        { it == stateForContent },
        enter = activeEnter,
        exit = activeExit,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0, zIndex = targetZIndex)
                }
            }
            .then(childData)
            .then(
                if (isEntering) {
                    activeEnter[ScaleToFitTransitionKey]
                        ?: activeExit[ScaleToFitTransitionKey] ?: androidx.compose.ui.Modifier
                } else {
                    activeExit[ScaleToFitTransitionKey]
                        ?: activeEnter[ScaleToFitTransitionKey] ?: androidx.compose.ui.Modifier
                }
            ),
        shouldDisposeBlock = { currentState, targetState ->
            currentState == androidx.compose.animation.EnterExitState.PostExit &&
                targetState == androidx.compose.animation.EnterExitState.PostExit &&
                !activeExit.data.hold
        },
        onLookaheadMeasured = {
            if (isEntering) rootScope.targetSizeMap.getOrPut(targetState) {
                mutableStateOf(it)
            }.value = it
        }
    ) {
        // TODO: Should Transition.AnimatedVisibility have an end listener?
        DisposableEffect(this) {
            onDispose {
                currentlyVisible.remove(stateForContent)
                rootScope.targetSizeMap.remove(stateForContent)
            }
        }
        with(remember { AnimatedContentScopeImpl(this) }) {
            content(stateForContent)
        }
    }
}

/**
 * This measure policy returns the target content size in the lookahead pass, and the max width
 * and height needed for all contents to fit during the main measure pass.
 *
 * The measure policy will measure all children with lookahead constraints. For outgoing content,
 * we will use the constraints recorded before the content started to exit. This enables the
 * outgoing content to not change constraints on its way out.
 */
@Suppress("UNCHECKED_CAST")
private class AnimatedContentMeasurePolicy<S>(
    val rootScope: AnimatedContentRootScope<S>,
    val constraintsMap: MutableMap<S, Constraints>
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        // Measure the target composable first (but place it on top unless zIndex is specified)
        val targetState = rootScope.targetState
        measurables.fastForEachIndexed { index, measurable ->
            if ((measurable.parentData as? AnimatedContentRootScope.ChildData<*>)
                    ?.targetState == targetState
            ) {
                // Record lookahead constraints and always use it to measure target content.
                val lookaheadConstraints = if (isLookingAhead) {
                    constraintsMap[targetState] = constraints
                    constraints
                } else {
                    requireNotNull(constraintsMap[targetState]) {
                        "Lookahead pass was never done for target content."
                    }
                }
                placeables[index] = measurable.measure(lookaheadConstraints)
            }
        }
        // If no content is defined for target state, set the target size to zero
        rootScope.targetSizeMap.getOrPut(targetState) { mutableStateOf(IntSize.Zero) }

        val initialState = rootScope.initialState
        // Measure the non-target composables after target, since these have no impact on
        // container size in the size animation.
        measurables.fastForEachIndexed { index, measurable ->
            val stateForContent =
                (measurable.parentData as? AnimatedContentRootScope.ChildData<*>)
                    ?.targetState
            if (placeables[index] == null) {
                val lookaheadConstraints =
                    constraintsMap[stateForContent] ?: if (isLookingAhead) {
                        constraintsMap[stateForContent as S] = constraints
                        constraints
                    } else {
                        requireNotNull(constraintsMap[stateForContent as S]) {
                            "Error: Lookahead pass never happened for state: $stateForContent"
                        }
                    }
                placeables[index] = measurable.measure(lookaheadConstraints).also {
                    // If the initial state size isn't in the map, add it. This could be possible
                    // when the initial state is specified to be different than target state upon
                    // entering composition.
                    if (stateForContent == initialState &&
                        isLookingAhead &&
                        !rootScope.targetSizeMap.containsKey(initialState)
                    ) {
                        rootScope.targetSizeMap[initialState] =
                            mutableStateOf(IntSize(it.width, it.height))
                    }
                }
            }
        }
        val lookaheadSize = rootScope.targetSizeMap[targetState]!!.value
        val measuredWidth = if (isLookingAhead) {
            lookaheadSize.width
        } else {
            placeables.maxByOrNull { it?.width ?: 0 }?.width ?: 0
        }
        val measuredHeight = if (isLookingAhead) {
            lookaheadSize.height
        } else {
            placeables.maxByOrNull { it?.height ?: 0 }?.height ?: 0
        }
        rootScope.measuredSize = IntSize(measuredWidth, measuredHeight)
        // Position the children.
        return layout(measuredWidth, measuredHeight) {
            placeables.forEach { placeable ->
                placeable?.let {
                    val offset = rootScope.contentAlignment.align(
                        IntSize(it.width, it.height),
                        IntSize(measuredWidth, measuredHeight),
                        LayoutDirection.Ltr
                    )
                    it.place(offset.x, offset.y)
                }
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ) = measurables.asSequence().map { it.minIntrinsicWidth(height) }.maxOrNull() ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ) = measurables.asSequence().map { it.minIntrinsicHeight(width) }.maxOrNull() ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ) = measurables.asSequence().map { it.maxIntrinsicWidth(height) }.maxOrNull() ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ) = measurables.asSequence().map { it.maxIntrinsicHeight(width) }.maxOrNull() ?: 0
}

private class SizeModifierInLookaheadNode<S>(
    var rootScope: AnimatedContentRootScope<S>,
    var sizeAnimation: Transition<S>.DeferredAnimation<IntSize, AnimationVector2D>,
    var sizeTransform: State<SizeTransform?>,
) : LayoutModifierNodeWithPassThroughIntrinsics() {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val size = if (isLookingAhead) {
            val targetSize = IntSize(placeable.width, placeable.height)
            // lookahead pass
            rootScope.animatedSize = sizeAnimation.animate(
                transitionSpec = {
                    val initial = rootScope.targetSizeMap[initialState]?.value ?: IntSize.Zero
                    val target = rootScope.targetSizeMap[targetState]?.value ?: IntSize.Zero
                    sizeTransform.value?.createAnimationSpec(initial, target) ?: spring()
                }
            ) {
                rootScope.targetSizeMap[it]?.value ?: IntSize.Zero
            }
            targetSize
        } else {
            rootScope.animatedSize!!.value
        }
        val offset = rootScope.contentAlignment.align(
            IntSize(placeable.width, placeable.height), size, LayoutDirection.Ltr
        )
        return layout(size.width, size.height) {
            placeable.place(offset)
        }
    }
}

private data class SizeModifierInLookaheadElement<S>(
    val rootScope: AnimatedContentRootScope<S>,
    val sizeAnimation: Transition<S>.DeferredAnimation<IntSize, AnimationVector2D>,
    val sizeTransform: State<SizeTransform?>,
) : ModifierNodeElement<SizeModifierInLookaheadNode<S>>() {
    override fun create(): SizeModifierInLookaheadNode<S> {
        return SizeModifierInLookaheadNode(rootScope, sizeAnimation, sizeTransform)
    }

    override fun update(node: SizeModifierInLookaheadNode<S>) {
        node.rootScope = rootScope
        node.sizeTransform = sizeTransform
        node.sizeAnimation = sizeAnimation
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "sizeTransform"
        properties["sizeTransform"] = sizeTransform
        properties["sizeAnimation"] = sizeAnimation
    }
}

private data class ScaleToFitInLookaheadElement(
    val rootScope: AnimatedContentRootScope<*>,
    val contentScale: ContentScale,
    val alignment: Alignment
) : ModifierNodeElement<ScaleToFitInLookaheadNode>() {
    override fun create(): ScaleToFitInLookaheadNode =
        ScaleToFitInLookaheadNode(rootScope, contentScale, alignment)

    override fun update(node: ScaleToFitInLookaheadNode) {
        node.rootScope = rootScope
        node.contentScale = contentScale
        node.alignment = alignment
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scaleToFit"
        properties["rootScope"] = rootScope
        properties["scale"] = contentScale
        properties["alignment"] = alignment
    }
}

/**
 * Creates a Modifier Node to: 1) measure the layout with lookahead constraints, 2) scale the
 * resulting (potentially unfitting) layout based on the resizing container using the given
 * [contentScale] lambda.
 *
 * This node is designed to work in a lookahead scope, therefore it anticipates lookahead pass
 * before actual measure pass.
 */
private class ScaleToFitInLookaheadNode(
    var rootScope: AnimatedContentRootScope<*>,
    var contentScale: ContentScale,
    var alignment: Alignment
) : Modifier.Node(), LayoutModifierNode {
    private var lookaheadConstraints: Constraints = Constraints()
        set(value) {
            lookaheadPassOccurred = true
            field = value
        }
        get() {
            require(lookaheadPassOccurred) {
                "Error: Attempting to read lookahead constraints before lookahead pass."
            }
            return field
        }
    private var lookaheadPassOccurred = false

    override fun onDetach() {
        super.onDetach()
        lookaheadPassOccurred = false
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        if (isLookingAhead) lookaheadConstraints = constraints
        // Measure with lookahead constraints.
        val placeable = measurable.measure(lookaheadConstraints)
        val contentSize = IntSize(placeable.width, placeable.height)
        val sizeToReport = if (isLookingAhead) {
            // report size of the target content, as that's what the content will be scaled to.
            rootScope.targetSize
        } else {
            // report current animated size && scale based on that and full size
            rootScope.currentSize
        }
        val resolvedScale =
            if (contentSize.width == 0 || contentSize.height == 0) {
                ScaleFactor(1f, 1f)
            } else
                contentScale.computeScaleFactor(contentSize.toSize(), sizeToReport.toSize())
        return layout(sizeToReport.width, sizeToReport.height) {
            val (x, y) = alignment.align(
                IntSize(
                    (contentSize.width * resolvedScale.scaleX).roundToInt(),
                    (contentSize.height * resolvedScale.scaleY).roundToInt()
                ),
                sizeToReport,
                layoutDirection
            )
            placeable.placeWithLayer(x, y) {
                scaleX = resolvedScale.scaleX
                scaleY = resolvedScale.scaleY
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * Fixed key to read customization out of EnterTransition and ExitTransition.
 */
private val ScaleToFitTransitionKey = Any()
