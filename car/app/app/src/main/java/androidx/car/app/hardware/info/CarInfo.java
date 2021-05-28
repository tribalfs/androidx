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

import androidx.annotation.NonNull;
import androidx.car.app.annotations.RequiresCarApi;
import androidx.car.app.hardware.common.OnCarDataListener;

import java.util.concurrent.Executor;

/**
 * Manages access to car hardware specific info such as model, energy, and speed info.
 */
@RequiresCarApi(3)
public interface CarInfo {
    /**
     * Request the {@link Model} information about the car hardware.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void getModel(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<Model> listener);

    /**
     * Reguest the {@link EnergyProfile} information about the car hardware.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void getEnergyProfile(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<EnergyProfile> listener);

    /**
     * Setup an ongoing listener to receive {@link Toll} information from the car hardware.
     *
     * <p>If the listener was added previously then it won't be added again.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void addTollListener(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<Toll> listener);

    /**
     * Remove an ongoing listener for {@link Toll} information.
     *
     * <p>If the listener is not currently added, then nothing will be removed.
     *
     * @param listener the listener to remove
     */
    void removeTollListener(@NonNull OnCarDataListener<Toll> listener);

    /**
     * Setup an ongoing listener to receive {@link EnergyLevel} information from the car hardware.
     *
     * <p>If the listener was added previously then it won't be added.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void addEnergyLevelListener(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<EnergyLevel> listener);

    /**
     * Remove an ongoing listener for {@link EnergyLevel} information.
     *
     * <p>If the listener is not currently added, then nothing will be removed.
     *
     * @param listener the listener to remove
     */
    void removeEnergyLevelListener(@NonNull OnCarDataListener<EnergyLevel> listener);

    /**
     * Setup an ongoing listener to receive {@link Speed} information from the car hardware.
     *
     * <p>If the listener was added previously then it won't be added.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void addSpeedListener(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<Speed> listener);

    /**
     * Remove an ongoing listener for {@link Speed} information.
     *
     * <p>If the listener is not currently added, then nothing will be removed.
     *
     * @param listener the listener to remove
     */
    void removeSpeedListener(@NonNull OnCarDataListener<Speed> listener);

    /**
     * Setup an ongoing listener to receive {@link Mileage} information from the car hardware.
     *
     * <p>If the listener was added previously then it won't be added.
     *
     * @param executor the executor which will be used for invoking the listener
     * @param listener the listener that will be invoked when data is available
     */
    void addMileageListener(@NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnCarDataListener<Mileage> listener);

    /**
     * Remove an ongoing listener for {@link Mileage} information.
     *
     * <p>If the listener is not currently added, then nothing will be removed.
     *
     * @param listener the listener to remove
     */
    void removeMileageListener(@NonNull OnCarDataListener<Mileage> listener);
}
