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

package androidx.compose.animation.core

import androidx.compose.runtime.dispatch.DesktopUiDispatcher

class DesktopAnimationClock(fps: Int, private val dispatcher: DesktopUiDispatcher) :
    androidx.ui.desktop.BaseAnimationClock() {

    // TODO: detect actual display refresh rate? what to do with displays with
    //  different refresh rates?
    constructor() : this(60, DesktopUiDispatcher.Dispatcher)

    val delay = 1_000 / fps

    @Volatile
    private var scheduled = false

    private fun frameCallback(time: Long) {
        scheduled = false
        dispatchTime(time / 1000000)
    }

    override fun subscribe(observer: AnimationClockObserver) {
        super.subscribe(observer)
        scheduleIfNeeded()
    }

    override fun dispatchTime(frameTimeMillis: Long) {
        super.dispatchTime(frameTimeMillis)
        scheduleIfNeeded()
    }

    private fun scheduleIfNeeded() {
        when {
            scheduled -> return
            !hasObservers() -> return
            else -> {
                scheduled = true
                dispatcher.scheduleCallbackWithDelay(delay, ::frameCallback)
            }
        }
    }
}