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

@file:Suppress("NOTHING_TO_INLINE")

package androidx.compose.material

import androidx.compose.animation.AnimatedValueModel
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.AmbientIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Interaction
import androidx.compose.foundation.InteractionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSizeConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.AmbientAnimationClock
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material Design implementation of a
 * [Material Contained Button](https://material.io/design/components/buttons.html#contained-button).
 *
 * Contained buttons are high-emphasis, distinguished by their use of elevation and fill. They
 * contain actions that are primary to your app.
 *
 * To make a button clickable, you must provide an onClick. If no onClick is provided, this button
 * will display itself as disabled.
 *
 * The default text style for internal [Text] components will be set to [Typography.button]. Text
 * color will try to match the correlated color for the background color. For example if the
 * background color is set to [Colors.primary] then the text will by default use
 * [Colors.onPrimary].
 *
 * @sample androidx.compose.material.samples.ButtonSample
 *
 * If you need to add an icon just put it inside the [content] slot together with a spacing
 * and a text:
 *
 * @sample androidx.compose.material.samples.ButtonWithIconSample
 *
 * @param onClick Will be called when the user clicks the button
 * @param modifier Modifier to be applied to the button
 * @param enabled Controls the enabled state of the button. When `false`, this button will not
 * be clickable
 * @param interactionState the [InteractionState] representing the different [Interaction]s
 * present on this Button. You can create and pass in your own remembered [InteractionState] if
 * you want to read the [InteractionState] and customize the appearance / behavior of this Button
 * in different [Interaction]s, such as customizing how the [elevation] of this Button changes when
 * it is [Interaction.Pressed].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. This controls the size of the shadow below the button. Pass `null` here to disable
 * elevation for this button. See [ButtonConstants.defaultElevation].
 * @param shape Defines the button's shape as well as its shadow
 * @param border Border to draw around the button
 * @param colors [ButtonColors] that will be used to resolve the background and content color for
 * this button in different states. See [ButtonConstants.defaultButtonColors].
 * @param contentPadding The spacing values to apply internally between the container and the content
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionState: InteractionState = remember { InteractionState() },
    elevation: ButtonElevation? = ButtonConstants.defaultElevation(),
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonConstants.defaultButtonColors(),
    contentPadding: PaddingValues = ButtonConstants.DefaultContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    // TODO(aelias): Avoid manually putting the clickable above the clip and
    // the ripple below the clip once http://b/157687898 is fixed and we have
    // more flexibility to move the clickable modifier (see candidate approach
    // aosp/1361921)
    val contentColor = colors.contentColor(enabled)
    Surface(
        shape = shape,
        color = colors.backgroundColor(enabled),
        contentColor = contentColor.copy(alpha = 1f),
        border = border,
        elevation = elevation?.elevation(enabled, interactionState) ?: 0.dp,
        modifier = modifier.clickable(
            onClick = onClick,
            enabled = enabled,
            interactionState = interactionState,
            indication = null
        )
    ) {
        Providers(AmbientContentAlpha provides contentColor.alpha) {
            ProvideTextStyle(
                value = MaterialTheme.typography.button
            ) {
                Row(
                    Modifier
                        .defaultMinSizeConstraints(
                            minWidth = ButtonConstants.DefaultMinWidth,
                            minHeight = ButtonConstants.DefaultMinHeight
                        )
                        .indication(interactionState, AmbientIndication.current())
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    children = content
                )
            }
        }
    }
}

/**
 * Material Design implementation of a
 * [Material Outlined Button](https://material.io/design/components/buttons.html#outlined-button).
 *
 * Outlined buttons are medium-emphasis buttons. They contain actions that are important, but are
 * not the primary action in an app.
 *
 * Outlined buttons are also a lower emphasis alternative to contained buttons, or a higher emphasis
 * alternative to text buttons.
 *
 * To make a button clickable, you must provide an onClick. If no onClick is provided, this button
 * will display itself as disabled.
 *
 * The default text style for internal [Text] components will be set to [Typography.button]. Text
 * color will try to match the correlated color for the background color. For example if the
 * background color is set to [Colors.primary] then the text will by default use
 * [Colors.onPrimary].
 *
 * @sample androidx.compose.material.samples.OutlinedButtonSample
 *
 * @param onClick Will be called when the user clicks the button
 * @param modifier Modifier to be applied to the button
 * @param enabled Controls the enabled state of the button. When `false`, this button will not
 * be clickable
 * @param interactionState the [InteractionState] representing the different [Interaction]s
 * present on this Button. You can create and pass in your own remembered [InteractionState] if
 * you want to read the [InteractionState] and customize the appearance / behavior of this Button
 * in different [Interaction]s, such as customizing how the [elevation] of this Button changes when
 * it is [Interaction.Pressed].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. An OutlinedButton typically has no elevation, see [Button] for a button with elevation.
 * @param shape Defines the button's shape as well as its shadow
 * @param border Border to draw around the button
 * @param colors [ButtonColors] that will be used to resolve the background and content color for
 * this button in different states. See [ButtonConstants.defaultOutlinedButtonColors].
 * @param contentPadding The spacing values to apply internally between the container and the content
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
inline fun OutlinedButton(
    noinline onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionState: InteractionState = remember { InteractionState() },
    elevation: ButtonElevation? = null,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = ButtonConstants.defaultOutlinedBorder,
    colors: ButtonColors = ButtonConstants.defaultOutlinedButtonColors(),
    contentPadding: PaddingValues = ButtonConstants.DefaultContentPadding,
    noinline content: @Composable RowScope.() -> Unit
) = Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    interactionState = interactionState,
    elevation = elevation,
    shape = shape,
    border = border,
    colors = colors,
    contentPadding = contentPadding,
    content = content
)

/**
 * Material Design implementation of a
 * [Material Text Button](https://material.io/design/components/buttons.html#text-button).
 *
 * Text buttons are typically used for less-pronounced actions, including those located in cards and
 * dialogs.
 *
 * To make a button clickable, you must provide an onClick. If no onClick is provided, this button
 * will display itself as disabled.
 *
 * The default text style for internal [Text] components will be set to [Typography.button]. Text
 * color will try to match the correlated color for the background color. For example if the
 * background color is set to [Colors.primary] then the text will by default use
 * [Colors.onPrimary].
 *
 * @sample androidx.compose.material.samples.TextButtonSample
 *
 * @param onClick Will be called when the user clicks the button
 * @param modifier Modifier to be applied to the button
 * @param enabled Controls the enabled state of the button. When `false`, this button will not
 * be clickable
 * @param interactionState the [InteractionState] representing the different [Interaction]s
 * present on this Button. You can create and pass in your own remembered [InteractionState] if
 * you want to read the [InteractionState] and customize the appearance / behavior of this Button
 * in different [Interaction]s, such as customizing how the [elevation] of this Button changes when
 * it is [Interaction.Pressed].
 * @param elevation [ButtonElevation] used to resolve the elevation for this button in different
 * states. A TextButton typically has no elevation, see [Button] for a button with elevation.
 * @param shape Defines the button's shape as well as its shadow
 * @param border Border to draw around the button
 * @param colors [ButtonColors] that will be used to resolve the background and content color for
 * this button in different states. See [ButtonConstants.defaultTextButtonColors].
 * @param contentPadding The spacing values to apply internally between the container and the content
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
inline fun TextButton(
    noinline onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionState: InteractionState = remember { InteractionState() },
    elevation: ButtonElevation? = null,
    shape: Shape = MaterialTheme.shapes.small,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonConstants.defaultTextButtonColors(),
    contentPadding: PaddingValues = ButtonConstants.DefaultTextContentPadding,
    noinline content: @Composable RowScope.() -> Unit
) = Button(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    interactionState = interactionState,
    elevation = elevation,
    shape = shape,
    border = border,
    colors = colors,
    contentPadding = contentPadding,
    content = content
)

/**
 * Represents the elevation for a button in different states.
 *
 * See [ButtonConstants.defaultElevation] for the default elevation used in a [Button].
 */
@ExperimentalMaterialApi
@Stable
interface ButtonElevation {
    /**
     * Represents the elevation used in a button, depending on [enabled] and [interactionState].
     *
     * @param enabled whether the button is enabled
     * @param interactionState the [InteractionState] for this button
     */
    fun elevation(enabled: Boolean, interactionState: InteractionState): Dp
}

/**
 * Represents the background and content colors used in a button in different states.
 *
 * See [ButtonConstants.defaultButtonColors] for the default colors used in a [Button].
 * See [ButtonConstants.defaultOutlinedButtonColors] for the default colors used in a
 * [OutlinedButton].
 * See [ButtonConstants.defaultTextButtonColors] for the default colors used in a [TextButton].
 */
@ExperimentalMaterialApi
@Stable
interface ButtonColors {
    /**
     * Represents the background color for this button, depending on [enabled].
     *
     * @param enabled whether the button is enabled
     */
    fun backgroundColor(enabled: Boolean): Color

    /**
     * Represents the content color for this button, depending on [enabled].
     *
     * @param enabled whether the button is enabled
     */
    fun contentColor(enabled: Boolean): Color
}

/**
 * Contains the default values used by [Button]
 */
object ButtonConstants {
    private val ButtonHorizontalPadding = 16.dp
    private val ButtonVerticalPadding = 8.dp

    /**
     * The default content padding used by [Button]
     */
    val DefaultContentPadding = PaddingValues(
        start = ButtonHorizontalPadding,
        top = ButtonVerticalPadding,
        end = ButtonHorizontalPadding,
        bottom = ButtonVerticalPadding
    )

    /**
     * The default min width applied for the [Button].
     * Note that you can override it by applying Modifier.widthIn directly on [Button].
     */
    val DefaultMinWidth = 64.dp

    /**
     * The default min width applied for the [Button].
     * Note that you can override it by applying Modifier.heightIn directly on [Button].
     */
    val DefaultMinHeight = 36.dp

    /**
     * The default size of the icon when used inside a [Button].
     *
     * @sample androidx.compose.material.samples.ButtonWithIconSample
     */
    val DefaultIconSize = 18.dp

    /**
     * The default size of the spacing between an icon and a text when they used inside a [Button].
     *
     * @sample androidx.compose.material.samples.ButtonWithIconSample
     */
    val DefaultIconSpacing = 8.dp

    // TODO: b/152525426 add support for focused and hovered states
    /**
     * Creates a [ButtonElevation] that will animate between the provided values according to the
     * Material specification for a [Button].
     *
     * @param defaultElevation the elevation to use when the [Button] is enabled, and has no
     * other [Interaction]s.
     * @param pressedElevation the elevation to use when the [Button] is enabled and
     * is [Interaction.Pressed].
     * @param disabledElevation the elevation to use when the [Button] is not enabled.
     */
    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun defaultElevation(
        defaultElevation: Dp = 2.dp,
        pressedElevation: Dp = 8.dp,
        // focused: Dp = 4.dp,
        // hovered: Dp = 4.dp,
        disabledElevation: Dp = 0.dp
    ): ButtonElevation {
        val clock = AmbientAnimationClock.current.asDisposableClock()
        return remember(defaultElevation, pressedElevation, disabledElevation, clock) {
            DefaultButtonElevation(
                defaultElevation = defaultElevation,
                pressedElevation = pressedElevation,
                disabledElevation = disabledElevation,
                clock = clock
            )
        }
    }

    /**
     * Creates a [ButtonColors] that represents the default background and content colors used in
     * a [Button].
     *
     * @param backgroundColor the background color of this [Button] when enabled
     * @param disabledBackgroundColor the background color of this [Button] when not enabled
     * @param contentColor the content color of this [Button] when enabled
     * @param disabledContentColor the content color of this [Button] when not enabled
     */
    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun defaultButtonColors(
        backgroundColor: Color = MaterialTheme.colors.primary,
        disabledBackgroundColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            .compositeOver(MaterialTheme.colors.surface),
        contentColor: Color = contentColorFor(backgroundColor),
        disabledContentColor: Color = MaterialTheme.colors.onSurface
            .copy(alpha = ContentAlpha.disabled)
    ): ButtonColors = DefaultButtonColors(
        backgroundColor,
        disabledBackgroundColor,
        contentColor,
        disabledContentColor
    )

    /**
     * Creates a [ButtonColors] that represents the default background and content colors used in
     * an [OutlinedButton].
     *
     * @param backgroundColor the background color of this [OutlinedButton]
     * @param contentColor the content color of this [OutlinedButton] when enabled
     * @param disabledContentColor the content color of this [OutlinedButton] when not enabled
     */
    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun defaultOutlinedButtonColors(
        backgroundColor: Color = MaterialTheme.colors.surface,
        contentColor: Color = MaterialTheme.colors.primary,
        disabledContentColor: Color = MaterialTheme.colors.onSurface
            .copy(alpha = ContentAlpha.disabled)
    ): ButtonColors = DefaultButtonColors(
        backgroundColor,
        backgroundColor,
        contentColor,
        disabledContentColor
    )

    /**
     * Creates a [ButtonColors] that represents the default background and content colors used in
     * a [TextButton].
     *
     * @param backgroundColor the background color of this [TextButton]
     * @param contentColor the content color of this [TextButton] when enabled
     * @param disabledContentColor the content color of this [TextButton] when not enabled
     */
    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun defaultTextButtonColors(
        backgroundColor: Color = Color.Transparent,
        contentColor: Color = MaterialTheme.colors.primary,
        disabledContentColor: Color = MaterialTheme.colors.onSurface
            .copy(alpha = ContentAlpha.disabled)
    ): ButtonColors = DefaultButtonColors(
        backgroundColor,
        backgroundColor,
        contentColor,
        disabledContentColor
    )

    /**
     * The default color opacity used for an [OutlinedButton]'s border color
     */
    const val OutlinedBorderOpacity = 0.12f

    /**
     * The default [OutlinedButton]'s border size
     */
    val OutlinedBorderSize = 1.dp

    /**
     * The default disabled content color used by all types of [Button]s
     */
    @Composable
    val defaultOutlinedBorder: BorderStroke
        get() = BorderStroke(
            OutlinedBorderSize, MaterialTheme.colors.onSurface.copy(alpha = OutlinedBorderOpacity)
        )

    private val TextButtonHorizontalPadding = 8.dp

    /**
     * The default content padding used by [TextButton]
     */
    val DefaultTextContentPadding = DefaultContentPadding.copy(
        start = TextButtonHorizontalPadding,
        end = TextButtonHorizontalPadding
    )
}

/**
 * Default [ButtonElevation] implementation.
 */
@OptIn(ExperimentalMaterialApi::class)
@Stable
private class DefaultButtonElevation(
    private val defaultElevation: Dp,
    private val pressedElevation: Dp,
    private val disabledElevation: Dp,
    private val clock: AnimationClockObservable
) : ButtonElevation {
    private val lazyAnimatedElevation = LazyAnimatedValue<Dp, AnimationVector1D> { target ->
        AnimatedValueModel(initialValue = target, typeConverter = Dp.VectorConverter, clock = clock)
    }

    override fun elevation(enabled: Boolean, interactionState: InteractionState): Dp {
        val interaction = interactionState.value.lastOrNull {
            it is Interaction.Pressed
        }

        val target = if (!enabled) {
            disabledElevation
        } else {
            when (interaction) {
                Interaction.Pressed -> pressedElevation
                else -> defaultElevation
            }
        }

        val animatedElevation = lazyAnimatedElevation.animatedValueForTarget(target)

        if (animatedElevation.targetValue != target) {
            if (!enabled) {
                // No transition when moving to a disabled state
                animatedElevation.snapTo(target)
            } else {
                val lastInteraction = when (animatedElevation.targetValue) {
                    pressedElevation -> Interaction.Pressed
                    else -> null
                }
                animatedElevation.animateElevation(
                    from = lastInteraction,
                    to = interaction,
                    target = target
                )
            }
        }

        return animatedElevation.value
    }
}

/**
 * Default [ButtonColors] implementation.
 */
@OptIn(ExperimentalMaterialApi::class)
@Immutable
private class DefaultButtonColors(
    private val backgroundColor: Color,
    private val disabledBackgroundColor: Color,
    private val contentColor: Color,
    private val disabledContentColor: Color
) : ButtonColors {
    override fun backgroundColor(enabled: Boolean): Color {
        return if (enabled) backgroundColor else disabledBackgroundColor
    }

    override fun contentColor(enabled: Boolean): Color {
        return if (enabled) contentColor else disabledContentColor
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DefaultButtonColors

        if (backgroundColor != other.backgroundColor) return false
        if (disabledBackgroundColor != other.disabledBackgroundColor) return false
        if (contentColor != other.contentColor) return false
        if (disabledContentColor != other.disabledContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backgroundColor.hashCode()
        result = 31 * result + disabledBackgroundColor.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + disabledContentColor.hashCode()
        return result
    }
}
