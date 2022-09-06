/*
 * Copyright 2021 The Android Open Source Project
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

import androidx.annotation.RequiresApi;
import androidx.camera.extensions.impl.ExtenderStateListener;

/**
 * A processor that is used for invoking vendor extensions. {@link #onInit()} will be invoked
 * after
 * {@link androidx.camera.extensions.impl.ExtenderStateListener#onInit}. {@link #close()} is
 * invoked to close the processor.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public interface VendorProcessor {
    /**
     * Initialize the processor after {@link ExtenderStateListener#onInit}.
     *
     * <p>The subclass can start to handle the incoming images after this function is called.
     */
    default void onInit() {}

    /**
     * De-initialize the processor before {@link ExtenderStateListener#onDeInit}.
     *
     * <p>The subclass will stop to handle the incoming images after this function is called.
     */
    default void onDeInit() {}

    /**
     * Close the processor.
     */
    void close();
}
