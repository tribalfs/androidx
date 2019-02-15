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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.camera.camera2.CaptureSession.State;
import androidx.camera.core.CameraCaptureCallback;
import androidx.camera.core.CameraCaptureCallbacks;
import androidx.camera.core.CameraCaptureResult;
import androidx.camera.core.CameraUtil;
import androidx.camera.core.CaptureRequestConfiguration;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.SessionConfiguration;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link CaptureSession}. This requires an environment where a valid {@link
 * android.hardware.camera2.CameraDevice} can be opened since it is used to open a {@link
 * android.hardware.camera2.CaptureRequest}.
 */
@RunWith(AndroidJUnit4.class)
public class CaptureSessionAndroidTest {
    private CaptureSessionTestParameters mTestParameters0;
    private CaptureSessionTestParameters mTestParameters1;

    private CameraDevice mCameraDevice;

    @Before
    public void setup() throws CameraAccessException, InterruptedException {
        mTestParameters0 = new CaptureSessionTestParameters("mTestParameters0");
        mTestParameters1 = new CaptureSessionTestParameters("mTestParameters1");
        mCameraDevice = CameraUtil.getCameraDevice();
    }

    @After
    public void tearDown() {
        mTestParameters0.tearDown();
        mTestParameters1.tearDown();
        CameraUtil.releaseCameraDevice(mCameraDevice);
    }

    @Test
    public void setCaptureSessionSucceed() {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);

        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        assertThat(captureSession.getSessionConfiguration())
                .isEqualTo(mTestParameters0.mSessionConfiguration);
    }

    @Test
    public void setCaptureSessionOnClosedSession_throwsException() {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        SessionConfiguration newSessionConfiguration = mTestParameters0.mSessionConfiguration;

        captureSession.close();

        assertThrows(
                IllegalStateException.class,
                () -> captureSession.setSessionConfiguration(newSessionConfiguration));
    }

    @Test
    public void openCaptureSessionSucceed() throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);

        mTestParameters0.waitForData();

        assertThat(captureSession.getState()).isEqualTo(State.OPENED);

        // StateCallback.onConfigured() should be called to signal the session is configured.
        verify(mTestParameters0.mSessionStateCallback, times(1))
                .onConfigured(any(CameraCaptureSession.class));

        // CameraCaptureCallback.onCaptureCompleted() should be called to signal a capture attempt.
        verify(mTestParameters0.mSessionCameraCaptureCallback, timeout(3000).atLeast(1))
                .onCaptureCompleted(any());
    }

    @Test
    public void closeUnopenedSession() {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        captureSession.close();

        assertThat(captureSession.getState()).isEqualTo(State.RELEASED);
    }

    @Test
    public void releaseUnopenedSession() {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        captureSession.release();

        assertThat(captureSession.getState()).isEqualTo(State.RELEASED);
    }

    @Test
    public void closeOpenedSession() throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);
        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);

        captureSession.close();

        Thread.sleep(3000);
        // Session should not get released until triggered by another session opening
        assertThat(captureSession.getState()).isEqualTo(State.CLOSED);
    }

    @Test
    public void releaseOpenedSession() throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);
        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);
        captureSession.release();

        Thread.sleep(3000);
        assertThat(captureSession.getState()).isEqualTo(State.RELEASED);

        // StateCallback.onClosed() should be called to signal the session is closed.
        verify(mTestParameters0.mSessionStateCallback, times(1))
                .onClosed(any(CameraCaptureSession.class));
    }

    @Test
    public void openSecondSession() throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        // First session is opened
        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);
        captureSession.close();

        // Open second session, which should cause first one to be released
        CaptureSession captureSession1 = new CaptureSession(mTestParameters1.mHandler);
        captureSession1.setSessionConfiguration(mTestParameters1.mSessionConfiguration);
        captureSession1.open(mTestParameters1.mSessionConfiguration, mCameraDevice);

        mTestParameters1.waitForData();

        assertThat(captureSession1.getState()).isEqualTo(State.OPENED);
        assertThat(captureSession.getState()).isEqualTo(State.RELEASED);

        // First session should have StateCallback.onConfigured(), onClosed() calls.
        verify(mTestParameters0.mSessionStateCallback, times(1))
                .onConfigured(any(CameraCaptureSession.class));
        verify(mTestParameters0.mSessionStateCallback, times(1))
                .onClosed(any(CameraCaptureSession.class));

        // Second session should have StateCallback.onConfigured() call.
        verify(mTestParameters1.mSessionStateCallback, times(1))
                .onConfigured(any(CameraCaptureSession.class));

        // Second session should have CameraCaptureCallback.onCaptureCompleted() call.
        verify(mTestParameters1.mSessionCameraCaptureCallback, timeout(3000).atLeast(1))
                .onCaptureCompleted(any());
    }

    @Test
    public void issueSingleCaptureRequest() throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);
        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);

        mTestParameters0.waitForData();

        assertThat(captureSession.getState()).isEqualTo(State.OPENED);

        captureSession.issueSingleCaptureRequest(mTestParameters0.mCaptureRequestConfiguration);

        mTestParameters0.waitForCameraCaptureCallback();

        // CameraCaptureCallback.onCaptureCompleted() should be called to signal a capture attempt.
        verify(mTestParameters0.mCameraCaptureCallback, timeout(3000).times(1))
                .onCaptureCompleted(any());
    }

    @Test
    public void issueSingleCaptureRequestBeforeCaptureSessionOpened()
            throws CameraAccessException, InterruptedException {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);
        captureSession.setSessionConfiguration(mTestParameters0.mSessionConfiguration);

        captureSession.issueSingleCaptureRequest(mTestParameters0.mCaptureRequestConfiguration);
        captureSession.open(mTestParameters0.mSessionConfiguration, mCameraDevice);

        mTestParameters0.waitForCameraCaptureCallback();

        // CameraCaptureCallback.onCaptureCompleted() should be called to signal a capture attempt.
        verify(mTestParameters0.mCameraCaptureCallback, timeout(3000).times(1))
                .onCaptureCompleted(any());
    }

    @Test
    public void issueSingleCaptureRequestOnClosedSession_throwsException() {
        CaptureSession captureSession = new CaptureSession(mTestParameters0.mHandler);

        captureSession.close();

        assertThrows(
                IllegalStateException.class,
                () ->
                        captureSession.issueSingleCaptureRequest(
                                mTestParameters0.mCaptureRequestConfiguration));
    }

    /**
     * Collection of parameters required for setting a {@link CaptureSession} and wait for it to
     * produce data.
     */
    private static class CaptureSessionTestParameters {
        private static final int TIME_TO_WAIT_FOR_DATA_SECONDS = 3;
        /** Thread for all asynchronous calls. */
        private final HandlerThread mHandlerThread;
        /** Handler for all asynchronous calls. */
        private final Handler mHandler;
        /** Latch to wait for first image data to appear. */
        private final CountDownLatch mDataLatch = new CountDownLatch(1);

        /** Latch to wait for camera capture callback to be invoked. */
        private final CountDownLatch mCameraCaptureCallbackLatch = new CountDownLatch(1);

        /** Image reader that unlocks the latch waiting for the first image data to appear. */
        private final OnImageAvailableListener mOnImageAvailableListener =
                reader -> {
                    Image image = reader.acquireNextImage();
                    if (image != null) {
                        image.close();
                        mDataLatch.countDown();
                    }
                };

        private final ImageReader mImageReader;
        private final SessionConfiguration mSessionConfiguration;
        private final CaptureRequestConfiguration mCaptureRequestConfiguration;

        private final CameraCaptureSession.StateCallback mSessionStateCallback =
                Mockito.mock(CameraCaptureSession.StateCallback.class);
        private final CameraCaptureCallback mSessionCameraCaptureCallback =
                Mockito.mock(CameraCaptureCallback.class);
        private final CameraCaptureCallback mCameraCaptureCallback =
                Mockito.mock(CameraCaptureCallback.class);

        /**
         * A composite capture callback that dispatches callbacks to both mock and real callbacks.
         * The mock callback is used to verify the callback result. The real callback is used to
         * unlock the latch waiting.
         */
        private final CameraCaptureCallback mComboCameraCaptureCallback =
                CameraCaptureCallbacks.createComboCallback(
                        mCameraCaptureCallback,
                        new CameraCaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureResult result) {
                                mCameraCaptureCallbackLatch.countDown();
                            }
                        });

        CaptureSessionTestParameters(String name) {
            mHandlerThread = new HandlerThread(name);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());

            mImageReader =
                    ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, /*maxImages*/ 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

            SessionConfiguration.Builder builder = new SessionConfiguration.Builder();
            builder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            builder.addSurface(new ImmediateSurface(mImageReader.getSurface()));
            builder.setSessionStateCallback(mSessionStateCallback);
            builder.setCameraCaptureCallback(mSessionCameraCaptureCallback);

            mSessionConfiguration = builder.build();

            CaptureRequestConfiguration.Builder captureRequestConfigBuilder =
                    new CaptureRequestConfiguration.Builder();
            captureRequestConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestConfigBuilder.addSurface(new ImmediateSurface(mImageReader.getSurface()));
            captureRequestConfigBuilder.setCameraCaptureCallback(mComboCameraCaptureCallback);

            mCaptureRequestConfiguration = captureRequestConfigBuilder.build();
        }

        /**
         * Wait for data to get produced by the session.
         *
         * @throws InterruptedException if data is not produced after a set amount of time
         */
        void waitForData() throws InterruptedException {
            mDataLatch.await(TIME_TO_WAIT_FOR_DATA_SECONDS, TimeUnit.SECONDS);
        }

        void waitForCameraCaptureCallback() throws InterruptedException {
            mCameraCaptureCallbackLatch.await(TIME_TO_WAIT_FOR_DATA_SECONDS, TimeUnit.SECONDS);
        }

        /** Clean up resources. */
        void tearDown() {
            mImageReader.close();
            mHandlerThread.quitSafely();
        }
    }
}
