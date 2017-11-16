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
package android.arch.background.workmanager.constraints.controllers;

import android.arch.background.workmanager.constraints.listeners.ConstraintListener;
import android.arch.background.workmanager.constraints.trackers.ConstraintTracker;
import android.arch.background.workmanager.model.WorkSpec;
import android.arch.background.workmanager.utils.LiveDataUtils;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.OnLifecycleEvent;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;

/**
 * A controller for a particular constraint.
 *
 * @param <T> A specific type of {@link ConstraintListener} associated with this controller
 */

public abstract class ConstraintController<T extends ConstraintListener>
        implements LifecycleObserver {

    /**
     * A callback for when a constraint changes.
     */
    public interface OnConstraintUpdatedCallback {

        /**
         * Called when a constraint is met.
         *
         * @param workSpecs The list of {@link WorkSpec}s that may have become eligible to run
         */
        void onConstraintMet(List<WorkSpec> workSpecs);

        /**
         * Called when a constraint is not met.
         *
         * @param workSpecs The list of {@link WorkSpec}s that have become ineligible to run
         */
        void onConstraintNotMet(List<WorkSpec> workSpecs);
    }

    private static final String TAG = "ConstraintCtrlr";

    private LiveData<List<WorkSpec>> mConstraintLiveData;
    private LifecycleOwner mLifecycleOwner;
    private ConstraintTracker<T> mTracker;
    private Observer<List<WorkSpec>> mConstraintObserver;
    private OnConstraintUpdatedCallback mOnConstraintUpdatedCallback;
    private List<WorkSpec> mMatchingWorkSpecs;

    ConstraintController(
            LiveData<List<WorkSpec>> constraintLiveData,
            LifecycleOwner lifecycleOwner,
            ConstraintTracker<T> tracker,
            OnConstraintUpdatedCallback onConstraintUpdatedCallback) {

        mConstraintLiveData = LiveDataUtils.dedupedLiveDataFor(constraintLiveData);
        mLifecycleOwner = lifecycleOwner;
        mTracker = tracker;
        mOnConstraintUpdatedCallback = onConstraintUpdatedCallback;

        mConstraintObserver = new Observer<List<WorkSpec>>() {
            @Override
            public void onChanged(@Nullable List<WorkSpec> matchingWorkSpecs) {
                Log.d(
                        TAG,
                        ConstraintController.this.getClass().getSimpleName() + ": "
                                + matchingWorkSpecs);
                mMatchingWorkSpecs = matchingWorkSpecs;
                if (matchingWorkSpecs != null && matchingWorkSpecs.size() > 0) {
                    mTracker.addListener(getListener());
                    updateListener();
                } else {
                    mTracker.removeListener(getListener());
                }
            }
        };

        mLifecycleOwner.getLifecycle().addObserver(this);
    }

    /**
     * Registers the {@link Observer}.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onLifecycleStart() {
        mConstraintLiveData.observe(mLifecycleOwner, mConstraintObserver);
    }

    /**
     * Removes the {@link Observer} and stops tracking on the {@link ConstraintTracker}.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void shutdown() {
        mConstraintLiveData.removeObserver(mConstraintObserver);
        mTracker.removeListener(getListener());
    }

    /**
     * Determines if a particular {@link android.arch.background.workmanager.model.WorkSpec} is
     * considered constrained.  A constrained WorkSpec is one that is known to this controller and
     * the constraint for this controller is not met.  Note that if this controller has not yet
     * received a list of matching WorkSpec ids, *everything* is considered constrained.
     *
     * @param workSpec the {@link WorkSpec} to check
     * @return {@code true} if the WorkSpec is considered constrained
     */
    public boolean isWorkSpecConstrained(WorkSpec workSpec) {
        if (mMatchingWorkSpecs == null) {
            Log.d(TAG, getClass().getSimpleName() + ": null matching workspecs");
            return true;
        }
        return isConstrained() && mMatchingWorkSpecs.contains(workSpec);
    }

    protected void updateListener() {
        if (mMatchingWorkSpecs == null) {
            Log.d(TAG, getClass().getSimpleName() + ": updateListener - no workspecs!");
            return;
        }

        Log.d(TAG, getClass().getSimpleName() + ": updateListener");
        if (isConstrained()) {
            mOnConstraintUpdatedCallback.onConstraintNotMet(mMatchingWorkSpecs);
        } else {
            mOnConstraintUpdatedCallback.onConstraintMet(mMatchingWorkSpecs);
        }
    }

    abstract T getListener();

    abstract boolean isConstrained();
}
