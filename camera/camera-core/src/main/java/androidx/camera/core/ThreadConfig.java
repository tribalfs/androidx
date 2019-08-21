/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.Config.Option;

import java.util.concurrent.Executor;

/**
 * Configuration containing options pertaining to threads used by the configured object.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
interface ThreadConfig {

    // Option Declarations:
    // *********************************************************************************************

    /**
     * Option: camerax.core.thread.callbackHandler
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    Option<Handler> OPTION_CALLBACK_HANDLER =
            Option.create("camerax.core.thread.callbackHandler", Handler.class);


    /**
     * Option: camerax.core.thread.backgroundExecutor
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    Option<Executor> OPTION_BACKGROUND_EXECUTOR =
            Option.create("camerax.core.thread.backgroundExecutor", Executor.class);

    // *********************************************************************************************

    /**
     * Returns the default handler that will be used for callbacks.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Nullable
    Handler getCallbackHandler(@Nullable Handler valueIfMissing);

    /**
     * Returns the default handler that will be used for callbacks.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @NonNull
    Handler getCallbackHandler();

    /**
     * Returns the executor that will be used for background tasks.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Nullable
    Executor getBackgroundExecutor(@Nullable Executor valueIfMissing);


    /**
     * Returns the executor that will be used for background tasks.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @NonNull
    Executor getBackgroundExecutor();

    /**
     * Builder for a {@link ThreadConfig}.
     *
     * @param <B> The top level builder type for which this builder is composed with.
     * @hide
     */

    @RestrictTo(Scope.LIBRARY_GROUP)
    interface Builder<B> {

        /**
         * Sets the default handler that will be used for callbacks.
         *
         * @param handler The handler which will be used to post callbacks.
         * @return the current Builder.
         */
        @NonNull
        B setCallbackHandler(@NonNull Handler handler);

        /**
         * Sets the default executor that will be used for background tasks.
         *
         * @param executor The executor which will be used for background tasks.
         * @return the current Builder.
         */
        @NonNull
        B setBackgroundExecutor(@NonNull Executor executor);
    }
}
