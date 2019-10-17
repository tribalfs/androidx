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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.AppConfig;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraControlInternal.ControlUpdateCallback;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.HandlerUtil;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.core.os.HandlerCompat;
import androidx.lifecycle.Observer;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ZoomControlDeviceTest {
    private static final int TOLERANCE = 5;
    private ZoomControl mZoomControl;
    private Camera2CameraControl mCamera2CameraControl;
    private HandlerThread mHandlerThread;
    private ControlUpdateCallback mControlUpdateCallback;
    private CameraCharacteristics mCameraCharacteristics;
    private Handler mHandler;

    @Before
    public void setUp()
            throws CameraInfoUnavailableException, CameraAccessException, InterruptedException {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LensFacing.BACK));

        // Init CameraX
        Context context = ApplicationProvider.getApplicationContext();
        AppConfig config = Camera2AppConfig.create(context);
        CameraX.initialize(context, config);

        String cameraId = CameraX.getCameraWithLensFacing(LensFacing.BACK);
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

        assumeTrue(getMaxDigitalZoom() >= 2.0);

        mControlUpdateCallback = mock(ControlUpdateCallback.class);
        mHandlerThread = new HandlerThread("ControlThread");
        mHandlerThread.start();
        mHandler = HandlerCompat.createAsync(mHandlerThread.getLooper());

        ScheduledExecutorService executorService = CameraXExecutors.newHandlerExecutor(mHandler);
        mCamera2CameraControl = new Camera2CameraControl(mCameraCharacteristics,
                mControlUpdateCallback, executorService, executorService);

        mZoomControl = new ZoomControl(mCamera2CameraControl, mCameraCharacteristics);
        mZoomControl.setActive(true);

        // Await Camera2CameraControl updateSessionConfig to complete.
        HandlerUtil.waitForLooperToIdle(mHandler);
        Mockito.reset(mControlUpdateCallback);
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        CameraX.shutdown().get();
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    @Test
    @UiThreadTest
    public void setZoomRatio_getValueIsCorrect_InUIThread() {
        final float newZoomRatio = 2.0f;
        assumeTrue(newZoomRatio <= mZoomControl.getMaxZoomRatio().getValue());

        // We can only ensure new value is reflected immediately on getZoomRatio on UI thread
        // because of the nature of LiveData.
        mZoomControl.setZoomRatio(newZoomRatio);
        assertThat(mZoomControl.getZoomRatio().getValue()).isEqualTo(newZoomRatio);
    }

    @Test
    @UiThreadTest
    public void setZoomRatio_largerThanMax_zoomUnmodified() {
        mZoomControl.setZoomRatio(2.0f);
        float maxZoomRatio = mZoomControl.getMaxZoomRatio().getValue();
        mZoomControl.setZoomRatio(maxZoomRatio + 1.0f);
        assertThat(mZoomControl.getZoomRatio().getValue()).isEqualTo(2.0f);
    }

    @Test
    public void setZoomRatio_largerThanMax_OutOfRangeException() {
        float maxZoomRatio = mZoomControl.getMaxZoomRatio().getValue();
        ListenableFuture<Void> result = mZoomControl.setZoomRatio(maxZoomRatio + 1.0f);

        assertThrowOutOfRangeExceptionOnListenableFuture(result);
    }

    private void assertThrowOutOfRangeExceptionOnListenableFuture(ListenableFuture<Void> result) {
        try {
            result.get(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException e) {
        } catch (ExecutionException ee) {
            assertThat(ee.getCause()).isInstanceOf(CameraControl.ArgumentOutOfRangeException.class);
            return;
        }

        fail();
    }

    @Test
    @UiThreadTest
    public void setZoomRatio_smallerThanMin_zoomUnmodified() {
        mZoomControl.setZoomRatio(2.0f);
        float minZoomRatio = mZoomControl.getMinZoomRatio().getValue();
        mZoomControl.setZoomRatio(minZoomRatio - 1.0f);
        assertThat(mZoomControl.getZoomRatio().getValue()).isEqualTo(2.0f);
    }

    @Test
    public void setZoomRatio_smallerThanMin_OutOfRangeException() {
        float minZoomRatio = mZoomControl.getMinZoomRatio().getValue();
        ListenableFuture<Void> result = mZoomControl.setZoomRatio(minZoomRatio - 1.0f);
        assertThrowOutOfRangeExceptionOnListenableFuture(result);
    }

    private Rect getSensorRect() {
        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        // Some device like pixel 2 will have (0, 8) as the left-top corner.
        return new Rect(0, 0, rect.width(), rect.height());
    }

    @Test
    public void setZoomRatioBy1_0_CropRegionIsRemoved() throws InterruptedException {
        mZoomControl.setZoomRatio(1.0f);
        HandlerUtil.waitForLooperToIdle(mHandler);

        Rect sessionCropRegion = getSessionCropRegion(mControlUpdateCallback);
        assertThat(sessionCropRegion).isEqualTo(null);
    }

    @Test
    public void setZoomRatioBy2_0_cropRegionIsSetCorrectly() throws InterruptedException {
        mZoomControl.setZoomRatio(2.0f);
        HandlerUtil.waitForLooperToIdle(mHandler);

        Rect sessionCropRegion = getSessionCropRegion(mControlUpdateCallback);

        Rect sensorRect = getSensorRect();
        int cropX = (sensorRect.width() / 4);
        int cropY = (sensorRect.height() / 4);
        Rect cropRect = new Rect(cropX, cropY, cropX + sensorRect.width() / 2,
                cropY + sensorRect.height() / 2);
        assertThat(sessionCropRegion).isEqualTo(cropRect);
    }

    @NonNull
    private Rect getSessionCropRegion(ControlUpdateCallback controlUpdateCallback)
            throws InterruptedException {
        ArgumentCaptor<SessionConfig> sessionConfigArgumentCaptor =
                ArgumentCaptor.forClass(SessionConfig.class);

        verify(controlUpdateCallback, times(1)).onCameraControlUpdateSessionConfig(
                sessionConfigArgumentCaptor.capture());
        SessionConfig sessionConfig = sessionConfigArgumentCaptor.getValue();
        Camera2Config camera2Config = new Camera2Config(sessionConfig.getImplementationOptions());

        reset(controlUpdateCallback);
        return camera2Config.getCaptureRequestOption(CaptureRequest.SCALER_CROP_REGION, null);
    }

    @UiThreadTest
    @Test
    public void setZoomPercentageBy0_isSameAsMinRatio() {
        mZoomControl.setZoomPercentage(0);
        float ratioAtPercentage0 = mZoomControl.getZoomRatio().getValue();

        mZoomControl.setZoomRatio(mZoomControl.getMinZoomRatio().getValue());
        float ratioAtMinZoomRatio = mZoomControl.getZoomRatio().getValue();

        assertThat(ratioAtPercentage0).isEqualTo(ratioAtMinZoomRatio);
    }

    @UiThreadTest
    @Test
    public void setZoomPercentageBy1_isSameAsMaxRatio() {
        mZoomControl.setZoomPercentage(1);
        float ratioAtPercentage1 = mZoomControl.getZoomRatio().getValue();

        mZoomControl.setZoomRatio(mZoomControl.getMaxZoomRatio().getValue());
        float ratioAtMaxZoomRatio = mZoomControl.getZoomRatio().getValue();

        assertThat(ratioAtPercentage1).isEqualTo(ratioAtMaxZoomRatio);
    }

    @UiThreadTest
    @Test
    public void setZoomPercentageBy0_5_isHalfCropWidth() throws InterruptedException {
        mZoomControl.setZoomPercentage(1f);
        HandlerUtil.waitForLooperToIdle(mHandler);
        Rect cropRegionMaxZoom = getSessionCropRegion(mControlUpdateCallback);

        Rect cropRegionMinZoom = getSensorRect();

        mZoomControl.setZoomPercentage(0.5f);
        HandlerUtil.waitForLooperToIdle(mHandler);
        Rect cropRegionHalfZoom = getSessionCropRegion(mControlUpdateCallback);

        Assert.assertEquals(cropRegionHalfZoom.width(),
                (cropRegionMinZoom.width() + cropRegionMaxZoom.width()) / 2.0f, TOLERANCE);
    }

    @UiThreadTest
    @Test
    public void setZoomPercentage_cropWidthChangedLinearly() throws InterruptedException {
        // crop region in percentage == 0 is null, need to use sensor rect instead.
        Rect prevCropRegion = getSensorRect();

        float prevWidthDelta = 0;
        for (float percentage = 0.1f; percentage < 1.0f; percentage += 0.1f) {

            mZoomControl.setZoomPercentage(percentage);
            HandlerUtil.waitForLooperToIdle(mHandler);
            Rect cropRegion = getSessionCropRegion(mControlUpdateCallback);

            if (prevWidthDelta == 0) {
                prevWidthDelta = prevCropRegion.width() - cropRegion.width();
            } else {
                float widthDelta = prevCropRegion.width() - cropRegion.width();
                Assert.assertEquals(prevWidthDelta, widthDelta, TOLERANCE);
            }

            prevCropRegion = cropRegion;
        }
    }

    @UiThreadTest
    @Test
    public void setZoomPercentage_largerThan1_zoomUnmodified() {
        mZoomControl.setZoomPercentage(0.5f);
        mZoomControl.setZoomPercentage(1.1f);
        assertThat(mZoomControl.getZoomPercentage().getValue()).isEqualTo(0.5f);
    }

    @Test
    public void setZoomPercentage_largerThan1_outOfRangeExeception() {
        ListenableFuture<Void> result = mZoomControl.setZoomPercentage(1.1f);
        assertThrowOutOfRangeExceptionOnListenableFuture(result);
    }

    @UiThreadTest
    @Test
    public void setZoomPercentage_smallerThan0_zoomUnmodified() {
        mZoomControl.setZoomPercentage(0.5f);
        mZoomControl.setZoomPercentage(-0.1f);
        assertThat(mZoomControl.getZoomPercentage().getValue()).isEqualTo(0.5f);
    }

    @Test
    public void setZoomPercentage_smallerThan0_outOfRangeExeception() {
        ListenableFuture<Void> result = mZoomControl.setZoomPercentage(-0.1f);
        assertThrowOutOfRangeExceptionOnListenableFuture(result);
    }

    @UiThreadTest
    @Test
    public void getterLiveData_defaultValueIsNonNull() {
        assertThat(mZoomControl.getZoomRatio().getValue()).isNotNull();
        assertThat(mZoomControl.getZoomPercentage().getValue()).isNotNull();
        assertThat(mZoomControl.getMaxZoomRatio().getValue()).isNotNull();
        assertThat(mZoomControl.getMinZoomRatio().getValue()).isNotNull();
    }

    @UiThreadTest
    @Test
    public void getZoomRatioLiveData_observerIsCalledWhenZoomRatioIsSet()
            throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        FakeLifecycleOwner lifecycleOwner = new FakeLifecycleOwner();
        lifecycleOwner.startAndResume();

        mZoomControl.getZoomRatio().observe(lifecycleOwner, new Observer<Float>() {
            @Override
            public void onChanged(Float value) {
                if (value == 1.2f) {
                    latch1.countDown();
                } else if (value == 1.5f) {
                    latch2.countDown();
                } else if (value == 2.0f) {
                    latch3.countDown();
                }
            }
        });

        mZoomControl.setZoomRatio(1.2f);
        mZoomControl.setZoomRatio(1.5f);
        mZoomControl.setZoomRatio(2.0f);

        assertTrue(latch1.await(500, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(500, TimeUnit.MILLISECONDS));
        assertTrue(latch3.await(500, TimeUnit.MILLISECONDS));
    }

    @UiThreadTest
    @Test
    public void getZoomRatioLiveData_observerIsCalledWhenZoomPercentageIsSet()
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        FakeLifecycleOwner lifecycleOwner = new FakeLifecycleOwner();
        lifecycleOwner.startAndResume();

        mZoomControl.getZoomRatio().observe(lifecycleOwner, new Observer<Float>() {
            @Override
            public void onChanged(Float value) {
                if (value != 1.0f) {
                    latch.countDown();
                }
            }
        });

        mZoomControl.setZoomPercentage(0.1f);
        mZoomControl.setZoomPercentage(0.2f);
        mZoomControl.setZoomPercentage(0.3f);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    }

    @UiThreadTest
    @Test
    public void getZoomPercentageLiveData_observerIsCalledWhenZoomPercentageIsSet()
            throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        FakeLifecycleOwner lifecycleOwner = new FakeLifecycleOwner();
        lifecycleOwner.startAndResume();

        mZoomControl.getZoomPercentage().observe(lifecycleOwner, new Observer<Float>() {
            @Override
            public void onChanged(Float value) {
                if (value == 0.1f) {
                    latch1.countDown();
                } else if (value == 0.2f) {
                    latch2.countDown();
                } else if (value == 0.3f) {
                    latch3.countDown();
                }
            }
        });

        mZoomControl.setZoomPercentage(0.1f);
        mZoomControl.setZoomPercentage(0.2f);
        mZoomControl.setZoomPercentage(0.3f);

        assertTrue(latch1.await(500, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(500, TimeUnit.MILLISECONDS));
        assertTrue(latch3.await(500, TimeUnit.MILLISECONDS));
    }

    @UiThreadTest
    @Test
    public void getZoomPercentageLiveData_observerIsCalledWhenZoomRatioIsSet()
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        FakeLifecycleOwner lifecycleOwner = new FakeLifecycleOwner();
        lifecycleOwner.startAndResume();

        mZoomControl.getZoomPercentage().observe(lifecycleOwner, new Observer<Float>() {
            @Override
            public void onChanged(Float value) {
                if (value != 0f) {
                    latch.countDown();
                }
            }
        });

        mZoomControl.setZoomRatio(1.2f);
        mZoomControl.setZoomRatio(1.5f);
        mZoomControl.setZoomRatio(2.0f);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    }

    @UiThreadTest
    @Test
    public void getZoomRatioDefaultValue() {
        assertThat(mZoomControl.getZoomRatio().getValue()).isEqualTo(
                ZoomControl.DEFAULT_ZOOM_RATIO);
    }

    @UiThreadTest
    @Test
    public void getZoomPercentageDefaultValue() {
        assertThat(mZoomControl.getZoomPercentage().getValue()).isEqualTo(0);
    }

    @UiThreadTest
    @Test
    public void getMaxZoomRatio_isMaxDigitalZoom() {
        float maxZoom = mZoomControl.getMaxZoomRatio().getValue();
        assertThat(maxZoom).isEqualTo(getMaxDigitalZoom());
    }

    @UiThreadTest
    @Test
    public void getMinZoomRatio_isOne() {
        float maxZoom = mZoomControl.getMinZoomRatio().getValue();
        assertThat(maxZoom).isEqualTo(1f);
    }

    private float getMaxDigitalZoom() {
        return mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Test
    public void getMaxZoomRatio_isEqualToMaxDigitalZoom() {
        float maxZoom = mZoomControl.getMaxZoomRatio().getValue();

        assertThat(maxZoom).isEqualTo(getMaxDigitalZoom());
    }

    @UiThreadTest
    @Test
    public void valueIsResetAfterInactive() {
        mZoomControl.setActive(true);
        mZoomControl.setZoomPercentage(0.2f); // this will change ratio and percentage.

        mZoomControl.setActive(false);

        assertThat(mZoomControl.getZoomRatio().getValue()).isEqualTo(
                mZoomControl.DEFAULT_ZOOM_RATIO);
        assertThat(mZoomControl.getZoomPercentage().getValue()).isEqualTo(0);
    }
}
