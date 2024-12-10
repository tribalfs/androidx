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

package androidx.camera.camera2.internal.compat;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.Size;

import androidx.annotation.RequiresApi;
import androidx.camera.core.Logger;
import androidx.camera.core.impl.ImageFormatConstants;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class StreamConfigurationMapCompatBaseImpl
        implements StreamConfigurationMapCompat.StreamConfigurationMapCompatImpl {

    private static final String TAG = "StreamConfigurationMapCompatBaseImpl";

    final StreamConfigurationMap mStreamConfigurationMap;

    StreamConfigurationMapCompatBaseImpl(@NonNull StreamConfigurationMap map) {
        mStreamConfigurationMap = map;
    }

    @Override
    public int @Nullable [] getOutputFormats() {
        // b/361590210: try-catch to workaround the NullPointerException issue when using
        // StreamConfigurationMap provided by Robolectric.
        try {
            return mStreamConfigurationMap.getOutputFormats();
        } catch (NullPointerException | IllegalArgumentException e) {
            Logger.w(TAG, "Failed to get output formats from StreamConfigurationMap", e);
            return null;
        }
    }

    @Override
    public Size @Nullable [] getOutputSizes(int format) {
        Size[] sizes;
        if (format == ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE) {
            // This is a little tricky that 0x22 that is internal defined in
            // StreamConfigurationMap.java to be equal to ImageFormat.PRIVATE that is public
            // after Android level 23 but not public in Android L. Use {@link SurfaceTexture}
            // or {@link MediaCodec} will finally mapped to 0x22 in StreamConfigurationMap to
            // retrieve the output sizes information.
            sizes = mStreamConfigurationMap.getOutputSizes(SurfaceTexture.class);
        } else {
            sizes = mStreamConfigurationMap.getOutputSizes(format);
        }
        return sizes;
    }

    @Override
    public <T> Size @Nullable [] getOutputSizes(@NonNull Class<T> klass) {
        return mStreamConfigurationMap.getOutputSizes(klass);
    }

    @Override
    public Size @Nullable [] getHighResolutionOutputSizes(int format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Api23Impl.getHighResolutionOutputSizes(mStreamConfigurationMap, format);
        }
        return null;
    }

    @Override
    public Range<Integer> @Nullable [] getHighSpeedVideoFpsRanges() {
        return mStreamConfigurationMap.getHighSpeedVideoFpsRanges();
    }

    @Override
    public Range<Integer> @Nullable [] getHighSpeedVideoFpsRangesFor(@NonNull Size size)
            throws IllegalArgumentException {
        return mStreamConfigurationMap.getHighSpeedVideoFpsRangesFor(size);
    }

    @Override
    public Size @Nullable [] getHighSpeedVideoSizes() {
        return mStreamConfigurationMap.getHighSpeedVideoSizes();
    }

    @Override
    public Size @Nullable [] getHighSpeedVideoSizesFor(@NonNull Range<Integer> fpsRange)
            throws IllegalArgumentException {
        return mStreamConfigurationMap.getHighSpeedVideoSizesFor(fpsRange);
    }

    @Override
    public @NonNull StreamConfigurationMap unwrap() {
        return mStreamConfigurationMap;
    }

    @RequiresApi(23)
    static class Api23Impl {
        private Api23Impl() {
            // This class is not instantiable.
        }

        static Size[] getHighResolutionOutputSizes(StreamConfigurationMap streamConfigurationMap,
                int format) {
            return streamConfigurationMap.getHighResolutionOutputSizes(format);
        }
    }
}
