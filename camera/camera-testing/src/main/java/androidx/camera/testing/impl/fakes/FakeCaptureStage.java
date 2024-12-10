/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.testing.impl.fakes;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.CaptureStage;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A fake {@link CaptureStage} where the values can be set.
 *
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public class FakeCaptureStage implements CaptureStage {

    private final int mId;

    private final @NonNull CaptureConfig mCaptureConfig;

    /**
     * Create a FakeCaptureStage with the given parameters.
     *
     * If {@code captureConfig} is {@code null},
     * {@link CaptureConfig#defaultEmptyCaptureConfig()} will be used as a default value.
     */
    public FakeCaptureStage(int id, @Nullable CaptureConfig captureConfig) {
        mId = id;

        if (captureConfig == null) {
            mCaptureConfig = CaptureConfig.defaultEmptyCaptureConfig();
        } else {
            mCaptureConfig = captureConfig;
        }
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    public @NonNull CaptureConfig getCaptureConfig() {
        return mCaptureConfig;
    }
}
