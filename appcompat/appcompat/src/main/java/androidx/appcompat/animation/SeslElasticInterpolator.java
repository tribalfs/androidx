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

    public SeslElasticInterpolator(float amplitude, float point) {
        this.mAmplitude = amplitude;
        this.mPeriod = point;
    }

    private float out(float f10, float f11, float f12) {
        float f13;
        if (f10 == 0.0f) {
            return 0.0f;
        }
        if (f10 >= 1.0f) {
            return 1.0f;
        }
        if (f12 == 0.0f) {
            f12 = 0.3f;
        }
        if (f11 >= 1.0f) {
            f13 = (float) ((f12 / 6.283185307179586d) * Math.asin(1.0f / f11));
        } else {
            f13 = f12 / 4.0f;
            f11 = 1.0f;
        }
        return (float) ((f11 * Math.pow(2.0d, (-10.0f) * f10) * Math.sin(((f10 - f13) * 6.283185307179586d) / f12)) + 1.0d);
    }

    public float getAmplitude() {
        return this.mAmplitude;
    }

    @Override
    public float getInterpolation(float f10) {
        return out(f10, this.mAmplitude, this.mPeriod);
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
