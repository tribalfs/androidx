/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.testing.fakes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraInternal;
import androidx.camera.core.LensFacing;
import androidx.camera.core.LensFacingCameraIdFilter;
import androidx.camera.core.LensFacingConverter;
import androidx.core.util.Pair;
import androidx.core.util.Preconditions;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * A {@link CameraFactory} implementation that contains and produces fake cameras.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class FakeCameraFactory implements CameraFactory {

    private static final String DEFAULT_BACK_ID = "0";
    private static final String DEFAULT_FRONT_ID = "1";

    @Nullable
    private Set<String> mCachedCameraIds;
    @Nullable
    private Map<Integer, Set<String>> mCachedLensFacingToIdMap;
    private String mFrontCameraId = DEFAULT_FRONT_ID;
    private String mBackCameraId = DEFAULT_BACK_ID;

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    final Map<String, Pair<Integer, Callable<CameraInternal>>> mCameraMap = new HashMap<>();

    public FakeCameraFactory() {
    }

    @Override
    @NonNull
    public CameraInternal getCamera(@NonNull String cameraId) {
        Pair<Integer, Callable<CameraInternal>> cameraPair = mCameraMap.get(cameraId);
        if (cameraPair != null) {
            try {
                Callable<CameraInternal> cameraCallable = Preconditions.checkNotNull(
                        cameraPair.second);
                return cameraCallable.call();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create camera.", e);
            }
        }
        throw new IllegalArgumentException("Unknown camera: " + cameraId);
    }

    /**
     * Inserts a {@link Callable} for creating cameras with the given camera ID.
     *
     * @param cameraId       Identifier to use for the camera.
     * @param cameraInternal Callable used to provide the Camera implementation.
     */
    public void insertCamera(@LensFacing int lensFacing, @NonNull String cameraId,
            @NonNull Callable<CameraInternal> cameraInternal) {
        // Invalidate caches
        mCachedCameraIds = null;
        mCachedLensFacingToIdMap = null;

        mCameraMap.put(cameraId, Pair.create(lensFacing, cameraInternal));
    }

    /**
     * Inserts a camera and sets it as the default front camera.
     *
     * <p>This is a convenience method for calling
     * {@link #insertCamera(int, String, Callable)}
     * followed by {@link #setDefaultCameraIdForLensFacing(int, String)} with
     * {@link LensFacing#FRONT} for all lens facing arguments.
     *
     * @param cameraId       Identifier to use for the front camera.
     * @param cameraInternal Camera implementation.
     */
    public void insertDefaultFrontCamera(@NonNull String cameraId,
            @NonNull Callable<CameraInternal> cameraInternal) {
        insertCamera(LensFacing.FRONT, cameraId, cameraInternal);
        setDefaultCameraIdForLensFacing(LensFacing.FRONT, cameraId);
    }

    /**
     * Inserts a camera and sets it as the default back camera.
     *
     * <p>This is a convenience method for calling
     * {@link #insertCamera(int, String, Callable)}
     * followed by {@link #setDefaultCameraIdForLensFacing(int, String)} with
     * {@link LensFacing#BACK} for all lens facing arguments.
     *
     * @param cameraId       Identifier to use for the back camera.
     * @param cameraInternal Camera implementation.
     */
    public void insertDefaultBackCamera(@NonNull String cameraId,
            @NonNull Callable<CameraInternal> cameraInternal) {
        insertCamera(LensFacing.BACK, cameraId, cameraInternal);
        setDefaultCameraIdForLensFacing(LensFacing.BACK, cameraId);
    }

    /**
     * Sets the camera ID which will be returned by {@link #cameraIdForLensFacing(int)}.
     *
     * @param lensFacing The {@link LensFacing} to set.
     * @param cameraId   The camera ID which will be returned.
     */
    public void setDefaultCameraIdForLensFacing(@LensFacing int lensFacing,
            @NonNull String cameraId) {
        switch (lensFacing) {
            case LensFacing.FRONT:
                mFrontCameraId = cameraId;
                break;
            case LensFacing.BACK:
                mBackCameraId = cameraId;
                break;
            default:
                throw new IllegalArgumentException("Invalid lens facing: " + lensFacing);
        }
    }

    @Override
    @NonNull
    public Set<String> getAvailableCameraIds() {
        // Lazily cache the set of all camera ids. This cache will be invalidated anytime a new
        // camera is added.
        if (mCachedCameraIds == null) {
            mCachedCameraIds = Collections.unmodifiableSet(new HashSet<>(mCameraMap.keySet()));
        }

        return mCachedCameraIds;
    }

    @Override
    @Nullable
    public String cameraIdForLensFacing(@LensFacing int lensFacing) {
        switch (lensFacing) {
            case LensFacing.FRONT:
                return mFrontCameraId;
            case LensFacing.BACK:
                return mBackCameraId;
            default:
                return null;
        }
    }

    @Override
    @NonNull
    public LensFacingCameraIdFilter getLensFacingCameraIdFilter(@LensFacing int lensFacing) {
        // Lazily cache the map of LensFacing to set of camera ids. This cache will be
        // invalidated anytime a new camera is added.
        if (mCachedLensFacingToIdMap == null) {
            // Create empty sets of ids for all LensFacing types
            HashMap<Integer, Set<String>> lensFacingToIdMap = new HashMap<>();
            for (int l : LensFacingConverter.values()) {
                // Use a TreeSet to ensure lexical ordering of ids
                lensFacingToIdMap.put(l, new TreeSet<>());
            }

            // Populate the sets of ids
            for (Map.Entry<String, Pair<Integer, Callable<CameraInternal>>> entry :
                    mCameraMap.entrySet()) {
                Preconditions.checkNotNull(lensFacingToIdMap.get(entry.getValue().first))
                        .add(entry.getKey());
            }

            mCachedLensFacingToIdMap = Collections.unmodifiableMap(lensFacingToIdMap);
        }

        return new SettableLensFacingCameraIdFilter(lensFacing,
                mCachedLensFacingToIdMap.get(lensFacing));
    }

    private static final class SettableLensFacingCameraIdFilter extends LensFacingCameraIdFilter {
        @Nullable
        private final Set<String> mIds;

        SettableLensFacingCameraIdFilter(@LensFacing int lensFacing, @Nullable Set<String> ids) {
            super(lensFacing);
            mIds = ids;
        }

        @Override
        @NonNull
        public Set<String> filter(@NonNull Set<String> cameraIds) {
            if (mIds == null) {
                return cameraIds;
            }

            // Use a TreeSet to maintain lexical order of ids
            Set<String> resultCameraIdSet = new TreeSet<>(cameraIds);
            resultCameraIdSet.retainAll(mIds);
            return resultCameraIdSet;
        }
    }
}
