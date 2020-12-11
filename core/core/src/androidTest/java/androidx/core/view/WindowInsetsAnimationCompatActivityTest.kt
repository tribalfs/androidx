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

package androidx.core.view

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.test.R
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.testutils.withActivity
import com.google.common.truth.Truth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SdkSuppress(minSdkVersion = 21)
@RequiresApi(21)
@LargeTest
public class WindowInsetsAnimationCompatActivityTest {

    private lateinit var scenario: ActivityScenario<WindowInsetsCompatActivity>

    @Before
    public fun setup() {
        scenario = ActivityScenario.launch(WindowInsetsCompatActivity::class.java)
        onIdle()
        // Close the IME if it's open, so we start from a known scenario
        onView(withId(R.id.edittext)).perform(ViewActions.closeSoftKeyboard())
        scenario.onActivity {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            WindowCompat.getInsetsController(it.window, it.window.decorView)!!.show(systemBars())
        }
        onIdle()
    }

    @Test
    public fun add_remove_listener() {
        assumeNotCuttlefish()

        val container = scenario.withActivity { findViewById(R.id.container) }
        var applyInsetsCalled = false
        var insetsAnimationCallbackCalled = false
        var latch = CountDownLatch(2)
        val animationCallback = createCallback(
            onPrepare = {
                insetsAnimationCallbackCalled = true
                latch.countDown()
            }
        )
        val insetListener: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat =
            { _, insetsCompat ->
                applyInsetsCalled = true
                latch.countDown()
                insetsCompat
            }

        // Check that both ApplyWindowInsets and the Animation Callback are called
        ViewCompat.setOnApplyWindowInsetsListener(container, insetListener)
        ViewCompat.setWindowInsetsAnimationCallback(container, animationCallback)
        triggerInsetAnimation(container)
        latch.await(4, TimeUnit.SECONDS)
        assertTrue(
            "The WindowInsetsAnimationCallback has not been called",
            insetsAnimationCallbackCalled
        )
        assertTrue(
            "onApplyWindowInsetsListener has not been called",
            applyInsetsCalled
        )
        resetBars(container)

        // Remove the applyWindowInsets listener and check that the animation callback is still
        // called
        applyInsetsCalled = false
        insetsAnimationCallbackCalled = false
        latch = CountDownLatch(1)
        ViewCompat.setOnApplyWindowInsetsListener(container, null)
        triggerInsetAnimation(container)
        latch.await(4, TimeUnit.SECONDS)
        assertFalse(
            "onApplyWindowInsetsListener should NOT have been called",
            applyInsetsCalled
        )
        assertTrue(
            "The WindowInsetsAnimationCallback has not been called",
            insetsAnimationCallbackCalled
        )

        resetBars(container)

        // Add an applyWindowInsets listener and remove the animation callback and check if the
        // listener is called
        applyInsetsCalled = false
        insetsAnimationCallbackCalled = false
        latch = CountDownLatch(1)
        ViewCompat.setOnApplyWindowInsetsListener(container, insetListener)
        ViewCompat.setWindowInsetsAnimationCallback(container, null)
        triggerInsetAnimation(container)
        latch.await(4, TimeUnit.SECONDS)
        assertTrue("onApplyWindowInsetsListener has not been called", applyInsetsCalled)
        assertFalse(
            "The WindowInsetsAnimationCallback should NOT have been called",
            insetsAnimationCallbackCalled
        )
    }

    @Test
    public fun all_callbacks_called() {
        assumeNotCuttlefish()

        val container = scenario.withActivity { findViewById(R.id.container) }

        val res = mutableSetOf<String>()
        val progress = mutableListOf<Float>()
        val latch = CountDownLatch(3)
        ViewCompat.setWindowInsetsAnimationCallback(
            container,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    res.add("prepare")
                    latch.countDown()
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    res.add("progress")
                    progress.add(runningAnimations[0].fraction)
                    return insets
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.Bounds
                ): WindowInsetsAnimationCompat.Bounds {
                    res.add("start")
                    latch.countDown()
                    return bounds
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    res.add("end")
                    latch.countDown()
                }
            }
        )
        triggerInsetAnimation(container)
        latch.await(5, TimeUnit.SECONDS)
        Truth.assertThat(res).containsExactly("prepare", "start", "progress", "end").inOrder()
        Truth.assertThat(progress).containsAtLeast(0.0f, 1.0f)
        Truth.assertThat(progress).isInOrder()
    }

    private fun triggerInsetAnimation(container: View) {
        scenario.onActivity {
            ViewCompat.getWindowInsetsController(container)!!.hide(systemBars())
        }
    }

    private fun resetBars(container: View) {
        scenario.onActivity {
            ViewCompat.getWindowInsetsController(container)!!.show(systemBars())
        }
    }

    @Test
    public fun update_apply_listener() {
        val container = scenario.withActivity { findViewById(R.id.container) }
        var applyInsetsCalled1 = false
        var applyInsetsCalled2 = false
        var applyInsetsCalled3 = false
        var insetsAnimationCallbackCalled1 = false
        var insetsAnimationCallbackCalled2 = false
        val latch = CountDownLatch(2)
        val animationCallback1 = createCallback(
            onPrepare = {
                insetsAnimationCallbackCalled1 = true
            }
        )
        val animationCallback2 = createCallback(
            onPrepare = {
                insetsAnimationCallbackCalled2 = true
                latch.countDown()
            }
        )
        val insetListener1: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat =
            { _, insetsCompat ->
                applyInsetsCalled1 = true
                insetsCompat
            }

        val insetListener2: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat =
            { _, insetsCompat ->
                applyInsetsCalled2 = true
                insetsCompat
            }

        val insetListener3: (v: View, insets: WindowInsetsCompat) -> WindowInsetsCompat =
            { _, insetsCompat ->
                applyInsetsCalled3 = true
                latch.countDown()
                insetsCompat
            }

        // Check that both ApplyWindowInsets and the Animation Callback are called
        ViewCompat.setOnApplyWindowInsetsListener(container, insetListener1)
        ViewCompat.setWindowInsetsAnimationCallback(container, animationCallback1)
        ViewCompat.setOnApplyWindowInsetsListener(container, insetListener2)
        ViewCompat.setWindowInsetsAnimationCallback(container, animationCallback2)
        ViewCompat.setOnApplyWindowInsetsListener(container, insetListener3)
        triggerInsetAnimation(container)
        latch.await(5, TimeUnit.SECONDS)
        assertFalse(
            "The WindowInsetsAnimationCallback #1 should have not been called",
            insetsAnimationCallbackCalled1
        )
        assertFalse(
            "The onApplyWindowInsetsListener #1 should have not been called",
            applyInsetsCalled1
        )
        assertTrue(
            "The WindowInsetsAnimationCallback #2 has not been called",
            insetsAnimationCallbackCalled2
        )
        assertFalse(
            "onApplyWindowInsetsListener #2 should not have been called",
            applyInsetsCalled2
        )
        assertTrue(
            "onApplyWindowInsetsListener #3 has not been called",
            applyInsetsCalled3
        )
    }

    private fun createCallback(
        onPrepare: ((WindowInsetsAnimationCompat) -> Unit)? = null

    ): WindowInsetsAnimationCompat.Callback {
        return object :
            WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                onPrepare?.invoke(animation)
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat = insets
        }
    }

    private fun assumeNotCuttlefish() {
        // TODO: remove this if b/159103848 is resolved
        Assume.assumeFalse(
            "Unable to test: Cuttlefish devices default to the virtual keyboard being disabled.",
            Build.MODEL.contains("Cuttlefish", ignoreCase = true)
        )
    }
}