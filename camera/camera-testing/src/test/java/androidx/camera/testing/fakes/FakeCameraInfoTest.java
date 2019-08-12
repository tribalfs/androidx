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

package androidx.camera.testing.fakes;


import static com.google.common.truth.Truth.assertThat;

import android.os.Build;

import androidx.camera.core.CameraX;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
public final class FakeCameraInfoTest {

    private static final int SENSOR_ROTATION_DEGREES = 90;
    private static final CameraX.LensFacing LENS_FACING = CameraX.LensFacing.FRONT;

    private FakeCameraInfo mFakeCameraInfo;

    @Before
    public void setUp() {
        mFakeCameraInfo = new FakeCameraInfo(SENSOR_ROTATION_DEGREES, LENS_FACING);
    }

    @Test
    public void canRetrieveLensFacingDirection() {
        assertThat(mFakeCameraInfo.getLensFacing()).isSameInstanceAs(LENS_FACING);
    }

    @Test
    public void canRetrieveSensorRotation() {
        assertThat(mFakeCameraInfo.getSensorRotationDegrees()).isEqualTo(SENSOR_ROTATION_DEGREES);
    }
}
