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

package androidx.graphics.lowlatency

import android.graphics.Color
import android.opengl.GLES20
import android.os.Build
import android.view.SurfaceControl
import androidx.annotation.RequiresApi
import androidx.graphics.opengl.egl.EglManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class GLWetDryRendererTest {

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    fun testWetLayerRender() {
        val renderLatch = CountDownLatch(1)
        val callbacks = object : GLWetDryRenderer.Callback {
            override fun onDrawWetLayer(eglManager: EglManager, param: Any?) {
                GLES20.glViewport(
                    0, 0, WetDryRendererTestActivity.WIDTH, WetDryRendererTestActivity.HEIGHT)
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }

            override fun onDrawDryLayer(eglManager: EglManager, params: Collection<Any?>) {
                GLES20.glViewport(
                    0, 0, WetDryRendererTestActivity.WIDTH, WetDryRendererTestActivity.HEIGHT)
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }

            override fun onWetLayerRenderComplete(
                wetLayerSurfaceControl: SurfaceControl,
                transaction: SurfaceControl.Transaction
            ) {
                transaction.addTransactionCommittedListener(
                    Executors.newSingleThreadExecutor()
                ) {
                    renderLatch.countDown()
                }
            }
        }
        var renderer: GLWetDryRenderer? = null
        try {
            val scenario = ActivityScenario.launch(WetDryRendererTestActivity::class.java)
                .moveToState(Lifecycle.State.CREATED)
                .onActivity {
                    val surfaceView = it.getSurfaceView()
                    renderer = GLWetDryRenderer(surfaceView, callbacks)
                }

            scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                renderer?.renderWetLayer(Any())
                assertTrue(renderLatch.await(3000, TimeUnit.MILLISECONDS))

                val coords = IntArray(2)
                val width: Int
                val height: Int
                with(it.getSurfaceView()) {
                    getLocationOnScreen(coords)
                    width = this.width
                    height = this.height
                }

                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val bitmap = automation.takeScreenshot()
                assertEquals(
                    Color.RED,
                    bitmap.getPixel(coords[0] + width / 2, coords[1] + height / 2)
                )
            }
        } finally {
            renderer.blockingRelease()
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    fun testDryLayerRender() {
        val renderLatch = CountDownLatch(1)
        val callbacks = object : GLWetDryRenderer.Callback {
            override fun onDrawWetLayer(eglManager: EglManager, param: Any?) {
                GLES20.glViewport(
                    0, 0, WetDryRendererTestActivity.WIDTH, WetDryRendererTestActivity.HEIGHT)
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }

            override fun onDrawDryLayer(eglManager: EglManager, params: Collection<Any?>) {
                GLES20.glViewport(
                    0, 0, WetDryRendererTestActivity.WIDTH, WetDryRendererTestActivity.HEIGHT)
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }

            override fun onDryLayerRenderComplete(
                wetLayerSurfaceControl: SurfaceControl,
                transaction: SurfaceControl.Transaction
            ) {
                transaction.addTransactionCommittedListener(
                    Executors.newSingleThreadExecutor()
                ) {
                    renderLatch.countDown()
                }
            }
        }
        var renderer: GLWetDryRenderer? = null
        try {
            val scenario = ActivityScenario.launch(WetDryRendererTestActivity::class.java)
                .moveToState(Lifecycle.State.CREATED)
                .onActivity {
                    val surfaceView = it.getSurfaceView()
                    renderer = GLWetDryRenderer(surfaceView, callbacks)
                }

            scenario.moveToState(Lifecycle.State.RESUMED).onActivity {
                renderer?.renderWetLayer(Any())
                renderer?.dry()
                assertTrue(renderLatch.await(3000, TimeUnit.MILLISECONDS))

                val coords = IntArray(2)
                val width: Int
                val height: Int
                with(it.getSurfaceView()) {
                    getLocationOnScreen(coords)
                    width = this.width
                    height = this.height
                }

                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val bitmap = automation.takeScreenshot()
                assertEquals(
                    Color.BLUE,
                    bitmap.getPixel(coords[0] + width / 2, coords[1] + height / 2)
                )
            }
        } finally {
            renderer.blockingRelease()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun GLWetDryRenderer?.blockingRelease() {
        if (this != null) {
            val destroyLatch = CountDownLatch(1)
            release(false) {
                destroyLatch.countDown()
            }
            assertTrue(destroyLatch.await(3000, TimeUnit.MILLISECONDS))
        } else {
            fail("GLWetDryRenderer is not initialized")
        }
    }
}