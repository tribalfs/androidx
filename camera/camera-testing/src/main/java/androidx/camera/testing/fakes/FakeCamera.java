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

import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.BaseCamera;
import androidx.camera.core.CameraControlInternal;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CaptureConfig;
import androidx.camera.core.DeferrableSurface;
import androidx.camera.core.DeferrableSurfaces;
import androidx.camera.core.Observable;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseAttachState;
import androidx.camera.core.impl.LiveDataObservable;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A fake camera which will not produce any data, but provides a valid BaseCamera implementation.
 */
public class FakeCamera implements BaseCamera {
    private static final String TAG = "FakeCamera";
    private static final String DEFAULT_CAMERA_ID = "0";
    private final LiveDataObservable<BaseCamera.State> mObservableState =
            new LiveDataObservable<>();
    private final CameraControlInternal mCameraControlInternal;
    private final CameraInfo mCameraInfo;
    private String mCameraId;
    private UseCaseAttachState mUseCaseAttachState;
    private State mState = State.CLOSED;
    private int mAvailableCameraCount = 1;

    @Nullable
    private SessionConfig mSessionConfig;
    @Nullable
    private SessionConfig mCameraControlSessionConfig;

    private List<DeferrableSurface> mConfiguredDeferrableSurfaces = Collections.emptyList();

    public FakeCamera() {
        this(DEFAULT_CAMERA_ID, new FakeCameraInfo(), /*cameraControl=*/null);
    }

    public FakeCamera(@NonNull String cameraId) {
        this(cameraId, new FakeCameraInfo(), /*cameraControl=*/null);
    }

    public FakeCamera(@NonNull CameraInfo cameraInfo,
            @Nullable CameraControlInternal cameraControl) {
        this(DEFAULT_CAMERA_ID, cameraInfo, cameraControl);
    }

    public FakeCamera(@NonNull String cameraId,
            @NonNull CameraInfo cameraInfo,
            @Nullable CameraControlInternal cameraControl) {
        mCameraInfo = cameraInfo;
        mCameraId = cameraId;
        mUseCaseAttachState = new UseCaseAttachState(cameraId);
        mCameraControlInternal = cameraControl == null ? new FakeCameraControl(this)
                : cameraControl;
        mObservableState.postValue(State.CLOSED);
    }

    /**
     * Sets the number of cameras that are available to open.
     *
     * <p>If this number is set to 0, then calling {@link #open()} will wait in a {@code
     * PENDING_OPEN} state until the number is set to a value greater than 0 before entering an
     * {@code OPEN} state.
     *
     * @param count An integer number greater than 0 representing the number of available cameras
     *              to open on this device.
     */
    public void setAvailableCameraCount(@IntRange(from = 0) int count) {
        Preconditions.checkArgumentNonnegative(count);
        mAvailableCameraCount = count;
        if (mAvailableCameraCount > 0 && mState == State.PENDING_OPEN) {
            open();
        }
    }

    /**
     * Retrieves the number of cameras available to open on this device, as seen by this camera.
     *
     * @return An integer number greater than 0 representing the number of available cameras to
     * open on this device.
     */
    @IntRange(from = 0)
    public int getAvailableCameraCount() {
        return mAvailableCameraCount;
    }

    @Override
    public void open() {
        checkNotReleased();
        if (mState == State.CLOSED || mState == State.PENDING_OPEN) {
            if (mAvailableCameraCount > 0) {
                mState = State.OPEN;
                mObservableState.postValue(State.OPEN);
            } else {
                mState = State.PENDING_OPEN;
                mObservableState.postValue(State.PENDING_OPEN);
            }
        }
    }

    @Override
    public void close() {
        checkNotReleased();
        switch (mState) {
            case OPEN:
                mSessionConfig = null;
                reconfigure();
                // fall through
            case PENDING_OPEN:
                mState = State.CLOSED;
                mObservableState.postValue(State.CLOSED);
                break;
            default:
                break;
        }
    }

    @Override
    @NonNull
    public ListenableFuture<Void> release() {
        checkNotReleased();
        if (mState == State.OPEN) {
            close();
        }

        mState = State.RELEASED;
        mObservableState.postValue(State.RELEASED);
        return Futures.immediateFuture(null);
    }

    @NonNull
    @Override
    public Observable<BaseCamera.State> getCameraState() {
        return mObservableState;
    }

    @Override
    public void onUseCaseActive(@NonNull UseCase useCase) {
        Log.d(TAG, "Use case " + useCase + " ACTIVE for camera " + mCameraId);

        mUseCaseAttachState.setUseCaseActive(useCase);
        updateCaptureSessionConfig();
    }

    /** Removes the use case from a state of issuing capture requests. */
    @Override
    public void onUseCaseInactive(@NonNull UseCase useCase) {
        Log.d(TAG, "Use case " + useCase + " INACTIVE for camera " + mCameraId);

        mUseCaseAttachState.setUseCaseInactive(useCase);
        updateCaptureSessionConfig();
    }

    /** Updates the capture requests based on the latest settings. */
    @Override
    public void onUseCaseUpdated(@NonNull UseCase useCase) {
        Log.d(TAG, "Use case " + useCase + " UPDATED for camera " + mCameraId);

        mUseCaseAttachState.updateUseCase(useCase);
        updateCaptureSessionConfig();
    }

    @Override
    public void onUseCaseReset(@NonNull UseCase useCase) {
        Log.d(TAG, "Use case " + useCase + " RESET for camera " + mCameraId);

        mUseCaseAttachState.updateUseCase(useCase);
        updateCaptureSessionConfig();
        openCaptureSession();
    }

    /**
     * Sets the use case to be in the state where the capture session will be configured to handle
     * capture requests from the use case.
     */
    @Override
    public void addOnlineUseCase(@NonNull final Collection<UseCase> useCases) {
        if (useCases.isEmpty()) {
            return;
        }

        Log.d(TAG, "Use cases " + useCases + " ONLINE for camera " + mCameraId);
        for (UseCase useCase : useCases) {
            mUseCaseAttachState.setUseCaseOnline(useCase);
        }

        open();
        updateCaptureSessionConfig();
        openCaptureSession();
    }

    /**
     * Removes the use case to be in the state where the capture session will be configured to
     * handle capture requests from the use case.
     */
    @Override
    public void removeOnlineUseCase(@NonNull final Collection<UseCase> useCases) {
        if (useCases.isEmpty()) {
            return;
        }

        Log.d(TAG, "Use cases " + useCases + " OFFLINE for camera " + mCameraId);
        for (UseCase useCase : useCases) {
            mUseCaseAttachState.setUseCaseOffline(useCase);
        }

        if (mUseCaseAttachState.getOnlineUseCases().isEmpty()) {
            close();
            return;
        }

        openCaptureSession();
        updateCaptureSessionConfig();
    }

    // Returns fixed CameraControlInternal instance in order to verify the instance is correctly
    // attached.
    @NonNull
    @Override
    public CameraControlInternal getCameraControlInternal() {
        return mCameraControlInternal;
    }

    @NonNull
    @Override
    public CameraInfo getCameraInfo() {
        return mCameraInfo;
    }

    @Override
    public void onCameraControlUpdateSessionConfig(@NonNull SessionConfig sessionConfig) {
        mCameraControlSessionConfig = sessionConfig;
        updateCaptureSessionConfig();
    }

    @Override
    public void onCameraControlCaptureRequests(@NonNull List<CaptureConfig> captureConfigs) {
        Log.d(TAG, "Capture requests submitted:\n    " + TextUtils.join("\n    ", captureConfigs));
    }

    private void checkNotReleased() {
        if (mState == State.RELEASED) {
            throw new IllegalStateException("Camera has been released.");
        }
    }

    private void openCaptureSession() {
        SessionConfig.ValidatingBuilder validatingBuilder;
        validatingBuilder = mUseCaseAttachState.getOnlineBuilder();
        if (!validatingBuilder.isValid()) {
            Log.d(TAG, "Unable to create capture session due to conflicting configurations");
            return;
        }

        if (mState != State.OPEN) {
            Log.d(TAG, "CameraDevice is not opened");
            return;
        }

        mSessionConfig = validatingBuilder.build();
        reconfigure();
    }

    private void updateCaptureSessionConfig() {
        SessionConfig.ValidatingBuilder validatingBuilder;
        validatingBuilder = mUseCaseAttachState.getActiveAndOnlineBuilder();

        if (validatingBuilder.isValid()) {
            // Apply CameraControlInternal's SessionConfig to let CameraControlInternal be able
            // to control Repeating Request and process results.
            if (mCameraControlSessionConfig != null) {
                validatingBuilder.add(mCameraControlSessionConfig);
            }

            mSessionConfig = validatingBuilder.build();
        }
    }

    private void reconfigure() {
        notifySurfaceDetached();

        if (mSessionConfig != null) {
            List<DeferrableSurface> surfaces = mSessionConfig.getSurfaces();

            // Before creating capture session, some surfaces may need to refresh.
            DeferrableSurfaces.refresh(surfaces);

            mConfiguredDeferrableSurfaces = new ArrayList<>(surfaces);

            List<Surface> configuredSurfaces = new ArrayList<>(
                    DeferrableSurfaces.surfaceSet(
                            mConfiguredDeferrableSurfaces));
            if (configuredSurfaces.isEmpty()) {
                Log.e(TAG, "Unable to open capture session with no surfaces. ");
                return;
            }
        }

        notifySurfaceAttached();
    }

    // Notify the surface is attached to a new capture session.
    private void notifySurfaceAttached() {
        for (DeferrableSurface deferrableSurface : mConfiguredDeferrableSurfaces) {
            deferrableSurface.notifySurfaceAttached();
        }
    }

    // Notify the surface is detached from current capture session.
    private void notifySurfaceDetached() {
        for (DeferrableSurface deferredSurface : mConfiguredDeferrableSurfaces) {
            deferredSurface.notifySurfaceDetached();
        }
        // Clears the mConfiguredDeferrableSurfaces to prevent from duplicate
        // notifySurfaceDetached calls.
        mConfiguredDeferrableSurfaces.clear();
    }
}
