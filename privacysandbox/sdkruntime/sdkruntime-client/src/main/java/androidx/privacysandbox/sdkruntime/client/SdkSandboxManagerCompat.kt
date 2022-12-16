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
package androidx.privacysandbox.sdkruntime.client

import android.annotation.SuppressLint
import android.app.sdksandbox.LoadSdkException
import android.app.sdksandbox.SandboxedSdk
import android.app.sdksandbox.SdkSandboxManager
import android.content.Context
import android.os.Bundle
import android.os.ext.SdkExtensions.AD_SERVICES
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresExtension
import androidx.core.os.asOutcomeReceiver
import androidx.privacysandbox.sdkruntime.client.config.LocalSdkConfigsHolder
import androidx.privacysandbox.sdkruntime.client.loader.LocalSdk
import androidx.privacysandbox.sdkruntime.client.loader.SdkLoader
import androidx.privacysandbox.sdkruntime.core.AdServicesInfo
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException.Companion.LOAD_SDK_ALREADY_LOADED
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException.Companion.LOAD_SDK_NOT_FOUND
import androidx.privacysandbox.sdkruntime.core.LoadSdkCompatException.Companion.toLoadCompatSdkException
import androidx.privacysandbox.sdkruntime.core.SandboxedSdkCompat
import java.util.WeakHashMap
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Compat version of [SdkSandboxManager].
 *
 * Provides APIs to load [androidx.privacysandbox.sdkruntime.core.SandboxedSdkProviderCompat]
 * into SDK sandbox process or locally, and then interact with them.
 *
 * SdkSandbox process is a java process running in a separate uid range. Each app has its own
 * SDK sandbox process.
 *
 * First app needs to declare SDKs it depends on in it's AndroidManifest.xml
 * using <uses-sdk-library> tag. App can only load SDKs it depends on into the
 * SDK sandbox process.
 *
 * For loading SDKs locally App need to bundle and declare local SDKs in
 * assets/RuntimeEnabledSdkTable.xml with following format:
 *
 * <runtime-enabled-sdk-table>
 *     <runtime-enabled-sdk>
 *         <package-name>com.sdk1</package-name>
 *         <compat-config-path>assets/RuntimeEnabledSdk-com.sdk1/CompatSdkConfig.xml</compat-config-path>
 *     </runtime-enabled-sdk>
 *     <runtime-enabled-sdk>
 *         <package-name>com.sdk2</package-name>
 *         <compat-config-path>assets/RuntimeEnabledSdk-com.sdk2/CompatSdkConfig.xml</compat-config-path>
 *     </runtime-enabled-sdk>
 * </runtime-enabled-sdk-table>
 *
 * Each local SDK should have config with following format:
 *
 * <compat-config>
 *     <dex-path>RuntimeEnabledSdk-sdk.package.name/dex/classes.dex</dex-path>
 *     <dex-path>RuntimeEnabledSdk-sdk.package.name/dex/classes2.dex</dex-path>
 *     <java-resources-root-path>RuntimeEnabledSdk-sdk.package.name/res</java-resources-root-path>
 *     <compat-entrypoint>com.sdk.EntryPointClass</compat-entrypoint>
 *     <resource-id-remapping>
 *         <r-package-class>com.test.sdk.RPackage</r-package-class>
 *         <resources-package-id>123</resources-package-id>
 *     </resource-id-remapping>
 * </compat-config>
 *
 * @see [SdkSandboxManager]
 */
class SdkSandboxManagerCompat private constructor(
    private val platformApi: PlatformApi,
    private val configHolder: LocalSdkConfigsHolder,
    private val sdkLoader: SdkLoader
) {

    private val localLoadedSdks = HashMap<String, LocalSdk>()

    /**
     * Load SDK in a SDK sandbox java process or locally.
     *
     * App should already declare SDKs it depends on in its AndroidManifest using
     * <use-sdk-library> tag. App can only load SDKs it depends on into the SDK Sandbox process.
     *
     * When client application loads the first SDK, a new SdkSandbox process will be
     * created, otherwise other SDKs will be loaded into the same sandbox which already created for
     * the client application.
     *
     * Alternatively App could bundle and declare local SDKs dependencies in
     * assets/RuntimeEnabledSdkTable.xml to load SDKs locally.
     *
     * This API may only be called while the caller is running in the foreground. Calls from the
     * background will result in a [LoadSdkCompatException] being thrown.
     *
     * @param sdkName name of the SDK to be loaded.
     * @param params additional parameters to be passed to the SDK in the form of a [Bundle]
     *  as agreed between the client and the SDK.
     * @return [SandboxedSdkCompat] from SDK on a successful run.
     * @throws [LoadSdkCompatException] on fail.
     *
     * @see [SdkSandboxManager.loadSdk]
     */
    @Throws(LoadSdkCompatException::class)
    suspend fun loadSdk(
        sdkName: String,
        params: Bundle
    ): SandboxedSdkCompat {
        if (localLoadedSdks.containsKey(sdkName)) {
            throw LoadSdkCompatException(LOAD_SDK_ALREADY_LOADED, "$sdkName already loaded")
        }

        val sdkConfig = configHolder.getSdkConfig(sdkName)
        if (sdkConfig != null) {
            val sdkHolder = sdkLoader.loadSdk(sdkConfig)
            val sandboxedSdkCompat = sdkHolder.onLoadSdk(params)
            localLoadedSdks.put(sdkName, sdkHolder)
            return sandboxedSdkCompat
        }

        return platformApi.loadSdk(sdkName, params)
    }

    private interface PlatformApi {
        @DoNotInline
        suspend fun loadSdk(sdkName: String, params: Bundle): SandboxedSdkCompat
    }

    // TODO(b/249981547) Remove suppress after updating to new lint version (b/262251309)
    @SuppressLint("NewApi", "ClassVerificationFailure")
    @RequiresExtension(extension = AD_SERVICES, version = 4)
    private class ApiAdServicesV4Impl(context: Context) : PlatformApi {
        private val sdkSandboxManager = context.getSystemService(
            SdkSandboxManager::class.java
        )

        @DoNotInline
        override suspend fun loadSdk(
            sdkName: String,
            params: Bundle
        ): SandboxedSdkCompat {
            try {
                val sandboxedSdk = loadSdkInternal(sdkName, params)
                return SandboxedSdkCompat(sandboxedSdk)
            } catch (ex: LoadSdkException) {
                throw toLoadCompatSdkException(ex)
            }
        }

        private suspend fun loadSdkInternal(
            sdkName: String,
            params: Bundle
        ): SandboxedSdk {
            return suspendCancellableCoroutine { continuation ->
                sdkSandboxManager.loadSdk(
                    sdkName,
                    params,
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
    }

    private class FailImpl : PlatformApi {
        @DoNotInline
        override suspend fun loadSdk(
            sdkName: String,
            params: Bundle
        ): SandboxedSdkCompat {
            throw LoadSdkCompatException(LOAD_SDK_NOT_FOUND, "$sdkName not bundled with app")
        }
    }

    companion object {

        private val sInstances = WeakHashMap<Context, SdkSandboxManagerCompat>()

        /**
         *  Creates [SdkSandboxManagerCompat].
         *
         *  @param context Application context
         *
         *  @return SdkSandboxManagerCompat object.
         */
        @JvmStatic
        fun from(context: Context): SdkSandboxManagerCompat {
            synchronized(sInstances) {
                var instance = sInstances[context]
                if (instance == null) {
                    val configHolder = LocalSdkConfigsHolder.load(context)
                    val sdkLoader = SdkLoader.create(context)
                    val platformApi =
                        if (AdServicesInfo.version() >= 4) {
                            ApiAdServicesV4Impl(context)
                        } else {
                            FailImpl()
                        }
                    instance = SdkSandboxManagerCompat(platformApi, configHolder, sdkLoader)
                    sInstances[context] = instance
                }
                return instance
            }
        }
    }
}