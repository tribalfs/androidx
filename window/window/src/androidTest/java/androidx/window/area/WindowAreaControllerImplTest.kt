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

package androidx.window.area

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Binder
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.window.TestActivity
import androidx.window.WindowTestUtils.Companion.assumeAtLeastVendorApiLevel
import androidx.window.area.WindowAreaCapability.Operation.Companion.OPERATION_PRESENT_ON_AREA
import androidx.window.area.WindowAreaCapability.Operation.Companion.OPERATION_TRANSFER_ACTIVITY_TO_AREA
import androidx.window.area.WindowAreaCapability.Status.Companion.WINDOW_AREA_STATUS_AVAILABLE
import androidx.window.area.WindowAreaCapability.Status.Companion.WINDOW_AREA_STATUS_UNAVAILABLE
import androidx.window.core.Bounds
import androidx.window.extensions.area.ExtensionWindowAreaPresentation
import androidx.window.extensions.area.ExtensionWindowAreaStatus
import androidx.window.extensions.area.WindowAreaComponent
import androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_ACTIVE
import androidx.window.extensions.area.WindowAreaComponent.SESSION_STATE_INACTIVE
import androidx.window.extensions.area.WindowAreaComponent.STATUS_ACTIVE
import androidx.window.extensions.area.WindowAreaComponent.STATUS_AVAILABLE
import androidx.window.extensions.area.WindowAreaComponent.STATUS_UNAVAILABLE
import androidx.window.extensions.area.WindowAreaComponent.STATUS_UNSUPPORTED
import androidx.window.extensions.core.util.function.Consumer
import androidx.window.layout.WindowMetrics
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WindowAreaControllerImplTest {

    @get:Rule
    public val activityScenario: ActivityScenarioRule<TestActivity> =
        ActivityScenarioRule(TestActivity::class.java)

    private val testScope = TestScope(UnconfinedTestDispatcher())

    /**
     * Tests that we can get a list of [WindowAreaInfo] objects with a type of
     * [WindowAreaInfo.Type.TYPE_REAR_FACING]. Verifies that updating the status of features on
     * device returns an updated [WindowAreaInfo] list.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testRearFacingWindowAreaInfoList(): Unit = testScope.runTest {
        assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
        assumeAtLeastVendorApiLevel(2)
        activityScenario.scenario.onActivity {
            val extensionComponent = FakeWindowAreaComponent()
            val controller = WindowAreaControllerImpl(
                windowAreaComponent = extensionComponent,
                vendorApiLevel = 2
            )
            extensionComponent.currentRearDisplayStatus = STATUS_UNAVAILABLE
            val collector = TestWindowAreaInfoListConsumer()
            testScope.launch(Job()) {
                controller.windowAreaInfos.collect(collector::accept)
            }

            val expectedAreaInfo = WindowAreaInfo(
                metrics = createEmptyWindowMetrics(),
                type = WindowAreaInfo.Type.TYPE_REAR_FACING,
                token = Binder(REAR_FACING_BINDER_DESCRIPTION),
                windowAreaComponent = extensionComponent
            )
            val rearDisplayCapability = WindowAreaCapability(
                OPERATION_TRANSFER_ACTIVITY_TO_AREA,
                WINDOW_AREA_STATUS_UNAVAILABLE
            )
            expectedAreaInfo.capabilityMap[OPERATION_TRANSFER_ACTIVITY_TO_AREA] =
                rearDisplayCapability

            assertEquals(1, collector.values.size)
            assertEquals(listOf(expectedAreaInfo), collector.values[0])

            extensionComponent
                .updateRearDisplayStatusListeners(STATUS_AVAILABLE)

            val updatedAreaInfo = WindowAreaInfo(
                metrics = createEmptyWindowMetrics(),
                type = WindowAreaInfo.Type.TYPE_REAR_FACING,
                token = Binder(REAR_FACING_BINDER_DESCRIPTION),
                windowAreaComponent = extensionComponent
            )
            val updatedRearDisplayCapability = WindowAreaCapability(
                OPERATION_TRANSFER_ACTIVITY_TO_AREA,
                WINDOW_AREA_STATUS_AVAILABLE
            )
            updatedAreaInfo.capabilityMap[OPERATION_TRANSFER_ACTIVITY_TO_AREA] =
                updatedRearDisplayCapability

            assertEquals(2, collector.values.size)
            assertEquals(listOf(updatedAreaInfo), collector.values[1])
        }
    }

    @Test
    public fun testWindowAreaInfoListNullComponent(): Unit = testScope.runTest {
        activityScenario.scenario.onActivity {
            val controller = EmptyWindowAreaControllerImpl()
            val collector = TestWindowAreaInfoListConsumer()
            testScope.launch(Job()) {
                controller.windowAreaInfos.collect(collector::accept)
            }
            assertTrue(collector.values.size == 1)
            assertEquals(listOf(), collector.values[0])
        }
    }

    /**
     * Tests the transfer to rear facing window area flow. Tests the flow
     * through WindowAreaControllerImpl with a fake extension. This fake extension
     * changes the orientation of the activity to landscape to simulate a configuration change that
     * would occur when transferring to the rear facing window area and then returns it back to
     * portrait when it's disabled.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testTransferToRearFacingWindowArea(): Unit = testScope.runTest {
        assumeAtLeastVendorApiLevel(2)
        val extensions = FakeWindowAreaComponent()
        val controller = WindowAreaControllerImpl(
            windowAreaComponent = extensions,
            vendorApiLevel = 2
        )
        extensions.currentRearDisplayStatus = STATUS_AVAILABLE
        val callback = TestWindowAreaSessionCallback()
        var windowAreaInfo: WindowAreaInfo? = null
        testScope.launch(Job()) {
            windowAreaInfo = controller.windowAreaInfos.firstOrNull()
                ?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
        }
        assertNotNull(windowAreaInfo)
        assertEquals(
            windowAreaInfo!!.getCapability(OPERATION_TRANSFER_ACTIVITY_TO_AREA)?.status,
            WINDOW_AREA_STATUS_AVAILABLE
        )

        activityScenario.scenario.onActivity { testActivity ->
            testActivity.resetLayoutCounter()
            testActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            testActivity.waitForLayout()
        }

        activityScenario.scenario.onActivity { testActivity ->
            assert(testActivity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            testActivity.resetLayoutCounter()
            controller.transferActivityToWindowArea(
                windowAreaInfo!!.token,
                testActivity,
                Runnable::run,
                callback
            )
        }

        activityScenario.scenario.onActivity { testActivity ->
            assert(testActivity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assert(callback.currentSession != null)
            testActivity.resetLayoutCounter()
            callback.endSession()
        }
        activityScenario.scenario.onActivity { testActivity ->
            assert(testActivity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assert(callback.currentSession == null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testTransferRearDisplayReturnsError_statusUnavailable(): Unit = testScope.runTest {
        testTransferRearDisplayReturnsError(STATUS_UNAVAILABLE)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testTransferRearDisplayReturnsError_statusActive(): Unit = testScope.runTest {
        testTransferRearDisplayReturnsError(STATUS_ACTIVE)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun testTransferRearDisplayReturnsError(
        initialState: @WindowAreaComponent.WindowAreaStatus Int
    ) {
        assumeAtLeastVendorApiLevel(2)
        val extensions = FakeWindowAreaComponent()
        val controller = WindowAreaControllerImpl(
            windowAreaComponent = extensions,
            vendorApiLevel = 2
        )
        extensions.currentRearDisplayStatus = initialState
        val callback = TestWindowAreaSessionCallback()
        var windowAreaInfo: WindowAreaInfo? = null
        testScope.launch(Job()) {
            windowAreaInfo = controller.windowAreaInfos.firstOrNull()
                ?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
        }
        assertNotNull(windowAreaInfo)
        assertEquals(
            windowAreaInfo!!.getCapability(OPERATION_TRANSFER_ACTIVITY_TO_AREA)?.status,
            WindowAreaAdapter.translate(initialState)
        )

        activityScenario.scenario.onActivity { testActivity ->
            controller.transferActivityToWindowArea(
                windowAreaInfo!!.token,
                testActivity,
                Runnable::run,
                callback
            )
            assertNotNull(callback.error)
            assertNull(callback.currentSession)
        }
    }

    /**
     * Tests the presentation flow on to a rear facing display works as expected. The
     * [WindowAreaPresentationSessionCallback] provided to
     * [WindowAreaControllerImpl.presentContentOnWindowArea] should receive a
     * [WindowAreaSessionPresenter] when the session is active, and be notified that the [View]
     * provided through [WindowAreaSessionPresenter.setContentView] is visible when inflated.
     *
     * Tests the flow through WindowAreaControllerImpl with a fake extension component.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testRearDisplayPresentationMode(): Unit = testScope.runTest {
        assumeAtLeastVendorApiLevel(3)
        val extensions = FakeWindowAreaComponent()
        val controller = WindowAreaControllerImpl(
            windowAreaComponent = extensions,
            vendorApiLevel = 3
        )
        var windowAreaInfo: WindowAreaInfo? = null
        extensions.updateRearDisplayStatusListeners(STATUS_AVAILABLE)
        extensions.updateRearDisplayPresentationStatusListeners(STATUS_AVAILABLE)
        testScope.launch(Job()) {
            windowAreaInfo = controller.windowAreaInfos.firstOrNull()
                ?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
        }
        assertNotNull(windowAreaInfo)
        assertTrue {
            windowAreaInfo!!
                .getCapability(OPERATION_PRESENT_ON_AREA)?.status ==
                WINDOW_AREA_STATUS_AVAILABLE
        }

        val callback = TestWindowAreaPresentationSessionCallback()
        activityScenario.scenario.onActivity { testActivity ->
            controller.presentContentOnWindowArea(
                windowAreaInfo!!.token,
                testActivity,
                Runnable::run,
                callback
            )
            assert(callback.sessionActive)
            assert(!callback.contentVisible)

            callback.presentation?.setContentView(TextView(testActivity))
            assert(callback.contentVisible)
            assert(callback.sessionActive)

            callback.presentation?.close()
            assert(!callback.contentVisible)
            assert(!callback.sessionActive)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    public fun testRearDisplayPresentationModeSessionEndedError(): Unit = testScope.runTest {
        assumeAtLeastVendorApiLevel(3)
        val extensionComponent = FakeWindowAreaComponent()
        val controller = WindowAreaControllerImpl(
            windowAreaComponent = extensionComponent,
            vendorApiLevel = 3
        )
        var windowAreaInfo: WindowAreaInfo? = null
        extensionComponent.updateRearDisplayStatusListeners(STATUS_UNAVAILABLE)
        extensionComponent.updateRearDisplayPresentationStatusListeners(STATUS_UNAVAILABLE)
        testScope.launch(Job()) {
            windowAreaInfo = controller.windowAreaInfos.firstOrNull()
                ?.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
        }
        assertNotNull(windowAreaInfo)
        assertTrue {
            windowAreaInfo!!
                .getCapability(OPERATION_PRESENT_ON_AREA)?.status ==
                WINDOW_AREA_STATUS_UNAVAILABLE
        }

        val callback = TestWindowAreaPresentationSessionCallback()
        activityScenario.scenario.onActivity { testActivity ->
            controller.presentContentOnWindowArea(
                windowAreaInfo!!.token,
                testActivity,
                Runnable::run,
                callback
            )
            assert(!callback.sessionActive)
            assert(callback.sessionError != null)
            assert(callback.sessionError is IllegalStateException)
        }
    }

    private fun createEmptyWindowMetrics(): WindowMetrics {
        val displayMetrics = DisplayMetrics()
        return WindowMetrics(
            Bounds(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels),
            WindowInsetsCompat.Builder().build()
        )
    }

    private class TestWindowAreaInfoListConsumer : Consumer<List<WindowAreaInfo>> {

        val values: MutableList<List<WindowAreaInfo>> = mutableListOf()
        override fun accept(infos: List<WindowAreaInfo>) {
            values.add(infos)
        }
    }

    private class FakeWindowAreaComponent : WindowAreaComponent {
        val rearDisplayStatusListeners = mutableListOf<Consumer<Int>>()
        val rearDisplayPresentationStatusListeners =
            mutableListOf<Consumer<ExtensionWindowAreaStatus>>()
        var currentRearDisplayStatus = STATUS_UNSUPPORTED
        var currentRearDisplayPresentationStatus = STATUS_UNSUPPORTED

        var testActivity: Activity? = null
        var rearDisplaySessionConsumer: Consumer<Int>? = null
        var rearDisplayPresentationSessionConsumer: Consumer<Int>? = null

        override fun addRearDisplayStatusListener(consumer: Consumer<Int>) {
            rearDisplayStatusListeners.add(consumer)
            consumer.accept(currentRearDisplayStatus)
        }

        override fun removeRearDisplayStatusListener(consumer: Consumer<Int>) {
            rearDisplayStatusListeners.remove(consumer)
        }

        override fun addRearDisplayPresentationStatusListener(
            consumer: Consumer<ExtensionWindowAreaStatus>
        ) {
            rearDisplayPresentationStatusListeners.add(consumer)
            consumer.accept(TestExtensionWindowAreaStatus(currentRearDisplayPresentationStatus))
        }

        override fun removeRearDisplayPresentationStatusListener(
            consumer: Consumer<ExtensionWindowAreaStatus>
        ) {
            rearDisplayPresentationStatusListeners.remove(consumer)
        }

        // Fake WindowAreaComponent will change the orientation of the activity to signal
        // entering rear display mode, as well as ending the session
        override fun startRearDisplaySession(
            activity: Activity,
            rearDisplaySessionConsumer: Consumer<Int>
        ) {
            if (currentRearDisplayStatus != STATUS_AVAILABLE) {
                rearDisplaySessionConsumer.accept(SESSION_STATE_INACTIVE)
            }
            testActivity = activity
            this.rearDisplaySessionConsumer = rearDisplaySessionConsumer
            testActivity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            rearDisplaySessionConsumer.accept(WindowAreaComponent.SESSION_STATE_ACTIVE)
        }

        override fun endRearDisplaySession() {
            testActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            rearDisplaySessionConsumer?.accept(SESSION_STATE_INACTIVE)
        }

        override fun startRearDisplayPresentationSession(
            activity: Activity,
            consumer: Consumer<Int>
        ) {
            if (currentRearDisplayPresentationStatus != STATUS_AVAILABLE) {
                consumer.accept(SESSION_STATE_INACTIVE)
                return
            }
            testActivity = activity
            rearDisplayPresentationSessionConsumer = consumer
            consumer.accept(SESSION_STATE_ACTIVE)
        }

        override fun endRearDisplayPresentationSession() {
            rearDisplayPresentationSessionConsumer?.accept(
                WindowAreaComponent.SESSION_STATE_CONTENT_INVISIBLE)
            rearDisplayPresentationSessionConsumer?.accept(
                WindowAreaComponent.SESSION_STATE_INACTIVE)
        }

        override fun getRearDisplayPresentation(): ExtensionWindowAreaPresentation? {
            return TestExtensionWindowAreaPresentation(
                testActivity!!,
                rearDisplayPresentationSessionConsumer!!
            )
        }

        fun updateRearDisplayStatusListeners(newStatus: Int) {
            currentRearDisplayStatus = newStatus
            for (consumer in rearDisplayStatusListeners) {
                consumer.accept(currentRearDisplayStatus)
            }
        }

        fun updateRearDisplayPresentationStatusListeners(newStatus: Int) {
            currentRearDisplayPresentationStatus = newStatus
            for (consumer in rearDisplayPresentationStatusListeners) {
                consumer.accept(TestExtensionWindowAreaStatus(currentRearDisplayPresentationStatus))
            }
        }
    }

    private class TestWindowAreaSessionCallback : WindowAreaSessionCallback {
        var currentSession: WindowAreaSession? = null
        var error: Throwable? = null

        override fun onSessionStarted(session: WindowAreaSession) {
            currentSession = session
        }

        override fun onSessionEnded(t: Throwable?) {
            error = t
            currentSession = null
        }

        fun endSession() = currentSession?.close()
    }

    private class TestWindowAreaPresentationSessionCallback :
        WindowAreaPresentationSessionCallback {
        var sessionActive: Boolean = false
        var contentVisible: Boolean = false
        var presentation: WindowAreaSessionPresenter? = null
        var sessionError: Throwable? = null
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            sessionActive = true
            presentation = session
        }

        override fun onSessionEnded(t: Throwable?) {
            presentation = null
            sessionActive = false
            sessionError = t
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) {
            contentVisible = isVisible
        }
    }

    private class TestExtensionWindowAreaStatus(private val status: Int) :
        ExtensionWindowAreaStatus {
        override fun getWindowAreaStatus(): Int {
            return status
        }

        override fun getWindowAreaDisplayMetrics(): DisplayMetrics {
            return DisplayMetrics()
        }
    }

    private class TestExtensionWindowAreaPresentation(
        private val activity: Activity,
        private val sessionConsumer: Consumer<Int>
    ) : ExtensionWindowAreaPresentation {
        override fun getPresentationContext(): Context {
            return activity
        }

        override fun setPresentationView(view: View) {
            sessionConsumer.accept(WindowAreaComponent.SESSION_STATE_CONTENT_VISIBLE)
        }
    }

    companion object {
        private const val REAR_FACING_BINDER_DESCRIPTION = "TEST_WINDOW_AREA_REAR_FACING"
    }
}