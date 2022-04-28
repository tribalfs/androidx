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

/** Where on the user's body a temperature measurement was taken from. */
public object BodyTemperatureMeasurementLocation {
    const val ARMPIT = "armpit"
    const val FINGER = "finger"
    const val FOREHEAD = "forehead"
    const val MOUTH = "mouth"
    const val RECTUM = "rectum"
    const val TEMPORAL_ARTERY = "temporal_artery"
    const val TOE = "toe"
    const val EAR = "ear"
    const val WRIST = "wrist"
    const val VAGINA = "vagina"
}

/**
 * Where on the user's body a temperature measurement was taken from.
 * @suppress
 */
@Retention(AnnotationRetention.SOURCE)
@StringDef(
    value =
        [
            BodyTemperatureMeasurementLocation.ARMPIT,
            BodyTemperatureMeasurementLocation.FINGER,
            BodyTemperatureMeasurementLocation.FOREHEAD,
            BodyTemperatureMeasurementLocation.MOUTH,
            BodyTemperatureMeasurementLocation.RECTUM,
            BodyTemperatureMeasurementLocation.TEMPORAL_ARTERY,
            BodyTemperatureMeasurementLocation.TOE,
            BodyTemperatureMeasurementLocation.EAR,
            BodyTemperatureMeasurementLocation.WRIST,
            BodyTemperatureMeasurementLocation.VAGINA,
        ]
)
@RestrictTo(RestrictTo.Scope.LIBRARY)
annotation class BodyTemperatureMeasurementLocations
