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

import android.graphics.Path
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.test.filters.LargeTest
import androidx.viewpager2.LocaleTestUtils
import androidx.viewpager2.widget.BaseTest.Context.SwipeMethod
import androidx.viewpager2.widget.FakeDragTest.Event.OnPageScrollStateChangedEvent
import androidx.viewpager2.widget.FakeDragTest.Event.OnPageScrolledEvent
import androidx.viewpager2.widget.FakeDragTest.Event.OnPageSelectedEvent
import androidx.viewpager2.widget.FakeDragTest.TestConfig
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL
import androidx.viewpager2.widget.swipe.PageSwiperFakeDrag
import androidx.viewpager2.widget.swipe.PageSwiperManual
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.Matchers.greaterThan
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.math.roundToInt
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING as DRAGGING
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE as IDLE
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_SETTLING as SETTLING

@RunWith(Parameterized::class)
@LargeTest
class FakeDragTest(private val config: TestConfig) : BaseTest() {
    data class TestConfig(
        @ViewPager2.Orientation val orientation: Int,
        val rtl: Boolean,
        val enableUserInput: Boolean
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun spec(): List<TestConfig> = createTestSet()
    }

    private val pageCount = 10
    private lateinit var test: Context
    private lateinit var adapterProvider: AdapterProvider
    private lateinit var fakeDragger: PageSwiperFakeDrag

    // Used to overcome touch slop and gently slide forward.
    // Similar to but better than DecelerateInterpolator in this case.
    private val quadInterpolator = PathInterpolatorCompat.create(Path().also {
        it.quadTo(0f, 1f, 1f, 1f)
    })

    override fun setUp() {
        super.setUp()
        assumeApiBeforeQ()
        if (config.rtl) {
            localeUtil.resetLocale()
            localeUtil.setLocale(LocaleTestUtils.RTL_LANGUAGE)
        }
        adapterProvider = viewAdapterProvider(stringSequence(pageCount))
        test = setUpTest(config.orientation).also {
            fakeDragger = PageSwiperFakeDrag(it.viewPager) { it.viewPager.pageSize }
            it.viewPager.isUserInputEnabled = config.enableUserInput
            it.setAdapterSync(adapterProvider)
            it.assertBasicState(0)
        }
    }

    @Test
    fun test_flingToNextPage() {
        basicFakeDragTest(.2f, 100, 1, suppressFling = false)
    }

    @Test
    fun test_peekNextPage() {
        basicFakeDragTest(.1f, 200, 0, DecelerateInterpolator(), true)
    }

    @Test
    fun test_flingCompletelyToNextPage() {
        basicFakeDragTest(1f, 100, 1, suppressFling = false)
    }

    @Test
    fun test_peekNextAndMoveBack() {
        // Roughly interpolates like this:
        //   |
        // 3 |   .-.
        //   |  /   ',
        // 1 | /      '-.___
        //   |/
        // 0 +--------------
        //   0             1
        basicFakeDragTest(.2f, 300, 0, PathInterpolatorCompat.create(Path().also {
            it.moveTo(0f, 0f)
            it.cubicTo(.4f, 6f, .5f, 1f, .8f, 1f)
            it.lineTo(1f, 1f)
        }), true)
    }

    @Test
    fun test_dragAlmostToNextPageAndFlingBack() {
        // Roughly interpolates like this:
        //   |
        //   |   .-.
        // 1 |  /   '
        //   | /
        //   |/
        // 0 +-------
        //   0      1
        basicFakeDragTest(.7f, 200, 0, PathInterpolatorCompat.create(Path().also {
            it.moveTo(0f, 0f)
            it.cubicTo(.4f, 1.3f, .7f, 1.5f, 1f, 1f)
        }), false)
    }

    @Test
    fun test_startFakeDragDuringManualDrag() {
        // Skip tests where manual dragging is disabled
        assumeThat(config.enableUserInput, equalTo(true))

        // start manual drag
        val latch = test.viewPager.addWaitForStateLatch(DRAGGING)
        // Perform manual swipe in separate thread, because the SwipeMethod.MANUAL blocks while
        // injecting events, and we need to interrupt it
        newSingleThreadExecutor().execute { test.swipeForward(SwipeMethod.MANUAL) }
        assertThat(latch.await(1, SECONDS), equalTo(true))

        // start fake drag
        assertThat(test.viewPager.beginFakeDrag(), equalTo(false))
    }

    @Test
    fun test_startFakeDragToTargetPageWhileSettling() {
        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val targetPage = test.viewPager.currentItem + 1
            startFakeDragWhileSettling(targetPage, .2f, .2f, targetPage)
        }
    }

    @Test
    fun test_startFakeDragExactlyToTargetPageWhileSettling() {
        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val tracker = PositionTracker().also { test.viewPager.registerOnPageChangeCallback(it) }
            val targetPage = test.viewPager.currentItem + 1
            startFakeDragWhileSettling(targetPage, .4f,
                { targetPage - tracker.lastPosition }, targetPage, true)
            test.viewPager.unregisterOnPageChangeCallback(tracker)
        }
    }

    @Test
    fun test_startFakeDragToNextPageWhileSettling() {
        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val targetPage = test.viewPager.currentItem + 1
            startFakeDragWhileSettling(targetPage, .5f, 1f, targetPage + 1)
        }
    }

    @Test
    fun test_startFakeDragExactlyToNextPageWhileSettling() {
        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val tracker = PositionTracker().also { test.viewPager.registerOnPageChangeCallback(it) }
            val targetPage = test.viewPager.currentItem + 1
            val nextPage = targetPage + 1
            startFakeDragWhileSettling(targetPage, .5f,
                { nextPage - tracker.lastPosition }, nextPage, true)
            test.viewPager.unregisterOnPageChangeCallback(tracker)
        }
    }

    @Test
    fun test_setCurrentItemDuringFakeDrag() {
        setCurrentItemDuringFakeDrag(false)
    }

    @Test
    fun test_smoothScrollDuringFakeDrag() {
        setCurrentItemDuringFakeDrag(true)
    }

    /*
     * Fake drag, interrupted by manual drag after 0.1 page has been fake-dragged
     *
     * > Starting fake drag
     * onPageScrollStateChanged(1)
     * onPageScrolled(0, 0.016, 24)
     * onPageScrolled(0, 0.032, 48)
     * ...
     * onPageScrolled(0, 0.096, 145)
     * onPageScrolled(0, 0.112, 169)
     * > Starting manual drag
     * onPageScrolled(0, 0.129, 194)
     * onPageScrolled(0, 0.187, 281)
     * ...
     * onPageScrolled(0, 0.616, 925)
     * onPageScrolled(0, 0.669, 1004)
     * onPageScrollStateChanged(2)
     * onPageSelected(1)
     * onPageScrolled(0, 0.706, 1059)
     * onPageScrolled(0, 0.740, 1111)
     * ...
     * onPageScrolled(0, 0.999, 1499)
     * onPageScrolled(1, 0.0, 0)
     * onPageScrollStateChanged(0)
     */
    @Test
    fun test_startManualDragDuringFakeDrag() {
        startManualDragDuringFakeDrag(.5f, 500) {
            test.swipeForward(SwipeMethod.MANUAL)
        }
    }

    /*
     * Fake drag, interrupted by manual drag after 1.1 page has been fake-dragged
     *
     * > Starting fake drag
     * onPageScrollStateChanged(1)
     * onPageScrolled(0, 0.040, 61)
     * onPageScrolled(0, 0.082, 123)
     * ...
     * onPageScrolled(1, 0.063, 95)
     * onPageScrolled(1, 0.104, 157)
     * > Starting manual drag
     * onPageScrolled(1, 0.145, 218)
     * onPageScrolled(1, 0.173, 260)
     * ...
     * onPageScrolled(1, 0.201, 302)
     * onPageScrolled(1, 0.202, 303)
     * onPageScrollStateChanged(2)
     * onPageSelected(1)
     * onPageScrolled(1, 0.165, 248)
     * onPageScrolled(1, 0.128, 193)
     * ...
     * onPageScrolled(1, 0.001, 1)
     * onPageScrolled(1, 0.0, 0)
     * onPageScrollStateChanged(0)
     */
    @Test
    fun test_startManualPeekAfterFakeDrag1Page() {
        val vc = ViewConfiguration.get(test.viewPager.context)
        val touchSlop = vc.scaledTouchSlop
        startManualDragDuringFakeDrag(1.8f, 700, 1) {
            PageSwiperManual(test.viewPager, config.rtl)
                .swipeForward(touchSlop * 5f, quadInterpolator)
        }
    }

    private fun basicFakeDragTest(
        relativeDragDistance: Float,
        duration: Long,
        expectedFinalPage: Int,
        interpolator: Interpolator = LinearInterpolator(),
        suppressFling: Boolean = false
    ) {
        val startPage = test.viewPager.currentItem
        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val initialPage = test.viewPager.currentItem
            val expectedFinalPageWithOffset = expectedFinalPage + initialPage - startPage
            val recorder = test.viewPager.addNewRecordingCallback()

            val latch = test.viewPager.addWaitForIdleLatch()
            fakeDragger.fakeDrag(relativeDragDistance, duration, interpolator, suppressFling)
            latch.await(2000 + duration, MILLISECONDS)

            // test assertions
            test.assertBasicState(expectedFinalPageWithOffset)
            recorder.apply {
                scrollEvents.assertValueSanity(0, pageCount - 1, test.viewPager.pageSize)
                assertFirstEvents(DRAGGING)
                assertLastEvents(expectedFinalPageWithOffset)
                assertPageSelectedEvents(initialPage, expectedFinalPageWithOffset)
                assertStateChanges(
                    listOf(DRAGGING, SETTLING, IDLE),
                    listOf(DRAGGING, IDLE)
                )
            }

            test.viewPager.unregisterOnPageChangeCallback(recorder)
        }
    }

    private fun startFakeDragWhileSettling(
        settleTarget: Int,
        settleDistance: Float,
        dragDistance: Float,
        expectedFinalPage: Int
    ) {
        startFakeDragWhileSettling(settleTarget, settleDistance,
            { dragDistance }, expectedFinalPage, false)
    }

    private fun startFakeDragWhileSettling(
        settleTarget: Int,
        settleDistance: Float,
        dragDistance: () -> Float,
        expectedFinalPage: Int,
        fakeDragMustEndSnapped: Boolean
    ) {
        val initialPage = test.viewPager.currentItem
        val recorder = test.viewPager.addNewRecordingCallback()

        // start smooth scroll
        val threshold = 1f - settleDistance
        val scrollLatch = test.viewPager.addWaitForDistanceToTarget(settleTarget, threshold)
        test.runOnUiThread { test.viewPager.setCurrentItem(settleTarget, true) }
        assertThat(scrollLatch.await(1, SECONDS), equalTo(true))

        // start fake drag
        val idleLatch = test.viewPager.addWaitForIdleLatch()
        fakeDragger.fakeDrag(dragDistance(), 100)
        assertThat(idleLatch.await(2, SECONDS), equalTo(true))

        // test assertions
        test.assertBasicState(expectedFinalPage)
        recorder.apply {
            scrollEvents.assertValueSanity(0, pageCount - 1, test.viewPager.pageSize)
            assertFirstEvents(SETTLING)
            assertLastEvents(expectedFinalPage)
            assertPageSelectedEvents(initialPage, settleTarget, expectedFinalPage)
            if (fakeDragMustEndSnapped) {
                assertThat("When a fake drag should end in a snapped position, we expect the last" +
                        " scroll event after the FAKE_DRAG event to be snapped. ${dumpEvents()}",
                    expectSettlingAfterState(DRAGGING), equalTo(false))
            }
            assertStateChanges(
                listOf(SETTLING, DRAGGING, SETTLING, IDLE),
                listOf(SETTLING, DRAGGING, IDLE)
            )
        }

        test.viewPager.unregisterOnPageChangeCallback(recorder)
    }

    private fun setCurrentItemDuringFakeDrag(smoothScroll: Boolean) {
        val initialPage = test.viewPager.currentItem
        // start fake drag
        val latch = test.viewPager.addWaitForStateLatch(DRAGGING)
        fakeDragger.fakeDrag(.5f, 500)
        assertThat(latch.await(1, SECONDS), equalTo(true))

        // start smooth scroll
        doIllegalAction("Cannot change current item when ViewPager2 is fake dragging") {
            test.viewPager.setCurrentItem(initialPage + 1, smoothScroll)
        }
    }

    private fun doIllegalAction(errorMessage: String, action: () -> Unit) {
        val executionLatch = CountDownLatch(1)
        var exception: IllegalStateException? = null
        test.runOnUiThread {
            try {
                action()
            } catch (e: IllegalStateException) {
                exception = e
            } finally {
                executionLatch.countDown()
            }
        }
        assertThat(executionLatch.await(1, SECONDS), equalTo(true))
        assertThat(exception, notNullValue())
        assertThat(exception!!.message, equalTo(errorMessage))
    }

    private fun startManualDragDuringFakeDrag(
        fakeDragDistance: Float,
        fakeDragDuration: Long,
        referencePageOffset: Int = 0,
        manualDragCallback: () -> Unit
    ) {
        // Skip tests where manual dragging is disabled
        assumeThat(config.enableUserInput, equalTo(true))

        // Run the test two times to verify that state doesn't linger
        repeat(2) {
            val initialPage = test.viewPager.currentItem
            val expectedFinalPage = initialPage + 1
            val recorder = test.viewPager.addNewRecordingCallback()

            // start fake drag
            val fakeDragLatch = test.viewPager.addWaitForDistanceToTarget(
                expectedFinalPage + referencePageOffset, .9f)
            val idleLatch = test.viewPager.addWaitForIdleLatch()
            fakeDragger.fakeDrag(fakeDragDistance, fakeDragDuration)
            assertThat(fakeDragLatch.await(5, SECONDS), equalTo(true))

            // start manual drag
            manualDragCallback()
            assertThat(idleLatch.await(2, SECONDS), equalTo(true))

            // test assertions
            test.assertBasicState(expectedFinalPage)
            recorder.apply {
                scrollEvents.assertValueSanity(0, pageCount - 1, test.viewPager.pageSize)
                assertFirstEvents(DRAGGING)
                assertLastEvents(expectedFinalPage)
                assertPageSelectedEvents(initialPage, expectedFinalPage)
                assertStateChanges(
                    listOf(DRAGGING, SETTLING, IDLE),
                    listOf(DRAGGING, IDLE)
                )
            }

            test.viewPager.unregisterOnPageChangeCallback(recorder)
        }
    }

    private fun ViewPager2.addNewRecordingCallback(): RecordingCallback {
        return RecordingCallback().also { registerOnPageChangeCallback(it) }
    }

    private sealed class Event {
        data class OnPageScrolledEvent(
            val position: Int,
            val positionOffset: Float,
            val positionOffsetPixels: Int
        ) : Event()
        data class OnPageSelectedEvent(val position: Int) : Event()
        data class OnPageScrollStateChangedEvent(val state: Int) : Event()
    }

    private class RecordingCallback : ViewPager2.OnPageChangeCallback() {
        private val events = mutableListOf<Event>()

        val allEvents get() = events.toList()
        val scrollEvents get() = events.mapNotNull { it as? OnPageScrolledEvent }
        val stateEvents get() = events.mapNotNull { it as? OnPageScrollStateChangedEvent }
        val selectEvents get() = events.mapNotNull { it as? OnPageSelectedEvent }

        val eventCount get() = events.size
        val firstEvent get() = events.firstOrNull()
        val lastEvent get() = events.lastOrNull()

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            synchronized(events) {
                events.add(OnPageScrolledEvent(position, positionOffset, positionOffsetPixels))
            }
        }

        override fun onPageSelected(position: Int) {
            synchronized(events) {
                events.add(OnPageSelectedEvent(position))
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            synchronized(events) {
                events.add(OnPageScrollStateChangedEvent(state))
            }
        }

        fun expectSettlingAfterState(state: Int): Boolean {
            val changeToStateEvent = OnPageScrollStateChangedEvent(state)
            val lastScrollEvent = events
                .dropWhile { it != changeToStateEvent }
                .dropWhile { it !is OnPageScrolledEvent }
                .takeWhile { it is OnPageScrolledEvent }
                .lastOrNull() as? OnPageScrolledEvent
            return lastScrollEvent?.let { it.positionOffsetPixels != 0 } ?: true
        }

        fun dumpEvents(): String {
            return events.joinToString("\n- ", "\n(${scrollStateGlossary()})\n- ")
        }
    }

    private fun RecordingCallback.assertFirstEvents(expectedFirstState: Int) {
        assertThat("There should be events", eventCount, greaterThan(0))
        assertThat("First event should be state change to " +
                "${scrollStateToString(expectedFirstState)}: ${dumpEvents()}",
            firstEvent, equalTo(OnPageScrollStateChangedEvent(expectedFirstState) as Event))
    }

    private fun RecordingCallback.assertLastEvents(expectedFinalPage: Int) {
        assertThat("Last event should be state change to IDLE: ${dumpEvents()}",
            lastEvent, equalTo(OnPageScrollStateChangedEvent(IDLE) as Event))
        assertThat("Scroll events don't end in snapped position: ${dumpEvents()}",
            scrollEvents.last().positionOffsetPixels, equalTo(0))
        assertThat("Scroll events don't end at page $expectedFinalPage: ${dumpEvents()}",
            scrollEvents.last().position, equalTo(expectedFinalPage))
    }

    private fun RecordingCallback.assertPageSelectedEvents(vararg visitedPages: Int) {
        val expectedPageSelects = visitedPages.toList().zipWithNext().mapNotNull { pair ->
            // If visited page is same as previous page, no page selected event should be fired
            if (pair.first == pair.second) null else pair.second
        }
        assertThat("Sequence of selected pages should be $expectedPageSelects: ${dumpEvents()}",
            selectEvents.map { it.position }, equalTo(expectedPageSelects))

        val settleEvent = OnPageScrollStateChangedEvent(SETTLING)
        val idleEvent = OnPageScrollStateChangedEvent(IDLE)
        val events = allEvents
        events.forEachIndexed { i, event ->
            if (event is OnPageSelectedEvent) {
                assertThat("OnPageSelectedEvents cannot be the first or last event: " +
                        dumpEvents(), i, isBetweenInEx(1, eventCount - 1))
                val isAfterSettleEvent = events[i - 1] == settleEvent
                val isBeforeIdleEvent = events[i + 1] == idleEvent
                assertThat("OnPageSelectedEvent at index $i must follow a SETTLE event or precede" +
                        " an IDLE event, but not both: ${dumpEvents()}",
                    isAfterSettleEvent.xor(isBeforeIdleEvent), equalTo(true))
            }
        }
    }

    private fun RecordingCallback.assertStateChanges(
        statesWithSettling: List<Int>,
        statesWithoutSettling: List<Int>
    ) {
        assertThat(
            "Unexpected sequence of state changes:" + dumpEvents(),
            stateEvents.map { it.state },
            equalTo(
                if (expectSettlingAfterState(DRAGGING)) {
                    statesWithSettling
                } else {
                    statesWithoutSettling
                }
            )
        )
    }

    private fun List<OnPageScrolledEvent>.assertValueSanity(
        initialPage: Int,
        otherPage: Int,
        pageSize: Int
    ) = forEach {
        assertThat(it.position, isBetweenInInMinMax(initialPage, otherPage))
        assertThat(it.positionOffset, isBetweenInEx(0f, 1f))
        assertThat((it.positionOffset * pageSize).roundToInt(), equalTo(it.positionOffsetPixels))
    }

    private class PositionTracker : ViewPager2.OnPageChangeCallback() {
        var lastPosition = 0f
        override fun onPageScrolled(position: Int, offset: Float, offsetPx: Int) {
            lastPosition = position + offset
        }
    }
}

private fun createTestSet(): List<TestConfig> {
    return listOf(ORIENTATION_HORIZONTAL, ORIENTATION_VERTICAL).flatMap { orientation ->
        listOf(false, true).flatMap { rtl ->
            listOf(true, false).map { enableUserInput ->
                TestConfig(orientation, rtl, enableUserInput)
            }
        }
    }
}