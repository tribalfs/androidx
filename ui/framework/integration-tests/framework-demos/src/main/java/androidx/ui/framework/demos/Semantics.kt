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

package androidx.ui.framework.demos

import androidx.ui.layout.Row
import androidx.ui.core.PxPosition
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.core.px
import androidx.ui.layout.Column
import androidx.ui.layout.Container
import androidx.ui.layout.CrossAxisAlignment
import androidx.ui.layout.MainAxisAlignment
import androidx.ui.material.Button
import androidx.ui.material.MaterialTheme
import androidx.ui.material.themeTextStyle
import androidx.compose.Children
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.unaryPlus

/** A [SemanticProperty] is used to store semantic information about a component.
 *
 * Note: We use an interface instead of a sealed class here to allow SemanticProperty to be
 * implemented as an Enum (Enums cannot inherit from classes, but they can implement interfaces). */
interface SemanticProperty<T> {
    val value: T

    // Returning null signifies that this property cannot be merged.
    fun merge(other: SemanticProperty<T>): SemanticProperty<T>? = null
}

// These are some example SemanticProperties provided by the framework.

/** [Label] stores a string that describes the component. */
class Label(override var value: String) : SemanticProperty<String> {
    // Labels are concatenated.
    override fun merge(other: SemanticProperty<String>) = Label("$this $other")
}

/** [Visibility] stores an enum that represents the visibility of the component. */
enum class Visibility : SemanticProperty<Visibility> {
    Undefined, Visible, Invisible;

    override val value: Visibility get() = this

    // The visibility of the parent takes precedence.
    override fun merge(other: SemanticProperty<Visibility>) = this
}

/**
 * This class provides a way to store a function that is run when the [SemanticAction] is invoked.
 */
class SemanticAction<T>(
    val phrase: String = "",
    val defaultParam: T,
    val types: Set<ActionType> = setOf(),
    val action: (ActionParam<T>) -> Unit
)

/**
 * An extension function to invoke the action.
 */
fun <T> SemanticAction<T>.invoke(
    caller: ActionCaller = ActionCaller.Unknown,
    param: T = defaultParam
) = action(ActionParam(caller, param))

/**
 * The parameter sent to every callback. In addition to the parameter value, it also provides
 * information about the framework that raised the action.
 */
class ActionParam<T>(val caller: ActionCaller = ActionCaller.Unknown, val value: T)

/**
 * Frameworks that invoke the action. The developer might be interested in knowing which framework
 * invoked the action.
 */
enum class ActionCaller { Unknown, Accessibility, AutoFill, Assistant, PointerInput, KeyInput }

/**
 * The [ActionType] is a way to provide more information about a [SemanticAction]. It can be used by
 * other frameworks to identify an action that is to be run.
 *
 * Right now we are just using this interface as a common base class.
 * */
interface ActionType

// These are some example action types provided by the framework:
class Autofill : ActionType

enum class PolarityAction : ActionType { Neutral, Positive, Negative, Toggle }
enum class AccessibilityAction : ActionType { Primary, Secondary, Tertiary }
enum class EditAction : ActionType { Cut, Copy, Paste, Select, SelectAll, Clear, Undo }
enum class NavigationAction : ActionType { Back, Forward, Up, Down, Left, Right }

/**
 * A PressGestureDetector that uses actions instead of lambda callbacks.
 *
 * This component just wraps the GestureDetetor and allows us to use it with actions, instead of
 * lambda functions. A [SemanticAction] allows us to specify more information in addition to the
 * lambda to be executed.*/
@Composable
fun PressGestureDetectorWithActions(
    onPress: SemanticAction<PxPosition> = SemanticAction(defaultParam = PxPosition.Origin) { _ -> },
    onRelease: SemanticAction<Unit> = SemanticAction(defaultParam = Unit) { _ -> },
    onCancel: SemanticAction<Unit> = SemanticAction(defaultParam = Unit) { _ -> },
    @Children children: @Composable() () -> Unit
) {
    PressGestureDetector(
        onPress = { onPress.action(ActionParam(ActionCaller.PointerInput, it)) },
        onRelease = { onRelease.action(ActionParam(ActionCaller.PointerInput, Unit)) },
        onCancel = { onCancel.action(ActionParam(ActionCaller.PointerInput, Unit)) }) {
        children()
    }
}

/**
 * This is our lowest level API for Semantics.
 *
 * This implementation is a component just wraps its child components in a frame that has buttons
 * which represent some frameworks that will trigger the semantic actions.
 */
@Composable
fun Semantics(
    // TODO: use this?
    // properties: Set<SemanticProperty<out Any>> = setOf(),
    actions: Set<SemanticAction<out Any?>> = setOf(),
    @Children children: @Composable() () -> Unit
) {
    Column {
        MaterialTheme {

            // Invoke actions by type.
            val primary = actions.firstOrNull { it.types.contains(AccessibilityAction.Primary) }
            val secondary = actions.firstOrNull { it.types.contains(AccessibilityAction.Secondary) }
            Text(text = "Accessibility Actions By Type", style = +themeTextStyle { h6.copy() })
            Row(mainAxisAlignment = MainAxisAlignment.SpaceEvenly) {
                Button(text = "Primary", onClick = { primary?.invoke(ActionCaller.Accessibility) })
                Button(text = "Secondary", onClick = {
                    secondary?.invoke(ActionCaller.Accessibility)
                })
            }

            // Invoke by phrase.
            Text(text = "Accessibility Actions By Phrase", style = +themeTextStyle { h6.copy() })
            Row(mainAxisAlignment = MainAxisAlignment.SpaceEvenly) {
                actions.forEach {
                    Button(text = it.phrase, onClick = { it.invoke(ActionCaller.Accessibility) })
                }
            }

            // Invoke by assistant type.
            val positive = actions.firstOrNull { it.types.contains(PolarityAction.Positive) }
            val negative = actions.firstOrNull { it.types.contains(PolarityAction.Negative) }
            Text(text = "Assistant Actions", style = +themeTextStyle { h6.copy() })
            Row(mainAxisAlignment = MainAxisAlignment.SpaceEvenly) {
                Button(text = "Negative", onClick = { negative?.invoke(ActionCaller.Assistant) })
                Button(text = "Positive", onClick = { positive?.invoke(ActionCaller.Assistant) })
            }

            // Invoke by passing parameters.
            @Suppress("UNCHECKED_CAST")
            val pxPositionAction =
                actions.firstOrNull { it.defaultParam is PxPosition } as SemanticAction<PxPosition>?
            @Suppress("UNCHECKED_CAST")
            val unitAction =
                actions.firstOrNull { it.defaultParam is Unit } as SemanticAction<Unit>?
            Text(text = "Actions using Parameters", style = +themeTextStyle { h6.copy() })
            Row(mainAxisAlignment = MainAxisAlignment.SpaceEvenly) {
                Button(
                    text = "IntAction",
                    onClick = { pxPositionAction?.invoke(param = PxPosition(1.px, 1.px)) })
                Button(
                    text = "VoidAction",
                    onClick = { unitAction?.invoke(param = Unit) })
            }
        }
        Row(
            mainAxisAlignment = MainAxisAlignment.Center,
            crossAxisAlignment = CrossAxisAlignment.Center
        ) {
            Container(height = 300.dp, width = 500.dp) {
                children()
            }
        }
    }
}

/**
 * This is a level 3 API, where the user uses the [SemanticActionBuilder] to build the action.
 * This component provides default values for all the parameters to the builder, the developer has
 * to just supply the callback lambda.
 */
@Composable
fun ClickInteraction(
    press: SemanticActionBuilder<PxPosition>.() -> Unit,
    release: SemanticActionBuilder<Unit>.() -> Unit,
    @Children children: @Composable() () -> Unit
) {
    val pressedAction = SemanticActionBuilder(
        label = "On Press",
        defaultParam = PxPosition.Origin
    ).apply(press).build()

    val releasedAction = SemanticActionBuilder(
        label = "On Release",
        defaultParam = Unit
    ).apply(release).build()

    Semantics(
        actions = setOf(pressedAction, releasedAction)
    ) {
        PressGestureDetectorWithActions(
            onPress = pressedAction,
            onRelease = releasedAction,
            onCancel = releasedAction
        ) {
            children()
        }
    }
}

/**
 * Builder to create a semantic action.
 */
class SemanticActionBuilder<T>(
    var label: String,
    var defaultParam: T,
    var types: Set<ActionType> = setOf(),
    var action: (ActionParam<T>) -> Unit = {}
) {
    fun build() = SemanticAction(label, defaultParam, types, action)
}

/**
 * Extension function to make the syntax cleaner when we use all default parameters for the action.
 */
fun <T> SemanticActionBuilder<T>.action(action: (ActionParam<T>) -> Unit = {}) {
    this.action = action
}