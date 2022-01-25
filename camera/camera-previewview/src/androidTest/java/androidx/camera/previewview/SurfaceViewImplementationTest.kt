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

package androidx.camera.previewview

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import android.view.View
import android.widget.FrameLayout
import androidx.camera.previewview.utils.CoreAppTestUtil
import androidx.camera.previewview.utils.FakeActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 21)
class SurfaceViewImplementationTest {

    companion object {
        private const val ANY_WIDTH = 640
        private const val ANY_HEIGHT = 480
        private val ANY_SIZE: Size by lazy { Size(ANY_WIDTH, ANY_HEIGHT) }
    }

    private lateinit var mParent: FrameLayout
    private lateinit var mImplementation: SurfaceViewImplementation
    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var mSurfaceRequest: PreviewSurfaceRequest
    private lateinit var mContext: Context
    private lateinit var mDisplay: Display

    // Shows the view in activity so that SurfaceView can work normally
    private lateinit var mActivityScenario: ActivityScenario<FakeActivity>

    @Before
    fun setUp() {
        CoreAppTestUtil.prepareDeviceUI(mInstrumentation)

        mActivityScenario = ActivityScenario.launch(FakeActivity::class.java)
        mContext = ApplicationProvider.getApplicationContext()
        mParent = FrameLayout(mContext)
        setContentView(mParent)

        val displayManager = mContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        mDisplay = displayManager.getDisplay(0)

        mSurfaceRequest = PreviewSurfaceRequest(
            ANY_SIZE,
            mDisplay,
            /*isLegacyDevice=*/true,
            /*isFrontCamera=*/true,
            /*sensorOrientation=*/0)
        mImplementation = SurfaceViewImplementation(mParent, PreviewTransformation())
    }

    @After
    fun tearDown() {
        mSurfaceRequest.previewSurface.close()
    }

    @Test
    fun surfaceProvidedSuccessfully() {
        CoreAppTestUtil.checkKeyguard(mContext)

        mInstrumentation.runOnMainSync {
            mImplementation.onSurfaceRequested(mSurfaceRequest)
        }

        mSurfaceRequest.previewSurface.surface.get(1000, TimeUnit.MILLISECONDS)
        mSurfaceRequest.previewSurface.close()
    }

    @Throws(Throwable::class)
    private fun setContentView(view: View) {
        mActivityScenario.onActivity { activity -> activity.setContentView(view) }
    }
}