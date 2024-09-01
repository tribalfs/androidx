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

package androidx.preference;

import static android.view.View.LAYOUT_DIRECTION_RTL;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.LayoutDirection;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.XmlRes;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.appcompat.util.SeslSubheaderRoundedCorner;
import androidx.core.graphics.Insets;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * <p><b>SESL variant</b></p><br>
 *
 * A PreferenceFragmentCompat is the entry point to using the Preference library. This
 * {@link Fragment} displays a hierarchy of {@link Preference} objects to the user. It also
 * handles persisting values to the device. To retrieve an instance of
 * {@link android.content.SharedPreferences} that the preference hierarchy in this fragment will
 * use by default, call
 * {@link PreferenceManager#getDefaultSharedPreferences(android.content.Context)} with a context
 * in the same package as this fragment.
 *
 * <p>You can define a preference hierarchy as an XML resource, or you can build a hierarchy in
 * code. In both cases you need to use a {@link PreferenceScreen} as the root component in your
 * hierarchy.
 *
 * <p>To inflate from XML, use the {@link #setPreferencesFromResource(int, String)}. An example
 * example XML resource is shown further down.
 *
 * <p>To build a hierarchy from code, use
 * {@link PreferenceManager#createPreferenceScreen(Context)} to create the root
 * {@link PreferenceScreen}. Once you have added other {@link Preference}s to this root screen
 * with {@link PreferenceScreen#addPreference(Preference)}, you then need to set the screen as
 * the root screen in your hierarchy with {@link #setPreferenceScreen(PreferenceScreen)}.
 *
 * <p>As a convenience, this fragment implements a click listener for any preference in the
 * current hierarchy, see {@link #onPreferenceTreeClick(Preference)}.
 *
 * <div class="special reference"> <h3>Developer Guides</h3> <p>For more information about
 * building a settings screen using the AndroidX Preference library, see
 * <a href="{@docRoot}guide/topics/ui/settings.html">Settings</a>.</p> </div>
 *
 * <a name="SampleCode"></a>
 * <h3>Sample Code</h3>
 *
 * <p>The following sample code shows a simple settings screen using an XML resource. The XML
 * resource is as follows:</p>
 *
 * {@sample samples/SupportPreferenceDemos/src/main/res/xml/preferences.xml preferences}
 *
 * <p>The fragment that loads the XML resource is as follows:</p>
 *
 * {@sample samples/SupportPreferenceDemos/src/main/java/com/example/androidx/preference
 * /Preferences.java preferences}
 *
 * @see Preference
 * @see PreferenceScreen
 */
public abstract class PreferenceFragmentCompat extends Fragment implements
        PreferenceManager.OnPreferenceTreeClickListener,
        PreferenceManager.OnDisplayPreferenceDialogListener,
        PreferenceManager.OnNavigateToScreenListener,
        DialogPreference.TargetFragment {

    private static final String TAG = "PreferenceFragment";

    /**
     * Fragment argument used to specify the tag of the desired root {@link PreferenceScreen}
     * object.
     */
    public static final String ARG_PREFERENCE_ROOT =
            "androidx.preference.PreferenceFragmentCompat.PREFERENCE_ROOT";

    private static final String PREFERENCES_TAG = "android:preferences";

    private static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";

    private static final int MSG_BIND_PREFERENCES = 1;

    private final DividerDecoration mDividerDecoration = new DividerDecoration();
    private PreferenceManager mPreferenceManager;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    RecyclerView mList;
    private boolean mHavePrefs;
    private boolean mInitDone;
    private int mLayoutResId = R.layout.preference_list_fragment;
    private Runnable mSelectPreferenceRunnable;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BIND_PREFERENCES:
                    bindPreferences();
                    break;
            }
        }
    };

    final private Runnable mRequestFocus = new Runnable() {
        @Override
        public void run() {
            mList.focusableViewAvailable(mList);
        }
    };

    //Sesl
    protected int mIsLargeLayout;
    protected ViewTreeObserver.OnPreDrawListener mOnPreDrawListener;
    protected int mScreenWidthDp;
    protected boolean mIsRoundedCorner = true;
    protected SeslRoundedCorner mListRoundedCorner;
    protected SeslRoundedCorner mRoundedCorner;
    protected SeslSubheaderRoundedCorner mSubheaderRoundedCorner;
    private static final float FONT_SCALE_LARGE = 1.3f;
    private static final float FONT_SCALE_MEDIUM = 1.1f;
    private boolean mIsReducedMargin;
    private int mSubheaderColor;
    int mLeft = -1;
    int mTop = -1;
    int mRight = -1;
    int mBottom = -1;
    final boolean mSupportsInsets = Build.VERSION.SDK_INT > 29;//custom
    //sesl

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.preferenceTheme, tv , true);

        //Sesl
        Configuration configuration = getResources().getConfiguration();
        int screenWidthDp = configuration.screenWidthDp;
        mIsLargeLayout =
                ((screenWidthDp > 320 || configuration.fontScale < FONT_SCALE_MEDIUM) && (screenWidthDp >= 411 || configuration.fontScale < FONT_SCALE_LARGE)) ? 2 : 1;
        mScreenWidthDp = screenWidthDp;
        mIsReducedMargin = screenWidthDp <= 250;
        //sesl

        int theme  = tv .resourceId;
        if (theme  == 0) {
            // Fallback to default theme.
            theme  = R.style.PreferenceThemeOverlay;
        }
        requireContext().getTheme().applyStyle(theme , false);

        mPreferenceManager = new PreferenceManager(requireContext());
        mPreferenceManager.setOnNavigateToScreenListener(this);
        final Bundle args = getArguments();
        final String rootKey;
        if (args != null) {
            rootKey = getArguments().getString(ARG_PREFERENCE_ROOT);
        } else {
            rootKey = null;
        }
        onCreatePreferences(savedInstanceState, rootKey);
    }

    /**
     * Called during {@link #onCreate(Bundle)} to supply the preferences for this fragment.
     * Subclasses are expected to call {@link #setPreferenceScreen(PreferenceScreen)} either
     * directly or via helper methods such as {@link #addPreferencesFromResource(int)}.
     *
     * @param savedInstanceState If the fragment is being re-created from a previous saved state,
     *                           this is the state.
     * @param rootKey            If non-null, this preference fragment should be rooted at the
     *                           {@link PreferenceScreen} with this key.
     */
    public abstract void onCreatePreferences(@Nullable Bundle savedInstanceState,
            @Nullable String rootKey);

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        Context context = requireContext();

        TypedArray a = context.obtainStyledAttributes(null,
                R.styleable.PreferenceFragmentCompat, R.attr.preferenceFragmentCompatStyle, 0);

        mLayoutResId = a.getResourceId(R.styleable.PreferenceFragmentCompat_android_layout,
                mLayoutResId);

        final Drawable divider = a.getDrawable(
                R.styleable.PreferenceFragmentCompat_android_divider);
        final int dividerHeight = a.getDimensionPixelSize(
                R.styleable.PreferenceFragmentCompat_android_dividerHeight, -1);
        final boolean allowDividerAfterLastItem = a.getBoolean(
                R.styleable.PreferenceFragmentCompat_allowDividerAfterLastItem, true);

        a.recycle();

        //Sesl
        TypedArray ta = context.obtainStyledAttributes(null,
                androidx.appcompat.R.styleable.View,
                android.R.attr.listSeparatorTextViewStyle,
                0);
        Drawable background =
                ta.getDrawable(androidx.appcompat.R.styleable.View_android_background);
        if (background instanceof ColorDrawable) {
            mSubheaderColor = ((ColorDrawable) background).getColor();
        }
        ta.recycle();
        //sesl

        final LayoutInflater themedInflater = inflater.cloneInContext(requireContext());

        final View view = themedInflater.inflate(mLayoutResId, container, false);

        final View rawListContainer = view.findViewById(AndroidResources.ANDROID_R_LIST_CONTAINER);
        if (!(rawListContainer instanceof ViewGroup)) {
            throw new IllegalStateException("Content has view with id attribute "
                    + "'android.R.id.list_container' that is not a ViewGroup class");
        }

        final ViewGroup listContainer = (ViewGroup) rawListContainer;

        final RecyclerView listView = onCreateRecyclerView(themedInflater, listContainer,
                savedInstanceState);
        if (listView == null) {
            throw new RuntimeException("Could not create RecyclerView");
        }

        mList = listView;

        listView.addItemDecoration(mDividerDecoration);
        setDivider(divider);
        if (dividerHeight != -1) {
            setDividerHeight(dividerHeight);
        }
        mDividerDecoration.setAllowDividerAfterLastItem(allowDividerAfterLastItem);

        //Sesl
        mList.setItemAnimator(null);
        mRoundedCorner = new SeslRoundedCorner(context);
        mSubheaderRoundedCorner = new SeslSubheaderRoundedCorner(context);

        if (mIsRoundedCorner) {
            listView.seslSetFillBottomEnabled(true);
            listView.seslSetFillBottomColor(mSubheaderColor);
            mListRoundedCorner = new SeslRoundedCorner(context, true);
            mListRoundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                    | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
        }

        if (mOnPreDrawListener == null) {
            createOnPreDrawListener();
            listView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
        }

        mList.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View view) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View view) {
                view.getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
                view.removeOnAttachStateChangeListener(this);
                mOnPreDrawListener = null;
            }
        });
        //sesl

        // If mList isn't present in the view hierarchy, add it. mList is automatically inflated
        // on an Auto device so don't need to add it.
        if (mList.getParent() == null) {
            listContainer.addView(mList);
        }
        mHandler.post(mRequestFocus);

        //Sesl7
        final int defaultHorizontalPadding = getResources().getDimensionPixelSize(R.dimen.sesl_preference_padding_horizontal);
        if (mLeft < 0) mLeft = defaultHorizontalPadding;
        if (mRight < 0) mRight = defaultHorizontalPadding;
        if (mTop < 0) mTop = 0;
        if (mBottom < 0) mBottom = 0;
        setPadding(mLeft, mTop, mRight, mBottom);
        //sesl7

        return view;
    }


    /**
     * Sets the {@link Drawable} that will be drawn between each item in the list.
     *
     * <p><strong>Note:</strong> If the drawable does not have an intrinsic height, you should also
     * call {@link #setDividerHeight(int)}.
     *
     * @param divider The drawable to use
     * {@link android.R.attr#divider}
     */
    public void setDivider(@Nullable Drawable divider) {
        mDividerDecoration.setDivider(divider);
    }

    /**
     * Sets the height of the divider that will be drawn between each item in the list. Calling
     * this will override the intrinsic height as set by {@link #setDivider(Drawable)}.
     *
     * @param height The new height of the divider in pixels
     * {@link android.R.attr#dividerHeight}
     */
    public void setDividerHeight(int height) {
        mDividerDecoration.setDividerHeight(height);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                }
            }
        }

        if (mHavePrefs) {
            bindPreferences();
            if (mSelectPreferenceRunnable != null) {
                mSelectPreferenceRunnable.run();
                mSelectPreferenceRunnable = null;
            }
        }

        mInitDone = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        mPreferenceManager.setOnPreferenceTreeClickListener(this);
        mPreferenceManager.setOnDisplayPreferenceDialogListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mPreferenceManager.setOnPreferenceTreeClickListener(null);
        mPreferenceManager.setOnDisplayPreferenceDialogListener(null);
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mHandler.removeMessages(MSG_BIND_PREFERENCES);
        if (mHavePrefs) {
            unbindPreferences();
        }
        //Sesl
        if (mOnPreDrawListener != null && mList != null) {
            mList.getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
        }
        //sesl
        mList = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            Bundle container = new Bundle();
            preferenceScreen.saveHierarchyState(container);
            outState.putBundle(PREFERENCES_TAG, container);
        }
    }

    /**
     * Returns the {@link PreferenceManager} used by this fragment.
     *
     * @return The {@link PreferenceManager} used by this fragment
     */
    @SuppressLint("UnknownNullness")
    public PreferenceManager getPreferenceManager() {
        return mPreferenceManager;
    }

    /**
     * Gets the root of the preference hierarchy that this fragment is showing.
     *
     * @return The {@link PreferenceScreen} that is the root of the preference hierarchy
     */
    @SuppressLint("UnknownNullness")
    public PreferenceScreen getPreferenceScreen() {
        if (mPreferenceManager == null) {
            return null;
        }
        return mPreferenceManager.getPreferenceScreen();
    }

    /**
     * Sets the root of the preference hierarchy that this fragment is showing.
     *
     * @param preferenceScreen The root {@link PreferenceScreen} of the preference hierarchy
     */
    public void setPreferenceScreen(
            @SuppressLint("UnknownNullness") PreferenceScreen preferenceScreen) {
        if (preferenceScreen != null && mPreferenceManager.setPreferences(preferenceScreen)) {
            onUnbindPreferences();
            mHavePrefs = true;
            if (mInitDone) {
                postBindPreferences();
            }
        }
    }

    /**
     * Inflates the given XML resource and adds the preference hierarchy to the current
     * preference hierarchy.
     *
     * @param preferencesResId The XML resource ID to inflate
     */
    public void addPreferencesFromResource(@XmlRes int preferencesResId) {
        requirePreferenceManager();

        setPreferenceScreen(mPreferenceManager.inflateFromResource(requireContext(),
                preferencesResId, getPreferenceScreen()));
    }

    /**
     * Inflates the given XML resource and replaces the current preference hierarchy (if any) with
     * the preference hierarchy rooted at {@code key}.
     *
     * @param preferencesResId The XML resource ID to inflate
     * @param key              The preference key of the {@link PreferenceScreen} to use as the
     *                         root of the preference hierarchy, or {@code null} to use the root
     *                         {@link PreferenceScreen}.
     */
    public void setPreferencesFromResource(@XmlRes int preferencesResId, @Nullable String key) {
        requirePreferenceManager();

        final PreferenceScreen xmlRoot = mPreferenceManager.inflateFromResource(requireContext(),
                preferencesResId, null);

        final Preference root;
        if (key != null) {
            root = xmlRoot.findPreference(key);
            if (!(root instanceof PreferenceScreen)) {
                throw new IllegalArgumentException("Preference object with key " + key
                        + " is not a PreferenceScreen");
            }
        } else {
            root = xmlRoot;
        }

        setPreferenceScreen((PreferenceScreen) root);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        if (preference.getFragment() != null) {
            boolean handled = false;
            if (getCallbackFragment() instanceof OnPreferenceStartFragmentCallback) {
                handled = ((OnPreferenceStartFragmentCallback) getCallbackFragment())
                        .onPreferenceStartFragment(this, preference);
            }
            //  If the callback fragment doesn't handle OnPreferenceStartFragmentCallback, looks up
            //  its parent fragment in the hierarchy that implements the callback until the first
            //  one that returns true
            Fragment callbackFragment = this;
            while (!handled && callbackFragment != null) {
                if (callbackFragment instanceof OnPreferenceStartFragmentCallback) {
                    handled = ((OnPreferenceStartFragmentCallback) callbackFragment)
                            .onPreferenceStartFragment(this, preference);
                }
                callbackFragment = callbackFragment.getParentFragment();
            }
            if (!handled && getContext() instanceof OnPreferenceStartFragmentCallback) {
                handled = ((OnPreferenceStartFragmentCallback) getContext())
                        .onPreferenceStartFragment(this, preference);
            }
            // Check the Activity as well in case getContext was overridden to return something
            // other than the Activity.
            if (!handled && getActivity() instanceof OnPreferenceStartFragmentCallback) {
                handled = ((OnPreferenceStartFragmentCallback) getActivity())
                        .onPreferenceStartFragment(this, preference);
            }
            if (!handled) {
                Log.w(TAG,
                        "onPreferenceStartFragment is not implemented in the parent activity - "
                                + "attempting to use a fallback implementation. You should "
                                + "implement this method so that you can configure the new "
                                + "fragment that will be displayed, and set a transition between "
                                + "the fragments.");
                final FragmentManager fragmentManager = getParentFragmentManager();
                final Bundle args = preference.getExtras();
                final Fragment fragment = fragmentManager.getFragmentFactory().instantiate(
                        requireActivity().getClassLoader(), preference.getFragment());
                fragment.setArguments(args);
                fragment.setTargetFragment(this, 0);
                fragmentManager.beginTransaction()
                        // Attempt to replace this fragment in its root view - developers should
                        // implement onPreferenceStartFragment in their activity so that they can
                        // customize this behaviour and handle any transitions between fragments
                        .replace(((View) requireView().getParent()).getId(), fragment)
                        .addToBackStack(null)
                        .commit();
            }
            return true;
        }
        return false;
    }

    /**
     * Called by {@link PreferenceScreen#onClick()} in order to navigate to a new screen of
     * preferences. Calls
     * {@link PreferenceFragmentCompat.OnPreferenceStartScreenCallback#onPreferenceStartScreen}
     * if the target fragment or containing activity implements
     * {@link PreferenceFragmentCompat.OnPreferenceStartScreenCallback}.
     *
     * @param preferenceScreen The {@link PreferenceScreen} to navigate to
     */
    @Override
    public void onNavigateToScreen(@NonNull PreferenceScreen preferenceScreen) {
        boolean handled = false;
        if (getCallbackFragment() instanceof OnPreferenceStartScreenCallback) {
            handled = ((OnPreferenceStartScreenCallback) getCallbackFragment())
                    .onPreferenceStartScreen(this, preferenceScreen);
        }
        //  If the callback fragment doesn't handle OnPreferenceStartScreenCallback, looks up
        //  its parent fragment in the hierarchy that implements the callback until the first
        //  one that returns true
        Fragment callbackFragment = this;
        while (!handled && callbackFragment != null) {
            if (callbackFragment instanceof OnPreferenceStartScreenCallback) {
                handled = ((OnPreferenceStartScreenCallback) callbackFragment)
                        .onPreferenceStartScreen(this, preferenceScreen);
            }
            callbackFragment = callbackFragment.getParentFragment();
        }
        if (!handled && getContext() instanceof OnPreferenceStartScreenCallback) {
            handled = ((OnPreferenceStartScreenCallback) getContext())
                    .onPreferenceStartScreen(this, preferenceScreen);
        }
        // Check the Activity as well in case getContext was overridden to return something other
        // than the Activity.
        if (!handled && getActivity() instanceof OnPreferenceStartScreenCallback) {
            ((OnPreferenceStartScreenCallback) getActivity())
                    .onPreferenceStartScreen(this, preferenceScreen);
        }
    }

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals")
    @Nullable
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        if (mPreferenceManager == null) {
            return null;
        }
        return mPreferenceManager.findPreference(key);
    }

    private void requirePreferenceManager() {
        if (mPreferenceManager == null) {
            throw new RuntimeException("This should be called after super.onCreate.");
        }
    }

    private void postBindPreferences() {
        if (mHandler.hasMessages(MSG_BIND_PREFERENCES)) return;
        mHandler.obtainMessage(MSG_BIND_PREFERENCES).sendToTarget();
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            getListView().setAdapter(onCreateAdapter(preferenceScreen));
            preferenceScreen.onAttached();
        }
        onBindPreferences();
    }

    private void unbindPreferences() {
        getListView().setAdapter(null);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.onDetached();
        }
        onUnbindPreferences();
    }

    /**
     * Used by Settings.
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    protected void onBindPreferences() {}

    /**
     * Used by Settings.
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    protected void onUnbindPreferences() {}

    @SuppressLint("UnknownNullness")
    public final RecyclerView getListView() {
        return mList;
    }

    /**
     * Creates the {@link RecyclerView} used to display the preferences.
     * Subclasses may override this to return a customized {@link RecyclerView}.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate the
     *                           {@link RecyclerView}.
     * @param parent             The parent {@link ViewGroup} that the RecyclerView will be attached
     *                           to. This method should not add the view itself, but this can be
     *                           used to generate the layout params of the view.
     * @param savedInstanceState If non-null, this view is being re-constructed from a previous
     *                           saved state as given here.
     * @return A new {@link RecyclerView} object to be placed into the view hierarchy
     */
    @SuppressWarnings("deprecation")
    @NonNull
    public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater,
            @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
        // If device detected is Auto, use Auto's custom layout that contains a custom ViewGroup
        // wrapping a RecyclerView
        if (requireContext().getPackageManager().hasSystemFeature(PackageManager
                .FEATURE_AUTOMOTIVE)) {
            RecyclerView recyclerView = parent.findViewById(R.id.recycler_view);
            if (recyclerView != null) {
                return recyclerView;
            }
        }
        RecyclerView recyclerView = (RecyclerView) inflater
                .inflate(R.layout.sesl_preference_recyclerview, parent, false);//sesl

        recyclerView.setLayoutManager(onCreateLayoutManager());
        recyclerView.setAccessibilityDelegateCompat(
                new PreferenceRecyclerViewAccessibilityDelegate(recyclerView));

        return recyclerView;
    }

    /**
     * Called from {@link #onCreateRecyclerView} to create the {@link RecyclerView.LayoutManager}
     * for the created {@link RecyclerView}.
     *
     * @return A new {@link RecyclerView.LayoutManager} instance
     */
    @NonNull
    public RecyclerView.LayoutManager onCreateLayoutManager() {
        return new LinearLayoutManager(requireContext());
    }

    /**
     * Creates the root adapter.
     *
     * @param preferenceScreen The {@link PreferenceScreen} object to create the adapter for
     * @return An adapter that contains the preferences contained in this {@link PreferenceScreen}
     */
    @NonNull
    protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new PreferenceGroupAdapter(preferenceScreen);
    }


    /**
     * Called when a preference in the tree requests to display a dialog. Subclasses should
     * override this method to display custom dialogs or to handle dialogs for custom preference
     * classes.
     *
     * @param preference The {@link Preference} object requesting the dialog
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {

        boolean handled = false;
        if (getCallbackFragment() instanceof OnPreferenceDisplayDialogCallback) {
            handled = ((OnPreferenceDisplayDialogCallback) getCallbackFragment())
                    .onPreferenceDisplayDialog(this, preference);
        }
        //  If the callback fragment doesn't handle OnPreferenceDisplayDialogCallback, looks up
        //  its parent fragment in the hierarchy that implements the callback until the first
        //  one that returns true
        Fragment callbackFragment = this;
        while (!handled && callbackFragment != null) {
            if (callbackFragment instanceof OnPreferenceDisplayDialogCallback) {
                handled = ((OnPreferenceDisplayDialogCallback) callbackFragment)
                        .onPreferenceDisplayDialog(this, preference);
            }
            callbackFragment = callbackFragment.getParentFragment();
        }
        if (!handled && getContext() instanceof OnPreferenceDisplayDialogCallback) {
            handled = ((OnPreferenceDisplayDialogCallback) getContext())
                    .onPreferenceDisplayDialog(this, preference);
        }
        // Check the Activity as well in case getContext was overridden to return something other
        // than the Activity.
        if (!handled && getActivity() instanceof OnPreferenceDisplayDialogCallback) {
            handled = ((OnPreferenceDisplayDialogCallback) getActivity())
                    .onPreferenceDisplayDialog(this, preference);
        }

        if (handled) {
            return;
        }

        // check if dialog is already showing
        if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f;
        if (preference instanceof EditTextPreference) {
            f = EditTextPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        } else if (preference instanceof ListPreference) {
            f = ListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        } else if (preference instanceof MultiSelectListPreference) {
            f = MultiSelectListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        } else {
            throw new IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                            + preference.getClass().getSimpleName()
                            + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                            + "displaying a custom dialog for this Preference.");
        }
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
    }

    /**
     * A wrapper for getParentFragment which is v17+. Used by the leanback preference lib.
     *
     * @return The {@link Fragment} to possibly use as a callback
     */
    @Nullable
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public Fragment getCallbackFragment() {
        return null;
    }

    public void scrollToPreference(@NonNull String key) {
        scrollToPreferenceInternal(null, key);
    }

    public void scrollToPreference(@NonNull Preference preference) {
        scrollToPreferenceInternal(preference, null);
    }

    private void scrollToPreferenceInternal(@Nullable final Preference preference,
            @Nullable final String key) {
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                final RecyclerView.Adapter<?> adapter = mList.getAdapter();
                if (!(adapter instanceof
                        PreferenceGroup.PreferencePositionCallback)) {
                    if (adapter != null) {
                        throw new IllegalStateException("Adapter must implement "
                                + "PreferencePositionCallback");
                    } else {
                        // Adapter was set to null, so don't scroll
                        return;
                    }
                }
                final int position;
                if (preference != null) {
                    position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                            .getPreferenceAdapterPosition(preference);
                } else {
                    position = ((PreferenceGroup.PreferencePositionCallback) adapter)
                            .getPreferenceAdapterPosition(key);
                }
                if (position != RecyclerView.NO_POSITION) {
                    mList.scrollToPosition(position);
                } else {
                    // Item not found, wait for an update and try again
                    adapter.registerAdapterDataObserver(
                            new ScrollToPreferenceObserver(adapter, mList, preference, key));
                }
            }
        };
        if (mList == null) {
            mSelectPreferenceRunnable = r;
        } else {
            r.run();
        }
    }

    /**
     * Interface that the fragment's containing activity should implement to be able to process
     * preference items that wish to switch to a specified fragment.
     */
    public interface OnPreferenceStartFragmentCallback {
        /**
         * Called when the user has clicked on a preference that has a fragment class name
         * associated with it. The implementation should instantiate and switch to an instance
         * of the given fragment.
         *
         * @param caller The fragment requesting navigation
         * @param pref   The preference requesting the fragment
         * @return {@code true} if the fragment creation has been handled
         */
        boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                @NonNull Preference pref);
    }

    /**
     * Interface that the fragment's containing activity should implement to be able to process
     * preference items that wish to switch to a new screen of preferences.
     */
    public interface OnPreferenceStartScreenCallback {
        /**
         * Called when the user has clicked on a {@link PreferenceScreen} in order to navigate to
         * a new screen of preferences.
         *
         * @param caller The fragment requesting navigation
         * @param pref   The preference screen to navigate to
         * @return {@code true} if the screen navigation has been handled
         */
        boolean onPreferenceStartScreen(@NonNull PreferenceFragmentCompat caller,
                @NonNull PreferenceScreen pref);
    }

    /**
     * Interface that the fragment's containing activity should implement to be able to process
     * preference items that wish to display a dialog.
     */
    public interface OnPreferenceDisplayDialogCallback {
        /**
         * @param caller The fragment containing the preference requesting the dialog
         * @param pref   The preference requesting the dialog
         * @return {@code true} if the dialog creation has been handled
         */
        boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentCompat caller,
                @NonNull Preference pref);
    }

    private static class ScrollToPreferenceObserver extends RecyclerView.AdapterDataObserver {
        private final RecyclerView.Adapter<?> mAdapter;
        private final RecyclerView mList;
        private final Preference mPreference;
        private final String mKey;

        ScrollToPreferenceObserver(RecyclerView.Adapter<?> adapter, RecyclerView list,
                Preference preference, String key) {
            mAdapter = adapter;
            mList = list;
            mPreference = preference;
            mKey = key;
        }

        private void scrollToPreference() {
            mAdapter.unregisterAdapterDataObserver(this);
            final int position;
            if (mPreference != null) {
                position = ((PreferenceGroup.PreferencePositionCallback) mAdapter)
                        .getPreferenceAdapterPosition(mPreference);
            } else {
                position = ((PreferenceGroup.PreferencePositionCallback) mAdapter)
                        .getPreferenceAdapterPosition(mKey);
            }
            if (position != RecyclerView.NO_POSITION) {
                mList.scrollToPosition(position);
            }
        }

        @Override
        public void onChanged() {
            scrollToPreference();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            scrollToPreference();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            scrollToPreference();
        }
    }

    private class DividerDecoration extends RecyclerView.ItemDecoration {

        private Drawable mDivider;
        private int mDividerHeight;
        private boolean mAllowDividerAfterLastItem = true;

        DividerDecoration() {}

//        @Override
//        public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
//                @NonNull RecyclerView.State state) {
//              if (mDivider == null) {
//                return;
//            }
//            final int childCount = parent.getChildCount();
//            final int width = parent.getWidth();
//            for (int childViewIndex = 0; childViewIndex < childCount; childViewIndex++) {
//                final View view = parent.getChildAt(childViewIndex);
//                if (shouldDrawDividerBelow(view, parent)) {
//                    int top = (int) view.getY() + view.getHeight();
//                    mDivider.setBounds(0, top, width, top + mDividerHeight);
//                    mDivider.draw(c);
//                }
//            }
//        }
//
//        @Override
//        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
//                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
//            if (shouldDrawDividerBelow(view, parent)) {
//                outRect.bottom = mDividerHeight;
//            }
//        }

        //Sesl
        @Override
        public void seslOnDispatchDraw(@NonNull Canvas c, @NonNull RecyclerView parent, RecyclerView.State state) {
            super.seslOnDispatchDraw(c, parent, state);

            int childCount = parent.getChildCount();
            int start = parent.getPaddingLeft() + parent.getLeft();
            int end = parent.getRight() - parent.getPaddingRight();

            PreferenceViewHolder preferenceHolder;
            int dividerLeftOffset;

            boolean isLayoutRtl =
                    getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < childCount; i++) {
                View view = parent.getChildAt(i);
                RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);

                if (holder instanceof PreferenceViewHolder) {
                    preferenceHolder = (PreferenceViewHolder) holder;
                    dividerLeftOffset = preferenceHolder.seslGetDividerLeftOffset();
                } else {
                    preferenceHolder = null;
                    dividerLeftOffset = 0;
                }

                int top = ((int) view.getY()) + view.getHeight();
                if (mDivider != null && shouldDrawDividerBelow(view, parent)) {
                    if (isLayoutRtl) {
                        mDivider.setBounds(start, top, end - dividerLeftOffset, mDividerHeight + top);
                    } else {
                        mDivider.setBounds(start + dividerLeftOffset, top, end, mDividerHeight + top);
                    }
                    mDivider.draw(c);
                }

                if (mIsRoundedCorner) {
                    if (preferenceHolder != null && preferenceHolder.isBackgroundDrawn()) {
                        if (preferenceHolder.isDrawSubheaderRound()) {
                            mSubheaderRoundedCorner.setRoundedCorners(preferenceHolder.getDrawCorners());
                            mSubheaderRoundedCorner.drawRoundedCorner(view, c);
                        } else {
                            mRoundedCorner.setRoundedCorners(preferenceHolder.getDrawCorners());
                            mRoundedCorner.drawRoundedCorner(view, c);
                        }
                    }
                }
            }

            if (mIsRoundedCorner) {
                mListRoundedCorner.drawRoundedCorner(c,
                        mSupportsInsets ? Insets.of(mLeft, mTop, mRight, mBottom) : null);
            }
        }
        //sesl

        private boolean shouldDrawDividerBelow(View view, RecyclerView parent) {
            final RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
            final boolean dividerAllowedBelow = holder instanceof PreferenceViewHolder
                    && ((PreferenceViewHolder) holder).isDividerAllowedBelow();
            if (!dividerAllowedBelow) {
                return false;
            }
            boolean nextAllowed = mAllowDividerAfterLastItem;
            int index = parent.indexOfChild(view);
            if (index < parent.getChildCount() - 1) {
                final View nextView = parent.getChildAt(index + 1);
                final RecyclerView.ViewHolder nextHolder = parent.getChildViewHolder(nextView);
                nextAllowed = nextHolder instanceof PreferenceViewHolder
                        && ((PreferenceViewHolder) nextHolder).isDividerAllowedAbove();
            }
            return nextAllowed;
        }

        public void setDivider(Drawable divider) {
            if (divider != null) {
                mDividerHeight = divider.getIntrinsicHeight();
            } else {
                mDividerHeight = 0;
            }
            mDivider = divider;
            mList.invalidateItemDecorations();
        }

        public void setDividerHeight(int dividerHeight) {
            mDividerHeight = dividerHeight;
            mList.invalidateItemDecorations();
        }

        public void setAllowDividerAfterLastItem(boolean allowDividerAfterLastItem) {
            mAllowDividerAfterLastItem = allowDividerAfterLastItem;
        }
    }

    //Sesl
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        RecyclerView listView = getListView();
        if (listView != null) {
            if (mOnPreDrawListener == null) {
                createOnPreDrawListener();
                listView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
            }
            RecyclerView.Adapter<?> adapter = listView.getAdapter();
            RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
            boolean isSmallScreenWidth = newConfig.screenWidthDp <= 250;
            if (isSmallScreenWidth != mIsReducedMargin && (adapter instanceof PreferenceGroupAdapter) && layoutManager != null) {
                mIsReducedMargin = isSmallScreenWidth;
                TypedArray obtainStyledAttributes = getContext().obtainStyledAttributes(null,
                        R.styleable.PreferenceFragmentCompat,
                        R.attr.preferenceFragmentCompatStyle, 0);
                try {
                    setDivider(obtainStyledAttributes.getDrawable(R.styleable.PreferenceFragment_android_divider));
                    Parcelable onSaveInstanceState = layoutManager.onSaveInstanceState();
                    listView.setAdapter(listView.getAdapter());
                    layoutManager.onRestoreInstanceState(onSaveInstanceState);
                } finally {
                    obtainStyledAttributes.recycle();
                }
            }
        }
        super.onConfigurationChanged(newConfig);
    }

    private void createOnPreDrawListener() {
        if (mList != null) {
            mOnPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    RecyclerView recyclerView = mList;
                    if (recyclerView != null) {
                        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                        Configuration configuration = getResources().getConfiguration();
                        int swDp = configuration.screenWidthDp;
                        int isLargeLayout =
                                ((swDp > 320 || configuration.fontScale < FONT_SCALE_MEDIUM)
                                        && (swDp >= 411 || configuration.fontScale < FONT_SCALE_LARGE)) ? 2 : 1;
                        if (adapter instanceof PreferenceGroupAdapter pgAdapter) {
                            if (needToRefeshSwitch(pgAdapter, isLargeLayout, swDp)) {
                                mIsLargeLayout = isLargeLayout;
                                for (int i = 0; i < pgAdapter.getItemCount(); i++) {
                                    Preference item = pgAdapter.getItem(i);
                                    if (item != null && pgAdapter.isSwitchLayout(item) && (item instanceof SwitchPreferenceCompat)) {
                                        adapter.notifyItemChanged(i);
                                    }
                                }
                            }
                        }
                        mScreenWidthDp = swDp;
                        recyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                        mOnPreDrawListener = null;
                    }
                    return false;
                }
            };
        }
    }

    public boolean needToRefeshSwitch(@NonNull PreferenceGroupAdapter preferenceGroupAdapter,
            int isLargeLayout, int swDp) {
        if (isLargeLayout == mIsLargeLayout) {
            return isLargeLayout == 1 && (mScreenWidthDp != swDp || preferenceGroupAdapter.getListWidth() == 0);
        }
        return true;
    }

    public void seslSetRoundedCorner(boolean enabled) {
        mIsRoundedCorner = enabled;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        mLeft = left;
        mTop = top;
        mRight = right;
        mBottom = bottom;
        updatePadding();
    }

    private void updatePadding() {
        RecyclerView list = mList;
        if (list != null) {
            list.setPadding(mLeft, mTop, mRight, mBottom);
            boolean fillHorizontal = mLeft != 0 || mRight != 0 || mTop != 0 || mBottom != 0;
            list.seslSetFillHorizontalPaddingEnabled(fillHorizontal);
            list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        }
    }
    //sesl
}
