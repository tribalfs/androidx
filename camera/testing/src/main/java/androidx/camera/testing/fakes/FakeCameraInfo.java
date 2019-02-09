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

package androidx.camera.testing.fakes;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImageOutputConfiguration.RotationValue;

/**
 * Information for a fake camera.
 *
 * <p>This camera info can be constructed with fake values.
 */
class FakeCameraInfo implements CameraInfo {

  private final int sensorRotation;
  private final LensFacing lensFacing;

  FakeCameraInfo() {
    this(/*sensorRotation=*/ 0, /*lensFacing=*/ LensFacing.BACK);
  }

  FakeCameraInfo(int sensorRotation, LensFacing lensFacing) {
    this.sensorRotation = sensorRotation;
    this.lensFacing = lensFacing;
  }

  @Nullable
  @Override
  public LensFacing getLensFacing() {
    return lensFacing;
  }

  @Override
  public int getSensorRotationDegrees(@RotationValue int relativeRotation) {
    return sensorRotation;
  }
}
