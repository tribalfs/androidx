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

import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.camera.core.AppConfig;
import androidx.camera.core.CameraDeviceSurfaceManager;
import androidx.camera.core.CameraX;
import androidx.camera.core.ConfigProvider;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.ExtendableUseCaseConfigFactory;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.testing.fakes.FakeCamera;
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager;
import androidx.camera.testing.fakes.FakeCameraFactory;
import androidx.camera.testing.fakes.FakeCameraInfo;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
public class DisplayOrientedMeteringPointFactoryTest {
    private static final float WIDTH = 480;
    private static final float HEIGHT = 640;
    private Context mMockContext;
    private Display mMockDisplay;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();

        // Init CameraX to inject our FakeCamera with FakeCameraInfo.
        FakeCameraFactory fakeCameraFactory = new FakeCameraFactory();
        fakeCameraFactory.insertBackCamera(
                new FakeCamera(new FakeCameraInfo(90, CameraX.LensFacing.BACK), null));
        fakeCameraFactory.insertFrontCamera(
                new FakeCamera(new FakeCameraInfo(270, CameraX.LensFacing.FRONT), null));

        CameraDeviceSurfaceManager surfaceManager = new FakeCameraDeviceSurfaceManager();
        ExtendableUseCaseConfigFactory defaultConfigFactory = new ExtendableUseCaseConfigFactory();
        defaultConfigFactory.installDefaultProvider(FakeUseCaseConfig.class,
                new ConfigProvider<FakeUseCaseConfig>() {
                    @Override
                    public FakeUseCaseConfig getConfig(CameraX.LensFacing lensFacing) {
                        return new FakeUseCaseConfig.Builder().build();
                    }
                });

        AppConfig appConfig =
                new AppConfig.Builder()
                        .setCameraFactory(fakeCameraFactory)
                        .setDeviceSurfaceManager(surfaceManager)
                        .setUseCaseConfigFactory(defaultConfigFactory)
                        .build();
        CameraX.init(context, appConfig);

        mMockDisplay = Mockito.mock(Display.class);
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_0);
        WindowManager mockWindowManager = Mockito.mock(WindowManager.class);
        when(mockWindowManager.getDefaultDisplay()).thenReturn(mMockDisplay);
        mMockContext = Mockito.mock(Context.class);
        when(mMockContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(mockWindowManager);
    }

    @After
    public void tearDown() {
        CameraX.deinit();
    }

    @Test
    public void defaultWeightAndAreaSize() {
        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        MeteringPoint point = factory.createPoint(0, 0);
        assertThat(point.getSize()).isEqualTo(MeteringPointFactory.DEFAULT_AREASIZE);
        assertThat(point.getWeight()).isEqualTo(MeteringPointFactory.DEFAULT_WEIGHT);
        assertThat(point.getFOVAspectRatio()).isNull();
    }

    @Test
    public void createPointWithValidWeightAndAreaSize() {
        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        final float areaSize = 0.2f;
        final float weight = 0.5f;
        MeteringPoint point = factory.createPoint(0, 0, areaSize, weight);
        assertThat(point.getSize()).isEqualTo(areaSize);
        assertThat(point.getWeight()).isEqualTo(weight);
        assertThat(point.getFOVAspectRatio()).isNull();
    }

    @Test
    public void display0_back() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_0);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(0f);
    }

    @Test
    public void display0_front() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_0);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.FRONT, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(0f);
    }

    @Test
    public void display90_back() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_90);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(1f);
    }

    @Test
    public void display90_front() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_90);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.FRONT, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(1f);
    }

    @Test
    public void display180_back() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_180);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(1f);
    }

    @Test
    public void display180_front() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_180);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.FRONT, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(1f);
    }

    @Test
    public void display270_back() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_270);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.BACK, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(0f);
    }

    @Test
    public void display270_front() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_270);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockContext, CameraX.LensFacing.FRONT, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(0f);
    }

    @Test
    public void display0_back_useCustomDisplay() {
        when(mMockDisplay.getRotation()).thenReturn(Surface.ROTATION_270);

        DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(
                mMockDisplay, CameraX.LensFacing.FRONT, WIDTH, HEIGHT);

        MeteringPoint meteringPoint = factory.createPoint(0f, 0f);
        assertThat(meteringPoint.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint2 = factory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getNormalizedCropRegionX()).isEqualTo(0f);
        assertThat(meteringPoint2.getNormalizedCropRegionY()).isEqualTo(0f);

        MeteringPoint meteringPoint3 = factory.createPoint(WIDTH, 0f);

        assertThat(meteringPoint3.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint3.getNormalizedCropRegionY()).isEqualTo(1f);

        MeteringPoint meteringPoint4 = factory.createPoint(WIDTH, HEIGHT);

        assertThat(meteringPoint4.getNormalizedCropRegionX()).isEqualTo(1f);
        assertThat(meteringPoint4.getNormalizedCropRegionY()).isEqualTo(0f);
    }

}
