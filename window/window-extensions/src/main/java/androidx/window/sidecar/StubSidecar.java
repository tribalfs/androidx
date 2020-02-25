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

package androidx.window.sidecar;

import android.os.IBinder;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Basic implementation of the {@link SidecarInterface}. An OEM can choose to use it as the base
 * class for their implementation.
 */
abstract class StubSidecar implements SidecarInterface {
    private SidecarCallback mSidecarCallback;
    private final Set<IBinder> mWindowLayoutChangeListenerTokens = new HashSet<>();
    private boolean mDeviceStateChangeListenerRegistered;

    @Override
    public void setSidecarCallback(@NonNull SidecarCallback callback) {
        mSidecarCallback = callback;
    }

    @Override
    public void onWindowLayoutChangeListenerAdded(@NonNull IBinder windowToken) {
        mWindowLayoutChangeListenerTokens.add(windowToken);
        onListenersChanged();
    }

    @Override
    public void onWindowLayoutChangeListenerRemoved(@NonNull IBinder windowToken) {
        mWindowLayoutChangeListenerTokens.remove(windowToken);
        onListenersChanged();
    }

    @Override
    public void onDeviceStateListenersChanged(boolean isEmpty) {
        mDeviceStateChangeListenerRegistered = !isEmpty;
        onListenersChanged();
    }

    protected void updateDeviceState(@NonNull SidecarDeviceState newState) {
        if (mSidecarCallback != null) {
            mSidecarCallback.onDeviceStateChanged(newState);
        }
    }

    protected void updateWindowLayout(@NonNull IBinder windowToken,
            @NonNull SidecarWindowLayoutInfo newLayout) {
        if (mSidecarCallback != null) {
            mSidecarCallback.onWindowLayoutChanged(windowToken, newLayout);
        }
    }

    @NonNull
    protected Set<IBinder> getWindowsListeningForLayoutChanges() {
        return mWindowLayoutChangeListenerTokens;
    }

    protected boolean hasListeners() {
        return !mWindowLayoutChangeListenerTokens.isEmpty() || mDeviceStateChangeListenerRegistered;
    }

    /** Notification to the OEM level that the registered listeners changed. */
    protected abstract void onListenersChanged();
}
