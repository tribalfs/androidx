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

package androidx.viewpager2.widget;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static androidx.core.view.ViewCompat.LAYOUT_DIRECTION_RTL;
import static androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE;
import static androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING;
import static androidx.viewpager2.widget.ViewPager2.ScrollState;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback;

import java.lang.annotation.Retention;
import java.util.Locale;

/**
 * Translates {@link RecyclerView.OnScrollListener} events to {@link OnPageChangeCallback} events
 * for {@link ViewPager2}. As part of this process, it keeps track of the current scroll position
 * relative to the pages and exposes this position via ({@link #getRelativeScrollPosition()}.
 */
final class ScrollEventAdapter extends RecyclerView.OnScrollListener {
    private static final MarginLayoutParams ZERO_MARGIN_LAYOUT_PARAMS;

    static {
        ZERO_MARGIN_LAYOUT_PARAMS = new MarginLayoutParams(MATCH_PARENT, MATCH_PARENT);
        ZERO_MARGIN_LAYOUT_PARAMS.setMargins(0, 0, 0, 0);
    }

    @Retention(SOURCE)
    @IntDef({STATE_IDLE, STATE_IN_PROGRESS_MANUAL_DRAG, STATE_IN_PROGRESS_SMOOTH_SCROLL,
            STATE_IN_PROGRESS_IMMEDIATE_SCROLL})
    private @interface AdapterState {
    }

    private static final int STATE_IDLE = 0;
    private static final int STATE_IN_PROGRESS_MANUAL_DRAG = 1;
    private static final int STATE_IN_PROGRESS_SMOOTH_SCROLL = 2;
    private static final int STATE_IN_PROGRESS_IMMEDIATE_SCROLL = 3;

    private static final int NO_POSITION = -1;

    private OnPageChangeCallback mCallback;
    private final @NonNull LinearLayoutManager mLayoutManager;

    // state related fields
    private @AdapterState int mAdapterState;
    private @ViewPager2.ScrollState int mScrollState;
    private ScrollEventValues mScrollValues;
    private int mDragStartPosition;
    private int mTarget;
    private boolean mDispatchSelected;
    private boolean mScrollHappened;

    ScrollEventAdapter(@NonNull LinearLayoutManager layoutManager) {
        mLayoutManager = layoutManager;
        mScrollValues = new ScrollEventValues();
        resetState();
    }

    private void resetState() {
        mAdapterState = STATE_IDLE;
        mScrollState = SCROLL_STATE_IDLE;
        mScrollValues.reset();
        mDragStartPosition = NO_POSITION;
        mTarget = NO_POSITION;
        mDispatchSelected = false;
        mScrollHappened = false;
    }

    /**
     * This method only deals with some cases of {@link AdapterState} transitions. The rest of
     * the state transition implementation is in the {@link #onScrolled} method.
     */
    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        // User started a drag (not dragging -> dragging)
        if (mAdapterState != STATE_IN_PROGRESS_MANUAL_DRAG
                && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            // Remember we're performing a drag
            mAdapterState = STATE_IN_PROGRESS_MANUAL_DRAG;
            if (mTarget != NO_POSITION) {
                // Target was set means programmatic scroll was in progress
                // Update "drag start page" to reflect the page that ViewPager2 thinks it is at
                mDragStartPosition = mTarget;
                // Reset target because drags have no target until released
                mTarget = NO_POSITION;
            } else {
                // ViewPager2 was at rest, set "drag start page" to current page
                mDragStartPosition = getPosition();
            }
            dispatchStateChanged(SCROLL_STATE_DRAGGING);
            return;
        }

        // Drag is released, RecyclerView is snapping to page (dragging -> settling)
        // Note that mAdapterState is not updated, to remember we were dragging when settling
        if (mAdapterState == STATE_IN_PROGRESS_MANUAL_DRAG
                && newState == RecyclerView.SCROLL_STATE_SETTLING) {
            // Only go through the settling phase if the drag actually moved the page
            if (mScrollHappened) {
                dispatchStateChanged(SCROLL_STATE_SETTLING);
                // Determine target page and dispatch onPageSelected on next scroll event
                mDispatchSelected = true;
            }
            return;
        }

        // Drag is finished (dragging || settling -> idle)
        if (mAdapterState == STATE_IN_PROGRESS_MANUAL_DRAG
                && newState == RecyclerView.SCROLL_STATE_IDLE) {
            boolean dispatchIdle = false;
            updateScrollEventValues();
            if (!mScrollHappened) {
                // Pages didn't move during drag, so must be at the start or end of the list
                // ViewPager's contract requires at least one scroll event though
                dispatchScrolled(getPosition(), 0f, 0);
                dispatchIdle = true;
            } else if (mScrollValues.mOffsetPx == 0) {
                // Normally we dispatch the selected page and go to idle in onScrolled when
                // mOffsetPx == 0, but in this case the drag was still ongoing when onScrolled was
                // called, so that didn't happen. And since mOffsetPx == 0, there will be no further
                // scroll events, so fire the onPageSelected event and go to idle now.
                // Note that if we _did_ go to idle in that last onScrolled event, this code will
                // not be executed because mAdapterState has been reset to STATE_IDLE.
                dispatchIdle = true;
                if (mDragStartPosition != mScrollValues.mPosition) {
                    dispatchSelected(mScrollValues.mPosition);
                }
            }
            if (dispatchIdle) {
                // Normally idle is fired in last onScrolled call, but either onScrolled was never
                // called, or we were still dragging when the last onScrolled was called
                dispatchStateChanged(SCROLL_STATE_IDLE);
                resetState();
            }
        }
    }

    /**
     * This method only deals with some cases of {@link AdapterState} transitions. The rest of
     * the state transition implementation is in the {@link #onScrollStateChanged} method.
     */
    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        mScrollHappened = true;
        ScrollEventValues values = updateScrollEventValues();

        if (mDispatchSelected) {
            // Drag started settling, need to calculate target page and dispatch onPageSelected now
            mDispatchSelected = false;
            boolean scrollingForward = dy > 0 || (dy == 0 && dx < 0 == isLayoutRTL());

            // "&& values.mOffsetPx != 0": filters special case where we're scrolling forward and
            // the first scroll event after settling already got us at the target
            mTarget = scrollingForward && values.mOffsetPx != 0
                    ? values.mPosition + 1 : values.mPosition;
            if (mDragStartPosition != mTarget) {
                dispatchSelected(mTarget);
            }
        }

        dispatchScrolled(values.mPosition, values.mOffset, values.mOffsetPx);

        // Dispatch idle in onScrolled instead of in onScrollStateChanged because RecyclerView
        // doesn't send IDLE event when using setCurrentItem(x, false)
        if ((values.mPosition == mTarget || mTarget == NO_POSITION) && values.mOffsetPx == 0
                && mScrollState != SCROLL_STATE_DRAGGING) {
            // When the target page is reached and the user is not dragging anymore, we're settled,
            // so go to idle.
            // Special case and a bit of a hack when mTarget == NO_POSITION: RecyclerView is being
            // initialized and fires a single scroll event. This flags mScrollHappened, so we need
            // to reset our state. However, we don't want to dispatch idle. But that won't happen;
            // because we were already idle.
            dispatchStateChanged(SCROLL_STATE_IDLE);
            resetState();
        }
    }

    /**
     * Calculates the current position and the offset (as a percentage and in pixels) of that
     * position from the center.
     */
    private ScrollEventValues updateScrollEventValues() {
        ScrollEventValues values = mScrollValues;

        values.mPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (values.mPosition == RecyclerView.NO_POSITION) {
            return values.reset();
        }
        View firstVisibleView = mLayoutManager.findViewByPosition(values.mPosition);
        if (firstVisibleView == null) {
            return values.reset();
        }

        // TODO(123350297): automated test for this
        MarginLayoutParams margin =
                (firstVisibleView.getLayoutParams() instanceof MarginLayoutParams)
                        ? (MarginLayoutParams) firstVisibleView.getLayoutParams()
                        : ZERO_MARGIN_LAYOUT_PARAMS;

        boolean isHorizontal = mLayoutManager.getOrientation() == ORIENTATION_HORIZONTAL;
        int start, sizePx;
        if (isHorizontal) {
            sizePx = firstVisibleView.getWidth() + margin.leftMargin + margin.rightMargin;
            if (!isLayoutRTL()) {
                start = firstVisibleView.getLeft() - margin.leftMargin;
            } else {
                start = sizePx - firstVisibleView.getRight() - margin.rightMargin;
            }
        } else {
            sizePx = firstVisibleView.getHeight() + margin.topMargin + margin.bottomMargin;
            start = firstVisibleView.getTop() - margin.topMargin;
        }

        values.mOffsetPx = -start;
        if (values.mOffsetPx < 0) {
            throw new IllegalStateException(String.format(Locale.US, "Page can only be offset by a "
                    + "positive amount, not by %d", values.mOffsetPx));
        }
        values.mOffset = sizePx == 0 ? 0 : (float) values.mOffsetPx / sizePx;
        return values;
    }

    /**
     * Let the adapter know a programmatic scroll was initiated.
     */
    void notifyProgrammaticScroll(int target, boolean smooth) {
        mAdapterState = smooth
                ? STATE_IN_PROGRESS_SMOOTH_SCROLL
                : STATE_IN_PROGRESS_IMMEDIATE_SCROLL;
        boolean hasNewTarget = mTarget != target;
        mTarget = target;
        dispatchStateChanged(SCROLL_STATE_SETTLING);
        if (hasNewTarget) {
            dispatchSelected(target);
        }
    }

    /**
     * Let the adapter know that mCurrentItem was restored in onRestoreInstanceState
     */
    void notifyRestoreCurrentItem(int currentItem) {
        dispatchSelected(currentItem);
    }

    private boolean isLayoutRTL() {
        return mLayoutManager.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    void setOnPageChangeCallback(OnPageChangeCallback callback) {
        mCallback = callback;
    }

    /**
     * @return true if there is no known scroll in progress
     */
    boolean isIdle() {
        return mAdapterState == STATE_IDLE;
    }

    /**
     * Calculates the scroll position of the currently visible item of the ViewPager relative to its
     * width. Calculated by adding the fraction by which the first visible item is off screen to its
     * adapter position. E.g., if the ViewPager is currently scrolling from the second to the third
     * page, the returned value will be between 1 and 2. Thus, non-integral values mean that the
     * the ViewPager is settling towards its {@link ViewPager2#getCurrentItem() current item}, or
     * the user may be dragging it.
     *
     * @return The current scroll position of the ViewPager, relative to its width
     */
    float getRelativeScrollPosition() {
        updateScrollEventValues();
        return mScrollValues.mPosition + mScrollValues.mOffset;
    }

    private void dispatchStateChanged(@ScrollState int state) {
        // Callback contract for immediate-scroll requires not having state change notifications,
        // but only when there was no smooth scroll in progress.
        // By putting a suppress statement in here (rather than next to dispatch calls) we are
        // simplifying the code of the class and enforcing the contract in one place.
        if (mAdapterState == STATE_IN_PROGRESS_IMMEDIATE_SCROLL
                && mScrollState == SCROLL_STATE_IDLE) {
            return;
        }
        if (mScrollState == state) {
            return;
        }

        mScrollState = state;
        if (mCallback != null) {
            mCallback.onPageScrollStateChanged(state);
        }
    }

    private void dispatchSelected(int target) {
        if (mCallback != null) {
            mCallback.onPageSelected(target);
        }
    }

    private void dispatchScrolled(int position, float offset, int offsetPx) {
        if (mCallback != null) {
            mCallback.onPageScrolled(position, offset, offsetPx);
        }
    }

    private int getPosition() {
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    private static final class ScrollEventValues {
        int mPosition;
        float mOffset;
        int mOffsetPx;

        // to avoid a synthetic accessor
        ScrollEventValues() {
        }

        ScrollEventValues reset() {
            mPosition = RecyclerView.NO_POSITION;
            mOffset = 0f;
            mOffsetPx = 0;
            return this;
        }
    }
}
