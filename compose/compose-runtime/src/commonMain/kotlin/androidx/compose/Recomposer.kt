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
        @Deprecated(
            "Use the Recomposer instance fun instead",
            ReplaceWith(
                "Recomposer.current().hasPendingChanges()",
                "androidx.compose.Recomposer"
            )
        )
        fun hasPendingChanges() = current().hasPendingChanges()

        /**
         * Retrieves [Recomposer] for the current thread. Needs to be the main thread.
         */
        @TestOnly
        fun current(): Recomposer {
            require(isMainThread()) {
                "No Recomposer for this Thread"
            }
            return threadRecomposer.get()
        }

        private val threadRecomposer = ThreadLocal { createRecomposer() }
    }

    private val composers = mutableSetOf<Composer<*>>()

    @Suppress("PLUGIN_WARNING", "PLUGIN_ERROR")
    internal fun recompose(composable: @Composable () -> Unit, composer: Composer<*>) {
        val composerWasComposing = composer.isComposing
        val prevComposer = currentComposerInternal
        try {
            try {
                composer.isComposing = true
                currentComposerInternal = composer
                FrameManager.composing {
                    trace("Compose:recompose") {
                        var complete = false
                        try {
                            composer.startRoot()
                            composer.startGroup(invocation)
                            invokeComposable(composer, composable)
                            composer.endGroup()
                            composer.endRoot()
                            complete = true
                        } finally {
                            if (!complete) composer.abortRoot()
                        }
                    }
                }
            } finally {
                composer.isComposing = composerWasComposing
            }
            // TODO(b/143755743)
            if (!composerWasComposing) {
                FrameManager.nextFrame()
            }
            composer.applyChanges()
            if (!composerWasComposing) {
                FrameManager.nextFrame()
            }
        } finally {
            currentComposerInternal = prevComposer
        }
    }

    private fun performRecompose(composer: Composer<*>): Boolean {
        if (composer.isComposing) return false
        val prevComposer = currentComposerInternal
        val hadChanges: Boolean
        try {
            currentComposerInternal = composer
            composer.isComposing = true
            hadChanges = FrameManager.composing {
                composer.recompose()
            }
            composer.applyChanges()
        } finally {
            composer.isComposing = false
            currentComposerInternal = prevComposer
        }
        return hadChanges
    }

    abstract fun hasPendingChanges(): Boolean

    internal fun hasInvalidations() = composers.toTypedArray().any { it.hasInvalidations() }

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

        // Ensure any committed frames in other threads are visible.
        FrameManager.nextFrame()

        cs.forEach { performRecompose(it) }

        // Ensure any changes made during composition are now visible to other threads.
        FrameManager.nextFrame()
    }

    /**
     * Used to recompose changes from [scheduleChangesDispatch] immediately without waiting.
     *
     * This is supposed to be used in tests only.
     */
    @TestOnly
    abstract fun recomposeSync()
}