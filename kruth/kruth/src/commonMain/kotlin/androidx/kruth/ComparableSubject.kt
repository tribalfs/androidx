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

import androidx.kruth.Fact.Companion.fact

/**
 * Propositions for [Comparable] typed subjects.
 *
 * @param T the type of the object being tested by this [ComparableSubject]
 */
open class ComparableSubject<T : Comparable<T>> internal constructor(
    actual: T?,
    metadata: FailureMetadata = FailureMetadata(),
) : Subject<T>(actual = actual, metadata = metadata) {

    /** Checks that the subject is in [range]. */
    fun isIn(range: Range<T>) {
        if (requireNonNull(actual) !in range) {
            failWithoutActual("Expected to be in range", range)
        }
    }

    /** Checks that the subject is *not* in [range]. */
    fun isNotIn(range: Range<T>) {
        if (requireNonNull(actual) in range) {
            failWithoutActual("Expected not to be in range", range)
        }
    }

    /** Checks that the subject is in [range]. */
    fun isInRange(range: ClosedRange<T>) {
        if (requireNonNull(actual) !in range) {
            failWithoutActual(fact("Expected to be in range", range))
        }
    }

    /** Checks that the subject is *not* in [range]. */
    fun isNotInRange(range: ClosedRange<T>) {
        if (requireNonNull(actual) in range) {
            failWithoutActual(fact("Expected not to be in range", range))
        }
    }

    /**
     * Checks that the subject is equivalent to [other] according to [Comparable.compareTo],
     * (i.e., checks that `a.comparesTo(b) == 0`).
     *
     * **Note:** Do not use this method for checking object equality. Instead, use [isEqualTo].
     */
    open fun isEquivalentAccordingToCompareTo(other: T?) {
        requireNonNull(actual)
        requireNonNull(other)

        if (actual.compareTo(other) != 0) {
            failWithActual("Expected value that sorts equal to", other)
        }
    }

    /**
     * Checks that the subject is greater than [other].
     *
     * To check that the subject is greater than *or equal to* [other], use [isAtLeast].
     */
    fun isGreaterThan(other: T?) {
        requireNonNull(actual)
        requireNonNull(other)

        if (actual <= other) {
            failWithActual("Expected to be greater than", other)
        }
    }

    /**
     * Checks that the subject is less than [other].
     *
     * @throws NullPointerException if [actual] or [other] is `null`.
     */
    fun isLessThan(other: T?) {
        requireNonNull(actual) { "Expected to be less than $other, but was $actual" }
        requireNonNull(other) { "Expected to be less than $other, but was $actual" }

        if (actual >= other) {
            failWithActual("Expected to be less than", other)
        }
    }

    /**
     * Checks that the subject is less than or equal to [other].
     *
     * @throws NullPointerException if [actual] or [other] is `null`.
     */
    fun isAtMost(other: T?) {
        requireNonNull(actual) { "Expected to be at most $other, but was $actual" }
        requireNonNull(other) { "Expected to be at most $other, but was $actual" }
        if (actual > other) {
            failWithActual("Expected to be at most", other)
        }
    }

    /**
     * Checks that the subject is greater than or equal to [other].
     *
     * @throws NullPointerException if [actual] or [other] is `null`.
     */
    fun isAtLeast(other: T?) {
        requireNonNull(actual) { "Expected to be at least $other, but was $actual" }
        requireNonNull(other) { "Expected to be at least $other, but was $actual" }
        if (actual < other) {
            failWithActual("Expected to be at least", other)
        }
    }
}
