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

import android.Manifest;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.MessageQueue;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraDeviceConfig;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.DeferrableSurface;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseConfig;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Contains tests for {@link androidx.camera.camera2.impl.Camera} internal implementation.
 */
public class CameraImplTest {
    private static final CameraX.LensFacing DEFAULT_LENS_FACING = CameraX.LensFacing.BACK;
    static CameraFactory sCameraFactory;

    private Camera mCamera;
    private String mCameraId;

    Semaphore mSemaphore;

    CountDownLatch mLatchForDeviceClose;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    MessageQueue mMessageQueue;
    private boolean mWaitCameraCloseAtTearDown = true;

    private CameraDevice.StateCallback mDeviceStateCallback;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);

    private static String getCameraIdForLensFacingUnchecked(CameraX.LensFacing lensFacing) {
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
    public void setUp() {
        mCameraId = getCameraIdForLensFacingUnchecked(DEFAULT_LENS_FACING);

        mCameraHandlerThread = new HandlerThread("cameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
        mSemaphore = new Semaphore(0);
        mCamera = new Camera(CameraUtil.getCameraManager(), mCameraId, mCameraHandler);
        mLatchForDeviceClose = new CountDownLatch(1);

        // To get MessageQueue from HandlerThread.
        final CountDownLatch latchforMsgQueue = new CountDownLatch(1);
        mCameraHandler.post(new Runnable() {
            @Override
            public void run() {
                mMessageQueue = Looper.myQueue();
                latchforMsgQueue.countDown();
            }
        });

        try {
            latchforMsgQueue.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }

        mDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
            }

            @Override
            public void onClosed(@NonNull CameraDevice camera) {
                mLatchForDeviceClose.countDown();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
            }
        };

    }

    @After
    public void teardown() throws InterruptedException {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

        if (mWaitCameraCloseAtTearDown) {
            mLatchForDeviceClose.await(2, TimeUnit.SECONDS);
        }

        if (mCameraHandlerThread != null) {
            mCameraHandlerThread.quitSafely();
        }
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

    // wait until camera thread is idle
    private void waitHandlerIdle() {
        final CountDownLatch latchForWaitIdle = new CountDownLatch(1);


        // If the posted runnable runs, it means the previous runnnables are already executed.
        mMessageQueue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                latchForWaitIdle.countDown();
                return false;

            }
        });

        try {
            latchForWaitIdle.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    private UseCase createUseCase() {
        FakeUseCaseConfig.Builder configBuilder =
                new FakeUseCaseConfig.Builder()
                        .setTargetName("UseCase")
                        .setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(configBuilder).setDeviceStateCallback(mDeviceStateCallback);
        TestUseCase testUseCase = new TestUseCase(configBuilder.build());
        Map<String, Size> suggestedResolutionMap = new HashMap<>();
        suggestedResolutionMap.put(mCameraId, new Size(640, 480));
        testUseCase.updateSuggestedResolution(suggestedResolutionMap);
        return testUseCase;
    }

    @Test
    public void addOnline_OneUseCase() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera.isUseCaseOnline(useCase1)).isFalse();

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase1)).isTrue();

        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void addOnline_SameUseCases() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera.isUseCaseOnline(useCase1)).isFalse();

        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));
        mWaitCameraCloseAtTearDown = false;
    }


    @Test
    public void addOnline_alreadyOnline() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        unblockHandler();
        waitHandlerIdle();

        //Usecase1 is online now.
        assertThat(mCamera.isUseCaseOnline(useCase1)).isTrue();

        blockHandler();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera.close();
        waitHandlerIdle();

        // Surface is only attached once.
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);


        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));
    }

    @Test
    public void addOnline_twoUseCases() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));
        UseCase useCase2 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase2));

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);
        assertThat(getUseCaseSurface(useCase2).getAttachedCount()).isEqualTo(1);
        assertThat(mCamera.isUseCaseOnline(useCase1)).isFalse();
        assertThat(mCamera.isUseCaseOnline(useCase2)).isFalse();

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase1)).isTrue();
        assertThat(mCamera.isUseCaseOnline(useCase2)).isTrue();

        mCamera.removeOnlineUseCase(Arrays.asList(useCase1, useCase2));
    }

    @Test
    public void addOnline_fromPendingOffline() {
        blockHandler();

        // First make UseCase online
        UseCase useCase = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase));
        unblockHandler();
        waitHandlerIdle();

        blockHandler();
        // Then make it offline but pending for Camera thread to run it.
        mCamera.removeOnlineUseCase(Arrays.asList(useCase));

        // Then add the same UseCase .
        mCamera.addOnlineUseCase(Arrays.asList(useCase));

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase)).isTrue();

        mCamera.close();
        waitHandlerIdle();
        assertThat(getUseCaseSurface(useCase).getAttachedCount()).isEqualTo(1);

        mCamera.removeOnlineUseCase(Arrays.asList(useCase));
    }

    @Test
    public void removeOnline_notOnline() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        waitHandlerIdle();

        // It should not be detached so the attached count should still be 0
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);

        mWaitCameraCloseAtTearDown = false;
    }

    @Test
    public void removeOnline_fromPendingOnline() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1));
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(1);

        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase1)).isFalse();
        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);
    }

    @Test
    public void removeOnline_fromOnlineUseCases() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase1)).isTrue();
        assertThat(mCamera.isUseCaseOnline(useCase2)).isTrue();

        blockHandler();
        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        waitHandlerIdle();

        assertThat(mCamera.isUseCaseOnline(useCase1)).isFalse();
        assertThat(mCamera.isUseCaseOnline(useCase2)).isTrue();

        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera.close();
        waitHandlerIdle();

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);
        assertThat(getUseCaseSurface(useCase2).getAttachedCount()).isEqualTo(1);


        mCamera.removeOnlineUseCase(Arrays.asList(useCase2));
    }

    @Test
    public void removeOnline_twoSameUseCase() {
        blockHandler();

        UseCase useCase1 = createUseCase();
        UseCase useCase2 = createUseCase();
        mCamera.addOnlineUseCase(Arrays.asList(useCase1, useCase2));

        unblockHandler();
        waitHandlerIdle();

        // remove twice
        blockHandler();
        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));
        mCamera.removeOnlineUseCase(Arrays.asList(useCase1));

        unblockHandler();
        // Surface is attached when (1) UseCase added to online (2) Camera session opened
        // So here we need to wait until camera close before we start to verify the attach count
        mCamera.close();
        waitHandlerIdle();

        assertThat(getUseCaseSurface(useCase1).getAttachedCount()).isEqualTo(0);

        mCamera.removeOnlineUseCase(Arrays.asList(useCase2));
    }


    private DeferrableSurface getUseCaseSurface(UseCase useCase) {
        return useCase.getSessionConfig(mCameraId).getSurfaces().get(0);
    }

    private static class TestUseCase extends FakeUseCase {
        HandlerThread mHandlerThread = new HandlerThread("HandlerThread");
        Handler mHandler;
        ImageReader mImageReader;
        FakeUseCaseConfig mConfig;

        TestUseCase(
                FakeUseCaseConfig config) {
            super(config);
            // Ensure we're using the combined configuration (user config + defaults)
            mConfig = (FakeUseCaseConfig) getUseCaseConfig();

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
            if (mImageReader != null) {
                mImageReader.close();
            }
        }

        @Override
        protected Map<String, Size> onSuggestedResolutionUpdated(
                Map<String, Size> suggestedResolutionMap) {
            CameraX.LensFacing lensFacing =
                    ((CameraDeviceConfig) getUseCaseConfig()).getLensFacing();
            String cameraId = getCameraIdForLensFacingUnchecked(lensFacing);
            Size resolution = suggestedResolutionMap.get(cameraId);
            SessionConfig.Builder builder = SessionConfig.Builder.createFrom(mConfig);

            builder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            mImageReader =
                    ImageReader.newInstance(
                            resolution.getWidth(),
                            resolution.getHeight(),
                            ImageFormat.YUV_420_888, /*maxImages*/
                            2);
            builder.addSurface(new ImmediateSurface(mImageReader.getSurface()));

            attachToCamera(cameraId, builder.build());
            return suggestedResolutionMap;
        }
    }
}
