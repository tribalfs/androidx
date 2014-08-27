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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v4.view.WindowCompat;
import android.support.v7.appcompat.R;
import android.support.v7.internal.app.ToolbarActionBar;
import android.support.v7.internal.app.WindowDecorActionBar;
import android.support.v7.internal.view.StandaloneActionMode;
import android.support.v7.internal.view.menu.ListMenuPresenter;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPresenter;
import android.support.v7.internal.view.menu.MenuView;
import android.support.v7.internal.widget.ActionBarContextView;
import android.support.v7.internal.widget.DecorContentParent;
import android.support.v7.internal.widget.ProgressBarCompat;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

class ActionBarActivityDelegateBase extends ActionBarActivityDelegate
        implements MenuBuilder.Callback {
    private static final String TAG = "ActionBarActivityDelegateBase";

    private DecorContentParent mDecorContentParent;
    private ActionMenuPresenterCallback mActionMenuPresenterCallback;
    private PanelMenuPresenterCallback mPanelMenuPresenterCallback;

    private ListMenuPresenter mListMenuPresenter;
    private MenuBuilder mMenu;

    ActionMode mActionMode;
    ActionBarContextView mActionModeView;
    PopupWindow mActionModePopup;
    Runnable mShowActionModePopup;

    // true if we have installed a window sub-decor layout.
    private boolean mSubDecorInstalled;
    private ViewGroup mWindowDecor;

    private CharSequence mTitleToSet;

    // Used to keep track of Progress Bar Window features
    private boolean mFeatureProgress, mFeatureIndeterminateProgress;

    // Used for emulating PanelFeatureState
    private boolean mClosingActionMenu;
    private boolean mPanelIsPrepared;
    private boolean mPanelRefreshMenuContent;
    private Bundle mPanelFrozenActionViewState;

    private boolean mInvalidatePanelMenuPosted;
    private final Runnable mInvalidatePanelMenuRunnable = new Runnable() {
        @Override
        public void run() {
            supportInvalidateOptionsMenu();
        }
    };

    private boolean mEnableDefaultActionBarUp;

    ActionBarActivityDelegateBase(ActionBarActivity activity) {
        super(activity);
    }

    @Override
    void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWindowDecor = (ViewGroup) mActivity.getWindow().getDecorView();

        if (NavUtils.getParentActivityName(mActivity) != null) {
            ActionBar ab = getSupportActionBar();
            if (ab == null) {
                mEnableDefaultActionBarUp = true;
            } else {
                ab.setDefaultDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    public ActionBar createSupportActionBar() {
        ensureSubDecor();
        ActionBar ab = new WindowDecorActionBar(mActivity, mOverlayActionBar);
        ab.setDefaultDisplayHomeAsUpEnabled(mEnableDefaultActionBarUp);
        return ab;
    }

    @Override
    void setSupportActionBar(Toolbar toolbar) {
        if (getSupportActionBar() instanceof WindowDecorActionBar) {
            throw new IllegalStateException("This Activity already has an action bar supplied " +
                    "by the window decor. Do not request Window.FEATURE_ACTION_BAR and set " +
                    "windowActionBar to false in your theme to use a Toolbar instead.");
        }
        ToolbarActionBar tbab = new ToolbarActionBar(toolbar, mActivity.getTitle(),
                mWindowMenuCallback);
        setSupportActionBar(tbab);
        setWindowCallback(tbab.getWrappedWindowCallback());
        tbab.invalidateOptionsMenu();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // If this is called before sub-decor is installed, ActionBar will not
        // be properly initialized.
        if (mHasActionBar && mSubDecorInstalled) {
            // Note: The action bar will need to access
            // view changes from superclass.
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.onConfigurationChanged(newConfig);
            }
        }
    }

    @Override
    public void onStop() {
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setShowHideAnimationEnabled(false);
        }
    }

    @Override
    public void onPostResume() {
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setShowHideAnimationEnabled(true);
        }
    }

    @Override
    public void setContentView(View v) {
        ensureSubDecor();
        ViewGroup contentParent = (ViewGroup) mActivity.findViewById(android.R.id.content);
        contentParent.removeAllViews();
        contentParent.addView(v);
        mActivity.onSupportContentChanged();
    }

    @Override
    public void setContentView(int resId) {
        ensureSubDecor();
        ViewGroup contentParent = (ViewGroup) mActivity.findViewById(android.R.id.content);
        contentParent.removeAllViews();
        mActivity.getLayoutInflater().inflate(resId, contentParent);
        mActivity.onSupportContentChanged();
    }

    @Override
    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        ensureSubDecor();
        ViewGroup contentParent = (ViewGroup) mActivity.findViewById(android.R.id.content);
        contentParent.removeAllViews();
        contentParent.addView(v, lp);
        mActivity.onSupportContentChanged();
    }

    @Override
    public void addContentView(View v, ViewGroup.LayoutParams lp) {
        ensureSubDecor();
        ViewGroup contentParent = (ViewGroup) mActivity.findViewById(android.R.id.content);
        contentParent.addView(v, lp);
        mActivity.onSupportContentChanged();
    }

    @Override
    public void onContentChanged() {
        // Ignore all calls to this method as we call onSupportContentChanged manually above
    }

    final void ensureSubDecor() {
        if (!mSubDecorInstalled) {
            if (mHasActionBar) {
                mActivity.superSetContentView(R.layout.abc_screen_toolbar);

                ViewGroup root = (ViewGroup) mActivity.findViewById(R.id.action_bar_root);
                if (root != null && root.getChildCount() == 0) {
                    /**
                     * This needs some explanation. As we can not use the android:theme attribute
                     * pre-L, we emulate it by manually creating a LayoutInflater using a
                     * ContextThemeWrapper pointing to actionBarTheme.
                     */
                    TypedValue outValue = new TypedValue();
                    mActivity.getTheme().resolveAttribute(R.attr.actionBarTheme, outValue, true);

                    Context themedContext;
                    if (outValue.resourceId != 0) {
                        themedContext = new ContextThemeWrapper(mActivity, outValue.resourceId);
                    } else {
                        themedContext = mActivity;
                    }

                    LayoutInflater.from(themedContext)
                            .inflate(R.layout.abc_screen_toolbar_include, root, true);
                }

                mDecorContentParent = (DecorContentParent) mActivity
                        .findViewById(R.id.decor_content_parent);
                mDecorContentParent.setWindowCallback(mWindowMenuCallback);

                /**
                 * Propagate features to DecorContentParent
                 */
                if (mOverlayActionBar) {
                    mDecorContentParent.initFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
                }
                if (mFeatureProgress) {
                    mDecorContentParent.initFeature(Window.FEATURE_PROGRESS);
                }
                if (mFeatureIndeterminateProgress) {
                    mDecorContentParent.initFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
                }
            } else if (mOverlayActionMode) {
                mActivity.superSetContentView(R.layout.abc_screen_simple_overlay_action_mode);
            } else {
                mActivity.superSetContentView(R.layout.abc_screen_simple);
            }

            // Change our content FrameLayout to use the android.R.id.content id.
            // Useful for fragments.
            View content = mActivity.findViewById(android.R.id.content);
            content.setId(View.NO_ID);
            View abcContent = mActivity.findViewById(R.id.action_bar_activity_content);
            abcContent.setId(android.R.id.content);

            // A title was set before we've install the decor so set it now.
            if (mTitleToSet != null && mDecorContentParent != null) {
                mDecorContentParent.setWindowTitle(mTitleToSet);
                mTitleToSet = null;
            }

            applyFixedSizeWindow();

            onSubDecorInstalled();

            mSubDecorInstalled = true;

            invalidatePanelMenu();
        }
    }

    void onSubDecorInstalled() {}

    private void applyFixedSizeWindow() {
        TypedArray a = mActivity.obtainStyledAttributes(R.styleable.Theme);

        TypedValue mFixedWidthMajor = null;
        TypedValue mFixedWidthMinor = null;
        TypedValue mFixedHeightMajor = null;
        TypedValue mFixedHeightMinor = null;

        if (a.hasValue(R.styleable.Theme_windowFixedWidthMajor)) {
            if (mFixedWidthMajor == null) mFixedWidthMajor = new TypedValue();
            a.getValue(R.styleable.Theme_windowFixedWidthMajor, mFixedWidthMajor);
        }
        if (a.hasValue(R.styleable.Theme_windowFixedWidthMinor)) {
            if (mFixedWidthMinor == null) mFixedWidthMinor = new TypedValue();
            a.getValue(R.styleable.Theme_windowFixedWidthMinor, mFixedWidthMinor);
        }
        if (a.hasValue(R.styleable.Theme_windowFixedHeightMajor)) {
            if (mFixedHeightMajor == null) mFixedHeightMajor = new TypedValue();
            a.getValue(R.styleable.Theme_windowFixedHeightMajor, mFixedHeightMajor);
        }
        if (a.hasValue(R.styleable.Theme_windowFixedHeightMinor)) {
            if (mFixedHeightMinor == null) mFixedHeightMinor = new TypedValue();
            a.getValue(R.styleable.Theme_windowFixedHeightMinor, mFixedHeightMinor);
        }

        final DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.widthPixels < metrics.heightPixels;
        int w = ViewGroup.LayoutParams.MATCH_PARENT;
        int h = ViewGroup.LayoutParams.MATCH_PARENT;

        final TypedValue tvw = isPortrait ? mFixedWidthMinor : mFixedWidthMajor;
        if (tvw != null && tvw.type != TypedValue.TYPE_NULL) {
            if (tvw.type == TypedValue.TYPE_DIMENSION) {
                w = (int) tvw.getDimension(metrics);
            } else if (tvw.type == TypedValue.TYPE_FRACTION) {
                w = (int) tvw.getFraction(metrics.widthPixels, metrics.widthPixels);
            }
        }

        final TypedValue tvh = isPortrait ? mFixedHeightMajor : mFixedHeightMinor;
        if (tvh != null && tvh.type != TypedValue.TYPE_NULL) {
            if (tvh.type == TypedValue.TYPE_DIMENSION) {
                h = (int) tvh.getDimension(metrics);
            } else if (tvh.type == TypedValue.TYPE_FRACTION) {
                h = (int) tvh.getFraction(metrics.heightPixels, metrics.heightPixels);
            }
        }

        if (w != ViewGroup.LayoutParams.MATCH_PARENT || h != ViewGroup.LayoutParams.MATCH_PARENT) {
            mActivity.getWindow().setLayout(w, h);
        }

        a.recycle();
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
            case WindowCompat.FEATURE_ACTION_MODE_OVERLAY:
                mOverlayActionMode = true;
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
    public void onTitleChanged(CharSequence title) {
        if (mDecorContentParent != null) {
            mDecorContentParent.setWindowTitle(title);
        } else if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        } else {
            mTitleToSet = title;
        }
    }

    @Override
    public View onCreatePanelView(int featureId) {
        View createdPanelView = null;

        if (featureId == Window.FEATURE_OPTIONS_PANEL && preparePanel()) {
            createdPanelView = (View) getListMenuView(mActivity);
        }

        return createdPanelView;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId != Window.FEATURE_OPTIONS_PANEL) {
            return mWindowMenuCallback.onCreatePanelMenu(featureId, menu);
        }
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId != Window.FEATURE_OPTIONS_PANEL) {
            return mWindowMenuCallback.onPreparePanel(featureId, view, menu);
        }
        return false;
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            mPanelIsPrepared = false;
        }
    }

    @Override
    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
        return mActivity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
    }

    @Override
    public void onMenuModeChange(MenuBuilder menu) {
        reopenMenu(menu, true);
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

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            mActionMode = ab.startActionMode(wrappedCallback);
            if (mActionMode != null) {
                mActivity.onSupportActionModeStarted(mActionMode);
            }
        }

        if (mActionMode == null) {
            // If the action bar didn't provide an action mode, start the emulated window one
            mActionMode = startSupportActionModeFromWindow(wrappedCallback);
        }

        return mActionMode;
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        final ActionBar ab = getSupportActionBar();
        if (ab != null && ab.invalidateOptionsMenu()) return;

        if (mMenu != null) {
            Bundle savedActionViewStates = new Bundle();
            mMenu.saveActionViewStates(savedActionViewStates);
            if (savedActionViewStates.size() > 0) {
                mPanelFrozenActionViewState = savedActionViewStates;
            }
            // This will be started again when the panel is prepared.
            mMenu.stopDispatchingItemsChanged();
            mMenu.clear();
        }
        mPanelRefreshMenuContent = true;

        // Prepare the options panel if we have an action bar
        if (mDecorContentParent != null) {
            mPanelIsPrepared = false;
            preparePanel();
        }
    }

    @Override
    ActionMode startSupportActionModeFromWindow(ActionMode.Callback callback) {
        if (mActionMode != null) {
            mActionMode.finish();
        }

        final ActionMode.Callback wrappedCallback = new ActionModeCallbackWrapper(callback);
        ActionMode mode = null;

        if (mActionModeView == null) {
            if (mIsFloating) {
                mActionModeView = new ActionBarContextView(mActivity);
                mActionModePopup = new PopupWindow(mActivity, null,
                        R.attr.actionModePopupWindowStyle);
                mActionModePopup.setContentView(mActionModeView);
                mActionModePopup.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);

                TypedValue heightValue = new TypedValue();
                mActivity.getTheme().resolveAttribute(R.attr.actionBarSize, heightValue, true);
                final int height = TypedValue.complexToDimensionPixelSize(heightValue.data,
                        mActivity.getResources().getDisplayMetrics());
                mActionModeView.setContentHeight(height);
                mActionModePopup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                mShowActionModePopup = new Runnable() {
                    public void run() {
                        mActionModePopup.showAtLocation(
                                mActionModeView,
                                Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0);
                    }
                };
            } else {
                ViewStub stub = (ViewStub) mActivity.findViewById(R.id.action_mode_bar_stub);
                if (stub != null) {
                    mActionModeView = (ActionBarContextView) stub.inflate();
                }
            }
        }

        if (mActionModeView != null) {
            mActionModeView.killMode();
            mode = new StandaloneActionMode(mActivity, mActionModeView, wrappedCallback,
                    mActionModePopup == null);
            if (callback.onCreateActionMode(mode, mode.getMenu())) {
                mode.invalidate();
                mActionModeView.initForMode(mode);
                mActionModeView.setVisibility(View.VISIBLE);
                mActionMode = mode;
                if (mActionModePopup != null) {
                    mActivity.getWindow().getDecorView().post(mShowActionModePopup);
                }
                mActionModeView.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            } else {
                mActionMode = null;
            }
        }
        if (mActionMode != null && mActivity != null) {
            mActivity.onSupportActionModeStarted(mActionMode);
        }
        return mActionMode;
    }

    private void reopenMenu(MenuBuilder menu, boolean toggleMenuMode) {
        if (mDecorContentParent != null && mDecorContentParent.canShowOverflowMenu() &&
                (!ViewConfigurationCompat.hasPermanentMenuKey(ViewConfiguration.get(mActivity)) ||
                        mDecorContentParent.isOverflowMenuShowPending())) {
            if (!mDecorContentParent.isOverflowMenuShowing() || !toggleMenuMode) {

                // If we have a menu invalidation pending, do it now.
                if (mInvalidatePanelMenuPosted) {
                    mWindowDecor.removeCallbacks(mInvalidatePanelMenuRunnable);
                    mInvalidatePanelMenuRunnable.run();
                }

                // If we don't have a menu or we're waiting for a full content refresh,
                // forget it. This is a lingering event that no longer matters.
                if (mMenu != null && !mPanelRefreshMenuContent && preparePanel()) {
                    mDecorContentParent.showOverflowMenu();
                }
            } else {
                mDecorContentParent.hideOverflowMenu();
            }
            return;
        }
    }

    private MenuView getListMenuView(Context context) {
        if (mMenu == null) {
            return null;
        }

        if (mPanelMenuPresenterCallback == null) {
            mPanelMenuPresenterCallback = new PanelMenuPresenterCallback();
        }

        if (mListMenuPresenter == null) {
            TypedArray a = context.obtainStyledAttributes(R.styleable.Theme);
            final int listPresenterTheme = a.getResourceId(
                    R.styleable.Theme_panelMenuListTheme,
                    R.style.Theme_AppCompat_CompactMenu);
            a.recycle();

            mListMenuPresenter = new ListMenuPresenter(
                    R.layout.abc_list_menu_item_layout, listPresenterTheme);
            mListMenuPresenter.setCallback(mPanelMenuPresenterCallback);
            mMenu.addMenuPresenter(mListMenuPresenter, mActivity);
        } else {
            // Make sure we update the ListView
            mListMenuPresenter.updateMenuView(false);
        }

        if (mListMenuPresenter.getAdapter().isEmpty()) {
            return null;
        }

        return mListMenuPresenter.getMenuView(mWindowDecor);
    }

    @Override
    public boolean onBackPressed() {
        // Back cancels action modes first.
        if (mActionMode != null) {
            mActionMode.finish();
            return true;
        }

        // Next collapse any expanded action views.
        ActionBar ab = getSupportActionBar();
        if (ab != null && ab.collapseActionView()) {
            return true;
        }

        return false;
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
    int getHomeAsUpIndicatorAttrId() {
        return R.attr.homeAsUpIndicator;
    }

    /**
     * Progress Bar function. Mostly extracted from PhoneWindow.java
     */
    private void updateProgressBars(int value) {
        ProgressBarCompat circularProgressBar = getCircularProgressBar();
        ProgressBarCompat horizontalProgressBar = getHorizontalProgressBar();

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

    private void showProgressBars(ProgressBarCompat horizontalProgressBar,
            ProgressBarCompat spinnyProgressBar) {
        if (mFeatureIndeterminateProgress && spinnyProgressBar.getVisibility() == View.INVISIBLE) {
            spinnyProgressBar.setVisibility(View.VISIBLE);
        }
        // Only show the progress bars if the primary progress is not complete
        if (mFeatureProgress && horizontalProgressBar.getProgress() < 10000) {
            horizontalProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressBars(ProgressBarCompat horizontalProgressBar,
            ProgressBarCompat spinnyProgressBar) {
        if (mFeatureIndeterminateProgress && spinnyProgressBar.getVisibility() == View.VISIBLE) {
            spinnyProgressBar.setVisibility(View.INVISIBLE);
        }
        if (mFeatureProgress && horizontalProgressBar.getVisibility() == View.VISIBLE) {
            horizontalProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    private ProgressBarCompat getCircularProgressBar() {
        ProgressBarCompat pb = (ProgressBarCompat) mActivity.findViewById(R.id.progress_circular);
        if (pb != null) {
            pb.setVisibility(View.INVISIBLE);
        }
        return pb;
    }

    private ProgressBarCompat getHorizontalProgressBar() {
        ProgressBarCompat pb = (ProgressBarCompat) mActivity.findViewById(R.id.progress_horizontal);
        if (pb != null) {
            pb.setVisibility(View.INVISIBLE);
        }
        return pb;
    }

    private boolean initializePanelMenu() {
        Context context = mActivity;

        if (mDecorContentParent != null) {
            final TypedValue outValue = new TypedValue();
            final Resources.Theme baseTheme = context.getTheme();
            baseTheme.resolveAttribute(R.attr.actionBarTheme, outValue, true);

            Resources.Theme widgetTheme = null;
            if (outValue.resourceId != 0) {
                widgetTheme = context.getResources().newTheme();
                widgetTheme.setTo(baseTheme);
                widgetTheme.applyStyle(outValue.resourceId, true);
                widgetTheme.resolveAttribute(
                        R.attr.actionBarWidgetTheme, outValue, true);
            } else {
                baseTheme.resolveAttribute(
                        R.attr.actionBarWidgetTheme, outValue, true);
            }

            if (outValue.resourceId != 0) {
                if (widgetTheme == null) {
                    widgetTheme = context.getResources().newTheme();
                    widgetTheme.setTo(baseTheme);
                }
                widgetTheme.applyStyle(outValue.resourceId, true);
            }

            if (widgetTheme != null) {
                context = new ContextThemeWrapper(context, 0);
                context.getTheme().setTo(widgetTheme);
            }
        }

        mMenu = new MenuBuilder(context);
        mMenu.setCallback(this);

        return true;
    }

    private boolean preparePanel() {
        // Already prepared (isPrepared will be reset to false later)
        if (mPanelIsPrepared) {
            return true;
        }

        if (mDecorContentParent != null) {
            // Enforce ordering guarantees around events so that the action bar never
            // dispatches menu-related events before the panel is prepared.
            mDecorContentParent.setMenuPrepared();
        }

        // Init the panel state's menu--return false if init failed
        if (mMenu == null || mPanelRefreshMenuContent) {
            if (mMenu == null) {
                if (!initializePanelMenu() || (mMenu == null)) {
                    return false;
                }
            }

            if (mDecorContentParent != null) {
                if (mActionMenuPresenterCallback == null) {
                    mActionMenuPresenterCallback = new ActionMenuPresenterCallback();
                }
                mDecorContentParent.setMenu(mMenu, mActionMenuPresenterCallback);
            }

            // Creating the panel menu will involve a lot of manipulation;
            // don't dispatch change events to presenters until we're done.
            mMenu.stopDispatchingItemsChanged();

            // Call callback, and return if it doesn't want to display menu.
            if (!mWindowMenuCallback.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, mMenu)) {
                // Ditch the menu created above
                mMenu = null;

                if (mDecorContentParent != null) {
                    // Don't show it in the action bar either
                    mDecorContentParent.setMenu(null, mActionMenuPresenterCallback);
                }

                return false;
            }

            mPanelRefreshMenuContent = false;
        }

        // Preparing the panel menu can involve a lot of manipulation;
        // don't dispatch change events to presenters until we're done.
        mMenu.stopDispatchingItemsChanged();

        // Restore action view state before we prepare. This gives apps
        // an opportunity to override frozen/restored state in onPrepare.
        if (mPanelFrozenActionViewState != null) {
            mMenu.restoreActionViewStates(mPanelFrozenActionViewState);
            mPanelFrozenActionViewState = null;
        }

        // Callback and return if the callback does not want to show the menu
        if (!mWindowMenuCallback.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, mMenu)) {
            if (mDecorContentParent != null) {
                // The app didn't want to show the menu for now but it still exists.
                // Clear it out of the action bar.
                mDecorContentParent.setMenu(null, mActionMenuPresenterCallback);
            }
            mMenu.startDispatchingItemsChanged();
            return false;
        }

        mMenu.startDispatchingItemsChanged();

        // Set other state
        mPanelIsPrepared = true;

        return true;
    }

    private void checkCloseActionMenu() {
        if (mClosingActionMenu) {
            return;
        }

        mClosingActionMenu = true;
        mDecorContentParent.dismissPopups();
        mClosingActionMenu = false;
    }

    private void closePanel(int featureId) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL && mDecorContentParent != null &&
                mDecorContentParent.canShowOverflowMenu() &&
                !ViewConfigurationCompat.hasPermanentMenuKey(ViewConfiguration.get(mActivity))) {
            mDecorContentParent.hideOverflowMenu();
        } else {
            mActivity.closeOptionsMenu();
            mPanelIsPrepared = false;
        }
    }

    private void invalidatePanelMenu() {
        if (!mInvalidatePanelMenuPosted && mWindowDecor != null) {
            ViewCompat.postOnAnimation(mWindowDecor, mInvalidatePanelMenuRunnable);
            mInvalidatePanelMenuPosted = true;
        }
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
            return mWrapped.onPrepareActionMode(mode, menu);
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return mWrapped.onActionItemClicked(mode, item);
        }

        public void onDestroyActionMode(ActionMode mode) {
            mWrapped.onDestroyActionMode(mode);
            if (mActionModePopup != null) {
                mActivity.getWindow().getDecorView().removeCallbacks(mShowActionModePopup);
                mActionModePopup.dismiss();
            } else if (mActionModeView != null) {
                mActionModeView.setVisibility(View.GONE);
            }
            if (mActionModeView != null) {
                mActionModeView.removeAllViews();
            }
            if (mActivity != null) {
                try {
                    mActivity.onSupportActionModeFinished(mActionMode);
                } catch (AbstractMethodError ame) {
                    // Older apps might not implement this callback method.
                }
            }
            mActionMode = null;
        }
    }

    private final class PanelMenuPresenterCallback implements MenuPresenter.Callback {
        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            return false;
        }

        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            closePanel(Window.FEATURE_OPTIONS_PANEL);
        }
    }

    private final class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            return false;
        }

        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            checkCloseActionMenu();
        }
    }

}
