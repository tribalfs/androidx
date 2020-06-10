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

import androidx.annotation.NonNull;

import java.util.LinkedHashSet;

/**
 * An interface for filtering cameras.
 */
@ExperimentalCameraFilter
public interface CameraFilter {
    /**
     * Filters a set of {@link Camera}s. The method modifies the input set directly, leaves the
     * cameras that match requirement and removes the rest.
     *
     * <p>If the filtered set has extra cameras contained in the original set, when used by a
     * {@link androidx.camera.core.CameraSelector} then it will result in an
     * IllegalArgumentException thrown when calling bindToLifecycle.
     *
     * <p>The camera that has lower index in the set has higher priority. When used by
     * {@link androidx.camera.core.CameraSelector.Builder#addCameraFilter(CameraFilter)}, the
     * available cameras will be filtered by all {@link CameraFilter}s by the order they were
     * added. The first camera in the result will be selected if there are multiple cameras left.
     *
     * @param cameras The set of {@link Camera} being filtered.
     */
    void filter(@NonNull LinkedHashSet<Camera> cameras);
}
