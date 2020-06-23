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

package androidx.camera.core.internal;

import static com.google.common.truth.Truth.assertThat;

import androidx.camera.core.impl.CameraInternal;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.LinkedHashSet;

/** JUnit test cases for {@link CameraUseCaseAdapter} class. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CameraUseCaseAdapterTest {
    FakeCameraDeviceSurfaceManager mFakeCameraDeviceSurfaceManager;
    FakeCamera mFakeCamera;
    LinkedHashSet<CameraInternal> mFakeCameraSet = new LinkedHashSet<>();

    @Before
    public void setUp() {
        mFakeCameraDeviceSurfaceManager = new FakeCameraDeviceSurfaceManager();
        mFakeCamera = new FakeCamera();
        mFakeCameraSet.add(mFakeCamera);
    }

    @Test
    public void attachUseCases() throws CameraUseCaseAdapter.CameraException {
        CameraUseCaseAdapter cameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);
        FakeUseCase fakeUseCase = new FakeUseCase();
        cameraUseCaseAdapter.addUseCases(Collections.singleton(fakeUseCase));

        assertThat(fakeUseCase.getCamera()).isEqualTo(mFakeCamera);
        assertThat(mFakeCamera.getAttachedUseCases()).containsExactly(fakeUseCase);
    }

    @Test
    public void detachUseCases() throws CameraUseCaseAdapter.CameraException {
        CameraUseCaseAdapter cameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);
        FakeUseCase fakeUseCase = new FakeUseCase();
        cameraUseCaseAdapter.addUseCases(Collections.singleton(fakeUseCase));
        cameraUseCaseAdapter.removeUseCases(Collections.singleton(fakeUseCase));

        assertThat(fakeUseCase.getCamera()).isNull();
    }

    @Test
    public void closeCameraUseCaseAdapter() throws CameraUseCaseAdapter.CameraException {
        CameraUseCaseAdapter cameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);
        FakeUseCase fakeUseCase = new FakeUseCase();
        cameraUseCaseAdapter.addUseCases(Collections.singleton(fakeUseCase));
        cameraUseCaseAdapter.detachUseCases();

        assertThat(fakeUseCase.getCamera()).isEqualTo(mFakeCamera);
        assertThat(mFakeCamera.getAttachedUseCases()).isEmpty();
    }

    @Test
    public void cameraIdEquals() {
        CameraUseCaseAdapter cameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);

        CameraUseCaseAdapter.CameraId otherCameraId =
                CameraUseCaseAdapter.generateCameraId(mFakeCameraSet);

        assertThat(cameraUseCaseAdapter.getCameraId().equals(otherCameraId)).isTrue();
    }

    @Test
    public void cameraEquivalent() {
        CameraUseCaseAdapter cameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);

        CameraUseCaseAdapter otherCameraUseCaseAdapter = new CameraUseCaseAdapter(mFakeCamera,
                mFakeCameraSet,
                mFakeCameraDeviceSurfaceManager);
        assertThat(cameraUseCaseAdapter.isEquivalent(otherCameraUseCaseAdapter)).isTrue();
    }
}
