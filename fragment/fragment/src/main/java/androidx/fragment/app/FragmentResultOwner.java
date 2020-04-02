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

package androidx.fragment.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

/**
 * A class that manages passing data between fragments.
 */
public interface FragmentResultOwner {

    /**
     * Sets the given result for the requestKey. This result will be delivered to a
     * {@link FragmentResultListener} that is called given to
     * {@link #setResultListener(String, LifecycleOwner, FragmentResultListener)} with the same
     * requestKey. If no {@link FragmentResultListener} with the same key is set or the Lifecycle
     * associated with the listener is not at least
     * {@link androidx.lifecycle.Lifecycle.State#STARTED}, the result is stored until one becomes
     * available, or a null result with the same requestKey is set.
     *
     * @param requestKey key used to identify the result
     * @param result the result to be passed to another fragment or {@code null} if you want to
     *               clear out any pending result.
     */
    void setResult(@NonNull String requestKey, @Nullable Bundle result);

    /**
     * Sets the {@link FragmentResultListener} for a given requestKey. Once the given
     * {@link LifecycleOwner} is at least in the {@link androidx.lifecycle.Lifecycle.State#STARTED}
     * state, any results set by {@link #setResult(String, Bundle)} using the same requestKey
     * will be delivered to the
     * {@link FragmentResultListener#onFragmentResult(String, Bundle) callback}. The callback will
     * remain active until the LifecycleOwner reaches the
     * {@link androidx.lifecycle.Lifecycle.State#DESTROYED} state or a null
     * {@link FragmentResultListener} is set for the same requestKey.
     *
     * @param requestKey requestKey used to store the result
     * @param lifecycleOwner lifecycleOwner for handling the result
     * @param listener listener for result changes or {@code null} to remove any previously
     *                 registered listener.
     */
    void setResultListener(@NonNull String requestKey, @NonNull LifecycleOwner lifecycleOwner,
            @Nullable FragmentResultListener listener);
}
