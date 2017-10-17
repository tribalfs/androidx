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

package android.support.car.drawer;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.car.R;
import android.support.car.widget.PagedListView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import java.util.Stack;

/**
 * A controller that will handle the set up of the navigation drawer. It will hook up the
 * necessary buttons for up navigation, as well as expose methods to allow for a drill down
 * navigation.
 */
public class CarDrawerController {
    /** The amount that the drawer has been opened before its color should be switched. */
    private static final float COLOR_SWITCH_SLIDE_OFFSET = 0.25f;

    /**
     * A representation of the hierarchy of navigation being displayed in the list. The ordering of
     * this stack is the order that the user has visited each level. When the user navigates up,
     * the adapters are poopped from this list.
     */
    private final Stack<CarDrawerAdapter> mAdapterStack = new Stack<>();

    private final Context mContext;

    private final Toolbar mToolbar;
    private final DrawerLayout mDrawerLayout;
    private final ActionBarDrawerToggle mDrawerToggle;

    private final PagedListView mDrawerList;
    private final ProgressBar mProgressBar;
    private final View mDrawerContent;

    /**
     * Creates a {@link CarDrawerController} that will control the navigation of the drawer given by
     * {@code drawerLayout}.
     *
     * <p>The given {@code drawerLayout} should either have a child View that is inflated from
     * {@code R.layout.car_drawer} or ensure that it three children that have the IDs found in that
     * layout.
     *
     * @param toolbar The {@link Toolbar} that will serve as the action bar for an Activity.
     * @param drawerLayout The top-level container for the window content that shows the
     * interactive drawer.
     * @param drawerToggle The {@link ActionBarDrawerToggle} that bridges the given {@code toolbar}
     * and {@code drawerLayout}.
     */
    public CarDrawerController(Toolbar toolbar,
            DrawerLayout drawerLayout,
            ActionBarDrawerToggle drawerToggle) {
        mToolbar = toolbar;
        mContext = drawerLayout.getContext();

        mDrawerLayout = drawerLayout;

        mDrawerContent = drawerLayout.findViewById(R.id.drawer_content);
        mDrawerList = drawerLayout.findViewById(R.id.drawer_list);
        mDrawerList.setMaxPages(PagedListView.ItemCap.UNLIMITED);

        mProgressBar = drawerLayout.findViewById(R.id.drawer_progress);

        mDrawerToggle = drawerToggle;
        setupDrawerToggling();
    }

    /**
     * Sets the {@link CarDrawerAdapter} that will function as the root adapter. The contents of
     * this root adapter are shown when the drawer is first opened. It is also the top-most level of
     * navigation in the drawer.
     *
     * @param rootAdapter The adapter that will act as the root. If this value is {@code null}, then
     *                    this method will do nothing.
     */
    public void setRootAdapter(@Nullable CarDrawerAdapter rootAdapter) {
        if (rootAdapter == null) {
            return;
        }

        mAdapterStack.push(rootAdapter);
        setToolbarTitleFrom(rootAdapter);
        mDrawerList.setAdapter(rootAdapter);
    }

    /**
     * Switches to use the given {@link CarDrawerAdapter} as the one to supply the list to display
     * in the navigation drawer. The title will also be updated from the adapter.
     *
     * <p>This switch is treated as a navigation to the next level in the drawer. Navigation away
     * from this level will pop the given adapter off and surface contents of the previous adapter
     * that was set via this method. If no such adapter exists, then the root adapter set by
     * {@link #setRootAdapter(CarDrawerAdapter)} will be used instead.
     *
     * @param adapter Adapter for next level of content in the drawer.
     */
    public final void switchToAdapter(CarDrawerAdapter adapter) {
        mAdapterStack.peek().setTitleChangeListener(null);
        mAdapterStack.push(adapter);
        switchToAdapterInternal(adapter);
    }

    /** Close the drawer. */
    public void closeDrawer() {
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    /** Opens the drawer. */
    public void openDrawer() {
        if (!mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.openDrawer(Gravity.LEFT);
        }
    }

    /** Sets a listener to be notified of Drawer events. */
    public void addDrawerListener(@NonNull DrawerLayout.DrawerListener listener) {
        mDrawerLayout.addDrawerListener(listener);
    }

    /** Removes a listener to be notified of Drawer events. */
    public void removeDrawerListener(@NonNull DrawerLayout.DrawerListener listener) {
        mDrawerLayout.removeDrawerListener(listener);
    }

    /**
     * Sets whether the loading progress bar is displayed in the navigation drawer. If {@code true},
     * the progress bar is displayed and the navigation list is hidden and vice versa.
     */
    public void showLoadingProgressBar(boolean show) {
        mDrawerList.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Scroll to given position in the list. */
    public void scrollToPosition(int position) {
        mDrawerList.getRecyclerView().smoothScrollToPosition(position);
    }

    /**
     * Retrieves the title from the given {@link CarDrawerAdapter} and set its as the title of this
     * controller's internal Toolbar.
     */
    private void setToolbarTitleFrom(CarDrawerAdapter adapter) {
        if (adapter.getTitle() == null) {
            throw new RuntimeException("CarDrawerAdapter must supply a title via setTitle()");
        }

        mToolbar.setTitle(adapter.getTitle());
        adapter.setTitleChangeListener(mToolbar::setTitle);
    }

    /**
     * Sets up the necessary listeners for {@link DrawerLayout} so that the navigation drawer
     * hierarchy is properly displayed.
     */
    private void setupDrawerToggling() {
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerLayout.addDrawerListener(
                new DrawerLayout.DrawerListener() {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        // Correctly set the title and arrow colors as they are different between
                        // the open and close states.
                        updateTitleAndArrowColor(slideOffset >= COLOR_SWITCH_SLIDE_OFFSET);
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        // If drawer is closed, revert stack/drawer to initial root state.
                        cleanupStackAndShowRoot();
                        scrollToPosition(0);
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {}

                    @Override
                    public void onDrawerStateChanged(int newState) {}
                });
    }

    /** Sets the title and arrow color of the drawer depending on if it is open or not. */
    private void updateTitleAndArrowColor(boolean drawerOpen) {
        // When the drawer is open, use car_title, which resolves to appropriate color depending on
        // day-night mode. When drawer is closed, we always use light color.
        int titleColorResId = drawerOpen ? R.color.car_title : R.color.car_title_light;
        int titleColor = mContext.getColor(titleColorResId);
        mToolbar.setTitleTextColor(titleColor);
        mDrawerToggle.getDrawerArrowDrawable().setColor(titleColor);
    }

    /**
     * Synchronizes the display of the drawer with its linked {@link DrawerLayout}.
     *
     * <p>This should be called from the associated Activity's
     * {@link android.support.v7.app.AppCompatActivity#onPostCreate(Bundle)} method to synchronize
     * after teh DRawerLayout's instance state has been restored, and any other time when the
     * state may have diverged in such a way that this controller's associated
     * {@link ActionBarDrawerToggle} had not been notified.
     */
    public void syncState() {
        mDrawerToggle.syncState();

        // In case we're restarting after a config change (e.g. day, night switch), set colors
        // again. Doing it here so that Drawer state is fully synced and we know if its open or not.
        // NOTE: isDrawerOpen must be passed the second child of the DrawerLayout.
        updateTitleAndArrowColor(mDrawerLayout.isDrawerOpen(mDrawerContent));
    }

    /**
     * Notify this controller that device configurations may have changed.
     *
     * <p>This method should be called from the associated Activity's
     * {@code onConfigurationChanged()} method.
     */
    public void onConfigurationChanged(Configuration newConfig) {
        // Pass any configuration change to the drawer toggle.
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * An analog to an Activity's {@code onOptionsItemSelected()}. This method should be called
     * when the Activity's method is called and will return {@code true} if the selection has
     * been handled.
     *
     * @return {@code true} if the item processing was handled by this class.
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle home-click and see if we can navigate up in the drawer.
        if (item != null && item.getItemId() == android.R.id.home && maybeHandleUpClick()) {
            return true;
        }

        // DrawerToggle gets next chance to handle up-clicks (and any other clicks).
        return mDrawerToggle.onOptionsItemSelected(item);
    }

    /**
     * Sets the navigation drawer's title to be the one supplied by the given adapter and updates
     * the navigation drawer list with the adapter's contents.
     */
    private void switchToAdapterInternal(CarDrawerAdapter adapter) {
        setToolbarTitleFrom(adapter);
        // NOTE: We don't use swapAdapter() since different levels in the Drawer may switch between
        // car_drawer_list_item_normal, car_drawer_list_item_small and car_list_empty layouts.
        mDrawerList.getRecyclerView().setAdapter(adapter);
        scrollToPosition(0);
    }

    /**
     * Switches to the previous level in the drawer hierarchy if the current list being displayed
     * is not the root adapter. This is analogous to a navigate up.
     *
     * @return {@code true} if a navigate up was possible and executed. {@code false} otherwise.
     */
    private boolean maybeHandleUpClick() {
        // Check if already at the root level.
        if (mAdapterStack.size() <= 1) {
            return false;
        }

        CarDrawerAdapter adapter = mAdapterStack.pop();
        adapter.setTitleChangeListener(null);
        adapter.cleanup();
        switchToAdapterInternal(mAdapterStack.peek());
        return true;
    }

    /** Clears stack down to root adapter and switches to root adapter. */
    private void cleanupStackAndShowRoot() {
        while (mAdapterStack.size() > 1) {
            CarDrawerAdapter adapter = mAdapterStack.pop();
            adapter.setTitleChangeListener(null);
            adapter.cleanup();
        }
        switchToAdapterInternal(mAdapterStack.peek());
    }
}
