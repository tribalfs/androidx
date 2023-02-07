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

@file:Suppress("NOTHING_TO_INLINE")
@file:RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java

package androidx.camera.camera2.pipe.core

import android.os.SystemClock
import androidx.annotation.RequiresApi
import javax.inject.Inject
import javax.inject.Singleton

/** A nanosecond timestamp */
@JvmInline
value class TimestampNs constructor(val value: Long) {
    inline operator fun minus(other: TimestampNs): DurationNs =
        DurationNs(value - other.value)

    inline operator fun plus(other: DurationNs): TimestampNs =
        TimestampNs(value + other.value)
}

@JvmInline
value class DurationNs(val value: Long) {
    inline operator fun minus(other: DurationNs): DurationNs =
        DurationNs(value - other.value)

    inline operator fun plus(other: DurationNs): DurationNs = DurationNs(value + other.value)

    inline operator fun plus(other: TimestampNs): TimestampNs =
        TimestampNs(value + other.value)

    operator fun compareTo(other: DurationNs): Int {
        return if (value == other.value) {
            0
        } else if (value < other.value) {
            -1
        } else {
            1
        }
    }

    companion object {
        inline fun fromMs(durationMs: Long) = DurationNs(durationMs * 1_000_000L)
    }
}

interface TimeSource {
    fun now(): TimestampNs
}

@Singleton
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now() = TimestampNs(SystemClock.elapsedRealtimeNanos())
}

@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
object Timestamps {
    inline fun now(timeSource: TimeSource): TimestampNs = timeSource.now()

    inline fun DurationNs.formatNs(): String = "$this ns"
    inline fun DurationNs.formatMs(decimals: Int = 3): String =
        "%.${decimals}f ms".format(null, this.value / 1_000_000.0)

    inline fun TimestampNs.formatNs(): String = "$this ns"
    inline fun TimestampNs.formatMs(): String = "${this.value / 1_000_000} ms"
    inline fun TimestampNs.measureNow(timeSource: TimeSource = SystemTimeSource()) =
        now(timeSource) - this
}
