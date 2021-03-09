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

package androidx.wear.widget

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.wear.test.R
import androidx.wear.widget.util.AsyncViewActions.waitForMatchingView
import androidx.wear.widget.WearArcLayout.LayoutParams.VALIGN_CENTER
import androidx.wear.widget.WearArcLayout.LayoutParams.VALIGN_OUTER
import androidx.wear.widget.WearArcLayout.LayoutParams.VALIGN_INNER
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.any
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@MediumTest
class WearArcLayoutTest {

    private val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val renderDoneLatch = CountDownLatch(1)

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule("wear/wear")

    private fun doOneTest(
        key: String,
        views: List<View>,
        backgroundColor: Int = Color.GRAY,
        interactiveFunction: (FrameLayout.() -> Unit)? = null

    ) {
        // Set the main frame.
        val mainFrame = FrameLayout(ApplicationProvider.getApplicationContext())
        mainFrame.setBackgroundColor(backgroundColor)

        for (view in views) {
            mainFrame.addView(view)
        }
        val screenWidth = MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, MeasureSpec.EXACTLY)
        val screenHeight = MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT, MeasureSpec.EXACTLY)
        mainFrame.measure(screenWidth, screenHeight)
        mainFrame.layout(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
        mainFrame.draw(canvas)
        // If an interactive function is set, call it now and redraw.
        // The function will generate mouse events and then we draw again to see the result
        // displayed on the views (the test records and shows mouse events in the view)
        interactiveFunction?.let {
            it(mainFrame)
            mainFrame.draw(canvas)
        }
        renderDoneLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        bitmap.assertAgainstGolden(screenshotRule, key)
    }

    private fun createArc(text1: String = "SWEEP", text2: String = "Default") =
        WearArcLayout(ApplicationProvider.getApplicationContext()).apply {
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = text1
                    textColor = Color.BLUE
                    setBackgroundColor(Color.rgb(100, 100, 0))
                    minSweepDegrees = 45f
                }
            )
            addView(
                TextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "TXT"
                    setTextColor(Color.GREEN)
                    layoutParams =
                        WearArcLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                }
            )
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = text2
                    textColor = Color.RED
                    setBackgroundColor(Color.rgb(0, 100, 100))
                }
            )
        }

    private fun createMixedArc() =
        WearArcLayout(ApplicationProvider.getApplicationContext()).apply {
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "One"
                    setBackgroundColor(Color.rgb(100, 100, 100))
                    maxSweepDegrees = 20f
                    clockwise = true
                }
            )
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "Two"
                    setBackgroundColor(Color.rgb(150, 150, 150))
                    maxSweepDegrees = 20f
                    clockwise = false
                }
            )
            addView(
                TextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "TXT"
                    setTextColor(Color.GREEN)
                    layoutParams =
                        WearArcLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { rotate = false }
                }
            )
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "Three"
                    setBackgroundColor(Color.rgb(100, 100, 100))
                    maxSweepDegrees = 20f
                    clockwise = true
                }
            )
            addView(
                WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                    text = "Four"
                    setBackgroundColor(Color.rgb(150, 150, 150))
                    maxSweepDegrees = 20f
                    clockwise = false
                }
            )
        }

    @Test
    @Throws(Exception::class)
    fun testArcs() {
        doOneTest(
            "basic_arcs_screenshot",
            listOf(
                createArc(),
                createArc("SWEEP", "Start").apply {
                    anchorAngleDegrees = 90f
                    anchorType = WearArcLayout.ANCHOR_START
                },
                createArc("SWEEP", "End").apply {
                    anchorAngleDegrees = 270f
                    anchorType = WearArcLayout.ANCHOR_END
                },
                createArc("SWEEP", "Center").apply {
                    anchorAngleDegrees = 315f
                    anchorType = WearArcLayout.ANCHOR_CENTER
                }
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testArcsCcw() {
        doOneTest(
            "basic_arcs_ccw_screenshot",
            listOf(
                createArc(),
                createArc("SWEEP", "Start").apply {
                    anchorAngleDegrees = 270f
                    anchorType = WearArcLayout.ANCHOR_START
                },
                createArc("SWEEP", "End").apply {
                    anchorAngleDegrees = 90f
                    anchorType = WearArcLayout.ANCHOR_END
                },
                createArc("SWEEP", "Center").apply {
                    anchorAngleDegrees = 45f
                    anchorType = WearArcLayout.ANCHOR_CENTER
                }
            ).apply { forEach { it.clockwise = false } }
        )
    }

    @Test
    @Throws(Exception::class)
    fun testArcsMixed() {
        doOneTest(
            "basic_arcs_mix_screenshot",
            listOf(
                createMixedArc(),
                createMixedArc().apply {
                    clockwise = false
                }
            )
        )
    }

    // We keep track of the color of added widgets, to use on touch tests.
    // We should try to avoid using white since it have special meaning.
    var colorProcessor: (Int) -> Int = { color ->
        when (color) {
            Color.WHITE -> 0xFFCCCCCC.toInt()
            else -> color or 0xFF000000.toInt()
        }
    }
    var testColors = mutableListOf<Int>()

    @Before
    fun setup() {
        testColors = mutableListOf(0) // Used when no view got the event
    }

    // Extension functions to make the margin test more readable.
    fun WearArcLayout.addSeparator() {
        addView(
            WearCurvedTextView(ApplicationProvider.getApplicationContext()).apply {
                text = " "
                minSweepDegrees = 10f
                setBackgroundColor(Color.rgb(100, 100, 100))
                clockwise = true
                textSize = 40f
            }
        )
        testColors.add(colorProcessor(Color.rgb(150, 150, 150)))
    }

    fun WearArcLayout.addCurvedText(
        text: String,
        color: Int,
        marginLeft: Int = 0,
        marginTop: Int = 0,
        marginRight: Int = 0,
        marginBottom: Int = 0,
        vAlign: Int = VALIGN_CENTER,
        clockwise: Boolean = true,
        textSize: Float = 14f,
        textAlignment: Int = View.TEXT_ALIGNMENT_TEXT_START,
        minSweep: Float = 0f
    ) {
        addView(
            WearCurvedTextView(ApplicationProvider.getApplicationContext()).also {
                it.text = text
                it.setBackgroundColor(color)
                it.clockwise = clockwise
                it.textSize = textSize
                it.textAlignment = textAlignment
                it.minSweepDegrees = minSweep
                it.layoutParams = WearArcLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(marginLeft, marginTop, marginRight, marginBottom)
                    verticalAlignment = vAlign
                }
            }
        )
        testColors.add(colorProcessor(color))
    }

    fun WearArcLayout.addTextView(
        text: String,
        color: Int,
        textSize: Float = 14f
    ) {
        addView(
            TextView(context).also {
                it.text = text
                it.background = ColorDrawable(color)
                it.textSize = textSize
            }
        )
        testColors.add(colorProcessor(color))
    }

    fun WearArcLayout.addInvisibleTextView() {
        addView(
            TextView(context).also {
                it.text = "Invisible"
                it.visibility = View.INVISIBLE
            }
        )
        testColors.add(0xFF13579B.toInt())
    }

    fun WearArcLayout.addGoneTextView() {
        addView(
            TextView(context).also {
                it.text = "Gone"
                it.visibility = View.GONE
            }
        )
        testColors.add(0xFF13579B.toInt())
    }

    private fun createArcWithMargin() =
        WearArcLayout(ApplicationProvider.getApplicationContext()).apply {
            anchorType = WearArcLayout.ANCHOR_CENTER
            addSeparator()
            addCurvedText("RI", Color.RED, marginTop = 16, vAlign = VALIGN_INNER)
            addCurvedText("GI", Color.GREEN, marginTop = 8, marginBottom = 8, vAlign = VALIGN_INNER)
            addCurvedText("BI", Color.BLUE, marginBottom = 16, vAlign = VALIGN_INNER)
            addSeparator()
            addCurvedText("Red", Color.RED, marginTop = 16)
            addCurvedText("Green", Color.GREEN, marginTop = 8, marginBottom = 8)
            addCurvedText("Blue", Color.BLUE, marginBottom = 16)
            addSeparator()
            addCurvedText("RO", Color.RED, marginTop = 16, vAlign = VALIGN_OUTER)
            addCurvedText("GO", Color.GREEN, marginTop = 8, marginBottom = 8, vAlign = VALIGN_OUTER)
            addCurvedText("BO", Color.BLUE, marginBottom = 16, vAlign = VALIGN_OUTER)
            addSeparator()
            addCurvedText("L", Color.WHITE, marginRight = 20)
            addSeparator()
            addCurvedText("C", Color.WHITE, marginRight = 10, marginLeft = 10)
            addSeparator()
            addCurvedText("R", Color.WHITE, marginLeft = 20)
            addSeparator()
        }

    private fun createTwoArcsWithMargin() = listOf(
        // First arc goes on top
        createArcWithMargin(),

        // Second arc in the bottom, and we change al children to go counterclockwise.
        createArcWithMargin().apply {
            anchorAngleDegrees = 180f
            children.forEach {
                (it as? WearCurvedTextView)?.clockwise = false
            }
        }
    )

    @Test
    fun testMargins() {
        doOneTest(
            "margin_test",
            createTwoArcsWithMargin()
        )
    }

    @Test
    fun testMarginsCcw() {
        doOneTest(
            "margin_ccw_test",
            createTwoArcsWithMargin().map {
                it.apply { clockwise = false }
            }
        )
    }

    @Test
    fun testInvisibleAndGone() {
        doOneTest(
            "inivisible_gone_test",
            listOf(
                WearArcLayout(ApplicationProvider.getApplicationContext()).apply {
                    anchorType = WearArcLayout.ANCHOR_CENTER
                    addCurvedText("Initial", Color.RED, textSize = 30f)
                    addInvisibleTextView()
                    addCurvedText("Second", Color.GREEN, textSize = 30f)
                    addGoneTextView()
                    addCurvedText("Third", Color.BLUE, textSize = 30f)
                    addSeparator()
                    addCurvedText("Initial", Color.RED, textSize = 30f, clockwise = false)
                    addInvisibleTextView()
                    addCurvedText("Second", Color.GREEN, textSize = 30f, clockwise = false)
                    addGoneTextView()
                    addCurvedText("Third", Color.BLUE, textSize = 30f, clockwise = false)
                }
            )
        )
    }

    // Generates a click in the x,y coordinates in the view's coordinate system.
    fun customClick(x: Float, y: Float) = ViewActions.actionWithAssertions(
        GeneralClickAction(
            Tap.SINGLE,
            { view ->
                val xy = IntArray(2)
                view.getLocationOnScreen(xy)
                floatArrayOf(x + xy[0], y + xy[1])
            },
            Press.PINPOINT,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY
        )
    )

    // Sending clicks is slow, around a quarter of a second each, on a desktop emulator.
    @Test(timeout = 100000)
    fun testTouchEvents() {
        val scenario = ActivityScenario.launch(TouchTestActivity::class.java)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widgetWidth = context.resources.getDimension(R.dimen.touch_test_widget_width)
        val widgetHeight = context.resources.getDimension(R.dimen.touch_test_widget_height)

        val STEP = 30

        DrawableSurface.radius = 6f

        // Find the main FrameLayout that contains all widgets under test.
        val theView = Espresso.onView(withId(R.id.curved_frame))
            .perform(
                waitForMatchingView(
                    allOf(
                        withId(R.id.curved_frame),
                        isDisplayed()
                    ),
                    2000
                )
            )

        // Setup on-click handlers for each view so we can get the index of the clicked view.
        var clicked: Int
        scenario.onActivity {
            listOf(
                R.id.curved_text1, R.id.curved_text2, R.id.curved_text3,
                R.id.curved_text4, R.id.text5, R.id.curved_text6
            ).mapIndexed { ix, viewId ->
                it.findViewById<View>(viewId)?.setOnClickListener {
                    clicked = ix
                }
            }
        }

        // Simulate clicks in a grid all over the screen and draw a circle centered in the
        // position of the click and which color indicates the view that got clicked.
        // Black means no view got the click event, white means a out of range value.
        for (y in STEP / 2 until widgetHeight.toInt() step STEP) {
            val points = mutableListOf<ColoredPoint>()
            for (x in STEP / 2 until widgetWidth.toInt() step STEP) {
                // Perform a click, and record a point colored according to which view was clicked.
                clicked = -1
                theView.perform(customClick(x.toFloat(), y.toFloat()))
                points.add(
                    ColoredPoint(
                        x.toFloat(), y.toFloat(),
                        // Color the circle.
                        listOf(
                            Color.BLACK, // no view got the event.
                            Color.RED, Color.GREEN, Color.BLUE,
                            Color.YELLOW, Color.MAGENTA, Color.CYAN
                        ).elementAtOrNull(clicked + 1) ?: Color.WHITE
                    )
                )
            }

            // Add all circles on the current line to the DrawableSurface.
            // Points are batched to improve performance a bit.
            Espresso.onView(withId(R.id.drawable_surface)).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = any(View::class.java)
                override fun getDescription(): String = "Add Points"
                override fun perform(uiController: UiController?, view: View?) {
                    (view as? DrawableSurface)?.addPoints(points)
                }
            })
        }

        // At the end, get a screenshot to compare against the golden
        scenario.onActivity {
            it.findViewById<View>(R.id.curved_frame).draw(canvas)
        }
        bitmap.assertAgainstGolden(screenshotRule, "touch_screenshot")
    }

    // This is not testing the full event journey as the previous method does, but it's faster so
    // we can make more tests, and test more points in them.
    private fun testEventsFast(key: String, testViews: List<View>) {
        val context: Context = ApplicationProvider.getApplicationContext()

        // We setup the "mouse event display" view (on top, with a semi-transparent background)
        // and the views under test.
        val drawableSurface = DrawableSurface(context)
        drawableSurface.background = ColorDrawable(0x40000000.toInt())
        val views = testViews + drawableSurface

        // Setup the click handlers
        var clicked: Int
        var viewNumber = 0

        // We need this function because we want each listener to capture it's view number by value,
        // (and a reference to the clicked variable).
        val onTouchListenerGenerator = { myNumber: Int ->
            { _: View, _: MotionEvent ->
                clicked = myNumber
                true
            }
        }
        views.forEach { view ->
            (view as? WearArcLayout)?.let { arcLayout ->
                arcLayout.forEach { innerView ->
                    if (innerView is TextView || innerView is WearCurvedTextView) {
                        innerView.setOnTouchListener(onTouchListenerGenerator(viewNumber++))
                    }
                }
            }
        }

        // Do the test, sending the events
        var time = 0L
        DrawableSurface.radius = 1.5f
        doOneTest(
            key, views,
            backgroundColor = Color.rgb(0xFF, 0xFF, 0xC0)
        ) {
            val STEP = 4

            // Simulate clicks in a grid all over the screen and draw a circle centered in the
            // position of the click and which color indicates the view that got clicked.
            // Black means no view got the click event, white means a out of range value.
            for (y in STEP / 2 until SCREEN_HEIGHT step STEP) {
                for (x in STEP / 2 until SCREEN_WIDTH step STEP) {
                    // Perform a click, and record a point colored according to which view was clicked.
                    clicked = -1

                    val down_event = MotionEvent.obtain(
                        time, time, MotionEvent.ACTION_DOWN,
                        x.toFloat(), y.toFloat(), 0
                    )
                    dispatchTouchEvent(down_event)

                    val up_event = MotionEvent.obtain(
                        time, time + 5, MotionEvent.ACTION_UP,
                        x.toFloat(), y.toFloat(), 0
                    )
                    dispatchTouchEvent(up_event)

                    time += 10

                    drawableSurface.addPoints(
                        listOf(
                            ColoredPoint(
                                x.toFloat(), y.toFloat(),
                                // Color the circle.
                                // We use Transparent for not touched and white for out of index
                                testColors.elementAtOrNull(clicked + 1) ?: Color.WHITE
                            )
                        )
                    )
                }
            }
        }
    }

    @Test(timeout = 5000)
    fun testBasicTouch() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // This views are the same as the test testTouchEvents()
        val views = listOf(
            WearArcLayout(context).apply {
                anchorAngleDegrees = 0f
                anchorType = WearArcLayout.ANCHOR_CENTER
                clockwise = true
                addCurvedText(
                    "Left", color = 0x66FF0000, textSize = 48f, minSweep = 60f,
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                )
                addGoneTextView()
                addCurvedText(
                    "Center", color = 0x6600FF00, textSize = 48f, minSweep = 60f,
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                )
                addCurvedText(
                    "Right", color = 0x660000FF, textSize = 48f, minSweep = 60f,
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_END
                )
                addGoneTextView()
            },
            WearArcLayout(context).apply {
                anchorAngleDegrees = 180f
                anchorType = WearArcLayout.ANCHOR_CENTER
                clockwise = true
                addGoneTextView()
                addCurvedText(
                    "ACL", color = 0x66FFFF00, textSize = 48f, minSweep = 40f,
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                )
                addTextView(text = "N-TXT", color = 0x66FF00FF, textSize = 20f)
                addCurvedText(
                    "ACR", color = 0x6600FFFF, textSize = 60f, minSweep = 50f,
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_END, clockwise = false
                )
            }
        )
        testEventsFast("touch_fast_screenshot", views)
    }

    @FlakyTest // b/182268136
    @Test(timeout = 5000)
    fun testMarginTouch() {
        val views = createTwoArcsWithMargin()
        testEventsFast("touch_fast_margin_screenshot", views)
    }

    companion object {
        private const val SCREEN_WIDTH = 390
        private const val SCREEN_HEIGHT = 390
        private const val TIMEOUT_MS = 1000L
    }
}

// Helper activity for testing touch.
class TouchTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.wear_arc_layout_touch_test)
    }
}

data class ColoredPoint(val x: Float, val y: Float, val c: Int)

// Helper class to draw some point/circles of different colors. Used by the touch test.
open class DrawableSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var points = mutableListOf<ColoredPoint>()

    override fun onDraw(canvas: Canvas) {
        val paint = Paint().apply {
            strokeWidth = radius / 2f
            style = Paint.Style.STROKE
            alpha = 0
        }
        points.forEach { p ->
            paint.color = p.c
            canvas.drawCircle(p.x, p.y, radius, paint)
        }
    }

    fun addPoints(newPoints: Collection<ColoredPoint>) {
        points.addAll(newPoints)
        invalidate()
    }

    companion object {
        var radius = 6f
    }
}