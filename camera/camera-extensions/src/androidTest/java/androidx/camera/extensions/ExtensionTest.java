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

import static androidx.camera.testing.SurfaceTextureProvider.createSurfaceTextureProvider;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.camera2.impl.Camera2ImplConfig;
import androidx.camera.camera2.impl.CameraEventCallback;
import androidx.camera.camera2.impl.CameraEventCallbacks;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.extensions.ExtensionsManager.EffectMode;
import androidx.camera.extensions.util.ExtensionsTestUtil;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.SurfaceTextureProvider;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@LargeTest
@RunWith(Parameterized.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
public class ExtensionTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.CAMERA);

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        return ExtensionsTestUtil.getAllEffectLensFacingCombinations();
    }

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private EffectMode mEffectMode;
    @CameraSelector.LensFacing
    private int mLensFacing;
    private FakeLifecycleOwner mLifecycleOwner;

    public ExtensionTest(EffectMode effectMode, @CameraSelector.LensFacing int lensFacing) {
        mEffectMode = effectMode;
        mLensFacing = lensFacing;
    }

    @Before
    public void setUp() throws InterruptedException, TimeoutException, ExecutionException {
        assumeTrue(CameraUtil.deviceHasCamera());

        Context context = ApplicationProvider.getApplicationContext();
        CameraXConfig cameraXConfig = Camera2Config.defaultConfig(context);
        CameraX.initialize(context, cameraXConfig);

        assumeTrue(CameraUtil.hasCameraWithLensFacing(mLensFacing));
        assumeTrue(ExtensionsTestUtil.initExtensions());
        assumeTrue(ExtensionsManager.isExtensionAvailable(mEffectMode, mLensFacing));

        mLifecycleOwner = new FakeLifecycleOwner();
        mLifecycleOwner.startAndResume();
    }

    @After
    public void cleanUp() throws InterruptedException, ExecutionException {
        if (CameraX.isInitialized()) {
            mInstrumentation.runOnMainSync(CameraX::unbindAll);
        }
        CameraX.shutdown().get();
    }

    @Test
    public void testCanBindToLifeCycleAndTakePicture() {
        ImageCapture.OnImageCapturedCallback mockOnImageCapturedCallback = mock(
                ImageCapture.OnImageCapturedCallback.class);

        // To test bind/unbind and take picture.
        ImageCapture imageCapture = ExtensionsTestUtil.createImageCaptureWithEffect(mEffectMode,
                mLensFacing);
        Preview preview = ExtensionsTestUtil.createPreviewWithEffect(mEffectMode, mLensFacing);

        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(mLensFacing).build();
        mInstrumentation.runOnMainSync(
                () -> {
                    // To set the update listener and Preview will change to active state.
                    preview.setPreviewSurfaceProvider(createSurfaceTextureProvider(
                            new SurfaceTextureProvider.SurfaceTextureCallback() {
                                @Override
                                public void onSurfaceTextureReady(
                                        @NonNull SurfaceTexture surfaceTexture,
                                        @NonNull Size resolution) {
                                    // No-op.
                                }

                                @Override
                                public void onSafeToRelease(
                                        @NonNull SurfaceTexture surfaceTexture) {
                                    // No-op.
                                }
                            }));

                    CameraX.bindToLifecycle(mLifecycleOwner, cameraSelector, preview, imageCapture);

                    imageCapture.takePicture(CameraXExecutors.mainThreadExecutor(),
                            mockOnImageCapturedCallback);
                });

        // Verify the image captured.
        ArgumentCaptor<ImageProxy> imageProxy = ArgumentCaptor.forClass(ImageProxy.class);
        verify(mockOnImageCapturedCallback, timeout(3000)).onCaptureSuccess(
                imageProxy.capture());
        assertNotNull(imageProxy.getValue());
        imageProxy.getValue().close(); // Close the image after verification.

        // Verify the take picture should not have any error happen.
        verify(mockOnImageCapturedCallback, never()).onError(any(Integer.class), anyString(),
                any(Throwable.class));
    }

    @Test
    public void testEventCallbackInConfig() {
        // Verify Preview config should have related callback.
        PreviewConfig previewConfig = ExtensionsTestUtil.createPreviewConfigWithEffect(mEffectMode,
                mLensFacing);
        assertNotNull(previewConfig.getUseCaseEventCallback());
        CameraEventCallbacks callback1 = new Camera2ImplConfig(
                previewConfig).getCameraEventCallback(
                null);
        assertNotNull(callback1);
        assertEquals(callback1.getAllItems().size(), 1);
        assertThat(callback1.getAllItems().get(0)).isInstanceOf(CameraEventCallback.class);

        // Verify ImageCapture config should have related callback.
        ImageCaptureConfig imageCaptureConfig =
                ExtensionsTestUtil.createImageCaptureConfigWithEffect(mEffectMode, mLensFacing);
        assertNotNull(imageCaptureConfig.getUseCaseEventCallback());
        assertNotNull(imageCaptureConfig.getCaptureBundle());
        CameraEventCallbacks callback2 = new Camera2ImplConfig(
                imageCaptureConfig).getCameraEventCallback(null);
        assertNotNull(callback2);
        assertEquals(callback2.getAllItems().size(), 1);
        assertThat(callback2.getAllItems().get(0)).isInstanceOf(CameraEventCallback.class);
    }
}
