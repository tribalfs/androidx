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

package androidx.ui.core

import androidx.compose.Stable

/**
 * An ordered, immutable collection of [modifier elements][Modifier.Element] that decorate or add
 * behavior to Compose UI elements. For example, backgrounds, padding and click event listeners
 * decorate or add behavior to rows, text or buttons.
 *
 * @sample androidx.ui.core.samples.ModifierUsageSample
 *
 * Modifier implementations should offer a fluent factory extension function on [Modifier] for
 * creating combined modifiers by starting from existing modifiers:
 *
 * @sample androidx.ui.core.samples.ModifierFactorySample
 *
 * Modifier elements may be combined using [the + operator][plus]. Order is significant; modifier
 * elements that appear first will be applied first.
 *
 * Composables that accept a [Modifier] as a parameter to be applied to the whole component
 * represented by the composable function should name the parameter `modifier` and
 * assign the parameter a default value of [Modifier]. It should appear as the first
 * optional parameter in the parameter list; after all required parameters (except for trailing
 * lambda parameters) but before any other parameters with default values. Any default modifiers
 * desired by a composable function should come after the `modifier` parameter's value in the
 * composable function's implementation, keeping [Modifier] as the default parameter value.
 * For example:
 *
 * @sample androidx.ui.core.samples.ModifierParameterSample
 *
 * The pattern above allows default modifiers to still be applied as part of the chain
 * if a caller also supplies unrelated modifiers.
 *
 * Composables that accept modifiers to be applied to a specific subcomponent `foo`
 * should name the parameter `fooModifier` and follow the same guidelines above for default values
 * and behavior. Subcomponent modifiers should be grouped together and follow the parent
 * composable's modifier. For example:
 *
 * @sample androidx.ui.core.samples.SubcomponentModifierSample
 */
@Stable
interface Modifier {

    /**
     * Accumulates a value starting with [initial] and applying [operation] to the current value
     * and each element from outside in.
     *
     * Elements wrap one another in a chain from left to right; an [Element] that appears to the
     * left of another in a `+` expression or in [operation]'s parameter order affects all
     * of the elements that appear after it. [foldIn] may be used to accumulate a value starting
     * from the parent or head of the modifier chain to the final wrapped child.
     */
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    /**
     * Accumulates a value starting with [initial] and applying [operation] to the current value
     * and each element from inside out.
     *
     * Elements wrap one another in a chain from left to right; an [Element] that appears to the
     * left of another in a `+` expression or in [operation]'s parameter order affects all
     * of the elements that appear after it. [foldOut] may be used to accumulate a value starting
     * from the child or tail of the modifier chain up to the parent or head of the chain.
     */
    fun <R> foldOut(initial: R, operation: (Element, R) -> R): R

    /**
     * Concatenates this modifier with another.
     *
     * Returns a [Modifier] representing this modifier followed by [other] in sequence.
     */
    operator fun plus(other: Modifier): Modifier =
        if (other === Modifier) this else foldOut(other) { element, wrapped ->
            CombinedModifier(element, wrapped)
        }

    /**
     * A single element contained within a [Modifier] chain.
     */
    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R =
            operation(initial, this)

        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R =
            operation(this, initial)
    }

    /**
     * The companion object `Modifier` is the empty, default, or starter [Modifier]
     * that contains no [elements][Element]. Use it to create a new [Modifier] using
     * modifier extension factory functions:
     *
     * @sample androidx.ui.core.samples.ModifierUsageSample
     *
     * or as the default value for [Modifier] parameters:
     *
     * @sample androidx.ui.core.samples.ModifierParameterSample
     */
    // The companion object implements `Modifier` so that it may be used  as the start of a
    // modifier extension factory expression.
    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override fun <R> foldOut(initial: R, operation: (Element, R) -> R): R = initial
        override operator fun plus(other: Modifier): Modifier = other
        override fun toString() = "Modifier"

        /**
         * An empty [Modifier] that contains no [elements][Element].
         * Suitable for use as a sentinel or default parameter.
         *
         * @deprecated Use the [Modifier] companion object instead
         */
        @Deprecated(
            "use the Modifier companion object instead",
            replaceWith = ReplaceWith(
                "Modifier",
                "androidx.ui.core.Modifier"
            )
        )
        val None: Modifier get() = this
    }
}

/**
 * A node in a [Modifier] chain. A CombinedModifier always contains at least two elements;
 * a Modifier of one is always just the [Modifier.Element] itself, and a Modifier of zero is always
 * [Modifier].
 */
private class CombinedModifier(
    private val element: Modifier.Element,
    private val wrapped: Modifier
) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        wrapped.foldIn(operation(initial, element), operation)

    override fun <R> foldOut(initial: R, operation: (Modifier.Element, R) -> R): R =
        operation(element, wrapped.foldOut(initial, operation))

    override fun equals(other: Any?): Boolean =
        other is CombinedModifier && element == other.element && wrapped == other.wrapped

    override fun hashCode(): Int = wrapped.hashCode() + 31 * element.hashCode()

    override fun toString() = "[" + foldIn("") { acc, element ->
        if (acc.isEmpty()) element.toString() else "$acc, $element"
    } + "]"
}
