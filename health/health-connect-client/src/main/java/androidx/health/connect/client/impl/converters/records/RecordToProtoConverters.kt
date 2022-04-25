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
package androidx.health.connect.client.impl.converters.records

import androidx.health.connect.client.records.ActiveCaloriesBurned
import androidx.health.connect.client.records.ActiveEnergyBurned
import androidx.health.connect.client.records.ActivityEvent
import androidx.health.connect.client.records.ActivityLap
import androidx.health.connect.client.records.ActivitySession
import androidx.health.connect.client.records.BasalBodyTemperature
import androidx.health.connect.client.records.BasalMetabolicRate
import androidx.health.connect.client.records.BloodGlucose
import androidx.health.connect.client.records.BloodPressure
import androidx.health.connect.client.records.BodyFat
import androidx.health.connect.client.records.BodyTemperature
import androidx.health.connect.client.records.BodyWaterMass
import androidx.health.connect.client.records.BoneMass
import androidx.health.connect.client.records.CervicalMucus
import androidx.health.connect.client.records.CervicalPosition
import androidx.health.connect.client.records.CyclingPedalingCadenceSeries
import androidx.health.connect.client.records.Distance
import androidx.health.connect.client.records.ElevationGained
import androidx.health.connect.client.records.FloorsClimbed
import androidx.health.connect.client.records.HeartRateSeries
import androidx.health.connect.client.records.HeartRateVariabilityDifferentialIndex
import androidx.health.connect.client.records.HeartRateVariabilityRmssd
import androidx.health.connect.client.records.HeartRateVariabilityS
import androidx.health.connect.client.records.HeartRateVariabilitySd2
import androidx.health.connect.client.records.HeartRateVariabilitySdann
import androidx.health.connect.client.records.HeartRateVariabilitySdnn
import androidx.health.connect.client.records.HeartRateVariabilitySdnnIndex
import androidx.health.connect.client.records.HeartRateVariabilitySdsd
import androidx.health.connect.client.records.HeartRateVariabilityTinn
import androidx.health.connect.client.records.Height
import androidx.health.connect.client.records.HipCircumference
import androidx.health.connect.client.records.Hydration
import androidx.health.connect.client.records.LeanBodyMass
import androidx.health.connect.client.records.Menstruation
import androidx.health.connect.client.records.Nutrition
import androidx.health.connect.client.records.OvulationTest
import androidx.health.connect.client.records.OxygenSaturation
import androidx.health.connect.client.records.PowerSeries
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.Repetitions
import androidx.health.connect.client.records.RespiratoryRate
import androidx.health.connect.client.records.RestingHeartRate
import androidx.health.connect.client.records.SeriesRecord
import androidx.health.connect.client.records.SexualActivity
import androidx.health.connect.client.records.SleepSession
import androidx.health.connect.client.records.SleepStage
import androidx.health.connect.client.records.SpeedSeries
import androidx.health.connect.client.records.Steps
import androidx.health.connect.client.records.StepsCadenceSeries
import androidx.health.connect.client.records.SwimmingStrokes
import androidx.health.connect.client.records.TotalCaloriesBurned
import androidx.health.connect.client.records.TotalEnergyBurned
import androidx.health.connect.client.records.Vo2Max
import androidx.health.connect.client.records.WaistCircumference
import androidx.health.connect.client.records.Weight
import androidx.health.connect.client.records.WheelchairPushes
import androidx.health.platform.client.proto.DataProto

/** Converts public API object into internal proto for ipc. */
fun Record.toProto(): DataProto.DataPoint =
    when (this) {
        is BasalBodyTemperature ->
            instantaneousProto()
                .setDataType(protoDataType("BasalBodyTemperature"))
                .apply {
                    putValues("temperature", doubleVal(temperatureDegreesCelsius))
                    measurementLocation?.let { putValues("measurementLocation", enumVal(it)) }
                }
                .build()
        is BasalMetabolicRate ->
            instantaneousProto()
                .setDataType(protoDataType("BasalMetabolicRate"))
                .apply { putValues("bmr", doubleVal(kcalPerDay)) }
                .build()
        is BloodGlucose ->
            instantaneousProto()
                .setDataType(protoDataType("BloodGlucose"))
                .apply {
                    putValues("level", doubleVal(levelMillimolesPerLiter))
                    specimenSource?.let { putValues("specimenSource", enumVal(it)) }
                    mealType?.let { putValues("mealType", enumVal(it)) }
                    relationToMeal?.let { putValues("relationToMeal", enumVal(it)) }
                }
                .build()
        is BloodPressure ->
            instantaneousProto()
                .setDataType(protoDataType("BloodPressure"))
                .apply {
                    putValues("systolic", doubleVal(systolicMillimetersOfMercury))
                    putValues("diastolic", doubleVal(diastolicMillimetersOfMercury))
                    bodyPosition?.let { putValues("bodyPosition", enumVal(it)) }
                    measurementLocation?.let { putValues("measurementLocation", enumVal(it)) }
                }
                .build()
        is BodyFat ->
            instantaneousProto()
                .setDataType(protoDataType("BodyFat"))
                .apply { putValues("percentage", doubleVal(percentage)) }
                .build()
        is BodyTemperature ->
            instantaneousProto()
                .setDataType(protoDataType("BodyTemperature"))
                .apply {
                    putValues("temperature", doubleVal(temperatureDegreesCelsius))
                    measurementLocation?.let { putValues("measurementLocation", enumVal(it)) }
                }
                .build()
        is BodyWaterMass ->
            instantaneousProto()
                .setDataType(protoDataType("BodyWaterMass"))
                .apply { putValues("mass", doubleVal(massKg)) }
                .build()
        is BoneMass ->
            instantaneousProto()
                .setDataType(protoDataType("BoneMass"))
                .apply { putValues("mass", doubleVal(massKg)) }
                .build()
        is CervicalMucus ->
            instantaneousProto()
                .setDataType(protoDataType("CervicalMucus"))
                .apply {
                    texture?.let { putValues("texture", enumVal(it)) }
                    amount?.let { putValues("amount", enumVal(it)) }
                }
                .build()
        is CervicalPosition ->
            instantaneousProto()
                .setDataType(protoDataType("CervicalPosition"))
                .apply {
                    position?.let { putValues("position", enumVal(it)) }
                    dilation?.let { putValues("dilation", enumVal(it)) }
                    firmness?.let { putValues("firmness", enumVal(it)) }
                }
                .build()
        is CyclingPedalingCadenceSeries ->
            toProto(dataTypeName = "CyclingPedalingCadenceSeries") { sample ->
                DataProto.SeriesValue.newBuilder()
                    .putValues("rpm", doubleVal(sample.revolutionsPerMinute))
                    .setInstantTimeMillis(sample.time.toEpochMilli())
                    .build()
            }
        is HeartRateSeries ->
            toProto(dataTypeName = "HeartRateSeries") { sample ->
                DataProto.SeriesValue.newBuilder()
                    .putValues("bpm", longVal(sample.beatsPerMinute))
                    .setInstantTimeMillis(sample.time.toEpochMilli())
                    .build()
            }
        is Height ->
            instantaneousProto()
                .setDataType(protoDataType("Height"))
                .apply { putValues("height", doubleVal(heightMeters)) }
                .build()
        is HipCircumference ->
            instantaneousProto()
                .setDataType(protoDataType("HipCircumference"))
                .apply { putValues("circumference", doubleVal(circumferenceMeters)) }
                .build()
        is HeartRateVariabilityDifferentialIndex ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilityDifferentialIndex"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilityRmssd ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilityRmssd"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilityS ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilityS"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilitySd2 ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilitySd2"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilitySdann ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilitySdann"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilitySdnnIndex ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilitySdnnIndex"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilitySdnn ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilitySdnn"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilitySdsd ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilitySdsd"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is HeartRateVariabilityTinn ->
            instantaneousProto()
                .setDataType(protoDataType("HeartRateVariabilityTinn"))
                .apply { putValues("heartRateVariability", doubleVal(heartRateVariabilityMillis)) }
                .build()
        is LeanBodyMass ->
            instantaneousProto()
                .setDataType(protoDataType("LeanBodyMass"))
                .apply { putValues("mass", doubleVal(massKg)) }
                .build()
        is Menstruation ->
            instantaneousProto()
                .setDataType(protoDataType("Menstruation"))
                .apply { flow?.let { putValues("flow", enumVal(it)) } }
                .build()
        is OvulationTest ->
            instantaneousProto()
                .setDataType(protoDataType("OvulationTest"))
                .apply { putValues("result", enumVal(result)) }
                .build()
        is OxygenSaturation ->
            instantaneousProto()
                .setDataType(protoDataType("OxygenSaturation"))
                .apply { putValues("percentage", doubleVal(percentage)) }
                .build()
        is PowerSeries ->
            toProto(dataTypeName = "PowerSeries") { sample ->
                DataProto.SeriesValue.newBuilder()
                    .putValues("power", doubleVal(sample.watts))
                    .setInstantTimeMillis(sample.time.toEpochMilli())
                    .build()
            }
        is RespiratoryRate ->
            instantaneousProto()
                .setDataType(protoDataType("RespiratoryRate"))
                .apply { putValues("rate", doubleVal(rate)) }
                .build()
        is RestingHeartRate ->
            instantaneousProto()
                .setDataType(protoDataType("RestingHeartRate"))
                .apply { putValues("bpm", longVal(beatsPerMinute)) }
                .build()
        is SexualActivity ->
            instantaneousProto()
                .setDataType(protoDataType("SexualActivity"))
                .apply { protectionUsed?.let { putValues("protectionUsed", enumVal(it)) } }
                .build()
        is SpeedSeries ->
            toProto(dataTypeName = "SpeedSeries") { sample ->
                DataProto.SeriesValue.newBuilder()
                    .putValues("speed", doubleVal(sample.metersPerSecond))
                    .setInstantTimeMillis(sample.time.toEpochMilli())
                    .build()
            }
        is StepsCadenceSeries ->
            toProto(dataTypeName = "StepsCadenceSeries") { sample ->
                DataProto.SeriesValue.newBuilder()
                    .putValues("rate", doubleVal(sample.rate))
                    .setInstantTimeMillis(sample.time.toEpochMilli())
                    .build()
            }
        is Vo2Max ->
            instantaneousProto()
                .setDataType(protoDataType("Vo2Max"))
                .apply {
                    putValues("vo2", doubleVal(vo2MillilitersPerMinuteKilogram))
                    measurementMethod?.let { putValues("measurementMethod", enumVal(it)) }
                }
                .build()
        is WaistCircumference ->
            instantaneousProto()
                .setDataType(protoDataType("WaistCircumference"))
                .apply { putValues("circumference", doubleVal(circumferenceMeters)) }
                .build()
        is Weight ->
            instantaneousProto()
                .setDataType(protoDataType("Weight"))
                .apply { putValues("weight", doubleVal(weightKg)) }
                .build()
        is ActiveCaloriesBurned ->
            intervalProto()
                .setDataType(protoDataType("ActiveCaloriesBurned"))
                .apply { putValues("energy", doubleVal(energyKcal)) }
                .build()
        is ActiveEnergyBurned ->
            intervalProto()
                .setDataType(protoDataType("ActiveEnergyBurned"))
                .apply { putValues("energy", doubleVal(energyKcal)) }
                .build()
        is ActivityEvent ->
            intervalProto()
                .setDataType(protoDataType("ActivityEvent"))
                .apply { putValues("eventType", enumVal(eventType)) }
                .build()
        is ActivityLap ->
            intervalProto()
                .setDataType(protoDataType("ActivityLap"))
                .apply {
                    if (lengthMeters > 0) {
                        putValues("length", doubleVal(lengthMeters))
                    }
                }
                .build()
        is ActivitySession ->
            intervalProto()
                .setDataType(protoDataType("ActivitySession"))
                .apply {
                    putValues("activityType", enumVal(activityType))
                    title?.let { putValues("title", stringVal(it)) }
                    notes?.let { putValues("notes", stringVal(it)) }
                }
                .build()
        is Distance ->
            intervalProto()
                .setDataType(protoDataType("Distance"))
                .apply { putValues("distance", doubleVal(distanceMeters)) }
                .build()
        is ElevationGained ->
            intervalProto()
                .setDataType(protoDataType("ElevationGained"))
                .apply { putValues("elevation", doubleVal(elevationMeters)) }
                .build()
        is FloorsClimbed ->
            intervalProto()
                .setDataType(protoDataType("FloorsClimbed"))
                .apply { putValues("floors", doubleVal(floors)) }
                .build()
        is Hydration ->
            intervalProto()
                .setDataType(protoDataType("Hydration"))
                .apply { putValues("volume", doubleVal(volumeLiters)) }
                .build()
        is Nutrition ->
            intervalProto()
                .setDataType(protoDataType("Nutrition"))
                .apply {
                    if (biotinGrams > 0) {
                        putValues("biotin", doubleVal(biotinGrams))
                    }
                    if (caffeineGrams > 0) {
                        putValues("caffeine", doubleVal(caffeineGrams))
                    }
                    if (calciumGrams > 0) {
                        putValues("calcium", doubleVal(calciumGrams))
                    }
                    if (kcal > 0) {
                        putValues("calories", doubleVal(kcal))
                    }
                    if (kcalFromFat > 0) {
                        putValues("caloriesFromFat", doubleVal(kcalFromFat))
                    }
                    if (chlorideGrams > 0) {
                        putValues("chloride", doubleVal(chlorideGrams))
                    }
                    if (cholesterolGrams > 0) {
                        putValues("cholesterol", doubleVal(cholesterolGrams))
                    }
                    if (chromiumGrams > 0) {
                        putValues("chromium", doubleVal(chromiumGrams))
                    }
                    if (copperGrams > 0) {
                        putValues("copper", doubleVal(copperGrams))
                    }
                    if (dietaryFiberGrams > 0) {
                        putValues("dietaryFiber", doubleVal(dietaryFiberGrams))
                    }
                    if (folateGrams > 0) {
                        putValues("folate", doubleVal(folateGrams))
                    }
                    if (folicAcidGrams > 0) {
                        putValues("folicAcid", doubleVal(folicAcidGrams))
                    }
                    if (iodineGrams > 0) {
                        putValues("iodine", doubleVal(iodineGrams))
                    }
                    if (ironGrams > 0) {
                        putValues("iron", doubleVal(ironGrams))
                    }
                    if (magnesiumGrams > 0) {
                        putValues("magnesium", doubleVal(magnesiumGrams))
                    }
                    if (manganeseGrams > 0) {
                        putValues("manganese", doubleVal(manganeseGrams))
                    }
                    if (molybdenumGrams > 0) {
                        putValues("molybdenum", doubleVal(molybdenumGrams))
                    }
                    if (monounsaturatedFatGrams > 0) {
                        putValues("monounsaturatedFat", doubleVal(monounsaturatedFatGrams))
                    }
                    if (niacinGrams > 0) {
                        putValues("niacin", doubleVal(niacinGrams))
                    }
                    if (pantothenicAcidGrams > 0) {
                        putValues("pantothenicAcid", doubleVal(pantothenicAcidGrams))
                    }
                    if (phosphorusGrams > 0) {
                        putValues("phosphorus", doubleVal(phosphorusGrams))
                    }
                    if (polyunsaturatedFatGrams > 0) {
                        putValues("polyunsaturatedFat", doubleVal(polyunsaturatedFatGrams))
                    }
                    if (potassiumGrams > 0) {
                        putValues("potassium", doubleVal(potassiumGrams))
                    }
                    if (proteinGrams > 0) {
                        putValues("protein", doubleVal(proteinGrams))
                    }
                    if (riboflavinGrams > 0) {
                        putValues("riboflavin", doubleVal(riboflavinGrams))
                    }
                    if (saturatedFatGrams > 0) {
                        putValues("saturatedFat", doubleVal(saturatedFatGrams))
                    }
                    if (seleniumGrams > 0) {
                        putValues("selenium", doubleVal(seleniumGrams))
                    }
                    if (sodiumGrams > 0) {
                        putValues("sodium", doubleVal(sodiumGrams))
                    }
                    if (sugarGrams > 0) {
                        putValues("sugar", doubleVal(sugarGrams))
                    }
                    if (thiaminGrams > 0) {
                        putValues("thiamin", doubleVal(thiaminGrams))
                    }
                    if (totalCarbohydrateGrams > 0) {
                        putValues("totalCarbohydrate", doubleVal(totalCarbohydrateGrams))
                    }
                    if (totalFatGrams > 0) {
                        putValues("totalFat", doubleVal(totalFatGrams))
                    }
                    if (transFatGrams > 0) {
                        putValues("transFat", doubleVal(transFatGrams))
                    }
                    if (unsaturatedFatGrams > 0) {
                        putValues("unsaturatedFat", doubleVal(unsaturatedFatGrams))
                    }
                    if (vitaminAGrams > 0) {
                        putValues("vitaminA", doubleVal(vitaminAGrams))
                    }
                    if (vitaminB12Grams > 0) {
                        putValues("vitaminB12", doubleVal(vitaminB12Grams))
                    }
                    if (vitaminB6Grams > 0) {
                        putValues("vitaminB6", doubleVal(vitaminB6Grams))
                    }
                    if (vitaminCGrams > 0) {
                        putValues("vitaminC", doubleVal(vitaminCGrams))
                    }
                    if (vitaminDGrams > 0) {
                        putValues("vitaminD", doubleVal(vitaminDGrams))
                    }
                    if (vitaminEGrams > 0) {
                        putValues("vitaminE", doubleVal(vitaminEGrams))
                    }
                    if (vitaminKGrams > 0) {
                        putValues("vitaminK", doubleVal(vitaminKGrams))
                    }
                    if (zincGrams > 0) {
                        putValues("zinc", doubleVal(zincGrams))
                    }
                    mealType?.let { putValues("mealType", enumVal(it)) }
                    name?.let { putValues("name", stringVal(it)) }
                }
                .build()
        is Repetitions ->
            intervalProto()
                .setDataType(protoDataType("Repetitions"))
                .apply {
                    putValues("count", longVal(count))
                    putValues("type", enumVal(type))
                }
                .build()
        is SleepSession ->
            intervalProto()
                .setDataType(protoDataType("SleepSession"))
                .apply {
                    title?.let { putValues("title", stringVal(it)) }
                    notes?.let { putValues("notes", stringVal(it)) }
                }
                .build()
        is SleepStage ->
            intervalProto()
                .setDataType(protoDataType("SleepStage"))
                .apply { putValues("stage", enumVal(stage)) }
                .build()
        is Steps ->
            intervalProto()
                .setDataType(protoDataType("Steps"))
                .apply { putValues("count", longVal(count)) }
                .build()
        is SwimmingStrokes ->
            intervalProto()
                .setDataType(protoDataType("SwimmingStrokes"))
                .apply {
                    if (count > 0) {
                        putValues("count", longVal(count))
                    }
                    putValues("type", enumVal(type))
                }
                .build()
        is TotalCaloriesBurned ->
            intervalProto()
                .setDataType(protoDataType("TotalCaloriesBurned"))
                .apply { putValues("energy", doubleVal(energyKcal)) }
                .build()
        is TotalEnergyBurned ->
            intervalProto()
                .setDataType(protoDataType("TotalEnergyBurned"))
                .apply { putValues("energy", doubleVal(energyKcal)) }
                .build()
        is WheelchairPushes ->
            intervalProto()
                .setDataType(protoDataType("WheelchairPushes"))
                .apply { putValues("count", longVal(count)) }
                .build()
        else -> throw RuntimeException("Unsupported yet!")
    }

private fun <T : Any> SeriesRecord<T>.toProto(
    dataTypeName: String,
    getSeriesValue: (sample: T) -> DataProto.SeriesValue,
): DataProto.DataPoint =
    intervalProto()
        .setDataType(protoDataType(dataTypeName = dataTypeName))
        .apply {
            for (sample in samples) {
                addSeriesValues(getSeriesValue(sample))
            }
        }
        .build()
