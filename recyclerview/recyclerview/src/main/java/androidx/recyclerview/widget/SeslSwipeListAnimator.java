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

package androidx.recyclerview.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung SwipeListAnimator class.
 */
public class SeslSwipeListAnimator {
    private static final String TAG = "SeslSwipeListAnimator";
    private static final int DEFAULT_ANIMATION_CANCEL_DURATION = 250;
    private static final int DEFAULT_ANIMATION_DURATION = 100;

    private final int DIRECTION_LTR = 0;
    private final int DIRECTION_RTL = 1;

    private final int DEFAULT_DRAWABLE_PADDING = 10;
    private final int DEFAULT_TEXT_SIZE = 17;

    private final int DEFAULT_LEFT_COLOR = Color.parseColor("#6ebd52");
    private final int DEFAULT_RIGHT_COLOR = Color.parseColor("#56c0e5");
    private final int DEFAULT_TEXT_COLOR = Color.parseColor("#fafafa");

    private Paint mBgLeftToRight = null;
    private Paint mBgRightToLeft = null;
    private final Context mContext;
    private BitmapDrawable mDrawSwipeBitmapDrawable = null;
    private final RecyclerView mRecyclerView;
    private Bitmap mSwipeBitmap = null;
    private SwipeConfiguration mSwipeConfiguration;
    private Rect mSwipeRect = null;
    private Paint mTextPaint = null;
    public float mLastRectAlpha = 0.0f;

    public static class SwipeConfiguration {
        public int UNSET_VALUE = -1;

        @Nullable
        public Drawable drawableLeftToRight;

        @Nullable
        public Drawable drawableRightToLeft;

        @Nullable
        public String textLeftToRight;

        @Nullable
        public String textRightToLeft;

        public int colorLeftToRight = UNSET_VALUE;
        public int colorRightToLeft = UNSET_VALUE;
        public int drawablePadding = UNSET_VALUE;
        public int textSize = UNSET_VALUE;
        public int textColor = UNSET_VALUE;
    }

    public SeslSwipeListAnimator(@NonNull RecyclerView recyclerView, @NonNull Context context) {
        mContext = context;
        mRecyclerView = recyclerView;
    }

    public void setSwipeConfiguration(@NonNull SwipeConfiguration swipeConfiguration) {
        mSwipeConfiguration = swipeConfiguration;

        if (swipeConfiguration.textLeftToRight == null) {
            mSwipeConfiguration.textLeftToRight = " ";
        }
        if (mSwipeConfiguration.textRightToLeft == null) {
            mSwipeConfiguration.textRightToLeft = " ";
        }

        if (mSwipeConfiguration.colorLeftToRight == mSwipeConfiguration.UNSET_VALUE) {
            mSwipeConfiguration.colorLeftToRight = DEFAULT_LEFT_COLOR;
        }
        if (mSwipeConfiguration.colorRightToLeft == mSwipeConfiguration.UNSET_VALUE) {
            mSwipeConfiguration.colorRightToLeft = DEFAULT_RIGHT_COLOR;
        }
        if (mSwipeConfiguration.textColor == mSwipeConfiguration.UNSET_VALUE) {
            mSwipeConfiguration.textColor = DEFAULT_TEXT_COLOR;
        }

        if (mSwipeConfiguration.textSize == mSwipeConfiguration.UNSET_VALUE) {
            mSwipeConfiguration.textSize = DEFAULT_TEXT_SIZE;
        }
        if (mSwipeConfiguration.drawablePadding == mSwipeConfiguration.UNSET_VALUE) {
            mSwipeConfiguration.drawablePadding = DEFAULT_DRAWABLE_PADDING;
        }

        mBgLeftToRight = initPaintWithAlphaAntiAliasing(mSwipeConfiguration.colorLeftToRight);
        mBgRightToLeft = initPaintWithAlphaAntiAliasing(mSwipeConfiguration.colorRightToLeft);

        mTextPaint = initPaintWithAlphaAntiAliasing(mSwipeConfiguration.textColor);
        mTextPaint.setTextSize(convertDipToPixels(mContext, mSwipeConfiguration.textSize));

        if (Build.VERSION.SDK_INT >= 34) {
            mTextPaint.setTypeface(
                    Typeface.create(Typeface.create("sec", Typeface.NORMAL), 400, false));
        }else {
            mTextPaint.setTypeface(
                    Typeface.create(mContext.getString(androidx.appcompat.R.string.sesl_font_family_regular), Typeface.NORMAL));
        }

    }

    private Paint initPaintWithAlphaAntiAliasing(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        return paint;
    }

    private int convertDipToPixels(Context context, int dip) {
        final float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dip * density);
    }

    public void doMoveAction(@NonNull Canvas c, @NonNull View viewForeground, float deltaX, boolean isCurrentlyActive) {
        Log.i(TAG, "doMoveAction: viewForeground = " + viewForeground +
                " deltaX = " + deltaX + ", isCurrentlyActive = " + isCurrentlyActive);

        if (deltaX != 0f || isCurrentlyActive) {
            Log.i(TAG, "doMoveAction: #1 drawRectToBitmapCanvas");

            drawRectToBitmapCanvas(viewForeground, deltaX, deltaX / viewForeground.getWidth());
            viewForeground.setTranslationX(deltaX);
            float centerX = viewForeground.getWidth() / 2.0f;
            float absDeltaX = Math.abs(deltaX);
            float opacity = (Math.min(absDeltaX, centerX) / centerX);
            viewForeground.setAlpha(Math.min(1.0f - opacity, 1.0f));

            mDrawSwipeBitmapDrawable = getBitmapDrawableToSwipeBitmap();
            if (mDrawSwipeBitmapDrawable != null) {
                mRecyclerView.invalidate(mDrawSwipeBitmapDrawable.getBounds());
                Log.i(TAG, "doMoveAction: draw");
                mDrawSwipeBitmapDrawable.draw(c);
            }
        } else {
            Log.i(TAG, "doMoveAction: #2 return");
            clearSwipeAnimation(viewForeground);
        }
    }

    private int calculateTopOfList(View view) {
        final int top = view.getTop();
        View parent = (View) view.getParent();
        return (parent != null && !(parent instanceof RecyclerView)) ?
                top + calculateTopOfList(parent) : top;
    }

    private Canvas drawRectToBitmapCanvas(View view, float deltaX, float swipeProgress) {

        int[] recyclerViewLocation = new int[2];
        mRecyclerView.getLocationInWindow(recyclerViewLocation);

        int[] viewLocation = new int[2];
        view.setTranslationX(0.0F);
        view.getLocationInWindow(viewLocation);
        viewLocation[0] -= recyclerViewLocation[0];

        int topOfList = this.calculateTopOfList(view);
        int viewWidth = view.getWidth();
        int viewHeight = view.getHeight();

        mSwipeRect = new Rect(viewLocation[0] + view.getPaddingLeft(), topOfList,
                viewLocation[0] + view.getWidth() - view.getPaddingRight(), topOfList + viewHeight);

        if (mSwipeBitmap == null) {
            mSwipeBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(this.mSwipeBitmap);
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        float centerX = view.getWidth() / 2.0f;
        float absDeltaX = Math.abs(deltaX);
        float alpha = (Math.min(absDeltaX, centerX) / centerX) * 255.0f;

        if (mLastRectAlpha != 255.0f && alpha == 255.0f) {
            view.performHapticFeedback(SeslHapticFeedbackConstantsReflector.semGetVibrationIndex((108)));
        } else if (mLastRectAlpha == 255.0f && alpha != 255.0f) {
            view.performHapticFeedback(SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(109));
        }
        mLastRectAlpha = alpha;

        int startX;
        if (swipeProgress > 0.0F) {
            Drawable dltr = mSwipeConfiguration.drawableLeftToRight;

            Rect drawableBounds;
            if (dltr != null) {
                drawableBounds = dltr.getBounds();
                int drawableWidth = drawableBounds.width();
                int drawableHeight = drawableBounds.height();

                Log.i(TAG, "#1 draw LtoR, d = " + dltr + ", d.getBounds()=" + drawableBounds);
                int drawablePadding = mSwipeConfiguration.drawablePadding;
                drawableBounds = new Rect(drawablePadding, 0, drawableWidth + drawablePadding, drawableHeight);
                drawableBounds.offset(0, (viewHeight - drawableHeight) / 2);
            } else {
                Log.i(TAG, "#2 draw LtoR, d = null");
                drawableBounds = new Rect(0, 0, 0, 0);
            }

            startX = (int) deltaX;

            Rect leftRect = new Rect(0, 0, startX, viewHeight);
            Paint leftPaint = mBgLeftToRight;
            drawRectInto(canvas, leftRect, drawableBounds, dltr, leftPaint, 255,
                    mSwipeConfiguration.textLeftToRight,  mSwipeConfiguration.textSize, 0);

            Rect rightRect = new Rect(startX, 0, viewWidth, viewHeight);
            drawRectInto(canvas, rightRect, drawableBounds, dltr, mBgLeftToRight, (int) alpha,
                    mSwipeConfiguration.textLeftToRight, mSwipeConfiguration.textSize, 0);

        } else if (swipeProgress < 0.0F) {
            Drawable drtl = mSwipeConfiguration.drawableRightToLeft;

            Rect drawableBounds;
            if (drtl != null) {
                drawableBounds = drtl.getBounds();
                int drawableWidth = drawableBounds.width();
                int drawableHeight = drawableBounds.height();
                int right = viewWidth - mSwipeConfiguration.drawablePadding;
                Log.i(TAG, "#3 draw RtoL, d = " + drtl + ", d.getBounds()=" + drawableBounds);

                drawableBounds = new Rect(right - drawableWidth, 0, right, drawableHeight);
                drawableBounds.offset(0, (viewHeight - drawableHeight) / 2);
            } else {
                Log.i(TAG, "#4 draw RtoL, d = null");
                drawableBounds = new Rect(viewWidth, 0, viewWidth, 0);
            }

            int left = viewWidth - (int) absDeltaX;
            Rect leftRect = new Rect(left, 0, viewWidth, viewHeight);
            Paint leftPaint = this.mBgRightToLeft;

            drawRectInto(canvas, leftRect, drawableBounds, drtl, leftPaint, 255,
                    mSwipeConfiguration.textRightToLeft, mSwipeConfiguration.textSize, 1);

            Rect rightRect = new Rect(0, 0, left, viewHeight);
            drawRectInto(canvas, rightRect, drawableBounds, drtl, mBgRightToLeft, (int) alpha,
                    mSwipeConfiguration.textRightToLeft, mSwipeConfiguration.textSize, 1);
        }
        return canvas;
    }

    private void drawRectInto(Canvas canvas, Rect destinationRect, Rect sourceRect, Drawable drawable,
            Paint paint, int alpha, String text, float textStartX, int textAlignment) {

        canvas.save();
        paint.setAlpha(alpha);
        mTextPaint.setAlpha(alpha);

        canvas.clipRect(destinationRect);
        canvas.drawRect(destinationRect, paint);

        if (drawable != null) {
            drawable.setBounds(sourceRect);
            drawable.draw(canvas);
        }

        drawSwipeText(canvas, mTextPaint, text, textAlignment, sourceRect);
        canvas.restore();
    }


    private void drawSwipeText(Canvas canvas, Paint textPaint, String text, int direction, Rect bounds) {
        Rect textBounds = new Rect();

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.getTextBounds(text, 0, text.length(), textBounds);

        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();

        float textHeight = Math.abs(fontMetrics.top - fontMetrics.bottom);
        float centerY = (float) canvas.getHeight() / 2.0F;
        float textCenter = textHeight / 2.0F;
        float descent = fontMetrics.bottom;
        float y = (centerY + textCenter) - descent;
        float x;
        if (direction == DIRECTION_LTR) {
            x = bounds.right + mSwipeConfiguration.drawablePadding;
        } else {
            x = (bounds.left - mSwipeConfiguration.drawablePadding) - textBounds.right;

        }
        canvas.drawText(text, x, y, textPaint);
    }


    private BitmapDrawable getBitmapDrawableToSwipeBitmap() {
        if (mSwipeBitmap == null) {
            return null;
        }

        BitmapDrawable d = new BitmapDrawable(mRecyclerView.getResources(), mSwipeBitmap);
        d.setBounds(mSwipeRect);
        return d;
    }

    public void clearSwipeAnimation(@NonNull View view) {
        Log.i(TAG, "clearSwipeAnimation: view = " + view +
                " mDrawSwipeBitmapDrawable = " + mDrawSwipeBitmapDrawable);
        this.mLastRectAlpha = 0.0f;

        if (mDrawSwipeBitmapDrawable != null) {
            mDrawSwipeBitmapDrawable.getBitmap().recycle();
            mDrawSwipeBitmapDrawable = null;
            mSwipeBitmap = null;
        }

        Log.i(TAG, "clearSwipeAnimation: view.getTranslationX() = " + view.getTranslationX());

        if (view.getTranslationX() != 0f) {
            Log.i(TAG, "clearSwipeAnimation: **** set view.setTranslationX(0f) ****");
            view.setTranslationX(0f);
        }
    }

    public void onSwiped(@NonNull View view) {
        Log.i(TAG, "onSwiped");
        clearSwipeAnimation(view);
        view.setTranslationX(0f);
        view.setAlpha(1f);
    }


    public long getAnimationDuration(@NonNull RecyclerView recyclerView, int i10, float f10, float f11) {
        return i10 == 4 ? DEFAULT_ANIMATION_CANCEL_DURATION : DEFAULT_ANIMATION_DURATION;
    }


    private void drawTextToCenter(Canvas canvas, Paint paint, String text) {
        final int height = canvas.getHeight();
        final int width = canvas.getWidth();

        Rect rect = new Rect();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.getTextBounds(text, 0, text.length(), rect);

        final float x = ((width / 2f) - (rect.width() / 2f)) - rect.left;
        final float y = ((height / 2f) + (rect.height() / 2f)) - rect.bottom;
        canvas.drawText(text, x, y, paint);
    }


}
