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

package androidx.appcompat.animation;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.view.animation.Interpolator;

import androidx.annotation.RestrictTo;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung custom interpolator that simulates an elastic behavior.
 */
@RestrictTo(LIBRARY_GROUP)
class SeslElasticInterpolator implements Interpolator {
    private float mAmplitude;
    private float mPeriod;

    public SeslElasticInterpolator(float amplitude, float period) {
        this.mAmplitude = amplitude;
        this.mPeriod = period;
    }

    private float out(float value, float amplitude, float period) {
        float intermediateValue;
        if (value == 0.0f) {
            return 0.0f;
        }
        if (value >= 1.0f) {
            return 1.0f;
        }
        if (period == 0.0f) {
            period = 0.3f;
        }
        if (amplitude >= 1.0f) {
            intermediateValue = (float) ((period / 6.283185307179586) * Math.asin(1.0f / amplitude));
        } else {
            intermediateValue = period / 4.0f;
            amplitude = 1.0f;
        }
        return (float) (amplitude
                * Math.pow(2.0d, (-10.0f) * value)
                * Math.sin(((value - intermediateValue) * 6.283185307179586) / period)
                + 1.0d);
    }

    public float getAmplitude() {
        return this.mAmplitude;
    }

    @Override
    public float getInterpolation(float value) {
        return out(value, this.mAmplitude, this.mPeriod);
    }

    public float getPeriod() {
        return this.mPeriod;
    }

    public void setAmplitude(float amplitude) {
        this.mAmplitude = amplitude;
    }

    public void setPeriod(float period) {
        this.mPeriod = period;
    }
}
