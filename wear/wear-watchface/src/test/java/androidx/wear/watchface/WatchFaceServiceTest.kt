/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import android.support.wearable.watchface.Constants
import android.support.wearable.watchface.IWatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.support.wearable.watchface.accessibility.ContentDescriptionLabel
import android.view.SurfaceHolder
import android.view.WindowInsets
import androidx.annotation.Px
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.wear.complications.ComplicationSlotBounds
import androidx.wear.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.complications.SystemDataSources
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.control.IInteractiveWatchFace
import androidx.wear.watchface.control.IPendingInteractiveWatchFace
import androidx.wear.watchface.control.InteractiveInstanceManager
import androidx.wear.watchface.control.data.CrashInfoParcel
import androidx.wear.watchface.control.data.WallpaperInteractiveWatchFaceInstanceParams
import androidx.wear.watchface.data.ComplicationSlotBoundsType
import androidx.wear.watchface.data.DeviceConfig
import androidx.wear.watchface.data.IdAndComplicationDataWireFormat
import androidx.wear.watchface.data.WatchUiState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.WatchFaceLayer
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.Option
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.validateMockitoUsage
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.PriorityQueue

private const val INTERACTIVE_UPDATE_RATE_MS = 16L
private const val LEFT_COMPLICATION_ID = 1000
private const val RIGHT_COMPLICATION_ID = 1001
private const val EDGE_COMPLICATION_ID = 1002
private const val BACKGROUND_COMPLICATION_ID = 1111
private const val NO_COMPLICATIONS = "NO_COMPLICATIONS"
private const val LEFT_COMPLICATION = "LEFT_COMPLICATION"
private const val RIGHT_COMPLICATION = "RIGHT_COMPLICATION"
private const val LEFT_AND_RIGHT_COMPLICATIONS = "LEFT_AND_RIGHT_COMPLICATIONS"
private const val RIGHT_AND_LEFT_COMPLICATIONS = "RIGHT_AND_LEFT_COMPLICATIONS"

@Config(manifest = Config.NONE)
@RunWith(WatchFaceTestRunner::class)
public class WatchFaceServiceTest {

    private val handler = mock<Handler>()
    private val iWatchFaceService = mock<IWatchFaceService>()
    private val surfaceHolder = mock<SurfaceHolder>()
    private val tapListener = mock<WatchFace.TapListener>()
    private val watchState = MutableWatchState()

    init {
        `when`(surfaceHolder.surfaceFrame).thenReturn(ONE_HUNDRED_BY_ONE_HUNDRED_RECT)
    }

    private companion object {
        val ONE_HUNDRED_BY_ONE_HUNDRED_RECT = Rect(0, 0, 100, 100)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val complicationDrawableLeft = ComplicationDrawable(context)
    private val complicationDrawableRight = ComplicationDrawable(context)
    private val complicationDrawableEdge = ComplicationDrawable(context)
    private val complicationDrawableBackground = ComplicationDrawable(context)

    private val redStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("red_style"), "Red", icon = null)

    private val greenStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("green_style"), "Green", icon = null)

    private val blueStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("bluestyle"), "Blue", icon = null)

    private val colorStyleList = listOf(redStyleOption, greenStyleOption, blueStyleOption)

    private val colorStyleSetting = ListUserStyleSetting(
        UserStyleSetting.Id("color_style_setting"),
        "Colors",
        "Watchface colorization", /* icon = */
        null,
        colorStyleList,
        listOf(WatchFaceLayer.BASE)
    )

    private val classicStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("classic_style"), "Classic", icon = null)

    private val modernStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("modern_style"), "Modern", icon = null)

    private val gothicStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("gothic_style"), "Gothic", icon = null)

    private val watchHandStyleList =
        listOf(classicStyleOption, modernStyleOption, gothicStyleOption)

    private val watchHandStyleSetting = ListUserStyleSetting(
        UserStyleSetting.Id("hand_style_setting"),
        "Hand Style",
        "Hand visual look", /* icon = */
        null,
        watchHandStyleList,
        listOf(WatchFaceLayer.COMPLICATIONS_OVERLAY)
    )

    private val badStyleOption =
        ListUserStyleSetting.ListOption(Option.Id("bad_option"), "Bad", icon = null)

    private val leftComplication =
        ComplicationSlot.createRoundRectComplicationSlotBuilder(
            LEFT_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(
                    complicationDrawableLeft,
                    watchState,
                    listener
                )
            },
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE
            ),
            DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET),
            ComplicationSlotBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
        ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
            .build()

    private val rightComplication =
        ComplicationSlot.createRoundRectComplicationSlotBuilder(
            RIGHT_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(
                    complicationDrawableRight,
                    watchState,
                    listener
                )
            },
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE
            ),
            DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DAY_OF_WEEK),
            ComplicationSlotBounds(RectF(0.6f, 0.4f, 0.8f, 0.6f))
        ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
            .build()

    private val edgeComplicationHitTester = mock<ComplicationTapFilter>()
    private val edgeComplication =
        ComplicationSlot.createEdgeComplicationSlotBuilder(
            EDGE_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(
                    complicationDrawableEdge,
                    watchState,
                    listener
                )
            },
            listOf(
                ComplicationType.RANGED_VALUE,
                ComplicationType.LONG_TEXT,
                ComplicationType.SHORT_TEXT,
                ComplicationType.MONOCHROMATIC_IMAGE,
                ComplicationType.SMALL_IMAGE
            ),
            DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DAY_OF_WEEK),
            ComplicationSlotBounds(RectF(0.0f, 0.4f, 0.4f, 0.6f)),
            edgeComplicationHitTester
        ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
            .build()

    private val backgroundComplication =
        ComplicationSlot.createBackgroundComplicationSlotBuilder(
            BACKGROUND_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(
                    complicationDrawableBackground,
                    watchState,
                    listener
                )
            },
            listOf(
                ComplicationType.PHOTO_IMAGE
            ),
            DefaultComplicationDataSourcePolicy()
        ).setDefaultDataSourceType(ComplicationType.PHOTO_IMAGE)
            .build()

    private val leftAndRightComplicationsOption = ComplicationSlotsOption(
        Option.Id(LEFT_AND_RIGHT_COMPLICATIONS),
        "Left and Right",
        null,
        listOf(
            ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID)
                .setEnabled(true).build(),
            ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                .setEnabled(true).build()
        )
    )
    private val noComplicationsOption = ComplicationSlotsOption(
        Option.Id(NO_COMPLICATIONS),
        "None",
        null,
        listOf(
            ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID)
                .setEnabled(false).build(),
            ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                .setEnabled(false).build()
        )
    )
    private val leftComplicationsOption = ComplicationSlotsOption(
        Option.Id(LEFT_COMPLICATION),
        "Left",
        null,
        listOf(
            ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID)
                .setEnabled(true).build(),
            ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                .setEnabled(false).build()
        )
    )
    private val rightComplicationsOption = ComplicationSlotsOption(
        Option.Id(RIGHT_COMPLICATION),
        "Right",
        null,
        listOf(
            ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID)
                .setEnabled(false).build(),
            ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                .setEnabled(true).build()
        )
    )
    private val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
        UserStyleSetting.Id("complications_style_setting"),
        "AllComplicationSlots",
        "Number and position",
        icon = null,
        complicationConfig = listOf(
            leftAndRightComplicationsOption,
            noComplicationsOption,
            leftComplicationsOption,
            rightComplicationsOption
        ),
        affectsWatchFaceLayers = listOf(WatchFaceLayer.COMPLICATIONS)
    )

    private lateinit var renderer: TestRenderer
    private lateinit var complicationSlotsManager: ComplicationSlotsManager
    private lateinit var currentUserStyleRepository: CurrentUserStyleRepository
    private lateinit var watchFaceImpl: WatchFaceImpl
    private lateinit var testWatchFaceService: TestWatchFaceService
    private lateinit var engineWrapper: WatchFaceService.EngineWrapper
    private lateinit var interactiveWatchFaceInstance: IInteractiveWatchFace

    private class Task(val runTimeMillis: Long, val runnable: Runnable) : Comparable<Task> {
        override fun compareTo(other: Task) = runTimeMillis.compareTo(other.runTimeMillis)
    }

    private var looperTimeMillis = 0L
    private val pendingTasks = PriorityQueue<Task>()

    private fun runPostedTasksFor(durationMillis: Long) {
        looperTimeMillis += durationMillis
        while (pendingTasks.isNotEmpty() &&
            pendingTasks.peek()!!.runTimeMillis <= looperTimeMillis
        ) {
            pendingTasks.remove().runnable.run()
        }
    }

    private fun initEngine(
        @WatchFaceType watchFaceType: Int,
        complicationSlots: List<ComplicationSlot>,
        userStyleSchema: UserStyleSchema,
        apiVersion: Int = 2,
        hasLowBitAmbient: Boolean = false,
        hasBurnInProtection: Boolean = false,
        tapListener: WatchFace.TapListener? = null
    ) {
        testWatchFaceService = TestWatchFaceService(
            watchFaceType,
            complicationSlots,
            { _, currentUserStyleRepository, watchState ->
                renderer = TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
                renderer
            },
            userStyleSchema,
            watchState,
            handler,
            tapListener,
            true,
            null
        )
        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)

        // Set some initial complication data.
        for (complication in complicationSlots) {
            setComplicationViaWallpaperCommand(
                complication.id,
                when (complication.defaultDataSourceType) {
                    ComplicationType.SHORT_TEXT ->
                        ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(ComplicationText.plainText("Initial Short"))
                            .build()

                    ComplicationType.PHOTO_IMAGE ->
                        ComplicationData.Builder(ComplicationData.TYPE_LARGE_IMAGE)
                            .setLargeImage(Icon.createWithContentUri("someuri"))
                            .build()

                    else -> throw UnsupportedOperationException()
                }
            )
        }

        // Trigger watch face creation by setting the binder and the immutable properties.
        sendBinder(engineWrapper, apiVersion)
        sendImmutableProperties(engineWrapper, hasLowBitAmbient, hasBurnInProtection)
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)

        // [WatchFaceService.createWatchFace] Will have run by now because we're using an immediate
        // coroutine dispatcher.
        watchFaceImpl = engineWrapper.getWatchFaceImplOrNull()!!
        currentUserStyleRepository = watchFaceImpl.currentUserStyleRepository
        complicationSlotsManager = watchFaceImpl.complicationSlotsManager

        testWatchFaceService.setIsVisible(true)
    }

    private fun initWallpaperInteractiveWatchFaceInstance(
        @WatchFaceType watchFaceType: Int,
        complicationSlots: List<ComplicationSlot>,
        userStyleSchema: UserStyleSchema,
        wallpaperInteractiveWatchFaceInstanceParams: WallpaperInteractiveWatchFaceInstanceParams
    ) {
        testWatchFaceService = TestWatchFaceService(
            watchFaceType,
            complicationSlots,
            { _, currentUserStyleRepository, watchState ->
                renderer = TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
                renderer
            },
            userStyleSchema,
            watchState,
            handler,
            null,
            false,
            null
        )

        InteractiveInstanceManager
            .getExistingInstanceOrSetPendingWallpaperInteractiveWatchFaceInstance(
                InteractiveInstanceManager.PendingWallpaperInteractiveWatchFaceInstance(
                    wallpaperInteractiveWatchFaceInstanceParams,
                    object : IPendingInteractiveWatchFace.Stub() {
                        override fun getApiVersion() =
                            IPendingInteractiveWatchFace.API_VERSION

                        override fun onInteractiveWatchFaceCreated(
                            iInteractiveWatchFace: IInteractiveWatchFace
                        ) {
                            interactiveWatchFaceInstance = iInteractiveWatchFace
                        }

                        override fun onInteractiveWatchFaceCrashed(exception: CrashInfoParcel?) {
                            fail("WatchFace crashed: $exception")
                        }
                    }
                )
            )

        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)

        // [WatchFaceService.createWatchFace] Will have run by now because we're using an immediate
        // coroutine dispatcher.
        watchFaceImpl = engineWrapper.getWatchFaceImplOrNull()!!

        currentUserStyleRepository = watchFaceImpl.currentUserStyleRepository
        complicationSlotsManager = watchFaceImpl.complicationSlotsManager
        testWatchFaceService.setIsVisible(true)
    }

    private fun sendBinder(engine: WatchFaceService.EngineWrapper, apiVersion: Int) {
        `when`(iWatchFaceService.apiVersion).thenReturn(apiVersion)
        engine.onCommand(
            Constants.COMMAND_SET_BINDER,
            0,
            0,
            0,
            Bundle().apply {
                putBinder(
                    Constants.EXTRA_BINDER,
                    WatchFaceServiceStub(iWatchFaceService).asBinder()
                )
            },
            false
        )
    }

    private fun sendImmutableProperties(
        engine: WatchFaceService.EngineWrapper,
        hasLowBitAmbient: Boolean,
        hasBurnInProtection: Boolean
    ) {
        engine.wslFlow.onPropertiesChanged(
            Bundle().apply {
                putBoolean(Constants.PROPERTY_LOW_BIT_AMBIENT, hasLowBitAmbient)
                putBoolean(Constants.PROPERTY_BURN_IN_PROTECTION, hasBurnInProtection)
            }
        )
    }

    private fun sendRequestStyle() {
        engineWrapper.onCommand(Constants.COMMAND_REQUEST_STYLE, 0, 0, 0, null, false)
    }

    private fun setComplicationViaWallpaperCommand(
        complicationSlotId: Int,
        complicationData: ComplicationData
    ) {
        engineWrapper.onCommand(
            Constants.COMMAND_COMPLICATION_DATA,
            0,
            0,
            0,
            Bundle().apply {
                putInt(Constants.EXTRA_COMPLICATION_ID, complicationSlotId)
                putParcelable(Constants.EXTRA_COMPLICATION_DATA, complicationData)
            },
            false
        )
    }

    @Before
    public fun setUp() {
        `when`(handler.getLooper()).thenReturn(Looper.myLooper())

        // Capture tasks posted to mHandler and insert in mPendingTasks which is under our control.
        doAnswer {
            pendingTasks.add(Task(looperTimeMillis, it.arguments[0] as Runnable))
        }.`when`(handler).post(any())

        doAnswer {
            pendingTasks.add(
                Task(looperTimeMillis + it.arguments[1] as Long, it.arguments[0] as Runnable)
            )
        }.`when`(handler).postDelayed(any(), anyLong())

        doAnswer {
            // Remove task from the priority queue.  There's no good way of doing this quickly.
            val queue = ArrayDeque<Task>()
            while (pendingTasks.isNotEmpty()) {
                val task = pendingTasks.remove()
                if (task.runnable != it.arguments[0]) {
                    queue.add(task)
                }
            }

            // Push filtered tasks back on the queue.
            while (queue.isNotEmpty()) {
                pendingTasks.add(queue.remove())
            }
        }.`when`(handler).removeCallbacks(any())
    }

    @After
    public fun validate() {
        if (this::interactiveWatchFaceInstance.isInitialized) {
            interactiveWatchFaceInstance.release()
        }

        if (this::engineWrapper.isInitialized && !engineWrapper.destroyed) {
            engineWrapper.onDestroy()
        }

        validateMockitoUsage()
    }

    @Test
    public fun maybeUpdateDrawMode_setsCorrectDrawMode() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )
        watchState.isAmbient.value = false

        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)

        watchState.isBatteryLowAndNotCharging.value = true
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.LOW_BATTERY_INTERACTIVE)

        watchState.isBatteryLowAndNotCharging.value = false
        watchState.isAmbient.value = true
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.AMBIENT)

        watchState.isAmbient.value = false
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)

        watchState.interruptionFilter.value = NotificationManager.INTERRUPTION_FILTER_NONE
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.MUTE)

        watchState.interruptionFilter.value = NotificationManager.INTERRUPTION_FILTER_ALL
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)

        watchState.interruptionFilter.value = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.MUTE)

        watchState.interruptionFilter.value = NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)

        watchState.interruptionFilter.value = NotificationManager.INTERRUPTION_FILTER_ALARMS
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.MUTE)

        // Ambient takes precidence over interruption filter.
        watchState.isAmbient.value = true
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.AMBIENT)

        watchState.isAmbient.value = false
        watchState.interruptionFilter.value = 0
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(renderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)
    }

    @Test
    public fun onDraw_calendar_setFromSystemTime() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        watchState.isAmbient.value = false
        testWatchFaceService.mockSystemTimeMillis = 1000L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1000L)
    }

    @Test
    public fun onDraw_calendar_affectedCorrectly_with2xMockTime() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )
        watchState.isAmbient.value = false
        testWatchFaceService.mockSystemTimeMillis = 1000L

        watchFaceImpl.broadcastsReceiver!!.mockTimeReceiver.onReceive(
            context,
            Intent(WatchFaceImpl.MOCK_TIME_INTENT).apply {
                putExtra(WatchFaceImpl.EXTRA_MOCK_TIME_SPEED_MULTIPLIER, 2.0f)
                putExtra(WatchFaceImpl.EXTRA_MOCK_TIME_WRAPPING_MIN_TIME, -1L)
            }
        )

        // Time should not diverge initially.
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1000L)

        // However 1000ms of real time should result in 2000ms observed by onDraw.
        testWatchFaceService.mockSystemTimeMillis = 2000L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(3000L)
    }

    @Test
    public fun onDraw_calendar_affectedCorrectly_withMockTimeWrapping() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )
        watchState.isAmbient.value = false
        testWatchFaceService.mockSystemTimeMillis = 1000L

        watchFaceImpl.broadcastsReceiver!!.mockTimeReceiver.onReceive(
            context,
            Intent(WatchFaceImpl.MOCK_TIME_INTENT).apply {
                putExtra(WatchFaceImpl.EXTRA_MOCK_TIME_SPEED_MULTIPLIER, 2.0f)
                putExtra(WatchFaceImpl.EXTRA_MOCK_TIME_WRAPPING_MIN_TIME, 1000L)
                putExtra(WatchFaceImpl.EXTRA_MOCK_TIME_WRAPPING_MAX_TIME, 2000L)
            }
        )

        // Time in millis observed by onDraw should wrap betwween 1000 and 2000.
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1000L)

        testWatchFaceService.mockSystemTimeMillis = 1250L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1500L)

        testWatchFaceService.mockSystemTimeMillis = 1499L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1998L)

        testWatchFaceService.mockSystemTimeMillis = 1500L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1000L)

        testWatchFaceService.mockSystemTimeMillis = 1750L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1500L)

        testWatchFaceService.mockSystemTimeMillis = 1999L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1998L)

        testWatchFaceService.mockSystemTimeMillis = 2000L
        watchFaceImpl.onDraw()
        assertThat(watchFaceImpl.calendar.timeInMillis).isEqualTo(1000L)
    }

    private fun tapAt(x: Int, y: Int) {
        // The eventTime is ignored.
        watchFaceImpl.onTapCommand(TapType.DOWN, x, y)
        watchFaceImpl.onTapCommand(TapType.UP, x, y)
    }

    private fun tapCancelAt(x: Int, y: Int) {
        watchFaceImpl.onTapCommand(TapType.DOWN, x, y)
        watchFaceImpl.onTapCommand(TapType.CANCEL, x, y)
    }

    @Test
    public fun singleTaps_correctlyDetected_and_highlightComplications() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(RIGHT_COMPLICATION_ID)

        // Tap left complication.
        tapAt(30, 50)
        assertThat(complicationSlotsManager.pressedSlotIds).contains(LEFT_COMPLICATION_ID)
        assertThat(testWatchFaceService.tappedComplicationSlotIds)
            .isEqualTo(listOf(LEFT_COMPLICATION_ID))

        runPostedTasksFor(WatchFaceImpl.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)

        // Tap right complication.
        testWatchFaceService.reset()
        tapAt(70, 50)
        assertThat(complicationSlotsManager.pressedSlotIds).contains(RIGHT_COMPLICATION_ID)
        assertThat(testWatchFaceService.tappedComplicationSlotIds)
            .isEqualTo(listOf(RIGHT_COMPLICATION_ID))

        runPostedTasksFor(WatchFaceImpl.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)

        // Tap on blank space.
        testWatchFaceService.reset()
        tapAt(1, 1)
        assertThat(testWatchFaceService.tappedComplicationSlotIds).isEmpty()

        runPostedTasksFor(WatchFaceImpl.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)
        assertThat(testWatchFaceService.tappedComplicationSlotIds).isEmpty()
    }

    @Test
    public fun singleTaps_onDifferentComplications() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(RIGHT_COMPLICATION_ID)

        // Rapidly tap left then right complication.
        tapAt(30, 50)
        tapAt(70, 50)

        // Both complicationSlots get temporarily highlighted.
        assertThat(complicationSlotsManager.pressedSlotIds).contains(LEFT_COMPLICATION_ID)
        assertThat(complicationSlotsManager.pressedSlotIds).contains(RIGHT_COMPLICATION_ID)

        // And the highlight goes away after a delay.
        runPostedTasksFor(WatchFaceImpl.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(LEFT_COMPLICATION_ID)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(RIGHT_COMPLICATION_ID)

        // Taps are registered on both complicationSlots.
        assertThat(testWatchFaceService.tappedComplicationSlotIds)
            .isEqualTo(listOf(LEFT_COMPLICATION_ID, RIGHT_COMPLICATION_ID))
    }

    @Test
    public fun tapCancel_after_tapDown_CancelsTap() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        testWatchFaceService.reset()
        // Tap/Cancel left complication
        tapCancelAt(30, 50)
        assertThat(testWatchFaceService.tappedComplicationSlotIds).isEmpty()
    }

    @Test
    public fun edgeComplication_tap() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(edgeComplication),
            UserStyleSchema(emptyList()),
            tapListener = tapListener
        )

        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(EDGE_COMPLICATION_ID)

        `when`(
            edgeComplicationHitTester.hitTest(
                edgeComplication,
                ONE_HUNDRED_BY_ONE_HUNDRED_RECT,
                0,
                50
            )
        ).thenReturn(true)

        // Tap the edge complication.
        tapAt(0, 50)
        assertThat(complicationSlotsManager.pressedSlotIds).contains(EDGE_COMPLICATION_ID)
        assertThat(testWatchFaceService.tappedComplicationSlotIds)
            .isEqualTo(listOf(EDGE_COMPLICATION_ID))

        runPostedTasksFor(WatchFaceImpl.CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS)
        assertThat(complicationSlotsManager.pressedSlotIds).doesNotContain(EDGE_COMPLICATION_ID)
    }

    @Test
    public fun tapListener_tap() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            tapListener = tapListener
        )

        // Tap on nothing.
        tapAt(1, 1)

        verify(tapListener).onTap(TapType.DOWN, 1, 1)
        verify(tapListener).onTap(TapType.UP, 1, 1)
    }

    @Test
    public fun tapListener_tap_viaWallpaperCommand() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            tapListener = tapListener
        )

        // Tap on nothing.
        engineWrapper.onCommand(
            Constants.COMMAND_TOUCH,
            10,
            20,
            0,
            Bundle().apply {
                putBinder(
                    Constants.EXTRA_BINDER,
                    WatchFaceServiceStub(iWatchFaceService).asBinder()
                )
            },
            false
        )

        engineWrapper.onCommand(
            Constants.COMMAND_TAP,
            10,
            20,
            0,
            Bundle().apply {
                putBinder(
                    Constants.EXTRA_BINDER,
                    WatchFaceServiceStub(iWatchFaceService).asBinder()
                )
            },
            false
        )

        verify(tapListener).onTap(TapType.DOWN, 10, 20)
        verify(tapListener).onTap(TapType.UP, 10, 20)
    }

    @Test
    public fun tapListener_tapComplication() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            tapListener = tapListener
        )

        // Tap right complication.
        tapAt(70, 50)

        verify(tapListener, times(0)).onTap(TapType.DOWN, 70, 50)
        verify(tapListener, times(0)).onTap(TapType.UP, 70, 50)
    }

    @Test
    public fun interactiveFrameRate_reducedWhenBatteryLow() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            INTERACTIVE_UPDATE_RATE_MS
        )

        // The delay should change when battery is low.
        watchFaceImpl.broadcastsReceiver!!.actionBatteryLowReceiver.onReceive(
            context,
            Intent(Intent.ACTION_BATTERY_LOW)
        )
        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            WatchFaceImpl.MAX_LOW_POWER_INTERACTIVE_UPDATE_RATE_MS
        )

        // And go back to normal when battery is OK.
        watchFaceImpl.broadcastsReceiver!!.actionBatteryOkayReceiver.onReceive(
            context,
            Intent(Intent.ACTION_BATTERY_OKAY)
        )
        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            INTERACTIVE_UPDATE_RATE_MS
        )
    }

    @Test
    public fun interactiveFrameRate_restoreWhenPowerConnectedAfterBatteryLow() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            INTERACTIVE_UPDATE_RATE_MS
        )

        // The delay should change when battery is low.
        watchFaceImpl.broadcastsReceiver!!.actionBatteryLowReceiver.onReceive(
            context,
            Intent(Intent.ACTION_BATTERY_LOW)
        )
        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            WatchFaceImpl.MAX_LOW_POWER_INTERACTIVE_UPDATE_RATE_MS
        )

        // And go back to normal when power is connected.
        watchFaceImpl.broadcastsReceiver!!.actionPowerConnectedReceiver.onReceive(
            context,
            Intent(Intent.ACTION_POWER_CONNECTED)
        )
        assertThat(watchFaceImpl.computeDelayTillNextFrame(0, 0)).isEqualTo(
            INTERACTIVE_UPDATE_RATE_MS
        )
    }

    @Test
    public fun computeDelayTillNextFrame_accountsForSlowDraw() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(
            watchFaceImpl.computeDelayTillNextFrame(
                beginFrameTimeMillis = 0,
                currentTimeMillis = 2
            )
        )
            .isEqualTo(INTERACTIVE_UPDATE_RATE_MS - 2)
    }

    @Test
    public fun computeDelayTillNextFrame_dropsFramesForVerySlowDraw() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(
            watchFaceImpl.computeDelayTillNextFrame(
                beginFrameTimeMillis = 0,
                currentTimeMillis = INTERACTIVE_UPDATE_RATE_MS
            )
        ).isEqualTo(INTERACTIVE_UPDATE_RATE_MS)
    }

    @Test
    public fun computeDelayTillNextFrame_perservesPhaseForVerySlowDraw() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        // The phase of beginFrameTimeMillis % INTERACTIVE_UPDATE_RATE_MS is 2, but the phase of
        // currentTimeMillis % INTERACTIVE_UPDATE_RATE_MS is 3, so we expect to delay
        // INTERACTIVE_UPDATE_RATE_MS - 1 to preserve the phase while dropping a frame.
        assertThat(
            watchFaceImpl.computeDelayTillNextFrame(
                beginFrameTimeMillis = 2,
                currentTimeMillis = INTERACTIVE_UPDATE_RATE_MS + 3
            )
        ).isEqualTo(INTERACTIVE_UPDATE_RATE_MS - 1)
    }

    @Test
    public fun computeDelayTillNextFrame_beginFrameTimeInTheFuture() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(
            watchFaceImpl.computeDelayTillNextFrame(
                beginFrameTimeMillis = 100,
                currentTimeMillis = 10
            )
        ).isEqualTo(INTERACTIVE_UPDATE_RATE_MS)
    }

    @Test
    public fun getComplicationSlotIdAt_returnsCorrectComplications() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        assertThat(complicationSlotsManager.getComplicationSlotAt(30, 50)!!.id)
            .isEqualTo(LEFT_COMPLICATION_ID)
        leftComplication.enabled = false
        assertThat(complicationSlotsManager.getComplicationSlotAt(30, 50)).isNull()

        assertThat(complicationSlotsManager.getComplicationSlotAt(70, 50)!!.id)
            .isEqualTo(RIGHT_COMPLICATION_ID)
        assertThat(complicationSlotsManager.getComplicationSlotAt(1, 1)).isNull()
    }

    @Test
    public fun getBackgroundComplicationSlotId_returnsNull() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )
        // Flush pending tasks posted as a result of initEngine.
        runPostedTasksFor(0)
        assertThat(complicationSlotsManager.getBackgroundComplicationSlot()).isNull()
        engineWrapper.onDestroy()
    }

    @Test
    public fun getBackgroundComplicationSlotId_returnsCorrectId() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, backgroundComplication),
            UserStyleSchema(emptyList())
        )
        assertThat(complicationSlotsManager.getBackgroundComplicationSlot()!!.id).isEqualTo(
            BACKGROUND_COMPLICATION_ID
        )
    }

    @Test
    public fun getStoredUserStyleNotSupported_userStyle_isPersisted() {
        // The style should get persisted in a file because this test is set up using the legacy
        // Wear 2.0 APIs.
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            2
        )

        // This should get persisted.
        currentUserStyleRepository.userStyle = UserStyle(
            hashMapOf(
                colorStyleSetting to blueStyleOption,
                watchHandStyleSetting to gothicStyleOption
            )
        )
        engineWrapper.onDestroy()

        // Flush pending tasks posted as a result of initEngine.
        runPostedTasksFor(0)

        val service2 = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
            },
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            watchState,
            handler,
            null,
            true,
            null
        )

        // Trigger watch face creation.
        val engine2 = service2.onCreateEngine() as WatchFaceService.EngineWrapper
        sendBinder(engine2, apiVersion = 2)
        sendImmutableProperties(engine2, false, false)
        engine2.onSurfaceChanged(surfaceHolder, 0, 100, 100)

        val watchFaceImpl2 = engine2.getWatchFaceImplOrNull()!!
        val userStyleRepository2 = watchFaceImpl2.currentUserStyleRepository
        assertThat(userStyleRepository2.userStyle.selectedOptions[colorStyleSetting]!!.id)
            .isEqualTo(
                blueStyleOption.id
            )
        assertThat(userStyleRepository2.userStyle.selectedOptions[watchHandStyleSetting]!!.id)
            .isEqualTo(
                gothicStyleOption.id
            )
    }

    @SdkSuppress(maxSdkVersion = 29)
    @Test
    public fun onApplyWindowInsetsBeforeR_setsChinHeight() {
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList())
        )
        // Initially the chin size is set to zero.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(0)
        // When window insets are delivered to the watch face.
        engineWrapper.onApplyWindowInsets(getChinWindowInsetsApi25(chinHeight = 12))
        // Then the chin size is updated.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(12)
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test
    public fun onApplyWindowInsetsRAndAbove_setsChinHeight() {
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList())
        )
        // Initially the chin size is set to zero.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(0)
        // When window insets are delivered to the watch face.
        engineWrapper.onApplyWindowInsets(getChinWindowInsetsApi30(chinHeight = 12))
        // Then the chin size is updated.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(12)
    }

    @Test
    public fun onApplyWindowInsetsBeforeR_multipleCallsIgnored() {
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList())
        )
        // Initially the chin size is set to zero.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(0)
        // When window insets are delivered to the watch face.
        engineWrapper.onApplyWindowInsets(getChinWindowInsetsApi25(chinHeight = 12))
        // Then the chin size is updated.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(12)
        // When the same window insets are delivered to the watch face again.
        engineWrapper.onApplyWindowInsets(getChinWindowInsetsApi25(chinHeight = 12))
        // Nothing happens.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(12)
        // When different window insets are delivered to the watch face again.
        engineWrapper.onApplyWindowInsets(getChinWindowInsetsApi25(chinHeight = 24))
        // Nothing happens and the size is unchanged.
        assertThat(engineWrapper.mutableWatchState.chinHeight).isEqualTo(12)
    }

    @Test
    public fun initWallpaperInteractiveWatchFaceInstanceWithUserStyle() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(
                    hashMapOf(
                        colorStyleSetting to blueStyleOption,
                        watchHandStyleSetting to gothicStyleOption
                    )
                ).toWireFormat(),
                null
            )
        )

        // The style option above should get applied during watch face creation.
        assertThat(currentUserStyleRepository.userStyle.selectedOptions[colorStyleSetting]!!.id)
            .isEqualTo(
                blueStyleOption.id
            )
        assertThat(currentUserStyleRepository.userStyle.selectedOptions[watchHandStyleSetting]!!.id)
            .isEqualTo(
                gothicStyleOption.id
            )
    }

    @Test
    public fun initWallpaperInteractiveWatchFaceInstanceWithUserStyleThatDoesntMatchSchema() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(hashMapOf(watchHandStyleSetting to badStyleOption)).toWireFormat(),
                null
            )
        )

        assertThat(currentUserStyleRepository.userStyle.selectedOptions[watchHandStyleSetting])
            .isEqualTo(watchHandStyleList.first())
    }

    @Test
    public fun wear2ImmutablePropertiesSetCorrectly() {
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList()),
            2,
            true,
            false
        )

        assertTrue(watchState.hasLowBitAmbient)
        assertFalse(watchState.hasBurnInProtection)
    }

    @Test
    public fun wear2ImmutablePropertiesSetCorrectly2() {
        initEngine(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList()),
            2,
            false,
            true
        )

        assertFalse(watchState.hasLowBitAmbient)
        assertTrue(watchState.hasBurnInProtection)
    }

    @Test
    public fun wallpaperInteractiveWatchFaceImmutablePropertiesSetCorrectly() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    true,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(hashMapOf(watchHandStyleSetting to badStyleOption)).toWireFormat(),
                null
            )
        )

        assertTrue(watchState.hasLowBitAmbient)
        assertFalse(watchState.hasBurnInProtection)
    }

    @Test
    public fun onCreate_calls_setActiveComplications_withCorrectIDs() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication, backgroundComplication),
            UserStyleSchema(emptyList())
        )

        verify(iWatchFaceService).setActiveComplications(
            intArrayOf(LEFT_COMPLICATION_ID, RIGHT_COMPLICATION_ID, BACKGROUND_COMPLICATION_ID),
            true
        )
    }

    @Test
    public fun onCreate_calls_setContentDescriptionLabels_withCorrectArgs() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication, backgroundComplication),
            UserStyleSchema(emptyList())
        )

        runPostedTasksFor(0)

        // setContentDescriptionLabels gets called twice in the legacy WSL flow, once initially and
        // once in response to the complication data wallpaper commands.
        val arguments = ArgumentCaptor.forClass(Array<ContentDescriptionLabel>::class.java)
        verify(iWatchFaceService, times(2)).setContentDescriptionLabels(arguments.capture())

        val argument = arguments.allValues[1]
        assertThat(argument.size).isEqualTo(3)
        assertThat(argument[0].bounds).isEqualTo(Rect(25, 25, 75, 75)) // Clock element.
        assertThat(argument[1].bounds).isEqualTo(Rect(20, 40, 40, 60)) // Left complication.
        assertThat(argument[2].bounds).isEqualTo(Rect(60, 40, 80, 60)) // Right complication.
    }

    @Test
    public fun moveComplications() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            4
        )

        leftComplication.complicationSlotBounds =
            ComplicationSlotBounds(RectF(0.3f, 0.3f, 0.5f, 0.5f))
        rightComplication.complicationSlotBounds =
            ComplicationSlotBounds(RectF(0.7f, 0.75f, 0.9f, 0.95f))

        val complicationDetails = watchFaceImpl.getComplicationState()
        assertThat(complicationDetails[0].id).isEqualTo(LEFT_COMPLICATION_ID)
        assertThat(complicationDetails[0].complicationState.boundsType).isEqualTo(
            ComplicationSlotBoundsType.ROUND_RECT
        )
        assertThat(complicationDetails[0].complicationState.bounds).isEqualTo(
            Rect(30, 30, 50, 50)
        )

        assertThat(complicationDetails[1].id).isEqualTo(RIGHT_COMPLICATION_ID)
        assertThat(complicationDetails[1].complicationState.boundsType).isEqualTo(
            ComplicationSlotBoundsType.ROUND_RECT
        )
        assertThat(complicationDetails[1].complicationState.bounds).isEqualTo(
            Rect(70, 75, 90, 95)
        )

        // Despite disabling the background complication we should still get a
        // ContentDescriptionLabel for the main clock element.
        engineWrapper.updateContentDescriptionLabels()
        val contentDescriptionLabels = engineWrapper.contentDescriptionLabels
        assertThat(contentDescriptionLabels.size).isEqualTo(3)
        assertThat(contentDescriptionLabels[0].bounds).isEqualTo(
            Rect(
                25,
                25,
                75,
                75
            )
        ) // Clock element.
        assertThat(contentDescriptionLabels[1].bounds).isEqualTo(
            Rect(
                30,
                30,
                50,
                50
            )
        ) // Left complication.
        assertThat(contentDescriptionLabels[2].bounds).isEqualTo(
            Rect(
                70,
                75,
                90,
                95
            )
        ) // Right complication.
    }

    @Test
    public fun styleChangesAccessibilityTraversalIndex() {
        val rightAndSelectComplicationsOption = ComplicationSlotsOption(
            Option.Id(RIGHT_AND_LEFT_COMPLICATIONS),
            "Right and Left",
            null,
            listOf(
                ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID)
                    .setEnabled(true).setAccessibilityTraversalIndex(RIGHT_COMPLICATION_ID).build(),
                ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                    .setEnabled(true).setAccessibilityTraversalIndex(LEFT_COMPLICATION_ID).build()
            )
        )

        val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("complications_style_setting"),
            "AllComplicationSlots",
            "Number and position",
            icon = null,
            complicationConfig = listOf(
                leftAndRightComplicationsOption,
                rightAndSelectComplicationsOption
            ),
            affectsWatchFaceLayers = listOf(WatchFaceLayer.COMPLICATIONS)
        )

        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(listOf(complicationsStyleSetting)),
            4
        )

        // Despite disabling the background complication we should still get a
        // ContentDescriptionLabel for the main clock element.
        engineWrapper.updateContentDescriptionLabels()
        val contentDescriptionLabels = engineWrapper.contentDescriptionLabels
        assertThat(contentDescriptionLabels.size).isEqualTo(3)
        assertThat(contentDescriptionLabels[0].bounds).isEqualTo(
            Rect(
                25,
                25,
                75,
                75
            )
        ) // Clock element.
        assertThat(contentDescriptionLabels[1].bounds).isEqualTo(
            Rect(
                20,
                40,
                40,
                60
            )
        ) // Left complication.
        assertThat(contentDescriptionLabels[2].bounds).isEqualTo(
            Rect(
                60,
                40,
                80,
                60
            )
        ) // Right complication.

        // Change the style
        watchFaceImpl.currentUserStyleRepository.userStyle = UserStyle(
            hashMapOf(complicationsStyleSetting to rightAndSelectComplicationsOption)
        )
        runPostedTasksFor(0)

        val contentDescriptionLabels2 = engineWrapper.contentDescriptionLabels
        assertThat(contentDescriptionLabels2.size).isEqualTo(3)
        assertThat(contentDescriptionLabels2[0].bounds).isEqualTo(
            Rect(
                25,
                25,
                75,
                75
            )
        ) // Clock element.
        assertThat(contentDescriptionLabels2[1].bounds).isEqualTo(
            Rect(
                60,
                40,
                80,
                60
            )
        ) // Right complication.
        assertThat(contentDescriptionLabels2[2].bounds).isEqualTo(
            Rect(
                20,
                40,
                40,
                60
            )
        ) // Left complication.
    }

    @Test
    public fun getOptionForIdentifier_ListViewStyleSetting() {
        // Check the correct Options are returned for known option names.
        assertThat(colorStyleSetting.getOptionForId(redStyleOption.id)).isEqualTo(redStyleOption)
        assertThat(colorStyleSetting.getOptionForId(greenStyleOption.id)).isEqualTo(
            greenStyleOption
        )
        assertThat(colorStyleSetting.getOptionForId(blueStyleOption.id)).isEqualTo(blueStyleOption)

        // For unknown option names the first element in the list should be returned.
        assertThat(colorStyleSetting.getOptionForId(Option.Id("unknown".encodeToByteArray())))
            .isEqualTo(colorStyleList.first())
    }

    @Test
    public fun centerX_and_centerY_containUpToDateValues() {
        initEngine(WatchFaceType.ANALOG, emptyList(), UserStyleSchema(emptyList()))

        assertThat(renderer.centerX).isEqualTo(50f)
        assertThat(renderer.centerY).isEqualTo(50f)

        val argument = ArgumentCaptor.forClass(SurfaceHolder.Callback::class.java)
        verify(surfaceHolder, atLeastOnce()).addCallback(argument.capture())

        // Trigger an update to a larger surface which should move the center.
        reset(surfaceHolder)
        `when`(surfaceHolder.surfaceFrame).thenReturn(Rect(0, 0, 200, 300))

        for (value in argument.allValues) {
            value.surfaceChanged(surfaceHolder, 0, 200, 300)
        }

        assertThat(renderer.centerX).isEqualTo(100f)
        assertThat(renderer.centerY).isEqualTo(150f)
    }

    @Test
    public fun requestStyleBeforeSetBinder() {
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication, backgroundComplication),
            { _, currentUserStyleRepository, watchState ->
                TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
            },
            UserStyleSchema(emptyList()),
            watchState,
            handler,
            null,
            true,
            null
        )
        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)
        `when`(surfaceHolder.surfaceFrame).thenReturn(ONE_HUNDRED_BY_ONE_HUNDRED_RECT)

        sendRequestStyle()

        // Trigger watch face creation.
        sendBinder(engineWrapper, apiVersion = 2)
        sendImmutableProperties(engineWrapper, false, false)
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)
        watchFaceImpl = engineWrapper.getWatchFaceImplOrNull()!!

        val argument = ArgumentCaptor.forClass(WatchFaceStyle::class.java)
        verify(iWatchFaceService).setStyle(argument.capture())
        assertThat(argument.value.acceptsTapEvents).isEqualTo(true)
    }

    @Test
    public fun defaultComplicationDataSourcesWithFallbacks_newApi() {
        val dataSource1 = ComponentName("com.app1", "com.app1.App1")
        val dataSource2 = ComponentName("com.app2", "com.app2.App2")
        val complication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            LEFT_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(complicationDrawableLeft, watchState, listener)
            },
            emptyList(),
            DefaultComplicationDataSourcePolicy(
                dataSource1,
                dataSource2,
                SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET
            ),
            ComplicationSlotBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
        ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
            .build()
        initEngine(WatchFaceType.ANALOG, listOf(complication), UserStyleSchema(emptyList()))

        runPostedTasksFor(0)

        verify(iWatchFaceService).setDefaultComplicationProviderWithFallbacks(
            LEFT_COMPLICATION_ID,
            listOf(dataSource1, dataSource2),
            SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET,
            ComplicationData.TYPE_SHORT_TEXT
        )
    }

    @Test
    public fun defaultComplicationDataSourcesWithFallbacks_oldApi() {
        val dataSource1 = ComponentName("com.app1", "com.app1.App1")
        val dataSource2 = ComponentName("com.app2", "com.app2.App2")
        val complication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            LEFT_COMPLICATION_ID,
            { watchState, listener ->
                CanvasComplicationDrawable(complicationDrawableLeft, watchState, listener)
            },
            emptyList(),
            DefaultComplicationDataSourcePolicy(
                dataSource1,
                dataSource2,
                SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET
            ),
            ComplicationSlotBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
        ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
            .build()
        initEngine(
            WatchFaceType.ANALOG,
            listOf(complication),
            UserStyleSchema(emptyList()),
            apiVersion = 0
        )

        runPostedTasksFor(0)

        verify(iWatchFaceService).setDefaultComplicationProvider(
            LEFT_COMPLICATION_ID, dataSource2, ComplicationData.TYPE_SHORT_TEXT
        )
        verify(iWatchFaceService).setDefaultComplicationProvider(
            LEFT_COMPLICATION_ID, dataSource1, ComplicationData.TYPE_SHORT_TEXT
        )
        verify(iWatchFaceService).setDefaultSystemComplicationProvider(
            LEFT_COMPLICATION_ID,
            SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET,
            ComplicationData.TYPE_SHORT_TEXT
        )
    }

    @Test
    public fun previewReferenceTimeMillisAnalog() {
        val instanceParams = WallpaperInteractiveWatchFaceInstanceParams(
            "interactiveInstanceId",
            DeviceConfig(
                false,
                false,
                1000,
                2000,
            ),
            WatchUiState(false, 0),
            UserStyle(
                hashMapOf(
                    colorStyleSetting to blueStyleOption,
                    watchHandStyleSetting to gothicStyleOption
                )
            ).toWireFormat(),
            null
        )

        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            instanceParams
        )

        assertThat(watchFaceImpl.previewReferenceTimeMillis).isEqualTo(1000)
    }

    @Test
    public fun previewReferenceTimeMillisDigital() {
        val instanceParams = WallpaperInteractiveWatchFaceInstanceParams(
            "interactiveInstanceId",
            DeviceConfig(
                false,
                false,
                1000,
                2000,
            ),
            WatchUiState(false, 0),
            UserStyle(
                hashMapOf(
                    colorStyleSetting to blueStyleOption,
                    watchHandStyleSetting to gothicStyleOption
                )
            ).toWireFormat(),
            null
        )

        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.DIGITAL,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            instanceParams
        )

        assertThat(watchFaceImpl.previewReferenceTimeMillis).isEqualTo(2000)
    }

    @Test
    public fun getComplicationDetails() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication, backgroundComplication),
            UserStyleSchema(emptyList()),
            apiVersion = 4
        )

        val complicationDetails = watchFaceImpl.getComplicationState()
        assertThat(complicationDetails[0].id).isEqualTo(LEFT_COMPLICATION_ID)
        assertThat(complicationDetails[0].complicationState.boundsType).isEqualTo(
            ComplicationSlotBoundsType.ROUND_RECT
        )
        assertThat(complicationDetails[0].complicationState.bounds).isEqualTo(
            Rect(20, 40, 40, 60)
        )
        assertThat(complicationDetails[0].complicationState.supportedTypes).isEqualTo(
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_LONG_TEXT,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SMALL_IMAGE
            )
        )

        assertThat(complicationDetails[1].id).isEqualTo(RIGHT_COMPLICATION_ID)
        assertThat(complicationDetails[1].complicationState.boundsType).isEqualTo(
            ComplicationSlotBoundsType.ROUND_RECT
        )
        assertThat(complicationDetails[1].complicationState.bounds).isEqualTo(
            Rect(60, 40, 80, 60)
        )
        assertThat(complicationDetails[1].complicationState.supportedTypes).isEqualTo(
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_LONG_TEXT,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SMALL_IMAGE
            )
        )

        assertThat(complicationDetails[2].id).isEqualTo(BACKGROUND_COMPLICATION_ID)
        assertThat(complicationDetails[2].complicationState.boundsType).isEqualTo(
            ComplicationSlotBoundsType.BACKGROUND
        )
        assertThat(complicationDetails[2].complicationState.bounds).isEqualTo(
            Rect(0, 0, 100, 100)
        )
        assertThat(complicationDetails[2].complicationState.supportedTypes).isEqualTo(
            intArrayOf(ComplicationData.TYPE_LARGE_IMAGE)
        )
    }

    @Test
    public fun shouldAnimateOverrideControlsEnteringAmbientMode() {
        lateinit var testRenderer: TestRendererWithShouldAnimate
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                testRenderer = TestRendererWithShouldAnimate(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
                testRenderer
            },
            UserStyleSchema(emptyList()),
            watchState,
            handler,
            null,
            true,
            null
        )

        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)
        `when`(surfaceHolder.surfaceFrame).thenReturn(ONE_HUNDRED_BY_ONE_HUNDRED_RECT)

        // Trigger watch face creation.
        sendBinder(engineWrapper, apiVersion = 2)
        sendImmutableProperties(engineWrapper, false, false)
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)
        watchFaceImpl = engineWrapper.getWatchFaceImplOrNull()!!

        // Enter ambient mode.
        watchState.isAmbient.value = true
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(testRenderer.renderParameters.drawMode).isEqualTo(DrawMode.INTERACTIVE)

        // Simulate enter ambient animation finishing.
        testRenderer.animate = false
        watchFaceImpl.maybeUpdateDrawMode()
        assertThat(testRenderer.renderParameters.drawMode).isEqualTo(DrawMode.AMBIENT)
    }

    @Test
    public fun complicationsUserStyleSettingSelectionAppliesChanges() {
        initEngine(
            WatchFaceType.DIGITAL,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(listOf(complicationsStyleSetting)),
            apiVersion = 4
        )

        reset(iWatchFaceService)

        // Select a new style which turns off both complicationSlots.
        val newStyleA = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyleA[complicationsStyleSetting] = noComplicationsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyleA)

        assertFalse(leftComplication.enabled)
        assertFalse(rightComplication.enabled)
        verify(iWatchFaceService).setActiveComplications(intArrayOf(), false)

        val argumentA = ArgumentCaptor.forClass(Array<ContentDescriptionLabel>::class.java)
        verify(iWatchFaceService).setContentDescriptionLabels(argumentA.capture())
        assertThat(argumentA.value.size).isEqualTo(1)
        assertThat(argumentA.value[0].bounds).isEqualTo(Rect(25, 25, 75, 75)) // Clock element.

        reset(iWatchFaceService)

        // Select a new style which turns on only the left complication.
        val newStyleB = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyleB[complicationsStyleSetting] = leftComplicationsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyleB)

        assertTrue(leftComplication.enabled)
        assertFalse(rightComplication.enabled)
        verify(iWatchFaceService).setActiveComplications(intArrayOf(LEFT_COMPLICATION_ID), false)

        val argumentB = ArgumentCaptor.forClass(Array<ContentDescriptionLabel>::class.java)
        verify(iWatchFaceService).setContentDescriptionLabels(argumentB.capture())
        assertThat(argumentB.value.size).isEqualTo(2)
        assertThat(argumentB.value[0].bounds).isEqualTo(Rect(25, 25, 75, 75)) // Clock element.
        assertThat(argumentB.value[1].bounds).isEqualTo(Rect(20, 40, 40, 60)) // Left complication.
    }

    @Test
    public fun partialComplicationOverrides() {
        val bothComplicationsOption = ComplicationSlotsOption(
            Option.Id(LEFT_AND_RIGHT_COMPLICATIONS),
            "Left And Right",
            null,
            // An empty list means use the initial config.
            emptyList()
        )
        val leftOnlyComplicationsOption = ComplicationSlotsOption(
            Option.Id(LEFT_COMPLICATION),
            "Left",
            null,
            listOf(ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID).setEnabled(false).build())
        )
        val rightOnlyComplicationsOption = ComplicationSlotsOption(
            Option.Id(RIGHT_COMPLICATION),
            "Right",
            null,
            listOf(ComplicationSlotOverlay.Builder(LEFT_COMPLICATION_ID).setEnabled(false).build())
        )
        val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("complications_style_setting"),
            "AllComplicationSlots",
            "Number and position",
            icon = null,
            complicationConfig = listOf(
                bothComplicationsOption,
                leftOnlyComplicationsOption,
                rightOnlyComplicationsOption
            ),
            affectsWatchFaceLayers = listOf(WatchFaceLayer.COMPLICATIONS)
        )

        initEngine(
            WatchFaceType.DIGITAL,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(listOf(complicationsStyleSetting)),
            apiVersion = 4
        )

        assertTrue(leftComplication.enabled)
        assertTrue(rightComplication.enabled)

        // Select left complication only.
        val newStyleA = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyleA[complicationsStyleSetting] = leftOnlyComplicationsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyleA)

        runPostedTasksFor(0)

        assertTrue(leftComplication.enabled)
        assertFalse(rightComplication.enabled)

        // Select right complication only.
        val newStyleB = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyleB[complicationsStyleSetting] = rightOnlyComplicationsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyleB)

        runPostedTasksFor(0)

        assertFalse(leftComplication.enabled)
        assertTrue(rightComplication.enabled)

        // Select both complicationSlots.
        val newStyleC = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyleC[complicationsStyleSetting] = bothComplicationsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyleC)

        runPostedTasksFor(0)

        assertTrue(leftComplication.enabled)
        assertTrue(rightComplication.enabled)
    }

    @Test
    public fun partialComplicationOverrideAppliedToInitialStyle() {
        val bothComplicationsOption = ComplicationSlotsOption(
            Option.Id(LEFT_AND_RIGHT_COMPLICATIONS),
            "Left And Right",
            null,
            // An empty list means use the initial config.
            emptyList()
        )
        val leftOnlyComplicationsOption = ComplicationSlotsOption(
            Option.Id(LEFT_COMPLICATION),
            "Left",
            null,
            listOf(ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID).setEnabled(false).build())
        )
        val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("complications_style_setting"),
            "AllComplicationSlots",
            "Number and position",
            icon = null,
            complicationConfig = listOf(
                leftOnlyComplicationsOption, // The default value which should be applied.
                bothComplicationsOption,
            ),
            affectsWatchFaceLayers = listOf(WatchFaceLayer.COMPLICATIONS)
        )

        initEngine(
            WatchFaceType.DIGITAL,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(listOf(complicationsStyleSetting)),
            apiVersion = 4
        )

        assertTrue(leftComplication.enabled)
        assertFalse(rightComplication.enabled)
    }

    public fun UserStyleManager_init_applies_ComplicationsUserStyleSetting() {
        val complicationSlotId1 = 101
        val complicationSlotId2 = 102

        val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("ID"),
            "",
            "",
            icon = null,
            complicationConfig = listOf(
                ComplicationSlotsOption(
                    Option.Id("one"),
                    "one",
                    null,
                    listOf(
                        ComplicationSlotOverlay(
                            complicationSlotId1,
                            enabled = true
                        ),
                    )
                ),
                ComplicationSlotsOption(
                    Option.Id("two"),
                    "teo",
                    null,
                    listOf(
                        ComplicationSlotOverlay(
                            complicationSlotId2,
                            enabled = true
                        ),
                    )
                )
            ),
            listOf(WatchFaceLayer.COMPLICATIONS)
        )

        val currentUserStyleRepository =
            CurrentUserStyleRepository(UserStyleSchema(listOf(complicationsStyleSetting)))

        val manager = ComplicationSlotsManager(
            listOf(
                ComplicationSlot.createRoundRectComplicationSlotBuilder(
                    complicationSlotId1,
                    { watchState, listener ->
                        CanvasComplicationDrawable(complicationDrawableLeft, watchState, listener)
                    },
                    listOf(
                        ComplicationType.RANGED_VALUE,
                    ),
                    DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DAY_OF_WEEK),
                    ComplicationSlotBounds(RectF(0.2f, 0.7f, 0.4f, 0.9f))
                ).setDefaultDataSourceType(ComplicationType.RANGED_VALUE)
                    .setEnabled(false)
                    .build(),

                ComplicationSlot.createRoundRectComplicationSlotBuilder(
                    complicationSlotId2,
                    { watchState, listener ->
                        CanvasComplicationDrawable(complicationDrawableRight, watchState, listener)
                    },
                    listOf(
                        ComplicationType.LONG_TEXT,
                    ),
                    DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DAY_OF_WEEK),
                    ComplicationSlotBounds(RectF(0.2f, 0.7f, 0.4f, 0.9f))
                ).setDefaultDataSourceType(ComplicationType.LONG_TEXT)
                    .setEnabled(false)
                    .build()
            ),
            currentUserStyleRepository
        )

        // The init function of ComplicationSlotsManager should enable complicationSlotId1.
        assertThat(manager[complicationSlotId1]!!.enabled).isTrue()
    }

    @Test
    public fun observeComplicationData() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                null
            )
        )

        lateinit var leftComplicationData: ComplicationData
        lateinit var rightComplicationData: ComplicationData

        leftComplication.complicationData.addObserver {
            leftComplicationData = it.asWireComplicationData()
        }

        rightComplication.complicationData.addObserver {
            rightComplicationData = it.asWireComplicationData()
        }

        interactiveWatchFaceInstance.updateComplicationData(
            listOf(
                IdAndComplicationDataWireFormat(
                    LEFT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                        .setLongText(ComplicationText.plainText("TYPE_LONG_TEXT")).build()
                ),
                IdAndComplicationDataWireFormat(
                    RIGHT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("TYPE_SHORT_TEXT")).build()
                )
            )
        )

        assertThat(leftComplicationData.type).isEqualTo(ComplicationData.TYPE_LONG_TEXT)
        assertThat(leftComplicationData.longText?.getTextAt(context.resources, 0))
            .isEqualTo("TYPE_LONG_TEXT")
        assertThat(rightComplicationData.type).isEqualTo(ComplicationData.TYPE_SHORT_TEXT)
        assertThat(rightComplicationData.shortText?.getTextAt(context.resources, 0))
            .isEqualTo("TYPE_SHORT_TEXT")
    }

    @Test
    public fun complication_isActiveAt() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                null
            )
        )

        interactiveWatchFaceInstance.updateComplicationData(
            listOf(
                IdAndComplicationDataWireFormat(
                    LEFT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("TYPE_SHORT_TEXT"))
                        .build()
                )
            )
        )

        // Initially the complication should be active.
        assertThat(leftComplication.isActiveAt(0)).isTrue()

        // Send empty data.
        interactiveWatchFaceInstance.updateComplicationData(
            listOf(
                IdAndComplicationDataWireFormat(
                    LEFT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_EMPTY).build()
                )
            )
        )

        assertThat(leftComplication.isActiveAt(0)).isFalse()

        // Send a complication that is active for a time range.
        interactiveWatchFaceInstance.updateComplicationData(
            listOf(
                IdAndComplicationDataWireFormat(
                    LEFT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("TYPE_SHORT_TEXT"))
                        .setStartDateTimeMillis(1000000)
                        .setEndDateTimeMillis(2000000)
                        .build()
                )
            )
        )

        assertThat(leftComplication.isActiveAt(999999)).isFalse()
        assertThat(leftComplication.isActiveAt(1000000)).isTrue()
        assertThat(leftComplication.isActiveAt(2000000)).isTrue()
        assertThat(leftComplication.isActiveAt(2000001)).isFalse()
    }

    @Test
    public fun updateInvalidCOmpliationIdDoesNotCrash() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                null
            )
        )

        // Send a complication with an invalid id - this should get ignored.
        interactiveWatchFaceInstance.updateComplicationData(
            listOf(
                IdAndComplicationDataWireFormat(
                    RIGHT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("TYPE_SHORT_TEXT"))
                        .build()
                )
            )
        )
    }

    @Test
    public fun invalidateRendererBeforeFullInit() {
        renderer = TestRenderer(
            surfaceHolder,
            CurrentUserStyleRepository(UserStyleSchema(emptyList())),
            watchState.asWatchState(),
            INTERACTIVE_UPDATE_RATE_MS
        )

        // This should not throw an exception.
        renderer.invalidate()
    }

    @Test
    public fun watchStateObservableWatchDataMembersHaveValues() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                null
            )
        )

        assertTrue(watchState.interruptionFilter.hasValue())
        assertTrue(watchState.isAmbient.hasValue())
        assertTrue(watchState.isBatteryLowAndNotCharging.hasValue())
        assertTrue(watchState.isVisible.hasValue())
    }

    @Test
    public fun setIsBatteryLowAndNotChargingFromBatteryStatus() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                null
            )
        )

        watchFaceImpl.setIsBatteryLowAndNotChargingFromBatteryStatus(
            Intent().apply {
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(BatteryManager.EXTRA_LEVEL, 0)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
            }
        )
        assertTrue(watchState.isBatteryLowAndNotCharging.value)

        watchFaceImpl.setIsBatteryLowAndNotChargingFromBatteryStatus(
            Intent().apply {
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
                putExtra(BatteryManager.EXTRA_LEVEL, 0)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
            }
        )
        assertFalse(watchState.isBatteryLowAndNotCharging.value)

        watchFaceImpl.setIsBatteryLowAndNotChargingFromBatteryStatus(
            Intent().apply {
                putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(BatteryManager.EXTRA_LEVEL, 80)
                putExtra(BatteryManager.EXTRA_SCALE, 100)
            }
        )
        assertFalse(watchState.isBatteryLowAndNotCharging.value)

        watchFaceImpl.setIsBatteryLowAndNotChargingFromBatteryStatus(Intent())
        assertFalse(watchState.isBatteryLowAndNotCharging.value)

        watchFaceImpl.setIsBatteryLowAndNotChargingFromBatteryStatus(null)
        assertFalse(watchState.isBatteryLowAndNotCharging.value)
    }

    @Test
    public fun isAmbientInitalisedEvenWithoutPropertiesSent() {
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
            },
            UserStyleSchema(emptyList()),
            watchState,
            handler,
            null,
            true,
            null
        )

        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)

        sendBinder(engineWrapper, 1)

        // At this stage we haven't sent properties such as isAmbient, we expect it to be
        // initialized to false (as opposed to null).
        assertThat(watchState.isAmbient.value).isFalse()
    }

    @Test
    public fun onDestroy_clearsInstanceRecord() {
        val instanceId = "interactiveInstanceId"
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            emptyList(),
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            WallpaperInteractiveWatchFaceInstanceParams(
                instanceId,
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(hashMapOf(colorStyleSetting to blueStyleOption)).toWireFormat(),
                null
            )
        )
        engineWrapper.onDestroy()

        assertNull(InteractiveInstanceManager.getAndRetainInstance(instanceId))
    }

    @Test
    public fun sendComplicationWallpaperCommandPreRFlow() {
        initEngine(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        setComplicationViaWallpaperCommand(
            LEFT_COMPLICATION_ID,
            ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText("Override"))
                .build()
        )

        val complication =
            watchFaceImpl.complicationSlotsManager[LEFT_COMPLICATION_ID]!!.complicationData.value as
                ShortTextComplicationData
        assertThat(
            complication.text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("Override")
    }

    @Test
    public fun sendComplicationWallpaperCommandIgnoredPostRFlow() {
        val instanceId = "interactiveInstanceId"
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                instanceId,
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                listOf(
                    IdAndComplicationDataWireFormat(
                        LEFT_COMPLICATION_ID,
                        ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(ComplicationText.plainText("INITIAL_VALUE"))
                            .build()
                    )
                )
            )
        )

        // This should be ignored because we're on the R flow.
        setComplicationViaWallpaperCommand(
            LEFT_COMPLICATION_ID,
            ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                .setShortText(ComplicationText.plainText("Override"))
                .build()
        )

        val complication =
            watchFaceImpl.complicationSlotsManager[LEFT_COMPLICATION_ID]!!.complicationData.value as
                ShortTextComplicationData
        assertThat(
            complication.text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("INITIAL_VALUE")
    }

    @Test
    public fun directBoot() {
        val instanceId = "DirectBootInstance"
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
            },
            UserStyleSchema(listOf(colorStyleSetting, watchHandStyleSetting)),
            watchState,
            handler,
            null,
            false, // Allows DirectBoot
            WallpaperInteractiveWatchFaceInstanceParams(
                instanceId,
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(
                    hashMapOf(
                        colorStyleSetting to blueStyleOption,
                        watchHandStyleSetting to gothicStyleOption
                    )
                ).toWireFormat(),
                null
            )
        )

        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)

        val instance = InteractiveInstanceManager.getAndRetainInstance(instanceId)
        assertThat(instance).isNotNull()
        watchFaceImpl = engineWrapper.getWatchFaceImplOrNull()!!
        val userStyle = watchFaceImpl.currentUserStyleRepository.userStyle
        assertThat(userStyle[colorStyleSetting]).isEqualTo(blueStyleOption)
        assertThat(userStyle[watchHandStyleSetting]).isEqualTo(gothicStyleOption)

        InteractiveInstanceManager.deleteInstance(instanceId)
    }

    @Test
    public fun headlessFlagPreventsDirectBoot() {
        val instanceId = "DirectBootInstance"
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
            },
            UserStyleSchema(emptyList()),
            watchState,
            handler,
            null,
            false, // Allows DirectBoot
            WallpaperInteractiveWatchFaceInstanceParams(
                instanceId,
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(
                    hashMapOf(
                        colorStyleSetting to blueStyleOption,
                        watchHandStyleSetting to gothicStyleOption
                    )
                ).toWireFormat(),
                null
            )
        )

        testWatchFaceService.createHeadlessEngine()

        runPostedTasksFor(0)

        val instance = InteractiveInstanceManager.getAndRetainInstance(instanceId)
        assertThat(instance).isNull()
    }

    @Test
    public fun firstOnVisibilityChangedIgnoredPostRFlow() {
        val instanceId = "interactiveInstanceId"
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                instanceId,
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                listOf(
                    IdAndComplicationDataWireFormat(
                        LEFT_COMPLICATION_ID,
                        ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(ComplicationText.plainText("INITIAL_VALUE"))
                            .build()
                    )
                )
            )
        )

        val observer = mock<Observer<Boolean>>()

        // This should be ignored.
        engineWrapper.onVisibilityChanged(true)
        watchState.isVisible.addObserver(observer)
        verify(observer, times(0)).onChanged(false)

        // This should trigger the observer.
        engineWrapper.onVisibilityChanged(false)
        verify(observer).onChanged(true)
    }

    @Test
    public fun complicationsUserStyleSetting_with_setComplicationBounds() {
        val rightComplicationBoundsOption = ComplicationSlotsOption(
            Option.Id(RIGHT_COMPLICATION),
            "Right",
            null,
            listOf(
                ComplicationSlotOverlay.Builder(RIGHT_COMPLICATION_ID)
                    .setComplicationSlotBounds(
                        ComplicationSlotBounds(RectF(0.1f, 0.1f, 0.2f, 0.2f))
                    ).build()
            )
        )
        val complicationsStyleSetting = ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("complications_style_setting"),
            "AllComplicationSlots",
            "Number and position",
            icon = null,
            complicationConfig = listOf(
                ComplicationSlotsOption(
                    Option.Id("Default"),
                    "Default",
                    null,
                    emptyList()
                ),
                rightComplicationBoundsOption
            ),
            affectsWatchFaceLayers = listOf(WatchFaceLayer.COMPLICATIONS)
        )

        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(listOf(complicationsStyleSetting)),
            WallpaperInteractiveWatchFaceInstanceParams(
                "interactiveInstanceId",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                listOf(
                    IdAndComplicationDataWireFormat(
                        LEFT_COMPLICATION_ID,
                        ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                            .setShortText(ComplicationText.plainText("INITIAL_VALUE"))
                            .build()
                    )
                )
            )
        )

        var complicationDetails = watchFaceImpl.getComplicationState()
        assertThat(complicationDetails[1].id).isEqualTo(RIGHT_COMPLICATION_ID)
        assertThat(complicationDetails[1].complicationState.bounds).isEqualTo(
            Rect(60, 40, 80, 60)
        )

        // Select a style which changes the bounds of the right complication.
        val newStyle = HashMap(currentUserStyleRepository.userStyle.selectedOptions)
        newStyle[complicationsStyleSetting] = rightComplicationBoundsOption
        currentUserStyleRepository.userStyle = UserStyle(newStyle)

        complicationDetails = watchFaceImpl.getComplicationState()
        assertThat(complicationDetails[1].id).isEqualTo(RIGHT_COMPLICATION_ID)
        assertThat(complicationDetails[1].complicationState.bounds).isEqualTo(
            Rect(10, 10, 20, 20)
        )
    }

    @Test
    public fun canvasComplication_onRendererCreated() {
        val leftCanvasComplication = mock<CanvasComplication>()
        val leftComplication =
            ComplicationSlot.createRoundRectComplicationSlotBuilder(
                LEFT_COMPLICATION_ID,
                { _, _ -> leftCanvasComplication },
                listOf(
                    ComplicationType.SHORT_TEXT,
                ),
                DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET),
                ComplicationSlotBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
            ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
                .build()

        val rightCanvasComplication = mock<CanvasComplication>()
        val rightComplication =
            ComplicationSlot.createRoundRectComplicationSlotBuilder(
                RIGHT_COMPLICATION_ID,
                { _, _ -> rightCanvasComplication },
                listOf(
                    ComplicationType.SHORT_TEXT,
                ),
                DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_DATE),
                ComplicationSlotBounds(RectF(0.6f, 0.4f, 0.8f, 0.6f))
            ).setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
                .build()

        initEngine(
            WatchFaceType.DIGITAL,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList())
        )

        verify(leftCanvasComplication).onRendererCreated(renderer)
        verify(rightCanvasComplication).onRendererCreated(renderer)
    }

    @Test
    public fun additionalContentDescriptionLabelsSetBeforeWatchFaceInitComplete() {
        testWatchFaceService = TestWatchFaceService(
            WatchFaceType.ANALOG,
            emptyList(),
            { _, currentUserStyleRepository, watchState ->
                renderer = TestRenderer(
                    surfaceHolder,
                    currentUserStyleRepository,
                    watchState,
                    INTERACTIVE_UPDATE_RATE_MS
                )
                // Set additionalContentDescriptionLabels before renderer.watchFaceHostApi has been
                // set.
                renderer.additionalContentDescriptionLabels = listOf(
                    Pair(
                        0,
                        ContentDescriptionLabel(
                            PlainComplicationText.Builder("Example").build(),
                            Rect(10, 10, 20, 20),
                            null
                        )
                    )
                )
                renderer
            },
            UserStyleSchema(emptyList()),
            watchState,
            handler,
            null,
            false,
            null
        )

        InteractiveInstanceManager
            .getExistingInstanceOrSetPendingWallpaperInteractiveWatchFaceInstance(
                InteractiveInstanceManager.PendingWallpaperInteractiveWatchFaceInstance(
                    WallpaperInteractiveWatchFaceInstanceParams(
                        "TestID",
                        DeviceConfig(
                            false,
                            false,
                            0,
                            0
                        ),
                        WatchUiState(false, 0),
                        UserStyle(emptyMap()).toWireFormat(),
                        emptyList()
                    ),
                    object : IPendingInteractiveWatchFace.Stub() {
                        override fun getApiVersion() =
                            IPendingInteractiveWatchFace.API_VERSION

                        override fun onInteractiveWatchFaceCreated(
                            iInteractiveWatchFace: IInteractiveWatchFace
                        ) {
                            interactiveWatchFaceInstance = iInteractiveWatchFace
                        }

                        override fun onInteractiveWatchFaceCrashed(exception: CrashInfoParcel?) {
                            fail("WatchFace crashed: $exception")
                        }
                    }
                )
            )

        engineWrapper = testWatchFaceService.onCreateEngine() as WatchFaceService.EngineWrapper
        engineWrapper.onCreate(surfaceHolder)
        engineWrapper.onSurfaceChanged(surfaceHolder, 0, 100, 100)

        // Check the additional ContentDescriptionLabel was applied.
        assertThat(engineWrapper.contentDescriptionLabels.size).isEqualTo(2)
        assertThat(
            engineWrapper.contentDescriptionLabels[1].text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("Example")
    }

    @Test
    public fun setComplicationDataList() {
        initWallpaperInteractiveWatchFaceInstance(
            WatchFaceType.ANALOG,
            listOf(leftComplication, rightComplication),
            UserStyleSchema(emptyList()),
            WallpaperInteractiveWatchFaceInstanceParams(
                "TestID",
                DeviceConfig(
                    false,
                    false,
                    0,
                    0
                ),
                WatchUiState(false, 0),
                UserStyle(emptyMap()).toWireFormat(),
                emptyList()
            )
        )

        val interactiveInstance = InteractiveInstanceManager.getAndRetainInstance("TestID")
        interactiveInstance!!.updateComplicationData(
            mutableListOf(
                IdAndComplicationDataWireFormat(
                    LEFT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("LEFT!"))
                        .build()
                ),
                IdAndComplicationDataWireFormat(
                    RIGHT_COMPLICATION_ID,
                    ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                        .setShortText(ComplicationText.plainText("RIGHT!"))
                        .build()
                )
            )
        )

        assertThat(engineWrapper.contentDescriptionLabels.size).isEqualTo(3)
        assertThat(
            engineWrapper.contentDescriptionLabels[1].text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("LEFT!")
        assertThat(
            engineWrapper.contentDescriptionLabels[2].text.getTextAt(
                ApplicationProvider.getApplicationContext<Context>().resources,
                0
            )
        ).isEqualTo("RIGHT!")
    }

    @Suppress("DEPRECATION")
    private fun getChinWindowInsetsApi25(@Px chinHeight: Int): WindowInsets =
        WindowInsets.Builder().setSystemWindowInsets(
            Insets.of(0, 0, 0, chinHeight)
        ).build()

    private fun getChinWindowInsetsApi30(@Px chinHeight: Int): WindowInsets =
        WindowInsets.Builder().setInsets(
            WindowInsets.Type.systemBars(),
            Insets.of(Rect().apply { bottom = chinHeight })
        ).build()
}
