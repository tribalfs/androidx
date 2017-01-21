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

package android.support.navigation.app.nav;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.util.AttributeSet;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Navigator defines a mechanism for navigating within an app.
 *
 * <p>Each Navigator sets the policy for a specific type of navigation, e.g.
 * {@link ActivityNavigator} knows how to launch into {@link NavDestination destinations}
 * backed by activities using {@link Context#startActivity(Intent) startActivity} and
 * {@link FragmentNavigator} knows how to navigate by replacing fragments within a container.</p>
 *
 * <p>Navigators should be able to manage their own back stack when navigating between two
 * destinations that belong to that navigator. The {@link NavController} manages a back stack of
 * navigators representing the current navigation stack across all navigators.</p>
 *
 * @param <P> the subclass of {@link Params} unique to the Navigator subclass
 */
public abstract class Navigator<P extends Navigator.Params> {
    private final CopyOnWriteArrayList<OnNavigatorNavigatedListener> mOnNavigatedListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Create and return a {@link Params navigator params} object with default values
     * for this navigator.
     * @return a new {@link Params} with default values
     */
    public abstract P generateDefaultParams();

    /**
     * Inflate a {@link Params} object from a resource.
     *
     * <p>Parses the navigator params from a {@link NavDestination destination} node
     * of a navigation graph resource. Navigator param attributes should have the prefix
     * {@code nav_}.</p>
     *
     * @param context Context used to resolve attrs
     * @param attrs attrs to parse
     * @return a new {@link Params} instance parsed from attrs
     */
    public abstract P inflateParams(Context context, AttributeSet attrs);

    /**
     * Check if a {@link Params} object is valid for this navigator.
     *
     * <p>Returns {@code true} if the given params object is of the right type and
     * properties are in range. If this method returns false, callers may use
     * {@link #generateDefaultParams()} to obtain valid params instead.</p>
     *
     * @param params params to check for validity
     * @return {@code true} if the given params are valid
     */
    public boolean checkParams(Navigator.Params params) {
        return true;
    }

    /**
     * Navigate to a destination.
     *
     * <p>Requests navigation to a given destination associated with this navigator in
     * the navigation graph. This method generally should not be called directly;
     * {@link NavController} will delegate to it when appropriate.</p>
     *
     * @param destination destination node to navigate to
     * @param args arguments to use for navigation
     * @param navOptions additional options for navigation
     * @return true if navigation created a back stack entry that should be tracked
     */
    public abstract boolean navigate(NavDestination destination, Bundle args,
                                     NavOptions navOptions);

    /**
     * Attempt to pop this navigator's back stack, performing the appropriate navigation.
     *
     * <p>Implementations should {@link #dispatchOnNavigatorNavigated(int, boolean)} to notify
     * listeners of the resulting navigation destination and return {@link true} if navigation
     * was successful. Implementations should return {@code false} if navigation could not
     * be performed, for example if the navigator's back stack was empty.</p>
     *
     * @return {@code true} if pop was successful
     */
    public abstract boolean popBackStack();

    /**
     * Add a listener to be notified when this navigator changes navigation destinations.
     *
     * <p>Most application code should use
     * {@link NavController#addOnNavigatedListener(NavController.OnNavigatedListener)} instead.
     * </p>
     *
     * @param listener listener to add
     */
    public final void addOnNavigatorNavigatedListener(OnNavigatorNavigatedListener listener) {
        mOnNavigatedListeners.add(listener);
    }

    /**
     * Remove a listener so that it will no longer be notified when this navigator changes
     * navigation destinations.
     *
     * @param listener listener to remove
     */
    public final void removeOnNavigatorNavigatedListener(OnNavigatorNavigatedListener listener) {
        mOnNavigatedListeners.remove(listener);
    }

    /**
     * Dispatch a navigated event to all registered {@link OnNavigatorNavigatedListener listeners}.
     * Utility for navigator implementations.
     *
     * @param destId id of the new destination
     * @param isBackStackEmpty true if this navigator's back stack is empty after this navigation
     */
    public final void dispatchOnNavigatorNavigated(@IdRes int destId, boolean isBackStackEmpty) {
        for (OnNavigatorNavigatedListener listener : mOnNavigatedListeners) {
            listener.onNavigatorNavigated(this, destId, isBackStackEmpty);
        }
    }

    /**
     * Base class for navigator parameters.
     *
     * <p>Subclasses of {@link Navigator} should also subclass this class to hold any special
     * data about a {@link NavDestination} that will be needed to navigate to that destination.
     * Examples include information about an intent to navigate to other activities, or a fragment
     * class name to instantiate and swap to a new fragment.</p>
     */
    public static class Params {
        /**
         * Copy all valid fields from the given Params into this instance
         * @param other the Params to copy all fields from
         */
        public void copyFrom(Params other) {
        }
    }

    /**
     * Listener for observing navigation events for this specific navigator. Most app code
     * should use {@link NavController.OnNavigatedListener} instead.
     */
    public interface OnNavigatorNavigatedListener {
        /**
         * This method is called after the Navigator navigates to a new destination.
         *
         * @param navigator
         * @param destId
         * @param isBackStackEmpty
         */
        void onNavigatorNavigated(Navigator navigator, @IdRes int destId, boolean isBackStackEmpty);
    }
}
