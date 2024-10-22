/*
 * Copyright 2024 The Android Open Source Project
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

@file:JvmName("Profiling")

package androidx.core.os

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ProfilingManager
import android.os.ProfilingResult
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Helpers providing simple wrapper APIs for {@link ProfilingManager}. */

// Begin section: Keep in sync with {@link ProfilingManager}
private const val KEY_DURATION_MS: String = "KEY_DURATION_MS"
private const val KEY_SAMPLING_INTERVAL_BYTES: String = "KEY_SAMPLING_INTERVAL_BYTES"
private const val KEY_TRACK_JAVA_ALLOCATIONS: String = "KEY_TRACK_JAVA_ALLOCATIONS"
private const val KEY_FREQUENCY_HZ: String = "KEY_FREQUENCY_HZ"
private const val KEY_SIZE_KB: String = "KEY_SIZE_KB"
private const val KEY_BUFFER_FILL_POLICY: String = "KEY_BUFFER_FILL_POLICY"

private const val VALUE_BUFFER_FILL_POLICY_DISCARD: Int = 1
private const val VALUE_BUFFER_FILL_POLICY_RING_BUFFER: Int = 2

// End section: Keep in sync with ProfilingManager

@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
enum class BufferFillPolicy(val value: Int) {
    DISCARD(VALUE_BUFFER_FILL_POLICY_DISCARD),
    RING_BUFFER(VALUE_BUFFER_FILL_POLICY_RING_BUFFER),
}

/** Obtain a flow to be called with all profiling results for this UID. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun registerForAllProfilingResults(context: Context): Flow<ProfilingResult> = callbackFlow {
    val listener = Consumer<ProfilingResult> { result -> trySend(result) }

    val service = context.getSystemService(ProfilingManager::class.java)
    service.registerForAllProfilingResults({ runnable -> runnable.run() }, listener)

    awaitClose { service.unregisterForAllProfilingResults(listener) }
}

/** Register a listener to be called with all profiling results for this UID. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun registerForAllProfilingResults(
    context: Context,
    executor: Executor,
    listener: Consumer<ProfilingResult>
) {
    val service = context.getSystemService(ProfilingManager::class.java)
    service.registerForAllProfilingResults(executor, listener)
}

/** Unregister a listener that was to be called for all profiling results. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun unregisterForAllProfilingResults(context: Context, listener: Consumer<ProfilingResult>) {
    val service = context.getSystemService(ProfilingManager::class.java)
    service.unregisterForAllProfilingResults(listener)
}

/**
 * Obtain a builder to create a request for a java heap dump from {@link ProfilingManager}.
 *
 * Request is submitted by calling {@link JavaHeapDumpRequestBuilder#request}.
 */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun requestJavaHeapDump(): JavaHeapDumpRequestBuilder {
    return JavaHeapDumpRequestBuilder()
}

/**
 * Obtain a builder to create a request for a heap profile from {@link ProfilingManager}.
 *
 * Request is submitted by calling {@link HeapProfileRequestBuilder#request}.
 */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun requestHeapProfile(): HeapProfileRequestBuilder {
    return HeapProfileRequestBuilder()
}

/**
 * Obtain a builder to create a request for stack sampling from {@link ProfilingManager}.
 *
 * Request is submitted by calling {@link StackSamplingRequestBuilder#request}.
 */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun requestStackSampling(): StackSamplingRequestBuilder {
    return StackSamplingRequestBuilder()
}

/**
 * Obtain a builder to create a request for a system trace from {@link ProfilingManager}.
 *
 * Request is submitted by calling {@link SystemTraceRequestBuilder#request}.
 */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun requestSystemTrace(): SystemTraceRequestBuilder {
    return SystemTraceRequestBuilder()
}

/** Base class for request builders. */
@SuppressWarnings("StaticFinalBuilder", "MissingBuildMethod", "TopLevelBuilder")
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
abstract class RequestBuilder<T : RequestBuilder<T>> internal constructor() {
    private var mTag: String? = null
    private var mCancellationSignal: CancellationSignal? = null

    /**
     * Add data to help identify the output. The first 20 alphanumeric characters, plus dashes, will
     * be lowercased and included in the output filename.
     */
    fun setTag(tag: String): T {
        mTag = tag
        return getThis()
    }

    /**
     * Set a CancellationSignal to request cancellation of the requested trace. Results will be
     * returned if available.
     */
    fun setCancellationSignal(cancellationSignal: CancellationSignal): T {
        mCancellationSignal = cancellationSignal
        return getThis()
    }

    /**
     * Submit the profiling request with the provided executor and listener.
     *
     * If the executor and/or listener are null, and if no global listener and executor combinations
     * are registered using {@link registerForAllProfilingResults}, the request will be dropped.
     */
    @SuppressWarnings("BuilderSetStyle")
    fun request(context: Context, executor: Executor?, listener: Consumer<ProfilingResult>?) {
        val service = context.getSystemService(ProfilingManager::class.java)
        service.requestProfiling(
            getProfilingType(),
            getParams(),
            mTag,
            mCancellationSignal,
            executor,
            listener
        )
    }

    @SuppressWarnings("HiddenAbstractMethod")
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected abstract fun getProfilingType(): Int

    @SuppressWarnings("HiddenAbstractMethod")
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected abstract fun getThis(): T

    @SuppressWarnings("HiddenAbstractMethod")
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    protected abstract fun getParams(): Bundle
}

/** Request builder for a java heap dump. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class JavaHeapDumpRequestBuilder internal constructor() :
    RequestBuilder<JavaHeapDumpRequestBuilder>() {
    private val mParams: Bundle = Bundle()

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getParams(): Bundle {
        return mParams
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getProfilingType(): Int {
        return ProfilingManager.PROFILING_TYPE_JAVA_HEAP_DUMP
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getThis(): JavaHeapDumpRequestBuilder {
        return this
    }

    /** Set the buffer size in kilobytes for this profiling request. */
    fun setBufferSizeKb(bufferSizeKb: Int): JavaHeapDumpRequestBuilder {
        mParams.putInt(KEY_SIZE_KB, bufferSizeKb)
        return this
    }
}

/** Request builder for a heap profile. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class HeapProfileRequestBuilder internal constructor() :
    RequestBuilder<HeapProfileRequestBuilder>() {
    private val mParams: Bundle = Bundle()

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getParams(): Bundle {
        return mParams
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getProfilingType(): Int {
        return ProfilingManager.PROFILING_TYPE_HEAP_PROFILE
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getThis(): HeapProfileRequestBuilder {
        return this
    }

    /** Set the buffer size in kilobytes for this profiling request. */
    fun setBufferSizeKb(bufferSizeKb: Int): HeapProfileRequestBuilder {
        mParams.putInt(KEY_SIZE_KB, bufferSizeKb)
        return this
    }

    /** Set the duration in milliseconds for this profiling request. */
    fun setDurationMs(durationMs: Int): HeapProfileRequestBuilder {
        mParams.putInt(KEY_DURATION_MS, durationMs)
        return this
    }

    /** Set the sampling interval in bytes for this profiling request. */
    fun setSamplingIntervalBytes(samplingIntervalBytes: Long): HeapProfileRequestBuilder {
        mParams.putLong(KEY_SAMPLING_INTERVAL_BYTES, samplingIntervalBytes)
        return this
    }

    /** Set whether to track Java allocations rather than native ones. */
    fun setTrackJavaAllocations(traceJavaAllocations: Boolean): HeapProfileRequestBuilder {
        mParams.putBoolean(KEY_TRACK_JAVA_ALLOCATIONS, traceJavaAllocations)
        return this
    }
}

/** Request builder for stack sampling. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class StackSamplingRequestBuilder internal constructor() :
    RequestBuilder<StackSamplingRequestBuilder>() {
    private val mParams: Bundle = Bundle()

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getParams(): Bundle {
        return mParams
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getProfilingType(): Int {
        return ProfilingManager.PROFILING_TYPE_STACK_SAMPLING
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getThis(): StackSamplingRequestBuilder {
        return this
    }

    /** Set the buffer size in kilobytes for this profiling request. */
    fun setBufferSizeKb(bufferSizeKb: Int): StackSamplingRequestBuilder {
        mParams.putInt(KEY_SIZE_KB, bufferSizeKb)
        return this
    }

    /** Set the duration in milliseconds for this profiling request. */
    fun setDurationMs(durationMs: Int): StackSamplingRequestBuilder {
        mParams.putInt(KEY_DURATION_MS, durationMs)
        return this
    }

    /** Set the cpu sampling frequency. */
    fun setSamplingFrequencyHz(samplingFrequencyHz: Int): StackSamplingRequestBuilder {
        mParams.putInt(KEY_FREQUENCY_HZ, samplingFrequencyHz)
        return this
    }
}

/** Request builder for a system trace. */
@RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class SystemTraceRequestBuilder internal constructor() :
    RequestBuilder<SystemTraceRequestBuilder>() {
    private val mParams: Bundle = Bundle()

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getParams(): Bundle {
        return mParams
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getProfilingType(): Int {
        return ProfilingManager.PROFILING_TYPE_SYSTEM_TRACE
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    override fun getThis(): SystemTraceRequestBuilder {
        return this
    }

    /** Set the buffer size in kilobytes for this profiling request. */
    fun setBufferSizeKb(bufferSizeKb: Int): SystemTraceRequestBuilder {
        mParams.putInt(KEY_SIZE_KB, bufferSizeKb)
        return this
    }

    /** Set the duration in milliseconds for this profiling request. */
    fun setDurationMs(durationMs: Int): SystemTraceRequestBuilder {
        mParams.putInt(KEY_DURATION_MS, durationMs)
        return this
    }

    /** Set the buffer fill policy. */
    fun setBufferFillPolicy(bufferFillPolicy: BufferFillPolicy): SystemTraceRequestBuilder {
        mParams.putInt(KEY_BUFFER_FILL_POLICY, bufferFillPolicy.value)
        return this
    }
}
