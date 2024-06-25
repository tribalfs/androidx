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

package androidx.privacysandbox.ui.integration.testsdkprovider

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.view.View
import androidx.privacysandbox.sdkruntime.core.controller.SdkSandboxControllerCompat
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.AdType
import androidx.privacysandbox.ui.integration.sdkproviderutils.SdkApiConstants.Companion.MediationOption
import androidx.privacysandbox.ui.integration.sdkproviderutils.TestAdapters
import androidx.privacysandbox.ui.integration.sdkproviderutils.ViewabilityHandler
import androidx.privacysandbox.ui.integration.testaidl.IAppOwnedMediateeSdkApi
import androidx.privacysandbox.ui.integration.testaidl.IMediateeSdkApi
import androidx.privacysandbox.ui.integration.testaidl.ISdkApi
import androidx.privacysandbox.ui.provider.toCoreLibInfo

class SdkApi(private val sdkContext: Context) : ISdkApi.Stub() {
    private val testAdapters = TestAdapters(sdkContext)

    override fun loadBannerAd(
        @AdType adType: Int,
        @MediationOption mediationOption: Int,
        waitInsideOnDraw: Boolean,
        drawViewability: Boolean
    ): Bundle {
        val isMediation =
            (mediationOption == MediationOption.SDK_RUNTIME_MEDIATEE ||
                mediationOption == MediationOption.IN_APP_MEDIATEE)
        val isAppOwnedMediation = (mediationOption == MediationOption.IN_APP_MEDIATEE)
        val adapter: SandboxedUiAdapter =
            if (isMediation) {
                loadMediatedTestAd(isAppOwnedMediation, adType, waitInsideOnDraw, drawViewability)
            } else {
                when (adType) {
                    AdType.NON_WEBVIEW -> {
                        loadNonWebViewBannerAd("Simple Ad", waitInsideOnDraw)
                    }
                    AdType.WEBVIEW -> {
                        loadWebViewBannerAd()
                    }
                    AdType.WEBVIEW_FROM_LOCAL_ASSETS -> {
                        loadWebViewBannerAdFromLocalAssets()
                    }
                    else -> {
                        loadNonWebViewBannerAd("Ad type not present", waitInsideOnDraw)
                    }
                }.also { ViewabilityHandler.addObserverFactoryToAdapter(it, drawViewability) }
            }
        return adapter.toCoreLibInfo(sdkContext)
    }

    /** Kill sandbox process */
    override fun triggerProcessDeath() {
        Process.killProcess(Process.myPid())
    }

    private fun loadWebViewBannerAd(): SandboxedUiAdapter {
        return testAdapters.WebViewBannerAd()
    }

    private fun loadWebViewBannerAdFromLocalAssets(): SandboxedUiAdapter {
        return testAdapters.WebViewAdFromLocalAssets()
    }

    private fun loadNonWebViewBannerAd(
        text: String,
        waitInsideOnDraw: Boolean
    ): SandboxedUiAdapter {
        return testAdapters.TestBannerAd(text, waitInsideOnDraw)
    }

    private fun loadMediatedTestAd(
        isAppMediatee: Boolean,
        @AdType adType: Int,
        waitInsideOnDraw: Boolean,
        drawViewability: Boolean
    ): SandboxedUiAdapter {
        val mediateeBannerAdBundle =
            getMediateeBannerAdBundle(isAppMediatee, adType, waitInsideOnDraw, drawViewability)
        return MediatedBannerAd(mediateeBannerAdBundle)
    }

    override fun requestResize(width: Int, height: Int) {}

    private inner class MediatedBannerAd(private val mediateeBannerAdBundle: Bundle?) :
        TestAdapters.BannerAd() {
        override fun buildAdView(sessionContext: Context): View {
            if (mediateeBannerAdBundle == null) {
                return testAdapters
                    .TestBannerAd("Mediated SDK is not loaded, this is a mediator Ad!", true)
                    .buildAdView(sdkContext)
            }

            val view = SandboxedSdkView(sdkContext)
            val adapter = SandboxedUiAdapterFactory.createFromCoreLibInfo(mediateeBannerAdBundle)
            view.setAdapter(adapter)
            return view
        }
    }

    private fun getMediateeBannerAdBundle(
        isAppMediatee: Boolean,
        adType: Int,
        withSlowDraw: Boolean,
        drawViewability: Boolean
    ): Bundle? {
        val sdkSandboxControllerCompat = SdkSandboxControllerCompat.from(sdkContext)
        if (isAppMediatee) {
            val appOwnedSdkSandboxInterfaces =
                sdkSandboxControllerCompat.getAppOwnedSdkSandboxInterfaces()
            appOwnedSdkSandboxInterfaces.forEach { appOwnedSdkSandboxInterfaceCompat ->
                if (appOwnedSdkSandboxInterfaceCompat.getName() == MEDIATEE_SDK) {
                    val appOwnedMediateeSdkApi =
                        IAppOwnedMediateeSdkApi.Stub.asInterface(
                            appOwnedSdkSandboxInterfaceCompat.getInterface()
                        )
                    return appOwnedMediateeSdkApi.loadBannerAd(
                        adType,
                        withSlowDraw,
                        drawViewability
                    )
                }
            }
        } else {
            val sandboxedSdks = sdkSandboxControllerCompat.getSandboxedSdks()
            sandboxedSdks.forEach { sandboxedSdkCompat ->
                if (sandboxedSdkCompat.getSdkInfo()?.name == MEDIATEE_SDK) {
                    val mediateeSdkApi =
                        IMediateeSdkApi.Stub.asInterface(sandboxedSdkCompat.getInterface())
                    return mediateeSdkApi.loadBannerAd(adType, withSlowDraw, drawViewability)
                }
            }
        }
        return null
    }

    companion object {
        private const val MEDIATEE_SDK =
            "androidx.privacysandbox.ui.integration.mediateesdkprovider"
        private const val TAG = "SdkApi"
    }
}
