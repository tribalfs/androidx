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
package androidx.privacysandbox.sdkruntime.core

import android.annotation.SuppressLint
import android.app.sdksandbox.SandboxedSdk
import android.os.IBinder
import android.os.ext.SdkExtensions.AD_SERVICES
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP

/**
 * Compat wrapper for [SandboxedSdk].
 * Represents an SDK loaded in the sandbox process or locally.
 * An application should use this object to obtain an interface to the SDK through [getInterface].
 *
 * The SDK should create it when [SandboxedSdkProviderCompat.onLoadSdk] is called, and drop all
 * references to it when [SandboxedSdkProviderCompat.beforeUnloadSdk] is called. Additionally, the
 * SDK should fail calls made to the [IBinder] returned from [getInterface] after
 * [SandboxedSdkProviderCompat.beforeUnloadSdk] has been called.
 *
 * @see [SandboxedSdk]
 *
 */
class SandboxedSdkCompat private constructor(
    private val sdkImpl: SandboxedSdkImpl
) {

    /**
     * Creates SandboxedSdkCompat from SDK Binder object.
     *
     * @param sdkInterface The SDK's interface. This will be the entrypoint into the sandboxed SDK
     * for the application. The SDK should keep this valid until it's loaded in the sandbox, and
     * start failing calls to this interface once it has been unloaded
     *
     * This interface can later be retrieved using [getInterface].
     *
     * @see [SandboxedSdk]
     */
    constructor(sdkInterface: IBinder) : this(CompatImpl(sdkInterface))

    /**
     * Creates SandboxedSdkCompat wrapper around existing [SandboxedSdk] object.
     *
     * @param sandboxedSdk SandboxedSdk object. All calls will be delegated to that object.
     * @suppress
     */
    @RequiresExtension(extension = AD_SERVICES, version = 4)
    @RestrictTo(LIBRARY_GROUP)
    constructor(sandboxedSdk: SandboxedSdk) : this(ApiAdServicesV4Impl(sandboxedSdk))

    /**
     * Returns the interface to the loaded SDK.
     * A null interface is returned if the Binder has since
     * become unavailable, in response to the SDK being unloaded.
     *
     * @return [IBinder] object for loaded SDK.
     *
     * @see [SandboxedSdk.getInterface]
     */
    fun getInterface() = sdkImpl.getInterface()

    /**
     * Create [SandboxedSdk] from compat object.
     *
     * @return Platform SandboxedSdk
     */
    @RequiresExtension(extension = AD_SERVICES, version = 4)
    internal fun toSandboxedSdk() = sdkImpl.toSandboxedSdk()

    internal interface SandboxedSdkImpl {
        fun getInterface(): IBinder?

        @RequiresExtension(extension = AD_SERVICES, version = 4)
        @DoNotInline
        fun toSandboxedSdk(): SandboxedSdk
    }

    // TODO(b/249981547) Remove suppress after updating to new lint version (b/262251309)
    @SuppressLint("NewApi", "ClassVerificationFailure")
    @RequiresExtension(extension = AD_SERVICES, version = 4)
    private class ApiAdServicesV4Impl(private val mSandboxedSdk: SandboxedSdk) : SandboxedSdkImpl {

        @DoNotInline
        override fun getInterface(): IBinder? {
            return mSandboxedSdk.getInterface()
        }

        @DoNotInline
        override fun toSandboxedSdk(): SandboxedSdk {
            return mSandboxedSdk
        }

        companion object {
            @DoNotInline
            fun createSandboxedSdk(sdkInterface: IBinder): SandboxedSdk {
                return SandboxedSdk(sdkInterface)
            }
        }
    }

    private class CompatImpl(private val sdkInterface: IBinder) : SandboxedSdkImpl {

        override fun getInterface(): IBinder {
            // This will be null if the SDK has been unloaded and the IBinder originally provided
            // is now a dead object.
            return sdkInterface
        }

        @RequiresExtension(extension = AD_SERVICES, version = 4)
        override fun toSandboxedSdk(): SandboxedSdk {
            // avoid class verifications errors
            return ApiAdServicesV4Impl.createSandboxedSdk(sdkInterface)
        }
    }

    companion object {
        /**
         *  Deprecated and will be removed in next release.
         *  Use [SandboxedSdkCompat] constructor instead.
         *  TODO(b/261013990) Remove method after Shim generator migration and release
         */
        @JvmStatic
        fun create(binder: IBinder): SandboxedSdkCompat = SandboxedSdkCompat(binder)
    }
}