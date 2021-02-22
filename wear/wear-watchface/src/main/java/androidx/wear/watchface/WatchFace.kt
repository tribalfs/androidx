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

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.os.BatteryManager
import android.os.Build
import android.support.wearable.watchface.WatchFaceStyle
import android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.Px
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.wear.utility.TraceEvent
import androidx.wear.complications.SystemProviders
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.IdAndComplicationData
import androidx.wear.complications.data.NoDataComplicationData
import androidx.wear.watchface.control.IInteractiveWatchFaceSysUI
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.data.UserStyleWireFormat
import kotlinx.coroutines.CompletableDeferred
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.security.InvalidParameterException
import kotlin.math.max

// Human reaction time is limited to ~100ms.
private const val MIN_PERCEPTABLE_DELAY_MILLIS = 100

// Zero is a special value meaning we will accept the system's choice for the
// display frame rate, which is the default behavior if this function isn't called.
private const val SYSTEM_DECIDES_FRAME_RATE = 0f

/**
 * The type of watch face, whether it's digital or analog. This influences the time displayed for
 * remote previews.
 *
 * @hide
 */
@IntDef(
    value = [
        WatchFaceType.DIGITAL,
        WatchFaceType.ANALOG
    ]
)
public annotation class WatchFaceType {
    public companion object {
        /* The WatchFace has an analog time display. */
        public const val ANALOG: Int = 0

        /* The WatchFace has a digital time display. */
        public const val DIGITAL: Int = 1
    }
}

private fun readPrefs(context: Context, fileName: String): UserStyleWireFormat {
    val hashMap = HashMap<String, String>()
    try {
        val reader = InputStreamReader(context.openFileInput(fileName)).buffered()
        while (true) {
            val key = reader.readLine() ?: break
            val value = reader.readLine() ?: break
            hashMap[key] = value
        }
        reader.close()
    } catch (e: FileNotFoundException) {
        // We don't need to do anything special here.
    }
    return UserStyleWireFormat(hashMap)
}

private fun writePrefs(context: Context, fileName: String, style: UserStyle) {
    val writer = context.openFileOutput(fileName, Context.MODE_PRIVATE).bufferedWriter()
    for ((key, value) in style.selectedOptions) {
        writer.write(key.id)
        writer.newLine()
        writer.write(value.id)
        writer.newLine()
    }
    writer.close()
}

/**
 * The return value of [WatchFaceService.createWatchFace] which brings together rendering, styling,
 * complications and state observers.
 */
public class WatchFace @JvmOverloads constructor(
    /**
     * The type of watch face, whether it's digital or analog. Used to determine the
     * default time for editor preview screenshots.
     */
    @WatchFaceType internal var watchFaceType: Int,

    /** The [UserStyleRepository] for this WatchFace. */
    public val userStyleRepository: UserStyleRepository,

    /** The [Renderer] for this WatchFace. */
    internal val renderer: Renderer,

    /** The [ComplicationsManager] for this WatchFace. */
    internal var complicationsManager: ComplicationsManager =
        ComplicationsManager(emptyList(), userStyleRepository)
) {
    internal var tapListener: TapListener? = null

    public companion object {
        /** Returns whether [LegacyWatchFaceOverlayStyle] is supported on this device. */
        @JvmStatic
        public fun isLegacyWatchFaceOverlayStyleSupported(): Boolean = Build.VERSION.SDK_INT <= 27

        private val componentNameToEditorDelegate = HashMap<ComponentName, EditorDelegate>()

        private var pendingComponentName: ComponentName? = null
        private var pendingEditorDelegateCB: CompletableDeferred<EditorDelegate?>? = null

        /** @hide */
        @JvmStatic
        @UiThread
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun registerEditorDelegate(
            componentName: ComponentName,
            editorDelegate: EditorDelegate
        ) {
            componentNameToEditorDelegate[componentName] = editorDelegate
        }

        internal fun unregisterEditorDelegate(componentName: ComponentName) {
            componentNameToEditorDelegate.remove(componentName)
        }

        /**
         * For use by on watch face editors.
         * @hide
         */
        @JvmStatic
        @UiThread
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun getOrCreateEditorDelegate(
            componentName: ComponentName
        ): CompletableDeferred<EditorDelegate?> {
            componentNameToEditorDelegate[componentName]?.let {
                return CompletableDeferred(it)
            }

            // There's no pre-existing watch face. We expect Home/SysUI to switch the watchface soon
            // so record a pending request...
            pendingComponentName = componentName
            pendingEditorDelegateCB = CompletableDeferred()
            return pendingEditorDelegateCB!!
        }

        @UiThread
        internal fun maybeCreatePendingEditorDelegate(watchface: WatchFaceImpl) {
            if (pendingComponentName != null) {
                pendingEditorDelegateCB?.complete(
                    if (watchface.componentName == pendingComponentName) {
                        watchface.createWFEditorDelegate()
                    } else {
                        null
                    }
                )
                pendingComponentName = null
                pendingEditorDelegateCB = null
            }
        }
    }

    /**
     * Delegate used by on watch face editors.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface EditorDelegate {
        /** The [WatchFace]'s [UserStyleSchema]. */
        public val userStyleSchema: UserStyleSchema

        /** The watch face's  [UserStyle]. */
        public var userStyle: UserStyle

        /** The [WatchFace]'s [ComplicationsManager]. */
        public val complicationsManager: ComplicationsManager

        /** The [WatchFace]'s screen bounds [Rect]. */
        public val screenBounds: Rect

        /** The UTC reference time to use for previews in milliseconds since the epoch. */
        public val previewReferenceTimeMillis: Long

        /** Takes a screenshot with the [UserStyleRepository]'s [UserStyle]. */
        public fun takeScreenshot(
            renderParameters: RenderParameters,
            calendarTimeMillis: Long,
            idToComplicationData: Map<Int, ComplicationData>?
        ): Bitmap

        /** Signals that the activity is going away and resources should be released. */
        public fun onDestroy()
    }

    /**
     * Interface for getting the current system time.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public interface SystemTimeProvider {
        /** Returns the current system time in milliseconds. */
        public fun getSystemTimeMillis(): Long
    }

    /** Listens for taps on the watchface which didn't land on [Complication]s. */
    public interface TapListener {
        /** Called whenever the user taps on the watchface but doesn't hit a [Complication]. */
        @UiThread
        public fun onTap(
            @TapType originalTapType: Int,
            @Px xPos: Int,
            @Px yPos: Int
        )
    }

    /**
     * Legacy Wear 2.0 watch face styling. These settings will be ignored on Wear 3.0 devices.
     *
     * @throws IllegalArgumentException if [viewProtectionMode] has an unexpected value
     */
    public class LegacyWatchFaceOverlayStyle @JvmOverloads constructor(
        /**
         * The view protection mode bit field, must be a combination of
         *     zero or more of [PROTECT_STATUS_BAR], [PROTECT_HOTWORD_INDICATOR],
         *     [PROTECT_WHOLE_SCREEN].
         */
        public val viewProtectionMode: Int,

        /**
         * Controls the position of status icons (battery state, lack of connection) on the screen.
         *
         * This must be any combination of horizontal Gravity constant
         *     ([Gravity.LEFT], [Gravity.CENTER_HORIZONTAL], [Gravity.RIGHT])
         *     and vertical Gravity constants ([Gravity.TOP], [Gravity,CENTER_VERTICAL},
         *     [Gravity,BOTTOM]), e.g. {@code Gravity.LEFT | Gravity.BOTTOM}. On circular screens,
         *     only the vertical gravity is respected.
         */
        public val statusBarGravity: Int,

        /**
         * Controls whether this watch face accepts tap events.
         *
         * Watchfaces that set this {@code true} are indicating they are prepared to receive
         * [IInteractiveWatchFaceSysUI.TAP_TYPE_TOUCH],
         * [IInteractiveWatchFaceSysUI.TAP_TYPE_TOUCH_CANCEL], and
         * [IInteractiveWatchFaceSysUI.TAP_TYPE_TAP] events.
         */
        @get:JvmName("isTapEventsAccepted")
        public val tapEventsAccepted: Boolean,

        /**
         * The accent color which will be used when drawing the unread notification indicator.
         * Default color is white.
         */
        @ColorInt
        public val accentColor: Int = WatchFaceStyle.DEFAULT_ACCENT_COLOR
    ) {
        init {
            if (viewProtectionMode < 0 ||
                viewProtectionMode >
                WatchFaceStyle.PROTECT_STATUS_BAR + WatchFaceStyle.PROTECT_HOTWORD_INDICATOR +
                WatchFaceStyle.PROTECT_WHOLE_SCREEN
            ) {
                throw IllegalArgumentException(
                    "View protection must be combination " +
                        "PROTECT_STATUS_BAR, PROTECT_HOTWORD_INDICATOR or PROTECT_WHOLE_SCREEN"
                )
            }
        }
    }

    /** The UTC preview time in milliseconds since the epoch, or null if not set. */
    @get:SuppressWarnings("AutoBoxing")
    @IntRange(from = 0)
    public var overridePreviewReferenceTimeMillis: Long? = null
        private set

    /** The legacy [LegacyWatchFaceOverlayStyle] which only affects Wear 2.0 devices. */
    public var legacyWatchFaceStyle: LegacyWatchFaceOverlayStyle = LegacyWatchFaceOverlayStyle(
        0,
        0,
        true
    )
        private set

    internal var systemTimeProvider: SystemTimeProvider = object : SystemTimeProvider {
        override fun getSystemTimeMillis() = System.currentTimeMillis()
    }

    /**
     * Overrides the reference time for editor preview images.
     *
     * @param previewReferenceTimeMillis The UTC preview time in milliseconds since the epoch
     */
    public fun setOverridePreviewReferenceTimeMillis(
        @IntRange(from = 0) previewReferenceTimeMillis: Long
    ): WatchFace = apply {
        overridePreviewReferenceTimeMillis = previewReferenceTimeMillis
    }

    /**
     * Sets the legacy [LegacyWatchFaceOverlayStyle] which only affects Wear 2.0 devices.
     */
    public fun setLegacyWatchFaceStyle(
        legacyWatchFaceStyle: LegacyWatchFaceOverlayStyle
    ): WatchFace = apply {
        this.legacyWatchFaceStyle = legacyWatchFaceStyle
    }

    /**
     * Sets an optional [TapListener] which if not `null` gets called on the ui thread whenever
     * the user taps on the watchface but doesn't hit a [Complication].
     */
    @SuppressWarnings("ExecutorRegistration")
    public fun setTapListener(tapListener: TapListener?): WatchFace = apply {
        this.tapListener = tapListener
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    public fun setSystemTimeProvider(systemTimeProvider: SystemTimeProvider): WatchFace = apply {
        this.systemTimeProvider = systemTimeProvider
    }
}

@SuppressLint("SyntheticAccessor")
internal class WatchFaceImpl(
    watchface: WatchFace,
    private val watchFaceHostApi: WatchFaceHostApi,
    private val watchState: WatchState
) {
    companion object {
        internal const val NO_DEFAULT_PROVIDER = SystemProviders.NO_PROVIDER

        internal const val MOCK_TIME_INTENT = "androidx.wear.watchface.MockTime"

        // For debug purposes we support speeding up or slowing down time, these pair of constants
        // configure reading the mock time speed multiplier from a mock time intent.
        internal const val EXTRA_MOCK_TIME_SPEED_MULTIPLIER =
            "androidx.wear.watchface.extra.MOCK_TIME_SPEED_MULTIPLIER"
        private const val MOCK_TIME_DEFAULT_SPEED_MULTIPLIER = 1.0f

        // We support wrapping time between two instants, e.g. to loop an infrequent animation.
        // These constants configure reading this from a mock time intent.
        internal const val EXTRA_MOCK_TIME_WRAPPING_MIN_TIME =
            "androidx.wear.watchface.extra.MOCK_TIME_WRAPPING_MIN_TIME"
        private const val MOCK_TIME_WRAPPING_MIN_TIME_DEFAULT = -1L
        internal const val EXTRA_MOCK_TIME_WRAPPING_MAX_TIME =
            "androidx.wear.watchface.extra.MOCK_TIME_WRAPPING_MAX_TIME"

        // Many devices will enter Time Only Mode to reduce power consumption when the battery is
        // low, in which case only the system watch face will be displayed. On others there is a
        // battery saver mode triggering at 5% battery using an SCR to draw the display. For these
        // there's a gap of 10% battery (Intent.ACTION_BATTERY_LOW gets sent when < 15%) where we
        // clamp the framerate to a maximum of 10fps to conserve power.
        internal const val MAX_LOW_POWER_INTERACTIVE_UPDATE_RATE_MS = 100L

        // Complications are highlighted when tapped and after this delay the highlight is removed.
        internal const val CANCEL_COMPLICATION_HIGHLIGHTED_DELAY_MS = 300L

        // The threshold used to judge whether the battery is low during initialization.  Ideally
        // we would use the threshold for Intent.ACTION_BATTERY_LOW but it's not documented or
        // available programmatically. The value below is the default but it could be overridden
        // by OEMs.
        internal const val INITIAL_LOW_BATTERY_THRESHOLD = 15.0f
    }

    private val systemTimeProvider = watchface.systemTimeProvider
    private val legacyWatchFaceStyle = watchface.legacyWatchFaceStyle
    internal val userStyleRepository = watchface.userStyleRepository
    internal val renderer = watchface.renderer
    internal val complicationsManager = watchface.complicationsManager
    private val tapListener = watchface.tapListener

    private data class MockTime(var speed: Double, var minTime: Long, var maxTime: Long)

    private var mockTime = MockTime(1.0, 0, Long.MAX_VALUE)

    private var lastTappedComplicationId: Int? = null
    private var lastTappedPosition: Point? = null
    private var registeredReceivers = false

    // True if NotificationManager.INTERRUPTION_FILTER_NONE.
    private var muteMode = false
    private var nextDrawTimeMillis: Long = 0

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public val calendar: Calendar = Calendar.getInstance()

    private val pendingSingleTap: CancellableUniqueTask =
        CancellableUniqueTask(watchFaceHostApi.getHandler())
    private val pendingUpdateTime: CancellableUniqueTask =
        CancellableUniqueTask(watchFaceHostApi.getHandler())

    internal val componentName =
        ComponentName(
            watchFaceHostApi.getContext().packageName,
            watchFaceHostApi.getContext().javaClass.name
        )

    internal fun getWatchFaceStyle() = WatchFaceStyle(
        componentName,
        legacyWatchFaceStyle.viewProtectionMode,
        legacyWatchFaceStyle.statusBarGravity,
        legacyWatchFaceStyle.accentColor,
        false,
        false,
        legacyWatchFaceStyle.tapEventsAccepted
    )

    private val broadcastEventObserver = object : BroadcastReceivers.BroadcastEventObserver {
        override fun onActionTimeTick() {
            if (!watchState.isAmbient.value) {
                renderer.invalidate()
            }
        }

        override fun onActionTimeZoneChanged() {
            calendar.timeZone = TimeZone.getDefault()
            renderer.invalidate()
        }

        override fun onActionTimeChanged() {
            // System time has changed hence next scheduled draw is invalid.
            nextDrawTimeMillis = systemTimeProvider.getSystemTimeMillis()
            renderer.invalidate()
        }

        override fun onActionBatteryLow() {
            updateBatteryLowAndNotChargingStatus(true)
        }

        override fun onActionBatteryOkay() {
            updateBatteryLowAndNotChargingStatus(false)
        }

        override fun onActionPowerConnected() {
            updateBatteryLowAndNotChargingStatus(false)
        }

        override fun onMockTime(intent: Intent) {
            mockTime.speed = intent.getFloatExtra(
                EXTRA_MOCK_TIME_SPEED_MULTIPLIER,
                MOCK_TIME_DEFAULT_SPEED_MULTIPLIER
            ).toDouble()
            mockTime.minTime = intent.getLongExtra(
                EXTRA_MOCK_TIME_WRAPPING_MIN_TIME,
                MOCK_TIME_WRAPPING_MIN_TIME_DEFAULT
            )
            // If MOCK_TIME_WRAPPING_MIN_TIME_DEFAULT is specified then use the current time.
            if (mockTime.minTime == MOCK_TIME_WRAPPING_MIN_TIME_DEFAULT) {
                mockTime.minTime = systemTimeProvider.getSystemTimeMillis()
            }
            mockTime.maxTime =
                intent.getLongExtra(EXTRA_MOCK_TIME_WRAPPING_MAX_TIME, Long.MAX_VALUE)
        }

        private fun updateBatteryLowAndNotChargingStatus(value: Boolean) {
            val isBatteryLowAndNotCharging =
                watchState.isBatteryLowAndNotCharging as MutableObservableWatchData
            if (!isBatteryLowAndNotCharging.hasValue() ||
                value != isBatteryLowAndNotCharging.value
            ) {
                isBatteryLowAndNotCharging.value = value
                renderer.invalidate()
            }
        }
    }

    /** The UTC reference time for editor preview images in milliseconds since the epoch. */
    public val previewReferenceTimeMillis: Long =
        watchface.overridePreviewReferenceTimeMillis ?: when (watchface.watchFaceType) {
            WatchFaceType.ANALOG -> watchState.analogPreviewReferenceTimeMillis
            WatchFaceType.DIGITAL -> watchState.digitalPreviewReferenceTimeMillis
            else -> throw InvalidParameterException("Unrecognized watchFaceType")
        }

    init {
        // If the system has a stored user style then Home/SysUI is in charge of style
        // persistence, otherwise we need to do our own.
        val storedUserStyle = watchFaceHostApi.getInitialUserStyle()
        if (storedUserStyle != null) {
            userStyleRepository.userStyle =
                UserStyle(storedUserStyle, userStyleRepository.schema)
        } else {
            // The system doesn't support preference persistence we need to do it ourselves.
            val preferencesFile =
                "watchface_prefs_${watchFaceHostApi.getContext().javaClass.name}.txt"

            userStyleRepository.userStyle = UserStyle(
                readPrefs(watchFaceHostApi.getContext(), preferencesFile),
                userStyleRepository.schema
            )

            userStyleRepository.addUserStyleListener(
                object : UserStyleRepository.UserStyleListener {
                    @SuppressLint("SyntheticAccessor")
                    override fun onUserStyleChanged(userStyle: UserStyle) {
                        writePrefs(watchFaceHostApi.getContext(), preferencesFile, userStyle)
                    }
                })
        }

        renderer.watchFaceHostApi = watchFaceHostApi

        setIsBatteryLowAndNotChargingFromBatteryStatus(
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
                watchFaceHostApi.getContext().registerReceiver(null, iFilter)
            }
        )

        WatchFace.maybeCreatePendingEditorDelegate(this)
    }

    private var inOnSetStyle = false

    private val ambientObserver = Observer<Boolean> {
        scheduleDraw()
        watchFaceHostApi.invalidate()
    }

    private val interruptionFilterObserver = Observer<Int> {
        val inMuteMode = it == NotificationManager.INTERRUPTION_FILTER_NONE
        if (muteMode != inMuteMode) {
            muteMode = inMuteMode
            watchFaceHostApi.invalidate()
        }
    }

    private val visibilityObserver = Observer<Boolean> {
        if (it) {
            registerReceivers()
            // Update time zone in case it changed while we weren't visible.
            calendar.timeZone = TimeZone.getDefault()
            watchFaceHostApi.invalidate()
        } else {
            unregisterReceivers()
        }

        scheduleDraw()
    }

    // Only installed if Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    @SuppressLint("NewApi")
    private val batteryLowAndNotChargingObserver = Observer<Boolean> {
        // To save power we request a lower hardware display frame rate when the battery is low
        // and not charging.
        if (renderer.surfaceHolder.surface.isValid) {
            renderer.surfaceHolder.surface.setFrameRate(
                if (it) {
                    1000f / MAX_LOW_POWER_INTERACTIVE_UPDATE_RATE_MS.toFloat()
                } else {
                    SYSTEM_DECIDES_FRAME_RATE
                },
                FRAME_RATE_COMPATIBILITY_DEFAULT
            )
        }
    }

    init {
        // We need to inhibit an immediate callback during initialization because members are not
        // fully constructed and it will fail. It's also superfluous because we're going to render
        // anyway.
        var initFinished = false
        complicationsManager.init(
            watchFaceHostApi, calendar, renderer,
            object : Complication.InvalidateListener {
                @SuppressWarnings("SyntheticAccessor")
                override fun onInvalidate() {
                    // Ensure we render a frame if the Complication needs rendering, e.g. because it
                    // loaded an image. However if we're animating there's no need to trigger an
                    // extra invalidation.
                    if (renderer.shouldAnimate() && computeDelayTillNextFrame(
                            nextDrawTimeMillis,
                            systemTimeProvider.getSystemTimeMillis()
                        ) < MIN_PERCEPTABLE_DELAY_MILLIS
                    ) {
                        return
                    }
                    if (initFinished) {
                        watchFaceHostApi.invalidate()
                    }
                }
            }
        )

        if (!watchState.isHeadless) {
            WatchFace.registerEditorDelegate(componentName, WFEditorDelegate())
        }

        watchState.isAmbient.addObserver(ambientObserver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !watchState.isHeadless) {
            watchState.isBatteryLowAndNotCharging.addObserver(batteryLowAndNotChargingObserver)
        }
        watchState.interruptionFilter.addObserver(interruptionFilterObserver)
        watchState.isVisible.addObserver(visibilityObserver)

        initFinished = true
    }

    internal fun createWFEditorDelegate() = WFEditorDelegate() as WatchFace.EditorDelegate

    internal inner class WFEditorDelegate : WatchFace.EditorDelegate {
        override val userStyleSchema: UserStyleSchema
            get() = userStyleRepository.schema

        override var userStyle: UserStyle
            get() = userStyleRepository.userStyle
            set(value) {
                userStyleRepository.userStyle = value
                watchFaceHostApi.onUserStyleChanged()
            }

        override val complicationsManager: ComplicationsManager
            get() = this@WatchFaceImpl.complicationsManager

        override val screenBounds
            get() = renderer.screenBounds

        override val previewReferenceTimeMillis
            get() = this@WatchFaceImpl.previewReferenceTimeMillis

        override fun takeScreenshot(
            renderParameters: RenderParameters,
            calendarTimeMillis: Long,
            idToComplicationData: Map<Int, ComplicationData>?
        ): Bitmap = TraceEvent("WFEditorDelegate.takeScreenshot").use {
            val oldComplicationData =
                complicationsManager.complications.values.map {
                    it.renderer.getIdAndData() ?: IdAndComplicationData(
                        it.id,
                        NoDataComplicationData()
                    )
                }

            idToComplicationData?.let {
                for ((id, complicationData) in it) {
                    complicationsManager[id]!!.renderer.setIdAndData(
                        IdAndComplicationData(id, complicationData),
                        false
                    )
                }
            }
            val screenShot = renderer.takeScreenshot(
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = calendarTimeMillis
                },
                renderParameters
            )
            if (idToComplicationData != null) {
                for (idAndData in oldComplicationData) {
                    complicationsManager[idAndData.complicationId]!!.renderer
                        .setIdAndData(idAndData, false)
                }
            }
            return screenShot
        }

        override fun onDestroy(): Unit = TraceEvent("WFEditorDelegate.onDestroy").use {
            if (watchState.isHeadless) {
                this@WatchFaceImpl.onDestroy()
            }
        }
    }

    internal fun setIsBatteryLowAndNotChargingFromBatteryStatus(batteryStatus: Intent?) {
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val batteryPercent: Float = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        } ?: 100.0f
        val isBatteryLowAndNotCharging =
            watchState.isBatteryLowAndNotCharging as MutableObservableWatchData
        isBatteryLowAndNotCharging.value =
            (batteryPercent < INITIAL_LOW_BATTERY_THRESHOLD) && !isCharging
    }

    /**
     * Called by the system in response to remote configuration, on the main thread.
     */
    internal fun onSetStyleInternal(style: UserStyle) {
        // No need to echo the userStyle back.
        inOnSetStyle = true
        userStyleRepository.userStyle = style
        inOnSetStyle = false
    }

    internal fun onDestroy() {
        pendingSingleTap.cancel()
        pendingUpdateTime.cancel()
        renderer.onDestroy()
        watchState.isAmbient.removeObserver(ambientObserver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !watchState.isHeadless) {
            watchState.isBatteryLowAndNotCharging.removeObserver(batteryLowAndNotChargingObserver)
        }
        watchState.interruptionFilter.removeObserver(interruptionFilterObserver)
        watchState.isVisible.removeObserver(visibilityObserver)
        if (!watchState.isHeadless) {
            WatchFace.unregisterEditorDelegate(componentName)
        }
        unregisterReceivers()
    }

    private fun registerReceivers() {
        if (registeredReceivers) {
            return
        }
        registeredReceivers = true
        BroadcastReceivers.addBroadcastEventObserver(
            watchFaceHostApi.getContext(),
            broadcastEventObserver
        )
    }

    private fun unregisterReceivers() {
        if (!registeredReceivers) {
            return
        }
        registeredReceivers = false
        BroadcastReceivers.removeBroadcastEventObserver(broadcastEventObserver)
    }

    private fun scheduleDraw() {
        // Separate calls are issued to deliver the state of isAmbient and isVisible, so during init
        // we might not yet know the state of both (which is required by the shouldAnimate logic).
        if (!watchState.isAmbient.hasValue() || !watchState.isVisible.hasValue()) {
            return
        }

        setCalendarTime(systemTimeProvider.getSystemTimeMillis())
        if (renderer.shouldAnimate()) {
            pendingUpdateTime.postUnique {
                watchFaceHostApi.invalidate()
            }
        }
    }

    /**
     * Sets the calendar's time in milliseconds adjusted by the mock time controls.
     */
    private fun setCalendarTime(timeMillis: Long) {
        // This adjustment allows time to be sped up or slowed down and to wrap between two
        // instants. This is useful when developing animations that occur infrequently (e.g.
        // hourly).
        val millis = (mockTime.speed * (timeMillis - mockTime.minTime).toDouble()).toLong()
        val range = mockTime.maxTime - mockTime.minTime
        var delta = millis % range
        if (delta < 0) {
            delta += range
        }
        calendar.timeInMillis = mockTime.minTime + delta
    }

    /** @hide */
    @UiThread
    internal fun maybeUpdateDrawMode() {
        var newDrawMode = if (watchState.isBatteryLowAndNotCharging.getValueOr(false)) {
            DrawMode.LOW_BATTERY_INTERACTIVE
        } else {
            DrawMode.INTERACTIVE
        }
        // Watch faces may wish to run an animation while entering ambient mode and we let them
        // defer entering ambient mode.
        if (watchState.isAmbient.value && !renderer.shouldAnimate()) {
            newDrawMode = DrawMode.AMBIENT
        } else if (muteMode) {
            newDrawMode = DrawMode.MUTE
        }
        renderer.renderParameters =
            RenderParameters(
                newDrawMode,
                RenderParameters.DRAW_ALL_LAYERS,
                null,
                Color.BLACK // Required by the constructor but unused.
            )
    }

    /** @hide */
    @UiThread
    internal fun onDraw() {
        setCalendarTime(systemTimeProvider.getSystemTimeMillis())
        maybeUpdateDrawMode()
        renderer.renderInternal(calendar)

        val currentTimeMillis = systemTimeProvider.getSystemTimeMillis()
        setCalendarTime(currentTimeMillis)
        if (renderer.shouldAnimate()) {
            val delayMillis = computeDelayTillNextFrame(nextDrawTimeMillis, currentTimeMillis)
            nextDrawTimeMillis = currentTimeMillis + delayMillis
            pendingUpdateTime.postDelayedUnique(delayMillis) { watchFaceHostApi.invalidate() }
        }
    }

    internal fun onSurfaceRedrawNeeded() {
        setCalendarTime(systemTimeProvider.getSystemTimeMillis())
        maybeUpdateDrawMode()
        renderer.renderInternal(calendar)
    }

    /** @hide */
    @UiThread
    internal fun computeDelayTillNextFrame(
        beginFrameTimeMillis: Long,
        currentTimeMillis: Long
    ): Long {
        // Limit update rate to conserve power when the battery is low and not charging.
        val updateRateMillis =
            if (watchState.isBatteryLowAndNotCharging.getValueOr(false)) {
                max(
                    renderer.interactiveDrawModeUpdateDelayMillis,
                    MAX_LOW_POWER_INTERACTIVE_UPDATE_RATE_MS
                )
            } else {
                renderer.interactiveDrawModeUpdateDelayMillis
            }
        // Note beginFrameTimeMillis could be in the future if the user adjusted the time so we need
        // to compute min(beginFrameTimeMillis, currentTimeMillis).
        var nextFrameTimeMillis =
            Math.min(beginFrameTimeMillis, currentTimeMillis) + updateRateMillis
        // Drop frames if needed (happens when onDraw is slow).
        if (nextFrameTimeMillis <= currentTimeMillis) {
            // Compute the next runtime after currentTimeMillis with the same phase as
            //  beginFrameTimeMillis to keep the animation smooth.
            val phaseAdjust =
                updateRateMillis +
                    ((nextFrameTimeMillis - currentTimeMillis) % updateRateMillis)
            nextFrameTimeMillis = currentTimeMillis + phaseAdjust
        }
        return nextFrameTimeMillis - currentTimeMillis
    }

    /**
     * Called when new complication data is received.
     *
     * @param watchFaceComplicationId The id of the complication that the data relates to. This will
     *     be an id that was previously sent in a call to [setActiveComplications].
     * @param data The [ComplicationData] that should be displayed in the complication.
     */
    @UiThread
    internal fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData) {
        complicationsManager.onComplicationDataUpdate(watchFaceComplicationId, data)
        watchFaceHostApi.invalidate()
    }

    /**
     * Called when a tap or touch related event occurs. Detects double and single taps on
     * complications and triggers the associated action.
     *
     * @param originalTapType Value representing the event sent to the wallpaper
     * @param x X coordinate of the event
     * @param y Y coordinate of the event
     */
    @UiThread
    internal fun onTapCommand(
        @TapType originalTapType: Int,
        x: Int,
        y: Int
    ) {
        val tappedComplication = complicationsManager.getComplicationAt(x, y)
        if (tappedComplication == null) {
            clearGesture()
            tapListener?.onTap(originalTapType, x, y)
            return
        }

        // Unfortunately we don't get MotionEvents so we can't directly use the GestureDetector
        // to distinguish between single and double taps. Currently we do that ourselves.
        // TODO(alexclarke): Revisit this
        var tapType = originalTapType
        when (tapType) {
            TapType.TOUCH -> {
                lastTappedPosition = Point(x, y)
            }
            TapType.TOUCH_CANCEL -> {
                lastTappedPosition?.let { safeLastTappedPosition ->
                    if ((safeLastTappedPosition.x == x) && (safeLastTappedPosition.y == y)) {
                        tapType = TapType.TAP
                    }
                }
                lastTappedPosition = null
            }
        }

        when (tapType) {
            TapType.TAP -> {
                if (tappedComplication.id != lastTappedComplicationId &&
                    lastTappedComplicationId != null
                ) {
                    clearGesture()
                    return
                }
                if (!pendingSingleTap.isPending()) {
                    // Give the user immediate visual feedback, the UI feels sluggish if we defer
                    // this.
                    complicationsManager.bringAttentionToComplication(tappedComplication.id)

                    lastTappedComplicationId = tappedComplication.id

                    // This could either be a single or a double tap, post a task to process the
                    // single tap which will get canceled if a double tap gets there first
                    pendingSingleTap.postDelayedUnique(
                        ViewConfiguration.getDoubleTapTimeout().toLong()
                    ) {
                        complicationsManager.onComplicationSingleTapped(tappedComplication.id)
                        watchFaceHostApi.invalidate()
                        clearGesture()
                    }
                }
            }
            TapType.TOUCH -> {
                // Make sure the user isn't doing a swipe.
                if (tappedComplication.id != lastTappedComplicationId &&
                    lastTappedComplicationId != null
                ) {
                    clearGesture()
                }
                lastTappedComplicationId = tappedComplication.id
            }
            else -> clearGesture()
        }
    }

    private fun clearGesture() {
        lastTappedComplicationId = null
        pendingSingleTap.cancel()
    }

    @UiThread
    fun dump(writer: IndentingPrintWriter) {
        writer.println("WatchFaceImpl ($componentName): ")
        writer.increaseIndent()
        writer.println("calendar=$calendar")
        writer.println("mockTime.maxTime=${mockTime.maxTime}")
        writer.println("mockTime.minTime=${mockTime.minTime}")
        writer.println("mockTime.speed=${mockTime.speed}")
        writer.println("nextDrawTimeMillis=$nextDrawTimeMillis")
        writer.println("muteMode=$muteMode")
        writer.println("pendingSingleTap=${pendingSingleTap.isPending()}")
        writer.println("pendingUpdateTime=${pendingUpdateTime.isPending()}")
        writer.println("lastTappedComplicationId=$lastTappedComplicationId")
        writer.println("lastTappedPosition=$lastTappedPosition")
        writer.println("userStyleRepository.userStyle=${userStyleRepository.userStyle}")
        writer.println("userStyleRepository.schema=${userStyleRepository.schema}")
        watchState.dump(writer)
        complicationsManager.dump(writer)
        renderer.dump(writer)
        writer.decreaseIndent()
    }
}
