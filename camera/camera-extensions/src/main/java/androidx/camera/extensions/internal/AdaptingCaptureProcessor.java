/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.extensions.internal;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.impl.Camera2CameraCaptureResultConverter;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.impl.CameraCaptureResult;
import androidx.camera.core.impl.CameraCaptureResults;
import androidx.camera.core.impl.CaptureProcessor;
import androidx.camera.core.impl.ImageProxyBundle;
import androidx.camera.extensions.impl.CaptureProcessorImpl;
import androidx.camera.extensions.impl.ExtenderStateListener;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link CaptureProcessor} that calls a vendor provided implementation.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public final class AdaptingCaptureProcessor implements CaptureProcessor, VendorProcessor {
    @NonNull
    private final CaptureProcessorImpl mImpl;
    @Nullable
    private volatile Surface mSurface;
    private volatile int mImageFormat;
    private volatile Size mResolution;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mActive = false;

    private BlockingCloseAccessCounter mAccessCounter = new BlockingCloseAccessCounter();

    public AdaptingCaptureProcessor(@NonNull CaptureProcessorImpl impl) {
        mImpl = impl;
    }

    /**
     * Invoked after
     * {@link ExtenderStateListener#onInit(String, CameraCharacteristics, Context)}()} to
     * initialize the processor.
     */
    @Override
    public void onInit() {
        if (!mAccessCounter.tryIncrement()) {
            return;
        }

        // Delay the onOutputSurface / onImageFormatUpdate/ onResolutionUpdate calls because on
        // some OEM devices, these CaptureProcessImpl configuration should be performed only after
        // onInit. Otherwise it will cause black preview issue.
        try {
            mImpl.onOutputSurface(mSurface, mImageFormat);
            mImpl.onImageFormatUpdate(mImageFormat);
            mImpl.onResolutionUpdate(mResolution);
        } finally {
            mAccessCounter.decrement();
        }

        synchronized (mLock) {
            mActive = true;
        }
    }

    @Override
    public void onDeInit() {
        synchronized (mLock) {
            mActive = false;
        }
    }

    @Override
    public void close() {
        mAccessCounter.destroyAndWaitForZeroAccess();
        mSurface = null;
        mResolution = null;
    }

    @Override
    public void onOutputSurface(@NonNull Surface surface, int imageFormat) {
        mSurface = surface;
        mImageFormat = imageFormat;
    }

    @Override
    public void onResolutionUpdate(@NonNull Size size) {
        mResolution = size;
    }

    @Override
    @ExperimentalGetImage
    public void process(@NonNull ImageProxyBundle bundle) {
        synchronized (mLock) {
            if (!mActive) {
                return;
            }

            List<Integer> ids = bundle.getCaptureIds();

            Map<Integer, Pair<Image, TotalCaptureResult>> bundleMap = new HashMap<>();

            for (Integer id : ids) {
                ListenableFuture<ImageProxy> imageProxyListenableFuture = bundle.getImageProxy(id);
                try {
                    ImageProxy imageProxy = imageProxyListenableFuture.get(5, TimeUnit.SECONDS);
                    Image image = imageProxy.getImage();
                    if (image == null) {
                        return;
                    }

                    ImageInfo imageInfo = imageProxy.getImageInfo();

                    CameraCaptureResult result =
                            CameraCaptureResults.retrieveCameraCaptureResult(imageInfo);
                    if (result == null) {
                        return;
                    }

                    CaptureResult captureResult =
                            Camera2CameraCaptureResultConverter.getCaptureResult(result);
                    if (captureResult == null) {
                        return;
                    }

                    TotalCaptureResult totalCaptureResult = (TotalCaptureResult) captureResult;
                    if (totalCaptureResult == null) {
                        return;
                    }

                    Pair<Image, TotalCaptureResult> imageCapturePair = new Pair<>(
                            imageProxy.getImage(), totalCaptureResult);
                    bundleMap.put(id, imageCapturePair);
                } catch (TimeoutException | ExecutionException | InterruptedException e) {
                    return;
                }
            }

            if (!mAccessCounter.tryIncrement()) {
                return;
            }

            try {
                mImpl.process(bundleMap);
            } finally {
                mAccessCounter.decrement();
            }
        }
    }
}
