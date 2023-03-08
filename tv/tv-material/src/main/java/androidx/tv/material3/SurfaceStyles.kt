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

package androidx.tv.material3

import androidx.annotation.FloatRange
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Defines [Shape] for all TV [Interaction] states of a Clickable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ClickableSurfaceShape internal constructor(
    internal val shape: Shape,
    internal val focusedShape: Shape,
    internal val pressedShape: Shape,
    internal val disabledShape: Shape,
    internal val focusedDisabledShape: Shape
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClickableSurfaceShape

        if (shape != other.shape) return false
        if (focusedShape != other.focusedShape) return false
        if (pressedShape != other.pressedShape) return false
        if (disabledShape != other.disabledShape) return false
        if (focusedDisabledShape != other.focusedDisabledShape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + focusedShape.hashCode()
        result = 31 * result + pressedShape.hashCode()
        result = 31 * result + disabledShape.hashCode()
        result = 31 * result + focusedDisabledShape.hashCode()

        return result
    }

    override fun toString(): String {
        return "ClickableSurfaceShape(shape=$shape, focusedShape=$focusedShape, " +
            "pressedShape=$pressedShape, disabledShape=$disabledShape, " +
            "focusedDisabledShape=$focusedDisabledShape)"
    }
}

/**
 * Defines [Shape] for all TV [Interaction] states of a toggleable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ToggleableSurfaceShape internal constructor(
    internal val shape: Shape,
    internal val focusedShape: Shape,
    internal val pressedShape: Shape,
    internal val selectedShape: Shape,
    internal val disabledShape: Shape,
    internal val focusedSelectedShape: Shape,
    internal val focusedDisabledShape: Shape,
    internal val pressedSelectedShape: Shape,
    internal val selectedDisabledShape: Shape,
    internal val focusedSelectedDisabledShape: Shape
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ToggleableSurfaceShape

        if (shape != other.shape) return false
        if (focusedShape != other.focusedShape) return false
        if (pressedShape != other.pressedShape) return false
        if (selectedShape != other.selectedShape) return false
        if (disabledShape != other.disabledShape) return false
        if (focusedSelectedShape != other.focusedSelectedShape) return false
        if (focusedDisabledShape != other.focusedDisabledShape) return false
        if (pressedSelectedShape != other.pressedSelectedShape) return false
        if (selectedDisabledShape != other.selectedDisabledShape) return false
        if (focusedSelectedDisabledShape != other.focusedSelectedDisabledShape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + focusedShape.hashCode()
        result = 31 * result + pressedShape.hashCode()
        result = 31 * result + selectedShape.hashCode()
        result = 31 * result + disabledShape.hashCode()
        result = 31 * result + focusedSelectedShape.hashCode()
        result = 31 * result + focusedDisabledShape.hashCode()
        result = 31 * result + pressedSelectedShape.hashCode()
        result = 31 * result + selectedDisabledShape.hashCode()
        result = 31 * result + focusedSelectedDisabledShape.hashCode()

        return result
    }

    override fun toString(): String {
        return "ToggleableSurfaceShape(shape=$shape, focusedShape=$focusedShape," +
            "pressedShape=$pressedShape, selectedShape=$selectedShape," +
            "disabledShape=$disabledShape, focusedSelectedShape=$focusedSelectedShape, " +
            "focusedDisabledShape=$focusedDisabledShape," +
            "pressedSelectedShape=$pressedSelectedShape, " +
            "selectedDisabledShape=$selectedDisabledShape, " +
            "focusedSelectedDisabledShape=$focusedSelectedDisabledShape)"
    }
}

/**
 * Defines [Color] for all TV [Interaction] states of a Clickable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ClickableSurfaceColor internal constructor(
    internal val color: Color,
    internal val focusedColor: Color,
    internal val pressedColor: Color,
    internal val disabledColor: Color
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClickableSurfaceColor

        if (color != other.color) return false
        if (focusedColor != other.focusedColor) return false
        if (pressedColor != other.pressedColor) return false
        if (disabledColor != other.disabledColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + focusedColor.hashCode()
        result = 31 * result + pressedColor.hashCode()
        result = 31 * result + disabledColor.hashCode()

        return result
    }

    override fun toString(): String {
        return "ClickableSurfaceColor(color=$color, focusedColor=$focusedColor, " +
            "pressedColor=$pressedColor, disabledColor=$disabledColor)"
    }
}

/**
 * Defines [Color] for all TV [Interaction] states of a toggleable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ToggleableSurfaceColor internal constructor(
    internal val color: Color,
    internal val focusedColor: Color,
    internal val pressedColor: Color,
    internal val selectedColor: Color,
    internal val disabledColor: Color,
    internal val focusedSelectedColor: Color,
    internal val pressedSelectedColor: Color
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ToggleableSurfaceColor

        if (color != other.color) return false
        if (focusedColor != other.focusedColor) return false
        if (pressedColor != other.pressedColor) return false
        if (selectedColor != other.selectedColor) return false
        if (disabledColor != other.disabledColor) return false
        if (focusedSelectedColor != other.focusedSelectedColor) return false
        if (pressedSelectedColor != other.pressedSelectedColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + focusedColor.hashCode()
        result = 31 * result + pressedColor.hashCode()
        result = 31 * result + selectedColor.hashCode()
        result = 31 * result + disabledColor.hashCode()
        result = 31 * result + focusedSelectedColor.hashCode()
        result = 31 * result + pressedSelectedColor.hashCode()

        return result
    }

    override fun toString(): String {
        return "ToggleableSurfaceColor(color=$color, focusedColor=$focusedColor," +
            "pressedColor=$pressedColor, selectedColor=$selectedColor," +
            "disabledColor=$disabledColor, focusedSelectedColor=$focusedSelectedColor, " +
            "pressedSelectedColor=$pressedSelectedColor)"
    }
}

/**
 * Defines the scale for all TV indication states of Surface. Note: This scale must be
 * a non-negative float.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ClickableSurfaceScale internal constructor(
    @FloatRange(from = 0.0) internal val scale: Float,
    @FloatRange(from = 0.0) internal val focusedScale: Float,
    @FloatRange(from = 0.0) internal val pressedScale: Float,
    @FloatRange(from = 0.0) internal val disabledScale: Float,
    @FloatRange(from = 0.0) internal val focusedDisabledScale: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClickableSurfaceScale

        if (scale != other.scale) return false
        if (focusedScale != other.focusedScale) return false
        if (pressedScale != other.pressedScale) return false
        if (disabledScale != other.disabledScale) return false
        if (focusedDisabledScale != other.focusedDisabledScale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + focusedScale.hashCode()
        result = 31 * result + pressedScale.hashCode()
        result = 31 * result + disabledScale.hashCode()
        result = 31 * result + focusedDisabledScale.hashCode()

        return result
    }

    override fun toString(): String {
        return "ClickableSurfaceScale(scale=$scale, focusedScale=$focusedScale," +
            "pressedScale=$pressedScale, disabledScale=$disabledScale, " +
            "focusedDisabledScale=$focusedDisabledScale)"
    }

    companion object {
        /**
         * Signifies the absence of a scale in TV Components. Use this if you do not want to
         * display a [ScaleIndication] in any of the Leanback TV Components.
         */
        val None = ClickableSurfaceScale(
            scale = 1f,
            focusedScale = 1f,
            pressedScale = 1f,
            disabledScale = 1f,
            focusedDisabledScale = 1f
        )
    }
}

/**
 * Defines the scale for all TV [Interaction] states of toggleable Surface. Note: This
 * scale must be a non-negative float.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ToggleableSurfaceScale internal constructor(
    @FloatRange(from = 0.0) internal val scale: Float,
    @FloatRange(from = 0.0) internal val focusedScale: Float,
    @FloatRange(from = 0.0) internal val pressedScale: Float,
    @FloatRange(from = 0.0) internal val selectedScale: Float,
    @FloatRange(from = 0.0) internal val disabledScale: Float,
    @FloatRange(from = 0.0) internal val focusedSelectedScale: Float,
    @FloatRange(from = 0.0) internal val focusedDisabledScale: Float,
    @FloatRange(from = 0.0) internal val pressedSelectedScale: Float,
    @FloatRange(from = 0.0) internal val selectedDisabledScale: Float,
    @FloatRange(from = 0.0) internal val focusedSelectedDisabledScale: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ToggleableSurfaceScale

        if (scale != other.scale) return false
        if (focusedScale != other.focusedScale) return false
        if (pressedScale != other.pressedScale) return false
        if (selectedScale != other.selectedScale) return false
        if (disabledScale != other.disabledScale) return false
        if (focusedSelectedScale != other.focusedSelectedScale) return false
        if (focusedDisabledScale != other.focusedDisabledScale) return false
        if (pressedSelectedScale != other.pressedSelectedScale) return false
        if (selectedDisabledScale != other.selectedDisabledScale) return false
        if (focusedSelectedDisabledScale != other.focusedSelectedDisabledScale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + focusedScale.hashCode()
        result = 31 * result + pressedScale.hashCode()
        result = 31 * result + selectedScale.hashCode()
        result = 31 * result + disabledScale.hashCode()
        result = 31 * result + focusedSelectedScale.hashCode()
        result = 31 * result + focusedDisabledScale.hashCode()
        result = 31 * result + pressedSelectedScale.hashCode()
        result = 31 * result + selectedDisabledScale.hashCode()
        result = 31 * result + focusedSelectedDisabledScale.hashCode()

        return result
    }

    override fun toString(): String {
        return "ToggleableSurfaceScale(scale=$scale, focusedScale=$focusedScale," +
            "pressedScale=$pressedScale, selectedScale=$selectedScale," +
            "disabledScale=$disabledScale, focusedSelectedScale=$focusedSelectedScale, " +
            "focusedDisabledScale=$focusedDisabledScale," +
            "pressedSelectedScale=$pressedSelectedScale, " +
            "selectedDisabledScale=$selectedDisabledScale, " +
            "focusedSelectedDisabledScale=$focusedSelectedDisabledScale)"
    }
}

/**
 * Defines [Border] for all TV states of [Surface].
 */
@ExperimentalTvMaterial3Api
@Immutable
class ClickableSurfaceBorder internal constructor(
    internal val border: Border,
    internal val focusedBorder: Border,
    internal val pressedBorder: Border,
    internal val disabledBorder: Border,
    internal val focusedDisabledBorder: Border
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClickableSurfaceBorder

        if (border != other.border) return false
        if (focusedBorder != other.focusedBorder) return false
        if (pressedBorder != other.pressedBorder) return false
        if (disabledBorder != other.disabledBorder) return false
        if (focusedDisabledBorder != other.focusedDisabledBorder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + focusedBorder.hashCode()
        result = 31 * result + pressedBorder.hashCode()
        result = 31 * result + disabledBorder.hashCode()
        result = 31 * result + focusedDisabledBorder.hashCode()

        return result
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName}(border=$border, focusedBorder=$focusedBorder, " +
            "pressedBorder=$pressedBorder, disabledBorder=$disabledBorder, " +
            "focusedDisabledBorder=$focusedDisabledBorder)"
    }
}

/**
 * Defines [Border] for all TV states of a toggleable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ToggleableSurfaceBorder internal constructor(
    internal val border: Border,
    internal val focusedBorder: Border,
    internal val pressedBorder: Border,
    internal val selectedBorder: Border,
    internal val disabledBorder: Border,
    internal val focusedSelectedBorder: Border,
    internal val focusedDisabledBorder: Border,
    internal val pressedSelectedBorder: Border,
    internal val selectedDisabledBorder: Border,
    internal val focusedSelectedDisabledBorder: Border
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ToggleableSurfaceBorder

        if (border != other.border) return false
        if (focusedBorder != other.focusedBorder) return false
        if (pressedBorder != other.pressedBorder) return false
        if (selectedBorder != other.selectedBorder) return false
        if (disabledBorder != other.disabledBorder) return false
        if (focusedSelectedBorder != other.focusedSelectedBorder) return false
        if (focusedDisabledBorder != other.focusedDisabledBorder) return false
        if (pressedSelectedBorder != other.pressedSelectedBorder) return false
        if (selectedDisabledBorder != other.selectedDisabledBorder) return false
        if (focusedSelectedDisabledBorder != other.focusedSelectedDisabledBorder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + focusedBorder.hashCode()
        result = 31 * result + pressedBorder.hashCode()
        result = 31 * result + selectedBorder.hashCode()
        result = 31 * result + disabledBorder.hashCode()
        result = 31 * result + focusedSelectedBorder.hashCode()
        result = 31 * result + focusedDisabledBorder.hashCode()
        result = 31 * result + pressedSelectedBorder.hashCode()
        result = 31 * result + selectedDisabledBorder.hashCode()
        result = 31 * result + focusedSelectedDisabledBorder.hashCode()

        return result
    }

    override fun toString(): String {
        return "ToggleableSurfaceBorder(border=$border, focusedBorder=$focusedBorder," +
            "pressedBorder=$pressedBorder, selectedBorder=$selectedBorder," +
            "disabledBorder=$disabledBorder, focusedSelectedBorder=$focusedSelectedBorder, " +
            "focusedDisabledBorder=$focusedDisabledBorder," +
            "pressedSelectedBorder=$pressedSelectedBorder, " +
            "selectedDisabledBorder=$selectedDisabledBorder, " +
            "focusedSelectedDisabledBorder=$focusedSelectedDisabledBorder)"
    }
}

/**
 * Defines [Glow] for all TV [Interaction] states of [Surface].
 */
@ExperimentalTvMaterial3Api
@Immutable
class ClickableSurfaceGlow internal constructor(
    internal val glow: Glow,
    internal val focusedGlow: Glow,
    internal val pressedGlow: Glow
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClickableSurfaceGlow

        if (glow != other.glow) return false
        if (focusedGlow != other.focusedGlow) return false
        if (pressedGlow != other.pressedGlow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = glow.hashCode()
        result = 31 * result + focusedGlow.hashCode()
        result = 31 * result + pressedGlow.hashCode()

        return result
    }

    override fun toString(): String {
        return "ClickableSurfaceGlow(glow=$glow, focusedGlow=$focusedGlow, " +
            "pressedGlow=$pressedGlow)"
    }
}

/**
 * Defines [Glow] for all TV [Interaction] states of a toggleable Surface.
 */
@ExperimentalTvMaterial3Api
@Immutable
class ToggleableSurfaceGlow internal constructor(
    internal val glow: Glow,
    internal val focusedGlow: Glow,
    internal val pressedGlow: Glow,
    internal val selectedGlow: Glow,
    internal val focusedSelectedGlow: Glow,
    internal val pressedSelectedGlow: Glow
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ToggleableSurfaceGlow

        if (glow != other.glow) return false
        if (focusedGlow != other.focusedGlow) return false
        if (pressedGlow != other.pressedGlow) return false
        if (selectedGlow != other.selectedGlow) return false
        if (focusedSelectedGlow != other.focusedSelectedGlow) return false
        if (pressedSelectedGlow != other.pressedSelectedGlow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = glow.hashCode()
        result = 31 * result + focusedGlow.hashCode()
        result = 31 * result + pressedGlow.hashCode()
        result = 31 * result + selectedGlow.hashCode()
        result = 31 * result + focusedSelectedGlow.hashCode()
        result = 31 * result + pressedSelectedGlow.hashCode()

        return result
    }

    override fun toString(): String {
        return "ToggleableSurfaceGlow(glow=$glow, focusedGlow=$focusedGlow," +
            "pressedGlow=$pressedGlow, selectedGlow=$selectedGlow," +
            "focusedSelectedGlow=$focusedSelectedGlow, pressedSelectedGlow=$pressedSelectedGlow)"
    }
}
