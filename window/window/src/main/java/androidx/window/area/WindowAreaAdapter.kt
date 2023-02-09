/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.window.area

import androidx.window.core.ExperimentalWindowApi
import androidx.window.extensions.area.WindowAreaComponent

/**
 * Adapter object to assist in translating values received from [WindowAreaComponent]
 * to developer friendly values in [WindowAreaController]
 */
@ExperimentalWindowApi
internal object WindowAreaAdapter {

    internal fun translate(status: @WindowAreaComponent.WindowAreaStatus Int): WindowAreaStatus {
        return when (status) {
            WindowAreaComponent.STATUS_AVAILABLE -> WindowAreaStatus.AVAILABLE
            WindowAreaComponent.STATUS_UNAVAILABLE -> WindowAreaStatus.UNAVAILABLE
            else -> WindowAreaStatus.UNSUPPORTED
        }
    }
}