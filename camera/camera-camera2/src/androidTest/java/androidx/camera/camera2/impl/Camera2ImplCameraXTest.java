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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.camera2.impl.util.SemaphoreReleasingCamera2Callbacks.DeviceStateCallback;
import androidx.camera.camera2.impl.util.SemaphoreReleasingCamera2Callbacks.SessionCaptureCallback;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contains tests for {@link androidx.camera.core.CameraX} which require an actual implementation to
 * run.
 */
@FlakyTest
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class Camera2ImplCameraXTest {
    private static final LensFacing DEFAULT_LENS_FACING = LensFacing.BACK;
    private final MutableLiveData<Long> mAnalysisResult = new MutableLiveData<>();
    private final MutableLiveData<Long> mAnalysisResult2 = new MutableLiveData<>();
    private final ImageAnalysis.Analyzer mImageAnalyzer =
            new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(ImageProxy image, int rotationDegrees) {
                    mAnalysisResult.postValue(image.getTimestamp());
                }
            };
    private final ImageAnalysis.Analyzer mImageAnalyzer2 =
            new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(ImageProxy image, int rotationDegrees) {
                    mAnalysisResult2.postValue(image.getTimestamp());
                }
            };
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private CountDownLatch mLatchForDeviceClose;
    private CameraDevice.StateCallback mDeviceStateCallback;
    private FakeLifecycleOwner mLifecycle;

    private static Observer<Long> createCountIncrementingObserver(final AtomicLong counter) {
        return new Observer<Long>() {
            @Override
            public void onChanged(Long value) {
                counter.incrementAndGet();
            }
        };
    }

    @Before
    public void setUp() {
        assumeTrue(CameraUtil.deviceHasCamera());
        Context context = ApplicationProvider.getApplicationContext();
        CameraX.init(context, Camera2AppConfig.create(context));
        mLifecycle = new FakeLifecycleOwner();

        mLatchForDeviceClose = new CountDownLatch(1);
        mDeviceStateCallback = spy(new DeviceStateCallbackImpl());
    }

    @After
    public void tearDown() throws InterruptedException {
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.unbindAll();
            }
        });

        // Wait camera to be closed.
        if (mLatchForDeviceClose != null) {
            mLatchForDeviceClose.await(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void lifecycleResume_opensCameraAndStreamsFrames() {
        Observer<Long> mockObserver = Mockito.mock(Observer.class);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ImageAnalysisConfig.Builder builder =
                        new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
                new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
                ImageAnalysis useCase = new ImageAnalysis(builder.build());

                CameraX.bindToLifecycle(mLifecycle, useCase);
                useCase.setAnalyzer(mImageAnalyzer);
                mAnalysisResult.observe(mLifecycle, mockObserver);

                mLifecycle.startAndResume();
            }
        });
        verify(mockObserver, timeout(5000).times(10)).onChanged(any());
    }

    @Test
    public void removedUseCase_doesNotStreamWhenLifecycleResumes() throws NullPointerException,
            CameraAccessException, CameraInfoUnavailableException {
        // Legacy device would not support two ImageAnalysis use cases combination.
        int hardwareLevelValue;
        CameraCharacteristics cameraCharacteristics =
                CameraUtil.getCameraManager().getCameraCharacteristics(
                        CameraX.getCameraWithLensFacing(DEFAULT_LENS_FACING));
        hardwareLevelValue = cameraCharacteristics.get(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        assumeTrue(
                hardwareLevelValue != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY);

        Observer<Long> mockObserver = Mockito.mock(Observer.class);
        Observer<Long> mockObserver2 = Mockito.mock(Observer.class);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ImageAnalysisConfig.Builder builder =
                        new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
                new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
                ImageAnalysis useCase = new ImageAnalysis(builder.build());

                ImageAnalysisConfig config2 =
                        new ImageAnalysisConfig.Builder()
                                .setLensFacing(DEFAULT_LENS_FACING)
                                .build();
                ImageAnalysis useCase2 = new ImageAnalysis(config2);

                CameraX.bindToLifecycle(mLifecycle, useCase, useCase2);

                useCase.setAnalyzer(mImageAnalyzer);
                useCase2.setAnalyzer(mImageAnalyzer2);
                mAnalysisResult.observe(mLifecycle, mockObserver);
                mAnalysisResult2.observe(mLifecycle, mockObserver2);

                CameraX.unbind(useCase);

                mLifecycle.startAndResume();
            }
        });

        // Let second ImageAnalysis get some images. This shows that the first ImageAnalysis has
        // not observed any images, even though the camera has started to stream.
        verify(mockObserver2, timeout(3000).times(3)).onChanged(any());
        verify(mockObserver, never()).onChanged(any());
    }

    @Test
    public void lifecyclePause_closesCameraAndStopsStreamingFrames() throws InterruptedException {
        final AtomicLong observedCount = new AtomicLong(0);
        final SessionCaptureCallback sessionCaptureCallback = new SessionCaptureCallback();
        final DeviceStateCallback deviceStateCallback = new DeviceStateCallback();
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                ImageAnalysisConfig.Builder configBuilder =
                        new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
                new Camera2Config.Extender(configBuilder)
                        .setDeviceStateCallback(deviceStateCallback)
                        .setSessionCaptureCallback(sessionCaptureCallback);
                ImageAnalysis useCase = new ImageAnalysis(configBuilder.build());
                CameraX.bindToLifecycle(mLifecycle, useCase);
                useCase.setAnalyzer(mImageAnalyzer);
                mAnalysisResult.observe(mLifecycle, createCountIncrementingObserver(observedCount));

                mLifecycle.startAndResume();
            }
        });

        // Wait a little bit for the camera to open and stream frames.
        sessionCaptureCallback.waitForOnCaptureCompleted(5);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mLifecycle.pauseAndStop();
            }
        });

        // Wait a little bit for the camera to close.
        deviceStateCallback.waitForOnClosed(1);

        final Long firstObservedCount = observedCount.get();
        assertThat(firstObservedCount).isGreaterThan(1L);

        // Stay in idle state for a while.
        Thread.sleep(5000);

        // Additional frames should not be observed.
        final Long secondObservedCount = observedCount.get();
        assertThat(secondObservedCount).isEqualTo(firstObservedCount);
    }

    @Test
    public void bind_opensCamera() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config = builder.build();
        ImageAnalysis useCase = new ImageAnalysis(config);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase);
                useCase.setAnalyzer(mImageAnalyzer);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));
    }

    @Test
    public void bind_opensCamera_withOutAnalyzer() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config = builder.build();
        ImageAnalysis useCase = new ImageAnalysis(config);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));
    }

    @Test
    public void bind_opensCamera_noActiveUseCase_sessionIsConfigured() {
        CameraCaptureSession.StateCallback mockSessionStateCallback = Mockito.mock(
                CameraCaptureSession.StateCallback.class);

        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback)
                .setSessionStateCallback(mockSessionStateCallback);

        ImageAnalysisConfig config = builder.build();
        ImageAnalysis useCase = new ImageAnalysis(config);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase);
                mLifecycle.startAndResume();
            }
        });

        // When no analyzer is set, there will be no active surface for repeating request
        // CaptureSession#mSessionConfig will be null. Thus we wait until capture session
        // onConfigured to see if it causes any issue.
        verify(mockSessionStateCallback, timeout(3000)).onConfigured(
                any(CameraCaptureSession.class));
    }

    @Test
    public void bind_unbind_loopWithOutAnalyzer() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        mLifecycle.startAndResume();

        for (int i = 0; i < 2; i++) {
            CameraDevice.StateCallback callback = Mockito.mock(CameraDevice.StateCallback.class);
            new Camera2Config.Extender(builder).setDeviceStateCallback(callback);
            ImageAnalysisConfig config = builder.build();
            ImageAnalysis useCase = new ImageAnalysis(config);

            mInstrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    CameraX.bindToLifecycle(mLifecycle, useCase);
                }
            });

            verify(callback, timeout(5000)).onOpened(any(CameraDevice.class));

            mInstrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    CameraX.unbind(useCase);
                }
            });

            verify(callback, timeout(3000)).onClosed(any(CameraDevice.class));
        }
    }

    @Test
    public void bind_unbind_loopWithAnalyzer() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        mLifecycle.startAndResume();

        for (int i = 0; i < 2; i++) {
            CameraDevice.StateCallback callback = Mockito.mock(CameraDevice.StateCallback.class);
            new Camera2Config.Extender(builder).setDeviceStateCallback(callback);
            ImageAnalysisConfig config = builder.build();
            ImageAnalysis useCase = new ImageAnalysis(config);

            mInstrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    CameraX.bindToLifecycle(mLifecycle, useCase);
                    useCase.setAnalyzer(mImageAnalyzer);
                }
            });

            verify(callback, timeout(5000)).onOpened(any(CameraDevice.class));

            mInstrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    CameraX.unbind(useCase);
                }
            });

            verify(callback, timeout(3000)).onClosed(any(CameraDevice.class));
        }
    }

    @Test
    public void unbindAll_closesAllCameras() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config = builder.build();
        ImageAnalysis useCase = new ImageAnalysis(config);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.unbindAll();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onClosed(any(CameraDevice.class));
    }

    @Test
    public void unbindAllAssociatedUseCase_closesCamera() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config = builder.build();
        ImageAnalysis useCase = new ImageAnalysis(config);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.unbind(useCase);
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onClosed(any(CameraDevice.class));
    }

    @Test
    public void unbindPartialAssociatedUseCase_doesNotCloseCamera() throws InterruptedException {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config0 = builder.build();
        ImageAnalysis useCase0 = new ImageAnalysis(config0);

        ImageCaptureConfig configuration =
                new ImageCaptureConfig.Builder()
                        .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                        .build();
        ImageCapture useCase1 = new ImageCapture(configuration);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase0, useCase1);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.unbind(useCase1);
            }
        });

        Thread.sleep(3000);

        verify(mDeviceStateCallback, never()).onClosed(any(CameraDevice.class));
    }

    @Test
    public void unbindAllAssociatedUseCaseInParts_ClosesCamera() {
        ImageAnalysisConfig.Builder builder =
                new ImageAnalysisConfig.Builder().setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(builder).setDeviceStateCallback(mDeviceStateCallback);
        ImageAnalysisConfig config0 = builder.build();
        ImageAnalysis useCase0 = new ImageAnalysis(config0);

        ImageCaptureConfig configuration =
                new ImageCaptureConfig.Builder()
                        .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                        .build();
        ImageCapture useCase1 = new ImageCapture(configuration);

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, useCase0, useCase1);
                mLifecycle.startAndResume();
            }
        });

        verify(mDeviceStateCallback, timeout(3000)).onOpened(any(CameraDevice.class));

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.unbind(useCase0);
                CameraX.unbind(useCase1);
            }
        });

        verify(mDeviceStateCallback, timeout(3000).times(1)).onClosed(any(CameraDevice.class));
    }

    public class DeviceStateCallbackImpl extends CameraDevice.StateCallback {
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
    }
}
