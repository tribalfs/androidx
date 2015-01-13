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

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.appcompat.R;
import android.support.v7.internal.view.SupportMenuInflater;
import android.support.v7.internal.view.WindowCallbackWrapper;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.widget.TintTypedArray;
import android.support.v7.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;

abstract class AppCompatDelegateImplBase extends AppCompatDelegate {

    final Activity mActivity;
    final AppCompatActivityCallback mAppCompatCallback;

    private ActionBar mActionBar;
    private MenuInflater mMenuInflater;

    // true if this activity has an action bar.
    boolean mHasActionBar;
    // true if this activity's action bar overlays other activity content.
    boolean mOverlayActionBar;
    // true if this any action modes should overlay the activity content
    boolean mOverlayActionMode;
    // true if this activity is floating (e.g. Dialog)
    boolean mIsFloating;

    private boolean mIsDestroyed;

    AppCompatDelegateImplBase(Activity activity, AppCompatActivityCallback callback) {
        mActivity = activity;
        mAppCompatCallback = callback;
        installWindowCallback();
    }

    abstract ActionBar createSupportActionBar();

    @Override
    public ActionBar getSupportActionBar() {
        // The Action Bar should be lazily created as hasActionBar
        // could change after onCreate
        if (mHasActionBar) {
            if (mActionBar == null) {
                mActionBar = createSupportActionBar();
            }
        }
        return mActionBar;
    }

    final ActionBar peekSupportActionBar() {
        return mActionBar;
    }

    final void setSupportActionBar(ActionBar actionBar) {
        mActionBar = actionBar;
    }

    @Override
    public MenuInflater getMenuInflater() {
        if (mMenuInflater == null) {
            mMenuInflater = new SupportMenuInflater(getActionBarThemedContext());
        }
        return mMenuInflater;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TypedArray a = mActivity.obtainStyledAttributes(R.styleable.Theme);

        if (!a.hasValue(R.styleable.Theme_windowActionBar)) {
            a.recycle();
            throw new IllegalStateException(
                    "You need to use a Theme.AppCompat theme (or descendant) with this activity.");
        }

        if (a.getBoolean(R.styleable.Theme_windowActionBar, false)) {
            mHasActionBar = true;
        }
        if (a.getBoolean(R.styleable.Theme_windowActionBarOverlay, false)) {
            mOverlayActionBar = true;
        }
        if (a.getBoolean(R.styleable.Theme_windowActionModeOverlay, false)) {
            mOverlayActionMode = true;
        }
        mIsFloating = a.getBoolean(R.styleable.Theme_android_windowIsFloating, false);
        a.recycle();
    }

    // Methods used to create and respond to options menu
    abstract boolean onPanelClosed(int featureId, Menu menu);

    abstract boolean onMenuOpened(int featureId, Menu menu);

    abstract boolean dispatchKeyEvent(KeyEvent event);

    abstract boolean onKeyShortcut(int keyCode, KeyEvent event);

    @Override
    public final ActionBarDrawerToggle.Delegate getDrawerToggleDelegate() {
        return new ActionBarDrawableToggleImpl();
    }

    final Context getActionBarThemedContext() {
        Context context = null;

        // If we have an action bar, let it return a themed context
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            context = ab.getThemedContext();
        }

        if (context == null) {
            context = mActivity;
        }
        return context;
    }

    private class ActionBarDrawableToggleImpl implements ActionBarDrawerToggle.Delegate {
        @Override
        public Drawable getThemeUpIndicator() {
            final TintTypedArray a = TintTypedArray.obtainStyledAttributes(
                    getActionBarThemedContext(), null, new int[]{ R.attr.homeAsUpIndicator });
            final Drawable result = a.getDrawable(0);
            a.recycle();
            return result;
        }

        @Override
        public Context getActionBarThemedContext() {
            return AppCompatDelegateImplBase.this.getActionBarThemedContext();
        }

        @Override
        public boolean isNavigationVisible() {
            final ActionBar ab = getSupportActionBar();
            return ab != null && (ab.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0;
        }

        @Override
        public void setActionBarUpIndicator(Drawable upDrawable, int contentDescRes) {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setHomeAsUpIndicator(upDrawable);
                ab.setHomeActionContentDescription(contentDescRes);
            }
        }

        @Override
        public void setActionBarDescription(int contentDescRes) {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setHomeActionContentDescription(contentDescRes);
            }
        }
    }

    abstract ActionMode startSupportActionModeFromWindow(ActionMode.Callback callback);

    @Override
    public final void onDestroy() {
        mIsDestroyed = true;
    }

    final boolean isDestroyed() {
        return mIsDestroyed;
    }

    final Window.Callback getWindowCallback() {
        Window window = mActivity.getWindow();
        return window != null ? window.getCallback() : null;
    }

    private void installWindowCallback() {
        final Window window = mActivity.getWindow();
        final Window.Callback callback = window.getCallback();

        if (callback instanceof AppCompatWindowCallback) {
            throw new IllegalStateException(
                    "AppCompat has already installed itself into the Window");
        }

        // Now install the new callback
        window.setCallback(new AppCompatWindowCallback(callback));
    }

    private class AppCompatWindowCallback extends WindowCallbackWrapper {
        AppCompatWindowCallback(Window.Callback callback) {
            super(callback);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (AppCompatDelegateImplBase.this.dispatchKeyEvent(event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            if (featureId == Window.FEATURE_OPTIONS_PANEL && !(menu instanceof MenuBuilder)) {
                // If this is an options menu but it's not an AppCompat menu, we eat the event
                // and return false
                return false;
            }
            return super.onCreatePanelMenu(featureId, menu);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            if (featureId == Window.FEATURE_OPTIONS_PANEL && !(menu instanceof MenuBuilder)) {
                // If this is an options menu but it's not an AppCompat menu, we eat the event
                // and return false
                return false;
            }

            if (featureId == Window.FEATURE_OPTIONS_PANEL
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                // If this is an options menu but we're running on a pre-v4.1 device, we need to
                // call onPrepareOptionsMenu() directly, bypassing Activity.onPreparePanel().
                // This is because Activity.onPreparePanel() on <v4.1 calls menu.hasVisibleItems(),
                // which interferes with any initial invisible items.
                return mActivity.onPrepareOptionsMenu(menu);
            }

            // Else, defer to the default handling
            return super.onPreparePanel(featureId, view, menu);
        }

        @Override
        public boolean onMenuOpened(int featureId, Menu menu) {
            if (AppCompatDelegateImplBase.this.onMenuOpened(featureId, menu)) {
                return true;
            }
            return super.onMenuOpened(featureId, menu);
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            if (AppCompatDelegateImplBase.this.onKeyShortcut(event.getKeyCode(), event)) {
                return true;
            }
            return super.dispatchKeyShortcutEvent(event);
        }

        @Override
        public void onContentChanged() {
            // We purposely do not propagate this call, as this is called when we install
            // our sub-decor rather than the user's content
        }

        @Override
        public void onPanelClosed(int featureId, Menu menu) {
            if (AppCompatDelegateImplBase.this.onPanelClosed(featureId, menu)) {
                return;
            }
            super.onPanelClosed(featureId, menu);
        }
    }
}
