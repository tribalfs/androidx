/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.support.v7.app;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.support.v4.view.WindowCompat;
import android.support.v7.appcompat.R;
import android.support.v7.internal.view.menu.ListMenuPresenter;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPresenter;
import android.support.v7.internal.view.menu.MenuView;
import android.support.v7.internal.widget.ActionBarContainer;
import android.support.v7.internal.widget.ActionBarContextView;
import android.support.v7.internal.widget.ActionBarView;
import android.support.v7.internal.widget.ProgressBarICS;
import android.support.v7.view.ActionMode;
import android.support.v7.view.Menu;
import android.support.v7.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

class ActionBarActivityDelegateCompat extends ActionBarActivityDelegate implements
        MenuPresenter.Callback, MenuBuilder.Callback, ActionBarView.Callback {

    private ActionBarView mActionBarView;
    private ListMenuPresenter mListMenuPresenter;
    private MenuBuilder mMenu;

    private ActionMode mActionMode;
    private MenuBuilder mActionModeMenu;

    // true if we have installed a window sub-decor layout.
    private boolean mSubDecorInstalled;

    // Used to keep track of Progress Bar Window features
    private boolean mFeatureProgress, mFeatureIndeterminateProgress;

    private boolean mInvalidateMenuPosted;
    private final Runnable mInvalidateMenuRunnable = new Runnable() {
        @Override
        public void run() {
            final MenuBuilder menu = createMenu();
            if (dispatchCreateSupportOptionsMenu(menu) &&
                    dispatchPrepareSupportOptionsMenu(menu)) {
                setMenu(menu);
            } else {
                setMenu(null);
            }

            mInvalidateMenuPosted = false;
        }
    };

    ActionBarActivityDelegateCompat(ActionBarActivity activity) {
        super(activity);
    }

    @Override
    public ActionBar createSupportActionBar() {
        return new ActionBarImplCompat(mActivity, mActivity);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // If this is called before sub-decor is installed, ActionBar will not
        // be properly initialized.
        if (mHasActionBar && mSubDecorInstalled) {
            // Note: The action bar will need to access
            // view changes from superclass.
            ActionBarImplCompat actionBar = (ActionBarImplCompat) getSupportActionBar();
            actionBar.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onStop() {
        ActionBarImplCompat ab = (ActionBarImplCompat) getSupportActionBar();
        if (ab != null) {
            ab.setShowHideAnimationEnabled(false);
        }
    }

    @Override
    public void onPostResume() {
        ActionBarImplCompat ab = (ActionBarImplCompat) getSupportActionBar();
        if (ab != null) {
            ab.setShowHideAnimationEnabled(true);
        }
    }

    @Override
    public void setContentView(View v) {
        ensureSubDecor();
        if (mHasActionBar) {
            final ViewGroup contentParent =
                    (ViewGroup) mActivity.findViewById(R.id.action_bar_activity_content);
            contentParent.removeAllViews();
            contentParent.addView(v);
        } else {
            mActivity.superSetContentView(v);
        }
    }

    @Override
    public void setContentView(int resId) {
        ensureSubDecor();
        if (mHasActionBar) {
            final ViewGroup contentParent =
                    (ViewGroup) mActivity.findViewById(R.id.action_bar_activity_content);
            contentParent.removeAllViews();
            final LayoutInflater inflater = mActivity.getLayoutInflater();
            inflater.inflate(resId, contentParent);
        } else {
            mActivity.superSetContentView(resId);
        }
    }

    @Override
    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        ensureSubDecor();
        if (mHasActionBar) {
            final ViewGroup contentParent =
                    (ViewGroup) mActivity.findViewById(R.id.action_bar_activity_content);
            contentParent.removeAllViews();
            contentParent.addView(v, lp);
        } else {
            mActivity.superSetContentView(v, lp);
        }
    }

    @Override
    public void addContentView(View v, ViewGroup.LayoutParams lp) {
        ensureSubDecor();
        if (mHasActionBar) {
            final ViewGroup contentParent =
                    (ViewGroup) mActivity.findViewById(R.id.action_bar_activity_content);
            contentParent.addView(v, lp);
        } else {
            mActivity.superSetContentView(v, lp);
        }
    }

    private void ensureSubDecor() {
        if (mHasActionBar && !mSubDecorInstalled) {
            if (mOverlayActionBar) {
                mActivity.superSetContentView(R.layout.action_bar_decor_overlay);
            } else {
                mActivity.superSetContentView(R.layout.action_bar_decor);
            }
            mActionBarView = (ActionBarView) mActivity.findViewById(R.id.action_bar);
            mActionBarView.setViewCallback(this);

            /**
             * Progress Bars
             */
            if (mFeatureProgress) {
                mActionBarView.initProgress();
            }
            if (mFeatureIndeterminateProgress) {
                mActionBarView.initIndeterminateProgress();
            }

            /**
             * Split Action Bar
             */
            boolean splitWhenNarrow = UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW
                    .equals(getUiOptionsFromMetadata());
            boolean splitActionBar;

            if (splitWhenNarrow) {
                splitActionBar = mActivity.getResources()
                        .getBoolean(R.bool.split_action_bar_is_narrow);
            } else {
                TypedArray a = mActivity.obtainStyledAttributes(R.styleable.ActionBarWindow);
                splitActionBar = a
                        .getBoolean(R.styleable.ActionBarWindow_windowSplitActionBar, false);
                a.recycle();
            }

            final ActionBarContainer splitView = (ActionBarContainer) mActivity.findViewById(
                    R.id.split_action_bar);
            if (splitView != null) {
                mActionBarView.setSplitView(splitView);
                mActionBarView.setSplitActionBar(splitActionBar);
                mActionBarView.setSplitWhenNarrow(splitWhenNarrow);

                final ActionBarContextView cab = (ActionBarContextView) mActivity.findViewById(
                        R.id.action_context_bar);
                cab.setSplitView(splitView);
                cab.setSplitActionBar(splitActionBar);
                cab.setSplitWhenNarrow(splitWhenNarrow);
            }

            mSubDecorInstalled = true;

            supportInvalidateOptionsMenu();
        }
    }

    @Override
    public boolean supportRequestWindowFeature(int featureId) {
        switch (featureId) {
            case WindowCompat.FEATURE_ACTION_BAR:
                mHasActionBar = true;
                return true;
            case WindowCompat.FEATURE_ACTION_BAR_OVERLAY:
                mOverlayActionBar = true;
                return true;
            case Window.FEATURE_PROGRESS:
                mFeatureProgress = true;
                return true;
            case Window.FEATURE_INDETERMINATE_PROGRESS:
                mFeatureIndeterminateProgress = true;
                return true;
            default:
                return mActivity.requestWindowFeature(featureId);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(title);
        }
    }

    @Override
    public View onCreatePanelView(int featureId) {
        View createdPanelView = null;

        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean show = true;
            MenuBuilder menu = mMenu;

            if (mActionMode == null) {
                // We only want to dispatch Activity/Fragment menu calls if there isn't
                // currently an action mode

                if (menu == null) {
                    // We don't have a menu created, so create one
                    menu = createMenu();
                    setMenu(menu);

                    // Make sure we're not dispatching item changes to presenters
                    menu.stopDispatchingItemsChanged();
                    // Dispatch onCreateSupportOptionsMenu
                    show = dispatchCreateSupportOptionsMenu(menu);
                }

                if (show) {
                    // Make sure we're not dispatching item changes to presenters
                    menu.stopDispatchingItemsChanged();
                    // Dispatch onPrepareSupportOptionsMenu
                    show = dispatchPrepareSupportOptionsMenu(menu);
                }
            }

            if (show) {
                createdPanelView = (View) getListMenuView(mActivity, this);

                // Allow menu to start dispatching changes to presenters
                menu.startDispatchingItemsChanged();
            } else {
                // If the menu isn't being shown, we no longer need it
                setMenu(null);
            }
        }

        return createdPanelView;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, android.view.Menu frameworkMenu) {
        if (Window.FEATURE_OPTIONS_PANEL != featureId) {
            return mActivity.superOnCreatePanelMenu(featureId, frameworkMenu);
        }

        // Should never get here as FEATURE_OPTIONS_PANEL is handled by onCreatePanelView
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, android.view.Menu menu) {
        if (Window.FEATURE_OPTIONS_PANEL != featureId) {
            return mActivity.superOnPreparePanelMenu(featureId, view, menu);
        }

        // Should never get here as FEATURE_OPTIONS_PANEL is handled by onCreatePanelView
        return false;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, android.view.MenuItem frameworkItem) {
        // We don't want to handle framework items here
        return false;
    }

    @Override
    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
        return mActivity.onSupportMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
    }

    @Override
    public void onMenuModeChange(MenuBuilder menu) {
        reopenMenu(menu, true);
    }

    @Override
    public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
        mActivity.closeOptionsMenu();
    }

    @Override
    public boolean onOpenSubMenu(MenuBuilder subMenu) {
        return false;
    }

    @Override
    public ActionMode startSupportActionMode(ActionMode.Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("ActionMode callback can not be null.");
        }

        if (mActionMode != null) {
            mActionMode.finish();
        }

        final ActionMode.Callback wrappedCallback = new ActionModeCallbackWrapper(callback);
        ActionMode mode = null;

        ActionBarImplCompat ab = (ActionBarImplCompat) getSupportActionBar();
        if (ab != null) {
            mActionMode = ab.startActionMode(wrappedCallback);
        }

        if (mActionMode != null) {
            mActivity.onSupportActionModeStarted(mActionMode);
        }
        return mActionMode;
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        if (!mInvalidateMenuPosted) {
            mInvalidateMenuPosted = true;
            mActivity.getWindow().getDecorView().post(mInvalidateMenuRunnable);
        }
    }

    private MenuBuilder createMenu() {
        MenuBuilder menu = new MenuBuilder(getActionBarThemedContext());
        menu.setCallback(this);
        return menu;
    }

    private void reopenMenu(MenuBuilder menu, boolean toggleMenuMode) {
        if (mActionBarView != null && mActionBarView.isOverflowReserved()) {
            if (!mActionBarView.isOverflowMenuShowing() || !toggleMenuMode) {
                if (mActionBarView.getVisibility() == View.VISIBLE) {
                    mActionBarView.showOverflowMenu();
                }
            } else {
                mActionBarView.hideOverflowMenu();
            }
            return;
        }

        menu.close();
    }

    private MenuView getListMenuView(Context context, MenuPresenter.Callback cb) {
        if (mMenu == null) {
            return null;
        }

        if (mListMenuPresenter == null) {
            TypedArray a = context.obtainStyledAttributes(R.styleable.Theme);
            final int listPresenterTheme = a.getResourceId(
                    R.styleable.Theme_panelMenuListTheme,
                    R.style.Theme_AppCompat_CompactMenu);
            a.recycle();

            mListMenuPresenter = new ListMenuPresenter(
                    R.layout.list_menu_item_layout, listPresenterTheme);
            mListMenuPresenter.setCallback(cb);
            updateListMenuPresenterMenu();
        } else {
            // Make sure we update the ListView
            mListMenuPresenter.updateMenuView(false);
        }

        return mListMenuPresenter.getMenuView(null);
    }

    private void setMenu(MenuBuilder menu) {
        if (menu == mMenu) {
            return;
        }

        if (mMenu != null) {
            mMenu.removeMenuPresenter(mListMenuPresenter);
        }
        mMenu = menu;

        if (mActionModeMenu == null && menu != null && mListMenuPresenter != null) {
            // Only update list menu if there isn't an action mode menu
            menu.addMenuPresenter(mListMenuPresenter);
        }
        if (mActionBarView != null) {
            mActionBarView.setMenu(menu, this);
        }
    }

    @Override
    public boolean onBackPressed() {
        // Back cancels action modes first.
        if (mActionMode != null) {
            mActionMode.finish();
            return true;
        }

        // Next collapse any expanded action views.
        if (mActionBarView != null && mActionBarView.hasExpandedActionView()) {
            mActionBarView.collapseActionView();
            return true;
        }

        return false;
    }

    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {
        // Will never be called
    }

    @Override
    void setSupportProgressBarVisibility(boolean visible) {
        updateProgressBars(visible ? Window.PROGRESS_VISIBILITY_ON :
                Window.PROGRESS_VISIBILITY_OFF);
    }

    @Override
    void setSupportProgressBarIndeterminateVisibility(boolean visible) {
        updateProgressBars(visible ? Window.PROGRESS_VISIBILITY_ON :
                Window.PROGRESS_VISIBILITY_OFF);
    }

    @Override
    void setSupportProgressBarIndeterminate(boolean indeterminate) {
        updateProgressBars(indeterminate ? Window.PROGRESS_INDETERMINATE_ON
                : Window.PROGRESS_INDETERMINATE_OFF);
    }

    @Override
    void setSupportProgress(int progress) {
        updateProgressBars(Window.PROGRESS_START + progress);
    }

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {
        // Will never be called
    }

    private boolean dispatchCreateSupportOptionsMenu(MenuBuilder menu) {
        // Allow activity to inflate menu contents
        boolean show = mActivity.onCreateSupportOptionsMenu(menu);

        // FIXME: Reintroduce support options menu dispatch through facade.
        //show |= mActivity.mFragments.dispatchCreateSupportOptionsMenu(menu,
        //        mActivity.getCompatMenuInflater());

        return show;
    }

    private boolean dispatchPrepareSupportOptionsMenu(MenuBuilder menu) {
        boolean goforit = mActivity.onPrepareSupportOptionsMenu(menu);
        // FIXME: Reintroduce support options menu dispatch through facade.
        //goforit |= mActivity.mFragments.dispatchPrepareSupportOptionsMenu(menu);
        return goforit;
    }

    private void setActionModeMenu(MenuBuilder menu) {
        // Make sure that there are no menu's updating the list menu
        if (mActionModeMenu != null) {
            mActionModeMenu.removeMenuPresenter(mListMenuPresenter);
        }
        if (mMenu != null) {
            mMenu.removeMenuPresenter(mListMenuPresenter);
        }
        mActionModeMenu = menu;

        if (mListMenuPresenter != null) {
            updateListMenuPresenterMenu();
        }
    }

    private void updateListMenuPresenterMenu() {
        if (mActionModeMenu != null) {
            // We have a menu from an action mode so use it in the list menu
            mActionModeMenu.addMenuPresenter(mListMenuPresenter);
        } else if (mMenu != null) {
            // We have a menu from the activity/fragments so use it in the list menu
            mMenu.addMenuPresenter(mListMenuPresenter);
        }
    }

    /**
     * Progress Bar function. Mostly extracted from PhoneWindow.java
     */
    private void updateProgressBars(int value) {
        ProgressBarICS circularProgressBar = getCircularProgressBar();
        ProgressBarICS horizontalProgressBar = getHorizontalProgressBar();

        if (value == Window.PROGRESS_VISIBILITY_ON) {
            if (mFeatureProgress) {
                int level = horizontalProgressBar.getProgress();
                int visibility = (horizontalProgressBar.isIndeterminate() || level < 10000) ?
                        View.VISIBLE : View.INVISIBLE;
                horizontalProgressBar.setVisibility(visibility);
            }
            if (mFeatureIndeterminateProgress) {
                circularProgressBar.setVisibility(View.VISIBLE);
            }
        } else if (value == Window.PROGRESS_VISIBILITY_OFF) {
            if (mFeatureProgress) {
                horizontalProgressBar.setVisibility(View.GONE);
            }
            if (mFeatureIndeterminateProgress) {
                circularProgressBar.setVisibility(View.GONE);
            }
        } else if (value == Window.PROGRESS_INDETERMINATE_ON) {
            horizontalProgressBar.setIndeterminate(true);
        } else if (value == Window.PROGRESS_INDETERMINATE_OFF) {
            horizontalProgressBar.setIndeterminate(false);
        } else if (Window.PROGRESS_START <= value && value <= Window.PROGRESS_END) {
            // We want to set the progress value before testing for visibility
            // so that when the progress bar becomes visible again, it has the
            // correct level.
            horizontalProgressBar.setProgress(value - Window.PROGRESS_START);

            if (value < Window.PROGRESS_END) {
                showProgressBars(horizontalProgressBar, circularProgressBar);
            } else {
                hideProgressBars(horizontalProgressBar, circularProgressBar);
            }
        }
    }

    private void showProgressBars(ProgressBarICS horizontalProgressBar,
            ProgressBarICS spinnyProgressBar) {
        if (mFeatureIndeterminateProgress && spinnyProgressBar.getVisibility() == View.INVISIBLE) {
            spinnyProgressBar.setVisibility(View.VISIBLE);
        }
        // Only show the progress bars if the primary progress is not complete
        if (mFeatureProgress && horizontalProgressBar.getProgress() < 10000) {
            horizontalProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressBars(ProgressBarICS horizontalProgressBar,
            ProgressBarICS spinnyProgressBar) {
        if (mFeatureIndeterminateProgress && spinnyProgressBar.getVisibility() == View.VISIBLE) {
            spinnyProgressBar.setVisibility(View.INVISIBLE);
        }
        if (mFeatureProgress && horizontalProgressBar.getVisibility() == View.VISIBLE) {
            horizontalProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    private ProgressBarICS getCircularProgressBar() {
        ProgressBarICS pb = (ProgressBarICS) mActionBarView.findViewById(R.id.progress_circular);
        if (pb != null) {
            pb.setVisibility(View.INVISIBLE);
        }
        return pb;
    }

    private ProgressBarICS getHorizontalProgressBar() {
        ProgressBarICS pb = (ProgressBarICS) mActionBarView.findViewById(R.id.progress_horizontal);
        if (pb != null) {
            pb.setVisibility(View.INVISIBLE);
        }
        return pb;
    }

    /**
     *  From ActionBarView.Callback
     */
    @Override
    public boolean onMenuItemSelected(int featureId, android.support.v7.view.MenuItem item) {
        return mActivity.onSupportMenuItemSelected(featureId, item);
    }

    /**
     * Clears out internal reference when the action mode is destroyed.
     */
    private class ActionModeCallbackWrapper implements ActionMode.Callback {
        private ActionMode.Callback mWrapped;

        public ActionModeCallbackWrapper(ActionMode.Callback wrapped) {
            mWrapped = wrapped;
        }

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return mWrapped.onCreateActionMode(mode, menu);
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final boolean wrappedValue = mWrapped.onPrepareActionMode(mode, menu);
            setActionModeMenu((MenuBuilder) menu);
            return wrappedValue;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mWrapped.onActionItemClicked(mode, item);
        }

        public void onDestroyActionMode(ActionMode mode) {
            mWrapped.onDestroyActionMode(mode);
            mActivity.onSupportActionModeFinished(mode);

            setActionModeMenu(null);
            mActionMode = null;
        }
    }

}
