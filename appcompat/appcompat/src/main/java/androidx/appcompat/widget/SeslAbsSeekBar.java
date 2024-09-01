package androidx.appcompat.widget;

import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;
import static androidx.core.view.HapticFeedbackConstantsCompat.DRAG_START;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.R;
import androidx.appcompat.animation.SeslAnimationUtils;
import androidx.appcompat.graphics.drawable.DrawableWrapperCompat;
import androidx.appcompat.util.SeslMisc;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.math.MathUtils;
import androidx.core.util.Preconditions;
import androidx.reflect.DeviceInfo;
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector;
import androidx.reflect.view.SeslViewReflector;
import androidx.reflect.widget.SeslHoverPopupWindowReflector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Original code from Samsung, all rights reserved to the original author.
 */

/**
 * Samsung AbsSeekBar class.
 */
public abstract class SeslAbsSeekBar extends SeslProgressBar {
    private static final String TAG = "SeslAbsSeekBar";

    private static final boolean IS_BASE_SDK_VERSION = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M;

    static final float SCALE_FACTOR = 1000.0f;
    private static final int MUTE_VIB_TOTAL = 4;
    private static final int MUTE_VIB_DURATION = 500;
    private static final int MUTE_VIB_DISTANCE_LVL = 400;
    private static final int HOVER_DETECT_TIME = 200;
    private static final int HOVER_POPUP_WINDOW_GRAVITY_TOP_ABOVE = 12336;
    private static final int HOVER_POPUP_WINDOW_GRAVITY_CENTER_HORIZONTAL_ON_POINT = 513;


    private final Rect mTempRect = new Rect();

    private ValueAnimator mValueAnimator;
    private AnimatorSet mMuteAnimationSet;

    private ColorStateList mDefaultNormalProgressColor;
    private ColorStateList mDefaultSecondaryProgressColor;
    private ColorStateList mDefaultActivatedProgressColor;
    private ColorStateList mDefaultActivatedThumbColor;
    private ColorStateList mOverlapNormalProgressColor;
    private ColorStateList mOverlapActivatedProgressColor;
    private Drawable mOverlapBackground;
    private Drawable mSplitProgress;

    private Drawable mThumb;
    private ColorStateList mThumbTintList = null;
    private PorterDuff.Mode mThumbTintMode = null;
    private boolean mHasThumbTint = false;
    private boolean mHasThumbTintMode = false;

    private Drawable mTickMark;
    private ColorStateList mTickMarkTintList = null;
    private PorterDuff.Mode mTickMarkTintMode = null;
    private boolean mHasTickMarkTint = false;
    private boolean mHasTickMarkTintMode = false;

    private int mCurrentProgressLevel;
    public static final int NO_OVERLAP = -1;
    private int mOverlapPoint = NO_OVERLAP;
    private int mHoveringLevel = 0;

    private int mPreviousHoverPopupType = 0;

    private int mThumbRadius;
    int mThumbPosX;
    private int mThumbOffset;
    private boolean mSplitTrack;

    private int mTrackMinWidth;
    private int mTrackMaxWidth;

    private Drawable mDivider;


    /**
     * Whether this is user seekable.
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    boolean mIsUserSeekable = true;

    /**
     * On key presses (right or left), the amount to increment/decrement the
     * progress.
     */
    private int mKeyProgressIncrement = 1;

    private static final int NO_ALPHA = 0xFF;
    private float mDisabledAlpha;

    private int mScaledTouchSlop;
    private float mTouchDownX;
    private float mTouchDownY;
    private boolean mIsDragging;

    private boolean mAllowedSeekBarAnimation = false;
    private boolean mIsDraggingForSliding = false;
    private boolean mIsFirstSetProgress = false;
    private boolean mIsLightTheme;
    boolean mIsSeamless = false;
    private boolean mIsSetModeCalled = false;
    private boolean mIsTouchDisabled = false;
    private boolean mLargeFont = false;
    private boolean mSetDualColorMode = false;
    private boolean mUseMuteAnimation = false;

    private List<Rect> mUserGestureExclusionRects = Collections.emptyList();
    private final List<Rect> mGestureExclusionRects = new ArrayList<>();
    private final Rect mThumbRect = new Rect();

    //Sesl 6 added
    private Drawable mLevelBarThumbDrawable;
    private float mLevelDrawPadding = 0.0f;
    private int mModeExpandThumbRadius;
    private int mModeExpandTrackMaxWidth;
    private int mModeExpandTrackMinWidth;


    public SeslAbsSeekBar(Context context) {
        super(context);
    }

    public SeslAbsSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SeslAbsSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("RestrictedApi")
    public SeslAbsSeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);


        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AppCompatSeekBar, defStyleAttr, defStyleRes);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAttributeDataForStyleable(context, R.styleable.AppCompatSeekBar, attrs, a, defStyleAttr,
                        defStyleRes);
            }

            final Resources res = context.getResources();

            final Drawable thumb = a.getDrawable(R.styleable.AppCompatSeekBar_android_thumb);
            setThumb(thumb);

            if (a.hasValue(R.styleable.AppCompatSeekBar_android_thumbTintMode)) {
                mThumbTintMode = DrawableUtils.parseTintMode(a.getInt(
                        R.styleable.AppCompatSeekBar_android_thumbTintMode, -1), mThumbTintMode);
                mHasThumbTintMode = true;
            }

            if (a.hasValue(R.styleable.AppCompatSeekBar_android_thumbTint)) {
                mThumbTintList = a.getColorStateList(R.styleable.AppCompatSeekBar_android_thumbTint);
                mHasThumbTint = true;
            }

            final Drawable tickMark = a.getDrawable(R.styleable.AppCompatSeekBar_tickMark);
            setTickMark(tickMark);

            if (a.hasValue(R.styleable.AppCompatSeekBar_tickMarkTintMode)) {
                mTickMarkTintMode = DrawableUtils.parseTintMode(a.getInt(
                        R.styleable.AppCompatSeekBar_tickMarkTintMode, -1), mTickMarkTintMode);
                mHasTickMarkTintMode = true;
            }

            if (a.hasValue(R.styleable.AppCompatSeekBar_tickMarkTint)) {
                mTickMarkTintList = a.getColorStateList(R.styleable.AppCompatSeekBar_tickMarkTint);
                mHasTickMarkTint = true;
            }

            mSplitTrack = a.getBoolean(R.styleable.AppCompatSeekBar_android_splitTrack, false);

            mTrackMinWidth = a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslTrackMinWidth,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_track_height)));

            mTrackMaxWidth = a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslTrackMaxWidth,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_track_height_expand)));

            mThumbRadius = a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslThumbRadius,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_thumb_radius)));

            //Sesl6 added
            mModeExpandTrackMinWidth =
                    a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslTrackMinWidth,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_mode_expand_track_height)));
            mModeExpandTrackMaxWidth =
                    a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslTrackMaxWidth,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_mode_expand_track_height_expand)));
            mModeExpandThumbRadius =
                    a.getDimensionPixelSize(R.styleable.AppCompatSeekBar_seslThumbRadius,
                    Math.round(res.getDimension(R.dimen.sesl_seekbar_mode_expand_thumb_radius)));


            // Guess thumb offset if thumb != null, but allow layout to override.
            final int thumbOffset = a.getDimensionPixelOffset(
                    R.styleable.AppCompatSeekBar_android_thumbOffset, getThumbOffset());
            setThumbOffset(thumbOffset);

            if (a.hasValue(R.styleable.AppCompatSeekBar_seslSeekBarMode)) {
                mCurrentMode = a.getInt(R.styleable.AppCompatSeekBar_seslSeekBarMode,
                        MODE_STANDARD);
            }

            final boolean useDisabledAlpha =
                    a.getBoolean(R.styleable.AppCompatSeekBar_useDisabledAlpha, true);

            if (useDisabledAlpha) {
                final TypedArray ta = context.obtainStyledAttributes(attrs,
                        R.styleable.AppCompatTheme, 0, 0);
                mDisabledAlpha = ta.getFloat(R.styleable.AppCompatTheme_android_disabledAlpha,
                        0.5f);
                ta.recycle();
            } else {
                mDisabledAlpha = 1.0f;
            }

            applyThumbTint();
            applyTickMarkTint();

            mScaledTouchSlop =
                    (int) (ViewConfiguration.get(context).getScaledTouchSlop() * 0.35F);

            mIsLightTheme = SeslMisc.isLightTheme(context);

            mDefaultNormalProgressColor =
                    colorToColorStateList(res.getColor(R.color.sesl_seekbar_control_color_default));
            mDefaultSecondaryProgressColor =
                    colorToColorStateList(res.getColor(R.color.sesl_seekbar_control_color_secondary));
            mDefaultActivatedProgressColor =
                    colorToColorStateList(res.getColor(R.color.sesl_seekbar_control_color_activated));
            mOverlapNormalProgressColor = colorToColorStateList(res.getColor(mIsLightTheme ?
                    R.color.sesl_seekbar_overlap_color_default_light :
                    R.color.sesl_seekbar_overlap_color_default_dark));
            mOverlapActivatedProgressColor = colorToColorStateList(res.getColor(mIsLightTheme ?
                    R.color.sesl_seekbar_overlap_color_activated_light :
                    R.color.sesl_seekbar_overlap_color_activated_dark));

            mDefaultActivatedThumbColor = getThumbTintList();
            if (mDefaultActivatedThumbColor == null) {
                final int[][] states = {new int[]{android.R.attr.state_enabled},
                        new int[]{-android.R.attr.state_enabled}};
                int[] colors = new int[2];
                colors[0] = res.getColor(R.color.sesl_thumb_control_color_activated);
                colors[1] = res.getColor(mIsLightTheme ?
                        R.color.sesl_seekbar_disable_color_activated_light :
                        R.color.sesl_seekbar_disable_color_activated_dark);
                mDefaultActivatedThumbColor = new ColorStateList(states, colors);
            }

            mAllowedSeekBarAnimation = res.getBoolean(R.bool.sesl_seekbar_sliding_animation);
            if (mAllowedSeekBarAnimation) {
                initMuteAnimation();
            }

            if (mCurrentMode != MODE_STANDARD) {
                setMode(mCurrentMode);
            } else {
                initializeExpandMode();
            }
        } finally {
            a.recycle();
        }
    }

    public void setThumb(Drawable thumb) {
        final boolean needUpdate;
        // This way, calling setThumb again with the same bitmap will result in
        // it recalcuating mThumbOffset (if for example it the bounds of the
        // drawable changed)
        if (mThumb != null && thumb != mThumb) {
            mThumb.setCallback(null);
            needUpdate = true;
        } else {
            needUpdate = false;
        }

        if (thumb != null) {
            thumb.setCallback(this);
            if (canResolveLayoutDirection()) {
                DrawableCompat.setLayoutDirection(thumb, getLayoutDirection());
            }

            // Assuming the thumb drawable is symmetric, set the thumb offset
            // such that the thumb will hang halfway off either edge of the
            // progress bar.
            if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
                mThumbOffset = thumb.getIntrinsicHeight() / 2;
            } else {
                mThumbOffset = thumb.getIntrinsicWidth() / 2;
            }

            // If we're updating get the new states
            if (needUpdate &&
                    (thumb.getIntrinsicWidth() != mThumb.getIntrinsicWidth()
                            || thumb.getIntrinsicHeight() != mThumb.getIntrinsicHeight())) {
                requestLayout();
            }
        }

        mThumb = thumb;

        applyThumbTint();
        invalidate();

        if (needUpdate) {
            updateThumbAndTrackPos(getWidth(), getHeight());
            if (thumb != null && thumb.isStateful()) {
                // Note that if the states are different this won't work.
                // For now, let's consider that an app bug.
                int[] state = getDrawableState();
                thumb.setState(state);
            }
        }
    }

    /**
     * Return the drawable used to represent the scroll thumb - the component that
     * the user can drag back and forth indicating the current value by its position.
     *
     * @return The current thumb drawable
     */
    public Drawable getThumb() {
        return mThumb;
    }

    /**
     * Applies a tint to the thumb drawable. Does not modify the current tint
     * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     * <p>
     * Subsequent calls to {@link #setThumb(Drawable)} will automatically
     * mutate the drawable and apply the specified tint and tint mode using
     * {@link Drawable#setTintList(ColorStateList)}.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     *
     * @attr ref android.R.styleable#SeekBar_thumbTint
     * @see #getThumbTintList()
     * @see Drawable#setTintList(ColorStateList)
     */
    public void setThumbTintList(@Nullable ColorStateList tint) {
        mThumbTintList = tint;
        mHasThumbTint = true;

        applyThumbTint();
        mDefaultActivatedThumbColor = tint;
    }

    /**
     * Returns the tint applied to the thumb drawable, if specified.
     *
     * @return the tint applied to the thumb drawable
     * @attr ref android.R.styleable#SeekBar_thumbTint
     * @see #setThumbTintList(ColorStateList)
     */
    @Nullable
    public ColorStateList getThumbTintList() {
        return mThumbTintList;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setThumbTintList(ColorStateList)}} to the thumb drawable. The
     * default mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     *
     * @attr ref android.R.styleable#SeekBar_thumbTintMode
     * @see #getThumbTintMode()
     * @see Drawable#setTintMode(PorterDuff.Mode)
     */
    public void setThumbTintMode(@Nullable PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mHasThumbTintMode = true;
        applyThumbTint();
    }

    /**
     * Returns the blending mode used to apply the tint to the thumb drawable,
     * if specified.
     *
     * @return the blending mode used to apply the tint to the thumb drawable
     * @attr ref android.R.styleable#SeekBar_thumbTintMode
     * @see #setThumbTintMode(PorterDuff.Mode)
     */
    @Nullable
    public PorterDuff.Mode getThumbTintMode() {
        return mThumbTintMode;
    }

    private void applyThumbTint() {
        if (mThumb != null && (mHasThumbTint || mHasThumbTintMode)) {
            mThumb = mThumb.mutate();

            if (mHasThumbTint) {
                DrawableCompat.setTintList(mThumb, mThumbTintList);
            }

            if (mHasThumbTintMode) {
                DrawableCompat.setTintMode(mThumb, mThumbTintMode);
            }

            // The drawable (or one of its children) may not have been
            // stateful before applying the tint, so let's try again.
            if (mThumb.isStateful()) {
                mThumb.setState(getDrawableState());
            }
        }
    }

    /**
     * @see #setThumbOffset(int)
     */
    public int getThumbOffset() {
        return mThumbOffset;
    }

    /**
     * Sets the thumb offset that allows the thumb to extend out of the range of
     * the track.
     *
     * @param thumbOffset The offset amount in pixels.
     */
    public void setThumbOffset(int thumbOffset) {
        mThumbOffset = thumbOffset;
        invalidate();
    }

    /**
     * Specifies whether the track should be split by the thumb. When true,
     * the thumb's optical bounds will be clipped out of the track drawable,
     * then the thumb will be drawn into the resulting gap.
     *
     * @param splitTrack Whether the track should be split by the thumb
     */
    public void setSplitTrack(boolean splitTrack) {
        mSplitTrack = splitTrack;
        invalidate();
    }

    /**
     * Returns whether the track should be split by the thumb.
     */
    public boolean getSplitTrack() {
        return mSplitTrack;
    }

    /**
     * Sets the drawable displayed at each progress position, e.g. at each
     * possible thumb position.
     *
     * @param tickMark the drawable to display at each progress position
     */
    public void setTickMark(Drawable tickMark) {
        if (mTickMark != null) {
            mTickMark.setCallback(null);
        }

        mTickMark = tickMark;

        if (tickMark != null) {
            tickMark.setCallback(this);
            DrawableCompat.setLayoutDirection(tickMark, getLayoutDirection());
            if (tickMark.isStateful()) {
                tickMark.setState(getDrawableState());
            }
            applyTickMarkTint();
        }

        invalidate();
    }

    /**
     * @return the drawable displayed at each progress position
     */
    public Drawable getTickMark() {
        return mTickMark;
    }

    /**
     * Applies a tint to the tick mark drawable. Does not modify the current tint
     * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     * <p>
     * Subsequent calls to {@link #setTickMark(Drawable)} will automatically
     * mutate the drawable and apply the specified tint and tint mode using
     * {@link Drawable#setTintList(ColorStateList)}.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     *
     * @attr ref android.R.styleable#SeekBar_tickMarkTint
     * @see #getTickMarkTintList()
     * @see Drawable#setTintList(ColorStateList)
     */
    public void setTickMarkTintList(@Nullable ColorStateList tint) {
        mTickMarkTintList = tint;
        mHasTickMarkTint = true;

        applyTickMarkTint();
    }

    /**
     * Returns the tint applied to the tick mark drawable, if specified.
     *
     * @return the tint applied to the tick mark drawable
     * @attr ref android.R.styleable#SeekBar_tickMarkTint
     * @see #setTickMarkTintList(ColorStateList)
     */
    @Nullable
    public ColorStateList getTickMarkTintList() {
        return mTickMarkTintList;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setTickMarkTintList(ColorStateList)}} to the tick mark drawable. The
     * default mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     *
     * @attr ref android.R.styleable#SeekBar_tickMarkTintMode
     * @see #getTickMarkTintMode()
     * @see Drawable#setTintMode(PorterDuff.Mode)
     */
    public void setTickMarkTintMode(@Nullable PorterDuff.Mode tintMode) {
        mTickMarkTintMode = tintMode;
        mHasTickMarkTintMode = true;

        applyTickMarkTint();
    }

    /**
     * Returns the blending mode used to apply the tint to the tick mark drawable,
     * if specified.
     *
     * @return the blending mode used to apply the tint to the tick mark drawable
     * @attr ref android.R.styleable#SeekBar_tickMarkTintMode
     * @see #setTickMarkTintMode(PorterDuff.Mode)
     */
    @Nullable
    public PorterDuff.Mode getTickMarkTintMode() {
        return mTickMarkTintMode;
    }

    private void applyTickMarkTint() {
        if (mTickMark != null && (mHasTickMarkTint || mHasTickMarkTintMode)) {
            mTickMark = mTickMark.mutate();

            if (mHasTickMarkTint) {
                DrawableCompat.setTintList(mTickMark, mTickMarkTintList);
            }

            if (mHasTickMarkTintMode) {
                DrawableCompat.setTintMode(mTickMark, mTickMarkTintMode);
            }

            // The drawable (or one of its children) may not have been
            // stateful before applying the tint, so let's try again.
            if (mTickMark.isStateful()) {
                mTickMark.setState(getDrawableState());
            }
        }
    }

    /**
     * Sets the amount of progress changed via the arrow keys.
     *
     * @param increment The amount to increment or decrement when the user
     *            presses the arrow keys.
     */
    public void setKeyProgressIncrement(int increment) {
        mKeyProgressIncrement = increment < 0 ? -increment : increment;
    }

    /**
     * Returns the amount of progress changed via the arrow keys.
     * <p>
     * By default, this will be a value that is derived from the progress range.
     *
     * @return The amount to increment or decrement when the user presses the
     *         arrow keys. This will be positive.
     */
    public int getKeyProgressIncrement() {
        return mKeyProgressIncrement;
    }

    @Override
    public synchronized void setMin(int min) {
        if (mIsSeamless) {
            min = Math.round(min * SCALE_FACTOR);
        }

        super.setMin(min);
        int range = getMax() - getMin();

        if (mKeyProgressIncrement == 0 || range / mKeyProgressIncrement > 20) {

            // It will take the user too long to change this via keys, change it
            // to something more reasonable
            setKeyProgressIncrement(Math.max(1, Math.round((float) range / 20)));
        }
    }

    @Override
    public synchronized void setMax(int max) {
        if (mIsSeamless) {
            max = Math.round(max * SCALE_FACTOR);
        }

        super.setMax(max);
        mIsFirstSetProgress = true;
        int range = getMax() - getMin();

        if (mKeyProgressIncrement == 0 || range / mKeyProgressIncrement > 20) {
            // It will take the user too long to change this via keys, change it
            // to something more reasonable
            setKeyProgressIncrement(Math.max(1, Math.round((float) range / 20)));
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable drawable) {
        return drawable == mThumb || drawable == mTickMark || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (mThumb != null) {
            mThumb.jumpToCurrentState();
        }

        if (mTickMark != null) {
            mTickMark.jumpToCurrentState();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        final Drawable progressDrawable = getProgressDrawable();

        if (progressDrawable != null && mDisabledAlpha < 1.0f) {
            final int alpha = isEnabled() ? NO_ALPHA : (int) (NO_ALPHA * mDisabledAlpha);
            progressDrawable.setAlpha(alpha);
            if (mOverlapBackground != null) {
                mOverlapBackground.setAlpha(alpha);
            }
        }

        if (mThumb != null && mHasThumbTint) {
            if (!isEnabled()) {
                DrawableCompat.setTintList(mThumb, null);
            } else {
                DrawableCompat.setTintList(mThumb, mDefaultActivatedThumbColor);
                updateDualColorMode();
            }
        }
        if (mSetDualColorMode && progressDrawable != null && progressDrawable.isStateful() && mOverlapBackground != null) {
            mOverlapBackground.setState(getDrawableState());
        }

        final Drawable thumb = mThumb;
        if (thumb != null && thumb.isStateful()
                && thumb.setState(getDrawableState())) {
            invalidateDrawable(thumb);
        }

        final Drawable tickMark = mTickMark;
        if (tickMark != null && tickMark.isStateful() && tickMark.setState(getDrawableState())) {
            invalidateDrawable(tickMark);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mThumb != null) {
            DrawableCompat.setHotspot(mThumb, x, y);
        }
    }

    @Override
    public void onVisualProgressChanged(int id, float scale) {
        super.onVisualProgressChanged(id, scale);

        if (id == android.R.id.progress) {
            final Drawable thumb = mThumb;
            if (thumb != null) {
                setThumbPos(getWidth(), thumb, scale, Integer.MIN_VALUE);

                // Since we draw translated, the drawable's bounds that it signals
                // for invalidation won't be the actual bounds we want invalidated,
                // so just invalidate this whole view.
                invalidate();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        updateThumbAndTrackPos(w, h);
    }

    private void updateThumbAndTrackPos(int w, int h) {
        if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
            updateThumbAndTrackPosInVertical(w, h);
            return;
        }

        final int paddedHeight = h - getPaddingTop() - getPaddingBottom();
        final Drawable track = getCurrentDrawable();
        final Drawable thumb = mThumb;

        // The max height does not incorporate padding, whereas the height
        // parameter does.
        final int trackHeight = Math.min(mMaxHeight, paddedHeight);
        final int thumbHeight = thumb == null ? 0 : thumb.getIntrinsicHeight();

        // Apply offset to whichever item is taller.
        final int trackOffset;
        final int thumbOffset;
        if (thumbHeight > trackHeight) {
            final int offsetHeight = (paddedHeight - thumbHeight) / 2;
            trackOffset = offsetHeight + (thumbHeight - trackHeight) / 2;
            thumbOffset = offsetHeight;
        } else {
            final int offsetHeight = (paddedHeight - trackHeight) / 2;
            trackOffset = offsetHeight;
            thumbOffset = offsetHeight + (trackHeight - thumbHeight) / 2;
        }

        if (track != null) {
            final int trackWidth = w - getPaddingRight() - getPaddingLeft();
            track.setBounds(0, trackOffset, trackWidth, trackOffset + trackHeight);
        }

        if (thumb != null) {
            setThumbPos(w, thumb, getScale(), thumbOffset);
        }

        updateSplitProgress();
    }

    private void updateThumbAndTrackPosInVertical(int w, int h) {
        final int paddedWidth = w - getPaddingLeft() - getPaddingRight();
        final Drawable track = getCurrentDrawable();
        final Drawable thumb = mThumb;

        // The max width does not incorporate padding, whereas the width
        // parameter does.
        final int trackWidth = Math.min(mMaxWidth, paddedWidth);
        final int thumbWidth = thumb == null ? 0 : thumb.getIntrinsicWidth();

        // Apply offset to whichever item is taller.
        final int trackOffset;
        final int thumbOffset;
        if (thumbWidth > trackWidth) {
            final int offsetWidth = (paddedWidth - thumbWidth) / 2;
            trackOffset = offsetWidth + (thumbWidth - trackWidth) / 2;
            thumbOffset = offsetWidth;
        } else {
            final int offsetWidth = (paddedWidth - trackWidth) / 2;
            trackOffset = offsetWidth;
            thumbOffset = offsetWidth + (trackWidth - thumbWidth) / 2;
        }

        if (track != null) {
            final int trackHeight = h - getPaddingBottom() - getPaddingTop();
            track.setBounds(trackOffset, 0, paddedWidth - trackOffset, trackHeight);
        }

        if (thumb != null) {
            setThumbPosInVertical(h, thumb, getScale(), thumbOffset);
        }
    }

    private float getScale() {
        int min = getMin();
        int max = getMax();
        int range = max - min;
        return range > 0 ? (getProgress() - min) / (float) range : 0f;
    }

    private void setThumbPos(int w, Drawable thumb, float scale, int offset) {
        if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
            setThumbPosInVertical(getHeight(), thumb, scale, offset);
            return;
        }

        int available = ((w - getPaddingLeft()) - getPaddingRight())
                - ((int) (mLevelDrawPadding * 2.0f));//Sesl6 added
        final int thumbWidth = thumb.getIntrinsicWidth();
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbWidth;

        // The extra space for the thumb to move on the track
        available += mThumbOffset * 2;

        final int thumbPos = (int) ((scale * available) + 0.5f);

        final int top, bottom;
        if (offset == Integer.MIN_VALUE) {
            final Rect oldBounds = thumb.getBounds();
            top = oldBounds.top;
            bottom = oldBounds.bottom;
        } else {
            top = offset;
            bottom = offset + thumbHeight;
        }
        int levelDrawPadding = (int) mLevelDrawPadding;
        int left = (ViewUtils.isLayoutRtl(this) && mMirrorForRtl) ? available - thumbPos : thumbPos;
        left += levelDrawPadding;
        final int right = left + thumbWidth;

        final Drawable background = getBackground();
        if (background != null) {
            int offsetX = getPaddingLeft() - mThumbOffset;
            int offsetY = getPaddingTop();
            DrawableCompat.setHotspotBounds(background, left + offsetX, top + offsetY,
                    right + offsetX, bottom + offsetY);
        }

        // Canvas will be translated, so 0,0 is where we start drawing
        thumb.setBounds(left, top, right, bottom);
        updateGestureExclusionRects();

        mThumbPosX = (getPaddingLeft() + left) - (getPaddingLeft() - (thumbWidth / 2));
        updateSplitProgress();
    }


    private void setThumbPosInVertical(int h, Drawable thumb, float scale, int offset) {
        int available = h - getPaddingTop() - getPaddingBottom();
        final int thumbWidth = thumb.getIntrinsicWidth();
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbHeight;

        // The extra space for the thumb to move on the track
        available += mThumbOffset * 2;

        final int thumbPos = (int) ((scale * available) + 0.5f);

        final int left, right;
        if (offset == Integer.MIN_VALUE) {
            final Rect oldBounds = thumb.getBounds();
            left = oldBounds.left;
            right = oldBounds.right;
        } else {
            left = offset;
            right = offset + thumbWidth;
        }

        final int top = available - thumbPos;
        final int bottom = top + thumbHeight;

        final Drawable background = getBackground();
        if (background != null) {
            final int offsetX = getPaddingLeft();
            final int offsetY = getPaddingTop() - mThumbOffset;
            DrawableCompat.setHotspotBounds(background, left + offsetX, top + offsetY,
                    right + offsetX, bottom + offsetY);
        }

        // Canvas will be translated, so 0,0 is where we start drawing
        thumb.setBounds(left, top, right, bottom);

        mThumbPosX = top + (thumbWidth / 2) + getPaddingLeft();
    }

    @Override
    public void setSystemGestureExclusionRects(@NonNull List<Rect> rects) {
        Preconditions.checkNotNull(rects, "rects must not be null");
        mUserGestureExclusionRects = rects;
        updateGestureExclusionRects();
    }

    private void updateGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final Drawable thumb = mThumb;
            if (thumb == null) {
                super.setSystemGestureExclusionRects(mUserGestureExclusionRects);
                return;
            }
            mGestureExclusionRects.clear();
            thumb.copyBounds(mThumbRect);
            mGestureExclusionRects.add(mThumbRect);
            mGestureExclusionRects.addAll(mUserGestureExclusionRects);
            super.setSystemGestureExclusionRects(mGestureExclusionRects);
        }
    }

    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void onResolveDrawables(int layoutDirection) {
        super.onResolveDrawables(layoutDirection);

        if (mThumb != null) {
            DrawableCompat.setLayoutDirection(mThumb, layoutDirection);
        }
    }

    @Override
    public synchronized void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (supportIsHoveringUIEnabled()) {
            final int hoverPopupType = getHoverPopupType();
            if (isHoverPopupTypeUserCustom(hoverPopupType) && mPreviousHoverPopupType != hoverPopupType) {
                mPreviousHoverPopupType = hoverPopupType;
                setHoverPopupGravity(HOVER_POPUP_WINDOW_GRAVITY_TOP_ABOVE
                        | HOVER_POPUP_WINDOW_GRAVITY_CENTER_HORIZONTAL_ON_POINT);
                setHoverPopupOffset(0, getMeasuredHeight() / 2);
                setHoverPopupDetectTime();
            }
        }
        if (mCurrentMode == MODE_SPLIT) {
            mSplitProgress.draw(canvas);
            mDivider.draw(canvas);
        }
        if (!mIsTouchDisabled) {
            drawThumb(canvas);
        }
    }


    @Override
    public void drawTrack(Canvas canvas) {
        Drawable thumbDrawable = mThumb;
        if (thumbDrawable != null && mSplitTrack) {
            @SuppressLint("RestrictedApi")
            final Rect insets = DrawableUtils.getOpticalBounds(thumbDrawable);
            final Rect tempRect = mTempRect;
            thumbDrawable.copyBounds(tempRect);
            tempRect.offset(getPaddingLeft() - mThumbOffset, getPaddingTop());
            tempRect.left += insets.left;
            tempRect.right -= insets.right;

            final int saveCount = canvas.save();
            canvas.clipRect(tempRect, Region.Op.DIFFERENCE);
            super.drawTrack(canvas);
            drawTickMarks(canvas);
            canvas.restoreToCount(saveCount);
        } else {
            super.drawTrack(canvas);
            drawTickMarks(canvas);
        }

        if (!checkInvalidatedDualColorMode()) {
            canvas.save();
            if (mMirrorForRtl && ViewUtils.isLayoutRtl(this)) {
                canvas.translate(getWidth() - getPaddingRight(), getPaddingTop());
                canvas.scale(-1.0f, 1.0f);
            } else {
                canvas.translate(getPaddingLeft(), getPaddingTop());
            }

            Rect bounds = mOverlapBackground.getBounds();
            Rect tempRect = mTempRect;
            mOverlapBackground.copyBounds(tempRect);

            final int maxProgress;
            final int currentProgress;
            if (mIsSeamless) {
                int scaledOverlap = (int)(mOverlapPoint * SCALE_FACTOR);
                currentProgress = super.getProgress() > scaledOverlap ? super.getProgress() :
                        scaledOverlap;
                maxProgress = super.getMax();
            } else {
                currentProgress = Math.max(getProgress(), mOverlapPoint);
                maxProgress = getMax();
            }
            if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
                tempRect.bottom = (int) ( ( (float)bounds.bottom)
                        - ( (float)bounds.height() * (float)currentProgress / (float)maxProgress) );
            } else {
                tempRect.left =  (int) ((float) bounds.left
                        + ((float) bounds.width() * (float) currentProgress / (float) maxProgress));
            }
            canvas.clipRect(tempRect);
            if (mDefaultNormalProgressColor.getDefaultColor() != mOverlapNormalProgressColor.getDefaultColor()) {
                mOverlapBackground.draw(canvas);
            }
            canvas.restore();
        }
    }


    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void drawTickMarks(Canvas canvas) {
        if (mTickMark != null) {
            final int count = getMax() - getMin();
            if (count > 1) {
                int w = mTickMark.getIntrinsicWidth();
                int h = mTickMark.getIntrinsicHeight();
                int halfW = w >= 0 ? w / 2 : 1;
                int halfH = h >= 0 ? h / 2 : 1;
                mTickMark.setBounds(-halfW, -halfH, halfW, halfH);

                final float spacing =
                        (((getWidth() - getPaddingLeft()) - getPaddingRight()) - (mLevelDrawPadding * 2.0f)) / (float) count;
                final int saveCount = canvas.save();
                canvas.translate(mLevelDrawPadding + getPaddingLeft(), getHeight() / 2.0f);
                for (int i = 0; i <= count; i++) {
                    mTickMark.draw(canvas);
                    canvas.translate(spacing, 0);
                }
                canvas.restoreToCount(saveCount);
            }
        }
    }


    /**
     * Draw the thumb.
     */
    void drawThumb(Canvas canvas) {
        if (mThumb != null) {
            final int saveCount = canvas.save();
            // Translate the padding. For the x, we need to allow the thumb to
            // draw in its extra space
            if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
                canvas.translate(getPaddingLeft(), getPaddingTop() - mThumbOffset);
            } else {
                canvas.translate(getPaddingLeft() - mThumbOffset, getPaddingTop());
            }
            mThumb.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable d = getCurrentDrawable();

        int thumbHeight = mThumb == null ? 0 : mThumb.getIntrinsicHeight();
        int dw = 0;
        int dh = 0;
        if (d != null) {
            if (mCurrentMode != MODE_VERTICAL && mCurrentMode != MODE_EXPAND_VERTICAL) {
                dw = Math.max(mMinWidth, Math.min(mMaxWidth, d.getIntrinsicWidth()));
                dh = Math.max(mMinHeight, Math.min(mMaxHeight, d.getIntrinsicHeight()));
                dh = Math.max(thumbHeight, dh);
            } else {
                dh = Math.max(mMinHeight, Math.min(mMaxHeight, d.getIntrinsicWidth()));
                dw = Math.max(mMinWidth, Math.min(mMaxWidth, d.getIntrinsicHeight()));
                dw = Math.max(thumbHeight, dw);
            }
        }
        dw += getPaddingLeft() + getPaddingRight();
        dh += getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0),
                resolveSizeAndState(dh, heightMeasureSpec, 0));
    }

    //implemented fix for scrollable container
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsUserSeekable || mIsTouchDisabled || !isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsDraggingForSliding = false;
                mTouchDownX = event.getX();
                mTouchDownY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                mIsDraggingForSliding = true;
                if (mIsDragging) {
                    trackTouchEvent(event);
                } else {
                    final float x = event.getX();
                    final float y = event.getY();
                    final float adx = Math.abs(x - mTouchDownX);
                    final float ady = Math.abs(y - mTouchDownY);
                    if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL){
                        if (ady > mScaledTouchSlop &&  ady > adx) {
                            startDrag(event);
                        }
                    }else{
                        if (adx > mScaledTouchSlop && adx > ady) {
                            startDrag(event);
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mIsDraggingForSliding) {
                    mIsDraggingForSliding = false;
                }
                if (mIsDragging) {
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                    setPressed(false);
                } else {
                    // Touch up when we never crossed the touch slop threshold should
                    // be interpreted as a tap-seek to that location.
                    onStartTrackingTouch();
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                }
                // ProgressBar doesn't know to repaint the thumb drawable
                // in its inactive state when the touch stops (because the
                // value has not apparently changed)
                invalidate();
                break;

            case MotionEvent.ACTION_CANCEL:
                mIsDraggingForSliding = false;
                if (mIsDragging) {
                    onStopTrackingTouch();
                    setPressed(false);
                }
                invalidate(); // see above explanation
                break;
        }

        return true;
    }

    private void startDrag(MotionEvent motionEvent) {
        setPressed(true);

        if (mThumb != null) {
            // This may be within the padding region.
            invalidate(mThumb.getBounds());
        }

        onStartTrackingTouch();
        trackTouchEvent(motionEvent);
        attemptClaimDrag();
    }

    private void setHotspot(float x, float y) {
        final Drawable bg = getBackground();
        if (bg != null) {
            DrawableCompat.setHotspot(bg, x, y);
        }
    }


    private void trackTouchEvent(MotionEvent event) {
        if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
            trackTouchEventInVertical(event);
            return;
        }

        int x = Math.round(event.getX());
        int y = Math.round(event.getY());
        int w = getWidth();
        int availableWidth = w - getPaddingLeft() - getPaddingRight();

        float scale;

        if (ViewUtils.isLayoutRtl(this) && mMirrorForRtl) {
            scale = (float) (availableWidth - x + getPaddingLeft()) / (float) availableWidth;
        } else {
            scale = (float) (x - getPaddingLeft()) / (float) availableWidth;
        }
        scale = MathUtils.clamp(scale, 0.0F, 1.0F);

        float max = getMax();
        float min = getMin();

        float range = mIsSeamless ? (float) (super.getMax() - super.getMin()) : ( max - min);

        float base = mIsSeamless ? super.getMin() : min;
        float progress =  base + (scale * range);

        this.setHotspot((float) x, (float) y);
        this.setProgressInternal(Math.round(progress), true, false);
    }


    private void trackTouchEventInVertical(MotionEvent event) {
        int height = getHeight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        int x = Math.round(event.getX());
        int offsetY = height - Math.round(event.getY());

        float scale;
        scale = (float) (offsetY - paddingBottom) / (float) (height - paddingTop - paddingBottom);
        scale = MathUtils.clamp(scale, 0.0F, 1.0F);

        float max = getMax();
        float min = getMin();
        float range = mIsSeamless ? (float) (super.getMax() - super.getMin()) : ( max - min);
        float base = mIsSeamless ? super.getMin() : min;
        float progress =  base + (scale * range);

        setHotspot((float) x, (float) offsetY);
        setProgressInternal(Math.round(progress), true, false);
    }

    private void attemptClaimDrag() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    public void callSuperSetProgress(int progress) {
        super.setProgress(progress);
    }

    private void cancelMuteAnimation() {
        AnimatorSet animatorSet = mMuteAnimationSet;
        if (animatorSet == null || !animatorSet.isRunning()) {
            return;
        }
        mMuteAnimationSet.cancel();
    }

    private boolean checkInvalidatedDualColorMode() {
        return mOverlapPoint == NO_OVERLAP || mOverlapBackground == null;
    }

    private ColorStateList colorToColorStateList(int color) {
        int[][] EMPTY = {new int[0]};
        return new ColorStateList(EMPTY, new int[]{color});
    }

    private int getHoverPopupType() {
        if (IS_BASE_SDK_VERSION) {
            return SeslViewReflector.semGetHoverPopupType(this);
        }
        return 0;
    }


    private void initDualOverlapDrawable() {
        switch (mCurrentMode){
            case MODE_EXPAND:{
                mOverlapBackground = new SliderDrawable(mModeExpandTrackMinWidth,
                        mModeExpandTrackMaxWidth, mOverlapNormalProgressColor);
                break;
            }
            case MODE_EXPAND_VERTICAL:{
                mOverlapBackground = new SliderDrawable(mTrackMinWidth, mTrackMaxWidth,
                        mOverlapNormalProgressColor, true);
                break;
            }
            case MODE_STANDARD:{
                mOverlapBackground = new SliderDrawable(mTrackMinWidth, mTrackMaxWidth,
                        mOverlapNormalProgressColor, false);
                break;
            }
            default:{
                if (getProgressDrawable() != null && getProgressDrawable().getConstantState() != null) {
                    mOverlapBackground =
                            getProgressDrawable().getConstantState().newDrawable().mutate();
                }
            }
        }
    }

    private void initMuteAnimation() {
        mMuteAnimationSet = new AnimatorSet();
        List<Animator> list = new ArrayList<>();

        int distance = MUTE_VIB_DISTANCE_LVL;
        for (int i = 0; i < 8; i++) {
            final boolean isGoingDirection = i % 2 == 0;

            ValueAnimator progressZeroAnimation = isGoingDirection ?
                    ValueAnimator.ofInt(0, distance) : ValueAnimator.ofInt(distance, 0);
            progressZeroAnimation.setDuration(62);
            progressZeroAnimation.setInterpolator(new LinearInterpolator());
            progressZeroAnimation.addUpdateListener(animation -> {
                mCurrentProgressLevel = (Integer) animation.getAnimatedValue();
                onSlidingRefresh(mCurrentProgressLevel);
            });

            list.add(progressZeroAnimation);

            if (isGoingDirection) {
                distance = (int) (distance * 0.6d);
            }
        }

        mMuteAnimationSet.playSequentially(list);
    }

    private void initializeExpandMode() {
        SliderDrawable background =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultNormalProgressColor);
        SliderDrawable secondaryProgress =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultSecondaryProgressColor);
        SliderDrawable primaryProgress =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultActivatedProgressColor);
        Drawable thumbDrawable =
                new DrawableWrapperCompat(new ThumbDrawable(mThumbRadius,
                        mDefaultActivatedThumbColor, false));

        Drawable[] drawables = {background,
                new ClipDrawable(secondaryProgress, Gravity.CENTER_VERTICAL | Gravity.LEFT,
                        ClipDrawable.HORIZONTAL),
                new ClipDrawable(primaryProgress, Gravity.CENTER_VERTICAL | Gravity.LEFT,
                        ClipDrawable.HORIZONTAL)};
        LayerDrawable layer = new LayerDrawable(drawables);
        layer.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.secondaryProgress);
        layer.setId(2, android.R.id.progress);

        setProgressDrawable(layer);
        setThumb(thumbDrawable);
        setBackgroundResource(R.drawable.sesl_seekbar_background_borderless_expand);

        if (getMaxHeight() > mTrackMaxWidth) {
            setMaxHeight(mTrackMaxWidth);
        }
    }

    private void initializeExpandModeForModeExpand() {
        SliderDrawable background =
                new SliderDrawable( mModeExpandTrackMinWidth, mModeExpandTrackMaxWidth,
                        mDefaultNormalProgressColor);
        SliderDrawable secondaryProgress =
                new SliderDrawable( mModeExpandTrackMinWidth, mModeExpandTrackMaxWidth,
                        mDefaultSecondaryProgressColor);
        SliderDrawable primaryProgress =
                new SliderDrawable( mModeExpandTrackMinWidth, mModeExpandTrackMaxWidth,
                        mDefaultActivatedProgressColor);
        Drawable thumbDrawable =
                new DrawableWrapperCompat(new ThumbDrawable(mModeExpandThumbRadius,
                        mDefaultActivatedThumbColor, false));

        Drawable[] drawables = {background,
                new ClipDrawable(secondaryProgress, Gravity.CENTER_VERTICAL | Gravity.LEFT,
                        ClipDrawable.HORIZONTAL),
                new ClipDrawable(primaryProgress, Gravity.CENTER_VERTICAL | Gravity.LEFT,
                        ClipDrawable.HORIZONTAL)};

        LayerDrawable layer =
                new LayerDrawable(drawables);

        layer.setPaddingMode(1);
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.secondaryProgress);
        layer.setId(2, android.R.id.progress);

        setProgressDrawable(layer);
        setThumb(thumbDrawable);
        setBackgroundResource(R.drawable.sesl_seekbar_background_borderless_expand);

        if (getMaxHeight() > mModeExpandTrackMaxWidth) {
            setMaxHeight(mModeExpandTrackMaxWidth);
        }
    }

    private void initializeExpandVerticalMode() {
        SliderDrawable background =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultNormalProgressColor,
                        true);
        SliderDrawable secondaryProgress =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultSecondaryProgressColor
                        , true);
        SliderDrawable primaryProgress =
                new SliderDrawable(mTrackMinWidth, mTrackMaxWidth, mDefaultActivatedProgressColor
                        , true);
        Drawable thumbDrawable =
                new DrawableWrapperCompat(new ThumbDrawable(mThumbRadius,
                        mDefaultActivatedThumbColor, true));

        Drawable[] drawables = {background,
                new ClipDrawable(secondaryProgress, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                        ClipDrawable.VERTICAL),
                new ClipDrawable(primaryProgress, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                        ClipDrawable.VERTICAL)};
        LayerDrawable layer = new LayerDrawable(drawables);
        layer.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.secondaryProgress);
        layer.setId(2, android.R.id.progress);

        setProgressDrawable(layer);
        setThumb(thumbDrawable);
        setBackgroundResource(R.drawable.sesl_seekbar_background_borderless_expand);

        if (getMaxWidth() > mTrackMaxWidth) {
            setMaxWidth(mTrackMaxWidth);
        }
    }

    private void setThumbOverlapTintList(@Nullable ColorStateList colorStateList) {
        mThumbTintList = colorStateList;
        mHasThumbTint = true;
        applyThumbTint();
    }



    private void startMuteAnimation() {
        cancelMuteAnimation();
        AnimatorSet animatorSet = mMuteAnimationSet;
        if (animatorSet != null) {
            animatorSet.start();
        }
    }

    private boolean supportIsInScrollingContainer() {
        return SeslViewReflector.isInScrollingContainer(this);
    }

    private void trackHoverEvent(int posX) {
        final float scale;
        final int width = getWidth();
        final float available = width - getPaddingLeft() - getPaddingRight();
        float hoverLevel = 0.0f;
        if (posX < getPaddingLeft()) {
            scale = 0.0f;
        } else if (posX > width - getPaddingRight()) {
            scale = 1.0f;
        } else {
            scale = (posX - getPaddingLeft()) / available;
        }
        final int max = getMax();
        mHoveringLevel = (int) (hoverLevel + (max * scale));
    }


    private void updateDualColorMode() {
        if (checkInvalidatedDualColorMode()) {
            return;
        }
        DrawableCompat.setTintList(mOverlapBackground, mOverlapNormalProgressColor);

        if (!mLargeFont) {
            final boolean setOverlapColor = mIsSeamless ?
                    super.getProgress() > mOverlapPoint * SCALE_FACTOR
                    : getProgress() > mOverlapPoint;

            if (setOverlapColor) {
                setProgressOverlapTintList(mOverlapActivatedProgressColor);
                setThumbOverlapTintList(mOverlapActivatedProgressColor);
            } else {
                setProgressTintList(mDefaultActivatedProgressColor);
                setThumbTintList(mDefaultActivatedThumbColor);
            }
        }
        updateBoundsForDualColor();
    }

    private void updateBoundsForDualColor() {
        if (getCurrentDrawable() != null && !checkInvalidatedDualColorMode()) {
            Rect base = getCurrentDrawable().getBounds();
            mOverlapBackground.setBounds(base);
        }
    }

    private void updateSplitProgress() {
        if (mCurrentMode == MODE_SPLIT) {
            Drawable d = mSplitProgress;
            Rect base = getCurrentDrawable().getBounds();
            if (d != null) {
                if (mMirrorForRtl && ViewUtils.isLayoutRtl(this)) {
                    d.setBounds(mThumbPosX, base.top, getWidth() - getPaddingRight(), base.bottom);
                } else {
                    d.setBounds(getPaddingLeft(), base.top, mThumbPosX, base.bottom);
                }
            }

            final int w = getWidth();
            final int h = getHeight();
            if (mDivider != null) {
                mDivider.setBounds((int) ((w / 2.0f) - ((mDensity * 4.0f) / 2.0f)),
                        (int) ((h / 2.0f) - ((mDensity * 22.0f) / 2.0f)),
                        (int) ((w / 2.0f) + ((mDensity * 4.0f) / 2.0f)),
                        (int) ((h / 2.0f) + ((mDensity * 22.0f) / 2.0f)));
            }
        }
    }



    private void updateWarningMode(int progress) {
        if (mCurrentMode == MODE_WARNING) {
            if (progress == getMax()) {
                setProgressOverlapTintList(mOverlapActivatedProgressColor);
                setThumbOverlapTintList(mOverlapActivatedProgressColor);
                return;
            }
            setProgressTintList(mDefaultActivatedProgressColor);
            setThumbTintList(mDefaultActivatedThumbColor);
        }
    }

    public boolean canUserSetProgress() {
        return !isIndeterminate() && isEnabled();
    }


    @Override
    public CharSequence getAccessibilityClassName() {
        Log.d(TAG, "Stack:", new Throwable("stack dump"));
        return android.widget.AbsSeekBar.class.getName();
    }

    @Override
    public synchronized int getMax() {
        if (mIsSeamless) {
            return Math.round(super.getMax() / SCALE_FACTOR);
        } else  {
            return super.getMax();
        }
    }

    @Override
    public synchronized int getMin() {
        if (mIsSeamless) {
            return Math.round(super.getMin() / SCALE_FACTOR);
        } else  {
            return super.getMin();
        }
    }

    @Override
    public synchronized int getProgress() {
        if (mIsSeamless) {
            return Math.round(super.getProgress() / SCALE_FACTOR);
        } else  {
            return super.getProgress();
        }
    }


    @Nullable
    public Rect getThumbBounds() {
        Drawable drawable = mThumb;
        if (drawable != null) {
            return drawable.getBounds();
        }
        return null;
    }

    public int getThumbHeight() {
        return mThumb.getIntrinsicHeight();
    }


    @RestrictTo(LIBRARY_GROUP_PREFIX)
    void onHoverChanged(int hoverLevel, int posX, int posY) {
    }

    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public boolean onHoverEvent(MotionEvent motionEvent) {
        if (supportIsHoveringUIEnabled()) {
            int action = motionEvent.getAction();
            int x = (int) motionEvent.getX();
            int y = (int) motionEvent.getY();
            if (action == ACTION_HOVER_MOVE) {
                trackHoverEvent(x);
                onHoverChanged(mHoveringLevel, x, y);
                if (isHoverPopupTypeUserCustom(getHoverPopupType())) {
                    setHoveringPoint((int) motionEvent.getRawX(), (int) motionEvent.getRawY());
                    updateHoverPopup();
                }
            } else if (action == ACTION_HOVER_ENTER) {
                trackHoverEvent(x);
                onStartTrackingHover(mHoveringLevel, x, y);
            } else if (action == ACTION_HOVER_EXIT) {
                onStopTrackingHover();
            }
        }
        return super.onHoverEvent(motionEvent);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        if (isEnabled()) {
            int progress = getProgress();
            if (progress > getMin()) {
                accessibilityNodeInfo.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
            if (progress < getMax()) {
                accessibilityNodeInfo.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            }
        }
    }

    /**
     * Called when the user changes the seekbar's progress by using a key event.
     */
    public void onKeyChange() {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isEnabled()) {
            int increment = mKeyProgressIncrement;
            if (mCurrentMode == MODE_VERTICAL || mCurrentMode == MODE_EXPAND_VERTICAL) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_MINUS:
                        increment = -increment;
                        // fallthrough
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_PLUS:
                    case KeyEvent.KEYCODE_EQUALS:
                        increment = ViewUtils.isLayoutRtl(this) ? -increment : increment;

                        final int newProgress = mIsSeamless ?
                                Math.round((getProgress() + increment) * SCALE_FACTOR)
                                : getProgress() + increment;

                        if (setProgressInternal(newProgress, true, true)) {
                            onKeyChange();
                            return true;
                        }
                        break;
                }
            } else {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_MINUS:
                        increment = -increment;
                        // fallthrough
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_PLUS:
                    case KeyEvent.KEYCODE_EQUALS:
                        increment = ViewUtils.isLayoutRtl(this) ? -increment : increment;

                        final int newProgress = mIsSeamless ?
                                Math.round((getProgress() + increment) * SCALE_FACTOR)
                                : getProgress() + increment;

                        if (setProgressInternal(newProgress, true, true)) {
                            onKeyChange();
                            return true;
                        }
                        break;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onProgressRefresh(float scale, boolean fromUser, int progress) {
        final int targetLevel = (int) (10000 * scale);

        final boolean isMuteAnimationNeeded =
                mUseMuteAnimation && !mIsFirstSetProgress && !mIsDraggingForSliding;
        if (!isMuteAnimationNeeded || mCurrentProgressLevel == 0 || targetLevel != 0) {
            cancelMuteAnimation();
            mIsFirstSetProgress = false;
            mCurrentProgressLevel = targetLevel;

            super.onProgressRefresh(scale, fromUser, progress);

            Drawable thumb = mThumb;
            if (thumb != null) {
                setThumbPos(getWidth(), thumb, scale, Integer.MIN_VALUE);
                invalidate();
            }
        } else {
            startMuteAnimation();
        }

        if (fromUser && mCurrentMode == MODE_LEVEL_BAR) {
            int hapticFeedback;
            if (DeviceInfo.isOneUI()) {
                hapticFeedback = SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(41);
            } else {
                //custom added for non-oneui
                hapticFeedback =DRAG_START;
            }
            performHapticFeedback(hapticFeedback);
        }
    }

    @Override
    public void onRtlPropertiesChanged(int i10) {
        super.onRtlPropertiesChanged(i10);
        Drawable drawable = mThumb;
        if (drawable != null) {
            setThumbPos(getWidth(), drawable, getScale(), Integer.MIN_VALUE);
            invalidate();
        }
    }


    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    protected void onSlidingRefresh(int level) {
        super.onSlidingRefresh(level);

        final float scale = level / 10000.0f;
        Drawable thumb = mThumb;
        if (thumb != null) {
            setThumbPos(getWidth(), thumb, scale, Integer.MIN_VALUE);
            invalidate();
        }
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    void onStartTrackingHover(int i10, int i11, int i12) {
    }

    public void onStartTrackingTouch() {
        mIsDragging = true;
        ValueAnimator valueAnimator = mValueAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    void onStopTrackingHover() {
    }


    public void onStopTrackingTouch() {
        this.mIsDragging = false;
        if (mIsSeamless && isPressed()) {
            mValueAnimator = ValueAnimator.ofInt(super.getProgress(),
                    (int) (Math.round(super.getProgress() / 1000.0f) * 1000.0f));
            mValueAnimator.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_90);
            mValueAnimator.start();
            mValueAnimator.addUpdateListener(
                    valueAnimator -> callSuperSetProgress((Integer) valueAnimator.getAnimatedValue()));
        } else if (mIsSeamless) {
            setProgress(Math.round(super.getProgress() / 1000.0f));
        }
    }


    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }

        if (!isEnabled()) {
            return false;
        }

        switch (action) {
            case android.R.id.accessibilityActionSetProgress: {
                if (!canUserSetProgress()) {
                    return false;
                }
                if (arguments == null || !arguments.containsKey(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
                    return false;
                }
                @SuppressLint("InlinedApi")
                float value = arguments.getFloat(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                if (mIsSeamless) {
                    value = Math.round(value * SCALE_FACTOR);
                }
                return setProgressInternal((int) value, true, true);
            }
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (!canUserSetProgress()) {
                    return false;
                }
                int range = getMax() - getMin();
                int increment = Math.max(1, Math.round((float) range / 20));
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    increment = -increment;
                }

                // Let progress bar handle clamping values.
                final int newProgress = mIsSeamless ?
                        Math.round((getProgress() + increment) * SCALE_FACTOR)
                        : getProgress() + increment;
                if (setProgressInternal(newProgress, true, true)) {
                    onKeyChange();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public void seslSetLevelBarThumbStrokeColor(@ColorInt int i10) {
        Drawable.ConstantState constantState;
        GradientDrawable gradientDrawable;
        Drawable drawable = mLevelBarThumbDrawable;
        if (drawable == null || (constantState = drawable.getConstantState()) == null) {
            return;
        }
        Drawable drawable2 =
                ((DrawableContainer.DrawableContainerState) constantState).getChildren()[1];
        if (!(drawable2 instanceof LayerDrawable) || (gradientDrawable =
                (GradientDrawable) ((LayerDrawable) drawable2).findDrawableByLayerId(R.id.sesl_level_seekbar_thumb_enabled)) == null) {
            return;
        }
        gradientDrawable.setStroke(getResources().getDimensionPixelSize(R.dimen.sesl_seekbar_thumb_stroke), i10);
    }

    /**
     * Sets the overlap colors for the seekbar track
     * @param  bgColor      Overlap color for background tract
     * @param fgColor       Overlap color for activated tract
     */
    public void setDualModeOverlapColor(int bgColor, int fgColor) {
        ColorStateList mOverlapBackgroundColor = colorToColorStateList(bgColor);
        ColorStateList mOverlapForegroundColor = colorToColorStateList(fgColor);
        if (!mOverlapBackgroundColor.equals(mOverlapNormalProgressColor)) {
            mOverlapNormalProgressColor = mOverlapBackgroundColor;
        }
        if (!mOverlapForegroundColor.equals(mOverlapActivatedProgressColor)) {
            mOverlapActivatedProgressColor = mOverlapForegroundColor;
        }

        updateDualColorMode();
        invalidate();
    }


    @Override
    public void setMode(int mode) {
        if (mCurrentMode == mode && mIsSetModeCalled) {
            Log.w(TAG, "Seekbar mode is already set. Do not call this method redundant");
            return;
        }

        super.setMode(mode);
        Context context = getContext();
        mLevelDrawPadding = 0.0f;
        switch (mode) {
            case MODE_STANDARD:
                setProgressTintList(mDefaultActivatedProgressColor);
                setThumbTintList(mDefaultActivatedThumbColor);
                break;

            case MODE_WARNING:
                updateWarningMode(getProgress());
                break;

            case MODE_VERTICAL:
                setThumb(context.getDrawable(mIsLightTheme ?
                        R.drawable.sesl_scrubber_control_anim_light :
                        R.drawable.sesl_scrubber_control_anim_dark));
                setBackgroundResource(R.drawable.sesl_seek_bar_background_borderless);
                break;

            case MODE_SPLIT:
                mSplitProgress = context.getDrawable(R.drawable.sesl_split_seekbar_primary_progress);
                mDivider = context.getDrawable(R.drawable.sesl_split_seekbar_vertical_bar);
                updateSplitProgress();
                break;

            case MODE_EXPAND:
                initializeExpandModeForModeExpand();
                mLevelDrawPadding =
                        getContext().getResources().getDimension(R.dimen.sesl_seekbar_level_progress_padding_start_end);
                break;

            case MODE_EXPAND_VERTICAL:
                initializeExpandVerticalMode();
                break;

            case MODE_LEVEL_BAR: //added in sesl6

                mLevelDrawPadding = context.getResources().getDimension(R.dimen.sesl_seekbar_level_progress_padding_start_end);
                setProgressDrawable(context.getDrawable(R.drawable.sesl_level_seekbar_progress));
                setTickMark(context.getDrawable(R.drawable.sesl_level_seekbar_tick_mark));
                mLevelBarThumbDrawable = context.getDrawable(R.drawable.sesl_level_seekbar_thumb);
                setThumb(mLevelBarThumbDrawable);
                setBackgroundResource(R.drawable.sesl_seek_bar_background_borderless);
                break;
        }
        invalidate();
        mIsSetModeCalled = true;
    }

    /**
     * @deprecated Use {@link #setDualModeOverlapColor(int, int)}
     * @param color
     */
    @Deprecated
    public void setOverlapBackgroundForDualColor(int color) {
        ColorStateList mOverlapColor = colorToColorStateList(color);
        if (!mOverlapColor.equals(mOverlapNormalProgressColor)) {
            mOverlapNormalProgressColor = mOverlapColor;
        }
        mOverlapActivatedProgressColor = mOverlapNormalProgressColor;
        mLargeFont = true;
    }


    /**
     * Sets the point in the tract color where the overlap color will start
     * @param value Set to > {@link #getMin} and < {@link #getMax}
     */
    public void setOverlapPointForDualColor(int value) {
        if (value >= getMax()) {
            Log.e(TAG, "setOverlapPointForDualColor: set value is greater than max value");
            return;
        }

        mSetDualColorMode = true;
        mOverlapPoint = value;
        if (value == NO_OVERLAP) {
            setProgressTintList(mDefaultActivatedProgressColor);
            setThumbTintList(mDefaultActivatedThumbColor);
        } else {
            if (mOverlapBackground == null) {
                initDualOverlapDrawable();
            }
            updateDualColorMode();
        }
        invalidate();
    }

    @Override
    public synchronized void setProgress(int progress) {
        if (mIsSeamless) {
            progress = Math.round(progress * SCALE_FACTOR);
        }
        super.setProgress(progress);
    }

    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void setProgressDrawable(Drawable drawable) {
        super.setProgressDrawable(drawable);
    }

    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public boolean setProgressInternal(int progress, boolean fromUser, boolean animate) {
        boolean superRet = super.setProgressInternal(progress, fromUser, animate);
        updateWarningMode(progress);
        updateDualColorMode();
        return superRet;
    }

    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void setProgressTintList(@Nullable ColorStateList tint) {
        super.setProgressTintList(tint);
        mDefaultActivatedProgressColor = tint;
    }


    private boolean supportIsHoveringUIEnabled() {
        return IS_BASE_SDK_VERSION && SeslViewReflector.isHoveringUIEnabled(this);
    }

    private void setHoverPopupGravity(int gravity) {
        if (IS_BASE_SDK_VERSION) {
            SeslHoverPopupWindowReflector.setGravity(SeslViewReflector.semGetHoverPopup(this,
                    true), gravity);
        }
    }

    private void setHoverPopupOffset(int x, int y) {
        if (IS_BASE_SDK_VERSION) {
            SeslHoverPopupWindowReflector.setOffset(SeslViewReflector.semGetHoverPopup(this,
                    true), x, y);
        }
    }

    private void setHoverPopupDetectTime() {
        if (IS_BASE_SDK_VERSION) {
            SeslHoverPopupWindowReflector.setHoverDetectTime(SeslViewReflector.semGetHoverPopup(this, true), HOVER_DETECT_TIME);
        }
    }

    private void setHoveringPoint(int x, int y) {
        if (IS_BASE_SDK_VERSION) {
            SeslHoverPopupWindowReflector.setHoveringPoint(this, x, y);
        }
    }

    private boolean isHoverPopupTypeUserCustom(int i10) {
        return IS_BASE_SDK_VERSION && i10 == SeslHoverPopupWindowReflector.getField_TYPE_USER_CUSTOM();
    }

    public void setThumbTintColor(int color) {
        ColorStateList mOverlapColor = colorToColorStateList(color);
        if (!mOverlapColor.equals(mDefaultActivatedThumbColor)) {
            mDefaultActivatedThumbColor = mOverlapColor;
        }
    }


    @Override
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void updateDrawableBounds(int w, int h) {
        super.updateDrawableBounds(w, h);
        updateThumbAndTrackPos(w, h);
        updateBoundsForDualColor();
    }

    public void updateHoverPopup() {
        if (IS_BASE_SDK_VERSION) {
            SeslHoverPopupWindowReflector.update(SeslViewReflector.semGetHoverPopup(this, true));
        }
    }

    @Override
    public synchronized void setSecondaryProgress(int secondaryProgress) {
        if (mIsSeamless) {
            secondaryProgress = Math.round(secondaryProgress * SCALE_FACTOR);
        }
        super.setSecondaryProgress(secondaryProgress);
    }

    private void setProgressOverlapTintList(@Nullable ColorStateList tint) {
        super.setProgressTintList(tint);
    }

    /**
     * Sets dragging of the thumb to be seamless or not.
     *
     * @param seamless If set to true, the thumb slides at 1000x ({@link #SCALE_FACTOR}) scale
     *                 distance
     */
    public void setSeamless(boolean seamless) {
        if (mIsSeamless != seamless) {
            mIsSeamless = seamless;
            if (seamless) {
                super.setMax(Math.round(super.getMax() * SCALE_FACTOR));
                super.setMin(Math.round(super.getMin() * SCALE_FACTOR));
                super.setProgress(Math.round(super.getProgress() * SCALE_FACTOR));
                super.setSecondaryProgress(Math.round(super.getSecondaryProgress() * SCALE_FACTOR));
            }else {
                super.setProgress(Math.round(super.getProgress() / SCALE_FACTOR));
                super.setSecondaryProgress(Math.round(super.getSecondaryProgress() / SCALE_FACTOR));
                super.setMax(Math.round(super.getMax() / SCALE_FACTOR));
                super.setMin(Math.round(super.getMin() / SCALE_FACTOR));
            }
        }
    }


    public class SliderDrawable extends Drawable {
        private final long ANIMATION_DURATION = 250L;
        int mAlpha = NO_ALPHA;
        @ColorInt
        int mColor;
        ColorStateList mColorStateList;
        private boolean mIsStateChanged = false;
        private final boolean mIsVertical;
        private final Paint mPaint = new Paint();
        ValueAnimator mPressedAnimator;
        private float mRadius;
        ValueAnimator mReleasedAnimator;
        private final float mSliderMaxWidth;
        private final float mSliderMinWidth;
        private final SliderState mState = new SliderState();


        public SliderDrawable(float minWidth, float maxWidth, ColorStateList color) {
            this(minWidth, maxWidth, color, false);
        }

        public SliderDrawable(float minWidth, float maxWidth, ColorStateList color,
                boolean isVertical) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);

            mColorStateList = color;
            mColor = color.getDefaultColor();
            mPaint.setColor(mColor);

            mPaint.setStrokeWidth(minWidth);
            mSliderMinWidth = minWidth;
            mSliderMaxWidth = maxWidth;
            mRadius = minWidth / 2.0f;

            mIsVertical = isVertical;

            initAnimator();
        }


        private void initAnimator() {
            float tempTrackMinWidth = mSliderMinWidth;
            float tempTrackMaxWidth = mSliderMaxWidth;

            mPressedAnimator = ValueAnimator.ofFloat(tempTrackMinWidth, tempTrackMaxWidth);
            mPressedAnimator.setDuration(ANIMATION_DURATION);
            mPressedAnimator.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_80);
            mPressedAnimator.addUpdateListener(valueAnimator -> {
                final float value = (Float) valueAnimator.getAnimatedValue();
                invalidateTrack(value);
            });

            mReleasedAnimator = ValueAnimator.ofFloat(tempTrackMaxWidth, tempTrackMinWidth);
            mReleasedAnimator.setDuration(ANIMATION_DURATION);
            mReleasedAnimator.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_80);
            mReleasedAnimator.addUpdateListener(valueAnimator -> {
                final float value = (Float) valueAnimator.getAnimatedValue();
                invalidateTrack(value);
            });
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int prevAlpha = mPaint.getAlpha();
            mPaint.setAlpha(modulateAlpha(prevAlpha, mAlpha));
            canvas.save();
            if (mIsVertical) {
                canvas.drawLine(
                        getWidth() / 2.0f,
                        ((getHeight() - getPaddingTop()) - getPaddingBottom()) - mRadius,
                        getWidth() / 2.0f,
                        mRadius,
                        mPaint
                );
            } else {
                canvas.drawLine(
                        mRadius,
                        getHeight() / 2.0f,
                        ((getWidth() - getPaddingLeft()) - getPaddingRight()) - mRadius,
                        getHeight() / 2.0f,
                        mPaint
                );
            }
            canvas.restore();
            mPaint.setAlpha(prevAlpha);
        }

        private int modulateAlpha(int paintAlpha, int alpha) {
            int scale = alpha + (alpha >>> 7);
            return (paintAlpha * scale) >>> 8;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            Paint p = mPaint;
            if (p.getXfermode() == null) {
                int alpha = p.getAlpha();
                if (alpha == 0) {
                    return PixelFormat.TRANSPARENT;
                }
                return alpha == NO_ALPHA ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
            }
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setTintList(@Nullable ColorStateList tint) {
            super.setTintList(tint);
            if (tint != null) {
                mColorStateList = tint;
                mColor = mColorStateList.getDefaultColor();
                mPaint.setColor(mColor);
                invalidateSelf();
            }
        }

        @Override
        public boolean onStateChange(@NonNull int[] stateSet) {
            boolean onStateChange = super.onStateChange(stateSet);
            int colorForState = mColorStateList.getColorForState(stateSet, mColor);
            if (mColor != colorForState) {
                mColor = colorForState;
                mPaint.setColor(colorForState);
                invalidateSelf();
            }

            boolean enabled = false;
            boolean pressed = false;
            for (int i : stateSet) {
                if (i == android.R.attr.state_enabled) {
                    enabled = true;
                } else if (i == android.R.attr.state_pressed) {
                    pressed = true;
                }
            }
            startSliderAnimation(enabled && pressed);
            return onStateChange;
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) mSliderMaxWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) mSliderMaxWidth;
        }

        private void startSliderAnimation(boolean isChanged) {
            if (mIsStateChanged != isChanged) {
                if (isChanged) {
                    startPressedAnimation();
                } else {
                    startReleasedAnimation();
                }
                mIsStateChanged = isChanged;
            }
        }

        private void startPressedAnimation() {
            if (mPressedAnimator.isRunning()) {
                return;
            }
            if (mReleasedAnimator.isRunning()) {
                mReleasedAnimator.cancel();
            }
            mPressedAnimator.setFloatValues(mSliderMinWidth, mSliderMaxWidth);
            mPressedAnimator.start();
        }

        private void startReleasedAnimation() {
            if (mReleasedAnimator.isRunning()) {
                return;
            }
            if (mPressedAnimator.isRunning()) {
                mPressedAnimator.cancel();
            }
            mReleasedAnimator.setFloatValues(mSliderMaxWidth, mSliderMinWidth);
            mReleasedAnimator.start();
        }

        void invalidateTrack(float f10) {
            setStrokeWidth(f10);
            invalidateSelf();
        }

        @Override
        @Nullable
        public ConstantState getConstantState() {
            return mState;
        }


        public void setStrokeWidth(float width) {
            mPaint.setStrokeWidth(width);
            mRadius = width / 2.0f;
        }


        class SliderState extends ConstantState {
            @Override
            @NonNull
            public Drawable newDrawable() {
                return SliderDrawable.this;
            }
            @Override
            public int getChangingConfigurations() {
                return 0;
            }

        }
    }


    public class ThumbDrawable extends Drawable {
        private static final long  PRESSED_DURATION = 100L;
        private static final long RELEASED_DURATION = 300L;
        private int mAlpha = NO_ALPHA;
        @ColorInt
        int mColor;
        private ColorStateList mColorStateList;
        private boolean mIsStateChanged = false;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mPaintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator mThumbPressed;
        private ValueAnimator mThumbReleased;
        private boolean mIsVertical;
        private final int mRadius;
        private int mRadiusForAni;

        public ThumbDrawable(int radius, ColorStateList color, boolean isVertical) {
            mRadiusForAni = radius;
            mRadius = radius;
            mColorStateList = color;
            mColor = color.getDefaultColor();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(mColor);
            mPaint.setStrokeWidth(getContext().getResources()
                    .getDimension(R.dimen.sesl_seekbar_thumb_stroke));

            mPaintFill.setStyle(Paint.Style.FILL);
            mPaintFill.setColor(getContext().getResources()
                    .getColor(R.color.sesl_thumb_control_fill_color_activated));
            mIsVertical = isVertical;

            initAnimation();
        }

        public void initAnimation() {
            mThumbPressed = ValueAnimator.ofFloat(mRadius, 0.0f);
            mThumbPressed.setDuration(PRESSED_DURATION);
            mThumbPressed.setInterpolator(new LinearInterpolator());
            mThumbPressed.addUpdateListener(animation -> {
                final float value = (Float) animation.getAnimatedValue();
                setRadius((int) value);
                invalidateSelf();
            });

            mThumbReleased = ValueAnimator.ofFloat(0.0f, mRadius);
            mThumbReleased.setDuration(RELEASED_DURATION);
            mThumbReleased.setInterpolator(SeslAnimationUtils.SINE_IN_OUT_90);
            mThumbReleased.addUpdateListener(animation -> {
                final float value = (Float) animation.getAnimatedValue();
                setRadius((int) value);
                invalidateSelf();
            });
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int prevAlpha = mPaint.getAlpha();
            mPaint.setAlpha(modulateAlpha(prevAlpha, mAlpha));
            mPaintFill.setAlpha(modulateAlpha(prevAlpha, mAlpha));

            canvas.save();
            if (mIsVertical) {
                canvas.drawCircle(getWidth() / 2.0f,
                        mThumbPosX, mRadiusForAni, mPaintFill);
                canvas.drawCircle(getWidth() / 2.0f,
                        mThumbPosX, mRadiusForAni, mPaint);
            } else {
                canvas.drawCircle(mThumbPosX, getHeight() / 2.0f,
                        mRadiusForAni, mPaintFill);
                canvas.drawCircle(mThumbPosX, getHeight() / 2.0f,
                        mRadiusForAni, mPaint);
            }
            canvas.restore();

            mPaint.setAlpha(prevAlpha);
            mPaintFill.setAlpha(prevAlpha);
        }


        @Override
        public int getIntrinsicHeight() {
            return mRadius * 2;
        }

        @Override
        public int getIntrinsicWidth() {
            return mRadius * 2;
        }

        private int modulateAlpha(int paintAlpha, int alpha) {
            int scale = alpha + (alpha >>> 7);
            return (paintAlpha * scale) >>> 8;
        }

        private void setRadius(int radius) {
            mRadiusForAni = radius;
        }

        private void startPressedAnimation() {
            if (mThumbPressed.isRunning()) {
                return;
            }
            if (mThumbReleased.isRunning()) {
                mThumbReleased.cancel();
            }
            mThumbPressed.start();
        }

        private void startReleasedAnimation() {
            if (mThumbReleased.isRunning()) {
                return;
            }
            if (mThumbPressed.isRunning()) {
                mThumbPressed.cancel();
            }
            mThumbReleased.start();
        }

        private void startThumbAnimation(boolean isChanged) {
            if (mIsStateChanged != isChanged) {
                if (isChanged) {
                    startPressedAnimation();
                } else {
                    startReleasedAnimation();
                }
                mIsStateChanged = isChanged;
            }
        }

        @Override
        public int getOpacity() {
            Paint paint = mPaint;
            if (paint.getXfermode() == null) {
                int alpha = paint.getAlpha();
                if (alpha == 0) {
                    return PixelFormat.TRANSPARENT;
                }
                return alpha == NO_ALPHA ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
            }
            return PixelFormat.TRANSLUCENT;
        }


        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public boolean onStateChange(@NonNull int[] stateSet) {
            boolean changed = super.onStateChange(stateSet);
            final int color = mColorStateList.getColorForState(stateSet, mColor);
            if (mColor != color) {
                mColor = color;
                mPaint.setColor(color);
                invalidateSelf();
            }

            boolean enabled = false;
            boolean pressed = false;
            for (int i : stateSet) {
                if (i == android.R.attr.state_enabled) {
                    enabled = true;
                } else if (i ==  android.R.attr.state_pressed) {
                    pressed = true;
                }
            }

            startThumbAnimation(enabled && pressed);

            return changed;
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public void setTintList(@Nullable ColorStateList tint) {
            super.setTintList(tint);
            if (tint != null) {
                mColorStateList = tint;
                mColor = mColorStateList.getDefaultColor();
                mPaint.setColor(mColor);
                invalidateSelf();
            }
        }
    }

    @Override
    public int getPaddingLeft() {
        final boolean isInVerticalMode = mCurrentMode == MODE_VERTICAL
                || mCurrentMode == MODE_EXPAND_VERTICAL;
        return isInVerticalMode ? super.getPaddingTop()
                : super.getPaddingLeft();
    }

    @Override
    public int getPaddingTop() {
        final boolean isInVerticalMode = mCurrentMode == MODE_VERTICAL
                || mCurrentMode == MODE_EXPAND_VERTICAL;
        return isInVerticalMode ? super.getPaddingLeft()
                : super.getPaddingTop();
    }

    @Override
    public int getPaddingRight() {
        final boolean isInVerticalMode = mCurrentMode == MODE_VERTICAL
                || mCurrentMode == MODE_EXPAND_VERTICAL;
        return isInVerticalMode ? super.getPaddingBottom()
                : super.getPaddingRight();
    }

    @Override
    public int getPaddingBottom() {
        final boolean isInVerticalMode = mCurrentMode == MODE_VERTICAL
                || mCurrentMode == MODE_EXPAND_VERTICAL;
        return isInVerticalMode ? super.getPaddingRight()
                : super.getPaddingBottom();
    }
}