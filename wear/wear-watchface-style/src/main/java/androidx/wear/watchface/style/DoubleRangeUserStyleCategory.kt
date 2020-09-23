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

package androidx.wear.watchface.style

import android.graphics.drawable.Icon
import androidx.annotation.RestrictTo
import androidx.wear.watchface.style.data.DoubleRangeUserStyleCategoryWireFormat

/**
 * A DoubleRangeUserStyleCategory represents a category with a {@link Double} value in the range
 * [minimumValue .. maximumValue].
 */
class DoubleRangeUserStyleCategory : UserStyleCategory {

    /** @hide */
    internal companion object {
        internal fun createOptionsList(
            minimumValue: Double,
            maximumValue: Double,
            defaultValue: Double
        ): List<DoubleRangeOption> {
            require(minimumValue < maximumValue)
            require(defaultValue >= minimumValue)
            require(defaultValue <= maximumValue)

            return if (defaultValue != minimumValue && defaultValue != maximumValue) {
                listOf(
                    DoubleRangeOption(minimumValue),
                    DoubleRangeOption(defaultValue),
                    DoubleRangeOption(maximumValue)
                )
            } else {
                listOf(DoubleRangeOption(minimumValue), DoubleRangeOption(maximumValue))
            }
        }
    }

    constructor (
        /** Identifier for the element, must be unique. */
        id: String,

        /** Localized human readable name for the element, used in the userStyle selection UI. */
        displayName: String,

        /** Localized description string displayed under the displayName. */
        description: String,

        /** Icon for use in the userStyle selection UI. */
        icon: Icon?,

        /** Minimum value (inclusive). */
        minimumValue: Double,

        /** Maximum value (inclusive). */
        maximumValue: Double,

        /** The default value for this DoubleRangeUserStyleCategory. */
        defaultValue: Double,

        /**
         * Used by the style configuration UI. Describes which rendering layer this style affects.
         * Must be either 0 (for a style change with no visual effect, e.g. sound controls) or a
         * combination  of {@link #LAYER_WATCH_FACE_BASE}, {@link #LAYER_COMPLICATONS}, {@link
         * #LAYER_UPPER}.
         */
        layerFlags: Int
    ) : super(
        id,
        displayName,
        description,
        icon,
        createOptionsList(minimumValue, maximumValue, defaultValue),
        // The index of defaultValue can only ever be 0 or 1.
        when (defaultValue) {
            minimumValue -> 0
            else -> 1
        },
        layerFlags
    )

    internal constructor(wireFormat: DoubleRangeUserStyleCategoryWireFormat) : super(wireFormat)

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun toWireFormat() =
        DoubleRangeUserStyleCategoryWireFormat(
            id,
            displayName,
            description,
            icon,
            getWireFormatOptionsList(),
            defaultOptionIndex,
            layerFlags
        )

    /**
     * Represents an option as a {@link Double} in the range [minimumValue .. maximumValue].
     */
    class DoubleRangeOption : Option {
        /* The value for this option. Must be within the range [minimumValue .. maximumValue]. */
        val value: Double

        constructor(value: Double) : super(value.toString()) {
            this.value = value
        }

        internal constructor(
            wireFormat: DoubleRangeUserStyleCategoryWireFormat.DoubleRangeOptionWireFormat
        ) : super(wireFormat.mId) {
            value = wireFormat.mValue
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        override fun toWireFormat() =
            DoubleRangeUserStyleCategoryWireFormat.DoubleRangeOptionWireFormat(id, value)
    }

    /**
     * Returns the minimum value.
     */
    fun getMinimumValue() = (options.first() as DoubleRangeOption).value

    /**
     * Returns the maximum value.
     */
    fun getMaximumValue() = (options.last() as DoubleRangeOption).value

    /**
     * Returns the default value.
     */
    fun getDefaultValue() = (options[defaultOptionIndex] as DoubleRangeOption).value

    /**
     * We support all values in the range [min ... max] not just min & max.
     */
    override fun getOptionForId(optionId: String) =
        options.find { it.id == optionId } ?: checkedOptionForId(optionId)

    private fun checkedOptionForId(optionId: String): DoubleRangeOption {
        return try {
            val value = optionId.toDouble()
            if (value < getMinimumValue() || value > getMaximumValue()) {
                options[defaultOptionIndex] as DoubleRangeOption
            } else {
                DoubleRangeOption(value)
            }
        } catch (e: NumberFormatException) {
            options[defaultOptionIndex] as DoubleRangeOption
        }
    }
}
