/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.window.embedding

import android.app.Activity
import android.content.Context
import androidx.core.util.Consumer
import androidx.window.embedding.SplitController.Companion
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Controller class that will be used to get information about the currently active activity splits,
 * as well as provide interaction points to customize them and form new splits. A split is a pair of
 * containers that host activities in the same or different tasks, combined under the same parent
 * window of the hosting task.
 * <p>A pair of activities can be put into split by providing a static or runtime split rule and
 * launching activity in the same task and process using [android.content.Context.startActivity].
 * <p>This class should be configured before [android.app.Application.onCreate] for upcoming
 * activity launches using the split rules statically defined in an XML using
 * [androidx.startup.Initializer] and [Companion.initialize]. See Jetpack App Startup reference
 * for more information.
 */
class SplitController private constructor() {
    private val embeddingBackend: EmbeddingBackend = ExtensionEmbeddingBackend.getInstance()
    private var staticSplitRules: Set<EmbeddingRule> = emptySet()

    /**
     * Returns a copy of the currently applied split configurations.
     */
    fun getSplitRules(): Set<EmbeddingRule> {
        return embeddingBackend.getSplitRules()
    }

    /**
     * Registers a new runtime rule, or updates an existing rule if the
     * [tag][EmbeddingRule.tag] has been registered with [SplitController].
     * Will be cleared automatically when the process is stopped.
     *
     * Note that updating the existing rule will **not** be applied to any existing split activity
     * container, and will only be used for new split containers created with future activity
     * launches.
     *
     * @param rule new [EmbeddingRule] to register.
     */
    fun registerRule(rule: EmbeddingRule) {
        embeddingBackend.registerRule(rule)
    }

    /**
     * Unregisters a runtime rule that was previously registered via [SplitController.registerRule].
     *
     * @param rule the previously registered [EmbeddingRule] to unregister.
     */
    fun unregisterRule(rule: EmbeddingRule) {
        embeddingBackend.unregisterRule(rule)
    }

    /**
     * Unregisters all runtime rules added with [registerRule].
     */
    fun clearRegisteredRules() {
        embeddingBackend.setSplitRules(staticSplitRules)
    }

    /**
     * Registers a listener for updates about the active split state(s) that this
     * activity is part of. An activity can be in zero, one or more active splits.
     * More than one active split is possible if an activity created multiple
     * containers to side, stacked on top of each other. Or it can be in two
     * different splits at the same time - in a secondary container for one (it was
     * launched to the side) and in the primary for another (it launched another
     * activity to the side). The reported splits in the list are ordered from
     * bottom to top by their z-order, more recent splits appearing later.
     * Guaranteed to be called at least once to report the most recent state.
     *
     * @param activity only split that this [Activity] is part of will be reported.
     * @param executor when there is an update to the active split state(s), the [consumer] will be
     * invoked on this [Executor].
     * @param consumer [Consumer] that will be invoked on the [executor] when there is an update to
     * the active split state(s).
     */
    fun addSplitListener(
        activity: Activity,
        executor: Executor,
        consumer: Consumer<List<SplitInfo>>
    ) {
        embeddingBackend.registerSplitListenerForActivity(activity, executor, consumer)
    }

    /**
     * Unregisters a runtime rule that was previously registered via [addSplitListener].
     *
     * @param consumer the previously registered [Consumer] to unregister.
     */
    fun removeSplitListener(
        consumer: Consumer<List<SplitInfo>>
    ) {
        embeddingBackend.unregisterSplitListenerForActivity(consumer)
    }

    /**
     * Indicates whether the split functionality is supported on the device. Note
     * that the device might enable splits in all conditions, but it should be
     * available in some states that the device supports. An example can be a
     * foldable device with multiple screens can choose to collapse all splits for
     * apps running on a small display, but enable when running on a larger
     * one - on such devices this method will always return "true".
     * If the split is not supported, activities will be launched on top, following
     * the regular model.
     */
    fun isSplitSupported(): Boolean {
        return embeddingBackend.isSplitSupported()
    }

    private fun setStaticSplitRules(staticRules: Set<EmbeddingRule>) {
        staticSplitRules = staticRules
        embeddingBackend.setSplitRules(staticRules)
    }

    /**
     * Checks if an activity is embedded and its presentation may be customized by its or any other
     * process.
     *
     * @param activity the [Activity] to check.
     */
    // TODO(b/204399167) Migrate to a Flow
    fun isActivityEmbedded(activity: Activity): Boolean {
        return embeddingBackend.isActivityEmbedded(activity)
    }

    /**
     * Sets or updates the previously registered [SplitAttributesCalculator].
     *
     * **Note** that if the [SplitAttributesCalculator] is replaced, the existing split pairs will
     * be updated after there's a window or device state change.
     * The caller **must** make sure [isSplitAttributesCalculatorSupported] before invoking.
     *
     * @param calculator the calculator to set. It will replace the previously set
     * [SplitAttributesCalculator] if it exists.
     * @throws UnsupportedOperationException if [isSplitAttributesCalculatorSupported] reports
     * `false`
     */
    fun setSplitAttributesCalculator(calculator: SplitAttributesCalculator) {
        embeddingBackend.setSplitAttributesCalculator(calculator)
    }

    /**
     * Clears the previously set [SplitAttributesCalculator].
     * The caller **must** make sure [isSplitAttributesCalculatorSupported] before invoking.
     *
     * @see setSplitAttributesCalculator
     * @throws UnsupportedOperationException if [isSplitAttributesCalculatorSupported] reports
     * `false`
     */
    fun clearSplitAttributesCalculator() {
        embeddingBackend.clearSplitAttributesCalculator()
    }

    /** Returns whether [SplitAttributesCalculator] is supported or not. */
    fun isSplitAttributesCalculatorSupported(): Boolean =
        embeddingBackend.isSplitAttributesCalculatorSupported()

    companion object {
        @Volatile
        private var globalInstance: SplitController? = null
        private val globalLock = ReentrantLock()

        internal const val sDebug = false

        /**
         * Gets the shared instance of the class.
         */
        @JvmStatic
        fun getInstance(): SplitController {
            if (globalInstance == null) {
                globalLock.withLock {
                    if (globalInstance == null) {
                        globalInstance = SplitController()
                    }
                }
            }
            return globalInstance!!
        }

        /**
         * Initializes the shared class instance with the split rules statically defined in an
         * app-provided XML. The rules will be kept for the lifetime of the application process.
         * <p>It's recommended to set the static rules via an [androidx.startup.Initializer], or
         * [android.app.Application.onCreate], so that they are applied early in the application
         * startup before any activities appear.
         * <p>Note that it is not necessary to call this function in order to use [SplitController].
         * If the app doesn't have any static rule, it can use [registerRule] to register rules at
         * any time.
         *
         * @param context the context to read the split resource from.
         * @param staticRuleResourceId the resource containing the static split rules.
         * @throws IllegalArgumentException if any of the rules in the XML is malformed or if
         * there's a duplicated [tag][EmbeddingRule.tag].
         */
        @JvmStatic
        fun initialize(context: Context, staticRuleResourceId: Int) {
            val parser = SplitRuleParser()
            val configs = parser.parseSplitRules(context, staticRuleResourceId)
            val controllerInstance = getInstance()
            controllerInstance.setStaticSplitRules(configs ?: emptySet())
        }
    }
}