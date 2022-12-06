/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.kruth

import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Propositions for [Iterable] subjects.
 *
 * **Note:**
 *
 * - Assertions may iterate through the given [Iterable] more than once. If you have an
 * unusual implementation of [Iterable] which does not support multiple iterations
 * (sometimes known as a "one-shot iterable"), you must copy your iterable into a collection
 * which does (e.g. `iterable.toList()`). If you don't, you may see surprising failures.
 * - Assertions may also require that the elements in the given [Iterable] implement
 * [Any.hashCode] correctly.
 */
open class IterableSubject<T>(actual: Iterable<T>?) : Subject<Iterable<T>>(actual) {

    override fun isEqualTo(expected: Any?) {
        // method contract requires testing iterables for equality
        if (expected == actual) {
            return
        }

        // Fail but with a more descriptive message:
        when {
            (actual is List<*>) && (expected is List<*>) ->
                containsExactlyElementsIn(expected).inOrder()

            (actual is Set<*>) && (expected is Set<*>) ->
                containsExactlyElementsIn(expected)

            // TODO(b/18430105): Consider a special message if comparing incompatible collection types
            else -> super.isEqualTo(expected)
        }
    }

    /** Fails if the subject is not empty. */
    fun isEmpty() {
        requireNonNull(actual) { "Expected to be empty, but was null" }

        if (!actual.isEmpty()) {
            fail("Expected to be empty")
        }
    }

    /** Fails if the subject is empty. */
    fun isNotEmpty() {
        requireNonNull(actual) { "Expected not to be empty, but was null" }

        if (actual.isEmpty()) {
            fail("Expected to be not empty")
        }
    }

    /** Fails if the subject does not have the given size. */
    fun hasSize(expectedSize: Int) {
        require(expectedSize >= 0) { "expectedSize must be >= 0, but was $expectedSize" }
        requireNonNull(actual) { "Expected to have size $expectedSize, but was null" }

        assertEquals(expectedSize, actual.count())
    }

    /** Checks (with a side-effect failure) that the subject contains the supplied item. */
    fun contains(element: Any?) {
        requireNonNull(actual) { "Expected to contain $element, but was null" }

        if (element !in actual) {
            val matchingItems = actual.retainMatchingToString(listOf(element))
            if (matchingItems.isNotEmpty()) {
                fail(
                    "Expected to contain $element, but did not. " +
                        "Though it did contain $matchingItems"
                )
            } else {
                fail("Expected to contain $element, but did not")
            }
        }
    }

    /** Checks (with a side-effect failure) that the subject does not contain the supplied item. */
    fun doesNotContain(element: Any?) {
        requireNonNull(actual) { "Expected not to contain $element, but was null" }

        if (element in actual) {
            fail("Expected not to contain $element")
        }
    }

    fun containsExactlyElementsIn(required: Iterable<*>?): Ordered {
        val actualIter = requireNonNull(actual).iterator()
        val requiredIter = requireNonNull(required).iterator()

        if (!requiredIter.hasNext()) {
            if (actualIter.hasNext()) {
                isEmpty() // fails
            }

            return NoopOrdered
        }

        // Step through both iterators comparing elements pairwise.
        var isFirst = true
        while (actualIter.hasNext() && requiredIter.hasNext()) {
            val actualElement = actualIter.next()
            val requiredElement = requiredIter.next()

            // As soon as we encounter a pair of elements that differ, we know that inOrder()
            // cannot succeed, so we can check the rest of the elements more normally.
            // Since any previous pairs of elements we iterated over were equal, they have no
            // effect on the result now.
            if (actualElement != requiredElement) {
                if (isFirst && !actualIter.hasNext() && !requiredIter.hasNext()) {
                    /*
                     * There's exactly one actual element and exactly one expected element, and they don't
                     * match, so throw a ComparisonFailure. The logical way to do that would be
                     * `check(...).that(actualElement).isEqualTo(requiredElement)`. But isEqualTo has magic
                     * behavior for arrays and primitives, behavior that's inconsistent with how this method
                     * otherwise behaves. For consistency, we want to rely only on the equal() call we've
                     * already made. So we expose a special method for this and call it from here.
                     *
                     * TODO(b/135918662): Consider always throwing ComparisonFailure if there is exactly one
                     * missing and exactly one extra element, even if there were additional (matching)
                     * elements. However, this will probably be useful less often, and it will be tricky to
                     * explain. First, what would we say, "value of: iterable.onlyElementThatDidNotMatch()?"
                     * And second, it feels weirder to call out a single element when the expected and actual
                     * values had multiple elements. Granted, Fuzzy Truth already does this, so maybe it's OK?
                     * But Fuzzy Truth doesn't (yet) make the mismatched value so prominent.
                     */
                    fail("Expected $actualElement to be equal to $requiredElement, but was not")
                }

                // Missing elements; elements that are not missing will be removed as we iterate.
                // Missing elements; elements that are not missing will be removed as we iterate.
                val missing = ArrayList<Any?>()
                missing.add(requiredElement)
                missing.addAll(requiredIter.asSequence())

                // Extra elements that the subject had but shouldn't have.
                // Extra elements that the subject had but shouldn't have.
                val extra = ArrayList<T>()

                // Remove all actual elements from missing, and add any that weren't in missing
                // to extra.
                // Remove all actual elements from missing, and add any that weren't in missing
                // to extra.
                if (!missing.remove(actualElement)) {
                    extra.add(actualElement)
                }
                while (actualIter.hasNext()) {
                    val item = actualIter.next()
                    if (!missing.remove(item)) {
                        extra.add(item)
                    }
                }

                if (missing.isEmpty() && extra.isEmpty()) {
                    /*
                     * This containsExactly() call is a success. But the iterables were not in the same order,
                     * so return an object that will fail the test if the user calls inOrder().
                     */
                    return object : Ordered {
                        override fun inOrder() {
                            fail(
                                """
                                    Contents match. Expected the order to also match, but was not.
                                    Expected: $required.
                                    Actual: $actual.
                                """.trimIndent()
                            )
                        }
                    }
                }

                fail(
                    """
                        Contents do not match.
                        Expected: $required.
                        Actual: $actual.
                        Missing: $missing.
                        Unexpected: $extra.
                    """.trimIndent()
                )
            }

            isFirst = false
        }

        // Here, we must have reached the end of one of the iterators without finding any
        // pairs of elements that differ. If the actual iterator still has elements, they're
        // extras. If the required iterator has elements, they're missing elements.

        if (actualIter.hasNext()) {
            fail(
                """
                    Contents do not match.
                    Expected: $required.
                    Actual: $actual.
                    Unexpected: ${actualIter.asSequence().toList()}.
                """.trimIndent()
            )
        }

        if (requiredIter.hasNext()) {
            fail(
                """
                    Contents do not match.
                    Expected: $required.
                    Actual: $actual.
                    Missing: ${requiredIter.asSequence().toList()}.
                """.trimIndent()
            )
        }

        // If neither iterator has elements, we reached the end and the elements were in
        // order, so inOrder() can just succeed.
        return NoopOrdered
    }
}
