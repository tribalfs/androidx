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

package androidx.compose.material3.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect

/**
 * Posture info that can help make layout adaptation decisions. For example when
 * [Posture.separatingHingeBounds] is not empty, the layout may want to avoid putting any content
 * over those hinge areaa. We suggest to use [calculatePosture] to retrieve instances of this class
 * in applications, unless you have a strong need of customization that cannot be fulfilled by the
 * default implementation.
 *
 * @constructor create an instance of [Posture]
 * @param isTabletop `true` if the current window is considered as in the table top mode, i.e. there
 *        is one half-opened horizontal hinge in the middle of the current window. When this is
 *        `true` it usually means it's hard for users to interact with the window area around
 *        the hinge and developers may consider separating the layout along the hinge and show
 *        software keyboard or other controls in the bottom half of the window.
 * @param separatingHingeBounds the list of hinge bounds that are separating.
 * @param occludingHingeBounds the list of hinge bounds that are occluding.
 * @param allHingeBounds the list of all hinge bounds.
 */
@ExperimentalMaterial3AdaptiveApi
@Immutable
class Posture(
    val isTabletop: Boolean = false,
    val separatingHingeBounds: List<Rect> = emptyList(),
    val occludingHingeBounds: List<Rect> = emptyList(),
    val allHingeBounds: List<Rect> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Posture) return false
        if (isTabletop != other.isTabletop) return false
        if (separatingHingeBounds != other.separatingHingeBounds) return false
        if (occludingHingeBounds != other.occludingHingeBounds) return false
        if (allHingeBounds != other.allHingeBounds) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isTabletop.hashCode()
        result = 31 * result + separatingHingeBounds.hashCode()
        result = 31 * result + occludingHingeBounds.hashCode()
        result = 31 * result + allHingeBounds.hashCode()
        return result
    }
}
