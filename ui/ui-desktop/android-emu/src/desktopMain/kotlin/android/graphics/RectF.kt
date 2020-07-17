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

package android.graphics

class RectF(
    @kotlin.jvm.JvmField
    var left: Float,
    @kotlin.jvm.JvmField
    var top: Float,
    @kotlin.jvm.JvmField
    var right: Float,
    @kotlin.jvm.JvmField
    var bottom: Float
) {
    constructor() : this(0f, 0f, 0f, 0f)

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun set(src: RectF) {
        this.left = src.left
        this.top = src.top
        this.right = src.right
        this.bottom = src.bottom
    }

    fun set(src: Rect) {
        this.left = src.left.toFloat()
        this.top = src.top.toFloat()
        this.right = src.right.toFloat()
        this.bottom = src.bottom.toFloat()
    }
}