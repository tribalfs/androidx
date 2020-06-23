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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.LensFacingCameraFilter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashSet;

/**
 * A set of requirements and priorities used to select a camera.
 */
public final class CameraSelector {

    /** A camera on the device facing the same direction as the device's screen. */
    public static final int LENS_FACING_FRONT = 0;
    /** A camera on the device facing the opposite direction as the device's screen. */
    public static final int LENS_FACING_BACK = 1;

    /** A static {@link CameraSelector} that selects the default front facing camera. */
    @NonNull
    public static final CameraSelector DEFAULT_FRONT_CAMERA =
            new CameraSelector.Builder().requireLensFacing(LENS_FACING_FRONT).build();
    /** A static {@link CameraSelector} that selects the default back facing camera. */
    @NonNull
    public static final CameraSelector DEFAULT_BACK_CAMERA =
            new CameraSelector.Builder().requireLensFacing(LENS_FACING_BACK).build();

    private LinkedHashSet<CameraFilter> mCameraFilterSet;

    CameraSelector(LinkedHashSet<CameraFilter> cameraFilterSet) {
        mCameraFilterSet = cameraFilterSet;
    }

    /**
     * Selects the first camera that filtered by the {@link CameraFilter}s assigned to this
     * {@link CameraSelector}.
     *
     * <p>When filtering with {@link CameraFilter}, the output set must be contained in the input
     * set, otherwise an IllegalArgumentException will be thrown. The output set is compared with
     * a copy of the original input set despite the input set isn't expected to be modified.
     *
     * @param cameras The camera set being filtered.
     * @return The first camera filtered.
     * @throws IllegalArgumentException If there's no available camera after filtering or the
     *                                  filtered cameras aren't contained in the input set.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UseExperimental(markerClass = ExperimentalCameraFilter.class)
    @NonNull
    public CameraInternal select(@NonNull LinkedHashSet<CameraInternal> cameras) {
        return filter(cameras).iterator().next();
    }

    /**
     * Filters the input cameras using the {@link CameraFilter} assigned to the selector.
     *
     * <p>The camera filtered must be contained in the input set. Otherwise it will throw an
     * exception.
     *
     * @param cameras The camera set being filtered.
     * @return The remain set of cameras.
     * @throws IllegalArgumentException If there's no available camera after being filtered or
     *                                  the filtered camera ids aren't contained in the input set.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UseExperimental(markerClass = ExperimentalCameraFilter.class)
    @NonNull
    public LinkedHashSet<CameraInternal> filter(@NonNull LinkedHashSet<CameraInternal> cameras) {
        LinkedHashSet<Camera> camerasCopy = new LinkedHashSet<>(cameras);
        LinkedHashSet<Camera> resultCameras = new LinkedHashSet<>(cameras);
        for (CameraFilter filter : mCameraFilterSet) {
            resultCameras = filter.filter(resultCameras);
            // If the result is empty or has extra camera that isn't contained in the input,
            // throws an exception.
            if (resultCameras.isEmpty()) {
                throw new IllegalArgumentException("No available camera can be found.");
            } else if (!camerasCopy.containsAll(resultCameras)) {
                throw new IllegalArgumentException("The output isn't contained in the input.");
            }
            camerasCopy.retainAll(resultCameras);
        }

        LinkedHashSet<CameraInternal> returnCameras = new LinkedHashSet<>();
        for (Camera camera : resultCameras) {
            returnCameras.add((CameraInternal) camera);
        }

        return returnCameras;
    }

    /**
     * Gets the set of {@link CameraFilter} assigned to this camera selector.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public LinkedHashSet<CameraFilter> getCameraFilterSet() {
        return mCameraFilterSet;
    }

    /**
     * Returns a single lens facing from this camera selector, or null if lens facing has not
     * been set.
     *
     * @return The lens facing.
     * @throws IllegalStateException if a single lens facing cannot be resolved, such as if
     *                               multiple conflicting lens facing requirements exist in this
     *                               camera selector.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UseExperimental(markerClass = ExperimentalCameraFilter.class)
    @Nullable
    public Integer getLensFacing() {
        Integer currentLensFacing = null;
        for (CameraFilter filter : mCameraFilterSet) {
            if (filter instanceof LensFacingCameraFilter) {
                Integer newLensFacing = ((LensFacingCameraFilter) filter).getLensFacing();
                if (currentLensFacing == null) {
                    currentLensFacing = newLensFacing;
                } else if (!currentLensFacing.equals(newLensFacing)) {
                    // TODO(b/122975195): Now we assume the lens facing of a camera is either
                    //  FRONT or BACK, so if there's conflicting lens facings set, throws an
                    //  exception. It needs to be revisited if we have a third lens facing enum
                    //  in the future.
                    throw new IllegalStateException(
                            "Multiple conflicting lens facing requirements exist.");
                }
            }
        }

        return currentLensFacing;
    }

    /** Builder for a {@link CameraSelector}. */
    public static final class Builder {
        private final LinkedHashSet<CameraFilter> mCameraFilterSet;

        public Builder() {
            mCameraFilterSet = new LinkedHashSet<>();
        }

        private Builder(@NonNull LinkedHashSet<CameraFilter> cameraFilterSet) {
            mCameraFilterSet = new LinkedHashSet<>(cameraFilterSet);
        }

        /**
         * Requires a camera with the specified lens facing.
         *
         * <p>Valid values for lens facing are {@link CameraSelector#LENS_FACING_FRONT} and
         * {@link CameraSelector#LENS_FACING_BACK}.
         *
         * <p>If lens facing is already set, this will add extra requirement for lens facing
         * instead of replacing the previous setting.
         */
        @UseExperimental(markerClass = ExperimentalCameraFilter.class)
        @NonNull
        public Builder requireLensFacing(@LensFacing int lensFacing) {
            mCameraFilterSet.add(new LensFacingCameraFilter(lensFacing));
            return this;
        }

        /**
         * Adds a {@link CameraFilter} to the current set of filters. It can be used to select a
         * specific camera based on customized criteria like Camera2 characteristics.
         *
         * <p>Multiple filters can be added. All filters will be applied by the order they were
         * added when the {@link CameraSelector} is used, and the first camera output from the
         * filters will be selected.
         */
        @ExperimentalCameraFilter
        @NonNull
        public Builder addCameraFilter(@NonNull CameraFilter cameraFilter) {
            mCameraFilterSet.add(cameraFilter);
            return this;
        }

        /**
         * Generates a Builder from another CameraSelector object.
         *
         * @param cameraSelector An existing CameraSelector.
         * @return The new Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static Builder fromSelector(@NonNull CameraSelector cameraSelector) {
            CameraSelector.Builder builder = new CameraSelector.Builder(
                    cameraSelector.getCameraFilterSet());
            return builder;
        }

        /** Builds the {@link CameraSelector}. */
        @NonNull
        public CameraSelector build() {
            return new CameraSelector(mCameraFilterSet);
        }
    }

    /**
     * The direction the camera faces relative to device screen.
     *
     * @hide
     */
    @IntDef({LENS_FACING_FRONT, LENS_FACING_BACK})
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @interface LensFacing {
    }
}
