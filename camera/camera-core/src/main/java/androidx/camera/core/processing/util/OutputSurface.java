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

package androidx.camera.core.processing.util;

import android.opengl.EGLSurface;

import androidx.annotation.RestrictTo;

import com.google.auto.value.AutoValue;

import org.jspecify.annotations.NonNull;

/**
 * Wrapper for output {@link EGLSurface} in {@link androidx.camera.core.processing.OpenGlRenderer}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@AutoValue
public abstract class OutputSurface {

    /**
     * Creates {@link OutputSurface}.
     */
    public static @NonNull OutputSurface of(@NonNull EGLSurface eglSurface, int width, int height) {
        return new AutoValue_OutputSurface(eglSurface, width, height);
    }

    /**
     * Gets {@link EGLSurface}.
     */
    public abstract @NonNull EGLSurface getEglSurface();

    /**
     * Gets {@link EGLSurface} width.
     */
    public abstract int getWidth();

    /**
     * Gets {@link EGLSurface} height.
     */
    public abstract int getHeight();
}
