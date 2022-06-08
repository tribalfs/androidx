/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.viewfinder;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.viewfinder.internal.surface.ViewfinderSurface;
import androidx.camera.viewfinder.internal.utils.Logger;
import androidx.camera.viewfinder.internal.utils.executor.CameraExecutors;
import androidx.camera.viewfinder.internal.utils.futures.FutureCallback;
import androidx.camera.viewfinder.internal.utils.futures.Futures;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The request to get a {@link Surface} to display camera feed.
 *
 * <p> This request contains requirements for the surface resolution and camera
 * device information from {@link CameraCharacteristics}.
 *
 * <p> Calling {@link CameraViewfinder#requestSurfaceAsync(ViewfinderSurfaceRequest)} with this
 * request will send the request to the surface provider, which is either a {@link TextureView} or
 * {@link SurfaceView} and get a {@link ListenableFuture} of {@link Surface}.
 *
 * <p> Calling {@link ViewfinderSurfaceRequest#markSurfaceSafeToRelease()} will notify the
 * surface provider that the surface is not needed and related resources can be released.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public class ViewfinderSurfaceRequest {

    private static final String TAG = "ViewfinderSurfaceRequest";

    private final boolean mIsLegacyDevice;
    private final boolean mIsFrontCamera;
    private final int mSensorOrientation;
    @NonNull private final Size mResolution;
    @NonNull private final ViewfinderSurface mInternalViewfinderSurface;
    @NonNull private final CallbackToFutureAdapter.Completer<Void> mRequestCancellationCompleter;
    @NonNull private final ListenableFuture<Void> mSessionStatusFuture;
    @NonNull private final CallbackToFutureAdapter.Completer<Surface> mSurfaceCompleter;

    @SuppressWarnings("WeakerAccess") /*synthetic accessor */
    @NonNull
    final ListenableFuture<Surface> mSurfaceFuture;

    /**
     * Creates a new surface request with surface resolution and camera characteristics.
     *
     * <p>The resolution given here will be the default resolution of the Surface returned by
     * {@link CameraViewfinder#requestSurfaceAsync(ViewfinderSurfaceRequest)}, which can then be
     * passed to the camera API to set the camera viewfinder resolution.
     *
     * @param resolution The requested surface resolution.
     * @param cameraCharacteristics The {@link CameraCharacteristics} to get device information
     *                              e.g. hardware level, lens facing, sensor orientation, etc,.
     */
    public ViewfinderSurfaceRequest(
            @NonNull Size resolution,
            @NonNull CameraCharacteristics cameraCharacteristics) {
        this(resolution,
                cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                        == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                        == CameraCharacteristics.LENS_FACING_FRONT,
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
    }

    /**
     * Creates a new surface request with surface resolution, view display and camera device
     * information.
     *
     * @param resolution The requested surface resolution. It is the output surface size
     *                   the camera is configured with, instead of {@link CameraViewfinder}
     *                   view size.
     * {@link CameraViewfinder} view
     * @param isLegacyDevice The device hardware level is legacy or not.
     * @param isFrontCamera The camera is front facing or not.
     * @param sensorOrientation THe camera sensor orientation.
     */
    private ViewfinderSurfaceRequest(
            @NonNull Size resolution,
            boolean isLegacyDevice,
            boolean isFrontCamera,
            int sensorOrientation) {
        mResolution = resolution;
        mIsLegacyDevice = isLegacyDevice;
        mIsFrontCamera = isFrontCamera;
        mSensorOrientation = sensorOrientation;

        // To ensure concurrency and ordering, operations are chained. Completion can only be
        // triggered externally by the top-level completer (mSurfaceCompleter). The other future
        // completers are only completed by callbacks set up within the constructor of this class
        // to ensure correct ordering of events.

        // Cancellation listener must be called last to ensure the result can be retrieved from
        // the session listener.
        String surfaceRequestString =
                "SurfaceRequest[size: " + resolution + ", id: " + this.hashCode() + "]";
        AtomicReference<CallbackToFutureAdapter.Completer<Void>> cancellationCompleterRef =
                new AtomicReference<>(null);
        ListenableFuture<Void> requestCancellationFuture =
                CallbackToFutureAdapter.getFuture(completer -> {
                    cancellationCompleterRef.set(completer);
                    return surfaceRequestString + "-cancellation";
                });
        CallbackToFutureAdapter.Completer<Void> requestCancellationCompleter =
                Preconditions.checkNotNull(cancellationCompleterRef.get());
        mRequestCancellationCompleter = requestCancellationCompleter;

        // Surface session status future completes and is responsible for finishing the
        // cancellation listener.
        AtomicReference<CallbackToFutureAdapter.Completer<Void>> sessionStatusCompleterRef =
                new AtomicReference<>(null);
        mSessionStatusFuture = CallbackToFutureAdapter.getFuture(completer -> {
            sessionStatusCompleterRef.set(completer);
            return surfaceRequestString + "-status";
        });

        Futures.addCallback(mSessionStatusFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                // Cancellation didn't occur, so complete the cancellation future. There
                // shouldn't ever be any standard listeners on this future, so nothing should be
                // invoked.
                Preconditions.checkState(requestCancellationCompleter.set(null));
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof RequestCancelledException) {
                    // Cancellation occurred. Notify listeners.
                    Preconditions.checkState(requestCancellationFuture.cancel(false));
                } else {
                    // Cancellation didn't occur, complete the future so cancellation listeners
                    // are not invoked.
                    Preconditions.checkState(requestCancellationCompleter.set(null));
                }
            }
        }, CameraExecutors.directExecutor());

        // Create the surface future/completer. This will be used to complete the rest of the
        // future chain and can be set externally via SurfaceRequest methods.
        CallbackToFutureAdapter.Completer<Void> sessionStatusCompleter =
                Preconditions.checkNotNull(sessionStatusCompleterRef.get());
        AtomicReference<CallbackToFutureAdapter.Completer<Surface>> surfaceCompleterRef =
                new AtomicReference<>(null);
        mSurfaceFuture = CallbackToFutureAdapter.getFuture(completer -> {
            surfaceCompleterRef.set(completer);
            return surfaceRequestString + "-Surface";
        });
        mSurfaceCompleter = Preconditions.checkNotNull(surfaceCompleterRef.get());

        // Create the viewfinder surface which will be used for communicating when the
        // camera and consumer are done using the surface. Note this anonymous inner class holds
        // an implicit reference to the ViewfinderSurfaceRequest. This is by design, and ensures the
        // ViewfinderSurfaceRequest and all contained future completers will not be garbage
        // collected as long as the ViewfinderSurface is referenced externally (via
        // getViewfinderSurface()).
        mInternalViewfinderSurface = new ViewfinderSurface() {
            @SuppressLint("SyntheticAccessor")
            @NonNull
            @Override
            protected ListenableFuture<Surface> provideSurfaceAsync() {
                Logger.d(TAG,
                        "mInternalViewfinderSurface + " + mInternalViewfinderSurface + " "
                                + "provideSurface");
                return mSurfaceFuture;
            }
        };
        ListenableFuture<Void> terminationFuture =
                mInternalViewfinderSurface.getTerminationFuture();

        // Propagate surface completion to the session future.
        Futures.addCallback(mSurfaceFuture, new FutureCallback<Surface>() {
            @Override
            public void onSuccess(@Nullable Surface surface) {
                // On successful setting of a surface, defer completion of the session future to
                // the ViewfinderSurface termination future. Once that future completes, then it
                // is safe to release the Surface and associated resources.

                Futures.propagate(terminationFuture, sessionStatusCompleter);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                // Translate cancellation into a SurfaceRequestCancelledException. Other
                // exceptions mean either the request was completed via willNotProvideSurface() or a
                // programming error occurred. In either case, the user will never see the
                // session future (an immediate future will be returned instead), so complete the
                // future so cancellation listeners are never called.
                if (t instanceof CancellationException) {
                    Preconditions.checkState(sessionStatusCompleter.setException(
                            new RequestCancelledException(
                                    surfaceRequestString + " cancelled.", t)));
                } else {
                    sessionStatusCompleter.set(null);
                }
            }
        }, CameraExecutors.directExecutor());

        // If the viewfinder surface is terminated, there are two cases:
        // 1. The surface has not yet been provided to the camera (or marked as 'will not
        //    complete'). Treat this as if the surface request has been cancelled.
        // 2. The surface was already provided to the camera. In this case the camera is now
        //    finished with the surface, so cancelling the surface future below will be a no-op.
        terminationFuture.addListener(() -> {
            Logger.d(TAG,
                    "mInternalViewfinderSurface + " + mInternalViewfinderSurface + " "
                            + "terminateFuture triggered");
            mSurfaceFuture.cancel(true);
        }, CameraExecutors.directExecutor());
    }

    @Override
    @SuppressWarnings("GenericException") // super.finalize() throws Throwable
    protected void finalize() throws Throwable {
        mInternalViewfinderSurface.close();
        super.finalize();
    }

    /**
     * Returns the resolution of the requested {@link Surface}.
     *
     * The surface which fulfills this request must have the resolution specified here in
     * order to fulfill the resource requirements of the camera.
     *
     * @return The guaranteed supported resolution.
     * @see SurfaceTexture#setDefaultBufferSize(int, int)
     */
    @NonNull
    public Size getResolution() {
        return mResolution;
    }

    /**
     * Returns the sensor orientation.
     *
     * @return The sensor orientation.
     */
    public int getSensorOrientation() {
        return mSensorOrientation;
    }

    /**
     * Returns the status of camera lens facing.
     *
     * @return True if front camera, otherwise false.
     */
    public boolean isFrontCamera() {
        return mIsFrontCamera;
    }

    /**
     * Returns the status of camera hardware level.
     *
     * @return True if legacy device, otherwise false.
     */
    public boolean isLegacyDevice() {
        return mIsLegacyDevice;
    }

    /**
     * Closes the viewfinder surface to mark it as safe to release.
     *
     * <p> This method should be called by the user when the requested surface is not needed and
     * related resources can be released.
     */
    public void markSurfaceSafeToRelease() {
        mInternalViewfinderSurface.close();
    }

    @NonNull
    ViewfinderSurface getViewfinderSurface() {
        return mInternalViewfinderSurface;
    }

    @SuppressLint("PairedRegistration")
    void addRequestCancellationListener(@NonNull Executor executor,
            @NonNull Runnable listener) {
        mRequestCancellationCompleter.addCancellationListener(listener, executor);
    }

    /**
     * Completes the request for a {@link Surface} if it has not already been
     * completed or cancelled.
     *
     * <p>Once the camera no longer needs the provided surface, the {@code resultListener} will be
     * invoked with a {@link Result} containing {@link Result#RESULT_SURFACE_USED_SUCCESSFULLY}.
     * At this point it is safe to release the surface and any underlying resources. Releasing
     * the surface before receiving this signal may cause undesired behavior on lower API levels.
     *
     * <p>If the request is cancelled by the camera before successfully attaching the
     * provided surface to the camera, then the {@code resultListener} will be invoked with a
     * {@link Result} containing {@link Result#RESULT_REQUEST_CANCELLED}.
     *
     * <p>If the request was previously completed via {@link #willNotProvideSurface()}, then
     * {@code resultListener} will be invoked with a {@link Result} containing
     * {@link Result#RESULT_WILL_NOT_PROVIDE_SURFACE}.
     *
     * <p>Upon returning from this method, the surface request is guaranteed to be complete.
     * However, only the {@code resultListener} provided to the first invocation of this method
     * should be used to track when the provided {@link Surface} is no longer in use by the
     * camera, as subsequent invocations will always invoke the {@code resultListener} with a
     * {@link Result} containing {@link Result#RESULT_SURFACE_ALREADY_PROVIDED}.
     *
     * @param surface        The surface which will complete the request.
     * @param executor       Executor used to execute the {@code resultListener}.
     * @param resultListener Listener used to track how the surface is used by the camera in
     *                       response to being provided by this method.
     *
     */
    void provideSurface(
            @NonNull Surface surface,
            @NonNull Executor executor,
            @NonNull Consumer<Result> resultListener) {
        if (mSurfaceCompleter.set(surface) || mSurfaceFuture.isCancelled()) {
            // Session will be pending completion (or surface request was cancelled). Return the
            // session future.
            Futures.addCallback(mSessionStatusFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void result) {
                    resultListener.accept(Result.of(Result.RESULT_SURFACE_USED_SUCCESSFULLY,
                            surface));
                }

                @Override
                public void onFailure(Throwable t) {
                    Preconditions.checkState(t instanceof RequestCancelledException, "Camera "
                            + "surface session should only fail with request "
                            + "cancellation. Instead failed due to:\n" + t);
                    resultListener.accept(Result.of(Result.RESULT_REQUEST_CANCELLED, surface));
                }
            }, executor);
        } else {
            // Surface request is already complete
            Preconditions.checkState(mSurfaceFuture.isDone());
            try {
                mSurfaceFuture.get();
                // Getting this far means the surface was already provided.
                executor.execute(
                        () -> resultListener.accept(
                                Result.of(Result.RESULT_SURFACE_ALREADY_PROVIDED, surface)));
            } catch (InterruptedException | ExecutionException e) {
                executor.execute(
                        () -> resultListener.accept(
                                Result.of(Result.RESULT_WILL_NOT_PROVIDE_SURFACE, surface)));
            }
        }
    }

    /**
     * Signals that the request will never be fulfilled.
     *
     * <p>This may be called in the case where the application may be shutting down and a
     * surface will never be produced to fulfill the request.
     *
     * <p>This will be called by CameraViewfinder as soon as it is known that the request will not
     * be fulfilled. Failure to complete the SurfaceRequest via {@code willNotProvideSurface()}
     * or {@link #provideSurface(Surface, Executor, Consumer)} may cause long delays in shutting
     * down the camera.
     *
     * <p>Upon returning from this method, the request is guaranteed to be complete, regardless
     * of the return value. If the request was previously successfully completed by
     * {@link #provideSurface(Surface, Executor, Consumer)}, invoking this method will return
     * {@code false}, and will have no effect on how the surface is used by the camera.
     *
     * @return {@code true} if this call to {@code willNotProvideSurface()} successfully
     * completes the request, i.e., the request has not already been completed via
     * {@link #provideSurface(Surface, Executor, Consumer)} or by a previous call to
     * {@code willNotProvideSurface()} and has not already been cancelled by the camera.
     *
     */
    boolean willNotProvideSurface() {
        return mSurfaceCompleter.setException(
                new ViewfinderSurface.SurfaceUnavailableException("Surface request "
                        + "will not complete."));
    }

    static final class RequestCancelledException extends RuntimeException {
        RequestCancelledException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result of providing a surface to a {@link ViewfinderSurfaceRequest} via
     * {@link #provideSurface(Surface, Executor, Consumer)}.
     *
     */
    @AutoValue
    abstract static class Result {

        /**
         * Possible result codes.
         *
         * @hide
         */
        @IntDef({RESULT_SURFACE_USED_SUCCESSFULLY, RESULT_REQUEST_CANCELLED, RESULT_INVALID_SURFACE,
                RESULT_SURFACE_ALREADY_PROVIDED, RESULT_WILL_NOT_PROVIDE_SURFACE})
        @Retention(RetentionPolicy.SOURCE)
        @RestrictTo(Scope.LIBRARY)
        public @interface ResultCode {
        }

        /**
         * Provided surface was successfully used by the camera and eventually detached once no
         * longer needed by the camera.
         *
         * <p>This result denotes that it is safe to release the {@link Surface} and any underlying
         * resources.
         *
         * <p>For compatibility reasons, the {@link Surface} object should not be reused by
         * future {@link ViewfinderSurfaceRequest SurfaceRequests}, and a new surface should be
         * created instead.
         */
        public static final int RESULT_SURFACE_USED_SUCCESSFULLY = 0;

        /**
         * Provided surface was never attached to the camera due to the
         * {@link ViewfinderSurfaceRequest} being cancelled by the camera.
         *
         * <p>It is safe to release or reuse {@link Surface}, assuming it was not previously
         * attached to a camera via {@link #provideSurface(Surface, Executor, Consumer)}. If
         * reusing the surface for a future surface request, it should be verified that the
         * surface still matches the resolution specified by
         * {@link ViewfinderSurfaceRequest#getResolution()}.
         */
        public static final int RESULT_REQUEST_CANCELLED = 1;

        /**
         * Provided surface could not be used by the camera.
         *
         * <p>This is likely due to the {@link Surface} being closed prematurely or the resolution
         * of the surface not matching the resolution specified by
         * {@link ViewfinderSurfaceRequest#getResolution()}.
         */
        public static final int RESULT_INVALID_SURFACE = 2;

        /**
         * Surface was not attached to the camera through this invocation of
         * {@link #provideSurface(Surface, Executor, Consumer)} due to the
         * {@link ViewfinderSurfaceRequest} already being complete with a surface.
         *
         * <p>The {@link ViewfinderSurfaceRequest} has already been completed by a previous
         * invocation of {@link #provideSurface(Surface, Executor, Consumer)}.
         *
         * <p>It is safe to release or reuse the {@link Surface}, assuming it was not previously
         * attached to a camera via {@link #provideSurface(Surface, Executor, Consumer)}.
         */
        public static final int RESULT_SURFACE_ALREADY_PROVIDED = 3;

        /**
         * Surface was not attached to the camera through this invocation of
         * {@link #provideSurface(Surface, Executor, Consumer)} due to the
         * {@link ViewfinderSurfaceRequest} already being marked as "will not provide surface".
         *
         * <p>The {@link ViewfinderSurfaceRequest} has already been marked as 'will not provide
         * surface' by a previous invocation of {@link #willNotProvideSurface()}.
         *
         * <p>It is safe to release or reuse the {@link Surface}, assuming it was not previously
         * attached to a camera via {@link #provideSurface(Surface, Executor, Consumer)}.
         */
        public static final int RESULT_WILL_NOT_PROVIDE_SURFACE = 4;

        /**
         * Creates a result from the given result code and surface.
         *
         * <p>Can be used to compare to results returned to {@code resultListener} in
         * {@link #provideSurface(Surface, Executor, Consumer)}.
         *
         * @param code    One of {@link #RESULT_SURFACE_USED_SUCCESSFULLY},
         *                {@link #RESULT_REQUEST_CANCELLED}, {@link #RESULT_INVALID_SURFACE},
         *                {@link #RESULT_SURFACE_ALREADY_PROVIDED}, or
         *                {@link #RESULT_WILL_NOT_PROVIDE_SURFACE}.
         * @param surface The {@link Surface} used to complete the {@link ViewfinderSurfaceRequest}.
         */
        @NonNull
        static Result of(@ResultCode int code, @NonNull Surface surface) {
            return new AutoValue_ViewfinderSurfaceRequest_Result(code, surface);
        }

        /**
         * Returns the result of invoking {@link #provideSurface(Surface, Executor, Consumer)}
         * with the surface from {@link #getSurface()}.
         *
         * @return One of {@link #RESULT_SURFACE_USED_SUCCESSFULLY},
         * {@link #RESULT_REQUEST_CANCELLED}, {@link #RESULT_INVALID_SURFACE}, or
         * {@link #RESULT_SURFACE_ALREADY_PROVIDED}, {@link #RESULT_WILL_NOT_PROVIDE_SURFACE}.
         */
        @ResultCode
        public abstract int getResultCode();

        /**
         * The surface used to complete a {@link ViewfinderSurfaceRequest} with
         * {@link #provideSurface(Surface, Executor, Consumer)}.
         *
         * @return the surface.
         */
        @NonNull
        public abstract Surface getSurface();

        // Ensure Result can't be subclassed outside the package
        Result() {
        }
    }
}
