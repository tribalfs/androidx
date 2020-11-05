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

package androidx.compose.ui.platform

import androidx.compose.ui.unit.Duration

/**
 * Contains methods to standard constants used in the UI for timeouts, sizes, and distances.
 */
interface ViewConfiguration {
    /**
     * The duration before a press turns into a long press.
     */
    val longPressTimeout: Duration

    /**
     * The duration between the first tap's up event and the second tap's down
     * event for an interaction to be considered a double-tap.
     */
    val doubleTapTimeout: Duration

    /**
     * The minimum duration between the first tap's up event and the second tap's down event for
     * an interaction to be considered a double-tap.
     */
    val doubleTapMinTime: Duration

    /**
     * Distance in pixels a touch can wander before we think the user is scrolling.
     */
    val touchSlop: Float
}
