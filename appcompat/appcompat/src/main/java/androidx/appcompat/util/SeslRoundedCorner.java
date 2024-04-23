/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.appcompat.util;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appcompat.R;
import androidx.core.content.res.ResourcesCompat;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung Rounded Corners class.
 */
public class SeslRoundedCorner {

    public static final int ROUNDED_CORNER_NONE = 0;
    public static final int ROUNDED_CORNER_ALL = 15;
    public static final int ROUNDED_CORNER_TOP_LEFT = 1;
    public static final int ROUNDED_CORNER_TOP_RIGHT = 2;
    public static final int ROUNDED_CORNER_BOTTOM_LEFT = 4;
    public static final int ROUNDED_CORNER_BOTTOM_RIGHT = 8;

    Drawable mTopLeftRound;
    Drawable mTopRightRound;
    Drawable mBottomLeftRound;
    Drawable mBottomRightRound;
    @ColorInt
    private int mTopLeftRoundColor;
    @ColorInt
    private int mTopRightRoundColor;
    @ColorInt
    private int mBottomLeftRoundColor;
    @ColorInt
    private int mBottomRightRoundColor;

    Rect mRoundedCornerBounds = new Rect();
    int mRoundedCornerMode;
    int mRoundRadius;

    public SeslRoundedCorner(@NonNull Context context) {
        this(context, false);
    }

    public SeslRoundedCorner(@NonNull Context context, boolean isMutate) {
        Resources resources = context.getResources();

        mRoundRadius = resources.getDimensionPixelSize(R.dimen.sesl_rounded_corner_radius);

        final boolean darkTheme = !SeslMisc.isLightTheme(context);
        final Resources.Theme theme = context.getTheme();

        if (isMutate) {
            mTopLeftRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_top_left_round, theme).mutate();
            mTopRightRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_top_right_round, theme).mutate();
            mBottomLeftRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_bottom_left_round, theme).mutate();
            mBottomRightRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_bottom_right_round, theme).mutate();
        } else {
            mTopLeftRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_top_left_round
                    , theme);
            mTopRightRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_top_right_round, theme);
            mBottomLeftRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_bottom_left_round, theme);
            mBottomRightRound = ResourcesCompat.getDrawable(resources,
                    R.drawable.sesl_bottom_right_round, theme);
        }

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.roundedCornerColor,
                typedValue, true);
        final int roundColor;
        if (typedValue.resourceId > 0) {
            roundColor = ResourcesCompat.getColor(context.getResources(), typedValue.resourceId,
                    theme);
        }else {
            roundColor = resources.getColor(darkTheme? R.color.sesl_round_and_bgcolor_dark :
                    R.color.sesl_round_and_bgcolor_light);
        }

        mBottomRightRoundColor = roundColor;
        mBottomLeftRoundColor = roundColor;
        mTopRightRoundColor = roundColor;
        mTopLeftRoundColor = roundColor;

        PorterDuffColorFilter pdcf = new PorterDuffColorFilter(mTopLeftRoundColor,
                PorterDuff.Mode.SRC_IN);
        mTopLeftRound.setColorFilter(pdcf);
        mTopRightRound.setColorFilter(pdcf);
        mBottomLeftRound.setColorFilter(pdcf);
        mBottomRightRound.setColorFilter(pdcf);
    }

    public void setRoundedCorners(int corners) {
        if ((corners & (-16 )) == 0) {
            mRoundedCornerMode = corners;
        } else {
            throw new IllegalArgumentException("Use wrong rounded corners to the param, corners ="
                    + " " + corners);
        }
    }

    public int getRoundedCorners() {
        return mRoundedCornerMode;
    }

    public void setRoundedCornerColor(int corners, @ColorInt int color) {
        if (corners == ROUNDED_CORNER_NONE) {
            throw new IllegalArgumentException("There is no rounded corner on = " + this);
        } else if ((corners & (-16)) == 0) {
            PorterDuffColorFilter pdcf = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            if ((corners & ROUNDED_CORNER_TOP_LEFT) != 0) {
                mTopLeftRoundColor = color;
                mTopLeftRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_TOP_RIGHT) != 0) {
                mTopRightRoundColor = color;
                mTopRightRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
                mBottomLeftRoundColor = color;
                mBottomLeftRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_BOTTOM_RIGHT) != 0) {
                mBottomRightRoundColor = color;
                mBottomRightRound.setColorFilter(pdcf);
            }
        } else {
            throw new IllegalArgumentException("Use wrong rounded corners to the param, corners ="
                    + " " + corners);
        }
    }

    @ColorInt
    public int getRoundedCornerColor(int corner) {
        if (corner == ROUNDED_CORNER_NONE) {
            throw new IllegalArgumentException("There is no rounded corner on = " + this);
        } else if (corner != ROUNDED_CORNER_TOP_LEFT
                && corner != ROUNDED_CORNER_TOP_RIGHT
                && corner != ROUNDED_CORNER_BOTTOM_LEFT
                && corner != ROUNDED_CORNER_BOTTOM_RIGHT) {
            throw new IllegalArgumentException("Use multiple rounded corner as param on = " + this);
        }

        if ((corner & ROUNDED_CORNER_TOP_LEFT) != 0) {
            return mTopLeftRoundColor;
        }
        if ((corner & ROUNDED_CORNER_TOP_RIGHT) != 0) {
            return mTopRightRoundColor;
        }
        if ((corner & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
            return mBottomLeftRoundColor;
        }
        return mBottomRightRoundColor;
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public int getRoundedCornerRadius() {
        return mRoundRadius;
    }

    public void drawRoundedCorner(Canvas canvas) {
        canvas.getClipBounds(mRoundedCornerBounds);
        drawRoundedCornerInternal(canvas);
    }

    public void drawRoundedCorner(View view, Canvas canvas) {
        int left;
        int top;
        if (view.getTranslationY() != 0.0f) {
            left = Math.round(view.getX());
            top = Math.round(view.getY());
            canvas.translate(view.getX() - left + 0.5f, view.getY() - top + 0.5f);
        } else {
            left = view.getLeft();
            top = view.getTop();
        }

        mRoundedCornerBounds.set(left, top, view.getWidth() + left, top + view.getHeight());
        drawRoundedCornerInternal(canvas);
    }

    private void drawRoundedCornerInternal(Canvas canvas) {
        final int left = mRoundedCornerBounds.left;
        final int right = mRoundedCornerBounds.right;
        final int top = mRoundedCornerBounds.top;
        final int bottom = mRoundedCornerBounds.bottom;

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_LEFT) != 0) {
            mTopLeftRound.setBounds(left, top, left + mRoundRadius, mRoundRadius + top);
            mTopLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_RIGHT) != 0) {
            mTopRightRound.setBounds(right - mRoundRadius, top, right, mRoundRadius + top);
            mTopRightRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
            mBottomLeftRound.setBounds(left, bottom - mRoundRadius, mRoundRadius + left, bottom);
            mBottomLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_RIGHT) != 0) {
            mBottomRightRound.setBounds(right - mRoundRadius, bottom - mRoundRadius, right, bottom);
            mBottomRightRound.draw(canvas);
        }
    }
}
