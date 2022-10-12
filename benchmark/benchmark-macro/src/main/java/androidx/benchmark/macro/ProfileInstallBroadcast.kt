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

package androidx.benchmark.macro

import android.util.Log
import androidx.annotation.RequiresApi
import androidx.benchmark.Shell
import androidx.profileinstaller.ProfileInstallReceiver
import androidx.profileinstaller.ProfileInstaller

internal object ProfileInstallBroadcast {
    private val receiverName = ProfileInstallReceiver::class.java.name

    /**
     * Returns null on success, or an error string otherwise.
     *
     * Returned error strings aren't thrown, to let the calling function decide strictness.
     */
    fun installProfile(packageName: String): String? {
        Log.d(TAG, "Profile Installer - Install profile")
        // For baseline profiles, we trigger this broadcast to force the baseline profile to be
        // installed synchronously
        val action = ProfileInstallReceiver.ACTION_INSTALL_PROFILE
        // Use an explicit broadcast given the app was force-stopped.
        val result = Shell.executeCommand("am broadcast -a $action $packageName/$receiverName")
            .substringAfter("Broadcast completed: result=")
            .trim()
            .toIntOrNull()
        when (result) {
            null,
                // 0 is returned by the platform by default, and also if no broadcast receiver
                // receives the broadcast.
            0 -> {
                return "The baseline profile install broadcast was not received. " +
                    "This most likely means that the profileinstaller library is missing " +
                    "from the target apk."
            }
            ProfileInstaller.RESULT_INSTALL_SUCCESS -> {
                return null // success!
            }
            ProfileInstaller.RESULT_ALREADY_INSTALLED -> {
                throw RuntimeException(
                    "Unable to install baseline profile. This most likely means that the " +
                        "latest version of the profileinstaller library is not being used. " +
                        "Please use the latest profileinstaller library version " +
                        "in the target app."
                )
            }
            ProfileInstaller.RESULT_UNSUPPORTED_ART_VERSION -> {
                throw RuntimeException(
                    "Baseline profiles aren't supported on this device version"
                )
            }
            ProfileInstaller.RESULT_BASELINE_PROFILE_NOT_FOUND -> {
                return "No baseline profile was found in the target apk."
            }
            ProfileInstaller.RESULT_NOT_WRITABLE,
            ProfileInstaller.RESULT_DESIRED_FORMAT_UNSUPPORTED,
            ProfileInstaller.RESULT_IO_EXCEPTION,
            ProfileInstaller.RESULT_PARSE_EXCEPTION -> {
                throw RuntimeException("Baseline Profile wasn't successfully installed")
            }
            else -> {
                throw RuntimeException(
                    "unrecognized ProfileInstaller result code: $result"
                )
            }
        }
    }

    /**
     * Uses skip files for avoiding interference from ProfileInstaller when using
     * [CompilationMode.None].
     *
     * Operation name is one of `WRITE_SKIP_FILE` or `DELETE_SKIP_FILE`.
     *
     * Returned error strings aren't thrown, to let the calling function decide strictness.
     */
    fun skipFileOperation(
        packageName: String,
        @Suppress("SameParameterValue") operation: String
    ): String? {
        Log.d(TAG, "Profile Installer - Skip File Operation: $operation")
        // Redefining constants here, because these are only defined in the latest alpha for
        // ProfileInstaller.
        // Use an explicit broadcast given the app was force-stopped.
        val action = "androidx.profileinstaller.action.SKIP_FILE"
        val operationKey = "EXTRA_SKIP_FILE_OPERATION"
        val extras = "$operationKey $operation"
        val result = Shell.executeCommand(
            "am broadcast -a $action -e $extras $packageName/$receiverName"
        )
            .substringAfter("Broadcast completed: result=")
            .trim()
            .toIntOrNull()
        return when {
            result == null || result == 0 -> {
                // 0 is returned by the platform by default, and also if no broadcast receiver
                // receives the broadcast.

                "The baseline profile skip file broadcast was not received. " +
                    "This most likely means that the `androidx.profileinstaller` library " +
                    "used by the target apk is old. Please use `1.2.0-alpha03` or newer. " +
                    "For more information refer to the release notes at " +
                    "https://developer.android.com/jetpack/androidx/releases/profileinstaller."
            }
            operation == "WRITE_SKIP_FILE" && result == 10 -> { // RESULT_INSTALL_SKIP_FILE_SUCCESS
                null // success!
            }
            operation == "DELETE_SKIP_FILE" && result == 11 -> { // RESULT_DELETE_SKIP_FILE_SUCCESS
                null // success!
            }
            else -> {
                throw RuntimeException(
                    "unrecognized ProfileInstaller result code: $result"
                )
            }
        }
    }

    /**
     * Save any in-memory profile data in the target app to disk, so it can be used for compilation.
     *
     * Returned error strings aren't thrown, to let the calling function decide strictness.
     */
    @RequiresApi(24)
    fun saveProfile(packageName: String): String? {
        Log.d(TAG, "Profile Installer - Save Profile")
        val action = "androidx.profileinstaller.action.SAVE_PROFILE"
        val result = Shell.executeCommand("am broadcast -a $action $packageName/$receiverName")
            .substringAfter("Broadcast completed: result=")
            .trim()
            .toIntOrNull()
        return when (result) {
            null, 0 -> {
                // 0 is returned by the platform by default, and also if no broadcast receiver
                // receives the broadcast.

                "The save profile broadcast event was not received. " +
                    "This most likely means that the `androidx.profileinstaller` library " +
                    "used by the target apk is old. Please use `1.3.0-alpha01` or newer. " +
                    "For more information refer to the release notes at " +
                    "https://developer.android.com/jetpack/androidx/releases/profileinstaller."
            }
            12 -> { // RESULT_SAVE_PROFILE_SIGNALLED
                // For safety, since this is async, we wait before returning
                // Empirically, this is extremely fast (< 10ms)
                Thread.sleep(500)
                null // success!
            }
            else -> {
                // We don't bother supporting RESULT_SAVE_PROFILE_SKIPPED here,
                // since we already perform SDK_INT checks and use @RequiresApi(24)
                throw RuntimeException(
                    "unrecognized ProfileInstaller result code: $result"
                )
            }
        }
    }
}