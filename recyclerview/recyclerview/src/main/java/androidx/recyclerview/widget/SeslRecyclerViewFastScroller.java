/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.recyclerview.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroupOverlay;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.annotation.RestrictTo;
import androidx.appcompat.animation.SeslAnimationUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.math.MathUtils;
import androidx.recyclerview.R;
import androidx.reflect.content.res.SeslConfigurationReflector;
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung FastScroller class.
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
class SeslRecyclerViewFastScroller {
    // Sesl
    private static final String TAG = "SeslFastScroller";

    private static final int FASTSCROLL_VIBRATE_INDEX = 26;
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    public static final int EFFECT_STATE_CLOSE = 0;
    public static final int EFFECT_STATE_OPEN = 1;

    private final Context mContext;

    private final RecyclerView mRecyclerView;
    /** @noinspection rawtypes*/
    private RecyclerView.Adapter mListAdapter;

    private final float mAdditionalTouchArea;
    private float mLastDraggingY = 0f;
    private float mOldThumbPosition = -1f;
    private float mScrollY = 0f;
    private float mThreshold = 0f;

    private int mAdditionalBottomPadding;
    private int mAdditionalTopPadding;
    private int mColorPrimary = Color.WHITE;
    private int mEffectState = EFFECT_STATE_CLOSE;
    private int mImmersiveBottomPadding;
    private final int mPreviewMarginEnd;
    private int mThumbBackgroundColor = Color.WHITE;
    private final int mThumbMarginEnd;
    private final int mTrackBottomPadding;
    private final int mTrackTopPadding;
    private final int mVibrateIndex;

    private final boolean mIsDexMode;
    // Sesl

    /** Duration of fade-out animation. */
    private static final int DURATION_FADE_OUT = 150;

    /** Duration of fade-in animation. */
    private static final int DURATION_FADE_IN = 167;

    /** Duration of transition cross-fade animation. */
    private static final int DURATION_CROSS_FADE = 0;

    /** Duration of transition resize animation. */
    private static final int DURATION_RESIZE = 100;

    /** Inactivity timeout before fading controls. */
    private static final long FADE_TIMEOUT = 1500;


    /** Scroll thumb and preview not showing. */
    private static final int STATE_NONE = 0;

    /** Scroll thumb visible and moving along with the scrollbar. */
    private static final int STATE_VISIBLE = 1;

    /** Scroll thumb and preview being dragged by user. */
    private static final int STATE_DRAGGING = 2;

    // Positions for preview image and text.
    private static final int OVERLAY_FLOATING = 0;
    private static final int OVERLAY_AT_THUMB = 1;
    private static final int OVERLAY_ABOVE_THUMB = 2;

    // Positions for thumb in relation to track.
    private static final int THUMB_POSITION_MIDPOINT = 0;
    private static final int THUMB_POSITION_INSIDE = 1;

    // Indices for mPreviewResId.
    private static final int PREVIEW_LEFT = 0;
    private static final int PREVIEW_RIGHT = 1;

    /** Delay before considering a tap in the thumb area to be a drag. */
    private static final long TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

    private final Rect mTempBounds = new Rect();
    private final Rect mTempMargins = new Rect();
    private final Rect mContainerRect = new Rect();

    private final ViewGroupOverlay mOverlay;
    private final TextView mPrimaryText;
    private final TextView mSecondaryText;
    private final ImageView mThumbImage;
    private final ImageView mTrackImage;
    private final View mPreviewImage;
    /**
     * Preview image resource IDs for left- and right-aligned layouts. See
     * {@link #PREVIEW_LEFT} and {@link #PREVIEW_RIGHT}.
     */
    private final int[] mPreviewResId = new int[2];

    private final int mPreviewMinWidth;
    private final int mPreviewMinHeight;
    private final int mThumbMinWidth;
    private final int mThumbMinHeight;

    /** Theme-specified text size. Used only if text appearance is not set. */
    private final float mTextSize;

    /** Theme-specified text color. Used only if text appearance is not set. */
    private final ColorStateList mTextColor;

    private final Drawable mThumbDrawable;
    private final Drawable mTrackDrawable;
    private final int mTextAppearance;
    private final int mThumbPosition;

    // Used to convert between y-coordinate and thumb position within track.
    private float mThumbOffset;
    private float mThumbRange;

    /** Total width of decorations. */
    private int mWidth;

    /** Set containing decoration transition animations. */
    private AnimatorSet mDecorAnimation;

    /** Set containing preview text transition animations. */
    private AnimatorSet mPreviewAnimation;

    /** Whether the primary text is showing. */
    boolean mShowingPrimary;

    /** Whether we're waiting for completion of scrollTo(). */
    private boolean mScrollCompleted;

    /** The index of the current section. */
    private int mCurrentSection = -1;

    /** The current scrollbar position. */
    private int mScrollbarPosition = -1;

    /** Whether the list is long enough to need a fast scroller. */
    private boolean mLongList;

    private Object[] mSections;

    /** Whether this view is currently performing layout. */
    private boolean mUpdatingLayout;

    /**
     * Current decoration state, one of:
     * <ul>
     * <li>{@link #STATE_NONE}, nothing visible
     * <li>{@link #STATE_VISIBLE}, showing track and thumb
     * <li>{@link #STATE_DRAGGING}, visible and showing preview
     * </ul>
     */
    private int mState;

    /** Whether the preview image is visible. */
    private boolean mShowingPreview;

    private SectionIndexer mSectionIndexer;

    /** Whether decorations should be laid out from right to left. */
    private boolean mLayoutFromRight;

    /** Whether the fast scroller is enabled. */
    private boolean mEnabled;

    /** Whether the scrollbar and decorations should always be shown. */
    private boolean mAlwaysShow;

    /**
     * Position for the preview image and text. One of:
     * <ul>
     * <li>{@link #OVERLAY_FLOATING}
     * <li>{@link #OVERLAY_AT_THUMB}
     * <li>{@link #OVERLAY_ABOVE_THUMB}
     * </ul>
     */
    private final int mOverlayPosition;

    /** Current scrollbar style, including inset and overlay properties. */
    private int mScrollBarStyle;

    /** Whether to precisely match the thumb position to the list. */
    private final boolean mMatchDragPosition;

    private float mInitialTouchY;
    private long mPendingDrag = -1;
    private final int mScaledTouchSlop;

    private int mOldItemCount;
    private int mOldChildCount;

    /**
     * Used to delay hiding fast scroll decorations.
     */
    private final Runnable mDeferHide = () -> setState(STATE_NONE);

    /**
     * Used to effect a transition from primary to secondary text.
     */
    private final AnimatorListener mSwitchPrimaryListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mShowingPrimary = !mShowingPrimary;
        }
    };

    public SeslRecyclerViewFastScroller(RecyclerView listView) {
        mRecyclerView = listView;
        mOldItemCount = listView.getAdapter().getItemCount();
        mOldChildCount = listView.getChildCount();

        final Context context = listView.getContext();
        mContext = context;
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mScrollBarStyle = listView.getScrollBarStyle();

        mScrollCompleted = true;
        mState = STATE_VISIBLE;
        mMatchDragPosition =
                mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.HONEYCOMB;

        mTrackImage = new ImageView(mContext);
        mTrackImage.setScaleType(ScaleType.FIT_XY);
        mThumbImage = new ImageView(mContext);
        mThumbImage.setScaleType(ScaleType.FIT_XY);
        mPreviewImage = new View(mContext);
        mPreviewImage.setAlpha(0f);

        mPrimaryText = createPreviewTextView(mContext);
        mSecondaryText = createPreviewTextView(mContext);

        TypedArray a = mContext.getTheme().obtainStyledAttributes(null,
                R.styleable.FastScroll, 0, R.style.Widget_RecyclerView_FastScroll);

        mOverlayPosition = a.getInt(R.styleable.FastScroll_position, OVERLAY_FLOATING);

        mPreviewResId[0] = a.getResourceId(R.styleable.FastScroll_backgroundLeft, 0);
        mPreviewResId[1] = a.getResourceId(R.styleable.FastScroll_backgroundRight, 0);

        mThumbDrawable = a.getDrawable(R.styleable.FastScroll_thumbDrawable);
        mTrackDrawable = a.getDrawable(R.styleable.FastScroll_trackDrawable);

        mTextAppearance = a.getResourceId(R.styleable.FastScroll_android_textAppearance, 0);
        mTextColor = a.getColorStateList(R.styleable.FastScroll_android_textColor);
        mTextSize = a.getDimensionPixelSize(R.styleable.FastScroll_android_textSize, 0);

        mPreviewMinWidth = a.getDimensionPixelSize(R.styleable.FastScroll_android_minWidth, 0);
        mPreviewMinHeight = a.getDimensionPixelSize(R.styleable.FastScroll_android_minHeight, 0);

        mThumbMinWidth = a.getDimensionPixelSize(R.styleable.FastScroll_thumbMinWidth, 0);
        mThumbMinHeight = a.getDimensionPixelSize(R.styleable.FastScroll_thumbMinHeight, 0);

        /*
         * Padding in pixels around the preview text. Applied as layout margins to
         * the preview text and padding to the preview image.
         */
        int mPreviewPadding = a.getDimensionPixelSize(R.styleable.FastScroll_android_padding, 0);

        mThumbPosition = a.getInt(R.styleable.FastScroll_thumbPosition, THUMB_POSITION_MIDPOINT);

        a.recycle();

        updateAppearance();

        final ViewGroupOverlay overlay = listView.getOverlay();
        mOverlay = overlay;
        overlay.add(mTrackImage);
        overlay.add(mThumbImage);
        overlay.add(mPreviewImage);
        overlay.add(mPrimaryText);
        overlay.add(mSecondaryText);

        final Resources resources = mContext.getResources();
        mPreviewMarginEnd = resources.getDimensionPixelOffset(R.dimen.sesl_fast_scroll_preview_margin_end);
        mThumbMarginEnd = resources.getDimensionPixelOffset(R.dimen.sesl_fast_scroll_thumb_margin_end);
        mAdditionalTouchArea = resources.getDimension(R.dimen.sesl_fast_scroll_additional_touch_area);
        mTrackTopPadding = resources.getDimensionPixelOffset(R.dimen.sesl_fast_scroller_track_top_padding);
        mTrackBottomPadding = resources.getDimensionPixelOffset(R.dimen.sesl_fast_scroller_track_bottom_padding);
        mAdditionalBottomPadding = 0;
        mAdditionalTopPadding = 0;
        mImmersiveBottomPadding = 0;
        mIsDexMode = SeslConfigurationReflector.isDexEnabled(resources.getConfiguration());

        mPrimaryText.setPadding(mPreviewPadding, 0, mPreviewPadding, 0);
        mSecondaryText.setPadding(mPreviewPadding, 0, mPreviewPadding, 0);

        getSectionsFromIndexer();
        updateLongList(mOldChildCount, mOldItemCount);
        setScrollbarPosition(listView.getVerticalScrollbarPosition());
        postAutoHide();

        mVibrateIndex = SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(FASTSCROLL_VIBRATE_INDEX);
    }

    private void updateAppearance() {
        TypedValue outValue = new TypedValue();
        mContext.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, outValue, true);

        mColorPrimary = getColorWithAlpha(mContext.getResources().getColor(outValue.resourceId), 0.9f);

        mThumbBackgroundColor = mContext.getResources().getColor(androidx.appcompat.R.color.sesl_fast_scrollbar_bg_color);

        int width = 0;

        // Add track to overlay if it has an image.
        mTrackImage.setImageDrawable(mTrackDrawable);
        if (mTrackDrawable != null) {
            width = Math.max(width, mTrackDrawable.getIntrinsicWidth());
        }

        // Add thumb to overlay if it has an image.
        if (mThumbDrawable != null) {
            DrawableCompat.setTint(mThumbDrawable, mThumbBackgroundColor);
        }
        mThumbImage.setImageDrawable(mThumbDrawable);
        mThumbImage.setMinimumWidth(mThumbMinWidth);
        mThumbImage.setMinimumHeight(mThumbMinHeight);
        if (mThumbDrawable != null) {
            width = Math.max(width, mThumbDrawable.getIntrinsicWidth());
        }

        // Account for minimum thumb width.
        mWidth = Math.max(width, mThumbMinWidth);

        mPreviewImage.setMinimumWidth(mPreviewMinWidth);
        mPreviewImage.setMinimumHeight(mPreviewMinHeight);

        if (mTextAppearance != 0) {
            mPrimaryText.setTextAppearance(mContext, mTextAppearance);
            mSecondaryText.setTextAppearance(mContext, mTextAppearance);
        }

        if (mTextColor != null) {
            mPrimaryText.setTextColor(mTextColor);
            mSecondaryText.setTextColor(mTextColor);
        }

        if (mTextSize > 0) {
            mPrimaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            mSecondaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        }

        final int minHeight = Math.max(0, mPreviewMinHeight);
        mPrimaryText.setMinimumWidth(mPreviewMinWidth);
        mPrimaryText.setMinimumHeight(minHeight);
        mPrimaryText.setIncludeFontPadding(false);
        mSecondaryText.setMinimumWidth(mPreviewMinWidth);
        mSecondaryText.setMinimumHeight(minHeight);
        mSecondaryText.setIncludeFontPadding(false);

        refreshDrawablePressedState();
    }

    /**
     * Removes this FastScroller overlay from the host view.
     */
    public void remove() {
        mOverlay.remove(mTrackImage);
        mOverlay.remove(mThumbImage);
        mOverlay.remove(mPreviewImage);
        mOverlay.remove(mPrimaryText);
        mOverlay.remove(mSecondaryText);
    }

    /**
     * @param enabled Whether the fast scroll thumb is enabled.
     */
    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;

            onStateDependencyChanged(true);
        }
    }

    /**
     * @return Whether the fast scroll thumb is enabled.
     */
    public boolean isEnabled() {
        if (mEnabled && !mLongList) {
            mLongList = canScrollList(1) || canScrollList(-1);
        }
        return mEnabled && (mLongList || mAlwaysShow);
    }

    /**
     * @param alwaysShow Whether the fast scroll thumb should always be shown
     */
    public void setAlwaysShow(boolean alwaysShow) {
        if (mAlwaysShow != alwaysShow) {
            mAlwaysShow = alwaysShow;

            onStateDependencyChanged(false);
        }
    }

    /**
     * @return Whether the fast scroll thumb will always be shown
     * @see #setAlwaysShow(boolean)
     */
    public boolean isAlwaysShowEnabled() {
        return mAlwaysShow;
    }

    /**
     * Called when one of the variables affecting enabled state changes.
     *
     * @param peekIfEnabled whether the thumb should peek, if enabled
     */
    private void onStateDependencyChanged(boolean peekIfEnabled) {
        if (isEnabled()) {
            if (isAlwaysShowEnabled()) {
                setState(STATE_VISIBLE);
            } else if (mState == STATE_VISIBLE) {
                postAutoHide();
            } else if (peekIfEnabled) {
                setState(STATE_VISIBLE);
                postAutoHide();
            }
        } else {
            stop();
        }
    }

    public void setScrollBarStyle(int style) {
        if (mScrollBarStyle != style) {
            mScrollBarStyle = style;

            updateLayout();
        }
    }

    /**
     * Immediately transitions the fast scroller decorations to a hidden state.
     */
    public void stop() {
        setState(STATE_NONE);
    }

    public void setScrollbarPosition(int position) {
        if (position == View.SCROLLBAR_POSITION_DEFAULT) {
            position = mRecyclerView.getLayoutManager().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ?
                    View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT;
        }

        if (mScrollbarPosition != position) {
            mScrollbarPosition = position;
            mLayoutFromRight = position != View.SCROLLBAR_POSITION_LEFT;

            final int previewResId = mPreviewResId[mLayoutFromRight ? PREVIEW_RIGHT : PREVIEW_LEFT];
            mPreviewImage.setBackgroundResource(previewResId);
            DrawableCompat.setTintMode(mPreviewImage.getBackground(), PorterDuff.Mode.MULTIPLY);
            DrawableCompat.setTintList(mPreviewImage.getBackground(), ColorStateList.valueOf(mColorPrimary));

            // Requires re-layout.
            updateLayout();
        }
    }

    private int getColorWithAlpha(int color, float ratio) {
        int newColor;
        int alpha = Math.round(Color.alpha(color) * ratio);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        newColor = Color.argb(alpha, r, g, b);
        return newColor;
    }

    public int getWidth() {
        return mWidth;
    }

    int getEffectState() {
        return mEffectState;
    }

    float getScrollY() {
        return mScrollY;
    }

    void setThreshold(float threshold) {
        Log.d(TAG, "FastScroller setThreshold called = " + threshold);
        mThreshold = threshold;
    }

    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        mLongList = canScrollList(1) || canScrollList(-1);
        updateLayout();
    }

    public void onItemCountChanged(int childCount, int itemCount) {
        if (mOldChildCount == 0) {
            mOldChildCount = mRecyclerView.getChildCount();
        }
        if (mOldItemCount != itemCount || mOldChildCount != childCount) {
            mOldItemCount = itemCount;
            mOldChildCount = childCount;

            final boolean hasMoreItems = itemCount - childCount > 0;
            if (hasMoreItems && mState != STATE_DRAGGING) {
                final int firstVisibleItem = mRecyclerView.findFirstVisibleItemPosition();
                setThumbPos(getPosFromItemCount(firstVisibleItem, childCount, itemCount));
            }

            updateLongList(childCount, itemCount);
        }
    }

    private void updateLongList(int childCount, int itemCount) {
        final boolean longList = childCount > 0 && (canScrollList(1) || canScrollList(-1));
        if (mLongList != longList) {
            mLongList = longList;

            onStateDependencyChanged(true);
        }
    }

    /**
     * Creates a view into which preview text can be placed.
     */
    private TextView createPreviewTextView(Context context) {
        final LayoutParams params = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        final TextView textView = new TextView(context);
        textView.setLayoutParams(params);
        textView.setSingleLine(true);
        textView.setEllipsize(TruncateAt.MIDDLE);
        textView.setGravity(Gravity.CENTER);
        textView.setAlpha(0f);

        // Manually propagate inherited layout direction.
        textView.setLayoutDirection(mRecyclerView.getLayoutDirection());

        return textView;
    }

    /**
     * Measures and layouts the scrollbar and decorations.
     */
    public void updateLayout() {
        // Prevent re-entry when RTL properties change as a side-effect of
        // resolving padding.
        if (mUpdatingLayout) {
            return;
        }

        mUpdatingLayout = true;

        updateContainerRect();

        layoutThumb();
        layoutTrack();

        updateOffsetAndRange();

        mUpdatingLayout = false;

        final Rect bounds = mTempBounds;
        measurePreview(mPrimaryText, bounds);
        applyLayout(mPrimaryText, bounds);
        measurePreview(mSecondaryText, bounds);
        applyLayout(mSecondaryText, bounds);

        // Apply preview image padding.
        bounds.left -= mPreviewImage.getPaddingLeft();
        bounds.top -= mPreviewImage.getPaddingTop();
        bounds.right += mPreviewImage.getPaddingRight();
        bounds.bottom += mPreviewImage.getPaddingBottom();
        applyLayout(mPreviewImage, bounds);
    }

    /**
     * Layouts a view within the specified bounds and pins the pivot point to
     * the appropriate edge.
     *
     * @param view The view to layout.
     * @param bounds Bounds at which to layout the view.
     */
    private void applyLayout(View view, Rect bounds) {
        view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
        view.setPivotX(mLayoutFromRight ? bounds.right - bounds.left : 0);
    }

    /**
     * Measures the preview text bounds, taking preview image padding into
     * account. This method should only be called after {@link #layoutThumb()}
     * and {@link #layoutTrack()} have both been called at least once.
     *
     * @param v The preview text view to measure.
     * @param out Rectangle into which measured bounds are placed.
     */
    private void measurePreview(View v, Rect out) {
        // Apply the preview image's padding as layout margins.
        final Rect margins = mTempMargins;
        margins.left = mPreviewImage.getPaddingLeft();
        margins.top = mPreviewImage.getPaddingTop();
        margins.right = mPreviewImage.getPaddingRight();
        margins.bottom = mPreviewImage.getPaddingBottom();

        if (mOverlayPosition == OVERLAY_FLOATING) {
            measureFloating(v, margins, out);
        } else {
            measureViewToSide(v, mThumbImage, margins, out);
        }
    }

    /**
     * Measures the bounds for a view that should be laid out against the edge
     * of an adjacent view. If no adjacent view is provided, lays out against
     * the list edge.
     *
     * @param view The view to measure for layout.
     * @param adjacent (Optional) The adjacent view, may be null to align to the
     *            list edge.
     * @param margins Layout margins to apply to the view.
     * @param out Rectangle into which measured bounds are placed.
     */
    private void measureViewToSide(View view, View adjacent, Rect margins, Rect out) {
        final int marginLeft;
        final int marginTop = 0;
        final int marginRight;
        if (mLayoutFromRight) {
            if (adjacent == null) {
                marginRight = mThumbMarginEnd;
            } else {
                marginRight = mPreviewMarginEnd;
            }
            marginLeft = 0;
        } else {
            if (adjacent == null) {
                marginLeft = mThumbMarginEnd;
            } else {
                marginLeft = mPreviewMarginEnd;
            }
            marginRight = 0;
        }

        final Rect container = mContainerRect;
        final int containerWidth = container.width();
        final int maxWidth;
        if (adjacent == null) {
            maxWidth = containerWidth;
        } else if (mLayoutFromRight) {
            maxWidth = adjacent.getLeft();
        } else {
            maxWidth = containerWidth - adjacent.getRight();
        }

        final int adjMaxHeight = Math.max(0, container.height());
        final int adjMaxWidth = Math.max(0, maxWidth - marginLeft - marginRight);
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(adjMaxWidth, MeasureSpec.AT_MOST);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(adjMaxHeight), MeasureSpec.UNSPECIFIED);
        view.measure(widthMeasureSpec, heightMeasureSpec);

        // Align to the left or right.
        final int width = Math.min(adjMaxWidth, view.getMeasuredWidth());
        final int left;
        final int right;
        if (mLayoutFromRight) {
            right = (adjacent == null ? container.right : adjacent.getLeft()) - marginRight;
            left = right - width;
        } else {
            left = (adjacent == null ? container.left : adjacent.getRight()) + marginLeft;
            right = left + width;
        }

        // Don't adjust the vertical position.
        final int bottom = marginTop + view.getMeasuredHeight();
        out.set(left, 0, right, bottom);
    }

    private void measureFloating(View preview, Rect margins, Rect out) {
        final int marginLeft;
        final int marginTop;
        final int marginRight;
        if (margins == null) {
            marginLeft = 0;
            marginTop = 0;
            marginRight = 0;
        } else {
            marginLeft = margins.left;
            marginTop = margins.top;
            marginRight = margins.right;
        }

        final Rect container = mContainerRect;
        final int containerWidth = container.width();
        final int adjMaxHeight = Math.max(0, container.height());
        final int adjMaxWidth = Math.max(0, containerWidth - marginLeft - marginRight);
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(adjMaxWidth, MeasureSpec.AT_MOST);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(adjMaxHeight), MeasureSpec.UNSPECIFIED);
        preview.measure(widthMeasureSpec, heightMeasureSpec);

        // Align at the vertical center, 10% from the top.
        final int containerHeight = container.height();
        final int width = preview.getMeasuredWidth();
        final int top = containerHeight / 10 + marginTop + container.top;
        final int bottom = top + preview.getMeasuredHeight();
        final int left = (containerWidth - width) / 2 + container.left;
        final int right = left + width;
        out.set(left, top, right, bottom);
    }

    /**
     * Updates the container rectangle used for layout.
     */
    private void updateContainerRect() {
        final RecyclerView list = mRecyclerView;

        final Rect container = mContainerRect;
        container.left = 0;
        container.top = 0;
        container.right = list.getWidth();
        container.bottom = list.getHeight();

        final int scrollbarStyle = mScrollBarStyle;
        if (scrollbarStyle == View.SCROLLBARS_INSIDE_INSET
                || scrollbarStyle == View.SCROLLBARS_INSIDE_OVERLAY) {
            container.left += list.getPaddingLeft();
            container.top += list.getPaddingTop();
            container.right -= list.getPaddingRight();
            container.bottom -= list.getPaddingBottom();

            // In inset mode, we need to adjust for padded scrollbar width.
            if (scrollbarStyle == View.SCROLLBARS_INSIDE_INSET) {
                final int width = getWidth();
                if (mScrollbarPosition == View.SCROLLBAR_POSITION_RIGHT) {
                    container.right += width;
                } else {
                    container.left -= width;
                }
            }
        }
    }

    public void setImmersiveBottomPadding(int bottom) {
        mImmersiveBottomPadding = bottom;
        updateOffsetAndRange();
    }

    public void setAdditionalPadding(int top, int bottom) {
        mAdditionalTopPadding = top;
        mAdditionalBottomPadding = bottom;
        updateLayout();
    }

    /**
     * Lays out the thumb according to the current scrollbar position.
     */
    private void layoutThumb() {
        final Rect bounds = mTempBounds;
        measureViewToSide(mThumbImage, null, null, bounds);
        applyLayout(mThumbImage, bounds);
    }

    /**
     * Lays out the track centered on the thumb. Must be called after
     * {@link #layoutThumb}.
     */
    private void layoutTrack() {
        final View track = mTrackImage;
        final View thumb = mThumbImage;
        final Rect container = mContainerRect;
        final int maxWidth = Math.max(0, container.width());
        final int maxHeight = Math.max(0, container.height());
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(maxHeight), MeasureSpec.UNSPECIFIED);
        track.measure(widthMeasureSpec, heightMeasureSpec);

        final int top;
        int bottom;
        if (mThumbPosition == THUMB_POSITION_INSIDE) {
            top = container.top + mTrackTopPadding + mAdditionalTopPadding;
            bottom = container.bottom - mTrackBottomPadding - mAdditionalBottomPadding;
        } else {
            final int thumbHalfHeight = thumb.getHeight() / 2;
            top = container.top + thumbHalfHeight + mTrackTopPadding + mAdditionalTopPadding;
            bottom = container.bottom - thumbHalfHeight - mTrackBottomPadding - mAdditionalBottomPadding;
        }

        if (bottom < top) {
            Log.e(TAG, "Error occured during layoutTrack() because bottom["
                    + bottom + "] is less than top[" + top + "].");
            bottom = top;
        }

        final int trackWidth = track.getMeasuredWidth();
        final int left = thumb.getLeft() + (thumb.getWidth() - trackWidth) / 2;
        final int right = left + trackWidth;
        track.layout(left, top, right, bottom);
    }

    /**
     * Updates the offset and range used to convert from absolute y-position to
     * thumb position within the track.
     */
    private void updateOffsetAndRange() {
        final View trackImage = mTrackImage;
        final View thumbImage = mThumbImage;
        final float min;
        final float max;
        if (mThumbPosition == THUMB_POSITION_INSIDE) {
            final float halfThumbHeight = thumbImage.getHeight() / 2f;
            min = trackImage.getTop() + halfThumbHeight;
            max = trackImage.getBottom() - halfThumbHeight;
        } else{
            min = trackImage.getTop();
            max = trackImage.getBottom();
        }

        mThumbOffset = min;
        mThumbRange = max - min - mImmersiveBottomPadding;
        if (mThumbRange < 0.0f) {
            mThumbRange = 0.0f;
        }
    }

    private void setState(int state) {
        mRecyclerView.removeCallbacks(mDeferHide);

        if (mAlwaysShow && state == STATE_NONE) {
            state = STATE_VISIBLE;
        }

        if (state == mState) {
            return;
        }

        switch (state) {
            case STATE_NONE:
                transitionToHidden();
                break;
            case STATE_VISIBLE:
                if (mThumbDrawable != null) {
                    DrawableCompat.setTint(mThumbDrawable, mThumbBackgroundColor);
                }
                transitionToVisible();
                break;
            case STATE_DRAGGING:
                if (mThumbDrawable != null) {
                    DrawableCompat.setTint(mThumbDrawable, mColorPrimary);
                }
                transitionPreviewLayout(mCurrentSection);
                break;
        }

        mState = state;

        refreshDrawablePressedState();
    }

    private void refreshDrawablePressedState() {
        final boolean isPressed = mState == STATE_DRAGGING;
        mThumbImage.setPressed(isPressed);
        mTrackImage.setPressed(isPressed);
    }

    /**
     * Shows nothing.
     */
    private void transitionToHidden() {
        mShowingPreview = false;
        mCurrentSection = -1;

        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }

        final Animator fadeOut = groupAnimatorOfFloat(View.ALPHA, 0f, mThumbImage, mTrackImage,
                mPreviewImage, mPrimaryText, mSecondaryText)
                .setDuration(mDecorAnimation != null ? DURATION_FADE_OUT : 0);

        mDecorAnimation = new AnimatorSet();
        mDecorAnimation.playTogether(fadeOut);
        mDecorAnimation.setInterpolator(LINEAR_INTERPOLATOR);
        mDecorAnimation.start();
    }

    /**
     * Shows the thumb and track.
     */
    private void transitionToVisible() {
        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }

        final Animator fadeIn = groupAnimatorOfFloat(View.ALPHA, 1f, mThumbImage, mTrackImage)
                .setDuration(DURATION_FADE_IN);
        final Animator fadeOut = groupAnimatorOfFloat(
                View.ALPHA, 0f, mPreviewImage, mPrimaryText, mSecondaryText)
                .setDuration(DURATION_FADE_OUT);

        mDecorAnimation = new AnimatorSet();
        mDecorAnimation.playTogether(fadeIn, fadeOut);
        mDecorAnimation.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_70);

        mShowingPreview = false;

        mDecorAnimation.start();
    }

    /**
     * Shows the thumb, preview, and track.
     */
    private void transitionToDragging() {
        if (mDecorAnimation != null) {
            mDecorAnimation.cancel();
        }

        final Animator fadeIn = groupAnimatorOfFloat(
                View.ALPHA, 1f, mThumbImage, mTrackImage, mPreviewImage)
                .setDuration(DURATION_FADE_IN);

        mDecorAnimation = new AnimatorSet();
        mDecorAnimation.playTogether(fadeIn);
        mDecorAnimation.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_70);
        mDecorAnimation.start();

        mShowingPreview = true;
    }

    private void postAutoHide() {
        mRecyclerView.removeCallbacks(mDeferHide);
        mRecyclerView.postDelayed(mDeferHide, FADE_TIMEOUT);
    }

    public boolean canScrollList(int direction) {
        final int childCount = mRecyclerView.getChildCount();
        if (childCount == 0) {
            return false;
        }
        final int firstPosition = mRecyclerView.findFirstVisibleItemPosition();
        final Rect listPadding = mRecyclerView.mListPadding;
        if (direction > 0) {
            final int lastBottom = mRecyclerView.getChildAt(childCount - 1).getBottom();
            final int lastPosition = firstPosition + childCount;
            return lastPosition < mRecyclerView.getAdapter().getItemCount()
                    || lastBottom > mRecyclerView.getHeight() - listPadding.bottom;
        } else {
            final int firstTop = mRecyclerView.getChildAt(0).getTop();
            return firstPosition > 0 || firstTop < listPadding.top;
        }
    }

    public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (!isEnabled()) {
            setState(STATE_NONE);
            return;
        }

        final boolean canScrollList = (canScrollList(1) || canScrollList(-1));
        if (canScrollList && mState != STATE_DRAGGING) {
            if (mOldThumbPosition != -1f) {
                setThumbPos(mOldThumbPosition);
                mOldThumbPosition = -1f;
            } else {
                setThumbPos(getPosFromItemCount(firstVisibleItem, visibleItemCount, totalItemCount));
            }
        }

        mScrollCompleted = true;

        // Show the thumb, if necessary, and set up auto-fade.
        if (mState != STATE_DRAGGING) {
            setState(STATE_VISIBLE);
            postAutoHide();
        }
    }

    private void getSectionsFromIndexer() {
        mSectionIndexer = null;

        RecyclerView.Adapter<?> adapter = mRecyclerView.getAdapter();
        if (adapter instanceof SectionIndexer) {
            mListAdapter = adapter;
            mSectionIndexer = (SectionIndexer) adapter;
            mSections = mSectionIndexer.getSections();
        } else {
            mListAdapter = adapter;
            mSections = null;
        }
    }

    public void onSectionsChanged() {
        mListAdapter = null;
    }

    /**
     * Scrolls to a specific position within the section
     * @param position
     */
    private void scrollTo(float position) {
        mScrollCompleted = false;

        final int count = mRecyclerView.getAdapter().getItemCount();
        final Object[] sections = mSections;
        final int sectionCount = sections == null ? 0 : sections.length;
        int sectionIndex;
        RecyclerView.LayoutManager layoutManager = this.mRecyclerView.getLayoutManager();
        if (sections != null && sectionCount > 0) {
            final int exactSection = MathUtils.clamp(
                    (int) (position * sectionCount), 0, sectionCount - 1);
            int targetSection = exactSection;
            int targetIndex = mSectionIndexer.getPositionForSection(targetSection);
            sectionIndex = targetSection;

            // Given the expected section and index, the following code will
            // try to account for missing sections (no names starting with..)
            // It will compute the scroll space of surrounding empty sections
            // and interpolate the currently visible letter's range across the
            // available space, so that there is always some list movement while
            // the user moves the thumb.
            int nextIndex = count;
            int prevIndex = targetIndex;
            int prevSection = targetSection;
            int nextSection = targetSection + 1;

            // Assume the next section is unique
            if (targetSection < sectionCount - 1) {
                nextIndex = mSectionIndexer.getPositionForSection(targetSection + 1);
            }

            // Find the previous index if we're slicing the previous section
            if (nextIndex == targetIndex) {
                // Non-existent letter
                while (targetSection > 0) {
                    targetSection--;
                    prevIndex = mSectionIndexer.getPositionForSection(targetSection);
                    if (prevIndex != targetIndex) {
                        prevSection = targetSection;
                        sectionIndex = targetSection;
                        break;
                    } else if (targetSection == 0) {
                        // When section reaches 0 here, sectionIndex must follow it.
                        // Assuming mSectionIndexer.getPositionForSection(0) == 0.
                        sectionIndex = 0;
                        break;
                    }
                }
            }

            // Find the next index, in case the assumed next index is not
            // unique. For instance, if there is no P, then request for P's
            // position actually returns Q's. So we need to look ahead to make
            // sure that there is really a Q at Q's position. If not, move
            // further down...
            int nextNextSection = nextSection + 1;
            while (nextNextSection < sectionCount &&
                    mSectionIndexer.getPositionForSection(nextNextSection) == nextIndex) {
                nextNextSection++;
                nextSection++;
            }

            // Compute the beginning and ending scroll range percentage of the
            // currently visible section. This could be equal to or greater than
            // (1 / nSections). If the target position is near the previous
            // position, snap to the previous position.
            final float prevPosition = (float) prevSection / sectionCount;
            final float nextPosition = (float) nextSection / sectionCount;
            final float snapThreshold = (count == 0) ? Float.MAX_VALUE : .125f / count;
            if (prevSection == exactSection && position - prevPosition < snapThreshold) {
                targetIndex = prevIndex;
            } else {
                targetIndex = prevIndex + (int) ((nextIndex - prevIndex) * (position - prevPosition)
                        / (nextPosition - prevPosition));
            }

            // Clamp to valid positions.
            targetIndex = MathUtils.clamp(targetIndex, 0, count - 1);

            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(targetIndex, 0);
            } else {
                ((StaggeredGridLayoutManager) layoutManager).scrollToPositionWithOffset(targetIndex, 0, true);
            }
        } else {
            final int index = MathUtils.clamp((int) (position * count), 0, count - 1);

            sectionIndex = -1;

            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(index, 0);
            } else {
                ((StaggeredGridLayoutManager) layoutManager).scrollToPositionWithOffset(index, 0, true);
            }
        }

        onScroll(mRecyclerView.findFirstVisibleItemPosition(),
                mRecyclerView.getChildCount(), mRecyclerView.getAdapter().getItemCount());

        mCurrentSection = sectionIndex;

        final boolean hasPreview = transitionPreviewLayout(sectionIndex);
        Log.d(TAG, "scrollTo() called transitionPreviewLayout() sectionIndex ="
                + sectionIndex + ", position = " + position);
        if (!mShowingPreview && hasPreview) {
            transitionToDragging();
        } else if (mShowingPreview && !hasPreview) {
            transitionToVisible();
        }
    }

    /**
     * Transitions the preview text to a new section. Handles animation,
     * measurement, and layout. If the new preview text is empty, returns false.
     *
     * @param sectionIndex The section index to which the preview should
     *            transition.
     * @return False if the new preview text is empty.
     */
    private boolean transitionPreviewLayout(int sectionIndex) {
        final Object[] sections = mSections;
        String text = null;
        if (sections != null && sectionIndex >= 0 && sectionIndex < sections.length) {
            final Object section = sections[sectionIndex];
            if (section != null) {
                text = section.toString();
            }
        }

        final Rect bounds = mTempBounds;
        final View preview = mPreviewImage;
        final TextView showing;
        final TextView target;
        if (mShowingPrimary) {
            showing = mPrimaryText;
            target = mSecondaryText;
        } else {
            showing = mSecondaryText;
            target = mPrimaryText;
        }

        // Set and layout target immediately.
        target.setText(text);
        measurePreview(target, bounds);
        applyLayout(target, bounds);

        if (mState == STATE_VISIBLE) {
            showing.setText("");
        } else if (mState == STATE_DRAGGING && showing.getText().equals(text)) {
            return !TextUtils.isEmpty(text);
        }

        if (mPreviewAnimation != null) {
            mPreviewAnimation.cancel();
        }

        if (!showing.getText().equals("")) {
            mRecyclerView.performHapticFeedback(mVibrateIndex);
        }

        // Cross-fade preview text.
        final Animator showTarget = animateAlpha(target, 1f).setDuration(DURATION_CROSS_FADE);
        final Animator hideShowing = animateAlpha(showing, 0f).setDuration(DURATION_CROSS_FADE);
        hideShowing.addListener(mSwitchPrimaryListener);

        // Apply preview image padding and animate bounds, if necessary.
        bounds.left -= preview.getPaddingLeft();
        bounds.top -= preview.getPaddingTop();
        bounds.right += preview.getPaddingRight();
        bounds.bottom += preview.getPaddingBottom();
        final Animator resizePreview = animateBounds(preview, bounds);
        resizePreview.setDuration(DURATION_RESIZE);

        mPreviewAnimation = new AnimatorSet();
        final AnimatorSet.Builder builder = mPreviewAnimation.play(hideShowing).with(showTarget);
        builder.with(resizePreview);

        // The current preview size is unaffected by hidden or showing. It's
        // used to set starting scales for things that need to be scaled down.
        final int previewWidth = preview.getWidth() - preview.getPaddingLeft()
                - preview.getPaddingRight();

        // If target is too large, shrink it immediately to fit and expand to
        // target size. Otherwise, start at target size.
        final int targetWidth = target.getWidth();
        if (targetWidth > previewWidth) {
            target.setScaleX((float) previewWidth / targetWidth);
            final Animator scaleAnim = animateScaleX(target, 1f).setDuration(DURATION_RESIZE);
            builder.with(scaleAnim);
        } else {
            target.setScaleX(1f);
        }

        // If showing is larger than target, shrink to target size.
        final int showingWidth = showing.getWidth();
        if (showingWidth > targetWidth) {
            final float scale = (float) targetWidth / showingWidth;
            final Animator scaleAnim = animateScaleX(showing, scale).setDuration(DURATION_RESIZE);
            builder.with(scaleAnim);
        }

        mPreviewAnimation.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_70);
        mPreviewAnimation.start();

        return !TextUtils.isEmpty(text);
    }

    /**
     * Positions the thumb and preview widgets.
     *
     * @param position The position, between 0 and 1, along the track at which
     *            to place the thumb.
     */
    private void setThumbPos(float position) {
        final Rect container = mContainerRect;
        final int top = container.top;
        final int bottom = container.bottom;

        if (position > 1.0f) {
            position = 1.0f;
        } else if (position < 0.0f) {
            position = 0.0f;
        }

        final float thumbMiddle = position * mThumbRange + mThumbOffset;
        mThumbImage.setTranslationY(thumbMiddle - mThumbImage.getHeight() / 2f);

        final View previewImage = mPreviewImage;
        final float previewHalfHeight = previewImage.getHeight() / 2f;
        final float minP = top + previewHalfHeight;
        final float maxP = bottom - previewHalfHeight;
        final float previewMiddle = MathUtils.clamp(thumbMiddle, minP, maxP);
        final float previewTop = previewMiddle - previewHalfHeight;
        previewImage.setTranslationY(previewTop);

        mPrimaryText.setTranslationY(previewTop);
        mSecondaryText.setTranslationY(previewTop);
    }

    private float getPosFromMotionEvent(float y) {
        // If the list is the same height as the thumbnail or shorter,
        // effectively disable scrolling.
        if (mThumbRange <= 0) {
            return 0f;
        }

        return MathUtils.clamp((y - mThumbOffset) / mThumbRange, 0f, 1f);
    }

    /**
     * Calculates the thumb position based on the visible items.
     *
     * @param firstVisibleItem First visible item, >= 0.
     * @param visibleItemCount Number of visible items, >= 0.
     * @param totalItemCount Total number of items, >= 0.
     * @return float position
     */
    private float getPosFromItemCount(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mSectionIndexer == null || mListAdapter == null) {
            getSectionsFromIndexer();
        }

        RecyclerView.LayoutManager layoutManager = this.mRecyclerView.getLayoutManager();

        float percentage = 0.0F;
        if (visibleItemCount != 0 && totalItemCount != 0) {
            SectionIndexer sectionIndexer = mSectionIndexer;
            int topPadding = mRecyclerView.getPaddingTop();
            int startPosition = firstVisibleItem;
            if (topPadding > 0) {
                if (layoutManager instanceof LinearLayoutManager) {
                    while(startPosition > 0 && layoutManager.findViewByPosition(startPosition - 1) != null) {
                        startPosition--;
                    }
                }
            }

            View firstChild = mRecyclerView.getChildAt(0);
            float position;
            int firstChildHeight;
            if (firstChild != null && (firstChildHeight = firstChild.getHeight()) != 0) {
                if (startPosition == 0) {
                    position = (float)(topPadding - firstChild.getTop()) / (float)(firstChildHeight + topPadding);
                } else {
                    position = (float)(-firstChild.getTop()) / (float)firstChildHeight;
                }
            } else {
                position = 0.0F;
            }

            boolean hasSections;
            checkHasSections: {
                if (sectionIndexer != null) {
                    Object[] sections = mSections;
                    if (sections != null && sections.length > 0) {
                        hasSections = true;
                        break checkHasSections;
                    }
                }

                hasSections = false;
            }

            if (hasSections && mMatchDragPosition) {
                if (startPosition < 0) {
                    return 0.0F;
                }

                int section = sectionIndexer.getSectionForPosition(startPosition);
                int sectionStartPosition = sectionIndexer.getPositionForSection(section);
                int sectionCount = mSections.length;
                if (section < sectionCount - 1) {
                    firstVisibleItem = section + 1;
                    if (firstVisibleItem < sectionCount) {
                        firstVisibleItem = sectionIndexer.getPositionForSection(firstVisibleItem);
                    } else {
                        firstVisibleItem = totalItemCount - 1;
                    }

                    firstVisibleItem -= sectionStartPosition;
                } else {
                    firstVisibleItem = totalItemCount - sectionStartPosition;
                }

                if (firstVisibleItem == 0) {
                    position = percentage;
                } else {
                    position = ((float)startPosition + position - (float)sectionStartPosition) / (float)firstVisibleItem;
                }

                percentage = (float)section + position;
                position = (float)sectionCount;
            } else {
                if (visibleItemCount == totalItemCount) {
                    if (layoutManager instanceof StaggeredGridLayoutManager
                            && startPosition != 0 && firstChild != null
                            && ((StaggeredGridLayoutManager.LayoutParams)firstChild.getLayoutParams()).isFullSpan()
                    ) {
                        return 1.0F;
                    }

                    return 0.0F;
                }

                int spanCount;
                if (layoutManager instanceof GridLayoutManager) {
                    spanCount = ((GridLayoutManager)layoutManager).getSpanCount();
                    int spanSize = ((GridLayoutManager)layoutManager).getSpanSizeLookup().getSpanSize(startPosition);
                    firstVisibleItem = spanCount / spanSize;
                } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                    spanCount = ((StaggeredGridLayoutManager)layoutManager).getSpanCount();
                    firstVisibleItem = spanCount;
                } else {
                    firstVisibleItem = 1;
                }

                percentage = (float)startPosition + position * (float)firstVisibleItem;
                position = (float)totalItemCount;
            }

            float itemSize = percentage / position;
            if (startPosition + visibleItemCount == totalItemCount) {
                View lastChild = mRecyclerView.getChildAt(visibleItemCount - 1);
                View firstVisibleChild = mRecyclerView.getChildAt(0);
                int visibleHeight = lastChild.getBottom() - mRecyclerView.getHeight() + mRecyclerView.getPaddingBottom();
                int firstVisibleItemHeight = visibleHeight - (firstVisibleChild.getTop() - mRecyclerView.getPaddingTop());
                if (firstVisibleItemHeight > lastChild.getHeight() || startPosition > 0) {
                    firstVisibleItemHeight = lastChild.getHeight();
                }

                visibleHeight = firstVisibleItemHeight - visibleHeight;
                if (visibleHeight > 0 && firstVisibleItemHeight > 0) {
                    position = itemSize + (1.0F - itemSize) * ((float)visibleHeight / (float)firstVisibleItemHeight);
                }
            }else{
                position = itemSize;
            }

            return position;
        } else {
            return 0.0F;
        }
    }

    /**
     * Cancels an ongoing fling event by injecting a
     * {@link MotionEvent#ACTION_CANCEL} into the host view.
     */
    private void cancelFling() {
        final MotionEvent cancelFling = MotionEvent.obtain(
                0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mRecyclerView.onTouchEvent(cancelFling);
        cancelFling.recycle();
    }

    /**
     * Cancels a pending drag.
     *
     * @see #startPendingDrag()
     */
    private void cancelPendingDrag() {
        mPendingDrag = -1;
    }

    /**
     * Delays dragging until after the framework has determined that the user is
     * scrolling, rather than tapping.
     */
    private void startPendingDrag() {
        mPendingDrag = SystemClock.uptimeMillis() + TAP_TIMEOUT;
    }

    private void beginDrag() {
        mPendingDrag = -1;

        if (mListAdapter == null) {
            getSectionsFromIndexer();
        }

        mRecyclerView.requestDisallowInterceptTouchEvent(true);

        cancelFling();

        setState(STATE_DRAGGING);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isPointInside(ev.getX(), ev.getY())) {
                    // If the parent has requested that its children delay
                    // pressed state (e.g. is a scrolling container) then we
                    // need to allow the parent time to decide whether it wants
                    // to intercept events. If it does, we will receive a CANCEL
                    // event.
                    mRecyclerView.performHapticFeedback(mVibrateIndex);
                    if (!mRecyclerView.isInScrollingContainer() || mIsDexMode) {
                        // This will get dispatched to onTouchEvent(). Start
                        // dragging there.
                        return true;
                    }

                    mInitialTouchY = ev.getY();
                    startPendingDrag();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isPointInside(ev.getX(), ev.getY())) {
                    cancelPendingDrag();
                } else if (mPendingDrag >= 0 && mPendingDrag <= SystemClock.uptimeMillis()) {
                    beginDrag();

                    final float pos = getPosFromMotionEvent(mInitialTouchY);
                    mOldThumbPosition = pos;
                    scrollTo(pos);

                    // This may get dispatched to onTouchEvent(), but it
                    // doesn't really matter since we'll already be in a drag.
                    return onTouchEvent(ev);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPendingDrag();
                break;
        }

        return false;
    }

    public boolean onInterceptHoverEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }

        final int actionMasked = ev.getActionMasked();
        if ((actionMasked == MotionEvent.ACTION_HOVER_ENTER
                || actionMasked == MotionEvent.ACTION_HOVER_MOVE) && mState == STATE_NONE
                && isPointInside(ev.getX(), ev.getY())) {
            setState(STATE_VISIBLE);
            postAutoHide();
        }

        return false;
    }

    public boolean onTouchEvent(MotionEvent me) {
        final Rect container = mContainerRect;
        final int containerTop = container.top;
        final int containerBottom = container.bottom;
        final View trackImage = mTrackImage;
        final int trackTop = trackImage.getTop();
        final int trackBottom = trackImage.getBottom();

        mScrollY = me.getY();

        if (!isEnabled()) {
            return false;
        }

        switch (me.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (isPointInside(me.getX(), me.getY())) {
                    if (!mRecyclerView.isInScrollingContainer() || mIsDexMode) {
                        beginDrag();
                        mEffectState = EFFECT_STATE_OPEN;
                        return true;
                    }
                }
            } break;

            case MotionEvent.ACTION_UP: {
                if (mPendingDrag >= 0) {
                    // Allow a tap to scroll.
                    beginDrag();

                    final float pos = getPosFromMotionEvent(me.getY());
                    mOldThumbPosition = pos;
                    setThumbPos(pos);
                    scrollTo(pos);
                    mEffectState = EFFECT_STATE_OPEN;

                    // Will hit the STATE_DRAGGING check below
                }

                if (mState == STATE_DRAGGING) {
                    // ViewGroup does the right thing already, but there might
                    // be other classes that don't properly reset on touch-up,
                    // so do this explicitly just in case.
                    mRecyclerView.requestDisallowInterceptTouchEvent(false);

                    setState(STATE_VISIBLE);
                    postAutoHide();

                    mEffectState = EFFECT_STATE_CLOSE;
                    mScrollY = 0.0f;
                    return true;
                }
            } break;

            case MotionEvent.ACTION_MOVE: {
                if (mPendingDrag >= 0 && Math.abs(me.getY() - mInitialTouchY) > mScaledTouchSlop) {
                    beginDrag();

                    if (mScrollY > containerTop && mScrollY < containerBottom) {
                        float top = containerTop + trackTop;
                        if (mScrollY < top) {
                            mScrollY = top;
                        } else if (mScrollY > trackBottom) {
                            mScrollY = trackBottom;
                        }
                        mEffectState = EFFECT_STATE_OPEN;
                    }

                    // Will hit the STATE_DRAGGING check below
                }

                if (mState == STATE_DRAGGING) {
                    // TODO: Ignore jitter.
                    final float pos = getPosFromMotionEvent(me.getY());
                    mOldThumbPosition = pos;
                    setThumbPos(pos);

                    if (mThreshold != 0.0f && Math.abs(mLastDraggingY - mScrollY) <= mThreshold) {
                        return true;
                    }
                    mLastDraggingY = mScrollY;

                    // If the previous scrollTo is still pending
                    if (mScrollCompleted) {
                        scrollTo(pos);
                    }

                    if (mScrollY > containerTop && mScrollY < containerBottom) {
                        float top = containerTop + trackTop;
                        if (mScrollY < top) {
                            mScrollY = top;
                        } else if (mScrollY > trackBottom) {
                            mScrollY = trackBottom;
                        }
                        mEffectState = EFFECT_STATE_OPEN;
                    }

                    return true;
                }
            } break;

            case MotionEvent.ACTION_CANCEL: {
                cancelPendingDrag();

                if (mState == STATE_DRAGGING) {
                    setState(STATE_NONE);
                }
                mEffectState = EFFECT_STATE_CLOSE;
                mScrollY = 0.0f;
            } break;
        }

        return false;
    }

    /**
     * Returns whether a coordinate is inside the scroller's activation area. If
     * there is a track image, touching anywhere within the thumb-width of the
     * track activates scrolling. Otherwise, the user has to touch inside thumb
     * itself.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return Whether the coordinate is inside the scroller's activation area.
     */
    private boolean isPointInside(float x, float y) {
        return isPointInsideX(x) && isPointInsideY(y) && mState != STATE_NONE;
    }

    private boolean isPointInsideX(float x) {
        if (mLayoutFromRight) {
            return x >= mThumbImage.getLeft() - mAdditionalTouchArea;
        } else {
            return x <= mThumbImage.getRight() + mAdditionalTouchArea;
        }
    }

    private boolean isPointInsideY(float y) {
        final float offset = mThumbImage.getTranslationY();
        final float top = mThumbImage.getTop() + offset;
        final float bottom = mThumbImage.getBottom() + offset;

        return y >= top && y <= bottom;
    }

    /**
     * Constructs an animator for the specified property on a group of views.
     * See {@link ObjectAnimator#ofFloat(Object, String, float...)} for
     * implementation details.
     *
     * @param property The property being animated.
     * @param value The value to which that property should animate.
     * @param views The target views to animate.
     * @return An animator for all the specified views.
     */
    private static Animator groupAnimatorOfFloat(
            Property<View, Float> property, float value, View... views) {
        AnimatorSet animSet = new AnimatorSet();
        AnimatorSet.Builder builder = null;

        for (int i = views.length - 1; i >= 0; i--) {
            final Animator anim = ObjectAnimator.ofFloat(views[i], property, value);
            if (builder == null) {
                builder = animSet.play(anim);
            } else {
                builder.with(anim);
            }
        }

        return animSet;
    }

    /**
     * Returns an animator for the view's scaleX value.
     */
    private static Animator animateScaleX(View v, float target) {
        return ObjectAnimator.ofFloat(v, View.SCALE_X, target);
    }

    /**
     * Returns an animator for the view's alpha value.
     */
    private static Animator animateAlpha(View v, float alpha) {
        return ObjectAnimator.ofFloat(v, View.ALPHA, alpha);
    }

    /**
     * A Property wrapper around the <code>left</code> functionality handled by the
     * {@link View#setLeft(int)} and {@link View#getLeft()} methods.
     */
    private static Property<View, Integer> LEFT = new IntProperty<View>("left") {
        @Override
        public void setValue(View object, int value) {
            object.setLeft(value);
        }

        @Override
        public Integer get(View object) {
            return object.getLeft();
        }
    };

    /**
     * A Property wrapper around the <code>top</code> functionality handled by the
     * {@link View#setTop(int)} and {@link View#getTop()} methods.
     */
    private static Property<View, Integer> TOP = new IntProperty<View>("top") {
        @Override
        public void setValue(View object, int value) {
            object.setTop(value);
        }

        @Override
        public Integer get(View object) {
            return object.getTop();
        }
    };

    /**
     * A Property wrapper around the <code>right</code> functionality handled by the
     * {@link View#setRight(int)} and {@link View#getRight()} methods.
     */
    private static Property<View, Integer> RIGHT = new IntProperty<View>("right") {
        @Override
        public void setValue(View object, int value) {
            object.setRight(value);
        }

        @Override
        public Integer get(View object) {
            return object.getRight();
        }
    };

    /**
     * A Property wrapper around the <code>bottom</code> functionality handled by the
     * {@link View#setBottom(int)} and {@link View#getBottom()} methods.
     */
    private static Property<View, Integer> BOTTOM = new IntProperty<View>("bottom") {
        @Override
        public void setValue(View object, int value) {
            object.setBottom(value);
        }

        @Override
        public Integer get(View object) {
            return object.getBottom();
        }
    };

    /**
     * Returns an animator for the view's bounds.
     */
    private static Animator animateBounds(View v, Rect bounds) {
        final PropertyValuesHolder left = PropertyValuesHolder.ofInt(LEFT, bounds.left);
        final PropertyValuesHolder top = PropertyValuesHolder.ofInt(TOP, bounds.top);
        final PropertyValuesHolder right = PropertyValuesHolder.ofInt(RIGHT, bounds.right);
        final PropertyValuesHolder bottom = PropertyValuesHolder.ofInt(BOTTOM, bounds.bottom);
        return ObjectAnimator.ofPropertyValuesHolder(v, left, top, right, bottom);
    }


    /**
     * An implementation of {@link android.util.Property} to be used specifically with fields of type
     * <code>int</code>. This type-specific subclass enables performance benefit by allowing
     * calls to a {@link #setValue(Object, int) setValue()} function that takes the primitive
     * <code>int</code> type and avoids autoboxing and other overhead associated with the
     * <code>Integer</code> class.
     *
     * @param <T> The class on which the Property is declared.
     */
    public abstract static class IntProperty<T> extends Property<T, Integer> {

        public IntProperty(String name) {
            super(Integer.class, name);
        }

        /**
         * A type-specific variant of {@link #set(Object, Integer)} that is faster when dealing
         * with fields of type <code>int</code>.
         */
        public abstract void setValue(T object, int value);

        @Override
        final public void set(T object, Integer value) {
            setValue(object, value.intValue());
        }

    }
}
