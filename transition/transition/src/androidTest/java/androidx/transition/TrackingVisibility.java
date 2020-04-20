/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.transition;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.animation.Animator;
import androidx.core.animation.AnimatorListenerAdapter;
import androidx.core.animation.ObjectAnimator;
import androidx.fragment.app.TargetTracking;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Visibility transition that tracks which targets are applied to it.
 * By default, this transition does no animation.
 */
public class TrackingVisibility extends Visibility implements TargetTracking {
    public final ArrayList<View> targets = new ArrayList<>();
    private final Rect[] mEpicenter = new Rect[1];
    private boolean mRealTransition;
    public CountDownLatch endAnimatorCountDownLatch = new CountDownLatch(1);

    public void setRealTransition(boolean realTransition) {
        this.mRealTransition = realTransition;
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        targets.add(endValues.view);
        Rect epicenter = getEpicenter();
        if (epicenter != null) {
            mEpicenter[0] = new Rect(epicenter);
        } else {
            mEpicenter[0] = null;
        }
        return null;
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        targets.add(startValues.view);
        Rect epicenter = getEpicenter();
        if (epicenter != null) {
            mEpicenter[0] = new Rect(epicenter);
        } else {
            mEpicenter[0] = null;
        }
        if (mRealTransition) {
            Animator animator = ObjectAnimator.ofFloat(view, "transitionAlpha", 0);
            // We need to wait until the exiting Transition has completed. Just adding a listener
            // is not enough because it will not be last listener to get an onTransitionEnd
            // callback, so we have to add a listener on the Animator that runs the Transition
            // and wait for that to end.
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                    super.onAnimationStart(animation);
                    animation.removeListener(this);
                    animation.addListener(this);
                    endAnimatorCountDownLatch = new CountDownLatch(1);
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    endAnimatorCountDownLatch.countDown();
                    animation.removeListener(this);
                }
            });
            return animator;
        }
        return null;
    }

    @NonNull
    @Override
    public ArrayList<View> getTrackedTargets() {
        return targets;
    }

    @Override
    public void clearTargets() {
        targets.clear();
    }

    @Override
    public Rect getCapturedEpicenter() {
        return mEpicenter[0];
    }
}
