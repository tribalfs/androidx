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
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.SparseIntArray;

import com.android.support.navigation.R;

/**
 * NavDestination represents one node within an overall navigation graph.
 *
 * <p>Each destination has a {@link Navigator}. The navigator determines valid
 * {@link Navigator.Params parameters} that can be {@link #setNavigatorParams(Navigator.Params) set}
 * for each destination, and how those parameters will be {@link NavInflater inflated} from
 * a resource.</p>
 *
 * <p>Destinations declare a set of {@link #putActionDestination(int, int) actions} that they
 * support. These actions form a navigation API for the destination; the same actions declared
 * on different destinations that fill similar roles allow application code to navigate based
 * on semantic intent.</p>
 *
 * <p>Each destination has a set of {@link #getDefaultArguments() default arguments} that will
 * be applied when {@link NavController#navigate(int, Bundle) navigating} to that destination.
 * These arguments can be overridden at the time of navigation.</p>
 */
public class NavDestination {
    private final Navigator mNavigator;
    private int mId;
    private Navigator.Params mNavParams;
    private Bundle mDefaultArgs;
    private SparseIntArray mActions;
    private int mFlowId;

    /**
     * NavDestinations should be created via {@link Navigator#createDestination}.
     */
    public NavDestination(@NonNull Navigator navigator) {
        mNavigator = navigator;
    }

    /**
     * Called when inflating a destination from a resource.
     *
     * @param context local context performing inflation
     * @param attrs attrs to parse during inflation
     */
    public void onInflate(Context context, AttributeSet attrs) {
        final TypedArray a = context.getResources().obtainAttributes(attrs,
                R.styleable.NavDestination);
        setId(a.getResourceId(R.styleable.NavDestination_android_id, 0));
        a.recycle();

        setNavigatorParams(mNavigator.inflateParams(context, attrs));
    }

    /**
     * Returns the destination's unique ID. This should be an ID resource generated by
     * the Android resource system.
     *
     * @return this destination's ID
     */
    @IdRes
    public int getId() {
        return mId;
    }

    /**
     * Sets the destination's unique ID. This should be an ID resource generated by
     * the Android resource system.
     *
     * @param id this destination's new ID
     */
    public void setId(@IdRes int id) {
        mId = id;
    }

    /**
     * Returns the destination's {@link Navigator}.
     *
     * @return this destination's navigator
     */
    public Navigator getNavigator() {
        return mNavigator;
    }

    /**
     * Sets the destination's {@link Navigator.Params}.
     *
     * <p>The params object will be checked for validity by the
     * {@link #getNavigator() current navigator}. If the params are not valid, the navigator
     * will be asked to convert them.</p>
     *
     * @param params params to set
     */
    public void setNavigatorParams(Navigator.Params params) {
        if (!mNavigator.checkParams(params)) {
            Navigator.Params newParams = mNavigator.generateDefaultParams();
            newParams.copyFrom(params);
            params = newParams;
        }
        mNavParams = params;
    }

    /**
     * Returns the destination's {@link Navigator.Params}.
     *
     * @return this destination's params
     */
    public Navigator.Params getNavigatorParams() {
        return mNavParams;
    }

    /**
     * Returns the destination's default arguments bundle.
     *
     * @return the default arguments bundle
     */
    public @NonNull Bundle getDefaultArguments() {
        if (mDefaultArgs == null) {
            mDefaultArgs = new Bundle();
        }
        return mDefaultArgs;
    }

    /**
     * Sets the destination's default arguments bundle.
     *
     * @param args the new bundle to set
     */
    public void setDefaultArguments(Bundle args) {
        mDefaultArgs = args;
    }

    /**
     * Merges a bundle of arguments into the current default arguments for this destination.
     * New values with the same keys will replace old values with those keys.
     *
     * @param args arguments to add
     */
    public void addDefaultArguments(Bundle args) {
        getDefaultArguments().putAll(args);
    }

    /**
     * Returns the destination ID for a given action.
     *
     * @param id action ID to fetch
     * @return destination ID mapped to the given action id, or 0 if none
     */
    public @IdRes int getActionDestination(@IdRes int id) {
        if (mActions == null) {
            return 0;
        }
        return mActions.get(id);
    }

    /**
     * Sets a destination ID for an action ID.
     *
     * @param actionId action ID to bind
     * @param destId destination ID for the given action
     */
    public void putActionDestination(@IdRes int actionId, @IdRes int destId) {
        if (actionId == 0) {
            throw new IllegalArgumentException("cannot setActionDestination for actionId 0");
        }
        if (mActions == null) {
            mActions = new SparseIntArray();
        }
        mActions.put(actionId, destId);
    }

    /**
     * Unsets the destination ID for an action ID.
     *
     * @param actionId action ID to remove
     */
    public void removeActionDestination(@IdRes int actionId) {
        if (mActions == null) {
            return;
        }
        mActions.delete(actionId);
    }

    /**
     * Navigates to this destination.
     *
     * <p>Uses the {@link #getNavigator() configured navigator} to navigate to this destination.
     * Apps should not call this directly, instead use {@link NavController}'s navigation methods
     * to ensure consistent back stack tracking and behavior.</p>
     *
     * @param args arguments to the new destination
     * @param navOptions options for navigation
     * @return true if navigation created a back stack entry that should be tracked
     */
    public boolean navigate(Bundle args, NavOptions navOptions) {
        Bundle finalArgs = null;
        Bundle defaultArgs = getDefaultArguments();
        if (defaultArgs != null) {
            finalArgs = new Bundle();
            finalArgs.putAll(defaultArgs);
        }
        if (args != null) {
            if (finalArgs == null) {
                finalArgs = new Bundle();
            }
            finalArgs.putAll(args);
        }
        return mNavigator.navigate(this, finalArgs, navOptions);
    }
}
