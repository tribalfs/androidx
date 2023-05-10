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

import android.app.Activity
import androidx.window.WindowTestUtils
import androidx.window.core.ExtensionsUtil
import androidx.window.core.PredicateAdapter
import androidx.window.embedding.SplitAttributes.SplitType
import androidx.window.embedding.SplitAttributes.SplitType.Companion.SPLIT_TYPE_HINGE
import androidx.window.extensions.WindowExtensions
import androidx.window.extensions.embedding.ActivityStack as OEMActivityStack
import androidx.window.extensions.embedding.SplitAttributes as OEMSplitAttributes
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection.TOP_TO_BOTTOM
import androidx.window.extensions.embedding.SplitAttributes.SplitType.RatioSplitType
import androidx.window.extensions.embedding.SplitInfo as OEMSplitInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

        val oemSplitInfo = createTestOEMSplitInfo(
            createTestOEMActivityStack(ArrayList(), true),
            createTestOEMActivityStack(ArrayList(), true),
            OEMSplitAttributes.Builder().build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.SPLIT_TYPE_EQUAL)
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Test
    fun testTranslateSplitInfoWithExpandingContainers() {
        WindowTestUtils.assumeAtLeastVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val oemSplitInfo = createTestOEMSplitInfo(
            createTestOEMActivityStack(ArrayList(), true),
            createTestOEMActivityStack(ArrayList(), true),
            OEMSplitAttributes.Builder()
                .setSplitType(OEMSplitAttributes.SplitType.ExpandContainersSplitType())
                .build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SplitType.SPLIT_TYPE_EXPAND)
                .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testTranslateSplitInfoWithApiLevel1() {
        WindowTestUtils.assumeBeforeVendorApiLevel(WindowExtensions.VENDOR_API_LEVEL_2)

        val activityStack = createTestOEMActivityStack(ArrayList(), true)
        val expectedSplitRatio = 0.3f
        val oemSplitInfo = mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(activityStack)
            whenever(secondaryActivityStack).thenReturn(activityStack)
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

        val oemSplitInfo = createTestOEMSplitInfo(
            createTestOEMActivityStack(ArrayList(), true),
            createTestOEMActivityStack(ArrayList(), true),
            OEMSplitAttributes.Builder()
                .setSplitType(OEMSplitAttributes.SplitType.HingeSplitType(RatioSplitType(0.5f)))
                .setLayoutDirection(TOP_TO_BOTTOM)
                .build(),
        )
        val expectedSplitInfo = SplitInfo(
            ActivityStack(ArrayList(), isEmpty = true),
            ActivityStack(ArrayList(), isEmpty = true),
            SplitAttributes.Builder()
                .setSplitType(SPLIT_TYPE_HINGE)
                .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
                .build()
        )
        assertEquals(listOf(expectedSplitInfo), adapter.translate(listOf(oemSplitInfo)))
    }

    private fun createTestOEMSplitInfo(
        testPrimaryActivityStack: OEMActivityStack,
        testSecondaryActivityStack: OEMActivityStack,
        testSplitAttributes: OEMSplitAttributes,
    ): OEMSplitInfo {
        return mock<OEMSplitInfo>().apply {
            whenever(primaryActivityStack).thenReturn(testPrimaryActivityStack)
            whenever(secondaryActivityStack).thenReturn(testSecondaryActivityStack)
            if (ExtensionsUtil.safeVendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
                whenever(splitAttributes).thenReturn(testSplitAttributes)
            }
        }
    }

    private fun createTestOEMActivityStack(
        testActivities: List<Activity>,
        testIsEmpty: Boolean,
    ): OEMActivityStack {
        return mock<OEMActivityStack>().apply {
            whenever(activities).thenReturn(testActivities)
            whenever(isEmpty).thenReturn(testIsEmpty)
        }
    }
}