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

import androidx.annotation.NonNull;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/**
 * A {@link DeferrableSurface} wraps around user provided {@link Preview.PreviewSurfaceCallback}
 * and {@link Executor}.
 */
final class CallbackDeferrableSurface extends DeferrableSurface implements SurfaceTextureHolder {

    @NonNull
    private ListenableFuture<Surface> mSurfaceFuture;
    @NonNull
    private Preview.PreviewSurfaceCallback mPreviewSurfaceCallback;
    @NonNull
    private Executor mCallbackExecutor;

    CallbackDeferrableSurface(@NonNull Size resolution, @NonNull Executor callbackExecutor,
            @NonNull Preview.PreviewSurfaceCallback previewSurfaceCallback) {
        mCallbackExecutor = callbackExecutor;
        mPreviewSurfaceCallback = previewSurfaceCallback;
        // Re-wrap user's ListenableFuture with user's executor.
        mSurfaceFuture = CallbackToFutureAdapter.getFuture(
                completer -> {
                    callbackExecutor.execute(() -> {
                        // TODO(b/117519540): pass the image format to user.
                        Futures.propagate(previewSurfaceCallback.createSurfaceFuture(resolution, 0),
                                completer);

                    });
                    return "GetSurfaceFutureWithExecutor";
                });
    }

    @Override
    ListenableFuture<Surface> provideSurface() {
        return mSurfaceFuture;
    }

    @NonNull
    @Override
    public SurfaceTexture getSurfaceTexture() {
        throw new IllegalStateException("getSurfaceTexture() is a deprecated method that is not "
                + "supported by UserDeferrableSurface.");
    }

    /**
     * Notifies user that the {@link Surface} can be safely released.
     */
    @Override
    public void release() {
        setOnSurfaceDetachedListener(mCallbackExecutor,
                () -> mPreviewSurfaceCallback.onSafeToRelease(mSurfaceFuture));
    }
}
