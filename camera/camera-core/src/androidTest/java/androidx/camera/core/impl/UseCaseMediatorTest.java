/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.core.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.camera.core.FakeOtherUseCase;
import androidx.camera.core.FakeOtherUseCaseConfig;
import androidx.camera.core.internal.CameraUseCaseAdapter;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class UseCaseMediatorTest {
    private UseCaseMediator mUseCaseMediator;
    private FakeUseCase mFakeUseCase;
    private FakeOtherUseCase mFakeOtherUseCase;
    private CameraUseCaseAdapter mCameraUseCaseAdapter;
    private CameraInternal mMockCamera = mock(CameraInternal.class);

    @Before
    public void setUp() {
        FakeUseCaseConfig fakeUseCaseConfig = new FakeUseCaseConfig.Builder()
                .setTargetName("fakeUseCaseConfig")
                .getUseCaseConfig();
        FakeOtherUseCaseConfig fakeOtherUseCaseConfig =
                new FakeOtherUseCaseConfig.Builder()
                        .setTargetName("fakeOtherUseCaseConfig")
                        .getUseCaseConfig();
        mCameraUseCaseAdapter = new CameraUseCaseAdapter(new FakeCamera(),
                new FakeCameraDeviceSurfaceManager());
        mUseCaseMediator = new UseCaseMediator(mCameraUseCaseAdapter);
        mFakeUseCase = new FakeUseCase(fakeUseCaseConfig);
        mFakeOtherUseCase = new FakeOtherUseCase(fakeOtherUseCaseConfig);
    }

    @Test
    public void mediatorStartsEmpty() {
        assertThat(mUseCaseMediator.getUseCases()).isEmpty();
    }

    @Test
    public void newUseCaseIsAdded_whenNoneExistsInMediator() {
        assertThat(mUseCaseMediator.addUseCase(mFakeUseCase)).isTrue();
        assertThat(mUseCaseMediator.getUseCases()).containsExactly(mFakeUseCase);
    }

    @Test
    public void multipleUseCases_canBeAdded() {
        assertThat(mUseCaseMediator.addUseCase(mFakeUseCase)).isTrue();
        assertThat(mUseCaseMediator.addUseCase(mFakeOtherUseCase)).isTrue();

        assertThat(mUseCaseMediator.getUseCases()).containsExactly(mFakeUseCase, mFakeOtherUseCase);
    }

    @Test
    public void mediatorBecomesEmpty_afterMediatorIsCleared()
            throws CameraUseCaseAdapter.CameraException {
        mUseCaseMediator.addUseCase(mFakeUseCase);
        mCameraUseCaseAdapter.attachUseCases(Collections.singleton(mFakeUseCase));

        mUseCaseMediator.destroy();

        assertThat(mUseCaseMediator.getUseCases()).isEmpty();
    }

    @Test
    public void useCaseIsDetached_afterMediatorIsCleared()
            throws CameraUseCaseAdapter.CameraException {
        mUseCaseMediator.addUseCase(mFakeUseCase);
        mCameraUseCaseAdapter.attachUseCases(Collections.singleton(mFakeUseCase));

        assertThat(mFakeUseCase.isCleared()).isFalse();

        mUseCaseMediator.destroy();

        // Assert - when the lifecycle is destroyed it should detach the UseCase from the Camera
        assertThat(mFakeUseCase.getCamera()).isNull();
    }

    @Test
    public void useCaseRemoved_afterRemovedCalled() {
        mUseCaseMediator.addUseCase(mFakeUseCase);

        mUseCaseMediator.removeUseCase(mFakeUseCase);

        assertThat(mUseCaseMediator.getUseCases()).isEmpty();
    }

    @Test
    public void cameraInternalAttached_ifUseCaseMediatorStarted() {
        CameraUseCaseAdapter cameraUseCaseAdaptor = new CameraUseCaseAdapter(mMockCamera,
                new FakeCameraDeviceSurfaceManager());
        UseCaseMediator useCaseMediator = new UseCaseMediator(cameraUseCaseAdaptor);
        useCaseMediator.start();
        verify(mMockCamera, times(1)).attachUseCases(any());
    }

    @Test
    public void cameraInternalDetached_ifUseCaseMediatorStopped() {
        CameraUseCaseAdapter cameraUseCaseAdaptor = new CameraUseCaseAdapter(mMockCamera,
                new FakeCameraDeviceSurfaceManager());
        UseCaseMediator useCaseMediator = new UseCaseMediator(cameraUseCaseAdaptor);
        useCaseMediator.stop();
        verify(mMockCamera, times(1)).detachUseCases(any());
    }
}
