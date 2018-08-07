/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.work.impl.constraints.trackers;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.RestrictTo;

import androidx.work.Logger;
import androidx.work.impl.constraints.ConstraintListener;
import androidx.work.impl.utils.ThreadUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A base for tracking constraints and notifying listeners of changes.
 *
 * @param <T> the constraint data type observed by this tracker
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ConstraintTracker<T> {

    private static final String TAG = "ConstraintTracker";

    protected final Context mAppContext;
    private final Set<ConstraintListener<T>> mListeners = new LinkedHashSet<>();
    private T mCurrentState;

    ConstraintTracker(Context context) {
        mAppContext = context.getApplicationContext();
    }

    /**
     * Add the given listener for tracking.
     * This may cause {@link #getInitialState()} and {@link #startTracking()} to be invoked.
     * If a state is set, this will immediately notify the given listener.
     *
     * @param listener The target listener to start notifying
     */
    @MainThread
    public void addListener(ConstraintListener<T> listener) {
        if (mListeners.add(listener)) {
            if (mListeners.size() == 1) {
                mCurrentState = getInitialState();
                Logger.debug(TAG, String.format("%s: initial state = %s",
                        getClass().getSimpleName(),
                        mCurrentState));
                startTracking();
            }
            listener.onConstraintChanged(mCurrentState);
        }
    }

    /**
     * Remove the given listener from tracking.
     *
     * @param listener The listener to stop notifying.
     */
    @MainThread
    public void removeListener(ConstraintListener<T> listener) {
        if (mListeners.remove(listener) && mListeners.isEmpty()) {
            stopTracking();
        }
    }

    /**
     * Sets the state of the constraint.
     * If state is has not changed, nothing happens.
     *
     * @param newState new state of constraint
     */
    @MainThread
    public void setState(T newState) {
        ThreadUtils.assertMainThread();
        if (mCurrentState == newState
                || (mCurrentState != null && mCurrentState.equals(newState))) {
            return;
        }
        mCurrentState = newState;
        // Create a copy of the listeners.  #addListener and #removeListener can be called on
        // background threads, but #setState is always called on the main thread.
        Set<ConstraintListener<T>> listeners = Collections.unmodifiableSet(mListeners);
        for (ConstraintListener<T> listener : listeners) {
            listener.onConstraintChanged(mCurrentState);
        }
    }

    /**
     * Determines the initial state of the constraint being tracked.
     */
    abstract T getInitialState();

    /**
     * Start tracking for constraint state changes.
     */
    abstract void startTracking();

    /**
     * Stop tracking for constraint state changes.
     */
    abstract void stopTracking();
}
