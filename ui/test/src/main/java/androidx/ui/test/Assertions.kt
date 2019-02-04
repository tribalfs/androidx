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

import androidx.ui.semantics.SemanticsProperties

/**
 * Asserts that current component is visible.
 */
// TODO(b/123702531): Provide guarantees of being visible VS being actually displayed
fun SemanticsTreeQuery.assertIsVisible() =
    verifyAssertOnExactlyOne("The component is not visible!") {
        it.hidden == null || it.hidden == false
    }

/**
 * Asserts that current component is hidden. This requires that the component actually exists in
 * the hierarchy and is hidden. If you want to actually verify that the component does not  exist
 * at all, please use [assertDoesNotExist]
 */
fun SemanticsTreeQuery.assertIsHidden() =
    verifyAssertOnExactlyOne("The component is visible!") {
        it.hidden == true
    }

/**
 * Asserts that there is no component that was matched by the query. If the component exists but is
 * hidden use [assertIsHidden] instead.
 */
fun SemanticsTreeQuery.assertDoesNotExist(): SemanticsTreeQuery {
    val foundNodes = findAllMatching()
    if (foundNodes.isNotEmpty()) {
        throw AssertionError("Found '${foundNodes.size}' nodes but 0 was expected!")
    }
    return this
}

/**
 * Asserts that current component is visible.
 */
// TODO(pavlis): Provide guarantees of being visible VS being actually displayed
fun SemanticsTreeQuery.assertIsChecked() =
    // TODO(pavlis): Throw exception if component is not checkable
    verifyAssertOnExactlyOne("The component is not checked!") {
        it.checked == true
    }

fun SemanticsTreeQuery.assertSemanticsIsEqualTo(expectedProperties: SemanticsProperties) {
    // TODO(b/123701912): Implement
    // We want to compare each individual field and create an error message containing every field
    // that was not equal. We can get some inspiration from Truth.
    // Example of possible error:
    // - Expected semantics is not equal to the current one:
    // - - expected 'checked' = 'true' but was 'false'
}

internal fun SemanticsTreeQuery.verifyAssertOnExactlyOne(
    assertionMessage: String,
    condition: (SemanticsProperties) -> Boolean
): SemanticsTreeQuery {
    val foundNodes = findAllMatching()
    if (foundNodes.size != 1) {
        throw AssertionError("Found '${foundNodes.size}' nodes but 1 was expected!")
    }
    if (!condition.invoke(foundNodes.first().data)) {
        throw AssertionError("Assert failed: $assertionMessage")
    }
    return this
}