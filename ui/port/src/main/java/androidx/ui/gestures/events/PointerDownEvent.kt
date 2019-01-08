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

package androidx.ui.gestures.events

import androidx.ui.core.Duration
import androidx.ui.engine.geometry.Offset
import androidx.ui.ui.pointer.PointerDeviceKind

// /// The pointer has made contact with the device.
class PointerDownEvent(
    timeStamp: Duration = Duration.zero,
    pointer: Int = 0,
    kind: PointerDeviceKind = PointerDeviceKind.touch,
    device: Int = 0,
    position: Offset = Offset.zero,
    buttons: Int = 0,
    obscured: Boolean = false,
    pressure: Float = 1.0f,
    pressureMin: Float = 1.0f,
    pressureMax: Float = 1.0f,
    distanceMax: Float = 0.0f,
    radiusMajor: Float = 0.0f,
    radiusMinor: Float = 0.0f,
    radiusMin: Float = 0.0f,
    radiusMax: Float = 0.0f,
    orientation: Float = 0.0f,
    tilt: Float = 0.0f
) : PointerEvent(
    timeStamp = timeStamp,
    pointer = pointer,
    kind = kind,
    device = device,
    position = position,
    buttons = buttons,
    down = true,
    obscured = obscured,
    pressure = pressure,
    pressureMin = pressureMin,
    pressureMax = pressureMax,
    distance = 0.0f,
    distanceMax = distanceMax,
    radiusMajor = radiusMajor,
    radiusMinor = radiusMinor,
    radiusMin = radiusMin,
    radiusMax = radiusMax,
    orientation = orientation,
    tilt = tilt
)
