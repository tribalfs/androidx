/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.connect.client

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.health.platform.client.HealthDataService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PROVIDER_PACKAGE_NAME = "com.example.fake.provider"

@RunWith(AndroidJUnit4::class)
class HealthConnectClientTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun noBackingImplementation_unavailable() {
        val packageManager = context.packageManager
        Shadows.shadowOf(packageManager).removePackage(PROVIDER_PACKAGE_NAME)
        assertThat(HealthConnectClient.isAvailable(context, listOf(PROVIDER_PACKAGE_NAME)))
            .isFalse()
        assertThrows(IllegalStateException::class.java) {
            HealthConnectClient.getOrCreate(context, listOf(PROVIDER_PACKAGE_NAME))
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun backingImplementation_notEnabled_unavailable() {
        installPackage(context, PROVIDER_PACKAGE_NAME, enabled = false)
        assertThat(HealthConnectClient.isAvailable(context, listOf(PROVIDER_PACKAGE_NAME)))
            .isFalse()
        assertThrows(IllegalStateException::class.java) {
            HealthConnectClient.getOrCreate(context, listOf(PROVIDER_PACKAGE_NAME))
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun backingImplementation_enabledNoService_unavailable() {
        installPackage(context, PROVIDER_PACKAGE_NAME, enabled = true)
        assertThat(HealthConnectClient.isAvailable(context, listOf(PROVIDER_PACKAGE_NAME)))
            .isFalse()
        assertThrows(IllegalStateException::class.java) {
            HealthConnectClient.getOrCreate(context, listOf(PROVIDER_PACKAGE_NAME))
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun backingImplementation_enabled_isAvailable() {
        installPackage(context, PROVIDER_PACKAGE_NAME, enabled = true)
        installService(context, PROVIDER_PACKAGE_NAME)
        assertThat(HealthConnectClient.isAvailable(context, listOf(PROVIDER_PACKAGE_NAME))).isTrue()
        HealthConnectClient.getOrCreate(context, listOf(PROVIDER_PACKAGE_NAME))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun sdkVersionTooOld_unavailable() {
        assertThat(HealthConnectClient.isAvailable(context, listOf(PROVIDER_PACKAGE_NAME)))
            .isFalse()
        assertThrows(UnsupportedOperationException::class.java) {
            HealthConnectClient.getOrCreate(context, listOf(PROVIDER_PACKAGE_NAME))
        }
    }

    private fun installPackage(context: Context, packageName: String, enabled: Boolean) {
        val packageInfo = PackageInfo()
        packageInfo.packageName = packageName
        packageInfo.applicationInfo = ApplicationInfo()
        packageInfo.applicationInfo.enabled = enabled
        val packageManager = context.packageManager
        Shadows.shadowOf(packageManager).installPackage(packageInfo)
    }

    private fun installService(context: Context, packageName: String) {
        val packageManager = context.packageManager
        val serviceIntentFilter =
            IntentFilter(HealthDataService.ANDROID_HEALTH_PLATFORM_SERVICE_BIND_ACTION)
        val serviceComponentName =
            ComponentName(
                packageName,
                HealthDataService.ANDROID_HEALTH_PLATFORM_SERVICE_BIND_ACTION
            )
        shadowOf(packageManager).addServiceIfNotPresent(serviceComponentName)
        shadowOf(packageManager)
            .addIntentFilterForService(serviceComponentName, serviceIntentFilter)
    }
}
