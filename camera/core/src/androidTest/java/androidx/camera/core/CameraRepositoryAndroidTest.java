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

import static org.junit.Assert.assertThrows;

import androidx.camera.testing.fakes.FakeCameraFactory;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class CameraRepositoryAndroidTest {

    private CameraRepository cameraRepository;

    @Before
    public void setUp() {
        cameraRepository = new CameraRepository();
        cameraRepository.init(new FakeCameraFactory());
    }

    @Test
    public void cameraIdsCanBeAcquired() {
        Set<String> cameraIds = cameraRepository.getCameraIds();

        assertThat(cameraIds).isNotEmpty();
    }

    @Test
    public void cameraCanBeObtainedWithValidId() {
        for (String cameraId : cameraRepository.getCameraIds()) {
            BaseCamera camera = cameraRepository.getCamera(cameraId);

            assertThat(camera).isNotNull();
        }
    }

    @Test
    public void cameraCannotBeObtainedWithInvalidId() {
        assertThrows(
                IllegalArgumentException.class, () -> cameraRepository.getCamera("no_such_id"));
    }
}
