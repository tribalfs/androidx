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

package androidx.core.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@JvmDefaultWithCompatibility
/**
 * Reports the media performance class of the device.
 *
 * Create an instance of DevicePerformance in your [android.app.Application.onCreate] and use
 * the [mediaPerformanceClass] value any time it is needed.
 * @sample androidx.core.performance.samples.usage
 *
 */
interface DevicePerformance {

    /**
     * The media performance class of the device or 0 if none.
     *
     * If this value is not <code>0</code>, the device conforms to the media performance class
     * definition of the SDK version of this value. This value is stable for the duration of
     * the process.
     *
     * Possible non-zero values are defined in
     * [Build.VERSION_CODES][android.os.Build.VERSION_CODES] starting with
     * [VERSION_CODES.R][android.os.Build.VERSION_CODES.R].
     *
     * Defaults to
     * [VERSION.MEDIA_PERFORMANCE_CLASS][android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS]
     *
     */
    val mediaPerformanceClass: Int

    companion object {
        /**
         * Create PerformanceClass from the context.
         *
         * This should be done in [android.app.Application.onCreate].
         *
         * Developers should call the createDevicePerformance companion method of
         * the desired DevicePerformanceSupplier implementation.
         *
         * @param devicePerformanceSupplier Supplies device performance.
         */
        @JvmStatic
        fun create(
            devicePerformanceSupplier: DevicePerformanceSupplier
        ): DevicePerformance = DefaultDevicePerformanceImpl(devicePerformanceSupplier)
    }
}

/**
 * Supplies a flow of mediaPerformanceClass
 */
interface DevicePerformanceSupplier {

    val mediaPerformanceClassFlow: Flow<Int>
}

/**
 * Lazy caches the mediaPerformanceClass
 */
private class DefaultDevicePerformanceImpl(
    val devicePerformanceSupplier: DevicePerformanceSupplier
) : DevicePerformance {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logTag = "DefaultDevicePerformanceImpl"

    init {
        scope.launch {
            devicePerformanceSupplier.mediaPerformanceClassFlow.first()
        }
    }

    override val mediaPerformanceClass by lazy(mode = LazyThreadSafetyMode.PUBLICATION) {
        runBlocking {
            devicePerformanceSupplier.mediaPerformanceClassFlow.last()
        }
    }
}
