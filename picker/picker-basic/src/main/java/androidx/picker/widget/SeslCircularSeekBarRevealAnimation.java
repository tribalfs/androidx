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

package androidx.picker.widget;

import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;

//TODO: rework
public class SeslCircularSeekBarRevealAnimation {
    private final PathInterpolator mAniInterpolator = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);
    private boolean mIsRevealAnimation;
    private SeslCircularSeekBarView mView;

    public SeslCircularSeekBarRevealAnimation(@NonNull View view) {
        if (view instanceof SeslCircularSeekBarView) {
            this.mView = (SeslCircularSeekBarView) view;
        }
    }

    public boolean isRevealAnimation() {
        return this.mIsRevealAnimation;
    }
}