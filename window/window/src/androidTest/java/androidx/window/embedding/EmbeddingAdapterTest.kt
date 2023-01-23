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
import android.graphics.Color
import androidx.window.WindowTestUtils
import androidx.window.core.PredicateAdapter
import androidx.window.embedding.SplitAttributes.SplitType
import androidx.window.extensions.WindowExtensions
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection.TOP_TO_BOTTOM
import androidx.window.extensions.embedding.SplitAttributes.SplitType.ExpandContainersSplitType
import androidx.window.extensions.embedding.SplitAttributes.SplitType.HingeSplitType
import androidx.window.extensions.embedding.SplitAttributes.SplitType.RatioSplitType
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        WindowTestUtils.assumeAtLeastVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val mockPrimaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSecondaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSplitAttributes = mock<OEMSplitAttributes>().apply {
            whenever(splitType).thenReturn(RatioSplitType.splitEqually())
        }
        val oemSplitInfo = mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(mockPrimaryActivityStack)
            whenever(secondaryActivityStack).thenReturn(mockSecondaryActivityStack)
            whenever(splitAttributes).thenReturn(mockSplitAttributes)
        }
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.splitEqually())
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .setAnimationBackgroundColor(0)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Test
    fun testTranslateSplitInfoWithExpandingContainers() {
        WindowTestUtils.assumeAtLeastVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val mockPrimaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSecondaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSplitAttributes = mock<OEMSplitAttributes>().apply {
            whenever(splitType).thenReturn(ExpandContainersSplitType())
        }
        val oemSplitInfo = mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(mockPrimaryActivityStack)
            whenever(secondaryActivityStack).thenReturn(mockSecondaryActivityStack)
            whenever(splitAttributes).thenReturn(mockSplitAttributes)
        }
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
        WindowTestUtils.assumeBeforeVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val expectedSplitRatio = 0.3f
        val mockPrimaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSecondaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val oemSplitInfo = mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(mockPrimaryActivityStack)
            whenever(secondaryActivityStack).thenReturn(mockSecondaryActivityStack)
            whenever(splitRatio).thenReturn(expectedSplitRatio)
        }

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
        WindowTestUtils.assumeAtLeastVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val mockPrimaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSecondaryActivityStack = mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(emptyList())
            whenever(isEmpty).thenReturn(true)
        }
        val mockSplitAttributes = mock<OEMSplitAttributes>().apply {
            whenever(splitType).thenReturn(HingeSplitType(RatioSplitType(0.3f)))
            whenever(layoutDirection).thenReturn(TOP_TO_BOTTOM)
            whenever(animationBackgroundColor).thenReturn(Color.YELLOW)
        }
        val oemSplitInfo = mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(mockPrimaryActivityStack)
            whenever(secondaryActivityStack).thenReturn(mockSecondaryActivityStack)
            whenever(splitAttributes).thenReturn(mockSplitAttributes)
        }
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.splitByHinge(SplitType.ratio(0.3f)))
                .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .setAnimationBackgroundColor(Color.YELLOW)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }
}