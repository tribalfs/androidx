/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.appcompat.graphics.drawable;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.R;
import androidx.core.graphics.ColorUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@RequiresApi(api = 29)
public class SeslRecoilDrawable extends LayerDrawable {

    private static final int DEFAULT_TINT_COLOR = Color.parseColor("#66FFFFFF");
    private static final int RADIUS_AUTO = -1;
    private static final String TAG = "SeslRecoilDrawable";
    private static final int ID_MASK = android.R.id.mask;
    private static final Long PRESS_ANIMATION_DURATION = 100L;
    private static final Long RELEASE_ANIMATION_DURATION = 350L;
    private static final Interpolator PRESS_INTERPOLATOR = new LinearInterpolator();
    private static final Interpolator RELEASE_INTERPOLATOR = new PathInterpolator(0.17f, 0.17f, 0.67f, 1.0f);

    private final ValueAnimator mAnimator;
    private float mHotspotPointX;
    private float mHotspotPointY;
    private boolean mIsActive;
    private boolean mIsPressed;
    private Drawable mMask;
    private long mPressDuration;
    private int mRadius;
    private long mReleaseDuration;
    private int mTintColor;

    public SeslRecoilDrawable() {
        super(new Drawable[0]);
        this.mIsActive = false;
        this.mIsPressed = false;
        mAnimator = ValueAnimator.ofFloat(0.0f);
        init();
    }

    private void drawHotspot(Canvas canvas) {
        Rect rect = new Rect();
        getHotspotBounds(rect);
        if (rect.height() > 0) {
            mHotspotPointX = rect.centerX();
            mHotspotPointY = rect.centerY();
        }
        canvas.translate(mHotspotPointX, mHotspotPointY);
        Paint paint = new Paint();
        paint.setColor(getAnimatingTintColor());
        canvas.drawCircle(0.0f, 0.0f, getRadius(), paint);
        canvas.translate(-mHotspotPointX, -mHotspotPointY);
    }

    private int getAnimatingTintColor() {
        return ColorUtils.setAlphaComponent(mTintColor,
                (int) ((Float) mAnimator.getAnimatedValue() * Color.valueOf(this.mTintColor).alpha() * 255.0f));
    }

    private float getRadius() {
        int i = this.mRadius;
        if (i > 0) {
            return i;
        }
        Rect rect = new Rect();
        getHotspotBounds(rect);
        int height = rect.height() / 2;
        return height > 0 ? height : getBounds().height() / 2f;
    }

    private void init() {
        mPressDuration = PRESS_ANIMATION_DURATION;
        mReleaseDuration = RELEASE_ANIMATION_DURATION;
        initAnimator();
        setPaddingMode(1);
    }

    private void initAnimator() {
        mAnimator.addUpdateListener(animation -> {
            setTint();
            invalidateSelf();
        });
    }

    private boolean isDrawHotspot() {
        return getNumberOfLayers() <= 0;
    }

    private void setActive(boolean isLongPressed, boolean isFocused, boolean isPressed) {
        boolean isActive = isLongPressed || isFocused || isPressed;
        if (isPressed) {
            mIsPressed = true;
            startEnterAnimation(1.0f);
        } else if (isFocused) {
            startEnterAnimation(0.6f);
        } else if (isLongPressed) {
            startEnterAnimation(0.8f);
        } else if (mIsActive && !isActive) {
            startExitAnimation();
        }
        mIsActive = isActive;
        mIsPressed = isPressed;
    }

    private void setTint() {
        BlendMode blendMode;
        int animatingTintColor = getAnimatingTintColor();
        Drawable findDrawableByLayerId = findDrawableByLayerId(ID_MASK);
        if (findDrawableByLayerId != null) {
            findDrawableByLayerId.setTint(animatingTintColor);
            return;
        }
        blendMode = BlendMode.HARD_LIGHT;
        setTintBlendMode(blendMode);
        setTint(animatingTintColor);
    }

    private void startEnterAnimation(float f) {
        if (mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator.setFloatValues((Float) mAnimator.getAnimatedValue(), f);
        mAnimator.setInterpolator(PRESS_INTERPOLATOR);
        mAnimator.setDuration(mPressDuration);
        mAnimator.start();
    }

    private void startExitAnimation() {
        if (mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator.setFloatValues(!this.mIsPressed ? (Float) mAnimator.getAnimatedValue() : 1.0f, 0.0f);
        mAnimator.setInterpolator(RELEASE_INTERPOLATOR);
        mAnimator.setDuration(this.mReleaseDuration);
        mAnimator.start();
    }

    private void updateMaskLayer() {
        BlendMode blendMode;
        Drawable findDrawableByLayerId = findDrawableByLayerId(ID_MASK);
        if (findDrawableByLayerId != null) {
            findDrawableByLayerId.setTint(0);
            blendMode = BlendMode.SRC_IN;
            findDrawableByLayerId.setTintBlendMode(blendMode);
        }
    }

    private void updateStateFromTypedArray(TypedArray ta) {
        this.mTintColor = ta.getColor(R.styleable.SeslRecoil_seslRecoilColor, DEFAULT_TINT_COLOR);
        mRadius = ta.getDimensionPixelSize(R.styleable.SeslRecoil_seslRecoilRadius, RADIUS_AUTO);
        mMask = ta.getDrawable(R.styleable.SeslRecoil_seslRecoilMask);
        if (mMask != null) {
            setId(addLayer(mMask), ID_MASK);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int saveCount = canvas.getSaveCount();
        if (isDrawHotspot()) {
            drawHotspot(canvas);
        } else {
            super.draw(canvas);
        }
        canvas.restoreToCount(saveCount);
    }

    @Nullable
    @Override
    public Drawable.ConstantState getConstantState() {
        return null;
    }

    @Override 
    public boolean hasFocusStateSpecified() {
        return true;
    }

    @Override
    public void inflate(@NonNull Resources resources, @NonNull XmlPullParser xmlPullParser, @NonNull AttributeSet attributeSet, @Nullable Resources.Theme theme) {
        TypedArray ta = resources.obtainAttributes(attributeSet, R.styleable.SeslRecoil);
        try {
            updateStateFromTypedArray(ta);
            super.inflate(resources, xmlPullParser, attributeSet, theme);
            updateMaskLayer();
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Failed to parse!!", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            ta.recycle();
        }
    }

    public boolean isActive() {
        if (this.mIsActive) {
            return true;
        }
        return mAnimator.isRunning();
    }

    @Override
    public boolean isProjected() {
        return isDrawHotspot();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public boolean onStateChange(@NonNull int[] iArr) {
        boolean isPressed = false;
        boolean isFocused = false;
        boolean isHovered = false;
        for (int i : iArr) {
            if (i == android.R.attr.state_pressed) {
                isPressed = true;
            } else if (i == android.R.attr.state_hovered) {
                isHovered = true;
            } else if (i == android.R.attr.state_focused) {
                isFocused = true;
            }
        }
        setActive(isPressed, isFocused, isHovered);
        return super.onStateChange(iArr);
    }

    public void setCancel() {
        setState(new int[0]);
    }

    @Override
    public void setHotspot(float f, float f4) {
        super.setHotspot(f, f4);
        mHotspotPointX = f;
        mHotspotPointY = f4;
    }

    @Override
    public void setTintBlendMode(@NonNull BlendMode blendMode) {
        super.setTintBlendMode(blendMode);
        Drawable findDrawableByLayerId = findDrawableByLayerId(ID_MASK);
        if (findDrawableByLayerId != null) {
            findDrawableByLayerId.setTintBlendMode(BlendMode.SRC_IN);
        }
    }

    @Override
    public void setTintList(@NonNull ColorStateList colorStateList) {
        super.setTintList(colorStateList);
        Drawable findDrawableByLayerId = findDrawableByLayerId(ID_MASK);
        if (findDrawableByLayerId != null) {
            findDrawableByLayerId.setTint(getAnimatingTintColor());
        }
    }

    public SeslRecoilDrawable(@NonNull Drawable[] drawableArr) {
        super(drawableArr);
        this.mIsActive = false;
        this.mIsPressed = false;
        mAnimator = ValueAnimator.ofFloat(0.0f);
        init();
    }


    public SeslRecoilDrawable(@ColorInt int i, @NonNull Drawable[] drawableArr, @Nullable Drawable drawable) {
        this(drawableArr);
        init();
        this.mTintColor = i;
        if (drawable != null) {
            this.mMask = drawable;
            setId(addLayer(drawable), ID_MASK);
        }
    }
}