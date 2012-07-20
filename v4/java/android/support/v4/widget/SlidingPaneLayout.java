/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.support.v4.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * SlidingPaneLayout provides a horizontal, multi-pane layout for use at the top level
 * of a UI. A left (or first) pane is treated as a view switcher, subordinate to a
 * primary detail view for displaying content.
 *
 * <p>Child views may overlap if their combined width exceeds the available width
 * in the SlidingPaneLayout. When this occurs the user may slide the topmost view out of the way
 * by dragging it, or by navigating in the direction of the overlapped view using a keyboard.
 * If the content of the dragged child view is itself horizontally scrollable, the user may
 * grab it by the very edge.</p>
 *
 * <p>Thanks to this sliding behavior, SlidingPaneLayout may be suitable for creating layouts
 * that can smoothly adapt across many different screen sizes, expanding out fully on larger
 * screens and collapsing on smaller screens.</p>
 *
 * <p>Like {@link android.widget.LinearLayout LinearLayout}, SlidingPaneLayout supports
 * the use of the layout parameter <code>layout_weight</code> on child views to determine
 * how to divide leftover space after measurement is complete. It is only relevant for width.
 * When views do not overlap weight behaves as it does in a LinearLayout.</p>
 *
 * <p>When views do overlap, weight on a slideable pane indicates that the pane should be
 * sized to fill all available space in the closed state. Weight on a pane that becomes covered
 * indicates that the pane should be sized to fill all available space except a small minimum strip
 * that the user may use to grab the slideable view and pull it back over into a closed state.</p>
 */
public class SlidingPaneLayout extends ViewGroup {
    private static final String TAG = "SlidingPaneLayout";

    /**
     * Default size of the touch gutter along the edge where the user
     * may grab and drag a sliding pane, even if its internal content
     * may horizontally scroll.
     */
    private static final int DEFAULT_GUTTER_SIZE = 16; // dp

    /**
     * Default size of the overhang for a pane in the open state.
     * At least this much of a sliding pane will remain visible.
     * This indicates that there is more content available and provides
     * a "physical" edge to grab to pull it closed.
     */
    private static final int DEFAULT_OVERHANG_SIZE = 80; // dp;

    private static final int MAX_SETTLE_DURATION = 600; // ms

    /**
     * The size of the touch gutter in pixels
     */
    private final int mGutterSize;

    /**
     * The size of the overhang in pixels.
     * This is the minimum section of the sliding panel that will
     * be visible in the open state to allow for a closing drag.
     */
    private final int mOverhangSize;

    /**
     * True if a panel can slide with the current measurements
     */
    private boolean mCanSlide;

    /**
     * The width of the fixed panel if mCanSlide is true.
     * This is also the distance that the non-fixed panel may slide open.
     */
    private int mFixedPanelWidth;

    private View mDraggingPane;

    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean mIsUnableToDrag;

    private int mTouchSlop;
    private float mInitialMotionX;
    private float mLastMotionX;
    private float mLastMotionY;
    private int mActivePointerId = INVALID_POINTER;

    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;

    private PanelSlideListener mPanelSlideListener;

    private static final int INVALID_POINTER = -1;

    /**
     * Indicates that the panels are in an idle, settled state. The current panel
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that a panel is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that a panel is in the process of settling to a final position.
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    private int mScrollState = SCROLL_STATE_IDLE;

    /**
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    /**
     * Used to animate flinging panes.
     */
    private final Scroller mScroller;

    static final SlidingPanelLayoutImpl IMPL;

    static {
        if (Build.VERSION.SDK_INT >= 16) {
            IMPL = new SlidingPanelLayoutImplJB();
        } else {
            IMPL = new SlidingPanelLayoutImplBase();
        }
    }

    /**
     * Listener for monitoring events about sliding panes.
     */
    public interface PanelSlideListener {
        /**
         * Called when a sliding pane's position changes.
         * @param panel The child view that was moved
         * @param slideOffset The new offset of this sliding pane within its range, from 0-1
         */
        public void onPanelSlide(View panel, float slideOffset);
        /**
         * Called when a sliding pane becomes slid completely open. The pane may or may not
         * be interactive at this point depending on how much of the pane is visible.
         * @param panel The child view that was slid to an open position, revealing other panes
         */
        public void onPanelOpened(View panel);

        /**
         * Called when a sliding pane becomes slid completely closed. The pane is now guaranteed
         * to be interactive. It may now obscure other views in the layout.
         * @param panel The child view that was slid to a closed position
         */
        public void onPanelClosed(View panel);
    }

    /**
     * No-op stubs for {@link PanelSlideListener}. If you only want to implement a subset
     * of the listener methods you can extend this instead of implement the full interface.
     */
    public static class SimplePanelSlideListener implements PanelSlideListener {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
        }
        @Override
        public void onPanelOpened(View panel) {
        }
        @Override
        public void onPanelClosed(View panel) {
        }
    }

    public SlidingPaneLayout(Context context) {
        this(context, null);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mScroller = new Scroller(context, sInterpolator);

        final float density = context.getResources().getDisplayMetrics().density;
        mGutterSize = (int) (DEFAULT_GUTTER_SIZE * density + 0.5f);
        mOverhangSize = (int) (DEFAULT_OVERHANG_SIZE * density + 0.5f);

        final ViewConfiguration viewConfig = ViewConfiguration.get(context);
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(viewConfig);
        mMaxVelocity = viewConfig.getScaledMaximumFlingVelocity();
    }

    void setScrollState(int state) {
        if (mScrollState != state) {
            mScrollState = state;
        }
    }

    public void setPanelSlideListener(PanelSlideListener listener) {
        mPanelSlideListener = listener;
    }

    void dispatchOnPanelSlide(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelSlide(panel,
                    ((LayoutParams) panel.getLayoutParams()).slideOffset);
        }
    }

    void dispatchOnPanelOpened(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelOpened(panel);
        }
    }

    void dispatchOnPanelClosed(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelClosed(panel);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            throw new IllegalStateException("Height must not be UNSPECIFIED");
        }

        int layoutHeight = 0;
        int maxLayoutHeight = -1;
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                layoutHeight = maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
            case MeasureSpec.AT_MOST:
                maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
        }

        float weightSum = 0;
        boolean canSlide = false;
        boolean foundDraggingPane = false;
        int widthRemaining = widthSize - getPaddingLeft() - getPaddingRight();
        final int childCount = getChildCount();

        if (childCount > 2) {
            Log.e(TAG, "onMeasure: More than two panes are not currently supported. We're in bat country!");
        }

        // First pass. Measure based on child LayoutParams width/height.
        // Weight will incur a second pass.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child == mDraggingPane) {
                foundDraggingPane = true;
            }

            if (lp.weight > 0) {
                weightSum += lp.weight;

                // If we have no width, weight is the only contributor to the final size.
                // Measure this view on the weight pass only.
                if (lp.width == 0) continue;
            }

            int childWidthSpec;
            final int horizontalMargin = lp.leftMargin + lp.rightMargin;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - horizontalMargin,
                        MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.FILL_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - horizontalMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            int childHeightSpec;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.AT_MOST);
            } else if (lp.height == LayoutParams.FILL_PARENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            }

            child.measure(childWidthSpec, childHeightSpec);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            if (heightMode == MeasureSpec.AT_MOST && childHeight > layoutHeight) {
                layoutHeight = Math.min(childHeight, maxLayoutHeight);
            }

            widthRemaining -= childWidth;
            canSlide |= lp.canSlide = widthRemaining < 0;
        }

        // Resolve weight and make sure non-sliding panels are smaller than the full screen.
        if (canSlide || weightSum > 0) {
            final int fixedPanelWidthLimit = widthSize - mOverhangSize;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final boolean skippedFirstPass = lp.width == 0 && lp.weight > 0;
                final int measuredWidth = skippedFirstPass ? 0 : child.getMeasuredWidth();
                if (canSlide && !lp.canSlide) {
                    if (lp.width < 0 && (measuredWidth > fixedPanelWidthLimit || lp.weight > 0)) {
                        // Fixed panels in a sliding configuration should
                        // be clamped to the fixed panel limit.
                        final int childHeightSpec;
                        if (skippedFirstPass) {
                            // Do initial height measurement if we skipped measuring this view
                            // the first time around.
                            if (lp.height == LayoutParams.WRAP_CONTENT) {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                        MeasureSpec.AT_MOST);
                            } else if (lp.height == LayoutParams.FILL_PARENT) {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                        MeasureSpec.EXACTLY);
                            } else {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height,
                                        MeasureSpec.EXACTLY);
                            }
                        } else {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(
                                    child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                        }
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                fixedPanelWidthLimit, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                        mFixedPanelWidth = fixedPanelWidthLimit;
                    } else {
                        mFixedPanelWidth = measuredWidth;
                    }
                } else if (lp.weight > 0) {
                    int childHeightSpec;
                    if (lp.width == 0) {
                        // This was skipped the first time; figure out a real height spec.
                        if (lp.height == LayoutParams.WRAP_CONTENT) {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                    MeasureSpec.AT_MOST);
                        } else if (lp.height == LayoutParams.FILL_PARENT) {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                    MeasureSpec.EXACTLY);
                        } else {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height,
                                    MeasureSpec.EXACTLY);
                        }
                    } else {
                        childHeightSpec = MeasureSpec.makeMeasureSpec(
                                child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                    }

                    if (canSlide) {
                        // Consume available space
                        final int horizontalMargin = lp.leftMargin + lp.rightMargin;
                        final int newWidth = widthSize - horizontalMargin;
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                newWidth, MeasureSpec.EXACTLY);
                        if (measuredWidth != newWidth) {
                            child.measure(childWidthSpec, childHeightSpec);
                        }
                    } else {
                        // Distribute the extra width proportionally similar to LinearLayout
                        final int widthToDistribute = Math.max(0, widthRemaining);
                        final int addedWidth = (int) (lp.weight * widthToDistribute / weightSum);
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                measuredWidth + addedWidth, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                    }
                }
            }
        }

        setMeasuredDimension(widthSize, layoutHeight);
        mCanSlide = canSlide;
        if (mDraggingPane != null && (!canSlide || !foundDraggingPane)) {
            // Cancel any drags in progress.
            mDraggingPane = null;
            setScrollState(SCROLL_STATE_IDLE);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final int height = b - t;
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        int xStart = paddingLeft;
        int nextXStart = xStart;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int childWidth = child.getMeasuredWidth();

            if (lp.canSlide) {
                final int range = Math.min(nextXStart - xStart,
                        width - paddingRight - mOverhangSize - xStart);
                lp.range = range;
                lp.dimWhenOffset = width - paddingRight - (xStart + range) < childWidth / 2;
                xStart += (int) (range * lp.slideOffset);
            } else {
                xStart = nextXStart;
            }

            final int childLeft = xStart + lp.leftMargin;
            final int childRight = childLeft + childWidth;
            final int childTop = paddingTop;
            final int childBottom = childTop + child.getMeasuredHeight();
            child.layout(childLeft, paddingTop, childRight, childBottom);

            nextXStart = child.getRight() + lp.rightMargin;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mCanSlide) {
            return super.onInterceptTouchEvent(ev);
        }

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mDraggingPane = null;
            mActivePointerId = INVALID_POINTER;
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            return false;
        }

        boolean interceptTap = false;

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // No valid pointer = no valid drag. Ignore.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float dx = x - mLastMotionX;
                final float xDiff = Math.abs(dx);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = Math.abs(y - mLastMotionY);

                if (dx != 0 && !isGutterDrag(mLastMotionX, dx) &&
                        canScroll(this, false, (int) dx, (int) x, (int) y)) {
                    mInitialMotionX = mLastMotionX = x;
                    mLastMotionY = y;
                    mIsUnableToDrag = true;
                    return false;
                }
                if (xDiff > mTouchSlop && xDiff > yDiff) {
                    if ((mDraggingPane = getChildUnderPoint(x, y)) != null) {
                        mLastMotionX = dx > 0 ? mInitialMotionX + mTouchSlop :
                                mInitialMotionX - mTouchSlop;
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                } else if (yDiff > mTouchSlop) {
                    mIsUnableToDrag = true;
                }
                if (mDraggingPane != null && performDrag(x)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                final View dragTarget = getChildUnderPoint(x, y);
                mIsUnableToDrag = false;
                if (dragTarget == null || !canSlide(dragTarget)) {
                    mDraggingPane = null;
                } else {
                    mLastMotionX = mInitialMotionX = x;
                    mLastMotionY = y;
                    mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                    if ((mScrollState == SCROLL_STATE_SETTLING && mDraggingPane == dragTarget)) {
                        // Start dragging immediately.
                        setScrollState(SCROLL_STATE_DRAGGING);
                    } else if (isDimmed(dragTarget)) {
                        interceptTap = true;
                    }
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        return mScrollState == SCROLL_STATE_DRAGGING || interceptTap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mCanSlide) {
            return super.onTouchEvent(ev);
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        boolean needsInvalidate = false;
        boolean wantTouchEvents = false;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                final View view = getChildUnderPoint(x, y);
                final LayoutParams lp = view != null ? (LayoutParams) view.getLayoutParams() : null;

                if (view != null && lp.canSlide) {
                    mScroller.abortAnimation();
                    wantTouchEvents = true;
                    mLastMotionX = mInitialMotionX = x;
                    mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                    mDraggingPane = view;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float dx = Math.abs(x - mLastMotionX);
                    final float dy = Math.abs(y - mLastMotionY);
                    if (dx > mTouchSlop && dx > dy &&
                            (mDraggingPane = getChildUnderPoint(x, y)) != null) {
                        mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop :
                                mInitialMotionX - mTouchSlop;
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
                    needsInvalidate |= performDrag(x);
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    final VelocityTracker vt = mVelocityTracker;
                    vt.computeCurrentVelocity(1000, mMaxVelocity);
                    int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(vt,
                            mActivePointerId);
                    final LayoutParams dragLp = (LayoutParams) mDraggingPane.getLayoutParams();
                    if (initialVelocity < 0 ||
                            (initialVelocity == 0 && dragLp.slideOffset < 0.5f)) {
                        closePane(mDraggingPane, initialVelocity);
                    } else {
                        openPane(mDraggingPane, initialVelocity);
                    }
                    mActivePointerId = INVALID_POINTER;
                } else if (mDraggingPane != null && isDimmed(mDraggingPane)) {
                    // Taps close a dimmed open pane.
                    closePane(mDraggingPane, 0);
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    mActivePointerId = INVALID_POINTER;
                    final LayoutParams dragLp = (LayoutParams) mDraggingPane.getLayoutParams();
                    if (dragLp.slideOffset < 0.5f) {
                        closePane(mDraggingPane, 0);
                    } else {
                        openPane(mDraggingPane, 0);
                    }
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                mLastMotionX = MotionEventCompat.getX(ev, index);
                mLastMotionY = MotionEventCompat.getY(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                break;
            }
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
        return wantTouchEvents;
    }

    private void closePane(View pane, int initialVelocity) {
        mDraggingPane = pane;
        smoothSlideTo(0.f, initialVelocity);
    }

    private void openPane(View pane, int initialVelocity) {
        mDraggingPane = pane;
        smoothSlideTo(1.f, initialVelocity);
    }

    /**
     * Animate the sliding panel to its open state.
     */
    public void smoothSlideOpen() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.canSlide) {
                openPane(child, 0);
                return;
            }
        }
    }

    /**
     * Animate the sliding panel to its closed state.
     */
    public void smoothSlideClosed() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.canSlide) {
                closePane(child, 0);
                return;
            }
        }
    }

    /**
     * @return true if sliding panels are completely open
     */
    public boolean isOpen() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.canSlide && lp.slideOffset < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if content in this layout can be slid open and closed
     */
    public boolean canSlide() {
        return mCanSlide;
    }

    private boolean performDrag(float x) {
        final float dxMotion = x - mLastMotionX;
        mLastMotionX = x;

        final LayoutParams lp = (LayoutParams) mDraggingPane.getLayoutParams();
        final int leftBound = getPaddingLeft() + lp.leftMargin;
        final int rightBound = leftBound + lp.range;

        final float oldLeft = mDraggingPane.getLeft();
        final float newLeft = Math.min(Math.max(oldLeft + dxMotion, leftBound), rightBound);
        final float dxPane = newLeft - oldLeft;
        final float newRight = mDraggingPane.getRight() + dxPane;

        mDraggingPane.layout((int) newLeft, mDraggingPane.getTop(),
                (int) newRight, mDraggingPane.getBottom());
        lp.slideOffset = (newLeft - leftBound) / lp.range;

        mLastMotionX += newLeft - (int) newLeft;
        dimChildViewForRange(mDraggingPane);
        dispatchOnPanelSlide(mDraggingPane);

        return false;
    }

    private void dimChildViewForRange(View v) {
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();
        if (!lp.dimWhenOffset) return;

        final float mag = lp.slideOffset;

        if (mag > 0) {
            int imag = 0x4c + (int) (0xb3 * (1 - mag));
            int color = 0xff000000 | imag << 16 | imag << 8 | imag;
            if (lp.dimPaint == null) {
                lp.dimPaint = new Paint();
            }
            lp.dimPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.DARKEN));
            if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_HARDWARE) {
                ViewCompat.setLayerType(v, ViewCompat.LAYER_TYPE_HARDWARE, lp.dimPaint);
            }
            invalidateChildRegion(v);
        } else if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_NONE) {
            ViewCompat.setLayerType(v, ViewCompat.LAYER_TYPE_NONE, null);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (Build.VERSION.SDK_INT >= 11) { // HC
            return super.drawChild(canvas, child, drawingTime);
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.dimWhenOffset && lp.slideOffset > 0) {
            if (!child.isDrawingCacheEnabled()) {
                child.setDrawingCacheEnabled(true);
            }
            final Bitmap cache = child.getDrawingCache();
            canvas.drawBitmap(cache, child.getLeft(), child.getTop(), lp.dimPaint);
            return false;
        } else {
            if (child.isDrawingCacheEnabled()) {
                child.setDrawingCacheEnabled(false);
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    private void invalidateChildRegion(View v) {
        IMPL.invalidateChildRegion(this, v);
    }

    private boolean isGutterDrag(float x, float dx) {
        return (x < mGutterSize && dx > 0) || (x > getWidth() - mGutterSize && dx < 0);
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity initial velocity in case of fling, or 0.
     */
    void smoothSlideTo(float slideOffset, int velocity) {
        if (mDraggingPane == null) {
            // Nothing to do.
            return;
        }

        final LayoutParams lp = (LayoutParams) mDraggingPane.getLayoutParams();

        final int leftBound = getPaddingLeft() + lp.leftMargin;
        int sx = mDraggingPane.getLeft();
        int x = (int) (leftBound + slideOffset * lp.range);
        int dx = x - sx;
        if (dx == 0) {
            setScrollState(SCROLL_STATE_IDLE);
            if (lp.slideOffset == 0) {
                dispatchOnPanelClosed(mDraggingPane);
            } else {
                dispatchOnPanelOpened(mDraggingPane);
            }
            mDraggingPane = null;
            return;
        }

        setScrollState(SCROLL_STATE_SETTLING);

        final int width = getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
        final float distance = halfWidth + halfWidth *
                distanceInfluenceForSnapDuration(distanceRatio);

        int duration = 0;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float range = (float) Math.abs(dx) / lp.range;
            duration = (int) ((range + 1) * 100);
        }
        duration = Math.min(duration, MAX_SETTLE_DURATION);

        mScroller.startScroll(sx, 0, dx, 0, duration);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            if (mDraggingPane == null) {
                mScroller.abortAnimation();
                return;
            }

            final int oldLeft = mDraggingPane.getLeft();
            final int newLeft = mScroller.getCurrX();
            final int dx = newLeft - oldLeft;
            mDraggingPane.layout(newLeft, mDraggingPane.getTop(),
                    mDraggingPane.getRight() + dx, mDraggingPane.getBottom());

            final LayoutParams lp = (LayoutParams) mDraggingPane.getLayoutParams();
            final int leftBound = getPaddingLeft() + lp.leftMargin;
            lp.slideOffset = (float) (newLeft - leftBound) / lp.range;
            dimChildViewForRange(mDraggingPane);
            dispatchOnPanelSlide(mDraggingPane);

            if (mScroller.isFinished()) {
                if (lp.slideOffset == 0) {
                    dispatchOnPanelClosed(mDraggingPane);
                } else {
                    dispatchOnPanelOpened(mDraggingPane);
                }
                setScrollState(SCROLL_STATE_IDLE);
                mDraggingPane = null;
            }
            ViewCompat.postInvalidateOnAnimation(this);
        }

    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx Delta scrolled in pixels
     * @param x X coordinate of the active touch point
     * @param y Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    View getChildUnderPoint(float x, float y) {
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (x >= child.getLeft() && x < child.getRight() &&
                    y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    static boolean canSlide(View child) {
        return ((LayoutParams) child.getLayoutParams()).canSlide;
    }

    static boolean isDimmed(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return lp.canSlide && lp.dimWhenOffset && lp.slideOffset > 0;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[] {
            android.R.attr.layout_gravity,
            android.R.attr.layout_weight
        };

        public int gravity = Gravity.NO_GRAVITY;
        public float weight = 0;

        boolean canSlide = false;

        /**
         * Value from 0-1 indicating how far the pane has been dragged from closed to open
         */
        float slideOffset;

        /**
         * The range this view can be dragged in pixels
         */
        int range;

        /**
         * True if this view should be drawn dimmed
         * when it's been offset from its default position.
         */
        boolean dimWhenOffset;

        Paint dimPaint;

        public LayoutParams() {
            super(FILL_PARENT, FILL_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
            this.weight = source.weight;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
            this.weight = a.getFloat(1, 0);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        boolean canSlide;
        boolean isOpen;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            canSlide = in.readInt() != 0;
            isOpen = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(canSlide ? 1 : 0);
            out.writeInt(isOpen ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    interface SlidingPanelLayoutImpl {
        void invalidateChildRegion(SlidingPaneLayout parent, View child);
    }

    static class SlidingPanelLayoutImplBase implements SlidingPanelLayoutImpl {
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            ViewCompat.postInvalidateOnAnimation(parent, child.getLeft(), child.getTop(),
                    child.getRight(), child.getBottom());
        }
    }

    static class SlidingPanelLayoutImplJB extends SlidingPanelLayoutImplBase {
        /*
         * Private API hacks! Nasty! Bad!
         *
         * In Jellybean, some optimizations in the hardware UI renderer
         * prevent a changed Paint on a View using a hardware layer from having
         * the intended effect. This twiddles some internal bits on the view to force
         * it to recreate the display list.
         */
        private Method mGetDisplayList;
        private Field mRecreateDisplayList;

        SlidingPanelLayoutImplJB() {
            try {
                mGetDisplayList = View.class.getDeclaredMethod("getDisplayList", (Class[]) null);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Couldn't fetch getDisplayList method; dimming won't work right.", e);
            }
            try {
                mRecreateDisplayList = View.class.getDeclaredField("mRecreateDisplayList");
                mRecreateDisplayList.setAccessible(true);
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "Couldn't fetch mRecreateDisplayList field; dimming will be slow.", e);
            }
        }

        @Override
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            if (mGetDisplayList != null && mRecreateDisplayList != null) {
                try {
                    mRecreateDisplayList.setBoolean(child, true);
                    mGetDisplayList.invoke(child, (Object[]) null);
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing display list state", e);
                }
            } else {
                // Slow path. REALLY slow path. Let's hope we don't get here.
                child.invalidate();
                return;
            }
            super.invalidateChildRegion(parent, child);
        }
    }
}
