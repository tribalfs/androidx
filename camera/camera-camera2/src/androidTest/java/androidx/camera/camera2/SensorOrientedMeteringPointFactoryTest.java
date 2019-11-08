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

package androidx.camera.camera2;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.util.Rational;

import androidx.camera.core.AppConfig;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.LensFacing;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.SensorOrientedMeteringPointFactory;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeLifecycleOwner;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public final class SensorOrientedMeteringPointFactoryTest {
    private static final float WIDTH = 480;
    private static final float HEIGHT = 640;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private LifecycleOwner mLifecycle;
    SensorOrientedMeteringPointFactory mPointFactory;
    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppConfig config = Camera2AppConfig.create(context);

        CameraX.initialize(context, config);
        mLifecycle = new FakeLifecycleOwner();
        mPointFactory = new SensorOrientedMeteringPointFactory(WIDTH, HEIGHT);
    }

    @After
    public void tearDown() throws ExecutionException, InterruptedException {
        CameraX.shutdown().get();
    }

    @Test
    public void defaultAreaSize() {
        MeteringPoint point = mPointFactory.createPoint(0, 0);
        assertThat(point.getSize()).isEqualTo(MeteringPointFactory.getDefaultPointSize());
        assertThat(point.getSurfaceAspectRatio()).isNull();
    }

    @Test
    public void createPointWithValidAreaSize() {
        final float areaSize = 0.2f;
        MeteringPoint point = mPointFactory.createPoint(0, 0, areaSize);
        assertThat(point.getSize()).isEqualTo(areaSize);
        assertThat(point.getSurfaceAspectRatio()).isNull();
    }

    @Test
    public void createPointLeftTop_correctValueSet() {
        MeteringPoint meteringPoint = mPointFactory.createPoint(0f, 0f);
        assertThat(meteringPoint.getX()).isEqualTo(0f);
        assertThat(meteringPoint.getY()).isEqualTo(0f);
    }

    @Test
    public void createPointLeftBottom_correctValueSet() {
        MeteringPoint meteringPoint2 = mPointFactory.createPoint(0f, HEIGHT);
        assertThat(meteringPoint2.getX()).isEqualTo(0f);
        assertThat(meteringPoint2.getY()).isEqualTo(1f);
    }

    @Test
    public void createPointRightTop_correctValueSet() {
        MeteringPoint meteringPoint3 = mPointFactory.createPoint(WIDTH, 0f);
        assertThat(meteringPoint3.getX()).isEqualTo(1f);
        assertThat(meteringPoint3.getY()).isEqualTo(0f);
    }

    @Test
    public void createPointRightBottom_correctValueSet() {
        MeteringPoint meteringPoint4 = mPointFactory.createPoint(WIDTH, HEIGHT);
        assertThat(meteringPoint4.getX()).isEqualTo(1f);
        assertThat(meteringPoint4.getY()).isEqualTo(1f);
    }

    @Test
    public void createPointWithFoVUseCase_success() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LensFacing.BACK));

        ImageAnalysisConfig imageAnalysisConfig =
                new ImageAnalysisConfig.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetName("ImageAnalysis")
                        .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(LensFacing.BACK).build();
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                CameraX.bindToLifecycle(mLifecycle, cameraSelector, imageAnalysis);
            }
        });

        SensorOrientedMeteringPointFactory factory = new SensorOrientedMeteringPointFactory(
                WIDTH, HEIGHT, imageAnalysis);
        MeteringPoint point = factory.createPoint(0f, 0f);
        assertThat(point.getSurfaceAspectRatio()).isEqualTo(new Rational(4, 3));
    }

    @Test(expected = IllegalStateException.class)
    public void createPointWithFoVUseCase_FailedNotBound() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(LensFacing.BACK));

        ImageAnalysisConfig imageAnalysisConfig =
                new ImageAnalysisConfig.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetName("ImageAnalysis")
                        .build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);

        // This will throw IllegalStateException.
        SensorOrientedMeteringPointFactory factory = new SensorOrientedMeteringPointFactory(
                WIDTH, HEIGHT, imageAnalysis);
    }
}
