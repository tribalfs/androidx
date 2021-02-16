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

package androidx.wear.complications

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.support.wearable.complications.IPreviewComplicationDataCallback
import android.support.wearable.complications.IProviderInfoService
import androidx.test.core.app.ApplicationProvider
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.LongTextComplicationData
import androidx.wear.complications.data.PlainComplicationText
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer

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

@RunWith(SharedRobolectricTestRunner::class)
public class ProviderInfoRetrieverTest {
    private val mockService = Mockito.mock(IProviderInfoService::class.java)
    private val mockBinder = Mockito.mock(IBinder::class.java)
    private val providerInfoRetriever = ProviderInfoRetriever(mockService)

    @Test
    public fun requestPreviewComplicationData() {
        runBlocking {
            val component = ComponentName("provider.package", "provider.class")
            val type = ComplicationType.LONG_TEXT
            Mockito.`when`(mockService.apiVersion).thenReturn(1)
            Mockito.`when`(mockService.asBinder()).thenReturn(mockBinder)

            val testData: ComplicationData = LongTextComplicationData.Builder(
                PlainComplicationText.Builder("Test Text").build()
            ).build()

            doAnswer {
                val callback = it.arguments[2] as IPreviewComplicationDataCallback
                callback.updateComplicationData(testData.asWireComplicationData())
                true
            }.`when`(mockService).requestPreviewComplicationData(
                eq(component),
                eq(type.asWireComplicationType()),
                any()
            )

            val previewData =
                providerInfoRetriever.requestPreviewComplicationData(component, type)!!
            assertThat(previewData.type).isEqualTo(type)
            assertThat(
                (previewData as LongTextComplicationData).text.getTextAt(
                    ApplicationProvider.getApplicationContext<Context>().resources, 0
                )
            ).isEqualTo("Test Text")
        }
    }

    @Test
    public fun requestPreviewComplicationDataProviderReturnsNull() {
        runBlocking {
            val component = ComponentName("provider.package", "provider.class")
            val type = ComplicationType.LONG_TEXT
            Mockito.`when`(mockService.apiVersion).thenReturn(1)
            Mockito.`when`(mockService.asBinder()).thenReturn(mockBinder)

            doAnswer {
                val callback = it.arguments[2] as IPreviewComplicationDataCallback
                callback.updateComplicationData(null)
                true
            }.`when`(mockService).requestPreviewComplicationData(
                eq(component),
                eq(type.asWireComplicationType()),
                any()
            )

            assertThat(providerInfoRetriever.requestPreviewComplicationData(component, type))
                .isNull()
        }
    }

    @Test
    public fun requestPreviewComplicationDataApiNotSupported() {
        runBlocking {
            val component = ComponentName("provider.package", "provider.class")
            val type = ComplicationType.LONG_TEXT
            Mockito.`when`(mockService.apiVersion).thenReturn(0)
            Mockito.`when`(mockService.asBinder()).thenReturn(mockBinder)

            assertThat(providerInfoRetriever.requestPreviewComplicationData(component, type))
                .isNull()
        }
    }

    @Test
    public fun requestPreviewComplicationDataApiReturnsFalse() {
        runBlocking {
            val component = ComponentName("provider.package", "provider.class")
            val type = ComplicationType.LONG_TEXT
            Mockito.`when`(mockService.apiVersion).thenReturn(1)
            Mockito.`when`(mockService.asBinder()).thenReturn(mockBinder)
            doAnswer {
                false
            }.`when`(mockService).requestPreviewComplicationData(
                eq(component),
                eq(type.asWireComplicationType()),
                any()
            )

            assertThat(providerInfoRetriever.requestPreviewComplicationData(component, type))
                .isNull()
        }
    }
}