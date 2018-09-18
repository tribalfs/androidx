/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.gestures

import androidx.ui.core.Duration

// / Modeled after Android's ViewConfiguration:
// / https://github.com/android/platform_frameworks_base/blob/master/core/java/android/view/ViewConfiguration.java

// / The time that must elapse before a tap gesture sends onTapDown, if there's
// / any doubt that the gesture is a tap.
val kPressTimeout: Duration = Duration.create(milliseconds = 100)

// / Maximum length of time between a tap down and a tap up for the gesture to be
// / considered a tap. (Currently not honored by the TapGestureRecognizer.)
// TODO(ianh): Remove this, or implement a hover-tap gesture recognizer which
// uses this.
val kHoverTapTimeout: Duration = Duration.create(milliseconds = 150)

// / Maximum distance between the down and up pointers for a tap. (Currently not
// / honored by the [TapGestureRecognizer]; [PrimaryPointerGestureRecognizer],
// / which TapGestureRecognizer inherits from, uses [kTouchSlop].)
// TODO(ianh): Remove this or implement it correctly.
const val kHoverTapSlop: Double = 20.0 // Logical pixels

// / The time before a long press gesture attempts to win.
val kLongPressTimeout: Duration = Duration.create(milliseconds = 500)

// / The maximum time from the start of the first tap to the start of the second
// / tap in a double-tap gesture.
// TODO(ianh): In Android, this is actually the time from the first's up event
// to the second's down event, according to the ViewConfiguration docs.
val kDoubleTapTimeout: Duration = Duration.create(milliseconds = 300)

// / The minimum time from the end of the first tap to the start of the second
// / tap in a double-tap gesture. (Currently not honored by the
// / DoubleTapGestureRecognizer.)
// TODO(ianh): Either implement this or remove the constant.
val kDoubleTapMinTime: Duration = Duration.create(milliseconds = 40)

// / The distance a touch has to travel for the framework to be confident that
// / the gesture is a scroll gesture, or, inversely, the maximum distance that a
// / touch can travel before the framework becomes confident that it is not a
// / tap.
// This value was empirically derived. We started at 8.0 and increased it to
// 18.0 after getting complaints that it was too difficult to hit targets.
const val kTouchSlop: Double = 18.0 // Logical pixels

// / The maximum distance that the first touch in a double-tap gesture can travel
// / before deciding that it is not part of a double-tap gesture.
// / DoubleTapGestureRecognizer also restricts the second touch to this distance.
const val kDoubleTapTouchSlop: Double = kTouchSlop // Logical pixels

// / Distance between the initial position of the first touch and the start
// / position of a potential second touch for the second touch to be considered
// / the second touch of a double-tap gesture.
const val kDoubleTapSlop: Double = 100.0 // Logical pixels

// / The time for which zoom controls (e.g. in a map interface) are to be
// / displayed on the screen, from the moment they were last requested.
val kZoomControlsTimeout: Duration = Duration.create(milliseconds = 3000)

// / The distance a touch has to travel for the framework to be confident that
// / the gesture is a paging gesture. (Currently not used, because paging uses a
// / regular drag gesture, which uses kTouchSlop.)
// TODO(ianh): Create variants of HorizontalDragGestureRecognizer et al for
// paging, which use this constant.
const val kPagingTouchSlop: Double = kTouchSlop * 2.0 // Logical pixels

// / The distance a touch has to travel for the framework to be confident that
// / the gesture is a panning gesture.
const val kPanSlop: Double = kTouchSlop * 2.0 // Logical pixels

// / The distance a touch has to travel for the framework to be confident that
// / the gesture is a scale gesture.
const val kScaleSlop: Double = kTouchSlop // Logical pixels

// / The margin around a dialog, popup menu, or other window-like widget inside
// / which we do not consider a tap to dismiss the widget. (Not currently used.)
// TODO(ianh): Make ModalBarrier support this.
const val kWindowTouchSlop: Double = 16.0 // Logical pixels

// / The minimum velocity for a touch to consider that touch to trigger a fling
// / gesture.
// TODO(ianh): Make sure nobody has their own version of this.
const val kMinFlingVelocity: Double = 50.0 // Logical pixels / second
// const Velocity kMinFlingVelocity = const Velocity(pixelsPerSecond: 50.0)

// / Drag gesture fling velocities are clipped to this value.
// TODO(ianh): Make sure nobody has their own version of this.
const val kMaxFlingVelocity: Double = 8000.0 // Logical pixels / second

// / The maximum time from the start of the first tap to the start of the second
// / tap in a jump-tap gesture.
// TODO(ianh): Implement jump-tap gestures.
val kJumpTapTimeout: Duration = Duration.create(milliseconds = 500)
