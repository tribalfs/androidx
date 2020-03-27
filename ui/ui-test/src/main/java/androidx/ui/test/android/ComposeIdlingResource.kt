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

package androidx.ui.test.android

import android.os.Handler
import android.os.Looper
import androidx.compose.Recomposer
import androidx.compose.frames.currentFrame
import androidx.compose.frames.inFrame
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.ui.test.TestAnimationClock

/**
 * Register compose's idling check to Espresso.
 *
 * This makes sure that Espresso is able to wait for any pending changes in Compose. This
 * resource is automatically registered when any compose testing APIs are used including
 * [AndroidComposeTestRule]. If you for some reasons want to only use Espresso but still have it
 * wait for Compose being idle you can use this function.
 */
fun registerComposeWithEspresso() {
    ComposeIdlingResource.registerSelfIntoEspresso()
}

/**
 * Unregisters resource registered as part of [registerComposeWithEspresso].
 */
fun unregisterComposeFromEspresso() {
    ComposeIdlingResource.unregisterSelfFromEspresso()
}

/**
 * Registers the given [clock] so Espresso can await the animations subscribed to that clock.
 */
fun registerTestClock(clock: TestAnimationClock) {
    ComposeIdlingResource.registerTestClock(clock)
}

/**
 * Unregisters the [clock] that was registered with [registerTestClock].
 */
fun unregisterTestClock(clock: TestAnimationClock) {
    ComposeIdlingResource.unregisterTestClock(clock)
}

/**
 * Provides an idle check to be registered into Espresso.
 *
 * This makes sure that Espresso is able to wait for any pending changes in Compose. This
 * resource is automatically registered when any compose testing APIs are used including
 * [AndroidComposeTestRule]. If you for some reasons want to only use Espresso but still have it
 * wait for Compose being idle you can register this yourself via [registerSelfIntoEspresso].
 */
internal object ComposeIdlingResource : IdlingResource {

    override fun getName(): String = "ComposeIdlingResource"

    private var callback: IdlingResource.ResourceCallback? = null

    private var isRegistered = false

    private var isIdleCheckScheduled = false

    private val clocks = mutableSetOf<TestAnimationClock>()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Returns whether or not Compose is idle, without starting to poll if it is not.
     */
    fun isIdle(): Boolean {
        return handler.runAndAwait {
            !(inFrame && currentFrame().hasPendingChanges()) &&
                    !Recomposer.hasPendingChanges() &&
                    areAllClocksIdle()
        }
    }

    /**
     * Returns whether or not Compose is idle, and starts polling if it is not. Will always be
     * called from the main thread by Espresso, and should _only_ be called from Espresso. Use
     * [isIdle] if you need to query the idleness of Compose manually.
     */
    override fun isIdleNow(): Boolean {
        val isIdle = isIdle()
        if (!isIdle) {
            scheduleIdleCheck()
        }
        return isIdle
    }

    private fun scheduleIdleCheck() {
        if (!isIdleCheckScheduled) {
            isIdleCheckScheduled = true
            handler.post {
                isIdleCheckScheduled = false
                if (isIdle()) {
                    if (callback != null) {
                        callback!!.onTransitionToIdle()
                    }
                } else {
                    scheduleIdleCheck()
                }
            }
        }
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }

    /**
     * Registers this resource into Espresso.
     *
     * Can be called multiple times.
     */
    internal fun registerSelfIntoEspresso() {
        if (isRegistered) {
            return
        }
        IdlingRegistry.getInstance().register(ComposeIdlingResource)
        isRegistered = true
    }

    /**
     * Unregisters this resource from Espresso.
     *
     * Can be called multiple times.
     */
    internal fun unregisterSelfFromEspresso() {
        if (!isRegistered) {
            return
        }
        IdlingRegistry.getInstance().unregister(ComposeIdlingResource)
        isRegistered = false
    }

    internal fun registerTestClock(clock: TestAnimationClock) {
        synchronized(clocks) {
            clocks.add(clock)
        }
    }

    internal fun unregisterTestClock(clock: TestAnimationClock) {
        synchronized(clocks) {
            clocks.remove(clock)
        }
    }

    private fun areAllClocksIdle(): Boolean {
        return synchronized(clocks) {
            clocks.all { it.isIdle }
        }
    }
}
