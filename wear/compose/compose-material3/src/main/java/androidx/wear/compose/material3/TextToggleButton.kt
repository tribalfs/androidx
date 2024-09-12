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

package androidx.wear.compose.material3

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.wear.compose.material3.tokens.MotionTokens
import androidx.wear.compose.material3.tokens.ShapeTokens
import androidx.wear.compose.material3.tokens.TextToggleButtonTokens
import androidx.wear.compose.materialcore.animateSelectionColor

/**
 * Wear Material [TextToggleButton] is a filled text toggle button which switches between primary
 * colors and tonal colors depending on [checked] value, and offers a single slot for text.
 *
 * Set the size of the [TextToggleButton] with Modifier.[touchTargetAwareSize] to ensure that the
 * background padding will correctly reach the edge of the minimum touch target. The recommended
 * [TextToggleButton] sizes are [TextToggleButtonDefaults.DefaultButtonSize],
 * [TextToggleButtonDefaults.LargeButtonSize] and [TextToggleButtonDefaults.ExtraLargeButtonSize].
 * The recommended text styles for each corresponding button size are
 * [TextToggleButtonDefaults.defaultButtonTextStyle],
 * [TextToggleButtonDefaults.largeButtonTextStyle] and
 * [TextToggleButtonDefaults.extraLargeButtonTextStyle].
 *
 * [TextToggleButton] can be enabled or disabled. A disabled button will not respond to click
 * events. When enabled, the checked and unchecked events are propagated by [onCheckedChange].
 *
 * A simple text toggle button using the default colors, animated when pressed.
 *
 * @sample androidx.wear.compose.material3.samples.TextToggleButtonSample
 *
 * A simple text toggle button using the default colors, animated when pressed and with different
 * shapes for the checked and unchecked states.
 *
 * @sample androidx.wear.compose.material3.samples.TextToggleButtonVariantSample
 *
 * Example of a large text toggle button:
 *
 * @sample androidx.wear.compose.material3.samples.LargeTextToggleButtonSample
 * @param checked Boolean flag indicating whether this toggle button is currently checked.
 * @param onCheckedChange Callback to be invoked when this toggle button is clicked.
 * @param modifier Modifier to be applied to the toggle button.
 * @param enabled Controls the enabled state of the toggle button. When `false`, this toggle button
 *   will not be clickable.
 * @param colors [TextToggleButtonColors] that will be used to resolve the container and content
 *   color for this toggle button.
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting [Interaction]s for this toggle button. You can use this to change the toggle button's
 *   appearance or preview the toggle button in different states. Note that if `null` is provided,
 *   interactions will still happen internally.
 * @param shape Defines the shape for this toggle button. It is strongly recommended to use the
 *   default as this shape is a key characteristic of the Wear Material 3 Theme.
 * @param border Optional [BorderStroke] for the [TextToggleButton].
 * @param content The text to be drawn inside the toggle button.
 */
@Composable
fun TextToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextToggleButtonColors = TextToggleButtonDefaults.textToggleButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextToggleButtonDefaults.shape,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.wear.compose.materialcore.ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.minimumInteractiveComponentSize(),
        enabled = enabled,
        backgroundColor = { isEnabled, isChecked ->
            colors.containerColor(enabled = isEnabled, checked = isChecked)
        },
        border = { _, _ -> border },
        toggleButtonSize = TextToggleButtonDefaults.DefaultButtonSize,
        interactionSource = interactionSource,
        shape = shape,
        ripple = ripple(),
        content =
            provideScopeContent(
                colors.contentColor(enabled = enabled, checked = checked),
                TextToggleButtonTokens.ContentDefaultFont.value,
                content
            )
    )
}

/** Contains the default values used by [TextToggleButton]. */
object TextToggleButtonDefaults {

    /** Recommended [Shape] for [TextToggleButton]. */
    val shape: RoundedCornerShape
        @Composable get() = ShapeTokens.CornerFull

    /** Recommended pressed [Shape] for [TextToggleButton]. */
    val pressedShape: CornerBasedShape
        @Composable get() = MaterialTheme.shapes.small

    /** The recommended size for an Unchecked button when animated. */
    val UncheckedCornerSize: CornerSize = ShapeTokens.CornerFull.topEnd

    /** The recommended size for a Checked button when animated. */
    val CheckedCornerSize: CornerSize = ShapeDefaults.Medium.topEnd

    /** The recommended size for a Pressed button when animated. */
    val PressedCornerSize: CornerSize = ShapeDefaults.Small.topEnd

    /**
     * The default size applied for text toggle buttons. It is recommended to apply this size using
     * [Modifier.touchTargetAwareSize].
     */
    val DefaultButtonSize = TextToggleButtonTokens.ContainerDefaultSize

    /**
     * The recommended size for a large text toggle button. It is recommended to apply this size
     * using [Modifier.touchTargetAwareSize].
     */
    val LargeButtonSize = TextToggleButtonTokens.ContainerLargeSize

    /**
     * The recommended size for an extra large text toggle button. It is recommended to apply this
     * size using [Modifier.touchTargetAwareSize].
     */
    val ExtraLargeButtonSize = TextToggleButtonTokens.ContainerExtraLargeSize

    /** The default text style applied for text toggle buttons. */
    val defaultButtonTextStyle
        @ReadOnlyComposable @Composable get() = TextToggleButtonTokens.ContentDefaultFont.value

    /** The recommended text style for a large text toggle button. */
    val largeButtonTextStyle
        @ReadOnlyComposable @Composable get() = TextToggleButtonTokens.ContentLargeFont.value

    /** The recommended text style for an extra large text toggle button. */
    val extraLargeButtonTextStyle
        @ReadOnlyComposable @Composable get() = TextToggleButtonTokens.ContentExtraLargeFont.value

    /**
     * Creates a [Shape] with a animation between two CornerBasedShapes based on the pressed state.
     *
     * A simple text toggle button using the default colors, animated when pressed.
     *
     * @sample androidx.wear.compose.material3.samples.TextToggleButtonSample
     * @param interactionSource the interaction source applied to the Button.
     * @param shape The normal shape of the TextToggleButton.
     * @param pressedShape The pressed shape of the TextToggleButton.
     */
    @Composable
    fun animatedShape(
        interactionSource: InteractionSource,
        shape: CornerBasedShape = TextToggleButtonDefaults.shape,
        pressedShape: CornerBasedShape = TextToggleButtonDefaults.pressedShape,
    ) = animatedPressedButtonShape(interactionSource, shape, pressedShape)

    /**
     * Creates a [Shape] with an animation between three [CornerSize]s based on the pressed state
     * and checked/unchecked.
     *
     * A simple text toggle button using the default colors, animated on Press and Check/Uncheck:
     *
     * @sample androidx.wear.compose.material3.samples.TextToggleButtonVariantSample
     * @param interactionSource the interaction source applied to the Button.
     * @param checked the current checked/unchecked state.
     * @param uncheckedCornerSize the size of the corner when unchecked.
     * @param checkedCornerSize the size of the corner when checked.
     * @param pressedCornerSize the size of the corner when pressed.
     * @param onPressAnimationSpec the spec for press animation.
     * @param onReleaseAnimationSpec the spec for release animation.
     */
    @Composable
    fun variantAnimatedShape(
        interactionSource: InteractionSource,
        checked: Boolean,
        uncheckedCornerSize: CornerSize = UncheckedCornerSize,
        checkedCornerSize: CornerSize = CheckedCornerSize,
        pressedCornerSize: CornerSize = PressedCornerSize,
        onPressAnimationSpec: FiniteAnimationSpec<Float> =
            MaterialTheme.motionScheme.rememberFastSpatialSpec(),
        onReleaseAnimationSpec: FiniteAnimationSpec<Float> =
            MaterialTheme.motionScheme.slowSpatialSpec(),
    ): Shape {
        val pressed = interactionSource.collectIsPressedAsState()

        return rememberAnimatedToggleRoundedCornerShape(
            uncheckedCornerSize = uncheckedCornerSize,
            checkedCornerSize = checkedCornerSize,
            pressedCornerSize = pressedCornerSize,
            pressed = pressed.value,
            checked = checked,
            onPressAnimationSpec = onPressAnimationSpec,
            onReleaseAnimationSpec = onReleaseAnimationSpec,
        )
    }

    /**
     * Creates a [TextToggleButtonColors] for a [TextToggleButton]
     * - by default, a colored background with a contrasting content color. If the button is
     *   disabled, then the colors will have an alpha ([DisabledContainerAlpha] or
     *   [DisabledContentAlpha]) value applied.
     */
    @Composable
    fun textToggleButtonColors() = MaterialTheme.colorScheme.defaultTextToggleButtonColors

    /**
     * Creates a [TextToggleButtonColors] for a [TextToggleButton]
     * - by default, a colored background with a contrasting content color. If the button is
     *   disabled, then the colors will have an alpha ([DisabledContainerAlpha] or
     *   [DisabledContentAlpha]) value applied.
     *
     * @param checkedContainerColor the container color of this [TextToggleButton] when enabled and
     *   checked
     * @param checkedContentColor the content color of this [TextToggleButton] when enabled and
     *   checked
     * @param uncheckedContainerColor the container color of this [TextToggleButton] when enabled
     *   and unchecked
     * @param uncheckedContentColor the content color of this [TextToggleButton] when enabled and
     *   unchecked
     * @param disabledCheckedContainerColor the container color of this [TextToggleButton] when
     *   checked and not enabled
     * @param disabledCheckedContentColor the content color of this [TextToggleButton] when checked
     *   and not enabled
     * @param disabledUncheckedContainerColor the container color of this [TextToggleButton] when
     *   unchecked and not enabled
     * @param disabledUncheckedContentColor the content color of this [TextToggleButton] when
     *   unchecked and not enabled
     */
    @Composable
    fun textToggleButtonColors(
        checkedContainerColor: Color = Color.Unspecified,
        checkedContentColor: Color = Color.Unspecified,
        uncheckedContainerColor: Color = Color.Unspecified,
        uncheckedContentColor: Color = Color.Unspecified,
        disabledCheckedContainerColor: Color = Color.Unspecified,
        disabledCheckedContentColor: Color = Color.Unspecified,
        disabledUncheckedContainerColor: Color = Color.Unspecified,
        disabledUncheckedContentColor: Color = Color.Unspecified,
    ): TextToggleButtonColors =
        MaterialTheme.colorScheme.defaultTextToggleButtonColors.copy(
            checkedContainerColor = checkedContainerColor,
            checkedContentColor = checkedContentColor,
            uncheckedContainerColor = uncheckedContainerColor,
            uncheckedContentColor = uncheckedContentColor,
            disabledCheckedContainerColor = disabledCheckedContainerColor,
            disabledCheckedContentColor = disabledCheckedContentColor,
            disabledUncheckedContainerColor = disabledUncheckedContainerColor,
            disabledUncheckedContentColor = disabledUncheckedContentColor,
        )

    private val ColorScheme.defaultTextToggleButtonColors: TextToggleButtonColors
        get() {
            return defaultTextToggleButtonColorsCached
                ?: TextToggleButtonColors(
                        checkedContainerColor =
                            fromToken(TextToggleButtonTokens.CheckedContainerColor),
                        checkedContentColor = fromToken(TextToggleButtonTokens.CheckedContentColor),
                        uncheckedContainerColor =
                            fromToken(TextToggleButtonTokens.UncheckedContainerColor),
                        uncheckedContentColor =
                            fromToken(TextToggleButtonTokens.UncheckedContentColor),
                        disabledCheckedContainerColor =
                            fromToken(TextToggleButtonTokens.DisabledCheckedContainerColor)
                                .toDisabledColor(
                                    disabledAlpha =
                                        TextToggleButtonTokens.DisabledCheckedContainerOpacity
                                ),
                        disabledCheckedContentColor =
                            fromToken(TextToggleButtonTokens.DisabledCheckedContentColor)
                                .toDisabledColor(
                                    disabledAlpha =
                                        TextToggleButtonTokens.DisabledCheckedContentOpacity
                                ),
                        disabledUncheckedContainerColor =
                            fromToken(TextToggleButtonTokens.DisabledUncheckedContainerColor)
                                .toDisabledColor(
                                    disabledAlpha =
                                        TextToggleButtonTokens.DisabledUncheckedContainerOpacity
                                ),
                        disabledUncheckedContentColor =
                            fromToken(TextToggleButtonTokens.DisabledUncheckedContentColor)
                                .toDisabledColor(
                                    disabledAlpha =
                                        TextToggleButtonTokens.DisabledUncheckedContentOpacity
                                ),
                    )
                    .also { defaultTextToggleButtonColorsCached = it }
        }
}

/**
 * Represents the different container and content colors used for [TextToggleButton] in various
 * states, that are checked, unchecked, enabled and disabled.
 *
 * @param checkedContainerColor Container or background color when the toggle button is checked
 * @param checkedContentColor Color of the content (text or icon) when the toggle button is checked
 * @param uncheckedContainerColor Container or background color when the toggle button is unchecked
 * @param uncheckedContentColor Color of the content (text or icon) when the toggle button is
 *   unchecked
 * @param disabledCheckedContainerColor Container or background color when the toggle button is
 *   disabled and checked
 * @param disabledCheckedContentColor Color of content (text or icon) when the toggle button is
 *   disabled and checked
 * @param disabledUncheckedContainerColor Container or background color when the toggle button is
 *   disabled and unchecked
 * @param disabledUncheckedContentColor Color of the content (text or icon) when the toggle button
 *   is disabled and unchecked
 */
@Immutable
class TextToggleButtonColors(
    val checkedContainerColor: Color,
    val checkedContentColor: Color,
    val uncheckedContainerColor: Color,
    val uncheckedContentColor: Color,
    val disabledCheckedContainerColor: Color,
    val disabledCheckedContentColor: Color,
    val disabledUncheckedContainerColor: Color,
    val disabledUncheckedContentColor: Color,
) {
    internal fun copy(
        checkedContainerColor: Color,
        checkedContentColor: Color,
        uncheckedContainerColor: Color,
        uncheckedContentColor: Color,
        disabledCheckedContainerColor: Color,
        disabledCheckedContentColor: Color,
        disabledUncheckedContainerColor: Color,
        disabledUncheckedContentColor: Color,
    ): TextToggleButtonColors =
        TextToggleButtonColors(
            checkedContainerColor = checkedContainerColor.takeOrElse { this.checkedContainerColor },
            checkedContentColor = checkedContentColor.takeOrElse { this.checkedContentColor },
            uncheckedContainerColor =
                uncheckedContainerColor.takeOrElse { this.uncheckedContainerColor },
            uncheckedContentColor = uncheckedContentColor.takeOrElse { this.uncheckedContentColor },
            disabledCheckedContainerColor =
                disabledCheckedContainerColor.takeOrElse { this.disabledCheckedContainerColor },
            disabledCheckedContentColor =
                disabledCheckedContentColor.takeOrElse { this.disabledCheckedContentColor },
            disabledUncheckedContainerColor =
                disabledUncheckedContainerColor.takeOrElse { this.disabledUncheckedContainerColor },
            disabledUncheckedContentColor =
                disabledUncheckedContentColor.takeOrElse { this.disabledUncheckedContentColor },
        )

    /**
     * Determines the container color based on whether the toggle button is [enabled] and [checked].
     *
     * @param enabled Whether the toggle button is enabled
     * @param checked Whether the toggle button is checked
     */
    @Composable
    internal fun containerColor(enabled: Boolean, checked: Boolean): State<Color> =
        animateSelectionColor(
            enabled = enabled,
            checked = checked,
            checkedColor = checkedContainerColor,
            uncheckedColor = uncheckedContainerColor,
            disabledCheckedColor = disabledCheckedContainerColor,
            disabledUncheckedColor = disabledUncheckedContainerColor,
            animationSpec = COLOR_ANIMATION_SPEC
        )

    /**
     * Determines the content color based on whether the toggle button is [enabled] and [checked].
     *
     * @param enabled Whether the toggle button is enabled
     * @param checked Whether the toggle button is checked
     */
    @Composable
    internal fun contentColor(enabled: Boolean, checked: Boolean): State<Color> =
        animateSelectionColor(
            enabled = enabled,
            checked = checked,
            checkedColor = checkedContentColor,
            uncheckedColor = uncheckedContentColor,
            disabledCheckedColor = disabledCheckedContentColor,
            disabledUncheckedColor = disabledUncheckedContentColor,
            animationSpec = COLOR_ANIMATION_SPEC
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as TextToggleButtonColors

        if (checkedContainerColor != other.checkedContainerColor) return false
        if (checkedContentColor != other.checkedContentColor) return false
        if (uncheckedContainerColor != other.uncheckedContainerColor) return false
        if (uncheckedContentColor != other.uncheckedContentColor) return false
        if (disabledCheckedContainerColor != other.disabledCheckedContainerColor) return false
        if (disabledCheckedContentColor != other.disabledCheckedContentColor) return false
        if (disabledUncheckedContainerColor != other.disabledUncheckedContainerColor) return false
        if (disabledUncheckedContentColor != other.disabledUncheckedContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checkedContainerColor.hashCode()
        result = 31 * result + checkedContentColor.hashCode()
        result = 31 * result + uncheckedContainerColor.hashCode()
        result = 31 * result + uncheckedContentColor.hashCode()
        result = 31 * result + disabledCheckedContainerColor.hashCode()
        result = 31 * result + disabledCheckedContentColor.hashCode()
        result = 31 * result + disabledUncheckedContainerColor.hashCode()
        result = 31 * result + disabledUncheckedContentColor.hashCode()
        return result
    }
}

private val COLOR_ANIMATION_SPEC: AnimationSpec<Color> =
    tween(MotionTokens.DurationMedium1, 0, MotionTokens.EasingStandardDecelerate)
