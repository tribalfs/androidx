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

package androidx.camera.extensions;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.camera2.impl.CameraEventCallbacks;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.CaptureProcessor;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.extensions.ExtensionsManager.EffectMode;
import androidx.camera.extensions.impl.CaptureStageImpl;
import androidx.camera.extensions.impl.ImageCaptureExtenderImpl;
import androidx.camera.extensions.util.ExtensionsTestUtil;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class ImageCaptureExtenderTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private FakeLifecycleOwner mLifecycleOwner;

    @Before
    public void setUp() throws InterruptedException, ExecutionException, TimeoutException {
        assumeTrue(CameraUtil.deviceHasCamera());
        mLifecycleOwner = new FakeLifecycleOwner();

        Context context = ApplicationProvider.getApplicationContext();
        CameraX.initialize(context, Camera2AppConfig.create(context));

        assumeTrue(ExtensionsTestUtil.initExtensions());
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        CameraX.shutdown().get();
    }

    @Test
    @MediumTest
    public void extenderLifeCycleTest_noMoreGetCaptureStagesBeforeAndAfterInitDeInit() {
        ImageCaptureExtenderImpl mockImageCaptureExtenderImpl = mock(
                ImageCaptureExtenderImpl.class);
        ArrayList<CaptureStageImpl> captureStages = new ArrayList<>();

        captureStages.add(new FakeCaptureStage());
        when(mockImageCaptureExtenderImpl.getCaptureStages()).thenReturn(captureStages);

        ImageCaptureExtender.ImageCaptureAdapter imageCaptureAdapter =
                new ImageCaptureExtender.ImageCaptureAdapter(mockImageCaptureExtenderImpl, null);
        ImageCaptureConfig.Builder configBuilder =
                new ImageCaptureConfig.Builder().setCaptureBundle(
                        imageCaptureAdapter).setUseCaseEventCallback(
                        imageCaptureAdapter).setCaptureProcessor(
                        mock(CaptureProcessor.class));

        ImageCapture useCase = new ImageCapture(configBuilder.build());
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycleOwner, useCase);
                mLifecycleOwner.startAndResume();
            }
        });

        // To verify the event callbacks in order, and to verification of the getCaptureStages()
        // is also used to wait for the capture session created. The test for the unbind
        // would come after the capture session was created.
        InOrder inOrder = inOrder(mockImageCaptureExtenderImpl);
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000)).onInit(any(String.class), any(
                CameraCharacteristics.class), any(Context.class));
        inOrder.verify(mockImageCaptureExtenderImpl,
                timeout(3000).atLeastOnce()).getCaptureStages();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Unbind the use case to test the onDeInit.
                CameraX.unbind(useCase);
            }
        });

        // To verify the deInit should been called.
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000)).onDeInit();

        // To verify there is no any other calls on the mock.
        verifyNoMoreInteractions(mockImageCaptureExtenderImpl);
    }

    @Test
    @MediumTest
    public void extenderLifeCycleTest_noMoreCameraEventCallbacksBeforeAndAfterInitDeInit() {
        ImageCaptureExtenderImpl mockImageCaptureExtenderImpl = mock(
                ImageCaptureExtenderImpl.class);
        ArrayList<CaptureStageImpl> captureStages = new ArrayList<>();

        captureStages.add(new FakeCaptureStage());
        when(mockImageCaptureExtenderImpl.getCaptureStages()).thenReturn(captureStages);

        ImageCaptureExtender.ImageCaptureAdapter imageCaptureAdapter =
                new ImageCaptureExtender.ImageCaptureAdapter(mockImageCaptureExtenderImpl, null);
        ImageCaptureConfig.Builder configBuilder =
                new ImageCaptureConfig.Builder().setCaptureBundle(
                        imageCaptureAdapter).setUseCaseEventCallback(
                        imageCaptureAdapter).setCaptureProcessor(
                        mock(CaptureProcessor.class));
        new Camera2Config.Extender(configBuilder).setCameraEventCallback(
                new CameraEventCallbacks(imageCaptureAdapter));

        ImageCapture useCase = new ImageCapture(configBuilder.build());

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycleOwner, useCase);
                mLifecycleOwner.startAndResume();
            }
        });

        // To verify the event callbacks in order, and to verification of the onEnableSession()
        // is also used to wait for the capture session created. The test for the unbind
        // would come after the capture session was created.
        InOrder inOrder = inOrder(mockImageCaptureExtenderImpl);
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000)).onInit(any(String.class),
                any(CameraCharacteristics.class), any(Context.class));
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000).atLeastOnce()).onPresetSession();
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000).atLeastOnce()).onEnableSession();

        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                // Unbind the use case to test the onDisableSession and onDeInit.
                CameraX.unbind(useCase);
            }
        });

        // To verify the onDisableSession and onDeInit.
        inOrder.verify(mockImageCaptureExtenderImpl,
                timeout(3000).atLeastOnce()).onDisableSession();
        inOrder.verify(mockImageCaptureExtenderImpl, timeout(3000)).onDeInit();

        // This test item only focus on onPreset, onEnable and onDisable callback testing,
        // ignore all the getCaptureStages callbacks.
        verify(mockImageCaptureExtenderImpl, atLeastOnce()).getCaptureStages();

        // To verify there is no any other calls on the mock.
        verifyNoMoreInteractions(mockImageCaptureExtenderImpl);
    }

    @Test
    @MediumTest
    public void canSetSupportedResolutionsToConfigTest() throws CameraInfoUnavailableException {
        assumeTrue(CameraUtil.deviceHasCamera());
        // getSupportedResolutions supported since version 1.1
        assumeTrue(ExtensionVersion.getRuntimeVersion().compareTo(Version.VERSION_1_1) >= 0);

        LensFacing lensFacing = CameraX.getDefaultLensFacing();
        ImageCaptureConfig.Builder configBuilder =
                new ImageCaptureConfig.Builder().setLensFacing(lensFacing);

        ImageCaptureExtenderImpl mockImageCaptureExtenderImpl = mock(
                ImageCaptureExtenderImpl.class);
        when(mockImageCaptureExtenderImpl.isExtensionAvailable(any(), any())).thenReturn(true);
        List<Pair<Integer, Size[]>> targetFormatResolutionsPairList =
                generateImageCaptureSupportedResolutions(lensFacing);
        when(mockImageCaptureExtenderImpl.getSupportedResolutions()).thenReturn(
                targetFormatResolutionsPairList);

        ImageCaptureExtender fakeExtender = new FakeImageCaptureExtender(configBuilder,
                mockImageCaptureExtenderImpl);

        // Checks the config does not include supported resolutions before applying effect mode.
        assertThat(configBuilder.build().getSupportedResolutions(null)).isNull();

        // Checks the config includes supported resolutions after applying effect mode.
        fakeExtender.enableExtension();
        List<Pair<Integer, Size[]>> resultFormatResolutionsPairList =
                configBuilder.build().getSupportedResolutions(null);
        assertThat(resultFormatResolutionsPairList).isNotNull();

        // Checks the result and target pair lists are the same
        for (Pair<Integer, Size[]> resultPair : resultFormatResolutionsPairList) {
            Size[] targetSizes = null;
            for (Pair<Integer, Size[]> targetPair : targetFormatResolutionsPairList) {
                if (targetPair.first.equals(resultPair.first)) {
                    targetSizes = targetPair.second;
                    break;
                }
            }

            assertThat(
                    Arrays.asList(resultPair.second).equals(Arrays.asList(targetSizes))).isTrue();
        }
    }

    private List<Pair<Integer, Size[]>> generateImageCaptureSupportedResolutions(
            @NonNull LensFacing lensFacing)
            throws CameraInfoUnavailableException {
        List<Pair<Integer, Size[]>> formatResolutionsPairList = new ArrayList<>();
        String cameraId =
                androidx.camera.extensions.CameraUtil.getCameraIdSetWithLensFacing(
                        lensFacing).iterator().next();

        StreamConfigurationMap map =
                androidx.camera.extensions.CameraUtil.getCameraCharacteristics(cameraId).get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map != null) {
            // Retrieves originally supported resolutions from CameraCharacteristics for JPEG and
            // YUV_420_888 formats to return.
            Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);

            if (outputSizes != null) {
                formatResolutionsPairList.add(Pair.create(ImageFormat.JPEG, outputSizes));
            }

            outputSizes = map.getOutputSizes(ImageFormat.YUV_420_888);

            if (outputSizes != null) {
                formatResolutionsPairList.add(Pair.create(ImageFormat.YUV_420_888, outputSizes));
            }
        }

        return formatResolutionsPairList;
    }

    final class FakeImageCaptureExtender extends ImageCaptureExtender {
        FakeImageCaptureExtender(ImageCaptureConfig.Builder builder,
                ImageCaptureExtenderImpl impl) {
            init(builder, impl, EffectMode.NORMAL);
        }
    }

    final class FakeCaptureStage implements CaptureStageImpl {

        @Override
        public int getId() {
            return 0;
        }

        @Override
        public List<Pair<CaptureRequest.Key, Object>> getParameters() {
            List<Pair<CaptureRequest.Key, Object>> parameters = new ArrayList<>();
            return parameters;
        }
    }
}
