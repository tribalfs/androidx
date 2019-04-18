/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.activity;

import static android.os.Build.VERSION.SDK_INT;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.CallSuper;
import androidx.annotation.ContentView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.GenericLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ReportFragment;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;

/**
 * Base class for activities that enables composition of higher level components.
 * <p>
 * Rather than all functionality being built directly into this class, only the minimal set of
 * lower level building blocks are included. Higher level components can then be used as needed
 * without enforcing a deep Activity class hierarchy or strong coupling between components.
 */
public class ComponentActivity extends androidx.core.app.ComponentActivity implements
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner,
        OnBackPressedDispatcherOwner {

    static final class NonConfigurationInstances {
        Object custom;
        ViewModelStore viewModelStore;
    }

    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);
    private final SavedStateRegistryController mSavedStateRegistryController =
            SavedStateRegistryController.create(this);

    // Lazily recreated from NonConfigurationInstances by getViewModelStore()
    private ViewModelStore mViewModelStore;

    private final OnBackPressedDispatcher mOnBackPressedDispatcher = new OnBackPressedDispatcher();

    @LayoutRes
    private int mContentLayoutId;

    /**
     * Default constructor for ComponentActivity. All Activities must have a default constructor
     * for API 27 and lower devices or when using the default
     * {@link android.app.AppComponentFactory}.
     */
    public ComponentActivity() {
        Lifecycle lifecycle = getLifecycle();
        //noinspection ConstantConditions
        if (lifecycle == null) {
            throw new IllegalStateException("getLifecycle() returned null in ComponentActivity's "
                    + "constructor. Please make sure you are lazily constructing your Lifecycle "
                    + "in the first call to getLifecycle() rather than relying on field "
                    + "initialization.");
        }
        if (Build.VERSION.SDK_INT >= 19) {
            getLifecycle().addObserver(new GenericLifecycleObserver() {
                @Override
                public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
                    if (event == Lifecycle.Event.ON_STOP) {
                        Window window = getWindow();
                        final View decor = window != null ? window.peekDecorView() : null;
                        if (decor != null) {
                            decor.cancelPendingInputEvents();
                        }
                    }
                }
            });
        }
        getLifecycle().addObserver(new GenericLifecycleObserver() {
            @Override
            public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    if (!isChangingConfigurations()) {
                        getViewModelStore().clear();
                    }
                }
            }
        });

        if (19 <= SDK_INT && SDK_INT <= 23) {
            getLifecycle().addObserver(new ImmLeaksCleaner(this));
        }
    }

    /**
     * Alternate constructor that can be used to provide a default layout
     * that will be inflated as part of <code>super.onCreate(savedInstanceState)</code>.
     *
     * <p>This should generally be called from your constructor that takes no parameters,
     * as is required for API 27 and lower or when using the default
     * {@link android.app.AppComponentFactory}.
     *
     * @see #ComponentActivity()
     */
    @ContentView
    public ComponentActivity(@LayoutRes int contentLayoutId) {
        this();
        mContentLayoutId = contentLayoutId;
    }

    /**
     * {@inheritDoc}
     *
     * If your ComponentActivity is annotated with {@link ContentView}, this will
     * call {@link #setContentView(int)} for you.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSavedStateRegistryController.performRestore(savedInstanceState);
        ReportFragment.injectIfNeededIn(this);
        if (mContentLayoutId != 0) {
            setContentView(mContentLayoutId);
        }
    }

    @CallSuper
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Lifecycle lifecycle = getLifecycle();
        if (lifecycle instanceof LifecycleRegistry) {
            ((LifecycleRegistry) lifecycle).markState(Lifecycle.State.CREATED);
        }
        super.onSaveInstanceState(outState);
        mSavedStateRegistryController.performSave(outState);
    }

    /**
     * Retain all appropriate non-config state.  You can NOT
     * override this yourself!  Use a {@link androidx.lifecycle.ViewModel} if you want to
     * retain your own non config state.
     */
    @Override
    @Nullable
    public final Object onRetainNonConfigurationInstance() {
        Object custom = onRetainCustomNonConfigurationInstance();

        ViewModelStore viewModelStore = mViewModelStore;
        if (viewModelStore == null) {
            // No one called getViewModelStore(), so see if there was an existing
            // ViewModelStore from our last NonConfigurationInstance
            NonConfigurationInstances nc =
                    (NonConfigurationInstances) getLastNonConfigurationInstance();
            if (nc != null) {
                viewModelStore = nc.viewModelStore;
            }
        }

        if (viewModelStore == null && custom == null) {
            return null;
        }

        NonConfigurationInstances nci = new NonConfigurationInstances();
        nci.custom = custom;
        nci.viewModelStore = viewModelStore;
        return nci;
    }

    /**
     * Use this instead of {@link #onRetainNonConfigurationInstance()}.
     * Retrieve later with {@link #getLastCustomNonConfigurationInstance()}.
     *
     * @deprecated Use a {@link androidx.lifecycle.ViewModel} to store non config state.
     */
    @Deprecated
    @Nullable
    public Object onRetainCustomNonConfigurationInstance() {
        return null;
    }

    /**
     * Return the value previously returned from
     * {@link #onRetainCustomNonConfigurationInstance()}.
     *
     * @deprecated Use a {@link androidx.lifecycle.ViewModel} to store non config state.
     */
    @Deprecated
    @Nullable
    public Object getLastCustomNonConfigurationInstance() {
        NonConfigurationInstances nc = (NonConfigurationInstances)
                getLastNonConfigurationInstance();
        return nc != null ? nc.custom : null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overriding this method is no longer supported and this method will be made
     * <code>final</code> in a future version of ComponentActivity. If you do override
     * this method, you <code>must</code>:
     * <ol>
     *     <li>Return an instance of {@link LifecycleRegistry}</li>
     *     <li>Lazily initialize your LifecycleRegistry object when this is first called.
     *     Note that this method will be called in the super classes' constructor, before any
     *     field initialization or object state creation is complete.</li>
     * </ol>
     */
    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    /**
     * Returns the {@link ViewModelStore} associated with this activity
     * <p>
     * Overriding this method is no longer supported and this method will be made
     * <code>final</code> in a future version of ComponentActivity.
     *
     * @return a {@code ViewModelStore}
     * @throws IllegalStateException if called before the Activity is attached to the Application
     * instance i.e., before onCreate()
     */
    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        if (getApplication() == null) {
            throw new IllegalStateException("Your activity is not yet attached to the "
                    + "Application instance. You can't request ViewModel before onCreate call.");
        }
        if (mViewModelStore == null) {
            NonConfigurationInstances nc =
                    (NonConfigurationInstances) getLastNonConfigurationInstance();
            if (nc != null) {
                // Restore the ViewModelStore from NonConfigurationInstances
                mViewModelStore = nc.viewModelStore;
            }
            if (mViewModelStore == null) {
                mViewModelStore = new ViewModelStore();
            }
        }
        return mViewModelStore;
    }

    /**
     * Called when the activity has detected the user's press of the back
     * key. The {@link #getOnBackPressedDispatcher() OnBackPressedDispatcher} will be given a
     * chance to handle the back button before the default behavior of
     * {@link android.app.Activity#onBackPressed()} is invoked.
     *
     * @see #getOnBackPressedDispatcher()
     */
    @Override
    public void onBackPressed() {
        if (mOnBackPressedDispatcher.hasEnabledCallbacks()) {
            mOnBackPressedDispatcher.onBackPressed();
            return;
        }
        // If the OnBackPressedDispatcher doesn't handle the back button,
        // delegate to the super implementation
        super.onBackPressed();
    }

    /**
     * Retrieve the {@link OnBackPressedDispatcher} that will be triggered when
     * {@link #onBackPressed()} is called.
     * @return The {@link OnBackPressedDispatcher} associated with this ComponentActivity.
     */
    @NonNull
    @Override
    public final OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return mOnBackPressedDispatcher;
    }

    /**
     * Add a new {@link OnBackPressedCallback}. Callbacks are invoked in order of recency, so
     * this newly added {@link OnBackPressedCallback} will be the first callback to receive a
     * callback if {@link #onBackPressed()} is called. Only if this callback returns
     * <code>false</code> from its {@link OnBackPressedCallback#handleOnBackPressed()} will any
     * previously added callback be called.
     * <p>
     * This is the equivalent of passing this {@link ComponentActivity} to
     * {@link #addOnBackPressedCallback(LifecycleOwner, OnBackPressedCallback)} and ensures that
     * the {@link OnBackPressedCallback#handleOnBackPressed()} callback will only be called
     * if this {@link ComponentActivity} is at least {@link Lifecycle.State#STARTED}. You can
     * remove the callback prior to the destruction of your activity by calling
     * {@link #removeOnBackPressedCallback(OnBackPressedCallback)}.
     *
     * @param onBackPressedCallback The callback to add
     *
     * @see #onBackPressed()
     * @see #removeOnBackPressedCallback(OnBackPressedCallback)
     * @deprecated Use {@link #getOnBackPressedDispatcher() and
     * {@link OnBackPressedDispatcher#addCallback(LifecycleOwner, OnBackPressedCallback)}},
     * explicitly passing in this Activity object as the {@link LifecycleOwner}.
     */
    @Deprecated
    public void addOnBackPressedCallback(@NonNull OnBackPressedCallback onBackPressedCallback) {
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    /**
     * Add a new {@link OnBackPressedCallback}. Callbacks are invoked in order of recency, so
     * this newly added {@link OnBackPressedCallback} will be the first callback to receive a
     * callback if {@link #onBackPressed()} is called. Only if this callback returns
     * <code>false</code> from its {@link OnBackPressedCallback#handleOnBackPressed()} will any
     * previously added callback be called.
     * <p>
     * The {@link OnBackPressedCallback#handleOnBackPressed()} callback will only be called if the
     * given {@link LifecycleOwner} is at least {@link Lifecycle.State#STARTED}. When the
     * {@link LifecycleOwner} is {@link Lifecycle.State#DESTROYED destroyed}, it will automatically
     * be removed from the list of callbacks. The only time you would need to manually call
     * {@link #removeOnBackPressedCallback(OnBackPressedCallback)} is if you'd like to remove the
     * callback prior to destruction of the associated lifecycle.
     *
     * @param owner The LifecycleOwner which controls when the callback should be invoked
     * @param onBackPressedCallback The callback to add
     *
     * @see #onBackPressed()
     * @see #removeOnBackPressedCallback(OnBackPressedCallback)
     * @deprecated Use {@link #getOnBackPressedDispatcher() and
     * {@link OnBackPressedDispatcher#addCallback(LifecycleOwner, OnBackPressedCallback)}}.
     */
    @Deprecated
    public void addOnBackPressedCallback(@NonNull LifecycleOwner owner,
            @NonNull OnBackPressedCallback onBackPressedCallback) {
        getOnBackPressedDispatcher().addCallback(owner, onBackPressedCallback);
    }

    /**
     * Remove a previously
     * {@link #addOnBackPressedCallback(LifecycleOwner, OnBackPressedCallback) added}
     * {@link OnBackPressedCallback} instance. The callback won't be called for any future
     * {@link #onBackPressed()} calls, but may still receive a callback if this method is called
     * during the dispatch of an ongoing {@link #onBackPressed()} call.
     * <p>
     * This call is usually not necessary as callbacks will be automatically removed when their
     * associated {@link LifecycleOwner} is {@link Lifecycle.State#DESTROYED destroyed}.
     *
     * @param onBackPressedCallback The callback to remove
     * @see #addOnBackPressedCallback(LifecycleOwner, OnBackPressedCallback)
     * @deprecated Use {@link OnBackPressedCallback#remove()}
     */
    @Deprecated
    public void removeOnBackPressedCallback(@NonNull OnBackPressedCallback onBackPressedCallback) {
        onBackPressedCallback.remove();
    }

    @NonNull
    @Override
    public final SavedStateRegistry getSavedStateRegistry() {
        return mSavedStateRegistryController.getSavedStateRegistry();
    }
}
