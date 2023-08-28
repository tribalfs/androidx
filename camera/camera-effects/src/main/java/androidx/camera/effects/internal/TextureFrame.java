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

package androidx.camera.effects.internal;

import static androidx.core.util.Preconditions.checkState;

import static java.util.Objects.requireNonNull;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.effects.opengl.GlRenderer;

/**
 * A GL texture for caching camera frames.
 *
 * <p>The frame can be empty or filled. A filled frame contains valid information on how to
 * render it. An empty frame can be filled with new content.
 */
class TextureFrame {

    private static final long NO_VALUE = Long.MIN_VALUE;

    private final int mTextureId;

    private long mTimestampNs = NO_VALUE;
    @Nullable
    private Surface mSurface;
    @Nullable
    private float[] mTransform;

    /**
     * Creates a frame that is backed by a texture ID.
     */
    TextureFrame(int textureId) {
        mTextureId = textureId;
    }

    /**
     * Checks if the frame is empty.
     *
     * <p>A empty frame means that the texture does not have valid content. It can be filled
     * with new content.
     */
    boolean isEmpty() {
        return mTimestampNs == NO_VALUE;
    }

    /**
     * Marks the frame as empty.
     *
     * <p>Once the frame is marked as empty, it can be filled with new content.
     */
    void markEmpty() {
        checkState(!isEmpty(), "Frame is already empty");
        mTimestampNs = NO_VALUE;
        mTransform = null;
        mSurface = null;
    }

    /**
     * Marks the frame as filled.
     *
     * <p>Call this method when a valid camera frame is copied to the texture with
     * {@link GlRenderer#renderInputToQueueTexture}. Once filled, the frame should not be
     * written into until it's made empty again.
     *
     * @param timestampNs the timestamp of the camera frame.
     * @param transform   the transform to apply when rendering the frame.
     * @param surface     the output surface to which the frame should render.
     */
    void markFilled(long timestampNs, float[] transform, Surface surface) {
        checkState(isEmpty(), "Frame is already filled");
        mTimestampNs = timestampNs;
        mTransform = transform;
        mSurface = surface;
    }

    /**
     * Gets the timestamp of the frame.
     *
     * <p>This value is used in {@link GlRenderer#renderQueueTextureToSurface}.
     */
    long getTimestampNs() {
        return mTimestampNs;
    }

    /**
     * Gets the 2D texture id of the frame.
     *
     * <p>This value is used in {@link GlRenderer#renderQueueTextureToSurface} and
     * {@link GlRenderer#renderInputToQueueTexture}.
     */
    int getTextureId() {
        return mTextureId;
    }

    /**
     * Gets the transform of the frame.
     *
     * <p>This value is used in {@link GlRenderer#renderQueueTextureToSurface}.
     */
    @NonNull
    float[] getTransform() {
        return requireNonNull(mTransform);
    }

    /**
     * Gets the output surface to render.
     *
     * <p>This value is used in {@link GlRenderer#renderQueueTextureToSurface}. A frame should
     * always be rendered to the same surface than the one it was originally filled with. If the
     * Surface is no longer valid, the frame should be dropped.
     */
    @NonNull
    Surface getSurface() {
        return requireNonNull(mSurface);
    }
}
