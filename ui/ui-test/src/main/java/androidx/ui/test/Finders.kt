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

import androidx.ui.core.semantics.SemanticsConfiguration
import androidx.ui.core.semantics.getOrNull
import androidx.ui.semantics.SemanticsProperties

/**
 * Extension methods that provide the entry point for the testing APIs.
 */

/**
 * Finds a component identified by the given tag.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 */
fun findByTag(testTag: String): SemanticsNodeInteraction {
    return find {
        getOrNull(SemanticsProperties.TestTag) == testTag
    }
}

/**
 * Finds all components identified by the given tag.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 */
fun findAllByTag(testTag: String): List<SemanticsNodeInteraction> {
    return findAll {
        getOrNull(SemanticsProperties.TestTag) == testTag
    }
}

/**
 * Finds a component with the given text as its accessibilityLabel.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 * @see findBySubstring to search by substring instead of via exact match.
 */
fun findByText(text: String, ignoreCase: Boolean = false): SemanticsNodeInteraction {
    return find {
        hasText(text, ignoreCase)
    }
}

/**
 *  Finds a component with accessibilityLabel that contains the given substring.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 * @see findByText to perform exact matches.
 */
fun findBySubstring(text: String, ignoreCase: Boolean = false): SemanticsNodeInteraction {
    return find {
        hasSubstring(text, ignoreCase)
    }
}

/**
 * Finds all components with the given text as their accessibility label.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 */
fun findAllByText(text: String, ignoreCase: Boolean = false): List<SemanticsNodeInteraction> {
    return findAll {
        getOrNull(SemanticsProperties.AccessibilityLabel).equals(text, ignoreCase)
    }
}

/**
 * Finds a component that matches the given condition.
 * This tries to match exactly one element and throws [AssertionError] if more than one is matched.
 *
 * For usage patterns see [SemanticsNodeInteraction]
 * @see findAll to work with multiple elements
 */
fun find(
    selector: SemanticsConfiguration.() -> Boolean
): SemanticsNodeInteraction {
    return semanticsTreeInteractionFactory(selector)
        .findOne()
}

/**
 * Finds all components that match the given condition.
 *
 * If you are working with elements that are not supposed to occur multiple times use [find]
 * instead.
 * @see find
 */
fun findAll(
    selector: SemanticsConfiguration.() -> Boolean
): List<SemanticsNodeInteraction> {
    return semanticsTreeInteractionFactory(selector)
        .findAllMatching()
}
