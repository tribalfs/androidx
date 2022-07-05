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
import androidx.tracing.perfetto.Tracing.EnableTracingResponse
import androidx.tracing.perfetto.PerfettoHandshake.EnableTracingResponse
import androidx.tracing.perfetto.PerfettoHandshake.RequestKeys.ACTION_ENABLE_TRACING
import androidx.tracing.perfetto.PerfettoHandshake.RequestKeys.KEY_PATH
import androidx.tracing.perfetto.PerfettoHandshake.ResponseExitCodes.RESULT_CODE_ERROR_OTHER
import androidx.tracing.perfetto.PerfettoHandshake.ResponseKeys
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

    // TODO: check value on app start
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != ACTION_ENABLE_TRACING) return

        // Path to the provided library binary file (optional). If not provided, local library files
        // will be used if present.
        val srcPath = intent.extras?.getString(KEY_PATH)

        val pendingResult = goAsync()

        executor.execute {
            try {
                val response = enableTracing(srcPath, context)
                pendingResult.setResult(response.exitCode, response.toJsonString(), null)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun enableTracing(srcPath: String?, context: Context?): EnableTracingResponse =
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
                    val dstFile = copyExternalLibraryFile(context, srcPath)
                    Tracing.enable(dstFile, context)
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

    private fun copyExternalLibraryFile(
        context: Context,
        srcPath: String
    ): File {
        // Prepare a location to copy the library into with the following properties:
        // 1) app has exclusive write access in
        // 2) app can load binaries from
        val abi: String = File(context.applicationInfo.nativeLibraryDir).name // e.g. arm64
        val dstDir = context.cacheDir.resolve("lib/$abi")
        dstDir.mkdirs()

        // Copy the library file over
        //
        // TODO: load into memory and verify in-memory to prevent from copying a malicious
        // library into app's local files. Use SHA or Signature to verify the binaries.
        val srcFile = File(srcPath)
        val dstFile = dstDir.resolve(srcFile.name)
        srcFile.copyTo(dstFile, overwrite = true)

        return dstFile
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
