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

import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.Futures;

/**
 * This class creates implementations of PreviewSurfaceProvider that provide Surfaces that have been
 * pre-configured for specific work flows.
 */
public final class PreviewSurfaceProviders {

    private static final String TAG = "PreviewSurfaceProviders";

    private PreviewSurfaceProviders() {
    }

    /**
     * Creates a {@link Preview.PreviewSurfaceProvider} that is backed by a {@link SurfaceTexture}.
     *
     * <p>This is a convenience method for creating a {@link Preview.PreviewSurfaceProvider}
     * whose {@link Surface} is backed by a {@link SurfaceTexture}. The returned
     * {@link Preview.PreviewSurfaceProvider} is responsible for creating the
     * {@link SurfaceTexture}. The {@link SurfaceTexture} may not be safe to use with
     * {@link TextureView}
     * Example:
     *
     * <pre><code>
     * preview.setPreviewSurfaceProvider(createPreviewSurfaceProvider(
     *         new PreviewSurfaceProviders.SurfaceTextureCallback() {
     *             &#64;Override
     *             public void onSurfaceTextureReady(@NonNull SurfaceTexture surfaceTexture) {
     *                 // Use the SurfaceTexture
     *             }
     *
     *             &#64;Override
     *             public void onSafeToRelease(@NonNull SurfaceTexture surfaceTexture) {
     *                 surfaceTexture.release();
     *             }
     *         }));
     * </code></pre>
     *
     * @param surfaceTextureCallback callback called when the SurfaceTexture is ready to be
     *                               set/released.
     * @return a {@link Preview.PreviewSurfaceProvider} to be used with
     * {@link Preview#setPreviewSurfaceProvider(Preview.PreviewSurfaceProvider)}.
     */
    @NonNull
    public static Preview.PreviewSurfaceProvider createSurfaceTextureProvider(
            @NonNull SurfaceTextureCallback surfaceTextureCallback) {
        return (resolution, safeToCancelFuture) -> {
            SurfaceTexture surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(resolution.getWidth(),
                    resolution.getHeight());
            surfaceTexture.detachFromGLContext();
            surfaceTextureCallback.onSurfaceTextureReady(surfaceTexture, resolution);
            Surface surface = new Surface(surfaceTexture);
            safeToCancelFuture.addListener(() -> {
                surface.release();
                surfaceTextureCallback.onSafeToRelease(surfaceTexture);
            }, CameraXExecutors.directExecutor());
            return Futures.immediateFuture(surface);
        };
    }

    /**
     * Callback that is called when the {@link SurfaceTexture} is ready to be set/released.
     *
     * <p> Implement this interface to receive the updates on  {@link SurfaceTexture} used in
     * {@link Preview}. See {@link #createSurfaceTextureProvider(SurfaceTextureCallback)} for
     * code example.
     */
    public interface SurfaceTextureCallback {

        /**
         * Called when a {@link Preview} {@link SurfaceTexture} has been created and is ready to
         * be used by the application.
         *
         * <p> This is called when the preview {@link SurfaceTexture} is created and ready. The
         * most common usage is to set it to a {@link TextureView}. Example:
         * <pre><code>textureView.setSurfaceTexture(surfaceTexture)</code></pre>.
         *
         * <p> To display the {@link SurfaceTexture} without a {@link TextureView},
         * {@link SurfaceTexture#getTransformMatrix(float[])} can be used to transform the
         * preview to natural orientation. For {@link TextureView}, it handles the transformation
         * automatically so that no additional work is needed.
         *
         * @param surfaceTexture {@link SurfaceTexture} created for {@link Preview}.
         * @param resolution     the resolution of the created {@link SurfaceTexture}.
         */
        void onSurfaceTextureReady(@NonNull SurfaceTexture surfaceTexture,
                @NonNull Size resolution);

        /**
         * Called when the {@link SurfaceTexture} is safe to be released.
         *
         * <p> This method is called when the {@link SurfaceTexture} previously provided in
         * {@link #onSurfaceTextureReady(SurfaceTexture, Size)} is no longer being used by the
         * camera system, and it's safe to be released during or after this is called. The
         * implementer is responsible to release the {@link SurfaceTexture} when it's also no
         * longer being used by the app.
         *
         * @param surfaceTexture the {@link SurfaceTexture} to be released.
         */
        void onSafeToRelease(@NonNull SurfaceTexture surfaceTexture);
    }
}
