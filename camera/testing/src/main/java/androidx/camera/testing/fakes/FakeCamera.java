/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.testing.fakes;

import androidx.camera.core.BaseCamera;
import androidx.camera.core.BaseUseCase;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CaptureRequestConfiguration;

import java.util.Collection;

/** A fake camera which will not produce any data. */
public class FakeCamera implements BaseCamera {
    private final CameraControl mCameraControl = CameraControl.defaultEmptyInstance();

    private final CameraInfo mCameraInfo;

    FakeCamera() {
        this(new FakeCameraInfo());
    }

    FakeCamera(FakeCameraInfo cameraInfo) {
        mCameraInfo = cameraInfo;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void release() {
    }

    @Override
    public void addOnlineUseCase(Collection<BaseUseCase> baseUseCases) {
    }

    @Override
    public void removeOnlineUseCase(Collection<BaseUseCase> baseUseCases) {
    }

    @Override
    public void onUseCaseActive(BaseUseCase useCase) {
    }

    @Override
    public void onUseCaseInactive(BaseUseCase useCase) {
    }

    @Override
    public void onUseCaseUpdated(BaseUseCase useCase) {
    }

    @Override
    public void onUseCaseReset(BaseUseCase useCase) {
    }

    @Override
    public void onUseCaseSingleRequest(
            BaseUseCase useCase, CaptureRequestConfiguration captureRequestConfiguration) {
    }

    // Returns fixed CameraControl instance in order to verify the instance is correctly attached.
    @Override
    public CameraControl getCameraControl() {
        return mCameraControl;
    }

    @Override
    public CameraInfo getCameraInfo() {
        return mCameraInfo;
    }
}
