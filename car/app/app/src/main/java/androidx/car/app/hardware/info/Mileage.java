/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.car.app.hardware.info;

import static java.util.Objects.requireNonNull;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.RequiresCarApi;
import androidx.car.app.hardware.common.CarUnit;
import androidx.car.app.hardware.common.CarValue;

import java.util.Objects;

/** Information about car mileage. */
@CarProtocol
@RequiresCarApi(3)
public final class Mileage {
    @Keep
    @NonNull
    private final CarValue<Float> mOdometer;

    @Keep
    @NonNull
    private final CarValue<Integer> mDistanceDisplayUnit;

    /** Returns the value of the odometer from the car hardware in meters. */
    @NonNull
    public CarValue<Float> getOdometer() {
        return requireNonNull(mOdometer);
    }

    /**
     * Returns the distance display unit from the car hardware.
     *
     * <p>See {@link CarUnit} for possible distance values.
     */
    @NonNull
    public CarValue<Integer> getDistanceDisplayUnit() {
        return requireNonNull(mDistanceDisplayUnit);
    }

    @Override
    @NonNull
    public String toString() {
        return "[ odometer: "
                + mOdometer
                + ", distance display unit: "
                + mDistanceDisplayUnit
                + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOdometer, mDistanceDisplayUnit);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Mileage)) {
            return false;
        }
        Mileage otherMileage = (Mileage) other;

        return Objects.equals(mOdometer, otherMileage.mOdometer)
                && Objects.equals(mDistanceDisplayUnit, otherMileage.mDistanceDisplayUnit);
    }

    Mileage(Builder builder) {
        mOdometer = requireNonNull(builder.mOdometer);
        mDistanceDisplayUnit = requireNonNull(builder.mDistanceDisplayUnit);
    }

    /** Constructs an empty instance, used by serialization code. */
    private Mileage() {
        mOdometer = CarValue.UNIMPLEMENTED_FLOAT;
        mDistanceDisplayUnit = CarValue.UNIMPLEMENTED_INTEGER;
    }

    /** A builder of {@link Mileage}. */
    public static final class Builder {
        CarValue<Float> mOdometer = CarValue.UNIMPLEMENTED_FLOAT;
        CarValue<Integer> mDistanceDisplayUnit = CarValue.UNIMPLEMENTED_INTEGER;

        /**
         * Sets the odometer value in meters.
         *
         * @throws NullPointerException if {@code odometer} is {@code null}
         */
        @NonNull
        public Builder setOdometer(@NonNull CarValue<Float> odometer) {
            mOdometer = requireNonNull(odometer);
            return this;
        }

        /**
         * Sets the mileage display unit.
         *
         * <p>Valid values are in {@link CarUnit}.
         *
         * @throws NullPointerException if {@code mileageDisplayUnit} is {@code null}
         */
        @NonNull
        public Builder setDistanceDisplayUnit(@NonNull CarValue<Integer> mileageDisplayUnit) {
            mDistanceDisplayUnit = requireNonNull(mileageDisplayUnit);
            return this;
        }

        /**
         * Constructs the {@link Mileage} defined by this builder.
         */
        @NonNull
        public Mileage build() {
            return new Mileage(this);
        }

    }
}
