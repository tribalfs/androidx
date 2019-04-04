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

import androidx.annotation.GuardedBy;
import androidx.concurrent.futures.ResolvableFuture;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ImageProxyBundle} with a predefined set of captured ids. The {@link ListenableFuture}
 * for the capture id becomes valid when the corresponding {@link ImageProxy} has been set.
 */
final class SettableImageProxyBundle implements ImageProxyBundle {
    private final Object mLock = new Object();

    // Whether or not the bundle has been closed or not
    @GuardedBy("mLock")
    private boolean mClosed = false;

    /** Map of id to {@link ImageProxy} Future. */
    @GuardedBy("mLock")
    private final Map<Integer, ResolvableFuture<ImageProxy>> mFutureResults = new HashMap<>();

    private final List<Integer> mCaptureIdList;

    @Override
    public ListenableFuture<ImageProxy> getImageProxy(int captureId) {
        synchronized (mLock) {
            if (mClosed) {
                throw new IllegalStateException("ImageProxyBundle already closed.");
            }

            // Returns the future that has been set if it exists
            if (!mFutureResults.containsKey(captureId)) {
                throw new IllegalArgumentException(
                        "ImageProxyBundle does not contain this id: " + captureId);
            }

            return mFutureResults.get(captureId);
        }
    }

    @Override
    public List<Integer> getCaptureIds() {
        return Collections.unmodifiableList(new ArrayList<>(mFutureResults.keySet()));
    }

    /**
     * Create a {@link ImageProxyBundle} for captures with the given ids.
     *
     * @param captureIds The set of captureIds contained by the ImageProxyBundle
     */
    SettableImageProxyBundle(List<Integer> captureIds) {
        mCaptureIdList = captureIds;
        setup();
    }

    /**
     * Add an {@link ImageProxy} to synchronize.
     */
    void addImageProxy(ImageProxy imageProxy) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            Integer captureId = (Integer) imageProxy.getImageInfo().getTag();
            if (captureId == null) {
                throw new IllegalArgumentException("CaptureId is null.");
            }

            // If the CaptureId is associated with this SettableImageProxyBundle, set the
            // corresponding Future. Otherwise, throws exception.
            if (mFutureResults.containsKey(captureId)) {
                ResolvableFuture<ImageProxy> futureResult = mFutureResults.get(captureId);
                futureResult.set(imageProxy);
            } else {
                throw new IllegalArgumentException(
                        "ImageProxyBundle does not contain this id: " + captureId);
            }
        }
    }

    /**
     * Flush all {@link ImageProxy} that have been added.
     */
    void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mFutureResults.clear();
            mClosed = true;
        }
    }

    /**
     * Clear all {@link ImageProxy} that have been added and recreate the entries from the bundle.
     */
    void reset() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            mFutureResults.clear();
            setup();
        }
    }

    private void setup() {
        for (Integer captureId : mCaptureIdList) {
            ResolvableFuture<ImageProxy> futureResult = ResolvableFuture.create();
            mFutureResults.put(captureId, futureResult);
        }
    }
}
