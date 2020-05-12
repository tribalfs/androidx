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

package androidx.camera.core;

import android.util.Rational;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.core.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The FOV of one or many {@link UseCase}s.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class ViewPort {

    /**
     * LayoutDirection that defines the start and end of the {@link ScaleType}.
     *
     * @hide
     * @see android.util.LayoutDirection
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntDef({android.util.LayoutDirection.LTR, android.util.LayoutDirection.RTL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LayoutDirection {
    }

    /**
     * Scale types used to calculate the crop rect for a {@link UseCase}.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntDef({FILL_START, FILL_CENTER, FILL_END, FIT_START, FIT_CENTER, FIT_END})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleType {
    }

    /**
     * Scale the preview, maintaining the source aspect ratio, so it fills the entire
     * container, and align it to the top left corner of the view.
     * This may cause the preview to be cropped if the camera preview aspect ratio does not
     * match that of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FILL_START = 0;

    /**
     * Scale the preview, maintaining the source aspect ratio, so it fills the entire
     * container, and center it inside the view.
     * This may cause the preview to be cropped if the camera preview aspect ratio does not
     * match that of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FILL_CENTER = 1;

    /**
     * Scale the preview, maintaining the source aspect ratio, so it fills the entire
     * container, and align it to the bottom right corner of the view.
     * This may cause the preview to be cropped if the camera preview aspect ratio does not
     * match that of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FILL_END = 2;

    /**
     * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
     * within the container, and align it to the top left corner of the view.
     * Both dimensions of the preview will be equal or less than the corresponding dimensions
     * of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FIT_START = 3;

    /**
     * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
     * within the container, and center it inside the view.
     * Both dimensions of the preview will be equal or less than the corresponding dimensions
     * of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FIT_CENTER = 4;

    /**
     * Scale the preview, maintaining the source aspect ratio, so it is entirely contained
     * within the container, and align it to the bottom right corner of the view.
     * Both dimensions of the preview will be equal or less than the corresponding dimensions
     * of its container.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final int FIT_END = 5;

    @ScaleType
    private int mScaleType;

    @NonNull
    private Rational mAspectRatio;

    @ImageOutputConfig.RotationValue
    private int mRotation;

    ViewPort(@ScaleType int scaleType, @NonNull Rational aspectRatio,
            @ImageOutputConfig.RotationValue int rotation) {
        mScaleType = scaleType;
        mAspectRatio = aspectRatio;
        mRotation = rotation;
    }

    /**
     * Gets the aspect ratio of the {@link ViewPort}.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Rational getAspectRatio() {
        return mAspectRatio;
    }

    /**
     * Gets the rotation of the {@link ViewPort}.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @ImageOutputConfig.RotationValue
    public int getRotation() {
        return mRotation;
    }

    /**
     * Gets the {@link ScaleType} of the {@link ViewPort}.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @ScaleType
    public int getScaleType() {
        return mScaleType;
    }

    /**
     * Builder for {@link ViewPort}.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static class Builder {

        @ScaleType
        private int mScaleType;

        @Nullable
        private Rational mAspectRatio;

        @ImageOutputConfig.RotationValue
        private int mRotation;

        public Builder() {
        }

        /**
         * Sets the {@link ScaleType} of the {@link ViewPort}.
         *
         * The value is used by {@link UseCase} to calculate the crop rect.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setScaleType(@ScaleType int scaleType) {
            mScaleType = scaleType;
            return this;
        }

        /**
         * Sets the rotation of the {@link ViewPort}.
         *
         * This value overrides the target rotation of {@link UseCase}.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setRotation(@ImageOutputConfig.RotationValue int rotation) {
            mRotation = rotation;
            return this;
        }

        /**
         * Sets the aspect ratio of the {@link ViewPort}.
         *
         * The is used by {@link UseCase} to calculate the crop rect.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setAspectRatio(@NonNull Rational aspectRatio) {
            mAspectRatio = aspectRatio;
            return this;
        }

        /**
         * Sets the layout direction of the {@link ViewPort}.
         *
         * <p> The {@link LayoutDirection} decides the start and the end of the crop rect if
         * the {@link ScaleType} is one of the following types: {@link #FILL_END},
         * {@link #FILL_START},{@link #FIT_START} or {@link #FIT_END}.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setLayoutDirection(@LayoutDirection int layoutDirection) {
            throw new UnsupportedOperationException("LayoutDirection is not implemented");
        }

        /**
         * Builds the {@link ViewPort}.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @NonNull
        public ViewPort build() {
            Preconditions.checkNotNull(mAspectRatio, "The crop aspect ratio must be set.");
            return new ViewPort(mScaleType, mAspectRatio, mRotation);
        }
    }
}
