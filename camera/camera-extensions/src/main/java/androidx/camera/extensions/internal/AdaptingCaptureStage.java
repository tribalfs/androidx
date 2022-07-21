/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.extensions.internal;

import android.hardware.camera2.CaptureRequest;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.impl.Camera2ImplConfig;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.CaptureStage;
import androidx.camera.extensions.impl.CaptureStageImpl;

/** A {@link CaptureStage} that calls a vendor provided implementation. */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public final class AdaptingCaptureStage implements CaptureStage {

    private final CaptureConfig mCaptureRequestConfiguration;
    private final int mId;

    @SuppressWarnings("unchecked")
    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public AdaptingCaptureStage(@NonNull CaptureStageImpl impl) {
        mId = impl.getId();
        Camera2ImplConfig.Builder camera2ConfigurationBuilder = new Camera2ImplConfig.Builder();

        for (Pair<CaptureRequest.Key, Object> captureParameter : impl.getParameters()) {
            camera2ConfigurationBuilder.setCaptureRequestOption(captureParameter.first,
                    captureParameter.second);
        }

        CaptureConfig.Builder captureConfigBuilder = new CaptureConfig.Builder();
        captureConfigBuilder.addImplementationOptions(camera2ConfigurationBuilder.build());
        mCaptureRequestConfiguration = captureConfigBuilder.build();
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    @NonNull
    public CaptureConfig getCaptureConfig() {
        return mCaptureRequestConfiguration;
    }
}
