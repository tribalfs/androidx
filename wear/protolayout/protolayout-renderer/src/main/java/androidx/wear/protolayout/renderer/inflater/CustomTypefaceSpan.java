/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.protolayout.renderer.inflater;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

import androidx.annotation.NonNull;

/**
 * Stripped down version of TypefaceSpan, which can accept a Typeface argument on API levels under
 * 28.
 */
public class CustomTypefaceSpan extends MetricAffectingSpan {
    @NonNull private final Typeface mTypeface;

    public CustomTypefaceSpan(@NonNull Typeface typeface) {
        mTypeface = typeface;
    }

    @Override
    public void updateMeasureState(@NonNull TextPaint textPaint) {
        textPaint.setTypeface(mTypeface);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint tp) {
        tp.setTypeface(mTypeface);
    }
}
