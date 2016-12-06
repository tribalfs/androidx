/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.support.lifecycle.state;

import android.support.annotation.MainThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

/**
 *  Factory and utility methods for {@link SavedStateProvider} and {@link RetainedStateProvider}
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class StateProviders {

    static final String HOLDER_TAG =
            "com.android.support.lifecycle.state.StateProviderHolderFragment";

    private static HolderFragment holderFragmentFor(FragmentManager manager) {
        Fragment fragmentByTag = manager.findFragmentByTag(HOLDER_TAG);
        if (fragmentByTag != null && !(fragmentByTag instanceof HolderFragment)) {
            throw new IllegalStateException("Unexpected "
                    + "fragment instance was returned by HOLDER_TAG");
        }

        HolderFragment holder = (HolderFragment) fragmentByTag;
        if (holder == null) {
            holder = new HolderFragment();
            manager.beginTransaction().add(holder, HOLDER_TAG).commitNowAllowingStateLoss();
        }
        return holder;
    }

    //TODO: create getter from LifecycleProvider

    /**
     * Returns {@link SavedStateProvider} associated with the given fragment.
     * if this call was made after fragment saved its state, all later operations on this
     * {@link SavedStateProvider} may be lost.
     */
    @MainThread
    public static SavedStateProvider savedStateProvider(Fragment fragment) {
        return holderFragmentFor(fragment.getChildFragmentManager()).getSavedStateProvider();
    }

    /**
     * Returns {@link SavedStateProvider} associated with the given activity.
     * if this call was made after activity saved its state, all later operations on this
     * {@link SavedStateProvider} may be lost.
     */
    @MainThread
    public static SavedStateProvider savedStateProvider(FragmentActivity activity) {
        return holderFragmentFor(activity.getSupportFragmentManager()).getSavedStateProvider();
    }

    /**
     * Returns {@link RetainedStateProvider} associated with the given fragment.
     * if this call was made after fragment saved its state, all later operations on this
     * {@link RetainedStateProvider} may be lost.
     */
    @MainThread
    public static RetainedStateProvider retainedStateProvider(Fragment fragment) {
        return holderFragmentFor(fragment.getChildFragmentManager()).getRetainedStateProvider();
    }

    /**
     * Returns {@link RetainedStateProvider} associated with the given fragment.
     * if this call was made after fragment saved its state, all later operations on this
     * {@link RetainedStateProvider} may be lost.
     */
    public static RetainedStateProvider retainedStateProvider(FragmentActivity activity) {
        return holderFragmentFor(activity.getSupportFragmentManager()).getRetainedStateProvider();
    }
}
