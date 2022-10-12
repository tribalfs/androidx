/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.connect.client.records

import androidx.annotation.RestrictTo
import androidx.annotation.StringDef
import androidx.health.connect.client.records.BloodGlucoseRecord.SpecimenSource
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the concentration of glucose in the blood. Each record represents a single instantaneous
 * blood glucose reading.
 */
public class BloodGlucoseRecord(
    override val time: Instant,
    override val zoneOffset: ZoneOffset?,
    /**
     * Blood glucose level or concentration. Required field. Valid range: 0-50 mmol/L.
     *
     * @see BloodGlucose
     */
    public val level: BloodGlucose,
    /**
     * Type of body fluid used to measure the blood glucose. Optional, enum field. Allowed values:
     * [SpecimenSource].
     *
     * @see SpecimenSource
     */
    @property:SpecimenSources public val specimenSource: String? = null,
    /**
     * Type of meal related to the blood glucose measurement. Optional, enum field. Allowed values:
     * [MealType].
     *
     * @see MealType
     */
    @property:MealTypes public val mealType: String? = null,
    /**
     * Relationship of the meal to the blood glucose measurement. Optional, enum field. Allowed
     * values: [RelationToMeal].
     *
     * @see RelationToMeal
     */
    @property:RelationToMeals public val relationToMeal: String? = null,
    override val metadata: Metadata = Metadata.EMPTY,
) : InstantaneousRecord {

    init {
        level.requireNotLess(other = level.zero(), name = "level")
        level.requireNotMore(other = MAX_BLOOD_GLUCOSE_LEVEL, name = "level")
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloodGlucoseRecord) return false

        if (level != other.level) return false
        if (specimenSource != other.specimenSource) return false
        if (mealType != other.mealType) return false
        if (relationToMeal != other.relationToMeal) return false
        if (time != other.time) return false
        if (zoneOffset != other.zoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun hashCode(): Int {
        var result = level.hashCode()
        result = 31 * result + (specimenSource?.hashCode() ?: 0)
        result = 31 * result + (mealType?.hashCode() ?: 0)
        result = 31 * result + (relationToMeal?.hashCode() ?: 0)
        result = 31 * result + time.hashCode()
        result = 31 * result + (zoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /**
     * List of supported blood glucose specimen sources (type of body fluid used to measure the
     * blood glucose).
     */
    object SpecimenSource {
        const val INTERSTITIAL_FLUID = "interstitial_fluid"
        const val CAPILLARY_BLOOD = "capillary_blood"
        const val PLASMA = "plasma"
        const val SERUM = "serum"
        const val TEARS = "tears"
        const val WHOLE_BLOOD = "whole_blood"
    }

    /**
     * List of supported blood glucose specimen sources (type of body fluid used to measure the
     * blood glucose).
     *
     * @suppress
     */
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        value =
            [
                SpecimenSource.INTERSTITIAL_FLUID,
                SpecimenSource.CAPILLARY_BLOOD,
                SpecimenSource.PLASMA,
                SpecimenSource.SERUM,
                SpecimenSource.TEARS,
                SpecimenSource.WHOLE_BLOOD,
            ]
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    annotation class SpecimenSources

    private companion object {
        private val MAX_BLOOD_GLUCOSE_LEVEL = BloodGlucose.millimolesPerLiter(50.0)
    }
}
