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

package androidx.camera.camera2.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import android.Manifest;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.impl.compat.CameraManagerCompat;
import androidx.camera.core.BaseCamera;
import androidx.camera.core.CameraCaptureCallback;
import androidx.camera.core.CameraCaptureResult;
import androidx.camera.core.CameraDeviceConfig;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.CaptureConfig;
import androidx.camera.core.DeferrableSurface;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.Observable;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.HandlerUtil;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contains tests for {@link androidx.camera.camera2.impl.Camera2CameraImpl} internal
 * implementation.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class Camera2CameraImplTest {
    private static final LensFacing DEFAULT_LENS_FACING = LensFacing.BACK;
    // For the purpose of this test, always say we have 1 camera available.
    private static final int DEFAULT_AVAILABLE_CAMERA_COUNT = 1;
    private static final Set<BaseCamera.State> STABLE_STATES = new HashSet<>(Arrays.asList(
            BaseCamera.State.CLOSED,
            BaseCamera.State.OPEN,
            BaseCamera.State.RELEASED));
    static CameraFactory sCameraFactory;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);

    private ArrayList<FakeUseCase> mFakeUseCases = new ArrayList<>();
    private Camera2CameraImpl mCamera2CameraImpl;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    private SettableObservable<Integer> mAvailableCameras;
    Semaphore mSemaphore;
    OnImageAvailableListener mMockOnImageAvailableListener;
    String mCameraId;

    private static String getCameraIdForLensFacingUnchecked(LensFacing lensFacing) {
        try {
            return sCameraFactory.cameraIdForLensFacing(lensFacing);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to attach to camera with LensFacing " + lensFacing, e);
        }
    }

    @BeforeClass
    public static void classSetup() {
        sCameraFactory = new Camera2CameraFactory(ApplicationProvider.getApplicationContext());
    }

    @Before
    public void setup() {
        assumeTrue(CameraUtil.deviceHasCamera());
        mMockOnImageAvailableListener = Mockito.mock(ImageReader.OnImageAvailableListener.class);

        mCameraId = getCameraIdForLensFacingUnchecked(DEFAULT_LENS_FACING);
        mCameraHandlerThread = new HandlerThread("cameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
        mSemaphore = new Semaphore(0);
        mAvailableCameras = new SettableObservable<>(DEFAULT_AVAILABLE_CAMERA_COUNT);
        mCamera2CameraImpl = new Camera2CameraImpl(
                CameraManagerCompat.from(ApplicationProvider.getApplicationContext()),
                mCameraId, mAvailableCameras, mCameraHandler);
    }

    @After
    public void teardown() throws InterruptedException, ExecutionException {
        // Need to release the camera no matter what is done, otherwise the CameraDevice is not
        // closed.
        // When the CameraDevice is not closed, then it can cause problems with interferes with
        // other test cases.
        if (mCamera2CameraImpl != null) {
            ListenableFuture<Void> cameraReleaseFuture = mCamera2CameraImpl.release();

            // Wait for camera to be fully closed
            cameraReleaseFuture.get();

            mCamera2CameraImpl = null;
        }

        for (FakeUseCase fakeUseCase : mFakeUseCases) {
            fakeUseCase.clear();
        }

        if (mCameraHandlerThread != null) {
            mCameraHandlerThread.quitSafely();
        }
    }

    @Test
    public void onlineUseCase() {
        mCamera2CameraImpl.open();

        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(createUseCase()));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));

        mCamera2CameraImpl.release();
    }

    @Test
    public void activeUseCase() {
        mCamera2CameraImpl.open();
        mCamera2CameraImpl.onUseCaseActive(createUseCase());

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));

        mCamera2CameraImpl.release();
    }

    @Test
    public void onlineAndActiveUseCase() {
        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.onUseCaseActive(useCase1);

        verify(mMockOnImageAvailableListener, timeout(4000).atLeastOnce())
                .onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void removeOnlineUseCase() {
        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));

        mCamera2CameraImpl.removeOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.onUseCaseActive(useCase1);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void unopenedCamera() {
        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.removeOnlineUseCase(Collections.<UseCase>singletonList(useCase1));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void closedCamera() {
        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.removeOnlineUseCase(Collections.<UseCase>singletonList(useCase1));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void releaseUnopenedCamera() {
        UseCase useCase1 = createUseCase();
        // Checks that if a camera has been released then calling open() will no longer open it.
        mCamera2CameraImpl.release();
        mCamera2CameraImpl.open();

        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.onUseCaseActive(useCase1);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void releasedOpenedCamera() {
        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.open();
        mCamera2CameraImpl.release();

        mCamera2CameraImpl.addOnlineUseCase(Collections.<UseCase>singletonList(useCase1));
        mCamera2CameraImpl.onUseCaseActive(useCase1);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void addOnline_OneUseCase() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isFalse();

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isTrue();

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void addOnline_SameUseCases() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isFalse();

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
    }


    @Test
    public void addOnline_alreadyOnline() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        //Usecase1 is online now.
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isTrue();

        blockHandler();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera2CameraImpl.close();
        waitForCameraClose(mCamera2CameraImpl);

        // Surface is only attached once.
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void addOnline_twoUseCases() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        UseCase useCase2 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase2));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(getUseCaseSurface(useCase2).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isFalse();
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase2)).isFalse();

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isTrue();
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase2)).isTrue();

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1, useCase2));
    }

    @Test
    public void addOnline_fromPendingOffline() throws InterruptedException {
        blockHandler();

        // First make UseCase online
        UseCase useCase = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase));
        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        blockHandler();
        // Then make it offline but pending for Camera thread to run it.
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase));

        // Then add the same UseCase .
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase)).isTrue();

        mCamera2CameraImpl.close();
        waitForCameraClose(mCamera2CameraImpl);

        assertThat(getUseCaseSurface(useCase).getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase));
    }

    @Test
    public void removeOnline_notOnline() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        // It should not be detached so the attached count should still be 0
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);
    }

    @Test
    public void removeOnline_fromPendingOnline() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isFalse();
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);
    }

    @Test
    public void removeOnline_fromOnlineUseCases() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isTrue();
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase2)).isTrue();

        blockHandler();
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase1)).isFalse();
        assertThat(mCamera2CameraImpl.isUseCaseOnline(useCase2)).isTrue();

        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera2CameraImpl.close();
        waitForCameraClose(mCamera2CameraImpl);

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);
        assertThat(getUseCaseSurface(useCase2).getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase2));
    }

    @Test
    public void removeOnline_twoSameUseCase() throws InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        // remove twice
        blockHandler();
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera2CameraImpl.close();
        waitForCameraClose(mCamera2CameraImpl);

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase2));
    }

    @Test
    public void onlineUseCase_changeSurface_onUseCaseUpdated_correctAttachCount()
            throws ExecutionException, InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        DeferrableSurface surface1 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        changeUseCaseSurface(useCase1);
        mCamera2CameraImpl.onUseCaseUpdated(useCase1);
        DeferrableSurface surface2 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        // Wait for camera to be released to ensure it has finished closing
        ListenableFuture<Void> releaseFuture = mCamera2CameraImpl.release();
        releaseFuture.get();

        assertThat(surface1).isNotEqualTo(surface2);

        // Old surface was removed from Camera
        assertThat(surface1.getAttachedCount()).isEqualTo(0);
        // New surface is still in Camera
        assertThat(surface2.getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void onlineUseCase_changeSurface_onUseCaseReset_correctAttachCount()
            throws ExecutionException, InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        DeferrableSurface surface1 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        changeUseCaseSurface(useCase1);
        mCamera2CameraImpl.onUseCaseReset(useCase1);
        DeferrableSurface surface2 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        // Wait for camera to be released to ensure it has finished closing
        ListenableFuture<Void> releaseFuture = mCamera2CameraImpl.release();
        releaseFuture.get();

        assertThat(surface1).isNotEqualTo(surface2);

        // Old surface was removed from Camera
        assertThat(surface1.getAttachedCount()).isEqualTo(0);
        // New surface is still in Camera
        assertThat(surface2.getAttachedCount()).isEqualTo(1);
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void onlineUseCase_changeSurface_onUseCaseActive_correctAttachCount()
            throws ExecutionException, InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        DeferrableSurface surface1 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        changeUseCaseSurface(useCase1);
        mCamera2CameraImpl.onUseCaseActive(useCase1);
        DeferrableSurface surface2 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        // Wait for camera to be released to ensure it has finished closing
        ListenableFuture<Void> releaseFuture = mCamera2CameraImpl.release();
        releaseFuture.get();

        assertThat(surface1).isNotEqualTo(surface2);

        // Old surface was removed from Camera
        assertThat(surface1.getAttachedCount()).isEqualTo(0);
        // New surface is still in Camera
        assertThat(surface2.getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }


    @Test
    public void offlineUseCase_changeSurface_onUseCaseUpdated_correctAttachCount()
            throws ExecutionException, InterruptedException {
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();
        // useCase1 is offline.
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase2));

        DeferrableSurface surface1 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        changeUseCaseSurface(useCase1);
        mCamera2CameraImpl.onUseCaseUpdated(useCase1);
        DeferrableSurface surface2 = useCase1.getSessionConfig(mCameraId).getSurfaces().get(0);

        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        assertThat(surface1).isNotEqualTo(surface2);

        // No attachment because useCase1 is offline.
        assertThat(surface1.getAttachedCount()).isEqualTo(0);
        assertThat(surface2.getAttachedCount()).isEqualTo(0);

        // make useCase1 online
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));

        // Wait for camera to be released to ensure it has finished closing
        ListenableFuture<Void> releaseFuture = mCamera2CameraImpl.release();
        releaseFuture.get();

        // only surface2 is attached.
        assertThat(surface1.getAttachedCount()).isEqualTo(0);
        assertThat(surface2.getAttachedCount()).isEqualTo(1);

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void pendingSingleRequestRunSuccessfully_whenAnotherUseCaseOnline()
            throws InterruptedException {

        // Block camera thread to queue all the camera operations.
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));

        CameraCaptureCallback captureCallback = mock(CameraCaptureCallback.class);
        CaptureConfig.Builder captureConfigBuilder = new CaptureConfig.Builder();
        captureConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
        captureConfigBuilder.addSurface(useCase1.getSessionConfig(mCameraId).getSurfaces().get(0));
        captureConfigBuilder.addCameraCaptureCallback(captureCallback);

        mCamera2CameraImpl.getCameraControlInternal().submitCaptureRequests(
                Arrays.asList(captureConfigBuilder.build()));

        UseCase useCase2 = createUseCase();
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase2));

        // Unblock camera handler to make camera operation run quickly .
        // To make the single request not able to run in 1st capture session.  and verify if it can
        // be carried over to the new capture session and run successfully.
        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        // CameraCaptureCallback.onCaptureCompleted() should be called to signal a capture attempt.
        verify(captureCallback, timeout(3000).times(1))
                .onCaptureCompleted(any(CameraCaptureResult.class));
    }

    @Test
    public void pendingSingleRequestSkipped_whenTheUseCaseIsRemoved()
            throws InterruptedException {

        // Block camera thread to queue all the camera operations.
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();

        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        CameraCaptureCallback captureCallback = mock(CameraCaptureCallback.class);
        CaptureConfig.Builder captureConfigBuilder = new CaptureConfig.Builder();
        captureConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
        captureConfigBuilder.addSurface(useCase1.getSessionConfig(mCameraId).getSurfaces().get(0));
        captureConfigBuilder.addCameraCaptureCallback(captureCallback);

        mCamera2CameraImpl.getCameraControlInternal().submitCaptureRequests(
                Arrays.asList(captureConfigBuilder.build()));
        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1));

        // Unblock camera handle to make camera operation run quickly .
        // To make the single request not able to run in 1st capture session.  and verify if it can
        // be carried to the new capture session and run successfully.
        unblockHandler();
        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        // TODO: b/133710422 should provide a way to detect if request is cancelled.
        Thread.sleep(1000);

        // CameraCaptureCallback.onCaptureCompleted() is not called and there is no crash.
        verify(captureCallback, times(0))
                .onCaptureCompleted(any(CameraCaptureResult.class));
    }

    @Test
    public void cameraStateIsClosed_afterInitialization()
            throws ExecutionException, InterruptedException {
        Observable<BaseCamera.State> state = mCamera2CameraImpl.getCameraState();
        BaseCamera.State currentState = state.fetchData().get();
        assertThat(currentState).isEqualTo(BaseCamera.State.CLOSED);
    }

    @Test
    public void cameraStateTransitionTest() throws InterruptedException {

        final AtomicReference<BaseCamera.State> lastStableState = new AtomicReference<>(null);
        Observable.Observer<BaseCamera.State> observer =
                new Observable.Observer<BaseCamera.State>() {
                    @Override
                    public void onNewData(@Nullable BaseCamera.State value) {
                        // Ignore any transient states.
                        if (STABLE_STATES.contains(value)) {
                            lastStableState.set(value);
                            mSemaphore.release();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable t) { /* Ignore any transient errors. */ }
                };

        List<BaseCamera.State> observedStates = new ArrayList<>();
        mCamera2CameraImpl.getCameraState().addObserver(CameraXExecutors.directExecutor(),
                observer);

        // Wait for initial CLOSED state
        mSemaphore.acquire();
        observedStates.add(lastStableState.get());

        // Wait for OPEN state
        mCamera2CameraImpl.open();
        mSemaphore.acquire();
        observedStates.add(lastStableState.get());

        // Wait for CLOSED state again
        mCamera2CameraImpl.close();
        mSemaphore.acquire();
        observedStates.add(lastStableState.get());

        // Wait for RELEASED state
        mCamera2CameraImpl.release();
        mSemaphore.acquire();
        observedStates.add(lastStableState.get());

        mCamera2CameraImpl.getCameraState().removeObserver(observer);

        assertThat(observedStates).containsExactly(
                BaseCamera.State.CLOSED,
                BaseCamera.State.OPEN,
                BaseCamera.State.CLOSED,
                BaseCamera.State.RELEASED);
    }

    @Test
    public void cameraTransitionsThroughPendingState_whenNoCamerasAvailable() {
        @SuppressWarnings("unchecked") // Cannot mock generic type inline
                Observable.Observer<BaseCamera.State> mockObserver =
                mock(Observable.Observer.class);

        // Set the available cameras to zero
        mAvailableCameras.setValue(0);

        mCamera2CameraImpl.getCameraState().addObserver(CameraXExecutors.directExecutor(),
                mockObserver);

        mCamera2CameraImpl.open();

        // Ensure that the camera gets to a PENDING_OPEN state
        verify(mockObserver, timeout(3000)).onNewData(BaseCamera.State.PENDING_OPEN);

        // Allow camera to be opened
        mAvailableCameras.setValue(1);

        verify(mockObserver, timeout(3000)).onNewData(BaseCamera.State.OPEN);

        mCamera2CameraImpl.getCameraState().removeObserver(mockObserver);
    }

    @Test
    public void cameraStateIsReleased_afterRelease()
            throws ExecutionException, InterruptedException {
        Observable<BaseCamera.State> state = mCamera2CameraImpl.getCameraState();

        // Wait for camera to release
        mCamera2CameraImpl.release().get();
        BaseCamera.State currentState = state.fetchData().get();

        assertThat(currentState).isEqualTo(BaseCamera.State.RELEASED);
    }

    @Test
    public void cameraStopsObservingAvailableCameras_afterRelease()
            throws ExecutionException, InterruptedException {
        // Camera should already be observing state after initialization
        int observerCountBefore = mAvailableCameras.getObserverCount();

        // Wait for camera to release
        mCamera2CameraImpl.release().get();

        // Observer count should now be zero
        int observerCountAfter = mAvailableCameras.getObserverCount();

        assertThat(observerCountBefore).isEqualTo(1);
        assertThat(observerCountAfter).isEqualTo(0);
    }

    // Blocks the camera thread handler.
    private void blockHandler() {
        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mSemaphore.acquire();
                } catch (InterruptedException e) {

                }
            }
        });
    }

    // unblock camera thread handler
    private void unblockHandler() {
        mSemaphore.release();
    }

    private UseCase createUseCase() {
        FakeUseCaseConfig.Builder configBuilder =
                new FakeUseCaseConfig.Builder()
                        .setTargetName("UseCase")
                        .setLensFacing(DEFAULT_LENS_FACING);
        TestUseCase testUseCase = new TestUseCase(configBuilder.build(),
                mMockOnImageAvailableListener);
        Map<String, Size> suggestedResolutionMap = new HashMap<>();
        suggestedResolutionMap.put(mCameraId, new Size(640, 480));
        testUseCase.updateSuggestedResolution(suggestedResolutionMap);
        mFakeUseCases.add(testUseCase);
        return testUseCase;
    }

    @Test
    public void useCaseOnStateOnline_isCalled() throws InterruptedException {
        TestUseCase useCase1 = spy((TestUseCase) createUseCase());
        TestUseCase useCase2 = spy((TestUseCase) createUseCase());

        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1));
        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        Handler uiThreadHandler = new Handler(Looper.getMainLooper());
        HandlerUtil.waitForLooperToIdle(uiThreadHandler);

        verify(useCase1, times(1)).onStateOnline(eq(mCameraId));
        verify(useCase2, times(1)).onStateOnline(eq(mCameraId));
    }

    @Test
    public void useCaseOnStateOffline_isCalled() throws InterruptedException {
        TestUseCase useCase1 = spy((TestUseCase) createUseCase());
        TestUseCase useCase2 = spy((TestUseCase) createUseCase());
        TestUseCase useCase3 = spy((TestUseCase) createUseCase());

        mCamera2CameraImpl.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        mCamera2CameraImpl.removeOnlineUseCase(Arrays.asList(useCase1, useCase2, useCase3));

        HandlerUtil.waitForLooperToIdle(mCameraHandler);

        Handler uiThreadHandler = new Handler(Looper.getMainLooper());
        HandlerUtil.waitForLooperToIdle(uiThreadHandler);

        verify(useCase1, times(1)).onStateOffline(eq(mCameraId));
        verify(useCase2, times(1)).onStateOffline(eq(mCameraId));
        verify(useCase3, times(0)).onStateOffline(eq(mCameraId));
    }

    private DeferrableSurface getUseCaseSurface(UseCase useCase) {
        return useCase.getSessionConfig(mCameraId).getSurfaces().get(0);
    }

    private void changeUseCaseSurface(UseCase useCase) {
        Map<String, Size> suggestedResolutionMap = new HashMap<>();
        suggestedResolutionMap.put(mCameraId, new Size(640, 480));
        useCase.updateSuggestedResolution(suggestedResolutionMap);
    }

    private void waitForCameraClose(Camera2CameraImpl camera2CameraImpl)
            throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);

        Observable.Observer<BaseCamera.State> observer =
                new Observable.Observer<BaseCamera.State>() {
                    @Override
                    public void onNewData(@Nullable BaseCamera.State value) {
                        // Ignore any transient states.
                        if (value == BaseCamera.State.CLOSED) {
                            semaphore.release();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable t) { /* Ignore any transient errors. */ }
                };

        camera2CameraImpl.getCameraState().addObserver(CameraXExecutors.directExecutor(), observer);

        // Wait until camera reaches closed state
        semaphore.acquire();
    }

    public static class TestUseCase extends FakeUseCase {
        private final ImageReader.OnImageAvailableListener mImageAvailableListener;
        HandlerThread mHandlerThread = new HandlerThread("HandlerThread");
        Handler mHandler;
        ImageReader mImageReader;
        FakeUseCaseConfig mConfig;
        List<ImageReader> mPreviousReaders = new ArrayList<>();

        TestUseCase(
                FakeUseCaseConfig config,
                ImageReader.OnImageAvailableListener listener) {
            super(config);
            // Ensure we're using the combined configuration (user config + defaults)
            mConfig = (FakeUseCaseConfig) getUseCaseConfig();

            mImageAvailableListener = listener;
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            Map<String, Size> suggestedResolutionMap = new HashMap<>();
            String cameraId = getCameraIdForLensFacingUnchecked(config.getLensFacing());
            suggestedResolutionMap.put(cameraId, new Size(640, 480));
            updateSuggestedResolution(suggestedResolutionMap);
        }

        // we need to set Camera2OptionUnpacker to the Config to enable the camera2 callback hookup.
        @Override
        protected UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder(CameraX.LensFacing lensFacing) {
            return new FakeUseCaseConfig.Builder()
                    .setLensFacing(lensFacing)
                    .setSessionOptionUnpacker(new Camera2SessionOptionUnpacker());
        }

        void close() {
            mHandler.removeCallbacksAndMessages(null);
            mHandlerThread.quitSafely();
            for (ImageReader reader : mPreviousReaders) {
                reader.close();
            }

            mPreviousReaders.clear();

            if (mImageReader != null) {
                mImageReader.close();
            }
        }

        @Override
        @NonNull
        protected Map<String, Size> onSuggestedResolutionUpdated(
                @NonNull Map<String, Size> suggestedResolutionMap) {
            LensFacing lensFacing = ((CameraDeviceConfig) getUseCaseConfig()).getLensFacing();
            String cameraId = getCameraIdForLensFacingUnchecked(lensFacing);
            Size resolution = suggestedResolutionMap.get(cameraId);
            SessionConfig.Builder builder = SessionConfig.Builder.createFrom(mConfig);

            builder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            if (mImageReader != null) {
                // Hold on to the previous reader so its Surface stays alive and it can be closed
                // later.
                mPreviousReaders.add(mImageReader);
            }
            mImageReader =
                    ImageReader.newInstance(
                            resolution.getWidth(),
                            resolution.getHeight(),
                            ImageFormat.YUV_420_888, /*maxImages*/
                            2);
            mImageReader.setOnImageAvailableListener(mImageAvailableListener, mHandler);
            builder.addSurface(new ImmediateSurface(mImageReader.getSurface()));

            attachToCamera(cameraId, builder.build());
            return suggestedResolutionMap;
        }
    }

    private final class SettableObservable<T> implements Observable<T> {

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private T mValue;

        @GuardedBy("mLock")
        private Map<Observer<T>, Executor> mObservers = new HashMap<>();

        SettableObservable(@Nullable T initialValue) {
            synchronized (mLock) {
                mValue = initialValue;
            }
        }

        void setValue(@Nullable final T value) {
            Map<Observer<T>, Executor> notifyMap = null;
            synchronized (mLock) {
                if (!Objects.equals(mValue, value)) {
                    mValue = value;

                    if (!mObservers.isEmpty()) {
                        notifyMap = new HashMap<>(mObservers);
                    }
                }
            }

            if (notifyMap != null) {
                for (Map.Entry<Observer<T>, Executor> observer : notifyMap.entrySet()) {
                    observer.getValue().execute(() -> observer.getKey().onNewData(value));
                }
            }
        }

        @NonNull
        @Override
        public ListenableFuture<T> fetchData() {
            synchronized (mLock) {
                return Futures.immediateFuture(mValue);
            }
        }

        @Override
        public void addObserver(@NonNull Executor executor, @NonNull Observer<T> observer) {
            boolean needsUpdate = false;
            T value;
            synchronized (mLock) {
                needsUpdate = !Objects.equals(mObservers.put(observer, executor), executor);
                value = mValue;
            }

            if (needsUpdate) {
                executor.execute(() -> observer.onNewData(value));
            }
        }

        @Override
        public void removeObserver(@NonNull Observer<T> observer) {
            synchronized (mLock) {
                mObservers.remove(observer);
            }
        }

        int getObserverCount() {
            synchronized (mLock) {
                return mObservers.size();
            }
        }
    }
}
