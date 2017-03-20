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
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.SparseArray;

import com.android.support.navigation.R;

/**
 * NavGraph is a collection of {@link NavDestination} nodes fetchable by ID.
 *
 * <p>A NavGraph serves as a 'virtual' destination: while the NavGraph itself will not appear
 * on the back stack, navigating to the NavGraph will cause the
 * {@link #getStartDestination starting destination} to be added to the back stack.</p>
 */
public class NavGraph extends NavDestination {
    private final SparseArray<NavDestination> mNodes = new SparseArray<>();
    private int mStartDestId;

    /**
     * Construct a new NavGraph.
     *
     * @param navigatorProvider The {@link NavController} which this NavGraph
     *                          will be associated with.
     */
    public NavGraph(@NonNull NavigatorProvider navigatorProvider) {
        super(navigatorProvider.getNavigator(NavGraphNavigator.NAME));
    }

    NavGraph(@NonNull Navigator navigator) {
        super(navigator);
    }

    @Override
    public void onInflate(Context context, AttributeSet attrs) {
        super.onInflate(context, attrs);
        TypedArray a = context.getResources().obtainAttributes(attrs,
                R.styleable.NavGraph);
        setStartDestination(
                a.getResourceId(R.styleable.NavGraph_startDestination, 0));
        a.recycle();
    }

    /**
     * Adds a destination to the collection.
     *
     * @param node destination to add
     */
    public void addDestination(NavDestination node) {
        if (node.getId() == 0) {
            throw new IllegalArgumentException("Destinations must have an id."
                    + " Call setId() or include an android:id in your navigation XML.");
        }
        mNodes.put(node.getId(), node);
    }

    /**
     * Finds a destination in the collection by ID.
     *
     * @param resid ID to locate
     * @return the node with ID resid
     */
    public NavDestination findNode(@IdRes int resid) {
        return mNodes.get(resid);
    }

    /**
     * Add all destinations from another collection to this one.
     *
     * @param other collection of destinations to add
     */
    public void addAll(NavGraph other) {
        for (int i = 0, size = other.mNodes.size(); i < size; i++) {
            mNodes.put(other.mNodes.keyAt(i), other.mNodes.valueAt(i));
        }
    }

    /**
     * Returns the starting destination for this NavGraph. When navigating to the NavGraph, this
     * destination is the one the user will initially see.
     * @return
     */
    @IdRes
    public int getStartDestination() {
        return mStartDestId;
    }

    /**
     * Sets the starting destination for this NavGraph.
     *
     * @param startDestId The id of the destination to be shown when navigating to this NavGraph.
     */
    public void setStartDestination(@IdRes int startDestId) {
        mStartDestId = startDestId;
    }
}
