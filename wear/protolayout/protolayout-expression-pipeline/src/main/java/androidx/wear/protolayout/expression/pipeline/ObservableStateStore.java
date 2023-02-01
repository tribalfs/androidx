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

package androidx.wear.protolayout.expression.pipeline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.UiThread;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;
import androidx.wear.protolayout.expression.proto.StateEntryProto.StateEntryValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * State storage for ProtoLayout, which also supports sending callback when data items change.
 *
 * <p>Note that this class is **not** thread-safe. Since ProtoLayout inflation currently happens on
 * the main thread, and because updates will eventually affect the main thread, this whole class
 * must only be used from the UI thread.
 */
public class ObservableStateStore {
    @NonNull private final Map<String, StateEntryValue> mCurrentState = new ArrayMap<>();

    @NonNull
    private final Map<String, Set<DynamicTypeValueReceiver<StateEntryValue>>> mRegisteredCallbacks =
            new ArrayMap<>();

    public ObservableStateStore(@NonNull Map<String, StateEntryValue> initialState) {
        mCurrentState.putAll(initialState);
    }

    /**
     * Sets the given state into a storage. It replaces the current state with the new map and
     * informs the registered listeners for changed values.
     */
    @UiThread
    public void setStateEntryValues(@NonNull Map<String, StateEntryValue> newState) {
        // Figure out which nodes have actually changed.
        List<String> changedKeys = new ArrayList<>();
        for (Entry<String, StateEntryValue> newEntry : newState.entrySet()) {
            StateEntryValue currentEntry = mCurrentState.get(newEntry.getKey());
            if (currentEntry == null || !currentEntry.equals(newEntry.getValue())) {
                changedKeys.add(newEntry.getKey());
            }
        }

        for (String key : changedKeys) {
            for (DynamicTypeValueReceiver<StateEntryValue> callback :
                    mRegisteredCallbacks.getOrDefault(key, Collections.emptySet())) {
                callback.onPreUpdate();
            }
        }

        mCurrentState.clear();
        mCurrentState.putAll(newState);

        for (String key : changedKeys) {
            for (DynamicTypeValueReceiver<StateEntryValue> callback :
                    mRegisteredCallbacks.getOrDefault(key, Collections.emptySet())) {
                if (newState.containsKey(key)) {
                    // The keys come from newState, so this should never be null.
                    callback.onData(newState.get(key));
                }
            }
        }
    }

    /**
     * Gets state with the given key.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @UiThread
    @Nullable
    public StateEntryValue getStateEntryValues(@NonNull String key) {
        return mCurrentState.get(key);
    }

    /**
     * Registers the given callback for updates to the state for the given key.
     *
     * <p>Note that the callback will be executed on the UI thread.
     */
    @UiThread
    void registerCallback(
            @NonNull String key, @NonNull DynamicTypeValueReceiver<StateEntryValue> callback) {
        mRegisteredCallbacks.computeIfAbsent(key, k -> new ArraySet<>()).add(callback);
    }

    /** Unregisters from receiving the updates. */
    @UiThread
    void unregisterCallback(
            @NonNull String key, @NonNull DynamicTypeValueReceiver<StateEntryValue> callback) {
        Set<DynamicTypeValueReceiver<StateEntryValue>> callbackSet = mRegisteredCallbacks.get(key);
        if (callbackSet != null) {
            callbackSet.remove(callback);
        }
    }
}
