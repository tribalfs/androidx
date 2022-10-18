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

package androidx.window.embedding

import androidx.window.extensions.embedding.ActivityStack as OEMActivityStack
import androidx.window.extensions.embedding.SplitAttributes as OEMSplitAttributes
import androidx.window.extensions.embedding.SplitInfo as OEMSplitInfo
import android.app.Activity
import androidx.window.core.ExtensionsUtil
import androidx.window.core.PredicateAdapter
import androidx.window.embedding.SplitAttributes.SplitType
import androidx.window.extensions.WindowExtensions
import com.nhaarman.mockitokotlin2.doReturn
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/** Tests for [EmbeddingAdapter] */
class EmbeddingAdapterTest {
    private lateinit var adapter: EmbeddingAdapter

    @Before
    fun setUp() {
        adapter = EmbeddingBackend::class.java.classLoader?.let { loader ->
            EmbeddingAdapter(PredicateAdapter(loader))
        }!!
    }

    @Test
    fun testTranslateSplitInfoWithDefaultAttrs() {
        assumeTrue(ExtensionsUtil.safeVendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2)

        val oemSplitInfo = OEMSplitInfo(
            OEMActivityStack(ArrayList(), true),
            OEMActivityStack(ArrayList(), true),
            OEMSplitAttributes.Builder().build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.splitEqually())
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Test
    fun testTranslateSplitInfoWithExpandingContainers() {
        assumeTrue(ExtensionsUtil.safeVendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2)

        val oemSplitInfo = OEMSplitInfo(
            OEMActivityStack(ArrayList(), true),
            OEMActivityStack(ArrayList(), true),
            OEMSplitAttributes.Builder()
                .setSplitType(OEMSplitAttributes.SplitType.ExpandContainersSplitType())
                .build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.expandContainers())
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testTranslateSplitInfoWithApiLevel1() {
        assumeTrue(ExtensionsUtil.safeVendorApiLevel == WindowExtensions.VENDOR_API_LEVEL_1)

        val activityStack = OEMActivityStack(ArrayList<Activity>(), true)
        val expectedSplitRatio = 0.3f
        val oemSplitInfo = mock(OEMSplitInfo::class.java)
        doReturn(activityStack).`when`(oemSplitInfo).primaryActivityStack
        doReturn(activityStack).`when`(oemSplitInfo).secondaryActivityStack
        doReturn(expectedSplitRatio).`when`(oemSplitInfo).splitRatio

        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.ratio(expectedSplitRatio))
                // OEMSplitInfo with Vendor API level 1 doesn't provide layoutDirection.
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Test
    fun testTranslateSplitInfoWithApiLevel2() {
        assumeTrue(ExtensionsUtil.safeVendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2)

        val oemSplitInfo = OEMSplitInfo(
            OEMActivityStack(ArrayList<Activity>(), true),
            OEMActivityStack(ArrayList<Activity>(), true),
            OEMSplitAttributes.Builder()
                .setSplitType(
                    OEMSplitAttributes.SplitType.HingeSplitType(
                        OEMSplitAttributes.SplitType.RatioSplitType(0.3f)
                    )
                ).setLayoutDirection(OEMSplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.splitByHinge(SplitType.ratio(0.3f)))
                .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }
}