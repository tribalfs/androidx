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
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung Subheader Rounded Corners class.
 */
public class SeslSubheaderRoundedCorner extends SeslRoundedCorner {
    private static final String TAG = "SeslSubheaderRoundedCorner";

    public SeslSubheaderRoundedCorner(@NonNull Context context) {
        super(context);
    }

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public void drawRoundedCorner(int left, int top, int right, int bottom, Canvas canvas) {
        mRoundedCornerBounds.set(left, top, right, bottom);
        drawRoundedCornerInternal(canvas);
    }

    @Override
    public void drawRoundedCorner(@NonNull View view, @NonNull Canvas canvas) {
        int left;
        int top;
        if (view.getTranslationY() != 0.0f) {
            left = Math.round(view.getX());
            top = Math.round(view.getY());
        } else {
            left = view.getLeft();
            top = view.getTop();
        }

        mRoundedCornerBounds.set(left, top, left + view.getWidth(), top + view.getHeight());
        drawRoundedCornerInternal(canvas);
    }

    private void drawRoundedCornerInternal(Canvas canvas) {
        final int left = mRoundedCornerBounds.left;
        final int right = mRoundedCornerBounds.right;
        final int top = mRoundedCornerBounds.top;
        final int bottom = mRoundedCornerBounds.bottom;

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_LEFT) != 0) {
            mTopLeftRound.setBounds(left, bottom, mRoundRadius + left, mRoundRadius + bottom);
            mTopLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_RIGHT) != 0) {
            mTopRightRound.setBounds(right - mRoundRadius, bottom, right, mRoundRadius + bottom);
            mTopRightRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
            mBottomLeftRound.setBounds(left, top - mRoundRadius, mRoundRadius + left, top);
            mBottomLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_RIGHT) != 0) {
            mBottomRightRound.setBounds(right - mRoundRadius, top - mRoundRadius, right, top);
            mBottomRightRound.draw(canvas);
        }
    }
}
