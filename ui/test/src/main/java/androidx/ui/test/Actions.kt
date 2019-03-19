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

import android.os.SystemClock
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_UP

fun SemanticsTreeQuery.doClick(): SemanticsTreeQuery {
    val foundNodes = findAllMatching()
    if (foundNodes.size != 1) {
        throw AssertionError("Found '${foundNodes.size}' nodes but 1 was expected!")
    }

    // TODO(catalintudor): get real coordonates after Semantics API is ready (b/125702443)
    val globalCoordinates = foundNodes[0].globalPosition
        ?: throw AssertionError("Semantic Node has no child layout to perform click on!")
    val x = globalCoordinates.x.value + 1f
    val y = globalCoordinates.y.value + 1f

    val eventDown = MotionEvent.obtain(SystemClock.uptimeMillis(), 10, ACTION_DOWN, x, y, 0)
    sendEvent(eventDown)
    eventDown.recycle()

    val eventUp = MotionEvent.obtain(SystemClock.uptimeMillis(), 10, ACTION_UP, x, y, 0)
    sendEvent(eventUp)
    eventUp.recycle()

    return this
}