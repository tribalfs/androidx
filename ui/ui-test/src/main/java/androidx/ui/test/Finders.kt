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

package androidx.ui.test

/**
 * Finds a component identified by the given tag.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 *
 * @see find for general find method.
 */
fun findByTag(
    testTag: String,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = find(hasTestTag(testTag), useUnmergedTree)

/**
 * Finds all components identified by the given tag.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 *
 * @see findAll for general find method.
 */
fun findAllByTag(
    testTag: String,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection = findAll(hasTestTag(testTag), useUnmergedTree)

/**
 * Finds a component with the given label as its accessibilityLabel.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 *
 * @see find for general find method.
 */
fun findByLabel(
    label: String,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = find(hasLabel(label, ignoreCase), useUnmergedTree)

/**
 * Finds a component with the given text.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 * @see findBySubstring to search by substring instead of via exact match.
 * @see find for general find method.
 */
fun findByText(
    text: String,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = find(hasText(text, ignoreCase), useUnmergedTree)

/**
 * Finds a component with text that contains the given substring.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 * @see findByText to perform exact matches.
 * @see find for general find method.
 */
fun findBySubstring(
    text: String,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction = find(hasSubstring(text, ignoreCase), useUnmergedTree)

/**
 * Finds all components with the given text.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 */
fun findAllByText(
    text: String,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection = findAll(hasText(text, ignoreCase), useUnmergedTree)

/**
 * Finds all components with the given label as AccessibilityLabel.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 */
fun findAllByLabel(
    label: String,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection = findAll(hasLabel(label, ignoreCase), useUnmergedTree)

/**
 * Finds the root semantics node of the Compose tree.  Useful for example for screenshot tests
 * of the entire scene.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 */
fun findRoot(useUnmergedTree: Boolean = false): SemanticsNodeInteraction =
    find(isRoot(), useUnmergedTree)

/**
 * Finds a component that matches the given condition.
 *
 * Any subsequent operation on its result will expect exactly one element found (unless
 * [SemanticsNodeInteraction.assertDoesNotExist] is used) and will throw [AssertionError] if
 * none or more than one element is found.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 * @see findAll to work with multiple elements
 */
fun find(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction {
    return SemanticsNodeInteraction(useUnmergedTree, SemanticsSelector(matcher))
}

/**
 * Finds all components that match the given condition.
 *
 * If you are working with elements that are not supposed to occur multiple times use [find]
 * instead.
 *
 * For usage patterns and semantics concepts see [SemanticsNodeInteraction]
 *
 * @param useUnmergedTree Find within merged composables like Buttons.
 * @see find
 */
fun findAll(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteractionCollection {
    return SemanticsNodeInteractionCollection(useUnmergedTree, SemanticsSelector(matcher))
}
