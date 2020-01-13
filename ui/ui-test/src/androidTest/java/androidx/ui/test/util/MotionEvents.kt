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

package androidx.ui.test.util

import android.content.ContextWrapper
import android.view.MotionEvent
import androidx.ui.core.SemanticsTreeNode
import androidx.ui.core.SemanticsTreeProvider
import androidx.ui.test.android.CollectedProviders
import androidx.ui.unit.PxPosition
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs

internal class MotionEventRecorder : SemanticsTreeProvider {

    private val _events = mutableListOf<MotionEvent>()
    val events get() = _events as List<MotionEvent>

    fun clear() {
        _events.removeAll { it.recycle(); true }
    }

    fun asCollectedProviders(): CollectedProviders {
        return CollectedProviders(ContextWrapper(null), setOf(this))
    }

    override fun getAllSemanticNodes(): List<SemanticsTreeNode> {
        throw NotImplementedError()
    }

    override fun sendEvent(event: MotionEvent) {
        _events.add(MotionEvent.obtain(event))
    }
}

val MotionEvent.relativeTime get() = eventTime - downTime

val List<MotionEvent>.relativeEventTimes get() = map { it.relativeTime }

val List<MotionEvent>.moveEvents
    get() = filter { it.action == MotionEvent.ACTION_MOVE }

/**
 * Asserts that all event times are after their corresponding down time, and that the event
 * stream has increasing event times.
 */
internal fun MotionEventRecorder.assertHasValidEventTimes() {
    events.fold(0L) { previousTime, event ->
        assertThat(event.relativeTime).isAtLeast(previousTime)
        event.relativeTime
    }
}

internal fun MotionEvent.verify(
    curve: (Long) -> PxPosition,
    expectedAction: Int,
    expectedRelativeTime: Long
) {
    verify(curve(expectedRelativeTime), expectedAction, expectedRelativeTime)
}

internal fun MotionEvent.verify(
    expectedPosition: PxPosition,
    expectedAction: Int,
    expectedRelativeTime: Long
) {
    assertThat(action).isEqualTo(expectedAction)
    assertThat(relativeTime).isEqualTo(expectedRelativeTime)
    // x and y can just be taken from the function. We're not testing the function, we're
    // testing if the MotionEvent sampled the function at the correct point
    assertThat(x).isEqualTo(expectedPosition.x.value)
    assertThat(y).isEqualTo(expectedPosition.y.value)
}

/**
 * Returns a list of all events between [t0] and [t1], excluding [t0] and including [t1].
 */
fun List<MotionEvent>.between(t0: Long, t1: Long): List<MotionEvent> {
    return dropWhile { it.relativeTime <= t0 }.takeWhile { it.relativeTime <= t1 }
}

/**
 * Checks that the coordinates are progressing in a monotonous direction
 */
fun List<MotionEvent>.isMonotonousBetween(start: PxPosition, end: PxPosition) {
    map { it.x }.isMonotonousBetween(start.x.value, end.x.value, 1e-6f)
    map { it.y }.isMonotonousBetween(start.y.value, end.y.value, 1e-6f)
}

/**
 * Verifies that the MotionEvents in this list are equidistant from each other in time between
 * [t0] and [t1], with a duration between them that is as close to the [desiredDuration] as
 * possible, given that the sequence is splitting the total duration between [t0] and [t1].
 */
fun List<MotionEvent>.splitsDurationEquallyInto(t0: Long, t1: Long, desiredDuration: Long) {
    val totalDuration = t1 - t0
    if (totalDuration < desiredDuration) {
        assertThat(this).hasSize(1)
        assertThat(first().relativeTime - t0).isEqualTo(totalDuration)
        return
    }

    // Either `desiredDuration` divides `totalDuration` perfectly, or it doesn't.
    // If it doesn't, `desiredDuration * size` must be as close to `totalDuration` as possible.
    // Verify that `desiredDuration * size` for any other number of events will be further away
    // from `totalDuration`. If the diff with `totalDuration` is the same, the higher value gets
    // precedence.
    val actualDiff = abs(totalDuration - desiredDuration * size)
    val oneLessDiff = abs(totalDuration - desiredDuration * (size - 1))
    val oneMoreDiff = abs(totalDuration - desiredDuration * (size + 1))
    assertThat(actualDiff).isAtMost(oneLessDiff)
    assertThat(actualDiff).isLessThan(oneMoreDiff)

    // Check that the timestamps are within .5 of the unrounded splits
    forEachIndexed { i, event ->
        assertThat((event.relativeTime - t0).toFloat()).isWithin(.5f).of(
            ((i + 1) / size.toDouble() * totalDuration).toFloat()
        )
    }
}
