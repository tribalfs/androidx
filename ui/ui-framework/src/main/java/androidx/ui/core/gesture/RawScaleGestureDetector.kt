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
import androidx.ui.core.IntPxSize
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerInputChange
import androidx.ui.core.PointerInputWrapper
import androidx.ui.core.changedToDown
import androidx.ui.core.changedToUp
import androidx.ui.core.consumeDownChange
import androidx.ui.testutils.consume

/**
 * Observes various events sent by [RawScaleGestureDetector].  Implement and pass into
 * [RawScaleGestureDetector] so that [RawScaleGestureDetector] may call the functions when events
 * occur.
 */
interface RawScaleObserver {

    /**
     * Override to be notified when scaling has started.
     *
     * This will be called when scaling occurs (and when the associated [RawScaleGestureDetector]
     * is allowed to start). Always called just before [onScale] and isn't called again until
     * after [onStop].
     *
     * @see RawScaleGestureDetector
     * @see onScale
     * @see onStop
     */
    fun onStart() {}

    /**
     * Override to be notified when scaling has occurred.
     *
     * When overridden, return the amount of scaling, expressed as a scale factor, that should be
     * consumed.  For example, if the [scaleFactor] is 1.5 and the client wants to consume all of
     * the scaling, it should return 1.5.  If it wants to consume none of the scaling, it should
     * return 1.
     *
     * Always called just after [onStart] (and for every subsequent scale).
     *
     * @param scaleFactor The ratio of newSize / oldSize that the scaling gesture has expressed
     * between pointers last position and current position (this value is not cumulative over the
     * lifetime of of the gesture). For example, if 2 fingers are 10 pixel apart, and then move
     * such that they are 20 pixels apart, the scaleFactor will be 2.  If 2 fingers that are 20
     * pixels apart move such that they are 10 pixels apart, the scaleFactor will be .5.
     *
     * @return The amount scaling that was actually used.  This value should also be a scaleFactor
     * that is in the range of S to 1, where S is the value of [scaleFactor].  (If you are
     * scaling an image, just return the scaleFactor that the image actually scaled.  For
     * example, if [scaleFactor] is 2, and the image can only scale to 1.5, return 1.5). If you
     * don't want bother with "nested scaling" (you simply want to consume all of the pointer
     * movement related to scaling), just return the value of [scaleFactor].
     */
    fun onScale(scaleFactor: Float) = 1f

    /**
     * Override to be notified when scaling has stopped.
     *
     * This is called once no pointers remain.
     *
     * Only called after [onStart] and one or more calls to [onScale]
     */
    fun onStop() {}
}

/**
 * This gesture detector detects scaling.
 *
 * Scaling is when the average distance between a set of pointers changes over time.  It is
 * also known as pinch, or pinch to zoom.
 *
 * Note: By default, this gesture detector will start as soon as the average distance between
 * pointers changes by just a little bit. It is likely that you don't want to use this gesture
 * detector directly, but instead use a scale gesture detector that is less aggressive about
 * starting (such as [ScaleGestureDetector] which waits for a pointer to have passed touch slop
 * before starting).
 *
 * Scaling begins when the average distance between a set of pointers changes and either
 * [canStartScaling] is null or returns true.  When scaling begins, [RawScaleObserver.onStart] is
 * called followed immediately by a call to [RawScaleObserver.onScale].
 * [RawScaleObserver.onScale] is then continuously called whenever the movement of all pointers
 * denotes scaling. [RawScaleObserver.onStop] is called when no pointers remain.
 *
 * @param scaleObserver The callback interface to report all events related to scaling.
 * @param canStartScaling If set, before scaling is started ([RawScaleObserver.onStart] is called),
 * canStartScaling is called for each pointer event to check to see if it is allowed to start.
 */
@Composable
fun RawScaleGestureDetector(
    scaleObserver: RawScaleObserver,
    canStartScaling: (() -> Boolean)? = null,
    children: @Composable() () -> Unit
) {
    val recognizer = remember { RawScaleGestureRecognizer() }
    // TODO(b/129784010): Consider also allowing onStart, onScale, and onEnd to be set individually.
    recognizer.scaleObserver = scaleObserver
    recognizer.canStartScaling = canStartScaling

    PointerInputWrapper(pointerInputHandler = recognizer.pointerInputHandler, children = children)
}

internal class RawScaleGestureRecognizer {
    private var active = false
    lateinit var scaleObserver: RawScaleObserver
    var canStartScaling: (() -> Boolean)? = null

    val pointerInputHandler =
        { changes: List<PointerInputChange>, pass: PointerEventPass, _: IntPxSize ->

            var changesToReturn = changes

            if (pass == PointerEventPass.InitialDown && active) {
                // If we are currently scaling, we want to prevent any children from reacting to any
                // down change.
                changesToReturn = changesToReturn.map {
                    if (it.changedToDown() || it.changedToUp()) {
                        it.consumeDownChange()
                    } else {
                        it
                    }
                }
            }

            if (pass == PointerEventPass.PostUp) {

                var (currentlyDownChanges, otherChanges) = changesToReturn.partition {
                    it.current.down && it.previous.down
                }

                val scaleObserver = scaleObserver

                if (currentlyDownChanges.isEmpty()) {
                    if (active) {
                        active = false
                        scaleObserver.onStop()
                    }
                } else {
                    val dimensionInformation =
                        currentlyDownChanges.calculateAllDimensionInformation()
                    val scalePercentage = dimensionInformation.calculateScaleFactor()

                    // If all of the pointers somehow report the same number, scalePercentage could
                    // end up as NaN (because the average distance that each pointer is to the
                    // center of them all will be 0, which means that the scalePercentage should be
                    // infinite, which clearly isn't correct).
                    if (!scalePercentage.isNaN() && scalePercentage != 1f) {

                        if (!active && canStartScaling?.invoke() != false) {
                            active = true
                            scaleObserver.onStart()
                        }

                        if (active) {

                            val scalePercentageUsed = scaleObserver.onScale(scalePercentage)

                            val percentageOfChangeUsed =
                                (scalePercentageUsed - 1) / (scalePercentage - 1)

                            if (percentageOfChangeUsed > 0f) {
                                val newCurrentlyDownChanges = mutableListOf<PointerInputChange>()
                                for (i in currentlyDownChanges.indices) {

                                    val xVectorToAverageChange =
                                        getVectorToAverageChange(
                                            dimensionInformation.previousX,
                                            dimensionInformation.currentX,
                                            i
                                        ) * percentageOfChangeUsed

                                    val yVectorToAverageChange =
                                        getVectorToAverageChange(
                                            dimensionInformation.previousY,
                                            dimensionInformation.currentY,
                                            i
                                        ) * percentageOfChangeUsed

                                    newCurrentlyDownChanges
                                        .add(
                                            currentlyDownChanges[i].consume(
                                                xVectorToAverageChange,
                                                yVectorToAverageChange
                                            )
                                        )
                                }
                                currentlyDownChanges = newCurrentlyDownChanges
                            }
                        }
                    }
                }

                changesToReturn = currentlyDownChanges + otherChanges
            }

            changesToReturn
        }
}
