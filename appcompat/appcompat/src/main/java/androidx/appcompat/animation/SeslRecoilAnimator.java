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

package androidx.appcompat.animation;


import static androidx.appcompat.util.SeslMisc.computeEasingFactor;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.R;
import java.util.ArrayList;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

//Added in sesl7
@RequiresApi(api = 29)
public class SeslRecoilAnimator {
    private static final int PRESS_INTERPOLATR = R.anim.sesl_recoil_pressed;
    private static final int RELEASE_INTERPOLATR = R.anim.sesl_recoil_released;
    private static final long mPressDuration = 100;
    private static  final long mReleaseDuration = 350;
    private static final float mScaleSizeDp = 3f;

    private static TimeInterpolator sPressInterpolator;
    private static TimeInterpolator sReleaseInterpolator;
    final ValueAnimator mAnimator;
    private final Context mContext;
    private float mScaleRatio;
    View mTarget;

    private boolean mIsScaleOnlyChildren = false;
    private boolean mIsPressed = false;

    public static class Holder {
        ArrayList<SeslRecoilAnimator> mAnimators = new ArrayList<>();
        private final Context mContext;

        public Holder(@NonNull Context context) {
            this.mContext = context;
        }

        @NonNull
        private SeslRecoilAnimator createOrReuseAnimator(View view) {
            for (SeslRecoilAnimator animator : mAnimators) {
                if (animator.mTarget == view) {
                    return animator;
                }
            }
            for (SeslRecoilAnimator animator : mAnimators) {
                if (!animator.isActive()) {
                    animator.mTarget = view;
                    return animator;
                }
            }
            SeslRecoilAnimator seslRecoilAnimator = new SeslRecoilAnimator(view, this.mContext);
            mAnimators.add(seslRecoilAnimator);
            return seslRecoilAnimator;
        }


        public void removeAllUpdateListeners() {
            for (SeslRecoilAnimator animator : mAnimators) {
                animator.mAnimator.removeAllUpdateListeners();
            }
        }

        public void setPress(@NonNull View view) {
            if (view.isClickable()) {
                createOrReuseAnimator(view).setPress();
            }
        }

        public void setRelease() {
            this.mAnimators.forEach((animator) -> {
                if (animator.isActive()) {
                    animator.setRelease();
                }
            });
        }
    }

    public SeslRecoilAnimator(@NonNull View view, @NonNull Context context) {
        mTarget = view;
        mContext = context;
        setScaleOnlyChildren(true);
        mAnimator = ValueAnimator.ofFloat(1.0f);
        mAnimator.setCurrentFraction(1.0f);
        if (sPressInterpolator == null) {
            sPressInterpolator = AnimationUtils.loadInterpolator(mContext, PRESS_INTERPOLATR);
        }
        if (sReleaseInterpolator == null) {
            sReleaseInterpolator = AnimationUtils.loadInterpolator(mContext, RELEASE_INTERPOLATR);
        }
        mAnimator.addUpdateListener(animation -> {
            if (mIsScaleOnlyChildren && (mTarget instanceof ViewGroup)) {
                setScaleChildren((Float) animation.getAnimatedValue());
            } else {
                setScale((Float) animation.getAnimatedValue());
            }
        });
    }

    private void setScale(float f) {
        mTarget.setScaleX(f);
        mTarget.setScaleY(f);
    }

    private void setScaleChildren(float scale) {
        ViewGroup viewGroup = (ViewGroup) mTarget;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            Matrix matrix = new Matrix();
            float dx = (mTarget.getWidth() / 2.0f) - child.getLeft();
            float dy = (mTarget.getHeight() / 2.0f) - child.getTop();
            matrix.setTranslate(-dx, -dy);
            matrix.postScale(scale, scale);
            matrix.postTranslate(dx, dy);
            child.setAnimationMatrix(matrix);
        }
    }

    private void setScaleRatioBySize() {
        float width = mTarget.getWidth();
        mScaleRatio = computeEasingFactor(mContext.getResources().getDisplayMetrics().density, mScaleSizeDp, width, width);
    }


    public boolean isActive() {
        return mIsPressed || mAnimator.isRunning();
    }

    public void setPress() {
        setScaleRatioBySize();
        if (mIsPressed) {
            return;
        }
        mIsPressed = true;
        if (mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator.setFloatValues((Float) mAnimator.getAnimatedValue(), mScaleRatio);
        mAnimator.setDuration(mPressDuration);
        mAnimator.setInterpolator(sPressInterpolator);
        mAnimator.start();
    }

    public void setRelease() {
        if (mIsPressed) {
            mIsPressed = false;
            if (mAnimator.isRunning()) {
                mAnimator.cancel();
            }

            mAnimator.setFloatValues((Float) mAnimator.getAnimatedValue(), 1.0f);
            mAnimator.setDuration(mReleaseDuration);
            mAnimator.setInterpolator(sReleaseInterpolator);
            mAnimator.start();
        }
    }

    public void setScaleOnlyChildren(boolean enable) {
        if (mTarget instanceof ViewGroup) {
            mIsScaleOnlyChildren = enable;
        } else {
            mIsScaleOnlyChildren = false;
        }
    }
}