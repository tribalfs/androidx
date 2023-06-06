/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.tracing.perfetto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.JsonWriter
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY
import androidx.tracing.perfetto.PerfettoSdkHandshake.EnableTracingResponse
import androidx.tracing.perfetto.PerfettoSdkHandshake.RequestKeys.ACTION_ENABLE_TRACING
import androidx.tracing.perfetto.PerfettoSdkHandshake.RequestKeys.ACTION_ENABLE_TRACING_COLD_START
import androidx.tracing.perfetto.PerfettoSdkHandshake.RequestKeys.KEY_PATH
import androidx.tracing.perfetto.PerfettoSdkHandshake.RequestKeys.KEY_PERSISTENT
import androidx.tracing.perfetto.PerfettoSdkHandshake.ResponseExitCodes.RESULT_CODE_ERROR_OTHER
import androidx.tracing.perfetto.PerfettoSdkHandshake.ResponseExitCodes.RESULT_CODE_SUCCESS
import androidx.tracing.perfetto.PerfettoSdkHandshake.ResponseKeys
import androidx.tracing.perfetto.StartupTracingConfigStore.store
import androidx.tracing.perfetto.Tracing.EnableTracingResponse
import java.io.File
import java.io.StringWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Allows for enabling tracing in an app using a broadcast. @see [ACTION_ENABLE_TRACING] */
@RestrictTo(LIBRARY)
class TracingReceiver : BroadcastReceiver() {
    private val executor by lazy {
        ThreadPoolExecutor(
            /* corePoolSize = */ 0,
            /* maximumPoolSize = */ 1,
            /* keepAliveTime = */ 10, // gives time for tooling to side-load the .so file
            /* unit = */ TimeUnit.SECONDS,
            /* workQueue = */ LinkedBlockingQueue()
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action !in listOf(
                ACTION_ENABLE_TRACING,
                ACTION_ENABLE_TRACING_COLD_START,
                // ACTION_DISABLE_TRACING_COLD_START // TODO(282733308): implement
            )
        ) return

        // Path to the provided library binary file (optional). If not provided, local library files
        // will be used if present.
        val srcPath = intent.extras?.getString(KEY_PATH)

        val pendingResult = goAsync()
        executor.execute {
            try {
                val response = when (intent.action) {
                    ACTION_ENABLE_TRACING -> enableTracingImmediate(srcPath, context)
                    ACTION_ENABLE_TRACING_COLD_START ->
                        enableTracingColdStart(
                            context,
                            srcPath,
                            isPersistent = intent.extras?.getBoolean(KEY_PERSISTENT) ?: false
                        )
                    else -> throw IllegalStateException() // supported actions checked earlier
                }

                pendingResult.setResult(response.exitCode, response.toJsonString(), null)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Enables Perfetto SDK tracing in the app
     */
    private fun enableTracingImmediate(srcPath: String?, context: Context?): EnableTracingResponse =
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> {
                // TODO(234351579): Support API < 30
                EnableTracingResponse(
                    RESULT_CODE_ERROR_OTHER,
                    "SDK version not supported. Current minimum SDK = ${Build.VERSION_CODES.R}"
                )
            }
            srcPath != null && context != null -> {
                try {
                    Tracing.enable(File(srcPath), context)
                } catch (e: Exception) {
                    EnableTracingResponse(RESULT_CODE_ERROR_OTHER, e)
                }
            }
            srcPath != null && context == null -> {
                EnableTracingResponse(
                    RESULT_CODE_ERROR_OTHER,
                    "Cannot copy source file: $srcPath without access to a Context instance."
                )
            }
            else -> {
                // Library path was not provided, trying to resolve using app's local library files.
                Tracing.enable()
            }
        }

    /**
     * Handles [ACTION_ENABLE_TRACING_COLD_START]
     *
     * See [ACTION_ENABLE_TRACING_COLD_START] documentation for steps required before and after.
     */
    private fun enableTracingColdStart(
        context: Context?,
        srcPath: String?,
        isPersistent: Boolean
    ): EnableTracingResponse = enableTracingImmediate(srcPath, context).also {
        if (it.exitCode == RESULT_CODE_SUCCESS) {
            val config = StartupTracingConfig(libFilePath = srcPath, isPersistent = isPersistent)
            if (context == null) return EnableTracingResponse(
                RESULT_CODE_ERROR_OTHER,
                "Cannot set up cold start tracing without a Context instance."
            )
            config.store(context.applicationInfo.packageName)
        }
    }

    private fun EnableTracingResponse.toJsonString(): String {
        val output = StringWriter()

        JsonWriter(output).use {
            it.beginObject()

            it.name(ResponseKeys.KEY_EXIT_CODE)
            it.value(exitCode)

            it.name(ResponseKeys.KEY_REQUIRED_VERSION)
            it.value(requiredVersion)

            message?.let { msg ->
                it.name(ResponseKeys.KEY_MESSAGE)
                it.value(msg)
            }

            it.endObject()
        }

        return output.toString()
    }
}
