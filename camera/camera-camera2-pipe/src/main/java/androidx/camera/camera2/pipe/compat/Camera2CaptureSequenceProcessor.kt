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

@file:RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java

package androidx.camera.camera2.pipe.compat

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.util.ArrayMap
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.CaptureSequence
import androidx.camera.camera2.pipe.CaptureSequenceProcessor
import androidx.camera.camera2.pipe.Metadata
import androidx.camera.camera2.pipe.Request
import androidx.camera.camera2.pipe.RequestMetadata
import androidx.camera.camera2.pipe.RequestNumber
import androidx.camera.camera2.pipe.RequestTemplate
import androidx.camera.camera2.pipe.StreamId
import androidx.camera.camera2.pipe.core.Log
import androidx.camera.camera2.pipe.core.Threads
import androidx.camera.camera2.pipe.writeParameters
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.atomicfu.atomic

internal interface Camera2CaptureSequenceProcessorFactory {
    fun create(
        session: CameraCaptureSessionWrapper,
        surfaceMap: Map<StreamId, Surface>
    ): CaptureSequenceProcessor<*, *>
}

internal class StandardCamera2CaptureSequenceProcessorFactory @Inject constructor(
    private val threads: Threads,
    private val graphConfig: CameraGraph.Config,
) : Camera2CaptureSequenceProcessorFactory {
    @Suppress("UNCHECKED_CAST")
    override fun create(
        session: CameraCaptureSessionWrapper,
        surfaceMap: Map<StreamId, Surface>
    ): CaptureSequenceProcessor<*, CaptureSequence<Any>> {
        @Suppress("SyntheticAccessor")
        return Camera2CaptureSequenceProcessor(
            session,
            threads,
            graphConfig.defaultTemplate,
            surfaceMap
        ) as CaptureSequenceProcessor<Any, CaptureSequence<Any>>
    }
}

internal val captureSequenceProcessorDebugIds = atomic(0)
internal val captureSequenceDebugIds = atomic(0L)
internal val requestTags = atomic(0L)
internal fun nextRequestTag(): RequestNumber = RequestNumber(requestTags.incrementAndGet())

private const val REQUIRE_SURFACE_FOR_ALL_STREAMS = false

/**
 * This class is designed to synchronously handle interactions with a [CameraCaptureSessionWrapper].
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
internal class Camera2CaptureSequenceProcessor(
    private val session: CameraCaptureSessionWrapper,
    private val threads: Threads,
    private val template: RequestTemplate,
    private val surfaceMap: Map<StreamId, Surface>
) : CaptureSequenceProcessor<CaptureRequest, Camera2CaptureSequence> {
    private val debugId = captureSequenceProcessorDebugIds.incrementAndGet()
    override fun build(
        isRepeating: Boolean,
        requests: List<Request>,
        defaultParameters: Map<*, Any?>,
        requiredParameters: Map<*, Any?>,
        listeners: List<Request.Listener>,
        sequenceListener: CaptureSequence.CaptureSequenceListener
    ): Camera2CaptureSequence? {
        check(requests.isNotEmpty()) {
            "build(...) should never be called with an empty request list!"
        }
        val requestMap = ArrayMap<RequestNumber, Camera2RequestMetadata>(requests.size)
        val requestList = ArrayList<Camera2RequestMetadata>(requests.size)

        val captureRequests = ArrayList<CaptureRequest>(requests.size)

        val surfaceToStreamMap = ArrayMap<Surface, StreamId>()
        val streamToSurfaceMap = ArrayMap<StreamId, Surface>()

        for (request in requests) {
            val requestTemplate = request.template ?: template

            Log.debug { "Building CaptureRequest for $request" }

            // Check to see if there is at least one valid surface for each stream.
            var hasSurface = false
            for (stream in request.streams) {
                if (streamToSurfaceMap.contains(stream)) {
                    hasSurface = true
                    continue
                }

                val surface = surfaceMap[stream]
                if (surface != null) {
                    Log.debug { "  Binding $stream to $surface" }

                    // TODO(codelogic) There should be a more efficient way to do these lookups than
                    // having two maps.
                    surfaceToStreamMap[surface] = stream
                    streamToSurfaceMap[stream] = surface
                    hasSurface = true
                } else if (REQUIRE_SURFACE_FOR_ALL_STREAMS) {
                    Log.info { "  Failed to bind surface to $stream" }

                    // If requireStreams is set we are required to map every stream to a valid
                    // Surface object for this request. If this condition is violated, then we
                    // return false because we cannot submit these request(s) until there is a valid
                    // StreamId -> Surface mapping for all streams.
                    return null
                }
            }

            // If there are no surfaces on a particular request, camera2 will not allow us to
            // submit it.
            if (!hasSurface) {
                Log.info { "  Failed to bind any surfaces for $request!" }
                return null
            }

            // Create the request builder. There is a risk this will throw an exception or return null
            // if the CameraDevice has been closed or disconnected. If this fails, indicate that the
            // request was not submitted.
            val requestBuilder: CaptureRequest.Builder
            try {
                requestBuilder = session.device.createCaptureRequest(requestTemplate)
            } catch (exception: ObjectUnavailableException) {
                Log.info { "  Failed to create a CaptureRequest.Builder from $requestTemplate!" }
                return null
            }

            // Apply the output surfaces to the requestBuilder
            hasSurface = false
            for (stream in request.streams) {
                val surface = streamToSurfaceMap[stream]
                if (surface != null) {
                    requestBuilder.addTarget(surface)
                    hasSurface = true
                }
            }

            // Soundness check to make sure we add at least one surface. This should be guaranteed
            // because we are supposed to exit early and return false if we cannot map at least one
            // surface per request.
            check(hasSurface)

            // Apply default parameters to the builder first.
            requestBuilder.writeParameters(defaultParameters)

            // Apply request parameters to the builder.
            requestBuilder.writeParameters(request.parameters)

            // Finally, write required parameters to the request builder. This will override any
            // value that has ben previously set.
            //
            // TODO(sushilnath@): Implement one of the two options. (1) Apply the 3A parameters
            // from internal 3A state machine at last and provide a flag in the Request object to
            // specify when the clients want to explicitly override some of the 3A parameters
            // directly. Add code to handle the flag. (2) Let clients override the 3A parameters
            // freely and when that happens intercept those parameters from the request and keep the
            // internal 3A state machine in sync.
            requestBuilder.writeParameters(requiredParameters)

            // The tag must be set for every request. We use it to lookup listeners for the
            // individual requests so that each request can specify individual listeners.
            val requestTag = nextRequestTag()
            requestBuilder.setTag(requestTag)

            // Create the camera2 captureRequest and add it to our list of requests.
            val captureRequest = requestBuilder.build()
            captureRequests.add(captureRequest)

            @Suppress("SyntheticAccessor")
            val metadata = Camera2RequestMetadata(
                captureRequest,
                defaultParameters,
                requiredParameters,
                streamToSurfaceMap,
                requestTemplate,
                isRepeating,
                request,
                requestTag
            )
            requestMap[requestTag] = metadata
            requestList.add(metadata)
        }

        // Create the captureSequence listener
        @Suppress("SyntheticAccessor")
        return Camera2CaptureSequence(
            session.device.cameraId,
            isRepeating,
            captureRequests,
            requestList,
            listeners,
            sequenceListener,
            requestMap,
            surfaceToStreamMap
        )
    }

    override fun submit(captureSequence: Camera2CaptureSequence): Int {
        val captureCallback = captureSequence as CameraCaptureSession.CaptureCallback
        // TODO: Update these calls to use executors on newer versions of the OS
        return if (captureSequence.captureRequestList.size == 1) {
            if (captureSequence.repeating) {
                session.setRepeatingRequest(
                    captureSequence.captureRequestList[0],
                    captureCallback,
                    threads.camera2Handler
                )
            } else {
                session.capture(
                    captureSequence.captureRequestList[0],
                    captureSequence,
                    threads.camera2Handler
                )
            }
        } else {
            if (captureSequence.repeating) {
                session.setRepeatingBurst(
                    captureSequence.captureRequestList,
                    captureSequence,
                    threads.camera2Handler
                )
            } else {
                session.captureBurst(
                    captureSequence.captureRequestList,
                    captureSequence,
                    threads.camera2Handler
                )
            }
        }
    }

    override fun abortCaptures() {
        session.abortCaptures()
    }

    override fun stopRepeating() {
        session.stopRepeating()
    }

    override fun close() {
        // Close should not shut down
    }

    override fun toString(): String {
        return "Camera2RequestProcessor-$debugId"
    }
}

/**
 * This class packages together information about a request that was submitted to the camera.
 */
@RequiresApi(21)
@Suppress("SyntheticAccessor") // Using an inline class generates a synthetic constructor
internal class Camera2RequestMetadata(
    private val captureRequest: CaptureRequest,
    private val defaultParameters: Map<*, Any?>,
    private val requiredParameters: Map<*, Any?>,
    override val streams: Map<StreamId, Surface>,
    override val template: RequestTemplate,
    override val repeating: Boolean,
    override val request: Request,
    override val requestNumber: RequestNumber
) : RequestMetadata {
    override fun <T> get(key: CaptureRequest.Key<T>): T? = captureRequest[key]
    override fun <T> getOrDefault(key: CaptureRequest.Key<T>, default: T): T =
        get(key) ?: default

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: Metadata.Key<T>): T? = when {
        requiredParameters.containsKey(key) -> {
            requiredParameters[key] as T?
        }
        request.extras.containsKey(key) -> {
            request.extras[key] as T?
        }
        else -> {
            defaultParameters[key] as T?
        }
    }

    override fun <T> getOrDefault(key: Metadata.Key<T>, default: T): T = get(key) ?: default

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> unwrapAs(type: KClass<T>): T? = when (type) {
        CaptureRequest::class -> captureRequest as T
        else -> null
    }
}
