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

package androidx.wear.watchface.samples

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.icu.util.Calendar
import android.view.SurfaceHolder
import androidx.wear.complications.ComplicationBounds
import androidx.wear.complications.DefaultComplicationProviderPolicy
import androidx.wear.complications.SystemProviders
import androidx.wear.complications.data.ComplicationType
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.Complication
import androidx.wear.watchface.ComplicationsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.LayerMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.Layer
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting.BooleanUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationsUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationsUserStyleSetting.ComplicationOverlay
import androidx.wear.watchface.style.UserStyleSetting.DoubleRangeUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import kotlin.math.cos
import kotlin.math.sin

private const val CENTER_CIRCLE_DIAMETER_FRACTION = 0.03738f
private const val OUTER_CIRCLE_STROKE_THICKNESS_FRACTION = 0.00467f
private const val NUMBER_STYLE_OUTER_CIRCLE_RADIUS_FRACTION = 0.00584f

private const val GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION = 0.03738f
private const val GAP_BETWEEN_HAND_AND_CENTER_FRACTION =
    0.01869f + CENTER_CIRCLE_DIAMETER_FRACTION / 2.0f

private const val HOUR_HAND_LENGTH_FRACTION = 0.21028f
private const val HOUR_HAND_THICKNESS_FRACTION = 0.02336f
private const val MINUTE_HAND_LENGTH_FRACTION = 0.3783f
private const val MINUTE_HAND_THICKNESS_FRACTION = 0.0163f
private const val SECOND_HAND_LENGTH_FRACTION = 0.37383f
private const val SECOND_HAND_THICKNESS_FRACTION = 0.00934f

private const val NUMBER_RADIUS_FRACTION = 0.45f

val COLOR_STYLE_SETTING = "color_style_setting"
val RED_STYLE = "red_style"
val GREEN_STYLE = "green_style"
val BLUE_STYLE = "blue_style"

val DRAW_HOUR_PIPS_STYLE_SETTING = "draw_hour_pips_style_setting"

val WATCH_HAND_LENGTH_STYLE_SETTING = "watch_hand_length_style_setting"

val COMPLICATIONS_STYLE_SETTING = "complications_style_setting"
val NO_COMPLICATIONS = "NO_COMPLICATIONS"
val LEFT_COMPLICATION = "LEFT_COMPLICATION"
val RIGHT_COMPLICATION = "RIGHT_COMPLICATION"
val LEFT_AND_RIGHT_COMPLICATIONS = "LEFT_AND_RIGHT_COMPLICATIONS"

/** How long each frame is displayed at expected frame rate.  */
private const val FRAME_PERIOD_MS: Long = 16L

/** A simple example canvas based analog watch face. */
open class ExampleCanvasAnalogWatchFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState
    ) = createExampleCanvasAnalogWatchFaceBuilder(
        this,
        surfaceHolder,
        watchState
    )
}

const val EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID = 101
const val EXAMPLE_CANVAS_WATCHFACE_RIGHT_COMPLICATION_ID = 102

fun createExampleCanvasAnalogWatchFaceBuilder(
    context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState
): WatchFace {
    val watchFaceStyle = WatchFaceColorStyle.create(context, RED_STYLE)
    val colorStyleSetting = ListUserStyleSetting(
        COLOR_STYLE_SETTING,
        context.getString(R.string.colors_style_setting),
        context.getString(R.string.colors_style_setting_description),
        icon = null,
        options = listOf(
            ListUserStyleSetting.ListOption(
                RED_STYLE,
                context.getString(R.string.colors_style_red),
                Icon.createWithResource(context, R.drawable.red_style)
            ),
            ListUserStyleSetting.ListOption(
                GREEN_STYLE,
                context.getString(R.string.colors_style_green),
                Icon.createWithResource(context, R.drawable.green_style)
            ),
            ListUserStyleSetting.ListOption(
                BLUE_STYLE,
                context.getString(R.string.colors_style_blue),
                Icon.createWithResource(context, R.drawable.blue_style)
            )
        ),
        listOf(Layer.BASE_LAYER, Layer.COMPLICATIONS, Layer.TOP_LAYER)
    )
    val drawHourPipsStyleSetting = BooleanUserStyleSetting(
        DRAW_HOUR_PIPS_STYLE_SETTING,
        context.getString(R.string.watchface_pips_setting),
        context.getString(R.string.watchface_pips_setting_description),
        null,
        listOf(Layer.BASE_LAYER),
        true
    )
    val watchHandLengthStyleSetting = DoubleRangeUserStyleSetting(
        WATCH_HAND_LENGTH_STYLE_SETTING,
        context.getString(R.string.watchface_hand_length_setting),
        context.getString(R.string.watchface_hand_length_setting_description),
        null,
        0.25,
        1.0,
        listOf(Layer.TOP_LAYER),
        0.75
    )
    // These are style overrides applied on top of the complications passed into
    // complicationsManager below.
    val complicationsStyleSetting = ComplicationsUserStyleSetting(
        COMPLICATIONS_STYLE_SETTING,
        context.getString(R.string.watchface_complications_setting),
        context.getString(R.string.watchface_complications_setting_description),
        icon = null,
        complicationConfig = listOf(
            ComplicationsUserStyleSetting.ComplicationsOption(
                LEFT_AND_RIGHT_COMPLICATIONS,
                context.getString(R.string.watchface_complications_setting_both),
                null,
                // NB this list is empty because each [ComplicationOverlay] is applied on top of
                // the initial config.
                listOf()
            ),
            ComplicationsUserStyleSetting.ComplicationsOption(
                NO_COMPLICATIONS,
                context.getString(R.string.watchface_complications_setting_none),
                null,
                listOf(
                    ComplicationOverlay(
                        EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
                        enabled = false
                    ),
                    ComplicationOverlay(
                        EXAMPLE_CANVAS_WATCHFACE_RIGHT_COMPLICATION_ID,
                        enabled = false
                    )
                )
            ),
            ComplicationsUserStyleSetting.ComplicationsOption(
                LEFT_COMPLICATION,
                context.getString(R.string.watchface_complications_setting_left),
                null,
                listOf(
                    ComplicationOverlay(
                        EXAMPLE_CANVAS_WATCHFACE_RIGHT_COMPLICATION_ID,
                        enabled = false
                    )
                )
            ),
            ComplicationsUserStyleSetting.ComplicationsOption(
                RIGHT_COMPLICATION,
                context.getString(R.string.watchface_complications_setting_right),
                null,
                listOf(
                    ComplicationOverlay(
                        EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
                        enabled = false
                    )
                )
            )
        ),
        listOf(Layer.COMPLICATIONS)
    )
    val userStyleRepository = UserStyleRepository(
        UserStyleSchema(
            listOf(
                colorStyleSetting,
                drawHourPipsStyleSetting,
                watchHandLengthStyleSetting,
                complicationsStyleSetting
            )
        )
    )
    val leftComplication = Complication.createRoundRectComplicationBuilder(
        EXAMPLE_CANVAS_WATCHFACE_LEFT_COMPLICATION_ID,
        watchFaceStyle.getComplicationDrawableRenderer(context, watchState),
        listOf(
            ComplicationType.RANGED_VALUE,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.MONOCHROMATIC_IMAGE,
            ComplicationType.SMALL_IMAGE
        ),
        DefaultComplicationProviderPolicy(SystemProviders.DAY_OF_WEEK),
        ComplicationBounds(RectF(0.2f, 0.4f, 0.4f, 0.6f))
    ).setDefaultProviderType(ComplicationType.SHORT_TEXT)
        .build()
    val rightComplication = Complication.createRoundRectComplicationBuilder(
        EXAMPLE_CANVAS_WATCHFACE_RIGHT_COMPLICATION_ID,
        watchFaceStyle.getComplicationDrawableRenderer(context, watchState),
        listOf(
            ComplicationType.RANGED_VALUE,
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.MONOCHROMATIC_IMAGE,
            ComplicationType.SMALL_IMAGE
        ),
        DefaultComplicationProviderPolicy(SystemProviders.STEP_COUNT),
        ComplicationBounds(RectF(0.6f, 0.4f, 0.8f, 0.6f))
    ).setDefaultProviderType(ComplicationType.SHORT_TEXT)
        .build()
    val complicationsManager = ComplicationsManager(
        listOf(leftComplication, rightComplication),
        userStyleRepository
    )
    val renderer = ExampleAnalogWatchCanvasRenderer(
        surfaceHolder,
        context,
        watchFaceStyle,
        userStyleRepository,
        watchState,
        colorStyleSetting,
        drawHourPipsStyleSetting,
        watchHandLengthStyleSetting,
        complicationsManager
    )
    return WatchFace(
        WatchFaceType.ANALOG,
        userStyleRepository,
        renderer,
        complicationsManager
    )
}

class ExampleAnalogWatchCanvasRenderer(
    surfaceHolder: SurfaceHolder,
    private val context: Context,
    private var watchFaceColorStyle: WatchFaceColorStyle,
    userStyleRepository: UserStyleRepository,
    private val watchState: WatchState,
    private val colorStyleSetting: ListUserStyleSetting,
    private val drawPipsStyleSetting: BooleanUserStyleSetting,
    private val watchHandLengthStyleSettingDouble: DoubleRangeUserStyleSetting,
    private val complicationsManager: ComplicationsManager
) : Renderer.CanvasRenderer(
    surfaceHolder,
    userStyleRepository,
    watchState,
    CanvasType.HARDWARE,
    FRAME_PERIOD_MS
) {
    private val clockHandPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = context.resources.getDimensionPixelSize(
            R.dimen.clock_hand_stroke_width
        ).toFloat()
    }

    private val outerElementPaint = Paint().apply {
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
    }

    companion object {
        private val HOUR_MARKS = arrayOf("3", "6", "9", "12")
    }

    private lateinit var hourHandFill: Path
    private lateinit var hourHandBorder: Path
    private lateinit var minuteHandFill: Path
    private lateinit var minuteHandBorder: Path
    private lateinit var secondHand: Path

    private var drawHourPips = true
    private var watchHandScale = 1.0f

    init {
        userStyleRepository.addUserStyleListener(
            object : UserStyleRepository.UserStyleListener {
                @SuppressLint("SyntheticAccessor")
                override fun onUserStyleChanged(userStyle: UserStyle) {
                    watchFaceColorStyle =
                        WatchFaceColorStyle.create(
                            context,
                            userStyle[colorStyleSetting]!!.id
                        )

                    // Apply the userStyle to the complications. ComplicationDrawables for each of
                    // the styles are defined in XML so we need to replace the complication's
                    // drawables.
                    for ((_, complication) in complicationsManager.complications) {
                        complication.renderer =
                            watchFaceColorStyle.getComplicationDrawableRenderer(context, watchState)
                    }

                    val drawPipsOption = userStyle[drawPipsStyleSetting]?.toBooleanOption()!!
                    val watchHandLengthOption =
                        userStyle[watchHandLengthStyleSettingDouble]?.toDoubleRangeOption()!!

                    drawHourPips = drawPipsOption.value
                    watchHandScale = watchHandLengthOption.value.toFloat()
                }
            }
        )
    }

    override fun render(canvas: Canvas, bounds: Rect, calendar: Calendar) {
        val style = if (renderParameters.drawMode == DrawMode.AMBIENT) {
            watchFaceColorStyle.ambientStyle
        } else {
            watchFaceColorStyle.activeStyle
        }

        canvas.drawColor(style.backgroundColor)

        // CanvasComplicationDrawable already obeys rendererParameters.
        drawComplications(canvas, calendar)

        if (renderParameters.layerParameters[Layer.TOP_LAYER] != LayerMode.HIDE) {
            drawClockHands(canvas, bounds, calendar, style)
        }

        if (renderParameters.drawMode != DrawMode.AMBIENT &&
            renderParameters.layerParameters[Layer.BASE_LAYER] != LayerMode.HIDE &&
            drawHourPips
        ) {
            drawNumberStyleOuterElement(canvas, bounds, style)
        }
    }

    private fun drawClockHands(
        canvas: Canvas,
        bounds: Rect,
        calendar: Calendar,
        style: ColorStyle
    ) {
        recalculateClockHands(bounds)
        val hours = calendar.get(Calendar.HOUR).toFloat()
        val minutes = calendar.get(Calendar.MINUTE).toFloat()
        val seconds = calendar.get(Calendar.SECOND).toFloat() +
            (calendar.get(Calendar.MILLISECOND).toFloat() / 1000f)

        val hourRot = (hours + minutes / 60.0f + seconds / 3600.0f) / 12.0f * 360.0f
        val minuteRot = (minutes + seconds / 60.0f) / 60.0f * 360.0f

        canvas.save()

        recalculateClockHands(bounds)

        if (renderParameters.drawMode == DrawMode.AMBIENT) {
            clockHandPaint.style = Paint.Style.STROKE
            clockHandPaint.color = style.primaryColor
            canvas.scale(
                watchHandScale,
                watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            canvas.rotate(hourRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawPath(hourHandBorder, clockHandPaint)
            canvas.rotate(-hourRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.scale(
                1.0f / watchHandScale,
                1.0f / watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )

            clockHandPaint.color = style.secondaryColor
            canvas.scale(
                watchHandScale,
                watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            canvas.rotate(minuteRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawPath(minuteHandBorder, clockHandPaint)
            canvas.rotate(-minuteRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.scale(
                1.0f / watchHandScale,
                1.0f / watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
        } else {
            clockHandPaint.style = Paint.Style.FILL
            clockHandPaint.color = style.primaryColor
            canvas.scale(
                watchHandScale,
                watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            canvas.rotate(hourRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawPath(hourHandFill, clockHandPaint)
            canvas.rotate(-hourRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.scale(
                1.0f / watchHandScale,
                1.0f / watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )

            clockHandPaint.color = style.secondaryColor
            canvas.scale(
                watchHandScale,
                watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            canvas.rotate(minuteRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawPath(minuteHandFill, clockHandPaint)
            canvas.rotate(-minuteRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.scale(
                1.0f / watchHandScale,
                1.0f / watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )

            val secondsRot = seconds / 60.0f * 360.0f

            clockHandPaint.color = style.secondaryColor
            canvas.scale(
                watchHandScale,
                watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
            canvas.rotate(secondsRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.drawPath(secondHand, clockHandPaint)
            canvas.rotate(-secondsRot, bounds.exactCenterX(), bounds.exactCenterY())
            canvas.scale(
                1.0f / watchHandScale,
                1.0f / watchHandScale,
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
        }

        canvas.restore()
    }

    private fun recalculateClockHands(bounds: Rect) {
        val rx = 1.5f
        val ry = 1.5f

        hourHandBorder =
            createClockHand(bounds, HOUR_HAND_LENGTH_FRACTION, HOUR_HAND_THICKNESS_FRACTION, rx, ry)

        minuteHandBorder =
            createClockHand(
                bounds, MINUTE_HAND_LENGTH_FRACTION, MINUTE_HAND_THICKNESS_FRACTION, rx, ry
            )

        hourHandFill =
            createClockHand(
                bounds,
                HOUR_HAND_LENGTH_FRACTION,
                HOUR_HAND_THICKNESS_FRACTION,
                rx,
                ry
            )

        minuteHandFill =
            createClockHand(
                bounds,
                MINUTE_HAND_LENGTH_FRACTION,
                MINUTE_HAND_THICKNESS_FRACTION,
                rx,
                ry
            )

        secondHand =
            createClockHand(
                bounds,
                SECOND_HAND_LENGTH_FRACTION,
                SECOND_HAND_THICKNESS_FRACTION,
                0.0f,
                0.0f
            )
    }

    /**
     * Returns a round rect clock hand if {@code rx} and {@code ry} equals to 0, otherwise return a
     * rect clock hand.
     *
     * @param bounds The bounds use to determine the coordinate of the clock hand.
     * @param length Clock hand's length, in fraction of {@code bounds.width()}.
     * @param thickness Clock hand's thickness, in fraction of {@code bounds.width()}.
     * @param rx The x-radius of the rounded corners on the round-rectangle.
     * @param ry The y-radius of the rounded corners on the round-rectangle.
     */
    private fun createClockHand(
        bounds: Rect,
        length: Float,
        thickness: Float,
        rx: Float,
        ry: Float
    ): Path {
        val width = bounds.width()
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val left = cx - thickness / 2 * width
        val top = cy - (GAP_BETWEEN_HAND_AND_CENTER_FRACTION + length) * width
        val right = cx + thickness / 2 * width
        val bottom = cy - GAP_BETWEEN_HAND_AND_CENTER_FRACTION * width
        val path = Path()
        if (rx != 0.0f || ry != 0.0f) {
            path.addRoundRect(left, top, right, bottom, rx, ry, Path.Direction.CW)
        } else {
            path.addRect(left, top, right, bottom, Path.Direction.CW)
        }
        return path
    }

    private fun drawComplications(canvas: Canvas, calendar: Calendar) {
        for ((_, complication) in complicationsManager.complications) {
            if (complication.enabled) {
                complication.render(canvas, calendar, renderParameters)
            }
        }
    }

    private fun drawNumberStyleOuterElement(canvas: Canvas, bounds: Rect, style: ColorStyle) {
        val textBounds = Rect()
        textPaint.color = style.outerElementColor
        for (i in 0 until 4) {
            val rot = 0.5f * (i + 1).toFloat() * Math.PI
            val dx = sin(rot).toFloat() * NUMBER_RADIUS_FRACTION * bounds.width().toFloat()
            val dy = -cos(rot).toFloat() * NUMBER_RADIUS_FRACTION * bounds.width().toFloat()
            textPaint.getTextBounds(HOUR_MARKS[i], 0, HOUR_MARKS[i].length, textBounds)
            canvas.drawText(
                HOUR_MARKS[i],
                bounds.exactCenterX() + dx - textBounds.width() / 2.0f,
                bounds.exactCenterY() + dy + textBounds.height() / 2.0f,
                textPaint
            )
        }

        // Draws the circle for the remain hour indicators.
        outerElementPaint.strokeWidth = OUTER_CIRCLE_STROKE_THICKNESS_FRACTION * bounds.width()
        outerElementPaint.color = style.outerElementColor
        canvas.save()
        for (i in 0 until 12) {
            if (i % 3 != 0) {
                drawTopMiddleCircle(
                    canvas,
                    bounds,
                    NUMBER_STYLE_OUTER_CIRCLE_RADIUS_FRACTION
                )
            }
            canvas.rotate(360.0f / 12.0f, bounds.exactCenterX(), bounds.exactCenterY())
        }
        canvas.restore()
    }

    /** Draws the outer circle on the top middle of the given bounds. */
    private fun drawTopMiddleCircle(
        canvas: Canvas,
        bounds: Rect,
        radiusFraction: Float
    ) {
        outerElementPaint.style = Paint.Style.FILL_AND_STROKE

        val cx = 0.5f * bounds.width().toFloat()
        val cy = bounds.width() * (GAP_BETWEEN_OUTER_CIRCLE_AND_BORDER_FRACTION + radiusFraction)

        canvas.drawCircle(
            cx,
            cy,
            radiusFraction * bounds.width(),
            outerElementPaint
        )
    }
}
