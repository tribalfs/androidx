/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.fragment.app;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.CancellationSignal;
import androidx.core.view.ViewCompat;
import androidx.fragment.R;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelStoreOwner;

class FragmentStateManager {
    private static final String TAG = FragmentManager.TAG;

    private static final String TARGET_REQUEST_CODE_STATE_TAG = "android:target_req_state";
    private static final String TARGET_STATE_TAG = "android:target_state";
    private static final String VIEW_STATE_TAG = "android:view_state";
    private static final String USER_VISIBLE_HINT_TAG = "android:user_visible_hint";

    private final FragmentLifecycleCallbacksDispatcher mDispatcher;
    private final FragmentStore mFragmentStore;
    @NonNull
    private final Fragment mFragment;

    private int mFragmentManagerState = Fragment.INITIALIZING;
    private CancellationSignal mEnterAnimationCancellationSignal;
    private CancellationSignal mExitAnimationCancellationSignal;

    /**
     * Create a FragmentStateManager from a brand new Fragment instance.
     *
     * @param dispatcher Dispatcher for any lifecycle callbacks triggered by this class
     * @param fragmentStore FragmentStore handling all Fragments
     * @param fragment The Fragment to manage
     */
    FragmentStateManager(@NonNull FragmentLifecycleCallbacksDispatcher dispatcher,
            @NonNull FragmentStore fragmentStore, @NonNull Fragment fragment) {
        mDispatcher = dispatcher;
        mFragmentStore = fragmentStore;
        mFragment = fragment;
    }

    /**
     * Recreate a FragmentStateManager from a FragmentState instance, instantiating
     * a new Fragment from the {@link FragmentFactory}.
     *
     * @param dispatcher Dispatcher for any lifecycle callbacks triggered by this class
     * @param fragmentStore FragmentStore handling all Fragments
     * @param classLoader ClassLoader used to instantiate the Fragment
     * @param fragmentFactory FragmentFactory used to instantiate the Fragment
     * @param fs FragmentState used to restore the state correctly
     */
    FragmentStateManager(@NonNull FragmentLifecycleCallbacksDispatcher dispatcher,
            @NonNull FragmentStore fragmentStore,
            @NonNull ClassLoader classLoader,
            @NonNull FragmentFactory fragmentFactory,
            @NonNull FragmentState fs) {
        mDispatcher = dispatcher;
        mFragmentStore = fragmentStore;
        mFragment = fragmentFactory.instantiate(classLoader, fs.mClassName);
        if (fs.mArguments != null) {
            fs.mArguments.setClassLoader(classLoader);
        }
        mFragment.setArguments(fs.mArguments);
        mFragment.mWho = fs.mWho;
        mFragment.mFromLayout = fs.mFromLayout;
        mFragment.mRestored = true;
        mFragment.mFragmentId = fs.mFragmentId;
        mFragment.mContainerId = fs.mContainerId;
        mFragment.mTag = fs.mTag;
        mFragment.mRetainInstance = fs.mRetainInstance;
        mFragment.mRemoving = fs.mRemoving;
        mFragment.mDetached = fs.mDetached;
        mFragment.mHidden = fs.mHidden;
        mFragment.mMaxState = Lifecycle.State.values()[fs.mMaxLifecycleState];
        if (fs.mSavedFragmentState != null) {
            mFragment.mSavedFragmentState = fs.mSavedFragmentState;
        } else {
            // When restoring a Fragment, always ensure we have a
            // non-null Bundle so that developers have a signal for
            // when the Fragment is being restored
            mFragment.mSavedFragmentState = new Bundle();
        }
        if (FragmentManager.isLoggingEnabled(Log.VERBOSE)) {
            Log.v(TAG, "Instantiated fragment " + mFragment);
        }
    }

    /**
     * Recreate the FragmentStateManager from a retained Fragment and a
     * FragmentState instance.
     *
     * @param dispatcher Dispatcher for any lifecycle callbacks triggered by this class
     * @param fragmentStore FragmentStore handling all Fragments
     * @param retainedFragment A retained fragment
     * @param fs FragmentState used to restore the state correctly
     */
    FragmentStateManager(@NonNull FragmentLifecycleCallbacksDispatcher dispatcher,
            @NonNull FragmentStore fragmentStore,
            @NonNull Fragment retainedFragment,
            @NonNull FragmentState fs) {
        mDispatcher = dispatcher;
        mFragmentStore = fragmentStore;
        mFragment = retainedFragment;
        mFragment.mSavedViewState = null;
        mFragment.mBackStackNesting = 0;
        mFragment.mInLayout = false;
        mFragment.mAdded = false;
        mFragment.mTargetWho = mFragment.mTarget != null ? mFragment.mTarget.mWho : null;
        mFragment.mTarget = null;
        if (fs.mSavedFragmentState != null) {
            mFragment.mSavedFragmentState = fs.mSavedFragmentState;
        } else {
            // When restoring a Fragment, always ensure we have a
            // non-null Bundle so that developers have a signal for
            // when the Fragment is being restored
            mFragment.mSavedFragmentState = new Bundle();
        }
    }

    @NonNull
    Fragment getFragment() {
        return mFragment;
    }

    /**
     * Set the state of the FragmentManager. This will be used by
     * {@link #computeExpectedState()} to limit the max state of the Fragment.
     *
     * @param state one of the constants in {@link Fragment}
     */
    void setFragmentManagerState(int state) {
        mFragmentManagerState = state;
    }

    /**
     * Compute the state that the Fragment should be in given the internal
     * state of the Fragment and the signals passed into FragmentStateManager.
     *
     * @return the state that the Fragment should be in
     */
    int computeExpectedState() {
        // If the FragmentManager is null, disallow changing the state at all
        if (mFragment.mFragmentManager == null) {
            return mFragment.mState;
        }
        // Assume the Fragment can go as high as the FragmentManager's state
        int maxState = mFragmentManagerState;

        // For fragments that are created from a layout using the <fragment> tag (mFromLayout)
        if (mFragment.mFromLayout) {
            if (mFragment.mInLayout) {
                // Move them immediately to CREATED when they are
                // actually added to the layout (mInLayout).
                maxState = Math.max(mFragmentManagerState, Fragment.CREATED);
            } else {
                // But don't allow their state to progress upward beyond CREATED
                // if they're not in a layout
                maxState = Math.min(maxState, Fragment.CREATED);
            }
        }
        // Fragments that are not currently added will sit in the CREATED state.
        if (!mFragment.mAdded) {
            maxState = Math.min(maxState, Fragment.CREATED);
        }
        if (mFragment.mRemoving) {
            if (mFragment.isInBackStack()) {
                // Fragments on the back stack shouldn't go higher than CREATED
                maxState = Math.min(maxState, Fragment.CREATED);
            } else {
                // While removing a fragment, we always move to INITIALIZING
                maxState = Math.min(maxState, Fragment.INITIALIZING);
            }
        }
        // Defer start if requested; don't allow it to move to STARTED or higher
        // if it's not already started.
        if (mFragment.mDeferStart && mFragment.mState < Fragment.STARTED) {
            maxState = Math.min(maxState, Fragment.ACTIVITY_CREATED);
        }
        // Don't allow the Fragment to go above its max lifecycle state
        switch (mFragment.mMaxState) {
            case RESUMED:
                // maxState can't go any higher than RESUMED, so there's nothing to do here
                break;
            case STARTED:
                maxState = Math.min(maxState, Fragment.STARTED);
                break;
            case CREATED:
                maxState = Math.min(maxState, Fragment.CREATED);
                break;
            default:
                maxState = Math.min(maxState, Fragment.INITIALIZING);
        }
        return maxState;
    }

    void moveToExpectedState() {
        int newState;
        while ((newState = computeExpectedState()) != mFragment.mState) {
            if (newState > mFragment.mState) {
                // Moving upward
                int nextStep = mFragment.mState + 1;
                // Cancel any ongoing exit animations as we're moving the state upward
                if (mExitAnimationCancellationSignal != null) {
                    mExitAnimationCancellationSignal.cancel();
                }
                switch (nextStep) {
                    case Fragment.ATTACHED:
                        attach();
                        break;
                    case Fragment.CREATED:
                        create();
                        break;
                    case Fragment.ACTIVITY_CREATED:
                        ensureInflatedView();
                        createView();
                        activityCreated();
                        restoreViewState();
                        if (mFragment.mContainer != null) {
                            SpecialEffectsController controller = SpecialEffectsController
                                    .getOrCreateController(mFragment.mContainer);
                            mEnterAnimationCancellationSignal = new CancellationSignal();
                            controller.enqueueAdd(this,
                                    mEnterAnimationCancellationSignal);
                        }
                        break;
                    case Fragment.STARTED:
                        start();
                        break;
                    case Fragment.RESUMED:
                        resume();
                        break;
                }
            } else {
                // Moving downward
                int nextStep = mFragment.mState - 1;
                // Cancel any ongoing enter animations as we're moving the state downward
                if (mEnterAnimationCancellationSignal != null) {
                    mEnterAnimationCancellationSignal.cancel();
                }
                switch (nextStep) {
                    case Fragment.STARTED:
                        pause();
                        break;
                    case Fragment.ACTIVITY_CREATED:
                        stop();
                        break;
                    case Fragment.CREATED:
                        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
                            Log.d(TAG, "movefrom ACTIVITY_CREATED: " + mFragment);
                        }
                        // TODO call saveViewState()
                        if (mFragment.mContainer != null) {
                            SpecialEffectsController controller = SpecialEffectsController
                                    .getOrCreateController(mFragment.mContainer);
                            mExitAnimationCancellationSignal = new CancellationSignal();
                            controller.enqueueRemove(this,
                                    mExitAnimationCancellationSignal);
                        }
                        // TODO wait for the special effects to finish
                        destroyFragmentView();
                        break;
                    case Fragment.ATTACHED:
                        // TODO move this into destroy()
                        boolean beingRemoved = mFragment.mRemoving && !mFragment.isInBackStack();
                        if (beingRemoved
                                || mFragmentStore.getNonConfig().shouldDestroy(mFragment)) {
                            mFragmentStore.makeInactive(this);
                        } else {
                            if (mFragment.mTargetWho != null) {
                                Fragment target = mFragmentStore.findActiveFragment(
                                        mFragment.mTargetWho);
                                if (target != null && target.mRetainInstance) {
                                    // Only keep references to other retained Fragments
                                    // to avoid developers accessing Fragments that
                                    // are never coming back
                                    mFragment.mTarget = target;
                                }
                            }
                        }
                        // TODO wait for animations to complete
                        destroy();
                        break;
                    case Fragment.INITIALIZING:
                        detach();
                        break;
                }
            }
        }
    }

    void ensureInflatedView() {
        if (mFragment.mFromLayout && mFragment.mInLayout && !mFragment.mPerformedCreateView) {
            if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
                Log.d(TAG, "moveto CREATE_VIEW: " + mFragment);
            }
            mFragment.performCreateView(mFragment.performGetLayoutInflater(
                    mFragment.mSavedFragmentState), null, mFragment.mSavedFragmentState);
            if (mFragment.mView != null) {
                mFragment.mView.setSaveFromParentEnabled(false);
                mFragment.mView.setTag(R.id.fragment_container_view_tag, mFragment);
                if (mFragment.mHidden) mFragment.mView.setVisibility(View.GONE);
                mFragment.onViewCreated(mFragment.mView, mFragment.mSavedFragmentState);
                mDispatcher.dispatchOnFragmentViewCreated(
                        mFragment, mFragment.mView, mFragment.mSavedFragmentState, false);
            }
        }
    }

    void restoreState(@NonNull ClassLoader classLoader) {
        if (mFragment.mSavedFragmentState == null) {
            return;
        }
        mFragment.mSavedFragmentState.setClassLoader(classLoader);
        mFragment.mSavedViewState = mFragment.mSavedFragmentState.getSparseParcelableArray(
                VIEW_STATE_TAG);
        mFragment.mTargetWho = mFragment.mSavedFragmentState.getString(
                TARGET_STATE_TAG);
        if (mFragment.mTargetWho != null) {
            mFragment.mTargetRequestCode = mFragment.mSavedFragmentState.getInt(
                    TARGET_REQUEST_CODE_STATE_TAG, 0);
        }
        if (mFragment.mSavedUserVisibleHint != null) {
            mFragment.mUserVisibleHint = mFragment.mSavedUserVisibleHint;
            mFragment.mSavedUserVisibleHint = null;
        } else {
            mFragment.mUserVisibleHint = mFragment.mSavedFragmentState.getBoolean(
                    USER_VISIBLE_HINT_TAG, true);
        }
        if (!mFragment.mUserVisibleHint) {
            mFragment.mDeferStart = true;
        }
    }

    void attach() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto ATTACHED: " + mFragment);
        }
        // If we have a target fragment, ensure it moves to its expected state first
        // so that this fragment can rely on it as an initialized dependency.
        FragmentStateManager targetFragmentStateManager;
        if (mFragment.mTarget != null) {
            targetFragmentStateManager = mFragmentStore.getFragmentStateManager(
                    mFragment.mTarget.mWho);
            if (targetFragmentStateManager == null) {
                throw new IllegalStateException("Fragment " + mFragment
                        + " declared target fragment " + mFragment.mTarget
                        + " that does not belong to this FragmentManager!");
            }
            mFragment.mTargetWho = mFragment.mTarget.mWho;
            mFragment.mTarget = null;
        } else if (mFragment.mTargetWho != null) {
            targetFragmentStateManager = mFragmentStore.getFragmentStateManager(
                    mFragment.mTargetWho);
            if (targetFragmentStateManager == null) {
                throw new IllegalStateException("Fragment " + mFragment
                        + " declared target fragment " + mFragment.mTargetWho
                        + " that does not belong to this FragmentManager!");
            }
        } else {
            targetFragmentStateManager = null;
        }
        if (targetFragmentStateManager != null) {
            targetFragmentStateManager.moveToExpectedState();
        }
        mFragment.mHost = mFragment.mFragmentManager.getHost();
        mFragment.mParentFragment = mFragment.mFragmentManager.getParent();
        mDispatcher.dispatchOnFragmentPreAttached(mFragment, false);
        mFragment.performAttach();
        mDispatcher.dispatchOnFragmentAttached(mFragment, false);
    }

    void create() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto CREATED: " + mFragment);
        }
        if (!mFragment.mIsCreated) {
            mDispatcher.dispatchOnFragmentPreCreated(
                    mFragment, mFragment.mSavedFragmentState, false);
            mFragment.performCreate(mFragment.mSavedFragmentState);
            mDispatcher.dispatchOnFragmentCreated(
                    mFragment, mFragment.mSavedFragmentState, false);
        } else {
            mFragment.restoreChildFragmentState(mFragment.mSavedFragmentState);
            mFragment.mState = Fragment.CREATED;
        }
    }

    void createView() {
        if (mFragment.mFromLayout) {
            // This case is handled by ensureInflatedView(), so there's nothing
            // else we need to do here.
            return;
        }
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto CREATE_VIEW: " + mFragment);
        }
        ViewGroup container = null;
        if (mFragment.mContainer != null) {
            container = mFragment.mContainer;
        } else if (mFragment.mContainerId != 0) {
            if (mFragment.mContainerId == View.NO_ID) {
                throw new IllegalArgumentException("Cannot create fragment " + mFragment
                        + " for a container view with no id");
            }
            FragmentContainer fragmentContainer = mFragment.mFragmentManager.getContainer();
            container = (ViewGroup) fragmentContainer.onFindViewById(mFragment.mContainerId);
            if (container == null && !mFragment.mRestored) {
                String resName;
                try {
                    resName = mFragment.getResources().getResourceName(mFragment.mContainerId);
                } catch (Resources.NotFoundException e) {
                    resName = "unknown";
                }
                throw new IllegalArgumentException("No view found for id 0x"
                        + Integer.toHexString(mFragment.mContainerId) + " ("
                        + resName + ") for fragment " + mFragment);
            }
        }
        mFragment.mContainer = container;
        mFragment.performCreateView(mFragment.performGetLayoutInflater(
                mFragment.mSavedFragmentState), container, mFragment.mSavedFragmentState);
        if (mFragment.mView != null) {
            mFragment.mView.setSaveFromParentEnabled(false);
            mFragment.mView.setTag(R.id.fragment_container_view_tag, mFragment);
            if (container != null) {
                container.addView(mFragment.mView);
            }
            if (mFragment.mHidden) {
                mFragment.mView.setVisibility(View.GONE);
            }
            ViewCompat.requestApplyInsets(mFragment.mView);
            mFragment.onViewCreated(mFragment.mView, mFragment.mSavedFragmentState);
            mDispatcher.dispatchOnFragmentViewCreated(
                    mFragment, mFragment.mView, mFragment.mSavedFragmentState, false);
            // Only animate the view if it is visible. This is done after
            // dispatchOnFragmentViewCreated in case visibility is changed
            mFragment.mIsNewlyAdded = (mFragment.mView.getVisibility() == View.VISIBLE)
                    && mFragment.mContainer != null;
        }
    }

    void activityCreated() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto ACTIVITY_CREATED: " + mFragment);
        }
        mFragment.performActivityCreated(mFragment.mSavedFragmentState);
        mDispatcher.dispatchOnFragmentActivityCreated(
                mFragment, mFragment.mSavedFragmentState, false);
    }

    void restoreViewState() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto RESTORE_VIEW_STATE: " + mFragment);
        }
        if (mFragment.mView != null) {
            mFragment.restoreViewState(mFragment.mSavedFragmentState);
        }
        mFragment.mSavedFragmentState = null;
    }

    void start() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto STARTED: " + mFragment);
        }
        mFragment.performStart();
        mDispatcher.dispatchOnFragmentStarted(mFragment, false);
    }

    void resume() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "moveto RESUMED: " + mFragment);
        }
        mFragment.performResume();
        mDispatcher.dispatchOnFragmentResumed(mFragment, false);
        mFragment.mSavedFragmentState = null;
        mFragment.mSavedViewState = null;
    }

    void pause() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "movefrom RESUMED: " + mFragment);
        }
        mFragment.performPause();
        mDispatcher.dispatchOnFragmentPaused(mFragment, false);
    }

    void stop() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "movefrom STARTED: " + mFragment);
        }
        mFragment.performStop();
        mDispatcher.dispatchOnFragmentStopped(mFragment, false);
    }

    @NonNull
    FragmentState saveState() {
        FragmentState fs = new FragmentState(mFragment);

        if (mFragment.mState > Fragment.INITIALIZING && fs.mSavedFragmentState == null) {
            fs.mSavedFragmentState = saveBasicState();

            if (mFragment.mTargetWho != null) {
                if (fs.mSavedFragmentState == null) {
                    fs.mSavedFragmentState = new Bundle();
                }
                fs.mSavedFragmentState.putString(
                        TARGET_STATE_TAG,
                        mFragment.mTargetWho);
                if (mFragment.mTargetRequestCode != 0) {
                    fs.mSavedFragmentState.putInt(
                            TARGET_REQUEST_CODE_STATE_TAG,
                            mFragment.mTargetRequestCode);
                }
            }

        } else {
            fs.mSavedFragmentState = mFragment.mSavedFragmentState;
        }
        return fs;
    }

    @Nullable
    Fragment.SavedState saveInstanceState() {
        if (mFragment.mState > Fragment.INITIALIZING) {
            Bundle result = saveBasicState();
            return result != null ? new Fragment.SavedState(result) : null;
        }
        return null;
    }

    private Bundle saveBasicState() {
        Bundle result = new Bundle();

        mFragment.performSaveInstanceState(result);
        mDispatcher.dispatchOnFragmentSaveInstanceState(mFragment, result, false);
        if (result.isEmpty()) {
            result = null;
        }

        if (mFragment.mView != null) {
            saveViewState();
        }
        if (mFragment.mSavedViewState != null) {
            if (result == null) {
                result = new Bundle();
            }
            result.putSparseParcelableArray(
                    VIEW_STATE_TAG, mFragment.mSavedViewState);
        }
        if (!mFragment.mUserVisibleHint) {
            if (result == null) {
                result = new Bundle();
            }
            // Only add this if it's not the default value
            result.putBoolean(USER_VISIBLE_HINT_TAG, mFragment.mUserVisibleHint);
        }

        return result;
    }

    void saveViewState() {
        if (mFragment.mView == null) {
            return;
        }
        SparseArray<Parcelable> mStateArray = new SparseArray<>();
        mFragment.mView.saveHierarchyState(mStateArray);
        if (mStateArray.size() > 0) {
            mFragment.mSavedViewState = mStateArray;
        }
    }

    void destroyFragmentView() {
        mFragment.performDestroyView();
        mDispatcher.dispatchOnFragmentViewDestroyed(mFragment, false);
        mFragment.mContainer = null;
        mFragment.mView = null;
        // Set here to ensure that Observers are called after
        // the Fragment's view is set to null
        mFragment.mViewLifecycleOwner = null;
        mFragment.mViewLifecycleOwnerLiveData.setValue(null);
        mFragment.mInLayout = false;
    }

    void destroy() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "movefrom CREATED: " + mFragment);
        }
        boolean beingRemoved = mFragment.mRemoving && !mFragment.isInBackStack();
        boolean shouldDestroy = beingRemoved
                || mFragmentStore.getNonConfig().shouldDestroy(mFragment);
        if (shouldDestroy) {
            FragmentHostCallback<?> host = mFragment.mHost;
            boolean shouldClear;
            if (host instanceof ViewModelStoreOwner) {
                shouldClear = mFragmentStore.getNonConfig().isCleared();
            } else if (host.getContext() instanceof Activity) {
                Activity activity = (Activity) host.getContext();
                shouldClear = !activity.isChangingConfigurations();
            } else {
                shouldClear = true;
            }
            if (beingRemoved || shouldClear) {
                mFragmentStore.getNonConfig().clearNonConfigState(mFragment);
            }
            mFragment.performDestroy();
            mDispatcher.dispatchOnFragmentDestroyed(mFragment, false);
        } else {
            mFragment.mState = Fragment.ATTACHED;
        }
    }

    void detach() {
        if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
            Log.d(TAG, "movefrom ATTACHED: " + mFragment);
        }
        mFragment.performDetach();
        mDispatcher.dispatchOnFragmentDetached(
                mFragment, false);
        mFragment.mState = Fragment.INITIALIZING;
        mFragment.mHost = null;
        mFragment.mParentFragment = null;
        mFragment.mFragmentManager = null;
        boolean beingRemoved = mFragment.mRemoving && !mFragment.isInBackStack();
        if (beingRemoved || mFragmentStore.getNonConfig().shouldDestroy(mFragment)) {
            if (FragmentManager.isLoggingEnabled(Log.DEBUG)) {
                Log.d(TAG, "initState called for fragment: " + mFragment);
            }
            mFragment.initState();
        }
    }
}
