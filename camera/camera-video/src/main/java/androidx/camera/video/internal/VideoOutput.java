/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.video.internal;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.impl.ConstantObservable;
import androidx.camera.core.impl.Observable;
import androidx.core.util.Consumer;

import java.util.concurrent.Executor;

/**
 * A class that will produce video data from a {@link Surface}.
 *
 * <p>Implementations will provide a {@link Surface} to a video frame producer via the
 * {@link SurfaceRequest} sent to {@link #onSurfaceRequested(SurfaceRequest)}.
 *
 * <p>The type of video data produced by a video output and API for saving or communicating that
 * data is left to the implementation.
 */
public interface VideoOutput {
    /**
     * A state which represents whether the video output is ready for frame streaming.
     *
     * <p>This is used in the observable returned by {@link #getStreamState()} to inform producers
     * that they can start or stop producing frames.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    enum StreamState {
        /** The video output is active and ready to receive frames. */
        ACTIVE,
        /** The video output is inactive and any frames sent will be discarded. */
        INACTIVE;

        static final Observable<StreamState> ALWAYS_ACTIVE_OBSERVABLE =
                ConstantObservable.withValue(StreamState.ACTIVE);
    }

    /**
     * Called when a new {@link Surface} has been requested by a video frame producer.
     *
     * <p>This is called when a video frame producer is ready to receive a surface that it can
     * use to send video frames to the video output.
     * The video frame producer may repeatedly request a surface more than once, but only the
     * latest {@link SurfaceRequest} should be considered active. All previous surface requests
     * will complete by sending a {@link androidx.camera.core.SurfaceRequest.Result} to the
     * consumer passed to {@link SurfaceRequest#provideSurface(Surface, Executor, Consumer)}.
     *
     * <p>A request is considered active until it is
     * {@linkplain SurfaceRequest#provideSurface(Surface, Executor, androidx.core.util.Consumer)
     * fulfilled}, {@linkplain SurfaceRequest#willNotProvideSurface() marked as 'will not
     * complete'}, or
     * {@linkplain SurfaceRequest#addRequestCancellationListener(Executor, Runnable) cancelled
     * by the video frame producer}. After one of these conditions occurs, a request is considered
     * completed.
     *
     * <p>Once a request is successfully completed, it is guaranteed that if a new request is
     * made, the {@link Surface} used to fulfill the previous request will be detached from the
     * video frame producer and {@link SurfaceRequest#provideSurface(Surface, Executor, Consumer)}
     * will be invoked with a {@link androidx.camera.core.SurfaceRequest.Result} containing
     * {@link androidx.camera.core.SurfaceRequest.Result#RESULT_SURFACE_USED_SUCCESSFULLY}.
     *
     * @param request the request for a surface which contains the requirements of the
     *                surface and methods for completing the request.
     */
    void onSurfaceRequested(@NonNull SurfaceRequest request);

    /**
     * Observable state which can be used to determine if the video output is ready for streaming.
     *
     * <p>When the StreamState is ACTIVE, the {@link Surface} provided to
     * {@link #onSurfaceRequested} should be ready to consume frames.
     *
     * <p>When the StreamState is INACTIVE, any frames drawn to the {@link Surface} may be
     * discarded.
     *
     * <p>This can be used by video producers to determine when frames should be drawn to the
     * {@link Surface} to ensure they are not doing excess work.
     *
     * <p>Implementers of the VideoOutput interface should consider overriding this method
     * as a performance improvement. The default implementation returns an {@link Observable}
     * which is always {@link StreamState#ACTIVE}.
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY)
    default Observable<StreamState> getStreamState() {
        return StreamState.ALWAYS_ACTIVE_OBSERVABLE;
    }
}
