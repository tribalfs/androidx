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

package androidx.camera.core;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.app.Instrumentation;
import android.content.Context;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeCameraFactory;
import androidx.camera.testing.fakes.FakeCameraInfoInternal;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class CameraXTest {
    private static final LensFacing CAMERA_LENS_FACING = LensFacing.BACK;
    private static final LensFacing CAMERA_LENS_FACING_FRONT = LensFacing.FRONT;
    private static final CameraSelector CAMERA_SELECTOR =
            new CameraSelector.Builder().requireLensFacing(CAMERA_LENS_FACING).build();
    private static final String CAMERA_ID = "0";
    private static final String CAMERA_ID_FRONT = "1";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private Context mContext;
    private String mCameraId;
    private CameraInternal mCameraInternal;
    private FakeLifecycleOwner mLifecycle;
    private AppConfig.Builder mAppConfigBuilder;
    private FakeCameraFactory mFakeCameraFactory;
    private CameraDeviceSurfaceManager mFakeSurfaceManager;
    private UseCaseConfigFactory mUseCaseConfigFactory;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        mFakeSurfaceManager = new FakeCameraDeviceSurfaceManager();
        ExtendableUseCaseConfigFactory defaultConfigFactory = new ExtendableUseCaseConfigFactory();
        defaultConfigFactory.installDefaultProvider(FakeUseCaseConfig.class,
                new ConfigProvider<FakeUseCaseConfig>() {
                    @Override
                    public FakeUseCaseConfig getConfig(LensFacing lensFacing) {
                        return new FakeUseCaseConfig.Builder().build();
                    }
                });
        mUseCaseConfigFactory = defaultConfigFactory;
        mFakeCameraFactory = new FakeCameraFactory();
        mCameraInternal = new FakeCamera(mock(CameraControlInternal.class),
                new FakeCameraInfoInternal(0, CAMERA_LENS_FACING));
        mFakeCameraFactory.insertCamera(CAMERA_LENS_FACING, CAMERA_ID, () -> mCameraInternal);
        mFakeCameraFactory.setDefaultCameraIdForLensFacing(CAMERA_LENS_FACING, CAMERA_ID);
        mAppConfigBuilder =
                new AppConfig.Builder()
                        .setCameraFactory(mFakeCameraFactory)
                        .setDeviceSurfaceManager(mFakeSurfaceManager)
                        .setUseCaseConfigFactory(mUseCaseConfigFactory);

        mLifecycle = new FakeLifecycleOwner();

        mCameraId = mFakeCameraFactory.cameraIdForLensFacing(CAMERA_LENS_FACING);
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        if (CameraX.isInitialized()) {
            mInstrumentation.runOnMainSync(CameraX::unbindAll);
        }

        CameraX.shutdown().get();
    }

    @Test
    public void initDeinit_success() throws ExecutionException, InterruptedException {
        CameraX.initialize(mContext, mAppConfigBuilder.build()).get();
        assertThat(CameraX.isInitialized()).isTrue();

        CameraX.shutdown().get();
        assertThat(CameraX.isInitialized()).isFalse();
    }

    @Test
    public void failInit_shouldInDeinitState() throws InterruptedException {
        // Create an empty config to cause a failed init.
        AppConfig appConfig = new AppConfig.Builder().build();
        Exception exception = null;
        try {
            CameraX.initialize(mContext, appConfig).get();
        } catch (ExecutionException e) {
            exception = e;
        }
        assertThat(exception).isInstanceOf(ExecutionException.class);
        assertThat(CameraX.isInitialized()).isFalse();
    }

    @Test
    public void reinit_success() throws ExecutionException, InterruptedException {
        CameraX.initialize(mContext, mAppConfigBuilder.build()).get();
        assertThat(CameraX.isInitialized()).isTrue();

        CameraX.shutdown().get();
        assertThat(CameraX.isInitialized()).isFalse();

        CameraX.initialize(mContext, mAppConfigBuilder.build()).get();
        assertThat(CameraX.isInitialized()).isTrue();
    }

    @Test
    public void reinit_withPreviousFailedInit() throws ExecutionException, InterruptedException {
        // Create an empty config to cause a failed init.
        AppConfig appConfig = new AppConfig.Builder().build();
        Exception exception = null;
        try {
            CameraX.initialize(mContext, appConfig).get();
        } catch (ExecutionException e) {
            exception = e;
        }
        assertThat(exception).isInstanceOf(ExecutionException.class);

        CameraX.initialize(mContext, mAppConfigBuilder.build()).get();
        assertThat(CameraX.isInitialized()).isTrue();
    }

    @Test
    public void initDeinit_withDirectExecutor() {
        mAppConfigBuilder.setCameraExecutor(CameraXExecutors.directExecutor());

        // Don't call Future.get() because its behavior should be the same as synchronous call.
        CameraX.initialize(mContext, mAppConfigBuilder.build());
        assertThat(CameraX.isInitialized()).isTrue();

        CameraX.shutdown();
        assertThat(CameraX.isInitialized()).isFalse();
    }

    @Test
    public void initDeinit_withMultiThreadExecutor()
            throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        mAppConfigBuilder.setCameraExecutor(executorService);

        CameraX.initialize(mContext, mAppConfigBuilder.build()).get();
        assertThat(CameraX.isInitialized()).isTrue();

        CameraX.shutdown().get();
        assertThat(CameraX.isInitialized()).isFalse();

        executorService.shutdown();
    }

    @Test
    public void init_withDifferentAppConfig() {
        FakeCameraFactory cameraFactory0 = new FakeCameraFactory();
        FakeCameraFactory cameraFactory1 = new FakeCameraFactory();

        mAppConfigBuilder.setCameraFactory(cameraFactory0);
        CameraX.initialize(mContext, mAppConfigBuilder.build());

        assertThat(CameraX.getCameraFactory()).isEqualTo(cameraFactory0);

        CameraX.shutdown();

        mAppConfigBuilder.setCameraFactory(cameraFactory1);
        CameraX.initialize(mContext, mAppConfigBuilder.build());

        assertThat(CameraX.getCameraFactory()).isEqualTo(cameraFactory1);
    }

    @Test
    @UiThreadTest
    public void bind_createsNewUseCaseGroup() {
        initCameraX();
        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, new FakeUseCase());
        // One observer is the use case group. The other observer removes the use case upon the
        // lifecycle's destruction.
        assertThat(mLifecycle.getObserverCount()).isEqualTo(2);
    }

    @Test
    @UiThreadTest
    public void bindMultipleUseCases() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        FakeUseCase fakeUseCase = new FakeUseCase(config0);
        FakeOtherUseCaseConfig config1 =
                new FakeOtherUseCaseConfig.Builder().setTargetName("config1").build();
        FakeOtherUseCase fakeOtherUseCase = new FakeOtherUseCase(config1);

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase, fakeOtherUseCase);

        assertThat(CameraX.isBound(fakeUseCase)).isTrue();
        assertThat(CameraX.isBound(fakeOtherUseCase)).isTrue();
    }

    @Test
    @UiThreadTest
    public void isNotBound_afterUnbind() {
        initCameraX();
        FakeUseCase fakeUseCase = new FakeUseCase();
        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase);

        CameraX.unbind(fakeUseCase);
        assertThat(CameraX.isBound(fakeUseCase)).isFalse();
    }

    @Test
    @UiThreadTest
    public void bind_createsDifferentUseCaseGroups_forDifferentLifecycles() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, new FakeUseCase(config0));

        FakeUseCaseConfig config1 =
                new FakeUseCaseConfig.Builder().setTargetName("config1").build();
        FakeLifecycleOwner anotherLifecycle = new FakeLifecycleOwner();
        CameraX.bindToLifecycle(anotherLifecycle, CAMERA_SELECTOR, new FakeUseCase(config1));

        // One observer is the use case group. The other observer removes the use case upon the
        // lifecycle's destruction.
        assertThat(mLifecycle.getObserverCount()).isEqualTo(2);
        assertThat(anotherLifecycle.getObserverCount()).isEqualTo(2);
    }

    @Test(expected = IllegalArgumentException.class)
    @UiThreadTest
    public void exception_withDestroyedLifecycle() {
        initCameraX();
        FakeUseCase useCase = new FakeUseCase();

        mLifecycle.destroy();

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, useCase);
    }

    @Test
    @UiThreadTest
    public void bind_returnTheSameCameraForSameSelector() {
        // This test scope does not include the Extension, so we only bind a fake use case with a
        // simple lensFacing selector.
        initCameraX();
        Camera camera1 = CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, new FakeUseCase());
        Camera camera2 = CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, new FakeUseCase());

        assertSame(camera1, camera2);
    }

    @Test
    @UiThreadTest
    public void noException_bindUseCases_withDifferentLensFacing() {
        // Initial the front camera for this test.
        CameraInternal cameraInternalFront =
                new FakeCamera(mock(CameraControlInternal.class),
                        new FakeCameraInfoInternal(0, CAMERA_LENS_FACING_FRONT));
        mFakeCameraFactory.insertCamera(CAMERA_LENS_FACING_FRONT, CAMERA_ID_FRONT,
                () -> cameraInternalFront);
        AppConfig.Builder appConfigBuilder =
                new AppConfig.Builder()
                        .setCameraFactory(mFakeCameraFactory)
                        .setDeviceSurfaceManager(mFakeSurfaceManager)
                        .setUseCaseConfigFactory(mUseCaseConfigFactory);

        CameraX.initialize(mContext, appConfigBuilder.build());

        CameraSelector frontSelector =
                new CameraSelector.Builder().requireLensFacing(LensFacing.FRONT).build();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().build();
        FakeUseCase fakeUseCase = new FakeUseCase(config0);
        CameraSelector backSelector =
                new CameraSelector.Builder().requireLensFacing(LensFacing.BACK).build();
        FakeOtherUseCaseConfig config1 =
                new FakeOtherUseCaseConfig.Builder().build();
        FakeOtherUseCase fakeOtherUseCase = new FakeOtherUseCase(config1);

        boolean hasException = false;
        try {
            CameraX.bindToLifecycle(mLifecycle, frontSelector, fakeUseCase);
            CameraX.bindToLifecycle(mLifecycle, backSelector, fakeOtherUseCase);
        } catch (IllegalArgumentException e) {
            hasException = true;
        }
        assertFalse(hasException);
    }

    @UiThreadTest
    public void bindUseCases_successReturnCamera() {
        initCameraX();
        FakeUseCaseConfig config0 = new FakeUseCaseConfig.Builder().build();

        assertThat(CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR,
                new FakeUseCase(config0))).isInstanceOf(Camera.class);
    }

    @Test(expected = IllegalArgumentException.class)
    @UiThreadTest
    public void bindUseCases_withNotExistedLensFacingCamera() {
        initCameraX();
        CameraSelector frontSelector = new CameraSelector.Builder().requireLensFacing(
                LensFacing.FRONT).build();
        FakeUseCaseConfig config0 = new FakeUseCaseConfig.Builder().build();
        FakeUseCase fakeUseCase = new FakeUseCase(config0);

        // The front camera is not defined, we should get the IllegalArgumentException when it
        // tries to get the camera.
        CameraX.bindToLifecycle(mLifecycle, frontSelector, fakeUseCase);
    }

    @Test
    public void requestingDefaultConfiguration_returnsDefaultConfiguration() {
        initCameraX();
        // Requesting a default configuration will throw if CameraX is not initialized.
        FakeUseCaseConfig config = CameraX.getDefaultUseCaseConfig(
                FakeUseCaseConfig.class, CAMERA_LENS_FACING);
        assertThat(config).isNotNull();
        assertThat(config.getTargetClass(null)).isEqualTo(FakeUseCase.class);
    }

    @Test
    @UiThreadTest
    public void attachCameraControl_afterBindToLifecycle() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        AttachCameraFakeCase fakeUseCase = new AttachCameraFakeCase(config0);

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase);

        assertThat(fakeUseCase.getCameraControl(mCameraId)).isEqualTo(
                mCameraInternal.getCameraControlInternal());
    }

    @Test
    @UiThreadTest
    public void onCameraControlReadyIsCalled_afterBindToLifecycle() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        AttachCameraFakeCase fakeUseCase = spy(new AttachCameraFakeCase(config0));

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase);

        Mockito.verify(fakeUseCase).onCameraControlReady(mCameraId);
    }

    @Test
    @UiThreadTest
    public void detachCameraControl_afterUnbind() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        AttachCameraFakeCase fakeUseCase = new AttachCameraFakeCase(config0);
        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase);

        CameraX.unbind(fakeUseCase);

        // after unbind, Camera's CameraControlInternal should be detached from Usecase
        assertThat(fakeUseCase.getCameraControl(mCameraId)).isNotEqualTo(
                mCameraInternal.getCameraControlInternal());
        // UseCase still gets a non-null default CameraControlInternal that does nothing.
        assertThat(fakeUseCase.getCameraControl(mCameraId)).isEqualTo(
                CameraControlInternal.DEFAULT_EMPTY_INSTANCE);
    }

    @Test
    @UiThreadTest
    public void eventCallbackCalled_bindAndUnbind() {
        initCameraX();
        UseCase.EventCallback eventCallback = Mockito.mock(UseCase.EventCallback.class);

        FakeUseCaseConfig.Builder fakeConfigBuilder = new FakeUseCaseConfig.Builder();
        fakeConfigBuilder.setUseCaseEventCallback(eventCallback);
        AttachCameraFakeCase fakeUseCase = new AttachCameraFakeCase(fakeConfigBuilder.build());

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase);
        Mockito.verify(eventCallback).onBind(mCameraId);

        CameraX.unbind(fakeUseCase);
        Mockito.verify(eventCallback).onUnbind();
    }

    @Test
    public void canRetrieveCameraInfo() throws CameraInfoUnavailableException {
        initCameraX();
        String cameraId = CameraX.getCameraWithLensFacing(CAMERA_LENS_FACING);
        CameraInfoInternal cameraInfoInternal = CameraX.getCameraInfo(cameraId);
        assertThat(cameraInfoInternal).isNotNull();
        assertThat(cameraInfoInternal.getLensFacing()).isEqualTo(CAMERA_LENS_FACING);
    }

    @Test
    public void canGetCameraXContext() {
        initCameraX();
        Context context = CameraX.getContext();
        assertThat(context).isNotNull();
    }

    @Test
    @UiThreadTest
    public void canGetActiveUseCases_afterBindToLifecycle() {
        initCameraX();
        FakeUseCaseConfig config0 =
                new FakeUseCaseConfig.Builder().setTargetName("config0").build();
        FakeUseCase fakeUseCase = new FakeUseCase(config0);
        FakeOtherUseCaseConfig config1 =
                new FakeOtherUseCaseConfig.Builder().setTargetName("config1").build();
        FakeOtherUseCase fakeOtherUseCase = new FakeOtherUseCase(config1);

        CameraX.bindToLifecycle(mLifecycle, CAMERA_SELECTOR, fakeUseCase, fakeOtherUseCase);
        mLifecycle.startAndResume();

        Collection<UseCase> useCases = CameraX.getActiveUseCases();

        assertThat(useCases.contains(fakeUseCase)).isTrue();
        assertThat(useCases.contains(fakeOtherUseCase)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void cameraInfo_cannotRetrieveCameraInfo_forFrontCamera() {
        initCameraX();
        // Expect throw the IllegalArgumentException when try to get the cameraInfo from the camera
        // which does not exist.
        CameraX.getCameraInfo(CAMERA_ID_FRONT);
    }

    @Test
    public void checkHasCameraTrueForExistentCamera() throws CameraInfoUnavailableException {
        initCameraX();
        assertThat(CameraX.hasCamera(
                new CameraSelector.Builder().requireLensFacing(LensFacing.BACK).build())).isTrue();
    }

    @Test
    public void checkHasCameraFalseForNonexistentCamera() throws CameraInfoUnavailableException {
        initCameraX();
        assertThat(CameraX.hasCamera(new CameraSelector.Builder().requireLensFacing(
                LensFacing.BACK).requireLensFacing(LensFacing.FRONT).build())).isFalse();
    }

    private void initCameraX() {
        CameraX.initialize(mContext, mAppConfigBuilder.build());
    }

    /** FakeUseCase that will call attachToCamera */
    public static class AttachCameraFakeCase extends FakeUseCase {

        public AttachCameraFakeCase(FakeUseCaseConfig config) {
            super(config);
        }

        @Override
        @NonNull
        protected Map<String, Size> onSuggestedResolutionUpdated(
                @NonNull Map<String, Size> suggestedResolutionMap) {

            SessionConfig.Builder builder = new SessionConfig.Builder();

            String cameraId = getBoundCameraId();
            attachToCamera(cameraId, builder.build());
            return suggestedResolutionMap;
        }
    }
}
