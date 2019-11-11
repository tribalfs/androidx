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

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

/**
 * The {@link CameraControl} provides various asynchronous operations like zoom, focus and
 * metering which affects output of all {@link UseCase}s currently bound to that camera.
 *
 * <p>The application can retrieve the {@link CameraControl} instance via
 * {@link Camera#getCameraControl()}. {@link CameraControl} is ready to start operations
 * immediately after {@link Camera} is retrieved and {@link UseCase}s are bound to that camera.
 * When all {@link UseCase}s are unbound, or when camera is closing or closed because
 * lifecycle onStop happens, the {@link CameraControl} will reject all operations.
 *
 * <p>Each method Of {@link CameraControl} returns a {@link ListenableFuture} which apps can use to
 * check the asynchronous result. If the operation is not allowed in current state, the returned
 * {@link ListenableFuture} will fail immediately with
 * {@link CameraControl.OperationCanceledException}.
 */
public interface CameraControl {
    /**
     * Enable the torch or disable the torch.
     *
     * <p>{@link CameraInfo#getTorchState()} can be used to query the torch state.
     * If the camera doesn't have a flash unit or doesn't support torch (see
     * {@link TorchState#UNAVAILABLE}), then the call will do nothing and the returned
     * {@link ListenableFuture} will complete immediately with a failed result.
     *
     * <p>When the torch is enabled, the torch will remain enabled during photo capture regardless
     * of {@link FlashMode} setting. When the torch is disabled, flash will function as
     * {@link FlashMode} set by either {@link ImageCapture#setFlashMode(FlashMode)} or
     * {@link ImageCaptureConfig.Builder#setFlashMode(FlashMode)}.
     *
     * @param torch true to open the torch, false to close it.
     * @return A {@link ListenableFuture} which is successful when the torch was changed to the
     * value specified. It fails when it is unable to change the torch state.
     */
    @NonNull
    ListenableFuture<Void> enableTorch(boolean torch);

    /**
     * Starts a focus and metering action configured by the {@link FocusMeteringAction}.
     *
     * <p>It will trigger a auto focus action and enable AF/AE/AWB metering regions. The action
     * is configured by a {@link FocusMeteringAction} which contains the configuration of
     * multiple AF/AE/AWB {@link MeteringPoint}s, auto-cancel duration. See
     * {@link FocusMeteringAction} for more details.
     *
     * <p>Only one {@link FocusMeteringAction} is allowed to run at a time. If multiple
     * {@link FocusMeteringAction} are executed in a row, only the latest one will work and
     * other actions will be cancelled.
     *
     * <p>If the {@link FocusMeteringAction} specifies more AF/AE/AWB regions than what is
     * supported on current device, only the first region and then in order up to the number of
     * regions supported by the device will be enabled. If it turns out no added regions can be
     * supported on the device, the returned {@link ListenableFuture} in
     * {@link CameraControl#startFocusAndMetering(FocusMeteringAction)} will fail immediately.
     *
     * @param action the {@link FocusMeteringAction} to be executed.
     * @return A {@link ListenableFuture} which completes when auto focus is done. The result of
     * the ListenableFuture is a {@link FocusMeteringResult} which contains a flag indicating
     * focus is locked successfully or not.
     */
    @NonNull
    ListenableFuture<FocusMeteringResult> startFocusAndMetering(
            @NonNull FocusMeteringAction action);

    /**
     * Cancels current {@link FocusMeteringAction} and clears AF/AE/AWB regions.
     *
     * <p>Clear the AF/AE/AWB regions and update current AF mode to continuous AF (if
     * supported). If current {@link FocusMeteringAction} has not completed, the returned
     * {@link ListenableFuture} in {@link #startFocusAndMetering} will fail with
     * {@link OperationCanceledException}.
     *
     * @return A {@link ListenableFuture} which completes when the AF/AE/AWB regions is clear and AF
     * mode is set to continuous focus (if supported).
     */
    @NonNull
    ListenableFuture<Void> cancelFocusAndMetering();

    /**
     * Sets current zoom by ratio.
     *
     * <p>It modifies both current zoom ratio and zoom percentage so if apps are observing
     * zoomRatio or zoomPercentage, they will get the update as well. If the ratio is
     * smaller than {@link CameraInfo#getMinZoomRatio()} or larger than
     * {@link CameraInfo#getMaxZoomRatio()}, it won't modify current zoom ratio. It is
     * applications' duty to clamp the ratio.
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     * result contains the requested zoom ratio. It fails with
     * {@link OperationCanceledException} if there is newer value being set or camera is closed.
     * If ratio is out of range, it fails with {@link IllegalArgumentException}.
     */
    @NonNull
    ListenableFuture<Void> setZoomRatio(float ratio);

    /**
     * Sets current zoom by percentage ranging from 0f to 1.0f. Percentage 0f represents the
     * minimum zoom while percentage 1.0f represents the maximum zoom. One advantage of zoom
     * percentage is that it ensures FOV varies linearly with the percentage value.
     *
     * <p>It modifies both current zoom ratio and zoom percentage so if apps are observing
     * zoomRatio or zoomPercentage, they will get the update as well. If the percentage is not in
     * the range [0..1], it won't modify current zoom percentage and zoom ratio. It is
     * applications' duty to clamp the zoomPercentage within [0..1].
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     * result contains the requested zoom percentage. It fails with
     * {@link OperationCanceledException} if there is newer value being set or camera is closed.
     * If percentage is out of range, it fails with {@link IllegalArgumentException}.
     */
    @NonNull
    ListenableFuture<Void> setZoomPercentage(@FloatRange(from = 0f, to = 1f) float percentage);

    /**
     * An exception representing a failure that the operation is canceled which might be caused by
     * a new value is set or camera is closed.
     *
     * <p>This is different from {@link CancellationException}. While
     * {@link CancellationException} means the {@link ListenableFuture} was cancelled by
     * {@link Future#cancel(boolean)}, {@link OperationCanceledException} occurs when there is
     * something wrong inside CameraControl and it has to cancel the operation.
     */
    final class OperationCanceledException extends Exception {
        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public OperationCanceledException(@NonNull String message) {
            super(message);
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public OperationCanceledException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }
}
