/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.privacysandbox.sdkruntime.core.controller

import android.content.Context
import androidx.annotation.Keep
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.privacysandbox.sdkruntime.core.AdServicesInfo
import androidx.privacysandbox.sdkruntime.core.SandboxedSdkCompat
import androidx.privacysandbox.sdkruntime.core.SandboxedSdkProviderCompat
import androidx.privacysandbox.sdkruntime.core.Versions
import androidx.privacysandbox.sdkruntime.core.controller.impl.NoOpImpl
import androidx.privacysandbox.sdkruntime.core.controller.impl.PlatformImpl
import org.jetbrains.annotations.TestOnly

/**
 * Compat version of [android.app.sdksandbox.sdkprovider.SdkSandboxController].
 *
 * Controller that is used by SDK loaded in the sandbox or locally to access information provided
 * by the sandbox environment.
 *
 * It enables the SDK to communicate with other SDKS and know about the state of the sdks that are
 * currently loaded.
 *
 * An instance can be obtained using [SdkSandboxControllerCompat.from].
 * The [Context] can be obtained using [SandboxedSdkProviderCompat.context].
 *
 * @see [android.app.sdksandbox.sdkprovider.SdkSandboxController]
 */
class SdkSandboxControllerCompat internal constructor(
    private val controllerImpl: SandboxControllerImpl
) {

    /**
     * Fetches information about Sdks that are loaded in the sandbox or locally.
     *
     * @return List of [SandboxedSdkCompat] containing all currently loaded sdks
     *
     * @see [android.app.sdksandbox.sdkprovider.SdkSandboxController.getSandboxedSdks]
     */
    fun getSandboxedSdks(): List<SandboxedSdkCompat> =
        controllerImpl.getSandboxedSdks()

    /** @suppress */
    @RestrictTo(LIBRARY_GROUP)
    interface SandboxControllerImpl {
        fun getSandboxedSdks(): List<SandboxedSdkCompat>
    }

    companion object {

        private var localImpl: SandboxControllerImpl? = null

        /**
         *  Creates [SdkSandboxControllerCompat].
         *
         *  @param context SDK context
         *
         *  @return SdkSandboxControllerCompat object.
         */
        @JvmStatic
        fun from(context: Context): SdkSandboxControllerCompat {
            val loadedLocally = Versions.CLIENT_VERSION != null
            if (loadedLocally) {
                val implFromClient = localImpl
                if (implFromClient != null) {
                    return SdkSandboxControllerCompat(implFromClient)
                }
                return SdkSandboxControllerCompat(NoOpImpl())
            }

            if (AdServicesInfo.isAtLeastV5()) {
                return SdkSandboxControllerCompat(PlatformImpl.from(context))
            }

            return SdkSandboxControllerCompat(NoOpImpl())
        }

        /**
         * Inject implementation from client library.
         * Implementation will be used only if loaded locally.
         * This method will be called from client side via reflection during loading SDK.
         *
         * @suppress
         */
        @JvmStatic
        @Keep
        @RestrictTo(LIBRARY_GROUP)
        fun injectLocalImpl(impl: SandboxControllerImpl) {
            check(localImpl == null) { "Local implementation already injected" }
            localImpl = impl
        }

        @TestOnly
        @RestrictTo(LIBRARY_GROUP)
        fun resetLocalImpl() {
            localImpl = null
        }
    }
}