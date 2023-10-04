/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.graphics.lowlatency

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.graphics.surface.SurfaceControlCompat
import androidx.graphics.surface.SurfaceControlUtils
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LowLatencyCanvasViewTest {

    private val executor = Executors.newSingleThreadExecutor()

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testFrontBufferRender() {
        val frontBufferRenderLatch = CountDownLatch(1)
        lowLatencyViewTest(
            renderCallbacks = object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                    // NO-OP
                }

                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.BLUE)
                }

                override fun onFrontBufferedLayerRenderComplete(
                    frontBufferedLayerSurfaceControl: SurfaceControlCompat,
                    transaction: SurfaceControlCompat.Transaction
                ) {
                    frontBufferRenderLatch.countDown()
                }
            },
            scenarioCallback = { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                    it.getLowLatencyCanvasView().renderFrontBufferedLayer()
                }
                assertTrue(frontBufferRenderLatch.await(3000, TimeUnit.MILLISECONDS))
            },
            validateBitmap = { bitmap, left, top, right, bottom ->
                Color.BLUE == bitmap.getPixel(
                    left + (right - left) / 2,
                    top + (bottom - top) / 2)
            }
        )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testRedrawScene() {
        lowLatencyViewTest(
            renderCallbacks = object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.RED)
                }

                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                    // NO-OP
                }
            },
            scenarioCallback = { scenario ->
                val drawLatch = CountDownLatch(1)
                scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                    it.getLowLatencyCanvasView().post {
                        drawLatch.countDown()
                    }
                }
                drawLatch.await(3000, TimeUnit.MILLISECONDS)
            },
            validateBitmap = { bitmap, left, top, right, bottom ->
                Color.RED == bitmap.getPixel(left + (right - left) / 2, top + (bottom - top) / 2)
            }
        )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testClear() {
        lowLatencyViewTest(
            renderCallbacks = object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.RED)
                }

                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                    // NO-OP
                }
            },
            scenarioCallback = { scenario ->
                val drawLatch = CountDownLatch(1)
                scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                    val view = it.getLowLatencyCanvasView()
                    view.clear()
                    view.post {
                        drawLatch.countDown()
                    }
                }
                assertTrue(drawLatch.await(3000, TimeUnit.MILLISECONDS))
            },
            validateBitmap = { bitmap, left, top, right, bottom ->
                Color.WHITE == bitmap.getPixel(left + (right - left) / 2, top + (bottom - top) / 2)
            }
        )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testCancel() {
        val cancelLatch = CountDownLatch(1)
        val frontBufferRenderLatch = CountDownLatch(1)
        lowLatencyViewTest(
            renderCallbacks = object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.RED)
                }

                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.BLUE)
                    frontBufferRenderLatch.countDown()
                }
            },
            scenarioCallback = { scenario ->
                val renderLatch = CountDownLatch(1)
                var lowLatencyView: LowLatencyCanvasView? = null
                scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                    lowLatencyView = it.getLowLatencyCanvasView()
                    lowLatencyView!!.post { renderLatch.countDown() }
                }
                assertTrue(renderLatch.await(3000, TimeUnit.MILLISECONDS))

                lowLatencyView!!.execute {
                    cancelLatch.await()
                }

                repeat(3) {
                    lowLatencyView!!.renderFrontBufferedLayer()
                }

                lowLatencyView!!.cancel()
                cancelLatch.countDown()

                assertFalse(frontBufferRenderLatch.await(1000, TimeUnit.MILLISECONDS))
            },
            validateBitmap = { bitmap, left, top, right, bottom ->
                Color.RED == bitmap.getPixel(
                    left + (right - left) / 2,
                    top + (bottom - top) / 2)
            }
        )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    @Test
    fun testConfigureFrontBufferTransaction() {
        val renderFrontBufferLatch = CountDownLatch(1)
        lowLatencyViewTest(
            renderCallbacks = object : LowLatencyCanvasView.Callback {
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.RED)
                }

                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                    canvas.drawColor(Color.BLUE)
                }

                override fun onFrontBufferedLayerRenderComplete(
                    frontBufferedLayerSurfaceControl: SurfaceControlCompat,
                    transaction: SurfaceControlCompat.Transaction
                ) {
                    transaction.setAlpha(frontBufferedLayerSurfaceControl, 0f)
                    renderFrontBufferLatch.countDown()
                }
            },
            scenarioCallback = { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                    it.getLowLatencyCanvasView().renderFrontBufferedLayer()
                }
                assertTrue(renderFrontBufferLatch.await(3000, TimeUnit.MILLISECONDS))
            },
            validateBitmap = { bitmap, left, top, right, bottom ->
                Color.WHITE == bitmap.getPixel(left + (right - left) / 2, top + (bottom - top) / 2)
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun lowLatencyViewTest(
        renderCallbacks: LowLatencyCanvasView.Callback,
        scenarioCallback: (ActivityScenario<LowLatencyActivity>) -> Unit,
        validateBitmap: (Bitmap, Int, Int, Int, Int) -> Boolean
    ) {
        val renderLatch = CountDownLatch(1)
        val callbacks = object : LowLatencyCanvasView.Callback {
            override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                renderCallbacks.onRedrawRequested(canvas, width, height)
            }

            override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                renderCallbacks.onDrawFrontBufferedLayer(canvas, width, height)
            }

            override fun onFrontBufferedLayerRenderComplete(
                frontBufferedLayerSurfaceControl: SurfaceControlCompat,
                transaction: SurfaceControlCompat.Transaction
            ) {
                renderCallbacks.onFrontBufferedLayerRenderComplete(
                    frontBufferedLayerSurfaceControl,
                    transaction
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    transaction.addTransactionCommittedListener(
                        executor,
                        object : SurfaceControlCompat.TransactionCommittedListener {
                            override fun onTransactionCommitted() {
                                renderLatch.countDown()
                            }
                        }
                    )
                } else {
                    renderLatch.countDown()
                }
            }
        }
        val destroyLatch = CountDownLatch(1)
        var lowLatencyView: LowLatencyCanvasView? = null
        val scenario = ActivityScenario.launch(LowLatencyActivity::class.java)
            .moveToState(Lifecycle.State.CREATED)
            .onActivity { activity ->
                with(activity) {
                    setOnDestroyCallback {
                        destroyLatch.countDown()
                    }
                }
                lowLatencyView = activity.getLowLatencyCanvasView().apply {
                    setRenderCallback(callbacks)
                }
            }
        scenarioCallback(scenario)

        val coords = IntArray(2)
        val width: Int
        val height: Int
        with(lowLatencyView!!) {
            getLocationOnScreen(coords)
            width = this.width
            height = this.height
        }

        try {
            SurfaceControlUtils.validateOutput { bitmap ->
                val left = coords[0]
                val top = coords[1]
                val right = coords[0] + width
                val bottom = coords[1] + height
                validateBitmap(bitmap, left, top, right, bottom)
            }
        } finally {
            scenario.moveToState(Lifecycle.State.DESTROYED)
            assertTrue(destroyLatch.await(3000, TimeUnit.MILLISECONDS))
        }
    }
}
