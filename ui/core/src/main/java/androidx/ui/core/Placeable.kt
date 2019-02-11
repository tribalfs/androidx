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

/**
 * A [Placeable] corresponds to a child which can be positioned by its parent container.
 * Most [Placeable]s are the result of a [Measureable.measure] call.
 */
// TODO(popam): investigate if this interface is really needed, as it blocks making
//              MeasuredPlaceable an inline class
interface Placeable {
    val width: Dimension
    val height: Dimension
    // TODO(popam): make this possible only inside the layoutResult lambda
    fun place(x: Dimension, y: Dimension)
}