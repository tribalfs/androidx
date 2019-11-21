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

package androidx.animation

/**
 * This interface allows AnimationClock to be subscribed and unsubscribed.
 */
// TODO: This is a temporary design for Animation Clock. In long term, we may use a Flow<Long>
// or some other data structure that takes care of the subscribing and unsubscribing
// implementation out of the box.
interface AnimationClockObservable {
    /**
     * Subscribes an observer to the animation clock source.
     *
     * @param observer The observer that will be notified when animation clock time is updated.
     */
    fun subscribe(observer: AnimationClockObserver)

    /**
     * Unsubscribes an observer from the animation clock.
     *
     * @param observer The observer to be removed from the subscription list.
     */
    fun unsubscribe(observer: AnimationClockObserver)
}

/**
 * Observer for animation clock changes. The observers will be notified via [onAnimationFrame] when
 * the frame time has been updated in animation clock
 */
interface AnimationClockObserver {
    /**
     * This gets called when animation clock ticks.
     *
     * @param frameTimeMillis The frame time of the new tick in milliseconds
     */
    fun onAnimationFrame(frameTimeMillis: Long)
}
