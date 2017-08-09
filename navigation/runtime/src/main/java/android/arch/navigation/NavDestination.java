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

package android.arch.navigation;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v4.util.SparseArrayCompat;
import android.util.AttributeSet;

import java.util.ArrayList;

/**
 * NavDestination represents one node within an overall navigation graph.
 *
 * <p>Each destination is associated with a {@link Navigator} which knows how to navigate to this
 * particular destination.</p>
 *
 * <p>Destinations declare a set of {@link #putAction(int, int) actions} that they
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
    private NavGraph mParent;
    private int mId;
    private CharSequence mLabel;
    private Bundle mDefaultArgs;
    private ArrayList<NavDeepLink> mDeepLinks;
    private SparseArrayCompat<NavAction> mActions;

    /**
     * NavDestinations should be created via {@link Navigator#createDestination}.
     */
    public NavDestination(@NonNull Navigator<? extends NavDestination> navigator) {
        mNavigator = navigator;
    }

    /**
     * Called when inflating a destination from a resource.
     *
     * @param context local context performing inflation
     * @param attrs attrs to parse during inflation
     */
    @CallSuper
    public void onInflate(Context context, AttributeSet attrs) {
        final TypedArray a = context.getResources().obtainAttributes(attrs,
                R.styleable.Navigator);
        setId(a.getResourceId(R.styleable.Navigator_android_id, 0));
        setLabel(a.getText(R.styleable.Navigator_android_label));
        a.recycle();
    }

    void setParent(NavGraph parent) {
        mParent = parent;
    }

    /**
     * Gets the {@link NavGraph} that contains this destination. This will be set when a
     * destination is added to a NavGraph via {@link NavGraph#addDestination}.
     * @return
     */
    public NavGraph getParent() {
        return mParent;
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
     * Sets the descriptive label of this destination.
     *
     * @param label A descriptive label of this destination.
     */
    public void setLabel(CharSequence label) {
        mLabel = label;
    }

    /**
     * Gets the descriptive label of this destination.
     */
    public CharSequence getLabel() {
        return mLabel;
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
     * Add a deep link to this destination. Matching Uris sent to
     * {@link NavController#onHandleDeepLink(Intent)} will trigger navigating to this destination.
     * <p>
     * In addition to a direct Uri match, the following features are supported:
     * <ul>
     *     <li>Uris without a scheme are assumed as http and https. For example,
     *     <code>www.example.com</code> will match <code>http://www.example.com</code> and
     *     <code>https://www.example.com</code>.</li>
     *     <li>Placeholders in the form of <code>{placeholder_name}</code> matches 1 or more
     *     characters. The String value of the placeholder will be available in the arguments
     *     {@link Bundle} with a key of the same name. For example,
     *     <code>http://www.example.com/users/{id}</code> will match
     *     <code>http://www.example.com/users/4</code>.</li>
     *     <li>The <code>.*</code> wildcard can be used to match 0 or more characters.</li>
     * </ul>
     * These Uris can be declared in your navigation XML files by adding one or more
     * <code>&lt;deepLink app:uri="uriPattern" /&gt;</code> elements as
     * a child to your destination.
     * @param uriPattern The uri pattern to add as a deep link
     * @see NavController#onHandleDeepLink(Intent)
     */
    public void addDeepLink(@NonNull String uriPattern) {
        if (mDeepLinks == null) {
            mDeepLinks = new ArrayList<>();
        }
        mDeepLinks.add(new NavDeepLink(uriPattern));
    }

    /**
     * Determines if this NavDestination has a deep link matching the given Uri.
     * @param uri The Uri to match against all deep links added in {@link #addDeepLink(String)}
     * @return The matching {@link NavDestination} and the appropriate {@link Bundle} of arguments
     * extracted from the Uri, or null if no match was found.
     */
    @Nullable
    Pair<NavDestination, Bundle> matchDeepLink(@NonNull Uri uri) {
        if (mDeepLinks == null) {
            return null;
        }
        for (NavDeepLink deepLink : mDeepLinks) {
            Bundle matchingArguments = deepLink.getMatchingArguments(uri);
            if (matchingArguments != null) {
                return Pair.create(this, matchingArguments);
            }
        }
        return null;
    }

    /**
     * Returns the destination ID for a given action. This will recursively check the
     * {@link #getParent() parent} of this destination if the action destination is not found in
     * this destination.
     *
     * @param id action ID to fetch
     * @return destination ID mapped to the given action id, or 0 if none
     */
    public NavAction getAction(@IdRes int id) {
        NavAction destination = mActions == null ? null : mActions.get(id);
        // Search the parent for the given action if it is not found in this destination
        return destination != null
                ? destination
                : getParent() != null ? getParent().getAction(id) : null;
    }

    /**
     * Sets a destination ID for an action ID.
     *
     * @param actionId action ID to bind
     * @param destId destination ID for the given action
     */
    public void putAction(@IdRes int actionId, @IdRes int destId) {
        putAction(actionId, new NavAction(destId));
    }

    /**
     * Sets a destination ID for an action ID.
     *
     * @param actionId action ID to bind
     * @param action action to associate with this action ID
     */
    public void putAction(@IdRes int actionId, @NonNull NavAction action) {
        if (actionId == 0) {
            throw new IllegalArgumentException("Cannot have an action with actionId 0");
        }
        if (mActions == null) {
            mActions = new SparseArrayCompat<>();
        }
        mActions.put(actionId, action);
    }

    /**
     * Unsets the destination ID for an action ID.
     *
     * @param actionId action ID to remove
     */
    public void removeAction(@IdRes int actionId) {
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
     */
    public void navigate(Bundle args, NavOptions navOptions) {
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
        mNavigator.navigate(this, finalArgs, navOptions);
    }
}
