/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.node

/**
 * This tracks the hit test results to allow for minimum touch target and single-pass hit testing.
 * If there is a hit at the minimum touch target, searching for a hit within the layout bounds
 * can still continue, but the near miss is still tracked.
 *
 * The List<T> interface should only be used after hit testing has completed.
 *
 * @see LayoutNode.hitTest
 * @see LayoutNodeWrapper.hitTest
 * @see PointerInputDelegatingWrapper.hitTest
 */
internal class HitTestResult<T> : List<T> {
    private var values = arrayOfNulls<Any>(16)
    private var distancesFromEdge = FloatArray(16)
    private var hitDepth = -1

    override var size: Int = 0
        private set

    /**
     * `true` when there has been a direct hit within touch bounds ([hit] called) or
     * `false` otherwise.
     */
    fun hasHit(): Boolean {
        val distance = findBestHitDistance()
        return distance < 0f
    }

    private fun resizeToHitDepth() {
        for (i in (hitDepth + 1)..lastIndex) {
            values[i] = null
        }
        size = hitDepth + 1
    }

    /**
     * Returns `true` if [distanceFromEdge] is less than the previous value passed in
     * [hitInMinimumTouchTarget] or [speculativeHit].
     */
    fun isHitInMinimumTouchTargetBetter(distanceFromEdge: Float): Boolean {
        if (hitDepth == lastIndex) {
            return true
        }
        val bestDistance = findBestHitDistance()
        return bestDistance > distanceFromEdge
    }

    private fun findBestHitDistance(): Float {
        var bestDistance = Float.POSITIVE_INFINITY
        for (i in hitDepth + 1..lastIndex) {
            bestDistance = minOf(distancesFromEdge[i], bestDistance)
            if (bestDistance < 0f) {
                return bestDistance
            }
        }
        return bestDistance
    }

    /**
     * Records [node] as a hit, adding it to the [HitTestResult] or replacing the existing one.
     * Runs [childHitTest] to do further hit testing for children.
     */
    fun hit(node: T, childHitTest: () -> Unit) {
        hitInMinimumTouchTarget(node, -1f, childHitTest)
    }

    /**
     * Records [node] as a hit with [distanceFromEdge] distance, replacing any existing record.
     * Runs [childHitTest] to do further hit testing for children.
     */
    fun hitInMinimumTouchTarget(node: T, distanceFromEdge: Float, childHitTest: () -> Unit) {
        hitDepth++
        ensureContainerSize()
        values[hitDepth] = node
        distancesFromEdge[hitDepth] = distanceFromEdge
        resizeToHitDepth()
        childHitTest()
        hitDepth--
    }

    /**
     * Temporarily records [node] as a hit with [distanceFromEdge] distance and calls
     * [childHitTest] to record hits for children. If no children have hits, then
     * the hit is discarded. If a child had a hit, then [node] replaces an existing
     * hit.
     */
    fun speculativeHit(node: T, distanceFromEdge: Float, childHitTest: () -> Unit) {
        if (hitDepth == lastIndex) {
            // Speculation is easy. We don't have to do any array shuffling.
            hitInMinimumTouchTarget(node, distanceFromEdge, childHitTest)
            if (hitDepth + 1 == lastIndex) {
                // Discard the hit because there were no child hits.
                resizeToHitDepth()
            }
            return
        }

        // We have to tack the speculation to the end of the array
        val previousDistance = findBestHitDistance()
        val previousHitDepth = hitDepth
        hitDepth = lastIndex

        hitInMinimumTouchTarget(node, distanceFromEdge, childHitTest)
        if (hitDepth + 1 < lastIndex && previousDistance > findBestHitDistance()) {
            // This was a successful hit, so we should move this to the previous hit depth
            val fromIndex = hitDepth + 1
            val toIndex = previousHitDepth + 1
            values.copyInto(
                destination = values,
                destinationOffset = toIndex,
                startIndex = fromIndex,
                endIndex = size
            )
            distancesFromEdge.copyInto(
                destination = distancesFromEdge,
                destinationOffset = toIndex,
                startIndex = fromIndex,
                endIndex = size
            )

            // Discard the remainder of the hits
            hitDepth = previousHitDepth + size - hitDepth - 1
        }
        resizeToHitDepth()
        hitDepth = previousHitDepth
    }

    private fun ensureContainerSize() {
        if (hitDepth >= values.size) {
            val newSize = values.size + 16
            values = values.copyOf(newSize)
            distancesFromEdge = distancesFromEdge.copyOf(newSize)
        }
    }

    override fun contains(element: T): Boolean = indexOf(element) != -1

    override fun containsAll(elements: Collection<T>): Boolean {
        elements.forEach {
            if (!contains(it)) {
                return false
            }
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): T = values[index] as T

    override fun indexOf(element: T): Int {
        for (i in 0..lastIndex) {
            if (values[i] == element) {
                return i
            }
        }
        return -1
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<T> = HitTestResultIterator()

    override fun lastIndexOf(element: T): Int {
        for (i in lastIndex downTo 0) {
            if (values[i] == element) {
                return i
            }
        }
        return -1
    }

    override fun listIterator(): ListIterator<T> = HitTestResultIterator()

    override fun listIterator(index: Int): ListIterator<T> = HitTestResultIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<T> =
        SubList(fromIndex, toIndex)

    /**
     * Clears all entries to make an empty list.
     */
    fun clear() {
        hitDepth = -1
        resizeToHitDepth()
    }

    private inner class HitTestResultIterator(
        var index: Int = 0,
        val minIndex: Int = 0,
        val maxIndex: Int = size
    ) : ListIterator<T> {
        override fun hasNext(): Boolean = index < maxIndex

        override fun hasPrevious(): Boolean = index > minIndex

        @Suppress("UNCHECKED_CAST")
        override fun next(): T = values[index++] as T

        override fun nextIndex(): Int = index - minIndex

        @Suppress("UNCHECKED_CAST")
        override fun previous(): T = values[--index] as T

        override fun previousIndex(): Int = index - minIndex - 1
    }

    private inner class SubList(
        val minIndex: Int,
        val maxIndex: Int
    ) : List<T> {
        override val size: Int
            get() = maxIndex - minIndex

        override fun contains(element: T): Boolean = indexOf(element) != -1

        override fun containsAll(elements: Collection<T>): Boolean {
            elements.forEach {
                if (!contains(it)) {
                    return false
                }
            }
            return true
        }

        @Suppress("UNCHECKED_CAST")
        override fun get(index: Int): T = values[index + minIndex] as T

        override fun indexOf(element: T): Int {
            for (i in minIndex..maxIndex) {
                if (values[i] == element) {
                    return i - minIndex
                }
            }
            return -1
        }

        override fun isEmpty(): Boolean = size == 0

        override fun iterator(): Iterator<T> = HitTestResultIterator(minIndex, minIndex, maxIndex)

        override fun lastIndexOf(element: T): Int {
            for (i in maxIndex downTo minIndex) {
                if (values[i] == element) {
                    return i - minIndex
                }
            }
            return -1
        }

        override fun listIterator(): ListIterator<T> =
            HitTestResultIterator(minIndex, minIndex, maxIndex)

        override fun listIterator(index: Int): ListIterator<T> =
            HitTestResultIterator(minIndex + index, minIndex, maxIndex)

        override fun subList(fromIndex: Int, toIndex: Int): List<T> =
            SubList(minIndex + fromIndex, minIndex + toIndex)
    }
}