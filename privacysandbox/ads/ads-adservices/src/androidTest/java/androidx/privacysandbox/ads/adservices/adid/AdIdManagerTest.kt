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

package androidx.privacysandbox.ads.adservices.adid

import android.content.Context
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock

@SmallTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34) // b/259092025
class AdIdManagerTest {
    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    @SdkSuppress(maxSdkVersion = 33)
    fun testAdIdOlderVersions() {
        assertThat(AdIdManager.obtain(mContext)).isEqualTo(null)
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun testAdIdAsync() {
        val adIdManager = mockAdIdManager(mContext)
        setupResponse(adIdManager)
        val managerCompat = AdIdManager.obtain(mContext)

        // Actually invoke the compat code.
        val result = runBlocking {
            managerCompat!!.getAdId()
        }

        // Verify that the compat code was invoked correctly.
        verify(adIdManager).getAdId(any(), any())

        // Verify that the result of the compat call is correct.
        verifyResponse(result)
    }

    @RequiresApi(34)
    private fun mockAdIdManager(spyContext: Context): android.adservices.adid.AdIdManager {
        val adIdManager = mock(android.adservices.adid.AdIdManager::class.java)
        `when`(spyContext.getSystemService(android.adservices.adid.AdIdManager::class.java))
            .thenReturn(adIdManager)
        return adIdManager
    }

    @RequiresApi(34)
    private fun setupResponse(adIdManager: android.adservices.adid.AdIdManager) {
        // Set up the response that AdIdManager will return when the compat code calls it.
        val adId = android.adservices.adid.AdId("1234", false)
        val answer = { args: InvocationOnMock ->
            val receiver = args.getArgument<
                OutcomeReceiver<android.adservices.adid.AdId, Exception>>(1)
            receiver.onResult(adId)
            null
        }
        doAnswer(answer)
            .`when`(adIdManager).getAdId(
                any(),
                any()
            )
    }

    @RequiresApi(34)
    private fun verifyResponse(adId: AdId) {
        Assert.assertEquals("1234", adId.adId)
        Assert.assertEquals(false, adId.isLimitAdTrackingEnabled)
    }
}