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

package androidx.camera.camera2;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import androidx.camera.core.AppConfiguration;
import androidx.camera.core.BaseCamera;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ImageReaderProxy;
import androidx.camera.core.ImageReaderProxys;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.SessionConfiguration;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfiguration;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ImageReaderProxysAndroidTest {
    private static final String CAMERA_ID = "0";

    private BaseCamera mCamera;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private static ImageReaderProxy.OnImageAvailableListener createSemaphoreReleasingListener(
            Semaphore semaphore) {
        return reader -> {
            ImageProxy image = reader.acquireLatestImage();
            if (image != null) {
                semaphore.release();
                image.close();
            }
        };
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppConfiguration appConfig = Camera2AppConfiguration.create(context);
        CameraFactory cameraFactory = appConfig.getCameraFactory(null);
        CameraX.init(context, appConfig);
        mCamera = cameraFactory.getCamera(CAMERA_ID);
        mHandlerThread = new HandlerThread("Background");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @After
    public void tearDown() {
        mCamera.release();
        mHandlerThread.quitSafely();
    }

    @Test(timeout = 5000)
    public void sharedReadersGetFramesFromCamera() throws InterruptedException {
        List<ImageReaderProxy> readers = new ArrayList<>();
        List<Semaphore> semaphores = new ArrayList<>();
        for (int i = 0; i < 2; ++i) {
            ImageReaderProxy reader =
                    ImageReaderProxys.createSharedReader(
                            CAMERA_ID, 640, 480, ImageFormat.YUV_420_888, 2, mHandler);
            Semaphore semaphore = new Semaphore(/*permits=*/ 0);
            reader.setOnImageAvailableListener(
                    createSemaphoreReleasingListener(semaphore), mHandler);
            readers.add(reader);
            semaphores.add(semaphore);
        }

        FakeUseCaseConfiguration configuration =
                new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
        UseCase useCase = new UseCase(configuration, readers);
        CameraUtil.openCameraWithUseCase(mCamera, useCase);

        // Wait for a few frames to be observed.
        for (Semaphore semaphore : semaphores) {
            semaphore.acquire(/*permits=*/ 5);
        }
    }

    @Test(timeout = 5000)
    public void isolatedReadersGetFramesFromCamera() throws InterruptedException {
        List<ImageReaderProxy> readers = new ArrayList<>();
        List<Semaphore> semaphores = new ArrayList<>();
        for (int i = 0; i < 2; ++i) {
            ImageReaderProxy reader =
                    ImageReaderProxys.createIsolatedReader(
                            640, 480, ImageFormat.YUV_420_888, 2, mHandler);
            Semaphore semaphore = new Semaphore(/*permits=*/ 0);
            reader.setOnImageAvailableListener(
                    createSemaphoreReleasingListener(semaphore), mHandler);
            readers.add(reader);
            semaphores.add(semaphore);
        }

        FakeUseCaseConfiguration configuration =
                new FakeUseCaseConfiguration.Builder().setTargetName("UseCase").build();
        UseCase useCase = new UseCase(configuration, readers);
        CameraUtil.openCameraWithUseCase(mCamera, useCase);

        // Wait for a few frames to be observed.
        for (Semaphore semaphore : semaphores) {
            semaphore.acquire(/*permits=*/ 5);
        }
    }

    private static final class UseCase extends FakeUseCase {
        private final List<ImageReaderProxy> mImageReaders;

        private UseCase(FakeUseCaseConfiguration configuration, List<ImageReaderProxy> readers) {
            super(configuration);
            mImageReaders = readers;
            Map<String, Size> suggestedResolutionMap = new HashMap<>();
            suggestedResolutionMap.put(CAMERA_ID, new Size(640, 480));
            updateSuggestedResolution(suggestedResolutionMap);
        }

        @Override
        protected Map<String, Size> onSuggestedResolutionUpdated(
                Map<String, Size> suggestedResolutionMap) {
            SessionConfiguration.Builder sessionConfigBuilder = new SessionConfiguration.Builder();
            sessionConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            for (ImageReaderProxy reader : mImageReaders) {
                sessionConfigBuilder.addSurface(new ImmediateSurface(reader.getSurface()));
            }
            attachToCamera(CAMERA_ID, sessionConfigBuilder.build());
            return suggestedResolutionMap;
        }
    }
}
