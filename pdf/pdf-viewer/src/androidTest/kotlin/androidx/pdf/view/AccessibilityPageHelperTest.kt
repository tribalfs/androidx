/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.pdf.view

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.pdf.content.PdfPageTextContent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@LargeTest
class AccessibilityPageHelperTest {
    private lateinit var pdfView: PdfView
    private lateinit var activityScenario: ActivityScenario<PdfViewTestActivity>

    private val pdfDocument =
        FakePdfDocument(
            pages = List(10) { Point(100, 200) },
            textContents =
                List(10) { index ->
                    PdfPageTextContent(
                        bounds = listOf(RectF(0f, 0f, 100f, 200f)),
                        text = "Sample text for page ${index + 1}"
                    )
                }
        )

    @Before
    fun setupPdfView() {
        // Setup the test activity to host the PdfView
        PdfViewTestActivity.onCreateCallback = { activity ->
            val container =
                FrameLayout(activity).apply {
                    addView(
                        PdfView(activity).apply {
                            this.pdfDocument = pdfDocument
                            id = PDF_VIEW_ID
                        },
                        ViewGroup.LayoutParams(100, 1000)
                    )
                }
            activity.setContentView(container)
        }

        activityScenario =
            ActivityScenario.launch(PdfViewTestActivity::class.java).onActivity { activity ->
                pdfView = activity.findViewById<PdfView>(PDF_VIEW_ID)
                requireNotNull(pdfView) { "PdfView must not be null." }
                pdfView.pdfDocument = pdfDocument
            }
    }

    @After
    fun closeActivityScenario() {
        activityScenario.close()
    }

    @Test
    fun getVirtualViewAt_returnsCorrectPage() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 2)

        // Test cases with content coordinates and expected page indices
        val testCases =
            listOf(
                Triple(25f, 25f, 0), // Maps to page 0
                Triple(25f, 250f, 1), // Maps to page 1
                Triple(0f, 0f, 0), // Maps to the very start of the first page
                Triple(0f, 100f, 0), // Edge of the first page
                Triple(110f, 25f, -1), // Outside valid page bounds
                Triple(-10f, -10f, -1), // Outside viewport
            )

        testCases.forEach { (x, y, expectedPage) ->
            val adjustedX = pdfView.toViewCoord(x, pdfView.zoom, pdfView.scrollX)
            val adjustedY = pdfView.toViewCoord(y, pdfView.zoom, pdfView.scrollY)

            assertThat(accessibilityPageHelper.getVirtualViewAt(adjustedX, adjustedY))
                .isEqualTo(expectedPage)
        }
    }

    @Test
    fun getVisibleVirtualViews_returnsCorrectPages() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 4)
        Espresso.onView(withId(PDF_VIEW_ID))
            .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)

        val visiblePages = mutableListOf<Int>()
        accessibilityPageHelper.getVisibleVirtualViews(visiblePages)
        assertThat(visiblePages).isEqualTo(listOf(0, 1, 2, 3, 4))
    }

    @Test
    fun onPopulateNodeForVirtualView_setsCorrectContentDescriptionAndFocusability() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 4)
        Espresso.onView(withId(PDF_VIEW_ID))
            .checkPagesAreVisible(firstVisiblePage = 0, visiblePages = 5)

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        val virtualViewId = 1 // Page 2

        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
        val expectedDescription = "Page 2: Sample text for page 2"
        verify(node).contentDescription = expectedDescription
        verify(node).isFocusable = true
    }

    @Test
    fun onPopulateNodeForVirtualView_setsCorrectBounds() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 0)

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        val virtualViewId = 0 // Page 1

        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)

        // Verify that bounds are set correctly.
        val boundsInParent = Rect(0, 0, 100, 200)
        val expectedBounds = accessibilityPageHelper.scalePageBounds(boundsInParent, pdfView.zoom)

        verify(node).let {
            accessibilityPageHelper.setBoundsInScreenFromBoundsInParent(node, expectedBounds)
        }
    }

    @Test
    fun onPageTextReady_updatesAccessibilityNode() = runTest {
        val accessibilityPageHelper =
            requireNotNull(pdfView.accessibilityPageHelper) {
                "AccessibilityPageHelper must not be null."
            }

        // Wait until layout completes for the required pages
        pdfDocument.waitForLayout(untilPage = 1)

        val node = mock(AccessibilityNodeInfoCompat::class.java)
        var virtualViewId = 0 // Page 1

        accessibilityPageHelper.onPageTextReady(virtualViewId)

        // Verify content description is set as expected for Page 1
        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
        verify(node).contentDescription = "Page 1: Sample text for page 1"

        // Verify default content description for non-visible page
        virtualViewId = 7 // Page 8
        accessibilityPageHelper.onPopulateNodeForVirtualView(virtualViewId, node)
        verify(node).contentDescription = "Page 8" // Default value
    }
}

/** Arbitrary fixed ID for PdfView */
private const val PDF_VIEW_ID = 123456789
