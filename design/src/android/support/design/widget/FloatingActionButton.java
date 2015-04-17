/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.design.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.design.R;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Floating action buttons are used for a special type of promoted action. They are distinguished
 * by a circled icon floating above the UI and have special motion behaviors related to morphing,
 * launching, and the transferring anchor point.
 *
 * Floating action buttons come in two sizes: the default, which should be used in most cases, and
 * the mini, which should only be used to create visual continuity with other elements on the
 * screen.
 */
@CoordinatorLayout.DefaultBehavior(FloatingActionButton.Behavior.class)
public class FloatingActionButton extends ImageView {

    // These values must match those in the attrs declaration
    private static final int SIZE_MINI = 1;
    private static final int SIZE_NORMAL = 0;

    private ColorStateList mBackgroundTint;
    private PorterDuff.Mode mBackgroundTintMode;

    private int mRippleColor;
    private int mSize;
    private int mContentPadding;

    private final Rect mShadowPadding;

    private final FloatingActionButtonImpl mImpl;

    public FloatingActionButton(Context context) {
        this(context, null);
    }

    public FloatingActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mShadowPadding = new Rect();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.FloatingActionButton, defStyleAttr,
                R.style.Widget_Design_FloatingActionButton);
        Drawable background = a.getDrawable(R.styleable.FloatingActionButton_android_background);
        mBackgroundTint = a.getColorStateList(R.styleable.FloatingActionButton_backgroundTint);
        mBackgroundTintMode = parseTintMode(a.getInt(
                R.styleable.FloatingActionButton_backgroundTintMode, -1), null);
        mRippleColor = a.getColor(R.styleable.FloatingActionButton_rippleColor, 0);
        mSize = a.getInt(R.styleable.FloatingActionButton_fabSize, SIZE_NORMAL);
        final float elevation = a.getDimension(R.styleable.FloatingActionButton_elevation, 0f);
        final float pressedTranslationZ = a.getDimension(
                R.styleable.FloatingActionButton_pressedTranslationZ, 0f);
        a.recycle();

        final ShadowViewDelegate delegate = new ShadowViewDelegate() {
            @Override
            public float getRadius() {
                return getSizeDimension() / 2f;
            }

            @Override
            public void setShadowPadding(int left, int top, int right, int bottom) {
                mShadowPadding.set(left, top, right, bottom);

                setPadding(left + mContentPadding, top + mContentPadding,
                        right + mContentPadding, bottom + mContentPadding);
            }

            @Override
            public void setBackgroundDrawable(Drawable background) {
                FloatingActionButton.super.setBackgroundDrawable(background);
            }
        };

        if (Build.VERSION.SDK_INT >= 21) {
            mImpl = new FloatingActionButtonLollipop(this, delegate);
        } else {
            mImpl = new FloatingActionButtonEclairMr1(this, delegate);
        }

        final int maxContentSize = (int) getResources().getDimension(R.dimen.fab_content_size);
        mContentPadding = (getSizeDimension() - maxContentSize) / 2;

        mImpl.setBackgroundDrawable(background, mBackgroundTint,
                mBackgroundTintMode, mRippleColor);
        mImpl.setElevation(elevation);
        mImpl.setPressedTranslationZ(pressedTranslationZ);

        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int preferredSize = getSizeDimension();

        final int w = resolveAdjustedSize(preferredSize, widthMeasureSpec);
        final int h = resolveAdjustedSize(preferredSize, heightMeasureSpec);

        // As we want to stay circular, we set both dimensions to be the
        // smallest resolved dimension
        final int d = Math.min(w, h);

        // We add the shadow's padding to the measured dimension
        setMeasuredDimension(
                d + mShadowPadding.left + mShadowPadding.right,
                d + mShadowPadding.top + mShadowPadding.bottom);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateOffset();
    }

    /**
     * Set the ripple color for this {@link FloatingActionButton}.
     * <p>
     * When running on devices with KitKat or below, we draw a fill rather than a ripple.
     *
     * @param color ARGB color to use for the ripple.
     */
    public void setRippleColor(int color) {
        if (mRippleColor != color) {
            mRippleColor = color;
            mImpl.setRippleColor(color);
        }
    }

    /**
     * Return the tint applied to the background drawable, if specified.
     *
     * @return the tint applied to the background drawable
     * @see #setBackgroundTintList(ColorStateList)
     */
    @Nullable
    @Override
    public ColorStateList getBackgroundTintList() {
        return mBackgroundTint;
    }

    /**
     * Applies a tint to the background drawable. Does not modify the current tint
     * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     */
    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        mImpl.setBackgroundTintList(tint);
    }


    /**
     * Return the blending mode used to apply the tint to the background
     * drawable, if specified.
     *
     * @return the blending mode used to apply the tint to the background
     *         drawable
     * @see #setBackgroundTintMode(PorterDuff.Mode)
     */
    @Nullable
    @Override
    public PorterDuff.Mode getBackgroundTintMode() {
        return mBackgroundTintMode;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setBackgroundTintList(ColorStateList)}} to the background
     * drawable. The default mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     */
    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        mImpl.setBackgroundTintMode(tintMode);
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (mImpl != null) {
            mImpl.setBackgroundDrawable(
                background, mBackgroundTint, mBackgroundTintMode, mRippleColor);
        }
    }

    final int getSizeDimension() {
        switch (mSize) {
            case SIZE_MINI:
                return getResources().getDimensionPixelSize(R.dimen.fab_size_mini);
            case SIZE_NORMAL:
            default:
                return getResources().getDimensionPixelSize(R.dimen.fab_size_normal);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        mImpl.onDrawableStateChanged(getDrawableState());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        mImpl.jumpDrawableToCurrentState();
    }

    private static int resolveAdjustedSize(int desiredSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                // Parent says we can be as big as we want. Just don't be larger
                // than max size imposed on ourselves.
                result = desiredSize;
                break;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be larger than
                // the max size imposed on ourselves.
                result = Math.min(desiredSize, specSize);
                break;
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    static PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
        switch (value) {
            case 3:
                return PorterDuff.Mode.SRC_OVER;
            case 5:
                return PorterDuff.Mode.SRC_IN;
            case 9:
                return PorterDuff.Mode.SRC_ATOP;
            case 14:
                return PorterDuff.Mode.MULTIPLY;
            case 15:
                return PorterDuff.Mode.SCREEN;
            default:
                return defaultMode;
        }
    }

    /**
     * Pre-Lollipop we use padding so that the shadow has enough space to be drawn. This method
     * offsets our layout position so that we're positioned correctly if we're on one of
     * our parent's edges.
     */
    private void updateOffset() {
        if (mShadowPadding.centerX() != 0 || mShadowPadding.centerY() != 0) {
            int offsetTB = 0, offsetLR = 0;

            if (isOnRightParentEdge()) {
                offsetLR = mShadowPadding.right;
            } else if (isOnLeftParentEdge()) {
                offsetLR = -mShadowPadding.left;
            }
            if (isOnBottomParentEdge()) {
                offsetTB = mShadowPadding.bottom;
            } else if (isOnTopParentEdge()) {
                offsetTB = -mShadowPadding.top;
            }

            offsetTopAndBottom(offsetTB);
            offsetLeftAndRight(offsetLR);
        }
    }

    private boolean isOnLeftParentEdge() {
        final int margin = getLayoutParams() instanceof ViewGroup.MarginLayoutParams ?
                ((ViewGroup.MarginLayoutParams) getLayoutParams()).leftMargin : 0;
        return getLeft() <= margin;
    }

    private boolean isOnTopParentEdge() {
        final int margin = getLayoutParams() instanceof ViewGroup.MarginLayoutParams ?
                ((ViewGroup.MarginLayoutParams) getLayoutParams()).topMargin : 0;
        return getTop() <= margin;
    }

    private boolean isOnRightParentEdge() {
        final int margin = getLayoutParams() instanceof ViewGroup.MarginLayoutParams ?
                ((ViewGroup.MarginLayoutParams) getLayoutParams()).rightMargin : 0;

        ViewParent parent = getParent();
        if (parent instanceof View) {
            return getRight() >= (((View) getParent()).getWidth() - margin);
        }
        return false;
    }

    private boolean isOnBottomParentEdge() {
        final int margin = getLayoutParams() instanceof ViewGroup.MarginLayoutParams ?
                ((ViewGroup.MarginLayoutParams) getLayoutParams()).bottomMargin : 0;

        ViewParent parent = getParent();
        if (parent instanceof View) {
            return getBottom() >= (((View) getParent()).getHeight() - margin);
        }
        return false;
    }

    /**
     * Behavior designed for use with {@link FloatingActionButton} instances. It's main function
     * is to move {@link FloatingActionButton} views so that any displayed {@link Snackbar}s do
     * not cover them.
     */
    public static class Behavior extends CoordinatorLayout.Behavior<FloatingActionButton> {
        // We only support the FAB <> Snackbar shift movement on Honeycomb and above. This is
        // because we can use view translation properties which greatly simplifies the code.
        private static final boolean SNACKBAR_BEHAVIOR_ENABLED = Build.VERSION.SDK_INT >= 11;

        private float mTranslationY;
        private Set<WeakReference<View>> mSnackbars;

        @Override
        public boolean layoutDependsOn(CoordinatorLayout parent,
                FloatingActionButton child,
                View dependency) {
            // We're dependent on all SnackbarLayouts
            if (SNACKBAR_BEHAVIOR_ENABLED && dependency instanceof Snackbar.SnackbarLayout) {
                if (!containsView(dependency)) {
                    if (mSnackbars == null) mSnackbars = new HashSet<>();
                    mSnackbars.add(new WeakReference<>(dependency));
                }
                cleanUpSet();
                return true;
            }
            return false;
        }

        @Override
        public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton child,
                View snackbar) {
            updateFabTranslation(parent, child, snackbar);
            return false;
        }

        private void updateFabTranslation(CoordinatorLayout parent, FloatingActionButton fab,
                View snackbar) {
            final float translationY = getTranslationYForFab(parent, fab);
            if (translationY != mTranslationY) {
                // First, cancel any current animation
                ViewCompat.animate(fab).cancel();

                if (Math.abs(translationY - mTranslationY) == snackbar.getHeight()) {
                    // If we're travelling by the height of the Snackbar then we probably need to
                    // animate to the value
                    ViewCompat.animate(fab).translationY(translationY)
                            .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
                } else {
                    // Else we'll set use setTranslationY
                    ViewCompat.setTranslationY(fab, translationY);
                }
                mTranslationY = translationY;
            }
        }

        private float getTranslationYForFab(CoordinatorLayout parent, FloatingActionButton fab) {
            float minOffset = 0;
            if (mSnackbars != null && !mSnackbars.isEmpty()) {
                for (WeakReference<View> viewRef : mSnackbars) {
                    final View view = viewRef.get();
                    if (view != null && parent.doViewsOverlap(fab, view)) {
                        minOffset = Math.min(minOffset,
                                ViewCompat.getTranslationY(view) - view.getHeight());
                    }
                }
            }
            return minOffset;
        }

        private void cleanUpSet() {
            if (mSnackbars != null && !mSnackbars.isEmpty()) {
                for (final Iterator<WeakReference<View>> i = mSnackbars.iterator(); i.hasNext();) {
                    WeakReference<View> ref = i.next();
                    if (ref == null || ref.get() == null) {
                        i.remove();
                    }
                }
            }
        }

        private boolean containsView(View dependency) {
            if (mSnackbars != null && !mSnackbars.isEmpty()) {
                for (WeakReference<View> viewRef : mSnackbars) {
                    if (viewRef.get() == dependency) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
