/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.car.app.hardware.common;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

/** CarZone to areaId converter for Global zones. */
public class GlobalCarZoneAreaIdConverter implements CarZoneAreaIdConverter {
    @NonNull
    @Override
    public ImmutableSet<CarZone> convertAreaIdToCarZones(int areaId) {
        return ImmutableSet.of(CarZone.CAR_ZONE_GLOBAL);
    }
}
