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

package androidx.camera.testing;

import android.Manifest;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.camera.core.BaseCamera;
import androidx.camera.core.CameraX;
import androidx.camera.core.UseCase;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Preconditions;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Utility functions for obtaining instances of camera2 classes. */
public final class CameraUtil {
    private CameraUtil() {
    }

    /** Amount of time to wait before timing out when trying to open a {@link CameraDevice}. */
    private static final int CAMERA_OPEN_TIMEOUT_SECONDS = 2;

    /**
     * Gets a new instance of a {@link CameraDevice}.
     *
     * <p>This method attempts to open up a new camera. Since the camera api is asynchronous it
     * needs to wait for camera open
     *
     * <p>After the camera is no longer needed {@link #releaseCameraDevice(CameraDeviceHolder)}
     * should be called to clean up resources.
     *
     * @throws CameraAccessException if the device is unable to access the camera
     * @throws InterruptedException  if a {@link CameraDevice} can not be retrieved within a set
     *                               time
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    @NonNull
    public static CameraDeviceHolder getCameraDevice()
            throws CameraAccessException, InterruptedException, TimeoutException,
            ExecutionException {
        CameraManager cameraManager = getCameraManager();

        // Use the first camera available.
        String[] cameraIds = cameraManager.getCameraIdList();
        if (cameraIds.length <= 0) {
            throw new CameraAccessException(
                    CameraAccessException.CAMERA_ERROR, "Device contains no cameras.");
        }
        String cameraName = cameraIds[0];

        return new CameraDeviceHolder(cameraManager, cameraName);
    }

    /**
     * A container class used to hold a {@link CameraDevice}.
     *
     * <p>This class should contain a valid {@link CameraDevice} that can be retrieved with
     * {@link #get()}, unless the device has been closed.
     *
     * <p>The camera device should always be closed with
     * {@link CameraUtil#releaseCameraDevice(CameraDeviceHolder)} once finished with the device.
     */
    public static class CameraDeviceHolder {

        final Object mLock = new Object();

        @GuardedBy("mLock")
        CameraDevice mCameraDevice;
        final HandlerThread mHandlerThread;
        private ListenableFuture<Void> mCloseFuture;

        @RequiresPermission(Manifest.permission.CAMERA)
        CameraDeviceHolder(@NonNull CameraManager cameraManager, @NonNull String cameraId)
                throws InterruptedException, ExecutionException, TimeoutException {
            mHandlerThread = new HandlerThread(String.format("CameraThread-%s", cameraId));
            mHandlerThread.start();

            ListenableFuture<Void> cameraOpenFuture = openCamera(cameraManager, cameraId);

            // Wait for the open future to complete before continuing.
            cameraOpenFuture.get(CAMERA_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @RequiresPermission(Manifest.permission.CAMERA)
        // Should only be called once during initialization.
        private ListenableFuture<Void> openCamera(@NonNull CameraManager cameraManager,
                @NonNull String cameraId) {
            return CallbackToFutureAdapter.getFuture(openCompleter -> {
                mCloseFuture = CallbackToFutureAdapter.getFuture(closeCompleter -> {
                    cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {

                        @Override
                        public void onOpened(@NonNull CameraDevice cameraDevice) {
                            synchronized (mLock) {
                                Preconditions.checkState(mCameraDevice == null, "CameraDevice "
                                        + "should not have been opened yet.");
                                mCameraDevice = cameraDevice;
                            }
                            openCompleter.set(null);
                        }

                        @Override
                        public void onClosed(@NonNull CameraDevice cameraDevice) {
                            closeCompleter.set(null);
                            mHandlerThread.quitSafely();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                            synchronized (mLock) {
                                mCameraDevice = null;
                            }
                            cameraDevice.close();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice cameraDevice, int i) {
                            boolean notifyOpenFailed = false;
                            synchronized (mLock) {
                                if (mCameraDevice == null) {
                                    notifyOpenFailed = true;
                                } else {
                                    mCameraDevice = null;
                                }
                            }

                            if (notifyOpenFailed) {
                                openCompleter.setException(new RuntimeException("Failed to "
                                        + "open camera device due to error code: " + i));
                            }
                            cameraDevice.close();

                        }
                    }, new Handler(mHandlerThread.getLooper()));

                    return "Close[cameraId=" + cameraId + "]";
                });

                return "Open[cameraId=" + cameraId + "]";
            });
        }

        /**
         * Blocks until the camera device has been closed.
         */
        void close() throws ExecutionException, InterruptedException {
            CameraDevice cameraDevice;
            synchronized (mLock) {
                cameraDevice = mCameraDevice;
                mCameraDevice = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
            }

            mCloseFuture.get();
        }

        /**
         * Returns the camera device if it opened successfully and has not been closed.
         */
        @Nullable
        public CameraDevice get() {
            synchronized (mLock) {
                return mCameraDevice;
            }
        }
    }

    /**
     * Cleans up resources that need to be kept around while the camera device is active.
     *
     * @param cameraDeviceHolder camera that was obtained via {@link #getCameraDevice()}
     */
    public static void releaseCameraDevice(@NonNull CameraDeviceHolder cameraDeviceHolder)
            throws ExecutionException, InterruptedException {
        cameraDeviceHolder.close();
    }

    public static CameraManager getCameraManager() {
        return (CameraManager)
                ApplicationProvider.getApplicationContext()
                        .getSystemService(Context.CAMERA_SERVICE);
    }

    /**
     * Opens a camera and associates the camera with multiple use cases.
     *
     * <p>Sets the use case to be online and active, so that the use case is in a state to issue
     * capture requests to the camera. The caller is responsible for making the use case inactive
     * and offline and for closing the camera afterwards.
     *
     * @param cameraId to open
     * @param camera   to open
     * @param useCases to associate with
     */
    public static void openCameraWithUseCase(String cameraId, BaseCamera camera,
            UseCase... useCases) {
        camera.addOnlineUseCase(Arrays.asList(useCases));
        for (UseCase useCase : useCases) {
            useCase.attachCameraControl(cameraId, camera.getCameraControlInternal());
            camera.onUseCaseActive(useCase);
        }
    }

    /**
     * Detach multiple use cases from a camera.
     *
     * <p>Sets the use cases to be inactive and remove from the online list.
     *
     * @param camera   to detach from
     * @param useCases to be detached
     */
    public static void detachUseCaseFromCamera(BaseCamera camera, UseCase... useCases) {
        for (UseCase useCase : useCases) {
            camera.onUseCaseInactive(useCase);
        }
        camera.removeOnlineUseCase(Arrays.asList(useCases));
    }

    /**
     * Check if there is any camera in the device.
     *
     * <p>If there is no camera in the device, most tests will failed.
     *
     * @return false if no camera
     */
    public static boolean deviceHasCamera() {
        // TODO Think about external camera case,
        //  especially no built in camera but there might be some external camera

        // It also could be checked by PackageManager's hasSystemFeature() with following:
        //     FEATURE_CAMERA, FEATURE_CAMERA_FRONT, FEATURE_CAMERA_ANY.
        // But its needed to consider one case that platform build with camera feature but there is
        // no built in camera or external camera.

        int numberOfCamera = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            try {
                numberOfCamera = ((CameraManager) ApplicationProvider.getApplicationContext()
                        .getSystemService(Context.CAMERA_SERVICE)).getCameraIdList().length;
            } catch (CameraAccessException e) {
                Log.e(CameraUtil.class.getSimpleName(), "Unable to check camera availability.", e);
            }
        } else {
            numberOfCamera = Camera.getNumberOfCameras();
        }

        return numberOfCamera > 0;
    }

    /**
     * Check if the specified lensFacing is supported by the device.
     *
     * @param lensFacing The desired camera lensFacing.
     * @return True if the device supports the lensFacing.
     * @throws IllegalStateException if the CAMERA permission is not currently granted.
     */
    public static boolean hasCameraWithLensFacing(@NonNull CameraX.LensFacing lensFacing) {

        CameraManager cameraManager = getCameraManager();

        List<String> camerasList = null;
        try {
            camerasList = Arrays.asList(cameraManager.getCameraIdList());
        } catch (CameraAccessException e) {
            throw new IllegalStateException(
                    "Unable to retrieve list of cameras on device.", e);
        }

        // Convert to from CameraX enum to Camera2 CameraMetadata
        Integer lensFacingInteger = -1;
        switch (lensFacing) {
            case BACK:
                lensFacingInteger = CameraMetadata.LENS_FACING_BACK;
                break;
            case FRONT:
                lensFacingInteger = CameraMetadata.LENS_FACING_FRONT;
                break;
        }

        for (String cameraId : camerasList) {
            CameraCharacteristics characteristics = null;
            try {
                characteristics = cameraManager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                throw new IllegalStateException(
                        "Unable to retrieve info for camera with id " + cameraId + ".", e);
            }
            Integer cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (cameraLensFacing == null) {
                continue;
            }
            if (cameraLensFacing.equals(lensFacingInteger)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The current lens facing directions supported by CameraX, as defined the
     * {@link CameraMetadata}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CameraMetadata.LENS_FACING_FRONT, CameraMetadata.LENS_FACING_BACK})
    @interface SupportedLensFacingInt {
    }


    /**
     * Converts a lens facing direction from a {@link CameraMetadata} integer to a
     * {@link CameraX.LensFacing}.
     *
     * @param lensFacingInteger The lens facing integer, as defined in {@link CameraMetadata}.
     * @return The lens facing enum.
     */
    @NonNull
    public static CameraX.LensFacing getLensFacingEnumFromInt(
            @SupportedLensFacingInt int lensFacingInteger) {
        switch (lensFacingInteger) {
            case CameraMetadata.LENS_FACING_BACK:
                return CameraX.LensFacing.BACK;
            case CameraMetadata.LENS_FACING_FRONT:
                return CameraX.LensFacing.FRONT;
            default:
                throw new IllegalArgumentException(
                        "Unsupported lens facing integer: " + lensFacingInteger);
        }
    }
}
