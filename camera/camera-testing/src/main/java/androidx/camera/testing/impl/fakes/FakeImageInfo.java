/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.testing.impl.fakes;

import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.impl.MutableTagBundle;
import androidx.camera.core.impl.TagBundle;
import androidx.camera.core.impl.utils.ExifData;

/**
 * A fake implementation of {@link ImageInfo} where the values are settable.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public final class FakeImageInfo implements ImageInfo {
    @NonNull private MutableTagBundle mTagBundle = MutableTagBundle.create();
    @NonNull private Matrix mSensorToBufferTransformMatrix = new Matrix();

    private long mTimestamp;
    private int mRotationDegrees;
    private float mFocalLength;

    /** set tag to a TagBundle */
    public void setTag(@NonNull String key, @NonNull Integer tag) {
        mTagBundle.putTag(key, tag);
    }

    /** set tag to a TagBundle */
    public void setTag(@NonNull TagBundle tagBundle) {
        mTagBundle.addTagBundle(tagBundle);
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public void setRotationDegrees(int rotationDegrees) {
        mRotationDegrees = rotationDegrees;
    }

    public void setFocalLength(float focalLength) {
        mFocalLength = focalLength;
    }

    public void setSensorToBufferTransformMatrix(@NonNull Matrix sensorToBufferTransformMatrix) {
        mSensorToBufferTransformMatrix = sensorToBufferTransformMatrix;
    }

    @NonNull
    @Override
    public TagBundle getTagBundle() {
        return mTagBundle;
    }

    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public int getRotationDegrees() {
        return mRotationDegrees;
    }

    @NonNull
    @Override
    public Matrix getSensorToBufferTransformMatrix() {
        return mSensorToBufferTransformMatrix;
    }

    @Override
    public void populateExifData(@NonNull ExifData.Builder exifBuilder) {
        exifBuilder.setOrientationDegrees(mRotationDegrees);
        exifBuilder.setFocalLength(mFocalLength);
    }
}
