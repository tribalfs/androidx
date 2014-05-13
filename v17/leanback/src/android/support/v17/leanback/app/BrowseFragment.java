/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.support.v17.leanback.app;

import android.support.v17.leanback.R;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemSelectedListener;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.SearchOrbView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.app.Fragment;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

/**
 * Wrapper fragment for leanback browse screens. Composed of a
 * RowsFragment and a HeadersFragment.
 *
 */
public class BrowseFragment extends Fragment {
    private static final String TAG = "BrowseFragment";
    private static boolean DEBUG = false;

    /** The fastlane navigation panel is enabled and shown by default. */
    public static final int HEADERS_ENABLED = 1;

    /** The fastlane navigation panel is enabled and hidden by default. */
    public static final int HEADERS_HIDDEN = 2;

    /** The fastlane navigation panel is disabled and will never be shown. */
    public static final int HEADERS_DISABLED = 3;

    private static final float SLIDE_DISTANCE_FACTOR = 2;

    private RowsFragment mRowsFragment;
    private HeadersFragment mHeadersFragment;

    private ObjectAdapter mAdapter;

    private Params mParams;
    private int mBrandColor = Color.TRANSPARENT;
    private boolean mBrandColorSet;

    private BrowseFrameLayout mBrowseFrame;
    private ImageView mBadgeView;
    private TextView mTitleView;
    private ViewGroup mBrowseTitle;
    private SearchOrbView mSearchOrbView;
    private boolean mShowingTitle = true;
    private boolean mShowingHeaders = true;
    private boolean mCanShowHeaders = true;
    private int mContainerListMarginLeft;
    private int mContainerListAlignTop;
    private int mSearchAffordanceColor;
    private boolean mSearchAffordanceColorSet;
    private OnItemSelectedListener mExternalOnItemSelectedListener;
    private OnClickListener mExternalOnSearchClickedListener;
    private OnItemClickedListener mOnItemClickedListener;
    private int mSelectedPosition = -1;

    private PresenterSelector mHeaderPresenterSelector;

    // transition related:
    private static TransitionHelper sTransitionHelper = TransitionHelper.getInstance();
    private int mReparentHeaderId = View.generateViewId();
    private Object mSceneWithTitle;
    private Object mSceneWithoutTitle;
    private Object mSceneWithHeaders;
    private Object mSceneWithoutHeaders;
    private Object mTitleTransition;
    private Object mHeadersTransition;
    private int mHeadersTransitionStartDelay;
    private int mHeadersTransitionDuration;

    private static final String ARG_TITLE = BrowseFragment.class.getCanonicalName() + ".title";
    private static final String ARG_BADGE_URI = BrowseFragment.class.getCanonicalName() + ".badge";
    private static final String ARG_HEADERS_STATE =
        BrowseFragment.class.getCanonicalName() + ".headersState";

    /**
     * @param args Bundle to use for the arguments, if null a new Bundle will be created.
     */
    public static Bundle createArgs(Bundle args, String title, String badgeUri) {
        return createArgs(args, title, badgeUri, HEADERS_ENABLED);
    }

    public static Bundle createArgs(Bundle args, String title, String badgeUri, int headersState) {
        if (args == null) {
            args = new Bundle();
        }
        args.putString(ARG_TITLE, title);
        args.putString(ARG_BADGE_URI, badgeUri);
        args.putInt(ARG_HEADERS_STATE, headersState);
        return args;
    }

    public static class Params {
        private String mTitle;
        private Drawable mBadgeDrawable;
        private int mHeadersState;

        /**
         * Sets the badge image.
         */
        public void setBadgeImage(Drawable drawable) {
            mBadgeDrawable = drawable;
        }

        /**
         * Returns the badge image.
         */
        public Drawable getBadgeImage() {
            return mBadgeDrawable;
        }

        /**
         * Sets a title for the browse fragment.
         */
        public void setTitle(String title) {
            mTitle = title;
        }

        /**
         * Returns the title for the browse fragment.
         */
        public String getTitle() {
            return mTitle;
        }

        /**
         * Sets the state for the headers column in the browse fragment.
         */
        public void setHeadersState(int headersState) {
            if (headersState < HEADERS_ENABLED || headersState > HEADERS_DISABLED) {
                Log.e(TAG, "Invalid headers state: " + headersState
                        + ", default to enabled and shown.");
                mHeadersState = HEADERS_ENABLED;
            } else {
                mHeadersState = headersState;
            }
        }

        /**
         * Returns the state for the headers column in the browse fragment.
         */
        public int getHeadersState() {
            return mHeadersState;
        }
    }

    /**
     * Set browse parameters.
     */
    public void setBrowseParams(Params params) {
        mParams = params;
        setBadgeDrawable(mParams.mBadgeDrawable);
        setTitle(mParams.mTitle);
        setHeadersState(mParams.mHeadersState);
    }

    /**
     * Returns browse parameters.
     */
    public Params getBrowseParams() {
        return mParams;
    }

    /**
     * Sets the brand color for the browse fragment.
     */
    public void setBrandColor(int color) {
        mBrandColor = color;
        mBrandColorSet = true;

        if (mHeadersFragment != null) {
            mHeadersFragment.setBackgroundColor(mBrandColor);
        }
    }

    /**
     * Returns the brand color for the browse fragment.
     * The default is transparent.
     */
    public int getBrandColor() {
        return mBrandColor;
    }

    /**
     * Sets the list of rows for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        if (mRowsFragment != null) {
            mRowsFragment.setAdapter(adapter);
            mHeadersFragment.setAdapter(adapter);
        }
    }

    /**
     * Returns the list of rows.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mExternalOnItemSelectedListener = listener;
    }

    /**
     * Sets an item clicked listener on the fragment.
     * OnItemClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general,  developer should choose one of the listeners but not both.
     */
    public void setOnItemClickedListener(OnItemClickedListener listener) {
        mOnItemClickedListener = listener;
        if (mRowsFragment != null) {
            mRowsFragment.setOnItemClickedListener(listener);
        }
    }

    /**
     * Returns the item Clicked listener.
     */
    public OnItemClickedListener getOnItemClickedListener() {
        return mOnItemClickedListener;
    }

    /**
     * Sets a click listener for the search affordance.
     *
     * The presence of a listener will change the visibility of the search affordance in the
     * title area. When set to non-null the title area will contain a call to search action.
     *
     * The listener onClick method will be invoked when the user click on the search action.
     *
     * @param listener The listener.
     */
    public void setOnSearchClickedListener(View.OnClickListener listener) {
        mExternalOnSearchClickedListener = listener;
        if (mSearchOrbView != null) {
            mSearchOrbView.setOnOrbClickedListener(listener);
        }
    }

    /**
     * Sets the color used to draw the search affordance.
     */
    public void setSearchAffordanceColor(int color) {
        mSearchAffordanceColor = color;
        mSearchAffordanceColorSet = true;

        if (mSearchOrbView != null) {
            mSearchOrbView.setOrbColor(mSearchAffordanceColor);
        }
    }

    /**
     * Returns the color used to draw the search affordance.
     * Can be called only after an activity has been attached.
     */
    public int getSearchAffordanceColor() {
        if (getActivity() == null) {
            throw new IllegalStateException("Activity must be attached");
        }

        if (mSearchAffordanceColorSet) {
            return mSearchAffordanceColor;
        }

        TypedValue outValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(android.R.attr.colorForeground, outValue, true);
        return getResources().getColor(outValue.resourceId);
    }

    private void onHeadersTransitionStart(boolean withHeaders) {
        mRowsFragment.onTransitionStart();
        mHeadersFragment.onTransitionStart();
        createHeadersTransition(withHeaders);
    }

    private boolean isVerticalScrolling() {
        // don't run transition
        return mHeadersFragment.getVerticalGridView().getScrollState()
                != HorizontalGridView.SCROLL_STATE_IDLE
                || mRowsFragment.getVerticalGridView().getScrollState()
                != HorizontalGridView.SCROLL_STATE_IDLE;
    }

    private final BrowseFrameLayout.OnFocusSearchListener mOnFocusSearchListener =
            new BrowseFrameLayout.OnFocusSearchListener() {
        @Override
        public View onFocusSearch(View focused, int direction) {
            // If fastlane is disabled, just return null.
            if (!mCanShowHeaders) return null;

            // if fast lane is running transition,  focus stays
            if (mHeadersTransition != null) return focused;
            if (DEBUG) Log.v(TAG, "onFocusSearch focused " + focused + " + direction " + direction);
            if (direction == View.FOCUS_LEFT) {
                if (isVerticalScrolling() || mShowingHeaders) {
                    return focused;
                }
                return mHeadersFragment.getVerticalGridView();
            } else if (direction == View.FOCUS_RIGHT) {
                if (isVerticalScrolling() || !mShowingHeaders) {
                    return focused;
                }
                return mRowsFragment.getVerticalGridView();
            } else if (focused == mSearchOrbView && direction == View.FOCUS_DOWN) {
                return mShowingHeaders ? mHeadersFragment.getVerticalGridView() :
                    mRowsFragment.getVerticalGridView();

            } else if (focused != mSearchOrbView && mSearchOrbView.getVisibility() == View.VISIBLE
                    && direction == View.FOCUS_UP) {
                return mSearchOrbView;

            } else {
                return null;
            }
        }
    };

    private final BrowseFrameLayout.OnChildFocusListener mOnChildFocusListener =
            new BrowseFrameLayout.OnChildFocusListener() {
        @Override
        public void onRequestChildFocus(View child, View focused) {
            int childId = child.getId();
            if (mHeadersTransition != null) return;
            if (childId == R.id.browse_container_dock && mShowingHeaders) {
                mShowingHeaders = false;
                onHeadersTransitionStart(false);
                sTransitionHelper.runTransition(mSceneWithoutHeaders, mHeadersTransition);
            } else if (childId == R.id.browse_headers_dock && !mShowingHeaders) {
                mShowingHeaders = true;
                //mHeadersFragment.getView().setAlpha(1f);
                onHeadersTransitionStart(true);
                sTransitionHelper.runTransition(mSceneWithHeaders, mHeadersTransition);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.LeanbackTheme);
        mContainerListMarginLeft = (int) ta.getDimension(
                R.styleable.LeanbackTheme_browseRowsMarginStart, 0);
        mContainerListAlignTop = (int) ta.getDimension(
                R.styleable.LeanbackTheme_browseRowsMarginTop, 0);
        ta.recycle();

        mHeadersTransitionStartDelay = getResources()
                .getInteger(R.integer.lb_browse_headers_transition_delay);
        mHeadersTransitionDuration = getResources()
                .getInteger(R.integer.lb_browse_headers_transition_duration);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (getChildFragmentManager().findFragmentById(R.id.browse_container_dock) == null) {
            mRowsFragment = new RowsFragment();
            mHeadersFragment = new HeadersFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.browse_headers_dock, mHeadersFragment)
                    .replace(R.id.browse_container_dock, mRowsFragment).commit();
        } else {
            mHeadersFragment = (HeadersFragment) getChildFragmentManager()
                    .findFragmentById(R.id.browse_headers_dock);
            mRowsFragment = (RowsFragment) getChildFragmentManager()
                    .findFragmentById(R.id.browse_container_dock);
        }
        mRowsFragment.setReparentHeaderId(mReparentHeaderId);
        mRowsFragment.setAdapter(mAdapter);
        if (mHeaderPresenterSelector != null) {
            mHeadersFragment.setPresenterSelector(mHeaderPresenterSelector);
        }
        mHeadersFragment.setReparentHeaderId(mReparentHeaderId);
        mHeadersFragment.setAdapter(mAdapter);

        mRowsFragment.setOnItemSelectedListener(mRowSelectedListener);
        mHeadersFragment.setOnItemSelectedListener(mHeaderSelectedListener);
        mHeadersFragment.setOnHeaderClickedListener(mHeaderClickedListener);
        mRowsFragment.setOnItemClickedListener(mOnItemClickedListener);

        View root = inflater.inflate(R.layout.lb_browse_fragment, container, false);

        mBrowseFrame = (BrowseFrameLayout) root.findViewById(R.id.browse_frame);
        mBrowseFrame.setOnFocusSearchListener(mOnFocusSearchListener);
        mBrowseFrame.setOnChildFocusListener(mOnChildFocusListener);

        mBrowseTitle = (ViewGroup) root.findViewById(R.id.browse_title_group);
        mBadgeView = (ImageView) mBrowseTitle.findViewById(R.id.browse_badge);
        mTitleView = (TextView) mBrowseTitle.findViewById(R.id.browse_title);
        mSearchOrbView = (SearchOrbView) mBrowseTitle.findViewById(R.id.browse_orb);
        mSearchOrbView.setOrbColor(getSearchAffordanceColor());
        if (mExternalOnSearchClickedListener != null) {
            mSearchOrbView.setOnOrbClickedListener(mExternalOnSearchClickedListener);
        }

        readArguments(getArguments());
        if (mParams != null) {
            setBadgeDrawable(mParams.mBadgeDrawable);
            setTitle(mParams.mTitle);
            setHeadersState(mParams.mHeadersState);
            if (mBrandColorSet) {
                mHeadersFragment.setBackgroundColor(mBrandColor);
            }
        }

        mSceneWithTitle = sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showTitle(true);
            }
        });
        mSceneWithoutTitle = sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showTitle(false);
            }
        });
        mSceneWithHeaders = sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showHeaders(true);
            }
        });
        mSceneWithoutHeaders =  sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showHeaders(false);
            }
        });
        mTitleTransition = sTransitionHelper.createAutoTransition();
        sTransitionHelper.excludeChildren(mTitleTransition, R.id.browse_headers, true);
        sTransitionHelper.excludeChildren(mTitleTransition, R.id.container_list, true);

        return root;
    }

    private void createHeadersTransition(boolean withHeaders) {
        mHeadersTransition = sTransitionHelper.createTransitionSet(false);
        sTransitionHelper.excludeChildren(mHeadersTransition, R.id.browse_title_group, true);
        Object changeBounds = sTransitionHelper.createChangeBounds(true);
        Object fadeIn = sTransitionHelper.createFadeTransition(TransitionHelper.FADE_IN);
        Object fadeOut = sTransitionHelper.createFadeTransition(TransitionHelper.FADE_OUT);

        sTransitionHelper.exclude(fadeIn, mReparentHeaderId, true);
        sTransitionHelper.exclude(fadeOut, mReparentHeaderId, true);
        if (!withHeaders) {
            sTransitionHelper.setChangeBoundsDefaultStartDelay(changeBounds,
                    mHeadersTransitionStartDelay);
        }
        sTransitionHelper.setChangeBoundsStartDelay(changeBounds, mReparentHeaderId,
                withHeaders ? mHeadersTransitionStartDelay : 0);

        final int selectedPosition = mSelectedPosition;
        Object slide = sTransitionHelper.createSlide(new SlideCallback() {
            @Override
            public boolean getSlide(View view, boolean appear, int[] edge, float[] distance) {
                // we only care about the view with specific transition position Tag.
                Integer position = (Integer) view.getTag(R.id.lb_header_transition_position);
                if (position == null) {
                    return false;
                }
                distance[0] = view.getHeight() * SLIDE_DISTANCE_FACTOR;
                if (position < selectedPosition) {
                    edge[0] = TransitionHelper.SLIDE_TOP;
                    return true;
                } else if (position > selectedPosition) {
                    edge[0] = TransitionHelper.SLIDE_BOTTOM;
                    return true;
                }
                return false;
            }
        });
        sTransitionHelper.exclude(slide, mReparentHeaderId, true);
        sTransitionHelper.setDuration(slide, mHeadersTransitionDuration);
        if (withHeaders) {
            sTransitionHelper.setStartDelay(slide, mHeadersTransitionStartDelay);
        }
        sTransitionHelper.addTransition(mHeadersTransition, slide);

        sTransitionHelper.setDuration(fadeOut, mHeadersTransitionDuration);
        sTransitionHelper.addTransition(mHeadersTransition, fadeOut);
        sTransitionHelper.setDuration(changeBounds, mHeadersTransitionDuration);
        sTransitionHelper.addTransition(mHeadersTransition, changeBounds);
        sTransitionHelper.setDuration(fadeIn, mHeadersTransitionDuration);
        sTransitionHelper.setStartDelay(fadeIn, mHeadersTransitionStartDelay);
        sTransitionHelper.addTransition(mHeadersTransition, fadeIn);

        sTransitionHelper.setTransitionListener(mHeadersTransition, new TransitionListener() {
            @Override
            public void onTransitionStart(Object transition) {
            }
            @Override
            public void onTransitionEnd(Object transition) {
                mHeadersTransition = null;
                mRowsFragment.onTransitionEnd();
                mHeadersFragment.onTransitionEnd();
                if (mShowingHeaders) {
                    VerticalGridView headerGridView = mHeadersFragment.getVerticalGridView();
                    if (headerGridView != null && !headerGridView.hasFocus()) {
                        headerGridView.requestFocus();
                    }
                } else {
                    VerticalGridView rowsGridView = mRowsFragment.getVerticalGridView();
                    if (rowsGridView != null && !rowsGridView.hasFocus()) {
                        rowsGridView.requestFocus();
                    }
                }
            }
        });
    }

    public void setHeaderPresenterSelector(PresenterSelector headerPresenterSelector) {
        mHeaderPresenterSelector = headerPresenterSelector;
        if (mHeadersFragment != null) {
            mHeadersFragment.setPresenterSelector(mHeaderPresenterSelector);
        }
    }

    private void showTitle(boolean show) {
        mBrowseTitle.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showHeaders(boolean show) {
        if (DEBUG) Log.v(TAG, "showHeaders " + show);
        mHeadersFragment.setHeadersVisiblity(show);
        MarginLayoutParams lp;
        View containerList;

        containerList = mRowsFragment.getView();
        lp = (MarginLayoutParams) containerList.getLayoutParams();
        lp.leftMargin = show ? mContainerListMarginLeft : 0;
        containerList.setLayoutParams(lp);

        containerList = mHeadersFragment.getView();
        lp = (MarginLayoutParams) containerList.getLayoutParams();
        lp.leftMargin = show ? 0 : -mContainerListMarginLeft;
        containerList.setLayoutParams(lp);

        mRowsFragment.setExpand(!show);
    }

    private HeadersFragment.OnHeaderClickedListener mHeaderClickedListener =
        new HeadersFragment.OnHeaderClickedListener() {
            @Override
            public void onHeaderClicked() {
                if (!mCanShowHeaders || !mShowingHeaders) return;

                if (mHeadersTransition != null) {
                    return;
                }
                mShowingHeaders = false;
                onHeadersTransitionStart(false);
                sTransitionHelper.runTransition(mSceneWithoutHeaders, mHeadersTransition);
                mRowsFragment.getVerticalGridView().requestFocus();
            }
        };

    private OnItemSelectedListener mRowSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(Object item, Row row) {
            int position = mRowsFragment.getVerticalGridView().getSelectedPosition();
            if (DEBUG) Log.v(TAG, "row selected position " + position);
            onRowSelected(position);
            if (mExternalOnItemSelectedListener != null) {
                mExternalOnItemSelectedListener.onItemSelected(item, row);
            }
        }
    };

    private OnItemSelectedListener mHeaderSelectedListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(Object item, Row row) {
            int position = mHeadersFragment.getVerticalGridView().getSelectedPosition();
            if (DEBUG) Log.v(TAG, "header selected position " + position);
            onRowSelected(position);
        }
    };

    private void onRowSelected(int position) {
        if (position != mSelectedPosition) {
            mSetSelectionRunnable.mPosition = position;
            mBrowseFrame.getHandler().post(mSetSelectionRunnable);

            if (position == 0) {
                if (!mShowingTitle) {
                    sTransitionHelper.runTransition(mSceneWithTitle, mTitleTransition);
                    mShowingTitle = true;
                }
            } else if (mShowingTitle) {
                sTransitionHelper.runTransition(mSceneWithoutTitle, mTitleTransition);
                mShowingTitle = false;
            }
        }
    }

    private class SetSelectionRunnable implements Runnable {
        int mPosition;
        @Override
        public void run() {
            setSelection(mPosition);
        }
    }

    private final SetSelectionRunnable mSetSelectionRunnable = new SetSelectionRunnable();

    private void setSelection(int position) {
        if (position != NO_POSITION) {
            mRowsFragment.setSelectedPosition(position);
            mHeadersFragment.setSelectedPosition(position);
        }
        mSelectedPosition = position;
    }

    private void setVerticalGridViewLayout(VerticalGridView listview, int extraOffset) {
        // align the top edge of item to a fixed position
        listview.setItemAlignmentOffset(0);
        listview.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignmentOffset(mContainerListAlignTop + extraOffset);
        listview.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
    }

    /**
     * Setup dimensions that are only meaningful when the child Fragments are inside
     * BrowseFragment.
     */
    private void setupChildFragmentsLayout() {
        VerticalGridView headerList = mHeadersFragment.getVerticalGridView();
        VerticalGridView containerList = mRowsFragment.getVerticalGridView();

        // Both fragments list view has the same alignment
        setVerticalGridViewLayout(headerList, 16);
        setVerticalGridViewLayout(containerList, 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        setupChildFragmentsLayout();
        if (mCanShowHeaders && mShowingHeaders && mHeadersFragment.getView() != null) {
            mHeadersFragment.getView().requestFocus();
        } else if ((!mCanShowHeaders || !mShowingHeaders)
                && mRowsFragment.getView() != null) {
            mRowsFragment.getView().requestFocus();
        }
        showHeaders(mCanShowHeaders && mShowingHeaders);
    }

    private void readArguments(Bundle args) {
        if (args == null) {
            return;
        }
        if (args.containsKey(ARG_TITLE)) {
            setTitle(args.getString(ARG_TITLE));
        }

        if (args.containsKey(ARG_BADGE_URI)) {
            setBadgeUri(args.getString(ARG_BADGE_URI));
        }

        if (args.containsKey(ARG_HEADERS_STATE)) {
            setHeadersState(args.getInt(ARG_HEADERS_STATE));
        }
    }

    private void setBadgeUri(String badgeUri) {
        // TODO - need a drawable downloader
    }

    private void setBadgeDrawable(Drawable drawable) {
        if (mBadgeView == null) {
            return;
        }
        mBadgeView.setImageDrawable(drawable);
        if (drawable != null) {
            mBadgeView.setVisibility(View.VISIBLE);
            mTitleView.setVisibility(View.GONE);
        } else {
            mBadgeView.setVisibility(View.GONE);
            mTitleView.setVisibility(View.VISIBLE);
        }
    }

    private void setTitle(String title) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    private void setHeadersState(int headersState) {
        if (DEBUG) Log.v(TAG, "setHeadersState " + headersState);
        switch (headersState) {
            case HEADERS_ENABLED:
                mCanShowHeaders = true;
                mShowingHeaders = true;
                break;
            case HEADERS_HIDDEN:
                mCanShowHeaders = true;
                mShowingHeaders = false;
                break;
            case HEADERS_DISABLED:
                mCanShowHeaders = false;
                mShowingHeaders = false;
                break;
            default:
                Log.w(TAG, "Unknown headers state: " + headersState);
                break;
        }
    }
}
