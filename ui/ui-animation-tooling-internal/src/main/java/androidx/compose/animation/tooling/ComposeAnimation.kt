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

package androidx.compose.animation.tooling

/**
 * Type of the animation. Different types might have different properties, for example a
 * `TransitionAnimation` (represented by [TRANSITION_ANIMATION]) has a set of states associated
 * with it.
 */
enum class ComposeAnimationType {
    TRANSITION_ANIMATION, ANIMATED_VALUE
}

/**
 * Simple interface to make it easier to share Compose animation objects between `ui-tooling` and
 * Android Studio. Since both ends communicate mostly using bytecode manipulation and reflection,
 * being able to parse these objects into a common type makes
 */
interface ComposeAnimation {

    /**
     * Returns the animation type. Ideally, the type should be checked before calling methods
     * specific to a certain type, e.g. [getStates].
     */
    fun getType(): ComposeAnimationType

    /**
     * Returns the actual animation object.
     */
    fun getAnimation(): Any

    /**
     * Returns all the available states of a `TransitionAnimation`.
     *
     * @throws UnsupportedOperationException if [getType] does not return `TRANSITION_ANIMATION`.
     */
    fun getStates(): Set<Any> {
        throw UnsupportedOperationException("Only available when getType() is TRANSITION_ANIMATION")
    }

    /**
     * Returns the animation label, which can be used to represent it as text in Android Studio.
     * Null if the label is not set or if it can't be inferred from the animation states.
     */
    fun getLabel(): String?
}
