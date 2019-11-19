/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

/**
 * ChangeClipBounds captures the {@link android.view.View#getClipBounds()} before and after the
 * scene change and animates those changes during the transition.
 *
 * <p>Prior to API 18 this does nothing.</p>
 */
public class ChangeClipBounds extends Transition {

    private static final String PROPNAME_CLIP = "android:clipBounds:clip";
    private static final String PROPNAME_BOUNDS = "android:clipBounds:bounds";

    private static final String[] sTransitionProperties = {
            PROPNAME_CLIP,
    };

    @Override
    @NonNull
    public String[] getTransitionProperties() {
        return sTransitionProperties;
    }

    public ChangeClipBounds() {
    }

    public ChangeClipBounds(@NonNull Context context, @NonNull AttributeSet attrs) {
        super(context, attrs);
    }

    private void captureValues(TransitionValues values) {
        View view = values.view;
        if (view.getVisibility() == View.GONE) {
            return;
        }

        Rect clip = ViewCompat.getClipBounds(view);
        values.values.put(PROPNAME_CLIP, clip);
        if (clip == null) {
            Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
            values.values.put(PROPNAME_BOUNDS, bounds);
        }
    }

    @Override
    public void captureStartValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Nullable
    @Override
    public Animator createAnimator(@NonNull final ViewGroup sceneRoot,
            @Nullable TransitionValues startValues,
            @Nullable TransitionValues endValues) {
        if (startValues == null || endValues == null
                || !startValues.values.containsKey(PROPNAME_CLIP)
                || !endValues.values.containsKey(PROPNAME_CLIP)) {
            return null;
        }
        Rect start = (Rect) startValues.values.get(PROPNAME_CLIP);
        Rect end = (Rect) endValues.values.get(PROPNAME_CLIP);
        final boolean endIsNull = end == null;
        if (start == null && end == null) {
            return null; // No animation required since there is no clip.
        }

        if (start == null) {
            start = (Rect) startValues.values.get(PROPNAME_BOUNDS);
        } else if (end == null) {
            end = (Rect) endValues.values.get(PROPNAME_BOUNDS);
        }
        if (start.equals(end)) {
            return null;
        }

        ViewCompat.setClipBounds(endValues.view, start);
        RectEvaluator evaluator = new RectEvaluator(new Rect());
        ObjectAnimator animator = ObjectAnimator.ofObject(endValues.view, ViewUtils.CLIP_BOUNDS,
                evaluator, start, end);
        if (endIsNull) {
            final View endView = endValues.view;
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ViewCompat.setClipBounds(endView, null);
                }
            });
        }
        return animator;
    }
}
