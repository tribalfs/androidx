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

package androidx.camera.core;

import android.content.Context;
import android.graphics.PointF;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.camera.core.impl.utils.CameraSelectorUtil;

/**
 * A {@link MeteringPointFactory} that can convert a {@link View} (x, y) into a
 * {@link MeteringPoint} which can then be used to construct a {@link FocusMeteringAction} to
 * start a focus and metering action.
 *
 * <p>For apps showing full camera preview in a View without any scaling, cropping or
 * rotating applied, they can simply use view width and height to create the
 * {@link DisplayOrientedMeteringPointFactory} and then pass {@link View} (x, y) to create a
 * {@link MeteringPoint}. This factory will convert the (x, y) into the sensor (x, y) based on
 * display rotation and {@link LensFacing}.
 *
 * <p>If camera preview is scaled, cropped or rotated in the {@link View}, it is applications'
 * duty to transform the coordinates properly so that the width and height of this
 * factory represents the full Preview FOV and also the (x,y) passed to create
 * {@link MeteringPoint} needs to be adjusted by apps to the  coordinates left-top (0,0) -
 * right-bottom (width, height). For Example, if the preview is scaled to 2X from the center and
 * is cropped in a {@link View}. Assuming that the dimension of View is (240, 320), then the
 * width/height of this {@link DisplayOrientedMeteringPointFactory} should be (480, 640).  And
 * the (x, y) from the {@link View} should be converted to (x + (480-240)/2, y + (640 - 320)/2)
 * first.
 *
 * @see MeteringPoint
 */
public final class DisplayOrientedMeteringPointFactory extends MeteringPointFactory {
    /** The logical width of FoV in current display orientation */
    private final float mWidth;
    /** The logical height of FoV in current display orientation */
    private final float mHeight;
    /** Lens facing is required for correctly adjusted for front camera */
    private final CameraSelector mCameraSelector;
    /** {@link Display} used for detecting display orientation */
    @NonNull
    private final Display mDisplay;
    @NonNull
    private final CameraInfoInternal mCameraInfo;

    /**
     * Creates a {@link DisplayOrientedMeteringPointFactory} for converting View (x, y) into a
     * {@link MeteringPoint} based on default display's orientation and {@link CameraSelector}.
     *
     * <p>The width/height of this factory forms a coordinate left-top (0, 0) - right-bottom
     * (width, height) which represents the full camera preview FOV in default display's
     * orientation. For apps showing full camera preview in a {@link View}, it is as simple as
     * passing View's width/height and passing View (x, y) directly to create a
     * {@link MeteringPoint}. Otherwise the (x, y) passed to
     * {@link MeteringPointFactory#createPoint(float, float)} should be adjusted to this
     * coordinate system first.
     *
     * @param context        context to get the {@link WindowManager} for default display rotation.
     * @param cameraSelector current cameraSelector to choose camera.
     * @param width          the width of the coordinate which are mapped to the full camera preview
     *                       FOV in default display's orientation.
     * @param height         the height of the coordinate which are mapped to the full camera
     *                       preview
     *                       FOVin default display's orientation.
     */
    public DisplayOrientedMeteringPointFactory(@NonNull Context context,
            @NonNull CameraSelector cameraSelector, float width, float height) {
        this(((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay(),
                cameraSelector, width, height);
    }

    /**
     * Creates a {@link DisplayOrientedMeteringPointFactory} for converting View (x, y) into a
     * {@link MeteringPoint} based on custom display's rotation and {@link CameraSelector}. This is
     * used in multi-display situation.
     *
     * <p>The width/height of this factory forms a coordinate left-top (0, 0) - right-bottom
     * (width, height) which represents the full camera preview FOV in default display's
     * orientation. For apps showing full camera preview in a {@link View}, it is as simple as
     * passing View's width/height and passing View (x, y) directly to create a
     * {@link MeteringPoint}. Otherwise the (x, y) passed to
     * {@link MeteringPointFactory#createPoint(float, float)} should be adjusted to this
     * coordinate system first.
     *
     * @param display        {@link Display} to get the orientation from.
     * @param cameraSelector current cameraSelector to choose camera.
     * @param width          the width of the coordinate which are mapped to the full camera preview
     *                       FOV in given display's orientation.
     * @param height         the height of the coordinate which are mapped to the full camera
     *                       preview
     *                       FOV in given display's orientation.
     */
    public DisplayOrientedMeteringPointFactory(@NonNull Display display,
            @NonNull CameraSelector cameraSelector, float width, float height) {
        mWidth = width;
        mHeight = height;
        mCameraSelector = cameraSelector;
        mDisplay = display;
        try {
            String cameraId =
                    CameraX.getCameraWithCameraDeviceConfig(
                            CameraSelectorUtil.toCameraDeviceConfig(mCameraSelector));
            mCameraInfo = CameraX.getCameraInfo(cameraId);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to get camera id for the CameraSelector.", e);
        }
    }

    private LensFacing getLensFacing() {
        return mCameraInfo.getLensFacing();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    protected PointF convertPoint(float x, float y) {
        float width = mWidth;
        float height = mHeight;

        boolean compensateForMirroring = (getLensFacing() == LensFacing.FRONT);
        int relativeCameraOrientation = getRelativeCameraOrientation(compensateForMirroring);
        float outputX = x;
        float outputY = y;
        float outputWidth = width;
        float outputHeight = height;

        if (relativeCameraOrientation == 90 || relativeCameraOrientation == 270) {
            // We're horizontal. Swap width/height. Swap x/y.
            outputX = y;
            outputY = x;
            outputWidth = height;
            outputHeight = width;
        }

        switch (relativeCameraOrientation) {
            // Map to correct coordinates according to relativeCameraOrientation
            case 90:
                outputY = outputHeight - outputY;
                break;
            case 180:
                outputX = outputWidth - outputX;
                outputY = outputHeight - outputY;
                break;
            case 270:
                outputX = outputWidth - outputX;
                break;
            default:
                break;
        }

        // Swap x if it's a mirrored preview
        if (compensateForMirroring) {
            outputX = outputWidth - outputX;
        }

        // Normalized it to [0, 1]
        outputX = outputX / outputWidth;
        outputY = outputY / outputHeight;

        return new PointF(outputX, outputY);
    }

    private int getRelativeCameraOrientation(boolean compensateForMirroring) {
        int rotationDegrees;
        try {
            int displayRotation = mDisplay.getRotation();
            rotationDegrees = mCameraInfo.getSensorRotationDegrees(displayRotation);
            if (compensateForMirroring) {
                rotationDegrees = (360 - rotationDegrees) % 360;
            }
        } catch (Exception e) {
            rotationDegrees = 0;
        }
        return rotationDegrees;
    }
}
