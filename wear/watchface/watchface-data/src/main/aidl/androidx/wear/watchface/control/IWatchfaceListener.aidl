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

package androidx.wear.watchface.control;

import androidx.wear.watchface.data.WatchFaceColorsWireFormat;

/**
 * Binder interface that allows the watch face to send notifications to its clients.
 *
 */
@JavaPassthrough(annotation="@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)")
interface IWatchfaceListener {
    // IMPORTANT NOTE: All methods must be given an explicit transaction id that must never change
    // in the future to remain binary backwards compatible.
    // Next Id: 6

    /**
     * API version number. This should be incremented every time a new method is added.
     */
    const int API_VERSION = 2;

    /**
     * Returns the version number for this API which the client can use to determine which methods
     * are available.
     *
     * @since API version 1.
     */
    int getApiVersion() = 1;

    /**
     * Called when the watch face is ready to render.
     *
     * @since API version 1.
     */
    oneway void onWatchfaceReady() = 2;

    /**
     * Called when the watch face is ready to render.
     *
     * @since API version 1.
     */
    oneway void onWatchfaceColorsChanged(in WatchFaceColorsWireFormat watchFaceColors) = 3;

    /**
     * Signals that the watch face's preview image needs updating.
     *
     * @since API version 1.
     */
    oneway void onPreviewImageUpdateRequested(in String watchFaceId) = 4;

    /**
     * Signals that the watch face engine has detached meaning this instance is defunct and should
     * be closed.
     *
     * @since API version 2.
     */
    oneway void onEngineDetached() = 5;
}
