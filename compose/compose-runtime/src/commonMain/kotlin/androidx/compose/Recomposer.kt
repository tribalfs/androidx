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

package androidx.compose

abstract class Recomposer {

    companion object {

        /**
         * Check if there's pending changes to be recomposed in this thread
         *
         * @return true if there're pending changes in this thread, false otherwise
         */
        fun hasPendingChanges() = current().hasPendingChanges()

        internal fun current(): Recomposer {
            require(isMainThread()) {
                "No Recomposer for this Thread"
            }
            return threadRecomposer.get() ?: error("No Recomposer for this Thread")
        }

        internal fun recompose(component: Component, composer: Composer<*>) =
            current().recompose(component, composer)

        // TODO delete the explicit type after https://youtrack.jetbrains.com/issue/KT-20996
        private val threadRecomposer: ThreadLocal<Recomposer> = object : ThreadLocal<Recomposer>() {
            override fun initialValue(): Recomposer? = createRecomposer()
        }
    }

    private val composers = mutableSetOf<Composer<*>>()

    @Suppress("PLUGIN_WARNING")
    private fun recompose(component: Component, composer: Composer<*>) {
        composer.runWithCurrent {
            val composerWasComposing = composer.isComposing
            try {
                composer.isComposing = true
                trace("Compose:recompose") {
                    composer.startRoot()
                    composer.startGroup(invocation)
                    component()
                    composer.endGroup()
                    composer.endRoot()
                }
                composer.applyChanges()
                FrameManager.nextFrame()
            } finally {
                composer.isComposing = composerWasComposing
            }
        }
    }

    private fun performRecompose(composer: Composer<*>): Boolean {
        if (composer.isComposing) return false
        return composer.runWithCurrent {
            var hadChanges: Boolean
            try {
                composer.isComposing = true
                hadChanges = composer.recompose()
                composer.applyChanges()
            } finally {
                composer.isComposing = false
            }
            hadChanges
        }
    }

    internal abstract fun hasPendingChanges(): Boolean

    internal fun scheduleRecompose(composer: Composer<*>) {
        composers.add(composer)
        scheduleChangesDispatch()
    }

    internal fun recomposeSync(composer: Composer<*>): Boolean {
        return performRecompose(composer)
    }

    protected abstract fun scheduleChangesDispatch()

    protected fun dispatchRecomposes() {
        val cs = composers.toTypedArray()
        composers.clear()
        cs.forEach { performRecompose(it) }
        FrameManager.nextFrame()
    }
}

internal expect fun createRecomposer(): Recomposer