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

package androidx.ui.core.gesture

import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.core.DensityAmbient
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerInputChange
import androidx.ui.core.PointerInput
import androidx.ui.core.changedToUpIgnoreConsumed
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.Px
import kotlin.math.absoluteValue

// TODO(b/143877464): Implement a "can scale in / can scale out" check so that scale slop is only
//  surpassed in the appropriate direction?
/**
 * This gesture detector detects when a user's pointer input is intended to include scaling.
 *
 * This gesture detector is very similar to [RawScaleGestureDetector] except that instead of
 * providing callbacks for scaling, it instead provides one callback for when a user is intending
 * to scale.  It does so using the same semantics as [RawScaleGestureDetector], and simply waits
 * until the user has scaled just enough to suggest the user is truly intended to scale.
 *
 * The gesture is considered to include scaling when the absolute cumulative average change in
 * distance of all pointers from the average pointer over time surpasses a particular value
 * (currently [ScaleSlop]).
 *
 * For example, if the [ScaleSlop] is 5 pixels and 2 pointers were 1 pixel away from each
 * other and now are 11.00001 pixels away from each other, the slop will have been surpassed and
 * [onScaleSlopExceeded] will be called (both pointers are slightly more than 5 pixels away from
 * the average of the pointers than they were).
 */
@Composable
fun ScaleSlopExceededGestureDetector(
    onScaleSlopExceeded: () -> Unit,
    children: @Composable() () -> Unit
) {
    val scaleSlop = with(DensityAmbient.current) { ScaleSlop.toPx() }
    val recognizer = remember { ScaleSlopExceededGestureRecognizer(scaleSlop) }
    // TODO(b/129784010): Consider also allowing onStart, onScale, and onEnd to be set individually.
    recognizer.onScaleSlopExceeded = onScaleSlopExceeded

    PointerInput(
        pointerInputHandler = recognizer.pointerInputHandler,
        cancelHandler = recognizer.cancelHandler,
        children = children
    )
}

/**
 * @param scaleSlop The absolute cumulative average change in distance of all pointers from the
 * average pointer over time that must be surpassed to indicate the user is trying to scale.
 *
 * @see ScaleSlopExceededGestureDetector
 */
internal class ScaleSlopExceededGestureRecognizer(private val scaleSlop: Px) {
    lateinit var onScaleSlopExceeded: () -> Unit

    var passedSlop = false
    var scaleDiffTotal = 0f

    val pointerInputHandler =
        { changes: List<PointerInputChange>, pass: PointerEventPass, _: IntPxSize ->

            if (pass == PointerEventPass.PostUp) {

                if (!passedSlop) {

                    val currentlyDownChanges =
                        changes.filter { it.current.down && it.previous.down }

                    if (currentlyDownChanges.isNotEmpty()) {
                        val dimensionInformation =
                            currentlyDownChanges.calculateAllDimensionInformation()
                        val scaleDifference = dimensionInformation.calculateScaleDifference()

                        scaleDiffTotal += scaleDifference

                        if (scaleDiffTotal.absoluteValue > scaleSlop.value) {
                            passedSlop = true
                            onScaleSlopExceeded.invoke()
                        }
                    }
                }
            }

            if (passedSlop &&
                pass == PointerEventPass.PostDown &&
                changes.all { it.changedToUpIgnoreConsumed() }
            ) {
                passedSlop = false
                scaleDiffTotal = 0f
            }

            changes
        }

    val cancelHandler = {
        passedSlop = false
        scaleDiffTotal = 0f
    }
}
