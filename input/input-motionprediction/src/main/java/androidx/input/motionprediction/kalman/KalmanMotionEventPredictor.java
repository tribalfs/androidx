/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.input.motionprediction.kalman;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.input.motionprediction.MotionEventPredictor;

/**
 */
@RestrictTo(LIBRARY)
public class KalmanMotionEventPredictor implements MotionEventPredictor {
    private MultiPointerPredictor mMultiPointerPredictor = new MultiPointerPredictor();

    public KalmanMotionEventPredictor() {
    }

    @Override
    public void record(@NonNull MotionEvent event) {
        if (mMultiPointerPredictor == null) {
            return;
        }
        mMultiPointerPredictor.onTouchEvent(event);
    }

    @Nullable
    @Override
    public MotionEvent predict() {
        if (mMultiPointerPredictor == null) {
            return null;
        }
        return mMultiPointerPredictor.predict(1);
    }

    @Override
    public void close() {
        mMultiPointerPredictor = null;
    }
}
