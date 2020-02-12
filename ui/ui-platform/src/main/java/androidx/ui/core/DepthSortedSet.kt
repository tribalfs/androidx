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

package androidx.ui.core

import java.util.TreeSet

/**
 * The set of [ComponentNode]s which orders items by their [ComponentNode.depth] and
 * allows modifications(additions and removals) while we iterate through it via [popEach].
 * While [ComponentNode] is added to the set it should always be:
 * 1) attached [ComponentNode.isAttached] == true
 * 2) maintaining the same [ComponentNode.depth]
 * as any of this modifications can break the comparator's contract which can cause
 * to not find the item in the tree set, which we previously added.
 */
internal class DepthSortedSet<T : ComponentNode>(
    private val extraAssertions: Boolean = true
) {
    // stores the depth used when the node was added into the set so we can assert it wasn't
    // changed since then. we need to enforce this as changing the depth can break the contract
    // used in comparator for building the tree in TreeSet.
    // Created and used only when extraAssertions == true
    private val mapOfOriginalDepth by lazy(LazyThreadSafetyMode.NONE) {
        mutableMapOf<T, Int>()
    }
    private val DepthComparator: Comparator<T> = object : Comparator<T> {
        override fun compare(l1: T, l2: T): Int {
            val depthDiff = l1.depth.compareTo(l2.depth)
            if (depthDiff != 0) {
                return depthDiff
            }
            return System.identityHashCode(l1).compareTo(System.identityHashCode(l2))
        }
    }
    private val set = TreeSet<T>(DepthComparator)

    fun contains(node: T): Boolean {
        val contains = set.contains(node)
        if (extraAssertions) {
            check(contains == mapOfOriginalDepth.containsKey(node))
        }
        return contains
    }

    fun add(node: T) {
        check(node.isAttached())
        if (extraAssertions) {
            val usedDepth = mapOfOriginalDepth[node]
            if (usedDepth == null) {
                mapOfOriginalDepth[node] = node.depth
            } else {
                check(usedDepth == node.depth)
            }
        }
        set.add(node)
    }

    fun remove(node: T) {
        check(node.isAttached())
        val contains = set.remove(node)
        if (extraAssertions) {
            val usedDepth = mapOfOriginalDepth.remove(node)
            if (contains) {
                check(usedDepth == node.depth)
            } else {
                check(usedDepth == null)
            }
        }
    }

    fun pop(): T {
        val node = set.first()
        remove(node)
        return node
    }

    inline fun popEach(crossinline block: (T) -> Unit) {
        while (isNotEmpty()) {
            val node = pop()
            block(node)
        }
    }

    fun isEmpty(): Boolean = set.isEmpty()

    @Suppress("NOTHING_TO_INLINE")
    inline fun isNotEmpty(): Boolean = !isEmpty()

    override fun toString(): String {
        return set.toString()
    }
}
