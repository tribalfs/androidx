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

package androidx.benchmark.macro

import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.benchmark.DeviceInfo
import androidx.benchmark.Shell
import androidx.profileinstaller.ProfileInstallReceiver
import androidx.profileinstaller.ProfileInstaller
import org.junit.AssumptionViolatedException

/**
 * Type of compilation to use for a Macrobenchmark.
 *
 * Every Macrobenchmark has compilation reset before running, so that previous runs do not interfere
 * with the next. This compilation mode dictates any pre-compilation that occurs before repeatedly
 * running the setup / measure blocks of the benchmark.
 *
 * On Android N+ (API 24+), there are different levels of compilation supported:
 *
 * * [Partial] - the default configuration of [Partial] will partially pre-compile your application,
 * if a Baseline Profile is included in your app. This represents the most realistic fresh-install
 * experience on an end-user's device. You can additionally or instead use
 * [Partial.warmupIterations] to use Profile Guided Optimization, using the benchmark content to
 * guide pre-compilation to mimic an application's performance after some, and JIT-ing has occurred.
 *
 * * [Full] - the app is fully pre-compiled. This is generally not representative of real user
 * experience, as apps are not fully pre-compiled on user devices, but this can be used to either
 * illustrate ideal performance, or to reduce noise/inconsistency from just-in-time compilation
 * while the benchmark runs.
 *
 * * [None] - the app isn't pre-compiled at all, bypassing the default compilation that should
 * generally be done at install time, e.g. by the Play Store. This will illustrate worst case
 * performance, and will show you performance of your app if you do not enable baseline profiles,
 * useful for judging the performance impact of the baseline profiles included in your application.
 *
 * On Android M (API 23), only [Full] is supported, as all apps are always fully compiled.
 *
 * To understand more how these modes work, you can see comments for each class, and also see the
 * [Android Runtime compilation modes](https://source.android.com/devices/tech/dalvik/configure#compilation_options)
 * (which are passed by benchmark into
 * [`cmd compile`](https://source.android.com/devices/tech/dalvik/jit-compiler#force-compilation-of-a-specific-package)
 * to compile the target app).
 */
sealed class CompilationMode {
    internal fun resetAndCompile(packageName: String, warmupBlock: () -> Unit) {
        Log.d(TAG, "Clearing profiles for $packageName")
        Shell.executeCommand("cmd package compile --reset $packageName")
        compileImpl(packageName, warmupBlock)
    }

    internal fun cmdPackageCompile(packageName: String, compileArgument: String) {
        Shell.executeCommand("cmd package compile -f -m $compileArgument $packageName")
    }

    internal abstract fun compileImpl(packageName: String, warmupBlock: () -> Unit)

    /**
     * No pre-compilation - entire app will be allowed to Just-In-Time compile as it runs.
     *
     * Note that later iterations may perform differently, as app code is jitted.
     */
    // Leaving possibility for future configuration (such as interpreted = true)
    @Suppress("CanSealedSubClassBeObject")
    @RequiresApi(24)
    class None : CompilationMode() {
        override fun toString(): String = "None"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            // nothing to do!
        }
    }

    /**
     * Partial ahead-of-time app compilation.
     *
     * The default parameters for this mimic the default state of an app partially pre-compiled by
     * the installer - such as via Google Play.
     *
     * Either [baselineProfileMode] must be set to non-[BaselineProfileMode.Disable], or
     * [warmupIterations] must be set to a non-`0` value.
     *
     * Note: `[baselineProfileMode] = [BaselineProfileMode.Require]` is only supported for APKs that
     * have the ProfileInstaller library included, and have been built by AGP 7.0+ to package the
     * baseline profile in the APK.
     */
    @RequiresApi(24)
    class Partial @JvmOverloads constructor(
        /**
         * Controls whether a Baseline Profile should be used to partially pre compile the app.
         *
         * Defaults to [BaselineProfileMode.Require]
         *
         * @see BaselineProfileMode
         */
        val baselineProfileMode: BaselineProfileMode = BaselineProfileMode.Require,

        /**
         * If greater than 0, your macrobenchmark will run an extra [warmupIterations] times before
         * compilation, to prepare
         */
        @IntRange(from = 0)
        val warmupIterations: Int = 0
    ) : CompilationMode() {
        init {
            require(warmupIterations >= 0) {
                "warmupIterations must be non-negative, was $warmupIterations"
            }
            require(
                baselineProfileMode != BaselineProfileMode.Disable || warmupIterations > 0
            ) {
                "Must set baselineProfileMode != Ignore, or warmup iterations > 0 to define" +
                    " which portion of the app to pre-compile."
            }
        }

        override fun toString(): String {
            return if (
                baselineProfileMode == BaselineProfileMode.Require && warmupIterations == 0
            ) {
                "BaselineProfile"
            } else if (baselineProfileMode == BaselineProfileMode.Disable && warmupIterations > 0) {
                "WarmupProfile(iterations=$warmupIterations)"
            } else {
                "Partial(baselineProfile=$baselineProfileMode,iterations=$warmupIterations)"
            }
        }

        /**
         * Returns null on success, or an error string otherwise.
         *
         * Returned error strings aren't thrown, to let the calling function decide strictness.
         */
        private fun broadcastBaselineProfileInstall(packageName: String): String? {
            // For baseline profiles, we trigger this broadcast to force the baseline profile to be
            // installed synchronously
            val action = ProfileInstallReceiver.ACTION_INSTALL_PROFILE
            // Use an explicit broadcast given the app was force-stopped.
            val name = ProfileInstallReceiver::class.java.name
            val result = Shell.executeCommand("am broadcast -a $action $packageName/$name")
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

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            if (baselineProfileMode != BaselineProfileMode.Disable) {
                val installErrorString = broadcastBaselineProfileInstall(packageName)
                if (installErrorString == null) {
                    // baseline profile install success, kill process before compiling
                    Log.d(TAG, "Killing process $packageName")
                    Shell.executeCommand("am force-stop $packageName")
                    cmdPackageCompile(packageName, "speed-profile")
                } else {
                    if (baselineProfileMode == BaselineProfileMode.Require) {
                        throw RuntimeException(installErrorString)
                    } else {
                        Log.d(TAG, installErrorString)
                    }
                }
            }
            if (warmupIterations > 0) {
                repeat(this.warmupIterations) {
                    warmupBlock()
                }
                // For speed profile compilation, ART team recommended to wait for 5 secs when app
                // is in the foreground, dump the profile, wait for another 5 secs before
                // speed-profile compilation.
                Thread.sleep(5000)
                val response = Shell.executeCommand("killall -s SIGUSR1 $packageName")
                if (response.isNotBlank()) {
                    Log.d(TAG, "Received dump profile response $response")
                    throw RuntimeException("Failed to dump profile for $packageName ($response)")
                }
                cmdPackageCompile(packageName, "speed-profile")
            }
        }
    }

    /**
     * Full ahead-of-time compilation.
     *
     * Equates to `cmd package compile -f -m speed <package>` on API 24+.
     *
     * On Android M (API 23), this is the only supported compilation mode, as all apps are
     * fully compiled ahead-of-time.
     */
    @Suppress("CanSealedSubClassBeObject") // Leaving possibility for future configuration
    class Full : CompilationMode() {
        override fun toString(): String = "Full"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            if (Build.VERSION.SDK_INT >= 24) {
                cmdPackageCompile(packageName, "speed")
            }
            // Noop on older versions: apps are fully compiled at install time on API 23 and below
        }
    }

    /**
     * Partial pre-compilation, based on configurable number of profiling iterations.
     *
     * The compilation itself is performed with `cmd package compile -f -m speed-profile <package>`
     */
    @Deprecated(
        message = "Use CompilationMode.Partial to partially pre-compile the target app",
        replaceWith = ReplaceWith(
            expression = "CompilationMode.Partial(baselineProfileMode=" +
                "BaselineProfileMode.Ignore, warmupIterations=warmupIterations)",
            imports = ["androidx.benchmark.macro.BaselineProfileMode"]
        )
    )
    class SpeedProfile(
        val warmupIterations: Int = 3
    ) : CompilationMode() {
        override fun toString(): String = "SpeedProfile(iterations=$warmupIterations)"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            if (Build.VERSION.SDK_INT >= 24) {
                Partial(BaselineProfileMode.Disable, warmupIterations).compileImpl(
                    packageName,
                    warmupBlock
                )
            }
            // Noop on older versions: this is compat behavior with previous library releases
        }
    }

    /**
     * Partial pre-compilation based on bundled baseline profile.
     *
     * Note: this mode is only supported for APKs that have the profileinstaller library
     * included, and have been built by AGP 7.0+ to package the baseline profile in the APK.
     *
     * The compilation itself is performed with `cmd package compile -f -m speed-profile <package>`
     */
    @Deprecated(
        message = "Use CompilationMode.Partial to partially pre-compile the target app",
        replaceWith = ReplaceWith("CompilationMode.Partial()")
    )
    object BaselineProfile : CompilationMode() {
        override fun toString(): String = "BaselineProfile"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            if (Build.VERSION.SDK_INT >= 24) {
                Partial(BaselineProfileMode.Require, 0).compileImpl(
                    packageName,
                    warmupBlock
                )
            }
            // Noop on older versions: this is compat behavior with previous library releases
        }
    }

    /**
     * Full ahead-of-time compilation.
     *
     * Equates to `cmd package compile -f -m speed <package>`
     */
    @Deprecated(
        message = "Use CompilationMode.Full to fully compile the target app",
        replaceWith = ReplaceWith("CompilationMode.Full")
    )
    object Speed : CompilationMode() {
        override fun toString(): String = "Speed"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            Full().compileImpl(packageName, warmupBlock)
        }
    }

    /**
     * No JIT / pre-compilation, all app code runs on the interpreter.
     *
     * Note: this mode will only be supported on rooted devices with jit disabled. For this reason,
     * it's only available for internal benchmarking.
     *
     * TODO: migrate this to an internal-only flag on [None] instead
     *
     * @suppress
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    object Interpreted : CompilationMode() {
        override fun toString(): String = "Interpreted"

        override fun compileImpl(packageName: String, warmupBlock: () -> Unit) {
            // Nothing to do - handled externally
        }
    }

    companion object {
        internal val noop: CompilationMode = if (Build.VERSION.SDK_INT >= 24) None() else Full()

        /**
         * Represents the default compilation mode for the platform, on an end user's device.
         *
         * This is a post-store-install app configuration for this device's SDK
         * version - [`Partial(BaselineProfileMode.UseIfAvailable)`][Partial] on API 24+, and
         * [Full] prior to API 24 (where all apps are fully AOT compiled).
         *
         * On API 24+, Baseline Profile pre-compilation is used if possible, but no error will be
         * thrown if installation fails.
         *
         * Generally, it is preferable to explicitly pass a compilation mode, such as
         * [`Partial(BaselineProfileMode.Required)`][Partial] to avoid ambiguity, and e.g. validate
         * an app's BaselineProfile can be correctly used.
         */
        @JvmField
        val DEFAULT: CompilationMode = if (Build.VERSION.SDK_INT >= 24) {
            Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable,
                warmupIterations = 0
            )
        } else {
            // API 23 is always fully compiled
            Full()
        }
    }
}

/**
 * Returns true if the CompilationMode can be run with the device's current VM settings.
 *
 * Used by jetpack-internal benchmarks to skip CompilationModes that would self-suppress.
 *
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
fun CompilationMode.isSupportedWithVmSettings(): Boolean {
    val getProp = Shell.executeCommand("getprop dalvik.vm.extra-opts")
    val vmRunningInterpretedOnly = getProp.contains("-Xusejit:false")

    // true if requires interpreted, false otherwise
    val interpreted = this == CompilationMode.Interpreted
    return vmRunningInterpretedOnly == interpreted
}

internal fun CompilationMode.assumeSupportedWithVmSettings() {
    if (!isSupportedWithVmSettings()) {
        throw AssumptionViolatedException(
            when {
                DeviceInfo.isRooted && this == CompilationMode.Interpreted ->
                    """
                        To run benchmarks with CompilationMode $this,
                        you must disable jit on your device with the following command:
                        `adb shell setprop dalvik.vm.extra-opts -Xusejit:false; adb shell stop; adb shell start`                         
                    """.trimIndent()
                DeviceInfo.isRooted && this != CompilationMode.Interpreted ->
                    """
                        To run benchmarks with CompilationMode $this,
                        you must enable jit on your device with the following command:
                        `adb shell setprop dalvik.vm.extra-opts \"\"; adb shell stop; adb shell start` 
                    """.trimIndent()
                else ->
                    "You must toggle usejit on the VM to use CompilationMode $this, this requires" +
                        "rooting your device."
            }
        )
    }
}
