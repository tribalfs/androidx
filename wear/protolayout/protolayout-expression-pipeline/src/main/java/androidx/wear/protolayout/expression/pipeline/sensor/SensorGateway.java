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

package androidx.wear.protolayout.expression.pipeline.sensor;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.wear.protolayout.expression.PlatformDataKey;

import java.util.concurrent.Executor;

/**
 * Gateway for proto layout expression library to be able to access sensor data, e.g. health data.
 */
@RestrictTo(Scope.LIBRARY_GROUP_PREFIX)
public interface SensorGateway {

    /**
     * Consumer for sensor data.
     *
     * <p>If Consumer is relying on multiple sources or upstream nodes, it should be responsible for
     * data coordination between pending updates and received data.
     *
     * <p>For example, this Consumer listens to two upstream sources:
     *
     * <pre>{@code
     * class MyConsumer implements Consumer {
     *  int pending = 0;
     *
     * @Override
     * public void onPreUpdate(){
     *      pending++;
     * }
     *
     *  @Override
     *  public void onData(double value){
     *   // store the value internally
     *   pending--;
     *   if (pending == 0) {
     *     // We've received data from every changed upstream source
     *     consumeTheChangedDataValues()
     *   }
     *  }
     * }
     * }</pre>
     */
    interface Consumer {
        /**
         * Called when a new batch of data has arrived. The actual data will be delivered in {@link
         * #onData(double)} after this method is called on all registered consumers.
         */
        @AnyThread
        default void onPreUpdate() {}

        /**
         * Called when a new data for the requested data type is received. This will be run on a
         * single background thread.
         *
         * <p>Note that there is no notification when a daily sensor data item resets to zero; this
         * will simply emit an item of sensor data with value 0.0 when the rollover happens.
         */
        @AnyThread
        void onData(double value);

        /**
         * Notify that the current data for the registered data type has been invalidated. This
         * could be, for example, that the current heart rate is no longer valid as the user is not
         * wearing the device.
         */
        @AnyThread
        default void onInvalidated() {}
    }

    /**
     * Enables/unpauses sending updates to the consumers. All cached updates (while updates were
     * paused) for data types will be delivered by sending the latest data.
     */
    @RestrictTo(Scope.LIBRARY_GROUP_PREFIX)
    void enableUpdates();

    /**
     * Disables/pauses sending updates to the consumers. While paused, updates will be cached to be
     * delivered after unpausing.
     */
    @RestrictTo(Scope.LIBRARY_GROUP_PREFIX)
    void disableUpdates();

    /**
     * Register for updates for the given data type. This may cause {@link Consumer} to immediately
     * fire if there is suitable cached data, otherwise {@link Consumer} will fire when there is
     * appropriate updates to the requested sensor data.
     *
     * <p>Implementations should check if the provider has permission to provide the requested data
     * type.
     *
     * <p>Note that the callback will be executed on the single background thread (implementation
     * dependent). To specify the execution thread, use {@link #registerSensorGatewayConsumer(
     * PlatformDataKey, Executor, Consumer)}.
     *
     * @throws SecurityException if the provider does not have permission to provide requested data
     *     type.
     */
    @UiThread
    void registerSensorGatewayConsumer(
            @NonNull PlatformDataKey<?> key, @NonNull Consumer consumer);

    /**
     * Register for updates for the given data type. This may cause {@link Consumer} to immediately
     * fire if there is suitable cached data, otherwise {@link Consumer} will fire when there is
     * appropriate updates to the requested sensor data.
     *
     * <p>Implementations should check if the provider has permission to provide the requested data
     * type.
     *
     * <p>The callback will be executed on the provided {@link Executor}.
     *
     * @throws SecurityException if the provider does not have permission to provide requested data
     *     type.
     */
    @UiThread
    void registerSensorGatewayConsumer(
            @NonNull PlatformDataKey<?> key,
            @NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull Consumer consumer);

    /** Unregister for updates for the given data type. */
    @UiThread
    void unregisterSensorGatewayConsumer(
            @NonNull PlatformDataKey<?> key, @NonNull Consumer consumer);
}
