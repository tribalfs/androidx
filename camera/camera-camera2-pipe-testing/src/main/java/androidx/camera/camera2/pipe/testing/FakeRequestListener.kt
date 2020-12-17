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

package androidx.camera.camera2.pipe.testing

import androidx.camera.camera2.pipe.CameraTimestamp
import androidx.camera.camera2.pipe.FrameInfo
import androidx.camera.camera2.pipe.FrameNumber
import androidx.camera.camera2.pipe.Request
import androidx.camera.camera2.pipe.RequestMetadata

/**
 * Fake implementation of a [Request.Listener] for tests.
 */
@Suppress("ListenerInterface")
public class FakeRequestListener : Request.Listener {
    public var lastStartedRequestMetadata: RequestMetadata? = null
    public var lastStartedFrameNumber: FrameNumber? = null
    public var lastStartedTimestamp: CameraTimestamp? = null

    public var lastCompleteRequestMetadata: RequestMetadata? = null
    public var lastCompleteFrameNumber: FrameNumber? = null
    public var lastCompleteResult: FrameInfo? = null

    public var lastAbortedRequest: Request? = null

    override fun onStarted(
        requestMetadata: RequestMetadata,
        frameNumber: FrameNumber,
        timestamp: CameraTimestamp
    ) {
        lastStartedRequestMetadata = requestMetadata
        lastStartedFrameNumber = frameNumber
        lastStartedTimestamp = timestamp
    }

    override fun onComplete(
        requestMetadata: RequestMetadata,
        frameNumber: FrameNumber,
        result: FrameInfo
    ) {
        lastCompleteRequestMetadata = requestMetadata
        lastCompleteFrameNumber = frameNumber
        lastCompleteResult = result
    }

    override fun onAborted(
        request: Request
    ) {
        lastAbortedRequest = request
    }
}