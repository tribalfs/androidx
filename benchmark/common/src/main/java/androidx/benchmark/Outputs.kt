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

package androidx.benchmark

import android.annotation.SuppressLint
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Outputs {

    /**
     * The output directory that the developer wants us to use.
     */
    public val outputDirectory: File

    /**
     * The usable output directory, given permission issues with `adb shell` on Android R.
     * Both the app and the shell have access to this output folder.
     */
    public val dirUsableByAppAndShell: File

    init {
        @SuppressLint("UnsafeNewApiCall", "NewApi")
        @Suppress("DEPRECATION")
        dirUsableByAppAndShell = when (Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.R -> {
                // On Android R, we are using the media directory because that is the directory
                // that the shell has access to. Context: b/181601156
                InstrumentationRegistry.getInstrumentation().context.externalMediaDirs
                    .firstOrNull {
                        Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
                    }
            }
            else -> InstrumentationRegistry.getInstrumentation().context.externalCacheDir
        } ?: throw IllegalStateException(
            "Unable to read externalCacheDir for writing files, " +
                "additionalTestOutputDir argument required to declare output dir."
        )

        Log.d(BenchmarkState.TAG, "Usable output directory: $dirUsableByAppAndShell")

        outputDirectory = Arguments.additionalTestOutputDir?.let { File(it) }
            ?: dirUsableByAppAndShell

        Log.d(BenchmarkState.TAG, "Output Directory: $outputDirectory")
    }

    /**
     * Create a benchmark output [File] to write to.
     *
     * This method handles reporting files to `InstrumentationStatus` to request copy,
     * writing them in the desired output directory, and handling shell access issues on Android R.
     *
     * @return The absolute path of the output [File].
     */
    public fun writeFile(
        fileName: String,
        reportKey: String,
        reportOnRunEndOnly: Boolean = false,
        block: (file: File) -> Unit,
    ): String {
        val override = Build.VERSION.SDK_INT == Build.VERSION_CODES.R
        // We override the `additionalTestOutputDir` argument on R.
        // Context: b/181601156
        val file = File(dirUsableByAppAndShell, fileName)
        try {
            block.invoke(file)
        } finally {
            var destination = file
            if (override) {
                // This respects the `additionalTestOutputDir` argument.
                val actualOutputDirectory = outputDirectory
                destination = File(actualOutputDirectory, fileName)
                if (file != destination) {
                    try {
                        destination.mkdirs()
                        file.copyTo(destination, overwrite = true)
                    } catch (exception: Throwable) {
                        // This can happen when `additionalTestOutputDir` being passed in cannot
                        // be written to. The shell does not have permissions to do the necessary
                        // setup, and this can cause `adb pull` to fail.
                        val message = """
                            Unable to copy files to ${destination.absolutePath}.
                            Please pull the Macrobenchmark results manually by using:
                            adb pull ${file.absolutePath}
                        """.trimIndent()
                        Log.e(BenchmarkState.TAG, message, exception)
                        destination = file
                    }
                }
            }
            InstrumentationResults.reportAdditionalFileToCopy(
                key = reportKey,
                absoluteFilePath = destination.absolutePath,
                reportOnRunEndOnly = reportOnRunEndOnly
            )
            return destination.absolutePath
        }
    }

    public fun testOutputFile(filename: String): File {
        return File(outputDirectory, filename)
    }

    public fun relativePathFor(path: String): String {
        var basePath = outputDirectory.absolutePath
        val relativePath = if (path.indexOf(basePath) > 0) {
            path.removePrefix("$basePath/")
        } else {
            basePath = dirUsableByAppAndShell.absolutePath
            path.removePrefix("$basePath/")
        }
        check(relativePath != path) {
            "$relativePath == $path"
        }
        return relativePath
    }
}
