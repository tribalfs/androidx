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

package androidx.picker3.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.picker.R;

import java.util.List;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */
class SeslColorSpectrumView extends View {
    private static final String TAG = "SeslColorSpectrumView";

    private static float STROKE_WIDTH = 2.0f;
    private static final int ROUNDED_CORNER_RADIUS = 4;

    private Drawable cursorDrawable;
    private final Context mContext;
    private Paint mCursorPaint;
    private Paint mHuePaint;
    SpectrumColorChangedListener mListener;
    final Resources mResources;
    private Paint mSaturationPaint;
    Rect mSpectrumRect;
    private Paint mStrokePaint;

    private float mCursorPosX;
    private float mCursorPosY;

    private int ROUNDED_CORNER_RADIUS_IN_Px = 0;
    private final int mCursorPaintSize;
    private final int mCursorStrokeSize;
    public boolean mFromSwatchTouch = false;
    private float mCurrentXPos;
    public float mCurrentYPos;

    private final int[] HUE_COLORS = {
            -65281, -16776961, -16711681, -16711936, -256, -65536
    };

    int mStartMargin;
    public int mTopMargin;

    private final static int INVALID_ID = -1;
    int mSelectedVirtualViewId = INVALID_ID;
    SeslColorSpectrumViewTouchHelper mTouchHelper;
    int mVirtualItemHeight;
    int mVirtualItemWidth;
    private Rect mSpectrumRectBackground;
    public Paint mBackgroundPaint;
    public int mSaturationProgress;

    interface SpectrumColorChangedListener {
        void onSpectrumColorChanged(float hue, float saturation);
    }

    void setOnSpectrumColorChangedListener(SpectrumColorChangedListener listener) {
        mListener = listener;
    }

    public SeslColorSpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mResources = mContext.getResources();
        STROKE_WIDTH = mResources.getDimensionPixelSize(R.dimen.sesl_spectrum_stroke_width);
        mStartMargin = getResources().getDimensionPixelSize(R.dimen.sesl_spectrum_rect_starting);
        mTopMargin = getResources().getDimensionPixelSize(R.dimen.sesl_spectrum_rect_top);

        mFromSwatchTouch = false;
        initAccessibility();

        int spectrumWidthPx = mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_oneui_3_color_spectrum_view_width);
        int spectrumHeightResId = R.dimen.sesl_color_picker_oneui_3_color_spectrum_view_height;
        int spectrumHeightPx = mResources.getDimensionPixelSize(spectrumHeightResId);

        mVirtualItemHeight = (int) (mResources.getDimension(spectrumHeightResId) / 25.0f);
        mVirtualItemWidth = (int) (mResources.getDimension(R.dimen.sesl_color_picker_oneui_3_color_swatch_view_width) / 30.0f);
        mSpectrumRect = new Rect(
                mStartMargin,
                mTopMargin,
                spectrumWidthPx,
                spectrumHeightPx
        );

        mSpectrumRectBackground = new Rect(
                0,
                0,
                mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_oneui_3_color_spectrum_view_width_background),
                mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_oneui_3_color_spectrum_view_height_background)
        );

        mCursorPaintSize = mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_spectrum_cursor_paint_size);
        mCursorStrokeSize = mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_spectrum_cursor_paint_size)
                + (mResources.getDimensionPixelSize(R.dimen.sesl_color_picker_spectrum_cursor_out_stroke_size) * 2);

        ROUNDED_CORNER_RADIUS_IN_Px = dpToPx(ROUNDED_CORNER_RADIUS);
        init();
    }

    public final void initAccessibility() {
        mTouchHelper = new SeslColorSpectrumViewTouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, mTouchHelper);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }


    private void init() {
        mCursorPaint = new Paint();
        cursorDrawable = mResources.getDrawable(R.drawable.sesl_color_picker_gradient_wheel_cursor);

        mStrokePaint = new Paint();
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setColor(mResources.getColor(R.color.sesl_color_picker_stroke_color_spectrumview));
        mStrokePaint.setStrokeWidth(STROKE_WIDTH);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setColor(mResources.getColor(R.color.sesl_color_picker_transparent));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float distance
                = (float) Math.sqrt(Math.pow(event.getX(), 2.0d) + Math.pow(event.getY(), 2.0d));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                playSoundEffect(SoundEffectConstants.CLICK);
                break;
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
        }

        float posX = event.getX();
        float posY = event.getY();
        mCurrentXPos = posX;

        if (posX > mSpectrumRect.width() + mStartMargin) {
            mCurrentXPos = mSpectrumRect.width() + mStartMargin;
        }

        mCurrentYPos = posY;
        if (posY > mSpectrumRect.height() + mTopMargin) {
            mCurrentYPos = mSpectrumRect.height() + mTopMargin;
        }

        if (posX > mSpectrumRect.width() + mStartMargin) {
            posX = mSpectrumRect.width() + mStartMargin;
        }
        if (posY > mSpectrumRect.height() + mTopMargin) {
            posY = mSpectrumRect.height() + mTopMargin;
        }
        if (posX < 0.0f) {
            posX = 0.0f;
        }
        if (posY < 0.0f) {
            posY = 0.0f;
        }

        mCursorPosX = posX;
        mCursorPosY = posY;

        final float hue = ((posX - mSpectrumRect.left) / mSpectrumRect.width()) * 300.0f;
        final float saturation = (mCursorPosY - mSpectrumRect.top) / mSpectrumRect.height();

        final float[] hsv = new float[3];
        hsv[0] = hue >= 0 ? hue : 0;
        hsv[1] = saturation;

        if (mListener != null) {
            mListener.onSpectrumColorChanged(hsv[0], hsv[1]);
        } else {
            Log.d(TAG, "Listener is not set.");
        }
        setSelectedVirtualViewId();
        invalidate();
        return true;
    }

    private void setSelectedVirtualViewId() {
        mSelectedVirtualViewId = (((int) (mCursorPosY / mVirtualItemHeight)) * 30)
                + ((int) (mCursorPosX / mVirtualItemWidth));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRoundRect(
                mSpectrumRectBackground.left,
                mSpectrumRectBackground.top,
                mSpectrumRectBackground.right,
                mSpectrumRectBackground.bottom,
                ROUNDED_CORNER_RADIUS_IN_Px,
                ROUNDED_CORNER_RADIUS_IN_Px,
                mBackgroundPaint
        );

        mHuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHuePaint.setShader(
                new LinearGradient((float) mSpectrumRect.right,
                        (float) mSpectrumRect.top,
                        (float) mSpectrumRect.left,
                        (float) mSpectrumRect.top,
                        HUE_COLORS,
                        null,
                        Shader.TileMode.CLAMP)
        );
        mHuePaint.setStyle(Paint.Style.FILL);

        mSaturationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSaturationPaint.setShader(
                new LinearGradient(
                        mSpectrumRect.left,
                        mSpectrumRect.top,
                        mSpectrumRect.left,
                        mSpectrumRect.bottom,
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                )
        );


        canvas.drawRoundRect(
                mSpectrumRect.left,
                mSpectrumRect.top,
                mSpectrumRect.right,
                mSpectrumRect.bottom,
                ROUNDED_CORNER_RADIUS_IN_Px,
                ROUNDED_CORNER_RADIUS_IN_Px,
                mHuePaint
        );

        canvas.drawRoundRect(
                mSpectrumRect.left,
                mSpectrumRect.top,
                mSpectrumRect.right,
                mSpectrumRect.bottom,
                ROUNDED_CORNER_RADIUS_IN_Px,
                ROUNDED_CORNER_RADIUS_IN_Px,
                mSaturationPaint
        );


        canvas.drawRoundRect(
                mSpectrumRect.left,
                mSpectrumRect.top,
                mSpectrumRect.right,
                mSpectrumRect.bottom,
                ROUNDED_CORNER_RADIUS_IN_Px,
                ROUNDED_CORNER_RADIUS_IN_Px,
                mStrokePaint
        );


        if (mCursorPosX < mSpectrumRect.left) {
            mCursorPosX = mSpectrumRect.left;
        }

        if (mCursorPosY < mSpectrumRect.top ) {
            mCursorPosY = mSpectrumRect.top;
        }

        if (mCursorPosX > mSpectrumRect.right + mStartMargin) {
            mCursorPosX = mSpectrumRect.right + mStartMargin;
        }

        if (mCursorPosY > mSpectrumRect.bottom + mTopMargin) {
            mCursorPosY = mSpectrumRect.bottom + mTopMargin;
        }

        canvas.drawCircle(mCursorPosX, mCursorPosY, mCursorPaintSize / 2.0f, mCursorPaint);
        cursorDrawable.setBounds(
                ((int) mCursorPosX) - (mCursorPaintSize / 2),
                ((int) mCursorPosY) - (mCursorPaintSize / 2),
                ((int) mCursorPosX) + (mCursorPaintSize / 2),
                ((int) mCursorPosY) + (mCursorPaintSize / 2)
        );

        cursorDrawable.draw(canvas);
        setDrawingCacheEnabled(true);

    }

    void setColor(int color) {
        final float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        updateCursorPosition(color, hsv);
    }

    void setProgress(int saturationProgress) {
        mSaturationProgress = saturationProgress;
    }


    public void updateCursorPosition(int color, float[] hsv) {
        if (mSpectrumRect != null) {
            String substring = String.format("%08x", color).substring(2);
            String string = getResources().getString(R.string.sesl_color_white_ffffff);

            if (mFromSwatchTouch && substring.equals(string)) {
                mCursorPosY = 0.0f;
                mCursorPosX = 0.0f;
            } else if (substring.equals(string)) {
                mCursorPosY = 0.0f;
                mCursorPosX = mCurrentXPos;
            } else {
                mCursorPosX = mSpectrumRect.left + ((mSpectrumRect.width() * hsv[0]) / 300.0f);
                mCursorPosY = mSpectrumRect.top + (mSpectrumRect.height() * hsv[1]);
                if (mCursorPosX > mSpectrumRect.width() + mStartMargin) {
                    mCursorPosX = mSpectrumRect.width() + mStartMargin;
                }
                if (mCursorPosY > mSpectrumRect.height() + mTopMargin) {
                    mCursorPosY = mSpectrumRect.height() + mTopMargin;
                }
            }

            Log.d(TAG, "updateCursorPosition() " +
                    "HSV[" + hsv[0] + ", " + hsv[1] + ", " + hsv[1]
                    + "] mCursorPosX=" + mCursorPosX + " mCursorPosY=" + mCursorPosY);
        }
        invalidate();
    }


    void updateCursorColor(int color) {
        Log.i("SeslColorSpectrumView", "updateCursorColor color " + color);
        if (String.format("%08x", color).substring(2).equals(getResources().getString(R.string.sesl_color_black_000000))) {
            mCursorPaint.setColor(Color.parseColor("#" + getResources().getString(R.string.sesl_color_white_ffffff)));
            return;
        }
        mCursorPaint.setColor(ColorUtils.setAlphaComponent(color, 255));
    }

    private static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent motionEvent) {
        return this.mTouchHelper.dispatchHoverEvent(motionEvent) || super.dispatchHoverEvent(motionEvent);
    }



    private class SeslColorSpectrumViewTouchHelper extends ExploreByTouchHelper {
        private final Integer[] mBrightnessNumber;
        private final String[] mColorName;
        private final Integer[] mHueNumber;
        private final String[][] mSBTable;
        private final Integer[] mSaturationNumber;
        private float mVirtualBrightness;
        float mVirtualCurrentCursorX;
        float mVirtualCurrentCursorY;
        private int mVirtualCursorPosX;
        private int mVirtualCursorPosY;
        private float mVirtualHue;
        private float mVirtualSaturation;
        float mVirtualSaturationDash;
        private float mVirtualValue;
        private final Rect mVirtualViewRect;


        public SeslColorSpectrumViewTouchHelper(View view) {
            super(view);
            mColorName = new String[]{
                    mResources.getString(R.string.sesl_color_picker_red),
                    mResources.getString(R.string.sesl_color_picker_red_orange),
                    mResources.getString(R.string.sesl_color_picker_orange),
                    mResources.getString(R.string.sesl_color_picker_orange_yellow),
                    mResources.getString(R.string.sesl_color_picker_yellow),
                    mResources.getString(R.string.sesl_color_picker_yellow_green),
                    mResources.getString(R.string.sesl_color_picker_green),
                    mResources.getString(R.string.sesl_color_picker_emerald_green),
                    mResources.getString(R.string.sesl_color_picker_cyan),
                    mResources.getString(R.string.sesl_color_picker_cerulean_blue),
                    mResources.getString(R.string.sesl_color_picker_blue),
                    mResources.getString(R.string.sesl_color_picker_purple),
                    mResources.getString(R.string.sesl_color_picker_magenta),
                    mResources.getString(R.string.sesl_color_picker_crimson)};
            mHueNumber = new Integer[]{15, 27, 45, 54, 66, 84, 138, 171, 189, 216, 255, 270, 318, 342};
            mSaturationNumber = new Integer[]{20, 40, 60, 80, 100};
            mBrightnessNumber = new Integer[]{20, 40, 60, 80, 100};

            String dark = mResources.getString(R.string.sesl_color_picker_dark);
            String grayishDark = mResources.getString(R.string.sesl_color_picker_grayish_dark);
            String grayish = mResources.getString(R.string.sesl_color_picker_grayish);
            String grayishLight = mResources.getString(R.string.sesl_color_picker_grayish_light);
            String light = mResources.getString(R.string.sesl_color_picker_light);
            String hueName = mResources.getString(R.string.sesl_color_picker_hue_name);

            String[] strArr = {dark, grayishDark, grayish, grayishLight, grayishLight};
            String[] strArr2 = {dark, grayishDark, grayish, grayishLight, light};
            String[] strArr3 = {dark, dark, grayish, light, light};
            mSBTable = new String[][]{
                    strArr, strArr2, strArr3,
                    new String[]{dark, dark, dark, hueName, hueName},
                    new String[]{dark, dark, dark, hueName, hueName}};

            mVirtualViewRect = new Rect();
        }

        @Override
        public int getVirtualViewAt(float x, float y) {
            setVirtualCursorIndexAt(x - mStartMargin, y - mTopMargin);
            return getFocusedVirtualViewId();
        }

        @Override
        public void getVisibleVirtualViews(List<Integer> list) {
            for (int i = 0; i < 750; i++) {
                list.add(i);
            }
        }

        @Override
        public void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            event.setContentDescription(getItemDescription(virtualViewId));
        }

        @Override
        public void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat infoCompat) {
            setVirtualCursorIndexAt(virtualViewId);
            setVirtualCursorRect(mVirtualViewRect);
            infoCompat.setContentDescription(getItemDescription(virtualViewId));
            infoCompat.setBoundsInParent(mVirtualViewRect);
            infoCompat.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            if (mSelectedVirtualViewId == INVALID_ID || virtualViewId != mSelectedVirtualViewId) {
                return;
            }
            infoCompat.addAction(4);
            infoCompat.setClickable(true);
            infoCompat.setSelected(true);
        }

        @Override
        public boolean onPerformActionForVirtualView(int index, int action, Bundle bundle) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) {
                return false;
            }
            setVirtualCursorIndexAt(index);
            onVirtualViewClick(mVirtualHue, mVirtualSaturation);
            return false;
        }

        public final int getFocusedVirtualViewId() {
            return mVirtualCursorPosX + (mVirtualCursorPosY * 30);
        }

        public final void setVirtualCursorIndexAt(float x, float y) {
            mVirtualCurrentCursorX = MathUtils.clamp(x, 0.0f, mSpectrumRect.width());
            mVirtualCurrentCursorY = MathUtils.clamp(y, 0.0f, mSpectrumRect.height());
            mVirtualCursorPosX = (int) (mVirtualCurrentCursorX / mVirtualItemWidth);
            mVirtualCursorPosY = (int) (mVirtualCurrentCursorY / mVirtualItemHeight);
            float width = (((mVirtualCurrentCursorX - mSpectrumRect.left) + mStartMargin) / mSpectrumRect.width()) * 300.0f;
            mVirtualSaturation = ((mVirtualCurrentCursorY - mSpectrumRect.top) + mTopMargin) / mSpectrumRect.height();
            mVirtualHue = Math.max(width, 0.0f);
            mVirtualBrightness = mSaturationProgress;
            mVirtualSaturationDash = 1.0f + mVirtualSaturation;
            mVirtualValue = mSaturationProgress / mVirtualSaturationDash;
            mVirtualSaturation = mVirtualSaturation * 100.0f;
        }

        private void setVirtualCursorIndexAt(int index) {
            mVirtualCursorPosX = index % 30;
            mVirtualCursorPosY = index / 30;
            setVirtualCursorIndexAt(mVirtualCursorPosX * mVirtualItemWidth,
                    mVirtualCursorPosY * mVirtualItemHeight);
        }

        private void setVirtualCursorRect(Rect rect) {
            rect.set(
                    (mVirtualCursorPosX * mVirtualItemWidth) + mStartMargin,
                    (int) (((mVirtualCursorPosY * mVirtualItemHeight) - 4.5f) + mTopMargin),
                    ((mVirtualCursorPosX + 1) * mVirtualItemWidth) + mStartMargin,
                    (int) ((((mVirtualCursorPosY + 1) * mVirtualItemHeight) - 4.5f) + mTopMargin)
            );
        }

        private StringBuilder getItemDescription(int virtualViewId) {
            setVirtualCursorIndexAt(virtualViewId);

            StringBuilder sb = new StringBuilder();

            int saturationIndex = getIndex(mSaturationNumber, (int) mVirtualSaturation);

            int brightnessIndex = getIndex(mBrightnessNumber, (int) mVirtualBrightness);

            String str;
            if (mVirtualHue >= 343) {
                str = mResources.getString(R.string.sesl_color_picker_red);
            } else {
                str = mColorName[getIndex(mHueNumber, (int) mVirtualHue)];
            }

            String virtualValueStr = Integer.toString((int) mVirtualValue);
            String str2 = mSBTable[saturationIndex][brightnessIndex];

            if (mVirtualValue == 0 || mVirtualValue == 1) {
                sb.append(mResources.getString(R.string.sesl_color_picker_black))
                        .append(" ")
                        .append(virtualValueStr);
            } else if (mVirtualValue >= 95 && mVirtualValue <= 100) {
                sb.append(mResources.getString(R.string.sesl_color_picker_white))
                        .append(" ")
                        .append(virtualValueStr);
            } else if (mVirtualSaturation <= 3) {
                if (mVirtualValue >= 2 && mVirtualValue <= 35) {
                    sb.append(mResources.getString(R.string.sesl_color_picker_dark_gray))
                            .append(" ")
                            .append(virtualValueStr);
                } else if (mVirtualValue >= 36 && mVirtualValue <= 80) {
                    sb.append(mResources.getString(R.string.sesl_color_picker_gray))
                            .append(" ")
                            .append(virtualValueStr);
                } else if (mVirtualValue >= 81 && mVirtualValue <= 98) {
                    sb.append(mResources.getString(R.string.sesl_color_picker_light_gray))
                            .append(" ")
                            .append(virtualValueStr);
                }
            } else if (mVirtualSaturation > 3) {
                if (str2.equals(mResources.getString(R.string.sesl_color_picker_hue_name))) {
                    sb.append(str)
                            .append(" ")
                            .append(virtualValueStr);
                } else {
                    String format = String.format(str2, str);
                    sb.append(format)
                            .append(" ")
                            .append(virtualValueStr);
                }
            }
            return sb;
        }

        private void onVirtualViewClick(float x, float y) {
            if (mListener != null) {
                mListener.onSpectrumColorChanged(x, y);
            }
            mTouchHelper.sendEventForVirtualView(mSelectedVirtualViewId, MotionEvent.TOOL_TYPE_FINGER);
        }

        private int getIndex(Integer[] colorArr, int color) {
            int lastIndex = colorArr.length - 1;
            int index = 0;
            int i = 0;
            while (i <= lastIndex) {
                int testIndex = (i + lastIndex) / 2;
                if (colorArr[testIndex] <= color) {
                    i = testIndex + 1;
                } else {
                    lastIndex = testIndex - 1;
                    index = testIndex;
                }
            }
            return index;
        }
    }


}
