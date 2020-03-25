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

package androidx.ui.core

import androidx.annotation.FloatRange

/**
 * Draw content with modified opacity (alpha) that may be less than 1.
 *
 * Example usage:
 * @sample androidx.ui.framework.samples.OpacitySample
 *
 * @param opacity the fraction of children's alpha value.
 */
fun Modifier.drawOpacity(
    @FloatRange(from = 0.0, to = 1.0) opacity: Float
) = this + drawLayer(alpha = opacity)

/**
 * Makes its children partially transparent.
 *
 * Example usage:
 *
 * @sample androidx.ui.framework.samples.OpacitySample
 *
 * @param opacity the fraction of children's alpha value.
 */
@Deprecated(
    "Use Modifier.drawOpacity",
    replaceWith = ReplaceWith(
        "Modifier.drawOpacity(opacity)",
        "androidx.ui.core.Modifier",
        "androidx.ui.core.drawOpacity"
    )
)
fun drawOpacity(
    @FloatRange(from = 0.0, to = 1.0) opacity: Float
): Modifier = Modifier.drawOpacity(opacity)
