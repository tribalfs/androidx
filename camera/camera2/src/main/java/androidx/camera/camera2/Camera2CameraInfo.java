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

import android.annotation.SuppressLint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraOrientationUtil;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImageOutputConfiguration.RotationValue;
import androidx.core.util.Preconditions;

/** Implementation of the {@link CameraInfo} interface that exposes parameters through camera2. */
final class Camera2CameraInfo implements CameraInfo {

    private final CameraCharacteristics mCameraCharacteristics;

    Camera2CameraInfo(CameraManager cameraManager, String cameraId)
            throws CameraInfoUnavailableException {
        try {
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            throw new CameraInfoUnavailableException(
                    "Unable to retrieve info for camera " + cameraId, e);
        }

        checkCharacteristicAvailable(
                CameraCharacteristics.SENSOR_ORIENTATION, "Sensor orientation");
        checkCharacteristicAvailable(CameraCharacteristics.LENS_FACING, "Lens facing direction");
    }

    @SuppressLint("RestrictedApi") // TODO(b/124323692): Remove after aosp/900913 is merged
    @Nullable
    @Override
    public LensFacing getLensFacing() {
        Integer lensFacing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        Preconditions.checkNotNull(lensFacing);
        switch (lensFacing) {
            case CameraCharacteristics.LENS_FACING_FRONT:
                return LensFacing.FRONT;
            case CameraCharacteristics.LENS_FACING_BACK:
                return LensFacing.BACK;
            default:
                return null;
        }
    }

    @SuppressLint("RestrictedApi") // TODO(b/124323692): Remove after aosp/900913 is merged
    @Override
    public int getSensorRotationDegrees(@RotationValue int relativeRotation) {
        Integer sensorOrientation =
                mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Preconditions.checkNotNull(sensorOrientation);
        int relativeRotationDegrees =
                CameraOrientationUtil.surfaceRotationToDegrees(relativeRotation);
        // Currently this assumes that a back-facing camera is always opposite to the screen.
        // This may not be the case for all devices, so in the future we may need to handle that
        // scenario.
        boolean isOppositeFacingScreen = LensFacing.BACK.equals(getLensFacing());
        return CameraOrientationUtil.getRelativeImageRotation(
                relativeRotationDegrees,
                sensorOrientation,
                isOppositeFacingScreen);
    }

    private void checkCharacteristicAvailable(CameraCharacteristics.Key<?> key, String readableName)
            throws CameraInfoUnavailableException {
        if (mCameraCharacteristics.get(key) == null) {
            throw new CameraInfoUnavailableException(
                    "Camera characteristics map is missing value for characteristic: "
                            + readableName);
        }
    }

    @Override
    public int getSensorRotationDegrees() {
        return getSensorRotationDegrees(Surface.ROTATION_0);
    }
}
