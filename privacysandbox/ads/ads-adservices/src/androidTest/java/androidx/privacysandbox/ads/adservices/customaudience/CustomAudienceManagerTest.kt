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

package androidx.privacysandbox.ads.adservices.customaudience

import android.adservices.customaudience.CustomAudienceManager
import android.content.Context
import android.net.Uri
import android.os.OutcomeReceiver
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.privacysandbox.ads.adservices.common.AdData
import androidx.privacysandbox.ads.adservices.common.AdSelectionSignals
import androidx.privacysandbox.ads.adservices.common.AdTechIdentifier
import androidx.privacysandbox.ads.adservices.customaudience.CustomAudienceManager.Companion.obtain
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock

@SmallTest
@SuppressWarnings("NewApi")
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class CustomAudienceManagerTest {

    @Before
    fun setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    @SdkSuppress(maxSdkVersion = 33, minSdkVersion = 30)
    fun testOlderVersions() {
        val sdkExtVersion = SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES)

        Assume.assumeTrue("maxSdkVersion = API 33 ext 3", sdkExtVersion < 4)
        Truth.assertThat(obtain(mContext)).isEqualTo(null)
    }

    @Test
    @RequiresExtension(extension = SdkExtensions.AD_SERVICES, version = 4)
    fun testJoinCustomAudience() {
        val sdkExtVersion = SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES)

        Assume.assumeTrue("minSdkVersion = API 33 ext 4", sdkExtVersion >= 4)
        val customAudienceManager = mockCustomAudienceManager(mContext)
        setupResponse(customAudienceManager)
        val managerCompat = obtain(mContext)

        // Actually invoke the compat code.
        runBlocking {
            val customAudience = CustomAudience.Builder(buyer, name, uri, uri, ads)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now())
                .setUserBiddingSignals(userBiddingSignals)
                .setTrustedBiddingData(trustedBiddingSignals)
                .build()
            val request = JoinCustomAudienceRequest(customAudience)
            managerCompat!!.joinCustomAudience(request)
        }

        // Verify that the compat code was invoked correctly.
        val captor = ArgumentCaptor.forClass(
            android.adservices.customaudience.JoinCustomAudienceRequest::class.java
        )
        verify(customAudienceManager).joinCustomAudience(captor.capture(), any(), any())

        // Verify that the request that the compat code makes to the platform is correct.
        verifyJoinCustomAudienceRequest(captor.value)
    }

    @Test
    @RequiresExtension(extension = SdkExtensions.AD_SERVICES, version = 4)
    fun testLeaveCustomAudience() {
        val sdkExtVersion = SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES)

        Assume.assumeTrue("minSdkVersion = API 33 ext 4", sdkExtVersion >= 4)
        val customAudienceManager = mockCustomAudienceManager(mContext)
        setupResponse(customAudienceManager)
        val managerCompat = obtain(mContext)

        // Actually invoke the compat code.
        runBlocking {
            val request = LeaveCustomAudienceRequest(buyer, name)
            managerCompat!!.leaveCustomAudience(request)
        }

        // Verify that the compat code was invoked correctly.
        val captor = ArgumentCaptor.forClass(
            android.adservices.customaudience.LeaveCustomAudienceRequest::class.java
        )
        verify(customAudienceManager).leaveCustomAudience(captor.capture(), any(), any())

        // Verify that the request that the compat code makes to the platform is correct.
        verifyLeaveCustomAudienceRequest(captor.value)
    }

    @SdkSuppress(minSdkVersion = 30)
    @RequiresExtension(extension = SdkExtensions.AD_SERVICES, version = 4)
    companion object {
        private lateinit var mContext: Context
        private val uri: Uri = Uri.parse("abc.com")
        private const val adtech = "1234"
        private val buyer: AdTechIdentifier = AdTechIdentifier(adtech)
        private const val name: String = "abc"
        private const val signals = "signals"
        private val userBiddingSignals: AdSelectionSignals = AdSelectionSignals(signals)
        private val keys: List<String> = listOf("key1", "key2")
        private val trustedBiddingSignals: TrustedBiddingData = TrustedBiddingData(uri, keys)
        private const val metadata = "metadata"
        private val ads: List<AdData> = listOf(AdData(uri, metadata))

        private fun mockCustomAudienceManager(spyContext: Context): CustomAudienceManager {
            val customAudienceManager = mock(CustomAudienceManager::class.java)
            `when`(spyContext.getSystemService(CustomAudienceManager::class.java))
                .thenReturn(customAudienceManager)
            return customAudienceManager
        }

        private fun setupResponse(customAudienceManager: CustomAudienceManager) {
            val answer = { args: InvocationOnMock ->
                val receiver = args.getArgument<OutcomeReceiver<Any, Exception>>(2)
                receiver.onResult(Object())
                null
            }
            doAnswer(answer).`when`(customAudienceManager).joinCustomAudience(any(), any(), any())
            doAnswer(answer).`when`(customAudienceManager).leaveCustomAudience(any(), any(), any())
        }

        private fun verifyJoinCustomAudienceRequest(
            joinCustomAudienceRequest: android.adservices.customaudience.JoinCustomAudienceRequest
        ) {
            // Set up the request that we expect the compat code to invoke.
            val adtechIdentifier = android.adservices.common.AdTechIdentifier.fromString(adtech)
            val userBiddingSignals =
                android.adservices.common.AdSelectionSignals.fromString(signals)
            val trustedBiddingSignals =
                android.adservices.customaudience.TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(keys)
                .setTrustedBiddingUri(uri)
                .build()
            val customAudience = android.adservices.customaudience.CustomAudience.Builder()
                .setBuyer(adtechIdentifier)
                .setName(name)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now())
                .setBiddingLogicUri(uri)
                .setDailyUpdateUri(uri)
                .setUserBiddingSignals(userBiddingSignals)
                .setTrustedBiddingData(trustedBiddingSignals)
                .setAds(listOf(android.adservices.common.AdData.Builder()
                    .setRenderUri(uri)
                    .setMetadata(metadata)
                    .build()))
                .build()

            val expectedRequest =
                android.adservices.customaudience.JoinCustomAudienceRequest.Builder()
                    .setCustomAudience(customAudience)
                    .build()

            // Verify that the actual request matches the expected one.
            Truth.assertThat(expectedRequest.customAudience.ads.size ==
                joinCustomAudienceRequest.customAudience.ads.size).isTrue()
            Truth.assertThat(expectedRequest.customAudience.ads[0].renderUri ==
                joinCustomAudienceRequest.customAudience.ads[0].renderUri).isTrue()
            Truth.assertThat(expectedRequest.customAudience.ads[0].metadata ==
                joinCustomAudienceRequest.customAudience.ads[0].metadata).isTrue()
            Truth.assertThat(expectedRequest.customAudience.biddingLogicUri ==
                joinCustomAudienceRequest.customAudience.biddingLogicUri).isTrue()
            Truth.assertThat(expectedRequest.customAudience.buyer.toString() ==
                joinCustomAudienceRequest.customAudience.buyer.toString()).isTrue()
            Truth.assertThat(expectedRequest.customAudience.dailyUpdateUri ==
                joinCustomAudienceRequest.customAudience.dailyUpdateUri).isTrue()
            Truth.assertThat(expectedRequest.customAudience.name ==
                joinCustomAudienceRequest.customAudience.name).isTrue()
            Truth.assertThat(trustedBiddingSignals.trustedBiddingKeys ==
                joinCustomAudienceRequest.customAudience.trustedBiddingData!!.trustedBiddingKeys)
                .isTrue()
            Truth.assertThat(trustedBiddingSignals.trustedBiddingUri ==
                joinCustomAudienceRequest.customAudience.trustedBiddingData!!.trustedBiddingUri)
                .isTrue()
            Truth.assertThat(
                joinCustomAudienceRequest.customAudience.userBiddingSignals!!.toString() ==
                signals).isTrue()
        }

        private fun verifyLeaveCustomAudienceRequest(
            leaveCustomAudienceRequest: android.adservices.customaudience.LeaveCustomAudienceRequest
        ) {
            // Set up the request that we expect the compat code to invoke.
            val adtechIdentifier = android.adservices.common.AdTechIdentifier.fromString(adtech)

            val expectedRequest = android.adservices.customaudience.LeaveCustomAudienceRequest
                .Builder()
                .setBuyer(adtechIdentifier)
                .setName(name)
                .build()

            // Verify that the actual request matches the expected one.
            Truth.assertThat(expectedRequest == leaveCustomAudienceRequest).isTrue()
        }
    }
}