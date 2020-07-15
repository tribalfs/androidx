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

package androidx.camera.lifecycle;

import static com.google.common.truth.Truth.assertThat;

import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.internal.CameraUseCaseAdapter;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class LifecycleCameraRepositoryTest {

    private FakeLifecycleOwner mLifecycle;
    private LifecycleCameraRepository mRepository;
    private CameraUseCaseAdapter mCameraUseCaseAdapter;
    private LinkedHashSet<CameraInternal> mCameraSet;

    @Before
    public void setUp() {
        mLifecycle = new FakeLifecycleOwner();
        mRepository = new LifecycleCameraRepository();
        CameraInternal camera = new FakeCamera();
        mCameraSet = new LinkedHashSet<>(Collections.singleton(camera));
        mCameraUseCaseAdapter = new CameraUseCaseAdapter(camera,
                mCameraSet,
                new FakeCameraDeviceSurfaceManager());
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwException_ifTryingToCreateWithExistingIdentifier() {
        LifecycleCamera firstLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        LifecycleCamera secondLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);

        assertThat(firstLifecycleCamera).isSameInstanceAs(secondLifecycleCamera);
    }

    @Test
    public void differentLifecycleCamerasAreCreated_forDifferentLifecycles() {
        LifecycleCamera firstLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        FakeLifecycleOwner secondLifecycle = new FakeLifecycleOwner();
        LifecycleCamera secondLifecycleCamera =
                mRepository.createLifecycleCamera(secondLifecycle,
                        mCameraUseCaseAdapter);

        assertThat(firstLifecycleCamera).isNotEqualTo(secondLifecycleCamera);
    }

    @Test
    public void differentLifecycleCamerasAreCreated_forDifferentCameraSets() {
        LifecycleCamera firstLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);

        // Creates LifecycleCamera with different camera set
        LifecycleCamera secondLifecycleCamera =
                mRepository.createLifecycleCamera(mLifecycle, createNewCameraUseCaseAdapter());

        assertThat(firstLifecycleCamera).isNotEqualTo(secondLifecycleCamera);
    }

    @Test
    public void lifecycleCameraIsNotActive_createWithNoUseCasesAfterLifecycleStarted() {
        mLifecycle.start();
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        assertThat(lifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraIsNotActive_createWithNoUseCasesBeforeLifecycleStarted() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        assertThat(lifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraIsNotActive_bindUseCase_whenLifecycleIsNotStarted() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        // LifecycleCamera is inactive before the lifecycle state becomes ON_START.
        assertThat(lifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraIsActive_lifecycleStartedAfterBindUseCase() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        mLifecycle.start();
        // LifecycleCamera is active after the lifecycle state becomes ON_START.
        assertThat(lifecycleCamera.isActive()).isTrue();
    }

    @Test
    public void lifecycleCameraIsActive_bindToLifecycleCameraAfterLifecycleStarted() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));

        // LifecycleCamera is active after binding a use case when lifecycle state is ON_START.
        assertThat(lifecycleCamera.isActive()).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwException_withUseCase_twoLifecycleCamerasControlledByOneLifecycle() {
        // Creates first LifecycleCamera with use case bound.
        LifecycleCamera lifecycleCamera0 = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        mRepository.bindToLifecycleCamera(lifecycleCamera0, null,
                Collections.singletonList(new FakeUseCase()));

        // Creates second LifecycleCamera with use case bound to the same Lifecycle.
        LifecycleCamera lifecycleCamera1 = mRepository.createLifecycleCamera(mLifecycle,
                createNewCameraUseCaseAdapter());
        mRepository.bindToLifecycleCamera(lifecycleCamera1, null,
                Collections.singletonList(new FakeUseCase()));
    }

    @Test
    public void lifecycleCameraIsNotActive_withNoUseCase_unbindAfterLifecycleStarted() {
        // Creates LifecycleCamera with use case bound.
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        FakeUseCase useCase = new FakeUseCase();
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(useCase));

        // Unbinds the use case that was bound previously.
        mRepository.unbind(Collections.singletonList(useCase));

        // LifecycleCamera is not active if LifecycleCamera has no use case bound after unbinding
        // the use case.
        assertThat(lifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraIsActive_withUseCase_unbindAfterLifecycleStarted() {
        // Creates LifecycleCamera with two use cases bound.
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        FakeUseCase useCase0 = new FakeUseCase();
        FakeUseCase useCase1 = new FakeUseCase();
        mRepository.bindToLifecycleCamera(lifecycleCamera, null, Arrays.asList(useCase0, useCase1));

        // Only unbinds one use case but another one is kept in the LifecycleCamera.
        mRepository.unbind(Collections.singletonList(useCase0));

        // LifecycleCamera is active if LifecycleCamera still has use case bound after unbinding
        // the use case.
        assertThat(lifecycleCamera.isActive()).isTrue();
    }

    @Test
    public void lifecycleCameraIsNotActive_unbindAllAfterLifecycleStarted() {
        // Creates LifecycleCamera with use case bound.
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));

        // Unbinds all use cases from all LifecycleCamera by the unbindAll() API.
        mRepository.unbindAll();

        // LifecycleCamera is not active after unbinding all use cases.
        assertThat(lifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraOf1stActiveLifecycleIsInactive_bindToNewActiveLifecycleCamera() {
        // Starts first lifecycle with use case bound.
        LifecycleCamera lifecycleCamera0 = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera0, null,
                Collections.singletonList(new FakeUseCase()));

        // Starts second lifecycle with use case bound.
        FakeLifecycleOwner lifecycle1 = new FakeLifecycleOwner();
        LifecycleCamera lifecycleCamera1 = mRepository.createLifecycleCamera(lifecycle1,
                createNewCameraUseCaseAdapter());
        lifecycle1.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera1, null,
                Collections.singletonList(new FakeUseCase()));

        // The previous LifecycleCamera becomes inactive after new LifecycleCamera becomes active.
        assertThat(lifecycleCamera0.isActive()).isFalse();
        // New LifecycleCamera becomes active after binding use case to it.
        assertThat(lifecycleCamera1.isActive()).isTrue();
    }

    @Test
    public void lifecycleCameraOf1stActiveLifecycleIsActive_bindNewUseCase() {
        // Starts first lifecycle with use case bound.
        LifecycleCamera lifecycleCamera0 = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera0, null,
                Collections.singletonList(new FakeUseCase()));

        // Starts second lifecycle with use case bound.
        FakeLifecycleOwner lifecycle1 = new FakeLifecycleOwner();
        LifecycleCamera lifecycleCamera1 = mRepository.createLifecycleCamera(lifecycle1,
                createNewCameraUseCaseAdapter());
        lifecycle1.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera1, null,
                Collections.singletonList(new FakeUseCase()));

        // Binds new use case to the next most recent active LifecycleCamera.
        mRepository.bindToLifecycleCamera(lifecycleCamera0, null,
                Collections.singletonList(new FakeUseCase()));

        // The next most recent active LifecycleCamera becomes active after binding new use case.
        assertThat(lifecycleCamera0.isActive()).isTrue();
        // The original active LifecycleCamera becomes inactive after the next most recent active
        // LifecycleCamera becomes active.
        assertThat(lifecycleCamera1.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraOf2ndActiveLifecycleIsActive_unbindFromActiveLifecycleCamera() {
        // Starts first lifecycle with use case bound.
        LifecycleCamera lifecycleCamera0 = mRepository.createLifecycleCamera(mLifecycle,
                mCameraUseCaseAdapter);
        mLifecycle.start();
        mRepository.bindToLifecycleCamera(lifecycleCamera0, null,
                Collections.singletonList(new FakeUseCase()));

        // Starts second lifecycle with use case bound.
        FakeLifecycleOwner lifecycle1 = new FakeLifecycleOwner();
        LifecycleCamera lifecycleCamera1 = mRepository.createLifecycleCamera(lifecycle1,
                createNewCameraUseCaseAdapter());
        lifecycle1.start();
        FakeUseCase useCase = new FakeUseCase();
        mRepository.bindToLifecycleCamera(lifecycleCamera1, null,
                Collections.singletonList(useCase));

        // Unbinds use case from the most recent active LifecycleCamera.
        mRepository.unbind(Collections.singletonList(useCase));

        // The most recent active LifecycleCamera becomes inactive after all use case unbound
        // from it.
        assertThat(lifecycleCamera1.isActive()).isFalse();
        // The next most recent active LifecycleCamera becomes active after previous active
        // LifecycleCamera becomes inactive.
        assertThat(lifecycleCamera0.isActive()).isTrue();
    }

    @Test
    public void useCaseIsCleared_whenLifecycleIsDestroyed() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        FakeUseCase useCase = new FakeUseCase();
        mRepository.bindToLifecycleCamera(lifecycleCamera, null,
                Collections.singletonList(useCase));

        assertThat(useCase.isCleared()).isFalse();

        mLifecycle.destroy();

        assertThat(useCase.isCleared()).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void exception_whenCreatingWithDestroyedLifecycle() {
        mLifecycle.destroy();

        // Should throw IllegalArgumentException
        mRepository.createLifecycleCamera(mLifecycle, mCameraUseCaseAdapter);
    }

    @Test
    public void lifecycleCameraIsStopped_whenNewLifecycleIsStarted() {
        // Starts first lifecycle and check LifecycleCamera active state is true.
        LifecycleCamera firstLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        mRepository.bindToLifecycleCamera(firstLifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        mLifecycle.start();
        assertThat(firstLifecycleCamera.isActive()).isTrue();

        // Starts second lifecycle and check previous LifecycleCamera is stopped.
        FakeLifecycleOwner secondLifecycle = new FakeLifecycleOwner();
        LifecycleCamera secondLifecycleCamera = mRepository.createLifecycleCamera(secondLifecycle,
                createNewCameraUseCaseAdapter());
        mRepository.bindToLifecycleCamera(secondLifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        secondLifecycle.start();
        assertThat(secondLifecycleCamera.isActive()).isTrue();
        assertThat(firstLifecycleCamera.isActive()).isFalse();
    }

    @Test
    public void lifecycleCameraOf2ndActiveLifecycleIsStarted_when1stActiveLifecycleIsStopped() {
        // Starts first lifecycle and check LifecycleCamera active state is true.
        LifecycleCamera firstLifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        mRepository.bindToLifecycleCamera(firstLifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        mLifecycle.start();
        assertThat(firstLifecycleCamera.isActive()).isTrue();

        // Starts second lifecycle and check previous LifecycleCamera is stopped.
        FakeLifecycleOwner secondLifecycle = new FakeLifecycleOwner();
        LifecycleCamera secondLifecycleCamera = mRepository.createLifecycleCamera(secondLifecycle,
                createNewCameraUseCaseAdapter());
        mRepository.bindToLifecycleCamera(secondLifecycleCamera, null,
                Collections.singletonList(new FakeUseCase()));
        secondLifecycle.start();
        assertThat(secondLifecycleCamera.isActive()).isTrue();
        assertThat(firstLifecycleCamera.isActive()).isFalse();

        // Stops second lifecycle and check previous LifecycleCamera is started again.
        secondLifecycle.stop();
        assertThat(secondLifecycleCamera.isActive()).isFalse();
        assertThat(firstLifecycleCamera.isActive()).isTrue();
    }

    @Test
    public void retrievesExistingCamera() {
        LifecycleCamera lifecycleCamera = mRepository.createLifecycleCamera(
                mLifecycle, mCameraUseCaseAdapter);
        CameraUseCaseAdapter.CameraId cameraId = CameraUseCaseAdapter.generateCameraId(mCameraSet);
        LifecycleCamera retrieved = mRepository.getLifecycleCamera(mLifecycle, cameraId);

        assertThat(lifecycleCamera).isSameInstanceAs(retrieved);
    }

    @Test
    public void keys() {
        LifecycleCameraRepository.Key key0 = LifecycleCameraRepository.Key.create(mLifecycle,
                mCameraUseCaseAdapter.getCameraId());
        LifecycleCameraRepository.Key key1 = LifecycleCameraRepository.Key.create(mLifecycle,
                CameraUseCaseAdapter.generateCameraId(mCameraSet));

        Map<LifecycleCameraRepository.Key, LifecycleOwner> map = new HashMap<>();
        map.put(key0, mLifecycle);
        assertThat(map).containsKey(key1);

        assertThat(key0).isEqualTo(key1);
    }

    private CameraUseCaseAdapter createNewCameraUseCaseAdapter() {
        CameraInternal fakeCamera = new FakeCamera("other");
        return new CameraUseCaseAdapter(fakeCamera,
                new LinkedHashSet<>(Collections.singleton(fakeCamera)),
                new FakeCameraDeviceSurfaceManager());
    }
}
