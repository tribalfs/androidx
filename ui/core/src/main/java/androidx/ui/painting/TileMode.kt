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

package androidx.ui.painting

// Defines what happens at the edge of the gradient.
//
// A gradient is defined along a finite inner area. In the case of a linear
// gradient, it's between the parallel lines that are orthogonal to the line
// drawn between two points. In the case of radial gradients, it's the disc
// that covers the circle centered on a particular point up to a given radius.
//
// This enum is used to define how the gradient should paint the regions
// outside that defined inner area.
//
// See also:
//
//  * [painting.Gradient], the superclass for [LinearGradient] and
//    [RadialGradient], as used by [BoxDecoration] et al, which works in
//    relative coordinates and can create a [Shader] representing the gradient
//    for a particular [Rect] on demand.
//  * [dart:ui.Gradient], the low-level class used when dealing with the
//    [Paint.shader] property directly, with its [new Gradient.linear] and [new
//    Gradient.radial] constructors.
// These enum values must be kept in sync with SkShader::TileMode.
enum class TileMode {
    // Edge is clamped to the final color.
    //
    // The gradient will paint the all the regions outside the inner area with
    // the color of the point closest to that region.
    //
    // ![](https://flutter.github.io/assets-for-api-docs/assets/dart-ui/tile_mode_clamp_radial.png)
    clamp,

    // Edge is repeated from first color to last.
    //
    // This is as if the stop points from 0.0 to 1.0 were then repeated from 1.0
    // to 2.0, 2.0 to 3.0, and so forth (and for linear gradients, similarly from
    // -1.0 to 0.0, -2.0 to -1.0, etc).
    //
    // ![](https://flutter.github.io/assets-for-api-docs/assets/dart-ui/tile_mode_repeated_linear.png)
    // ![](https://flutter.github.io/assets-for-api-docs/assets/dart-ui/tile_mode_repeated_radial.png)
    repeated,

    // Edge is mirrored from last color to first.
    //
    // This is as if the stop points from 0.0 to 1.0 were then repeated backwards
    // from 2.0 to 1.0, then forwards from 2.0 to 3.0, then backwards again from
    // 4.0 to 3.0, and so forth (and for linear gradients, similarly from in the
    // negative direction).
    //
    // ![](https://flutter.github.io/assets-for-api-docs/assets/dart-ui/tile_mode_mirror_linear.png)
    // ![](https://flutter.github.io/assets-for-api-docs/assets/dart-ui/tile_mode_mirror_radial.png)
    mirror
}