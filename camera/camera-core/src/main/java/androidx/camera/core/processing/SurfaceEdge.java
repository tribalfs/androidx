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

package androidx.camera.core.processing;

import static androidx.camera.core.impl.ImageOutputConfig.ROTATION_NOT_SPECIFIED;
import static androidx.camera.core.impl.utils.Threads.checkMainThread;
import static androidx.camera.core.impl.utils.executor.CameraXExecutors.directExecutor;
import static androidx.camera.core.impl.utils.executor.CameraXExecutors.mainThreadExecutor;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.SurfaceOutput;
import androidx.camera.core.SurfaceProcessor;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.SurfaceRequest.TransformationInfo;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.DeferrableSurface;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashSet;
import java.util.Set;

/**
 * An edge between two {@link Node} that is based on a {@link DeferrableSurface}.
 *
 * <p>This class contains a single {@link DeferrableSurface} with additional info such as size,
 * crop rect and transformation.
 *
 * <p>To connect a downstream node:
 * <ul>
 * <li>For external source, call {@link #createSurfaceRequest} and send the
 * {@link SurfaceRequest} to the app. For example, sending the {@link SurfaceRequest} to
 * PreviewView or Recorder.
 * <li>For internal source, call {@link #setProvider} with the {@link DeferrableSurface}
 * from another {@link UseCase}. For example, when sharing one stream to two use cases.
 * </ul>
 *
 * <p>To connect a upstream node:
 * <ul>
 * <li>For external source, call {@link #createSurfaceOutputFuture} and send the
 * {@link SurfaceOutput} to the app. For example, sending the {@link SurfaceOutput} to
 * {@link SurfaceProcessor}.
 * <li>For internal source, call {@link #getDeferrableSurface()} and set the
 * {@link DeferrableSurface} on {@link SessionConfig}.
 * </ul>
 *
 * TODO(b/241910577): add a #clearProvider method to reset the connection. This is useful if the
 *  downstream is a UseCase. When the UseCase is reset(video pause/resume), we need to replace
 *  the {@link DeferrableSurface} and maybe provide a different Surface.
 *
 * <p>For the full workflow, please see {@code SurfaceEdgeTest
 * #linkBothProviderAndConsumer_surfaceAndResultsArePropagatedE2E}
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class SurfaceEdge {

    private final Matrix mSensorToBufferTransform;
    private final boolean mHasCameraTransform;
    private final Rect mCropRect;
    private final boolean mMirroring;
    @CameraEffect.Targets
    private final int mTargets;
    private final Size mSize;
    // Guarded by main thread.
    private int mRotationDegrees;
    // Guarded by main thread.
    @Nullable
    private SurfaceOutputImpl mConsumerToNotify;

    // Guarded by main thread.
    private boolean mHasConsumer = false;
    // Guarded by main thread.
    @Nullable
    private SurfaceRequest mProviderSurfaceRequest;

    // TODO(b/259308680): recreate this variable when the downstream node changes.
    @NonNull
    private final SettableSurface mSettableSurface = new SettableSurface();

    private final Set<Runnable> mOnInvalidatedListeners = new HashSet<>();

    /**
     * Please see the getters to understand the parameters.
     */
    public SurfaceEdge(
            @CameraEffect.Targets int targets,
            @NonNull Size size,
            @NonNull Matrix sensorToBufferTransform,
            boolean hasCameraTransform,
            @NonNull Rect cropRect,
            int rotationDegrees,
            boolean mirroring) {
        mTargets = targets;
        mSize = size;
        mSensorToBufferTransform = sensorToBufferTransform;
        mHasCameraTransform = hasCameraTransform;
        mCropRect = cropRect;
        mRotationDegrees = rotationDegrees;
        mMirroring = mirroring;
    }

    /**
     * Adds a Runnable that gets invoked when the downstream pipeline is invalidated.
     *
     * <p>The added listeners are invoked when the downstream pipeline wants to replace the
     * previously provided {@link Surface}. For example, when {@link SurfaceRequest#invalidate()}
     * is called. When that happens, the edge should notify the upstream pipeline to get the new
     * Surface.
     */
    public void addOnInvalidatedListener(@NonNull Runnable onInvalidated) {
        mOnInvalidatedListeners.add(onInvalidated);
    }

    /**
     * Gets the {@link DeferrableSurface} for upstream nodes.
     */
    @NonNull
    public DeferrableSurface getDeferrableSurface() {
        return mSettableSurface;
    }

    /**
     * Sets the downstream {@link DeferrableSurface}.
     *
     * <p>Once connected, the value {@link #getDeferrableSurface()} and the provider will be
     * in sync on the following matters: 1) surface provision, 2) ref-counting, 3) closure and 4)
     * termination. See the list below for details:
     * <ul>
     * <li>Surface. the provider and the parent share the same Surface object.
     * <li>Ref-counting. The ref-count of the {@link #getDeferrableSurface()} represents whether
     * it's safe to release the Surface. The ref-count of the provider represents whether the
     * {@link #getDeferrableSurface()} is terminated. As long as the parent is not terminated, the
     * provider cannot release the surface because someone might be accessing the surface.
     * <li>Closure. When {@link #getDeferrableSurface()} is closed, if the surface is provided
     * via {@link SurfaceOutput}, it will invoke {@link SurfaceOutputImpl#requestClose()} to
     * decrease the ref-counter; if the surface is used by the camera-camera2, wait for the
     * ref-counter to go to zero on its own. For the provider, closing after providing the
     * surface has no effect; closing before providing the surface propagates the exception
     * upstream.
     * <li>Termination. On {@link #getDeferrableSurface()} termination, close the provider and
     * decrease the ref-count to notify that the Surface can be safely released. The provider
     * cannot be terminated before the {@link #getDeferrableSurface()} does.
     * </ul>
     *
     * <p>This method is for organizing the pipeline internally. For example, using the output of
     * one {@link UseCase} as the input of another {@link UseCase} for stream sharing.
     *
     * <p>It throws {@link IllegalStateException} if the current {@link SurfaceEdge}
     * already has a provider.
     *
     * @throws DeferrableSurface.SurfaceClosedException when the provider is already closed. This
     *                                                  should never happen.
     */
    @MainThread
    public void setProvider(@NonNull DeferrableSurface provider)
            throws DeferrableSurface.SurfaceClosedException {
        checkMainThread();
        mSettableSurface.setProvider(provider);
    }

    /**
     * Creates a {@link SurfaceRequest} that is linked to this {@link SurfaceEdge}.
     *
     * <p>The {@link SurfaceRequest} is for requesting a {@link Surface} from an external source
     * such as {@code PreviewView} or {@code VideoCapture}. {@link SurfaceEdge} uses the
     * {@link Surface} provided by {@link SurfaceRequest#provideSurface} as its source. For how
     * the ref-counting works, please see the Javadoc of {@link #setProvider}.
     *
     * <p>It throws {@link IllegalStateException} if the current {@link SurfaceEdge}
     * already has a provider.
     */
    @MainThread
    @NonNull
    public SurfaceRequest createSurfaceRequest(@NonNull CameraInternal cameraInternal) {
        return createSurfaceRequest(cameraInternal, null);
    }

    /**
     * Creates a {@link SurfaceRequest} that is linked to this {@link SurfaceEdge}.
     *
     * <p>The {@link SurfaceRequest} is for requesting a {@link Surface} from an external source
     * such as {@code PreviewView} or {@code VideoCapture}. {@link SurfaceEdge} uses the
     * {@link Surface} provided by {@link SurfaceRequest#provideSurface} as its source. For how
     * the ref-counting works, please see the Javadoc of {@link #setProvider}.
     *
     * <p>It throws {@link IllegalStateException} if the current {@link SurfaceEdge}
     * already has a provider.
     *
     * <p>This overload optionally allows allows specifying the expected frame rate range in which
     * the surface should operate.
     */
    @MainThread
    @NonNull
    public SurfaceRequest createSurfaceRequest(@NonNull CameraInternal cameraInternal,
            @Nullable Range<Integer> expectedFpsRange) {
        checkMainThread();
        // TODO(b/238230154) figure out how to support HDR.
        SurfaceRequest surfaceRequest = new SurfaceRequest(getSize(), cameraInternal,
                expectedFpsRange, () -> mainThreadExecutor().execute(this::invalidate));
        try {
            setProvider(surfaceRequest.getDeferrableSurface());
        } catch (DeferrableSurface.SurfaceClosedException e) {
            // This should never happen. We just created the SurfaceRequest. It can't be closed.
            throw new AssertionError("Surface is somehow already closed", e);
        }
        mProviderSurfaceRequest = surfaceRequest;
        notifyTransformationInfoUpdate();
        return surfaceRequest;
    }

    /**
     * Creates a {@link SurfaceOutput} that is linked to this {@link SurfaceEdge}.
     *
     * <p>The {@link SurfaceOutput} is for providing a surface to an external target such
     * as {@link SurfaceProcessor}.
     *
     * <p>This method returns a {@link ListenableFuture<SurfaceOutput>} that completes when the
     * {@link #getDeferrableSurface()} completes. The {@link SurfaceOutput} contains the surface
     * and ref-counts the {@link SurfaceEdge}.
     *
     * <p>Do not provide the {@link SurfaceOutput} to external target if the
     * {@link ListenableFuture} fails.
     *
     * @param inputSize       resolution of input image buffer
     * @param cropRect        crop rect of input image buffer
     * @param rotationDegrees expected rotation to the input image buffer
     * @param mirroring       expected mirroring to the input image buffer
     */
    @MainThread
    @NonNull
    public ListenableFuture<SurfaceOutput> createSurfaceOutputFuture(@NonNull Size inputSize,
            @NonNull Rect cropRect, int rotationDegrees, boolean mirroring) {
        checkMainThread();
        Preconditions.checkState(!mHasConsumer, "Consumer can only be linked once.");
        mHasConsumer = true;
        return Futures.transformAsync(mSettableSurface.getSurface(),
                surface -> {
                    Preconditions.checkNotNull(surface);
                    try {
                        mSettableSurface.incrementUseCount();
                    } catch (DeferrableSurface.SurfaceClosedException e) {
                        return Futures.immediateFailedFuture(e);
                    }
                    SurfaceOutputImpl surfaceOutputImpl = new SurfaceOutputImpl(surface,
                            getTargets(), getSize(), inputSize, cropRect, rotationDegrees,
                            mirroring);
                    surfaceOutputImpl.getCloseFuture().addListener(
                            mSettableSurface::decrementUseCount,
                            directExecutor());
                    mConsumerToNotify = surfaceOutputImpl;
                    return Futures.immediateFuture(surfaceOutputImpl);
                }, mainThreadExecutor());
    }

    /**
     * Closes the current connection and notifies that a new connection is ready.
     *
     * <p>Call this method to notify that the {@link Surface} previously provided via
     * {@link #createSurfaceRequest} or {@link #setProvider} should no longer be used. The
     * upstream pipeline should call {@link #getDeferrableSurface()} or
     * {@link #createSurfaceOutputFuture} to get the new {@link Surface}.
     *
     * <p>Only call this method when the surface provider is ready to provide a new {@link Surface}.
     * For example, when {@link SurfaceRequest#invalidate()} is invoked or when a downstream
     * {@link UseCase} resets.
     *
     * @see #close()
     */
    @MainThread
    public void invalidate() {
        checkMainThread();
        close();
        // TODO: recreate mSettableSurface.
        for (Runnable onInvalidated : mOnInvalidatedListeners) {
            onInvalidated.run();
        }
    }

    /**
     * Closes the current connection.
     *
     * <p>This method uses the mechanism in {@link DeferrableSurface} and/or
     * {@link SurfaceOutputImpl} to notify the upstream pipeline that the {@link Surface}
     * previously provided via {@link #createSurfaceRequest} or {@link #setProvider} should no
     * longer be used. The upstream pipeline will stops writing to the {@link Surface}, and the
     * downstream pipeline can choose to release the {@link Surface} once the writing stops.
     *
     * @see DeferrableSurface#close().
     * @see #invalidate()
     */
    @MainThread
    public final void close() {
        checkMainThread();
        mSettableSurface.close();
        if (mConsumerToNotify != null) {
            mConsumerToNotify.requestClose();
            mConsumerToNotify = null;
        }
    }

    /**
     * This field indicates that what purpose the {@link Surface} will be used for.
     */
    @CameraEffect.Targets
    public int getTargets() {
        return mTargets;
    }

    /**
     * The allocated size of the {@link Surface}.
     */
    @NonNull
    public Size getSize() {
        return mSize;
    }

    /**
     * Gets the {@link Matrix} represents the transformation from camera sensor to the current
     * {@link Surface}.
     *
     * <p>This value represents the transformation from sensor coordinates to the current buffer
     * coordinates, which is required to transform coordinates between UseCases. For example, in
     * AR, transforming the coordinates of the detected face in ImageAnalysis to coordinates in
     * PreviewView.
     *
     * <p> If the {@link SurfaceEdge} is directly connected to a camera output and its
     * aspect ratio matches the aspect ratio of the sensor, this value is usually an identity
     * matrix, with the exception of device quirks. Each time a intermediate {@link Node}
     * transforms the image buffer, it has to append the same transformation to this
     * {@link Matrix} and pass it to the downstream {@link Node}.
     */
    @NonNull
    public Matrix getSensorToBufferTransform() {
        return mSensorToBufferTransform;
    }

    /**
     * Whether the current {@link Surface} contains the camera transformation info.
     *
     * <p>Camera2 writes the camera transform to the {@link Surface}. The info is typically used by
     * {@link SurfaceView}/{@link TextureView} to correct the preview. Once it's buffer copied by
     * post-processing, the info is lost. The app (e.g. PreviewView) needs to handle the
     * transformation differently based on this flag.
     */
    public boolean hasCameraTransform() {
        return mHasCameraTransform;
    }

    // The following values represent the scenario that if this buffer is given directly to the
    // app, these are the additional transformation needs to be applied by the app. Every time we
    // make a change to the buffer, these values need to be updated as well.

    /**
     * Gets the crop rect based on {@link UseCase} config.
     */
    @NonNull
    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * Gets the clockwise rotation degrees based on {@link UseCase} config.
     */
    public int getRotationDegrees() {
        return mRotationDegrees;
    }

    /**
     * Sets the rotation degrees.
     *
     * <p>If the surface provider is created via {@link #createSurfaceRequest(CameraInternal)}, the
     * returned SurfaceRequest will receive the rotation update by
     * {@link SurfaceRequest.TransformationInfoListener}.
     */
    @MainThread
    public void setRotationDegrees(int rotationDegrees) {
        checkMainThread();
        if (mRotationDegrees == rotationDegrees) {
            return;
        }
        mRotationDegrees = rotationDegrees;
        notifyTransformationInfoUpdate();
    }

    @MainThread
    private void notifyTransformationInfoUpdate() {
        checkMainThread();
        if (mProviderSurfaceRequest != null) {
            mProviderSurfaceRequest.updateTransformationInfo(
                    TransformationInfo.of(mCropRect, mRotationDegrees, ROTATION_NOT_SPECIFIED,
                            hasCameraTransform()));
        }
    }

    /**
     * Gets whether the buffer needs to be horizontally mirrored based on {@link UseCase} config.
     */
    public boolean getMirroring() {
        return mMirroring;
    }

    /**
     * A {@link DeferrableSurface} that sets another {@link DeferrableSurface} as the source.
     *
     * <p>This class provides mechanisms to link an {@link DeferrableSurface}, and propagates
     * Surface releasing/closure to the {@link DeferrableSurface}.
     */
    static class SettableSurface extends DeferrableSurface {

        final ListenableFuture<Surface> mSurfaceFuture = CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCompleter = completer;
                    return "SettableFuture hashCode: " + hashCode();
                });

        CallbackToFutureAdapter.Completer<Surface> mCompleter;

        // Guarded by main thread.
        private boolean mHasProvider = false;

        @NonNull
        @Override
        protected ListenableFuture<Surface> provideSurface() {
            return mSurfaceFuture;
        }

        /**
         * Sets the {@link DeferrableSurface} that provides the surface.
         *
         * @see SurfaceEdge#setProvider(DeferrableSurface)
         */
        @MainThread
        public void setProvider(@NonNull DeferrableSurface provider) throws SurfaceClosedException {
            checkMainThread();
            Preconditions.checkState(!mHasProvider, "Provider can only be set once.");
            mHasProvider = true;
            Futures.propagate(provider.getSurface(), mCompleter);
            provider.incrementUseCount();
            getTerminationFuture().addListener(() -> {
                provider.decrementUseCount();
                // TODO(b/259308680): only close the provider if it's from the SurfaceRequest
                //  created by SurfaceEdge. If the provider comes from another UseCase, it's the
                //  UseCase's responsibility to close it when its lifecycle ends.
                provider.close();
            }, directExecutor());
        }
    }
}
