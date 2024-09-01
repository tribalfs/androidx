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

package androidx.appcompat.util;

import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.reflect.SeslBaseReflector;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Method;

@RequiresApi(api = 23)
public class SeslShapeDrawable extends GradientDrawable {
    static final String TAG = "SeslShapeDrawable";

    @Override
    public void inflate(@NonNull Resources resources, @NonNull XmlPullParser xmlPullParser,
            @NonNull AttributeSet attrs, @Nullable Resources.Theme theme)
            throws XmlPullParserException, IOException {
        super.inflate(resources, xmlPullParser, attrs, theme);
        Method declaredMethod = SeslBaseReflector.getDeclaredMethod(GradientDrawable.class, "setSmoothCorner", Boolean.TYPE);
        if (declaredMethod == null) {
            Log.w(TAG, "This API is not supported by the platform.");
        } else {
            SeslBaseReflector.invoke(this, declaredMethod, Boolean.TRUE);
        }
    }
}