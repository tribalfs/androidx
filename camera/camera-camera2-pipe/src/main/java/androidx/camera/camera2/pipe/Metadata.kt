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

package androidx.camera.camera2.pipe

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.view.Surface
import androidx.camera.camera2.pipe.impl.Debug
import java.util.concurrent.ConcurrentHashMap

/**
 * A map-like interface used to describe or interact with metadata from CameraPipe and Camera2.
 *
 * These interfaces are designed to wrap native camera2 metadata objects in a way that allows
 * additional values to be passed back internally computed values, state, or control values.
 *
 * These interfaces are read-only.
 */
interface Metadata {
    operator fun <T> get(key: Key<T>): T?
    fun <T> getOrDefault(key: Key<T>, default: T): T

    /**
     * Metadata keys provide values or controls that are provided or computed by CameraPipe.
     */
    class Key<T> private constructor(private val name: String) {
        companion object {
            @JvmStatic
            internal val keys: ConcurrentHashMap<String, Key<*>> = ConcurrentHashMap()

            /**
             * This will create a new Key instance, and will check to see that the key has not been
             * previously created somewhere else.
             */
            internal fun <T> create(name: String): Key<T> {
                val key = Key<T>(name)
                Debug.checkNull(keys.putIfAbsent(name, key)) { "$name is already defined!" }
                return key
            }
        }

        override fun toString(): String {
            return name
        }
    }
}

/**
 * CameraMetadata is a wrapper around [CameraCharacteristics].
 *
 * In some cases the properties on this interface will provide faster or more backwards compatible
 * access to features that are only available on newer versions of the OS.
 */
interface CameraMetadata : Metadata, UnsafeWrapper<CameraCharacteristics> {
    operator fun <T> get(key: CameraCharacteristics.Key<T>): T?
    fun <T> getOrDefault(key: CameraCharacteristics.Key<T>, default: T): T

    val camera: CameraId
    val isRedacted: Boolean

    val keys: Set<CameraCharacteristics.Key<*>>
    val requestKeys: Set<CaptureRequest.Key<*>>
    val resultKeys: Set<CaptureResult.Key<*>>
    val sessionKeys: Set<CaptureRequest.Key<*>>
    val physicalCameraIds: Set<CameraId>
    val physicalRequestKeys: Set<CaptureRequest.Key<*>>

    val streamMap: StreamConfigurationMap
}

/**
 * RequestMetadata wraps together all of the information about specific CaptureRequest that was
 * submitted to Camera2.
 *
 * <p> This class is distinct from [Request] which is used to configure and issue a request to the
 * [CameraGraph]. This class will report the actual keys / values that were sent to camera2 (if
 * different) from the request that was used to create the Camera2 [CaptureRequest].
 */
interface RequestMetadata : Metadata, UnsafeWrapper<CaptureRequest> {
    operator fun <T> get(key: CaptureRequest.Key<T>): T?
    fun <T> getOrDefault(key: CaptureRequest.Key<T>, default: T): T

    /** The actual Camera2 template that was used when creating this [CaptureRequest] */
    val template: RequestTemplate

    /**
     * A Map of StreamId(s) that were submitted with this CaptureRequest and the Surface(s) used
     * fot this request. It's possible that not all of the streamId's specified in the [Request]
     * are present in the CaptureRequest.
     */
    val streams: Map<StreamId, Surface>

    /** The request object that was used to create this [CaptureRequest] */
    val request: Request

    /** An internal number used to identify a specific [CaptureRequest] */
    val requestNumber: RequestNumber

    /** The android "sequence id" that is generated by camera2 when submitting [CaptureRequest]'s */
    val sequenceNumber: SequenceNumber
}

/**
 * ResultMetadata is a wrapper around [CaptureResult].
 */
interface ResultMetadata : Metadata, UnsafeWrapper<CaptureResult> {
    operator fun <T> get(key: CaptureResult.Key<T>): T?
    fun <T> getOrDefault(key: CaptureResult.Key<T>, default: T): T

    val camera: CameraId
    val requestMetadata: RequestMetadata
}

/**
 * A [RequestTemplate] indicates which preset set list of parameters will be applied to a request by
 * default. These values are defined by camera2.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class RequestTemplate(val value: Int)

/**
 * A [SequenceNumber] is the identifier that is returned when a single or repeating capture request
 * is submitted to the camera and represents that "sequence" of captures.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class SequenceNumber(val value: Int)

/**
 * A [RequestNumber] is an artificial identifier that is created for each request that is submitted
 * to the Camera.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class RequestNumber(val value: Long)

/**
 * A [FrameNumber] is the identifier that represents a specific exposure by the Camera. FrameNumbers
 * increase within a specific CameraCaptureSession, and are not created until the HAL begins
 * processing a request.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class FrameNumber(val value: Long)

/**
 * This is a timestamp from the Camera, and corresponds to the nanosecond exposure time of a Frame.
 * While the value is expressed in nano-seconds, the precision may be much lower. In addition, the
 * time-base of the Camera is undefined, although it's common for it to be in either Monotonic or
 * Realtime.
 *
 * <p> Timestamp may differ from timestamps that are obtained from other parts of the Camera and
 * media systems within the same device. For example, it's common for high frequency sensors to
 * operate based on a real-time clock, while audio/visual systems commonly operate based on a
 * monotonic clock.
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class CameraTimestamp(val value: Long)

/**
 * Utility function to help deal with the unsafe nature of the typed Key/Value pairs.
 */
fun CaptureRequest.Builder.writeParameters(
    parameters: Map<*, Any>
) {
    for ((key, value) in parameters) {
        if (key is CaptureRequest.Key<*>) {
            @Suppress("UNCHECKED_CAST")
            this.writeParameter(key as CaptureRequest.Key<Any>, value)
        }
    }
}

/**
 * Utility function to help deal with the unsafe nature of the typed Key/Value pairs.
 */
fun <T> CaptureRequest.Builder.writeParameter(key: CaptureRequest.Key<T>, value: T) {
    this.set(key, value)
}
