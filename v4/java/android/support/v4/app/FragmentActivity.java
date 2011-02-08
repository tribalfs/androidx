/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v4.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Base class for activities that want to use the support-based Fragment and
 * Loader APIs.
 */
public class FragmentActivity extends Activity {
    private static final String TAG = "FragmentActivity";
    
    private static final String FRAGMENTS_TAG = "android:support:fragments";
    
    final Handler mHandler = new Handler();
    final FragmentManagerImpl mFragments = new FragmentManagerImpl();
    
    boolean mCheckedForLoaderManager;
    boolean mLoadersStarted;
    HCSparseArray<LoaderManagerImpl> mAllLoaderManagers;
    LoaderManagerImpl mLoaderManager;
    
    static final class NonConfigurationInstances {
        Object activity;
        HashMap<String, Object> children;
        ArrayList<Fragment> fragments;
        HCSparseArray<LoaderManagerImpl> loaders;
    }
    
    static class FragmentTag {
        public static final int[] Fragment = {
            0x01010003, 0x010100d0, 0x010100d1
        };
        public static final int Fragment_id = 1;
        public static final int Fragment_name = 0;
        public static final int Fragment_tag = 2;
    }
    
    // ------------------------------------------------------------------------
    // HOOKS INTO ACTIVITY
    // ------------------------------------------------------------------------
    
    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int index = requestCode>>16;
        if (index != 0) {
            index--;
            if (mFragments.mActive == null || index < 0 || index >= mFragments.mActive.size()) {
                Log.w(TAG, "Activity result fragment index out of range: 0x"
                        + Integer.toHexString(requestCode));
                return;
            }
            Fragment frag = mFragments.mActive.get(index);
            if (frag == null) {
                Log.w(TAG, "Activity result no fragment exists for index: 0x"
                        + Integer.toHexString(requestCode));
            }
            frag.onActivityResult(requestCode&0xffff, resultCode, data);
            return;
        }
        
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Take care of popping the fragment back stack or finishing the activity
     * as appropriate.
     */
    public void onBackPressed() {
        if (!mFragments.popBackStackImmediate()) {
            finish();
        }
    }

    /**
     * Dispatch configuration change to all fragments.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mFragments.dispatchConfigurationChanged(newConfig);
    }

    /**
     * Perform initialization of all fragments and loaders.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFragments.attachActivity(this);
        // Old versions of the platform didn't do this!
        if (getLayoutInflater().getFactory() == null) {
            getLayoutInflater().setFactory(this);
        }
        
        super.onCreate(savedInstanceState);
        
        NonConfigurationInstances nc = (NonConfigurationInstances)
                getLastNonConfigurationInstance();
        if (nc != null) {
            mAllLoaderManagers = nc.loaders;
        }
        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(FRAGMENTS_TAG);
            mFragments.restoreAllState(p, nc != null ? nc.fragments : null);
        }
        mFragments.dispatchCreate();
    }

    /**
     * Dispatch to Fragment.onCreateOptionsMenu().
     */
    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean show = super.onCreatePanelMenu(featureId, menu);
            show |= mFragments.dispatchCreateOptionsMenu(menu, getMenuInflater());
            return show;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }
    
    /**
     * Add support for inflating the &lt;fragment> tag.
     */
    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        if (!"fragment".equals(name)) {
            return super.onCreateView(name, context, attrs);
        }
        
        String fname = attrs.getAttributeValue(null, "class");
        TypedArray a =  context.obtainStyledAttributes(attrs, FragmentTag.Fragment);
        if (fname == null) {
            fname = a.getString(FragmentTag.Fragment_name);
        }
        int id = a.getResourceId(FragmentTag.Fragment_id, View.NO_ID);
        String tag = a.getString(FragmentTag.Fragment_tag);
        a.recycle();
        
        View parent = null; // XXX no way to get parent pre-Honeycomb.
        int containerId = parent != null ? parent.getId() : 0;
        if (containerId == View.NO_ID && id == View.NO_ID && tag == null) {
            throw new IllegalArgumentException(attrs.getPositionDescription()
                    + ": Must specify unique android:id, android:tag, or have a parent with an id for " + fname);
        }

        // If we restored from a previous state, we may already have
        // instantiated this fragment from the state and should use
        // that instance instead of making a new one.
        Fragment fragment = id != View.NO_ID ? mFragments.findFragmentById(id) : null;
        if (fragment == null && tag != null) {
            fragment = mFragments.findFragmentByTag(tag);
        }
        if (fragment == null && containerId != View.NO_ID) {
            fragment = mFragments.findFragmentById(containerId);
        }

        if (FragmentManagerImpl.DEBUG) Log.v(TAG, "onCreateView: id=0x"
                + Integer.toHexString(id) + " fname=" + fname
                + " existing=" + fragment);
        if (fragment == null) {
            fragment = Fragment.instantiate(this, fname);
            fragment.mFromLayout = true;
            fragment.mFragmentId = id != 0 ? id : containerId;
            fragment.mContainerId = containerId;
            fragment.mTag = tag;
            fragment.mInLayout = true;
            fragment.mImmediateActivity = this;
            fragment.mFragmentManager = mFragments;
            fragment.onInflate(attrs, fragment.mSavedFragmentState);
            mFragments.addFragment(fragment, true);

        } else if (fragment.mInLayout) {
            // A fragment already exists and it is not one we restored from
            // previous state.
            throw new IllegalArgumentException(attrs.getPositionDescription()
                    + ": Duplicate id 0x" + Integer.toHexString(id)
                    + ", tag " + tag + ", or parent id 0x" + Integer.toHexString(containerId)
                    + " with another fragment for " + fname);
        } else {
            // This fragment was retained from a previous instance; get it
            // going now.
            fragment.mInLayout = true;
            fragment.mImmediateActivity = this;
            // If this fragment is newly instantiated (either right now, or
            // from last saved state), then give it the attributes to
            // initialize itself.
            if (!fragment.mRetaining) {
                fragment.onInflate(attrs, fragment.mSavedFragmentState);
            }
            mFragments.moveToState(fragment);
        }

        if (fragment.mView == null) {
            throw new IllegalStateException("Fragment " + fname
                    + " did not create a view.");
        }
        if (id != 0) {
            fragment.mView.setId(id);
        }
        if (fragment.mView.getTag() == null) {
            fragment.mView.setTag(tag);
        }
        return fragment.mView;
    }

    /**
     * Destroy all fragments and loaders.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mFragments.dispatchDestroy();
        if (mLoaderManager != null) {
            mLoaderManager.doDestroy();
        }
    }

    /**
     * Take care of calling onBackPressed() for pre-Eclair platforms.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (android.os.Build.VERSION.SDK_INT < 5 /* ECLAIR */
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            // Take care of calling this method on earlier versions of
            // the platform where it doesn't exist.
            onBackPressed();
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Dispatch onLowMemory() to all fragments.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mFragments.dispatchLowMemory();
    }

    /**
     * Dispatch context and options menu to fragments.
     */
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (super.onMenuItemSelected(featureId, item)) {
            return true;
        }
        
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                return mFragments.dispatchOptionsItemSelected(item);
                
            case Window.FEATURE_CONTEXT_MENU:
                return mFragments.dispatchContextItemSelected(item);

            default:
                return false;
        }
    }

    /**
     * Call onOptionsMenuClosed() on fragments.
     */
    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                mFragments.dispatchOptionsMenuClosed(menu);
                break;
        }
        super.onPanelClosed(featureId, menu);
    }
    
    /**
     * Dispatch onPause() to fragments.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mFragments.dispatchPause();
    }

    /**
     * Dispatch onActivityCreated() on fragments.
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mFragments.dispatchActivityCreated();
    }

    /**
     * Dispatch onResume() to fragments.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        mFragments.dispatchResume();
        mFragments.execPendingActions();
    }

    /**
     * Dispatch onPrepareOptionsMenu() to fragments.
     */
    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL && menu != null) {
            boolean goforit = super.onPreparePanel(featureId, view, menu);
            goforit |= mFragments.dispatchPrepareOptionsMenu(menu);
            return goforit && menu.hasVisibleItems();
        }
        return super.onPreparePanel(featureId, view, menu);
    }

    /**
     * Ensure any outstanding fragment transactions have been committed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mFragments.execPendingActions();
    }

    /**
     * Retain all appropriate fragment and loader state.  You can NOT
     * override this yourself!
     */
    @Override
    public final Object onRetainNonConfigurationInstance() {
        ArrayList<Fragment> fragments = mFragments.retainNonConfig();
        boolean retainLoaders = false;
        if (mAllLoaderManagers != null) {
            // prune out any loader managers that were already stopped and so
            // have nothing useful to retain.
            for (int i=mAllLoaderManagers.size()-1; i>=0; i--) {
                LoaderManagerImpl lm = mAllLoaderManagers.valueAt(i);
                if (lm.mRetaining) {
                    retainLoaders = true;
                } else {
                    lm.doDestroy();
                    mAllLoaderManagers.removeAt(i);
                }
            }
        }
        if (fragments == null && !retainLoaders) {
            return null;
        }
        
        NonConfigurationInstances nci = new NonConfigurationInstances();
        nci.activity = null;
        nci.children = null;
        nci.fragments = fragments;
        nci.loaders = mAllLoaderManagers;
        return nci;
    }

    /**
     * Save all appropriate fragment state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Parcelable p = mFragments.saveAllState();
        if (p != null) {
            outState.putParcelable(FRAGMENTS_TAG, p);
        }
    }

    /**
     * Dispatch onStart() to all fragments.  Ensure any created loaders are
     * now started.
     */
    @Override
    protected void onStart() {
        super.onStart();
        mFragments.noteStateNotSaved();
        mFragments.execPendingActions();
        
        
        if (!mLoadersStarted) {
            mLoadersStarted = true;
            if (mLoaderManager != null) {
                mLoaderManager.doStart();
            } else if (!mCheckedForLoaderManager) {
                mLoaderManager = getLoaderManager(-1, mLoadersStarted, false);
            }
            mCheckedForLoaderManager = true;
        }
        // XXX HC onStart goes here.
        
        mFragments.dispatchStart();
        if (mAllLoaderManagers != null) {
            for (int i=mAllLoaderManagers.size()-1; i>=0; i--) {
                mAllLoaderManagers.valueAt(i).finishRetain();
            }
        }
    }

    /**
     * Dispatch onStop() to all fragments.  Ensure all loaders are stopped.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (mLoadersStarted) {
            mLoadersStarted = false;
            if (mLoaderManager != null) {
                if (!isChangingConfigurations()) {
                    mLoaderManager.doStop();
                } else {
                    mLoaderManager.doRetain();
                }
            }
        }
        
        mFragments.dispatchStop();
    }

    // ------------------------------------------------------------------------
    // NEW METHODS
    // ------------------------------------------------------------------------
    
    /**
     * Note: this can't be supported pre-HC.  We'll see if we can figure out
     * something clever for it.
     */
    public void invalidateOptionsMenu() {
        // XXX TODO
    }
    
    /**
     * Not currently working, so retaining of loader state is broken.
     */
    public boolean isChangingConfigurations() {
        // XXX need to re-implement dependencies on this.
        return false;
    }
    
    /**
     * Print the Activity's state into the given stream.  This gets invoked if
     * you run "adb shell dumpsys activity <activity_component_name>".
     *
     * @param prefix Desired prefix to prepend at each line of output.
     * @param fd The raw file descriptor that the dump is being sent to.
     * @param writer The PrintWriter to which you should dump your state.  This will be
     * closed for you after you return.
     * @param args additional arguments to the dump request.
     */
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix); writer.print("Local Activity ");
                writer.print(Integer.toHexString(System.identityHashCode(this)));
                writer.println(" State:");
        String innerPrefix = prefix + "  ";
        //writer.print(innerPrefix); writer.print("mResumed=");
        //        writer.print(mResumed); writer.print(" mStopped=");
        //        writer.print(mStopped); writer.print(" mFinished=");
        //        writer.println(mFinished);
        writer.print(innerPrefix); writer.print("mLoadersStarted=");
                writer.println(mLoadersStarted);
        //writer.print(innerPrefix); writer.print("mCurrentConfig=");
        //        writer.println(mCurrentConfig);
        if (mLoaderManager != null) {
            writer.print(prefix); writer.print("Loader Manager ");
                    writer.print(Integer.toHexString(System.identityHashCode(mLoaderManager)));
                    writer.println(":");
            mLoaderManager.dump(prefix + "  ", fd, writer, args);
        }
        mFragments.dump(prefix, fd, writer, args);
    }

    // ------------------------------------------------------------------------
    // FRAGMENT SUPPORT
    // ------------------------------------------------------------------------
    
    /**
     * Called when a fragment is attached to the activity.
     */
    public void onAttachFragment(Fragment fragment) {
    }
    
    /**
     * Return the FragmentManager for interacting with fragments associated
     * with this activity.
     */
    public FragmentManager getSupportFragmentManager() {
        return mFragments;
    }

    /**
     * Modifies the standard behavior to allow results to be delivered to fragments.
     * This imposes a restriction that requestCode be <= 0xffff.
     */
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if ((requestCode&0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Called by Fragment.startActivityForResult() to implement its behavior.
     */
    public void startActivityFromFragment(Fragment fragment, Intent intent, 
            int requestCode) {
        if ((requestCode&0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
        super.startActivityForResult(intent, (fragment.mIndex+1)<<16 + (requestCode*0xffff));
    }
    
    void invalidateFragmentIndex(int index) {
        //Log.v(TAG, "invalidateFragmentIndex: index=" + index);
        if (mAllLoaderManagers != null) {
            LoaderManagerImpl lm = mAllLoaderManagers.get(index);
            if (lm != null) {
                lm.doDestroy();
            }
            mAllLoaderManagers.remove(index);
        }
    }
    
    // ------------------------------------------------------------------------
    // LOADER SUPPORT
    // ------------------------------------------------------------------------
    
    /**
     * Return the LoaderManager for this fragment, creating it if needed.
     */
    public LoaderManager getSupportLoaderManager() {
        if (mLoaderManager != null) {
            return mLoaderManager;
        }
        mCheckedForLoaderManager = true;
        mLoaderManager = getLoaderManager(-1, mLoadersStarted, true);
        return mLoaderManager;
    }
    
    LoaderManagerImpl getLoaderManager(int index, boolean started, boolean create) {
        if (mAllLoaderManagers == null) {
            mAllLoaderManagers = new HCSparseArray<LoaderManagerImpl>();
        }
        LoaderManagerImpl lm = mAllLoaderManagers.get(index);
        if (lm == null) {
            if (create) {
                lm = new LoaderManagerImpl(this, started);
                mAllLoaderManagers.put(index, lm);
            }
        } else {
            lm.updateActivity(this);
        }
        return lm;
    }
}
