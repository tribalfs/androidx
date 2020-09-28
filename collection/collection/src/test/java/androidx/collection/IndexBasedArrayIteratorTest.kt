/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.collection

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class IndexBasedArrayIteratorTest {

    @Test
    fun iterateAll() {
        val iterator = ArraySet(setOf("a", "b", "c")).iterator()
        assertThat(iterator.asSequence().toList()).containsExactly("a", "b", "c")
    }

    @Test
    fun iterateEmptyList() {
        val iterator = ArraySet<String>().iterator()
        assertThat(iterator.hasNext()).isFalse()

        assertThat(
            runCatching {
                iterator.next()
            }.exceptionOrNull()
        ).isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun removeSameItemTwice() {
        val iterator = ArraySet(listOf("a", "b", "c")).iterator()
        iterator.next() // move to next
        iterator.remove()
        assertThat(
            runCatching {
                iterator.remove()
            }.exceptionOrNull()
        ).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun removeLast() = removeViaIterator(
        original = setOf("a", "b", "c"),
        toBeRemoved = setOf("c"),
        expected = setOf("a", "b")
    )

    @Test
    fun removeFirst() = removeViaIterator(
        original = setOf("a", "b", "c"),
        toBeRemoved = setOf("a"),
        expected = setOf("b", "c")
    )

    @Test
    fun removeMid() = removeViaIterator(
        original = setOf("a", "b", "c"),
        toBeRemoved = setOf("b"),
        expected = setOf("a", "c")
    )

    @Test
    fun removeConsecutive() = removeViaIterator(
        original = setOf("a", "b", "c", "d"),
        toBeRemoved = setOf("b", "c"),
        expected = setOf("a", "d")
    )

    @Test
    fun removeLastTwo() = removeViaIterator(
        original = setOf("a", "b", "c", "d"),
        toBeRemoved = setOf("c", "d"),
        expected = setOf("a", "b")
    )

    @Test
    fun removeFirstTwo() = removeViaIterator(
        original = setOf("a", "b", "c", "d"),
        toBeRemoved = setOf("a", "b"),
        expected = setOf("c", "d")
    )

    @Test
    fun removeMultiple() = removeViaIterator(
        original = setOf("a", "b", "c", "d"),
        toBeRemoved = setOf("a", "c"),
        expected = setOf("b", "d")
    )

    private fun removeViaIterator(
        original: Set<String>,
        toBeRemoved: Set<String>,
        expected: Set<String>
    ) {
        val subject = ArraySet(original)
        val iterator = subject.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next in toBeRemoved) {
                iterator.remove()
            }
        }
        assertThat(subject).containsExactlyElementsIn(expected)
    }
}
