/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.viewpager2.widget

import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_DOWN
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_LEFT
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_RIGHT
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_PAGE_UP
import androidx.test.filters.LargeTest
import androidx.viewpager2.LocaleTestUtils
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
@LargeTest
class PageAccessibilityActionsTest(private val config: TestConfig) : BaseTest() {
    data class TestConfig(
        @ViewPager2.Orientation val orientation: Int,
        val rtl: Boolean
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun spec(): List<TestConfig> = createTestSet()
    }

    override fun setUp() {
        super.setUp()
        if (config.rtl) {
            localeUtil.resetLocale()
            localeUtil.setLocale(LocaleTestUtils.RTL_LANGUAGE)
        }
    }

    @Test
    fun test_onPerformPageAction_onHorizontalOrientation() {
        setUpTest(config.orientation).apply {
            setAdapterSync(viewAdapterProvider(stringSequence(6)))

            val initialPage = viewPager.currentItem
            assertBasicState(initialPage)

            listOf(1, 2, 3, 2, 3, 2, 3, 4, 5, 4, 5, 4, 3, 2, 1, 0, 1).forEach {
                    targetPage ->
                val currentPage = viewPager.currentItem
                val latch = viewPager.addWaitForScrolledLatch(targetPage)
                if (targetPage - currentPage == 1) {
                    ViewCompat.performAccessibilityAction(viewPager,
                        getNextPageAction(config.orientation, isRtl), null)
                } else {
                    ViewCompat.performAccessibilityAction(viewPager,
                        getPreviousPageAction(config.orientation, isRtl), null)
                }
                latch.await(1, TimeUnit.SECONDS)
                assertBasicState(targetPage)
            }
        }
    }

    @Test
    fun test_onOrientationChange() {
        setUpTest(config.orientation).apply {
            setAdapterSync(viewAdapterProvider(stringSequence(2)))

            val initialPage = viewPager.currentItem
            assertBasicState(initialPage)

            activityTestRule.runOnUiThread {
                viewPager.setOrientation(getOppositeOrientation(config.orientation))
            }
            assertBasicState(initialPage)
        }
    }

    private fun getNextPageAction(orientation: Int, isRtl: Boolean): Int {
        if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            if (isRtl) {
                return ACTION_PAGE_LEFT.id
            } else {
                return ACTION_PAGE_RIGHT.id
            }
        }
        return ACTION_PAGE_DOWN.id
    }

    private fun getPreviousPageAction(orientation: Int, isRtl: Boolean): Int {
        if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            if (isRtl) {
                return ACTION_PAGE_RIGHT.id
            } else {
                return ACTION_PAGE_LEFT.id
            }
        }
        return ACTION_PAGE_UP.id
    }

    private fun getOppositeOrientation(orientation: Int): Int {
        if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            return ViewPager2.ORIENTATION_VERTICAL
        } else {
            return ViewPager2.ORIENTATION_HORIZONTAL
        }
    }
}

// region Test Suite creation

private fun createTestSet(): List<PageAccessibilityActionsTest.TestConfig> {
    return listOf(ORIENTATION_HORIZONTAL, ORIENTATION_VERTICAL).flatMap { orientation ->
        listOf(true, false).map { rtl ->
            PageAccessibilityActionsTest.TestConfig(orientation, rtl)
        }
    }
}

// endregion
