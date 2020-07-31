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

package androidx.biometric.integration.testapp

import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * A collection of testing utilities for the biometric integration test app.
 */
internal object TestUtils {
    /**
     * The maximum time that [changeOrientation] should wait for the device to finish rotating.
     */
    private const val ROTATE_TIMEOUT_MS = 2000L

    /**
     * Changes the [device] to [landscape] or portrait orientation and waits for rotation to finish.
     */
    internal fun changeOrientation(device: UiDevice, landscape: Boolean) {
        // Create a monitor to wait for the activity to be recreated.
        val monitor = Instrumentation.ActivityMonitor(
            BiometricTestActivity::class.java.name,
            null /* result */,
            false /* block */
        )
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor)

        if (landscape) {
            device.setOrientationLeft()
        } else {
            device.setOrientationNatural()
        }

        // Wait for the rotation to complete.
        InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout(
            monitor,
            ROTATE_TIMEOUT_MS
        )
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * Checks [context] to determine if the device has an enrolled biometric authentication method.
     */
    internal fun hasEnrolledBiometric(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK) == BIOMETRIC_SUCCESS
    }

    /**
     * Checks [context] to determine if the device is currently locked.
     */
    internal fun isDeviceLocked(context: Context): Boolean {
        val keyguard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            context.getSystemService(KeyguardManager::class.java)
        else
            context.getSystemService(Context::KEYGUARD_SERVICE.toString()) as KeyguardManager?

        return when {
            keyguard == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> keyguard.isDeviceLocked
            else -> keyguard.isKeyguardLocked
        }
    }
}