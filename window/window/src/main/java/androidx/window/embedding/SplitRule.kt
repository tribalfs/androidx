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

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.LayoutDirection.LOCALE
import android.util.LayoutDirection.LTR
import android.util.LayoutDirection.RTL
import android.view.WindowMetrics
import androidx.annotation.DoNotInline
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.window.embedding.SplitRule.Companion.DEFAULT_SPLIT_MIN_DIMENSION_DP
import kotlin.math.min

/**
 * Split configuration rules for activities that are launched to side in a split.
 * Define the visual properties of the split. Can be set either via [RuleController.setRules] or
 * via [RuleController.addRule]. The rules are always applied only to activities that will be
 * started after the rules were set.
 *
 * @see androidx.window.embedding.SplitPairRule
 * @see androidx.window.embedding.SplitPlaceholderRule
 */
open class SplitRule internal constructor(
    /**
     * The smallest value of width of the parent window when the split should be used, in DP.
     * When the window size is smaller than requested here, activities in the secondary container
     * will be stacked on top of the activities in the primary one, completely overlapping them.
     *
     * The default is [DEFAULT_SPLIT_MIN_DIMENSION_DP] if the app doesn't set.
     * `0` means to always allow split.
     */
    @IntRange(from = 0)
    val minWidthDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP,

    /**
     * The smallest value of the smallest possible width of the parent window in any rotation
     * when the split should be used, in DP. When the window size is smaller than requested
     * here, activities in the secondary container will be stacked on top of the activities in
     * the primary one, completely overlapping them.
     *
     * The default is [DEFAULT_SPLIT_MIN_DIMENSION_DP] if the app doesn't set.
     * `0` means to always allow split.
     */
    @IntRange(from = 0)
    val minSmallestWidthDp: Int,

    /**
     * Defines what part of the width should be given to the primary activity. Defaults to an
     * equal width split.
     */
    @FloatRange(from = 0.0, to = 1.0)
    val splitRatio: Float = 0.5f,

    /**
     * The layout direction for the split. The value must be one of [LTR], [RTL] or [LOCALE].
     * - [LTR]: It splits the task bounds vertically, and put the primary container on the left
     *   portion, and the secondary container on the right portion.
     * - [RTL]: It splits the task bounds vertically, and put the primary container on the right
     *   portion, and the secondary container on the left portion.
     * - [LOCALE]: It splits the task bounds vertically, and the direction is deduced from the
     *   default language script of locale. The direction can be either [LTR] or [RTL].
     */
    @LayoutDirection
    val layoutDirection: Int = LOCALE
) : EmbeddingRule() {

    @IntDef(LTR, RTL, LOCALE)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class LayoutDirection

    /**
     * Determines what happens with the associated container when all activities are finished in
     * one of the containers in a split.
     *
     * For example, given that [SplitPairRule.finishPrimaryWithSecondary] is [FINISH_ADJACENT] and
     * secondary container finishes. The primary associated container is finished if it's
     * side-by-side with secondary container. The primary associated container is not finished
     * if it occupies entire task bounds.
     *
     * @see SplitPairRule.finishPrimaryWithSecondary
     * @see SplitPairRule.finishSecondaryWithPrimary
     * @see SplitPlaceholderRule.finishPrimaryWithPlaceholder
     */
    companion object {
        /**
         * Never finish the associated container.
         * @see SplitRule.Companion
         */
        const val FINISH_NEVER = 0
        /**
         * Always finish the associated container independent of the current presentation mode.
         * @see SplitRule.Companion
         */
        const val FINISH_ALWAYS = 1
        /**
         * Only finish the associated container when displayed side-by-side/adjacent to the one
         * being finished. Does not finish the associated one when containers are stacked on top of
         * each other.
         * @see SplitRule.Companion
         */
        const val FINISH_ADJACENT = 2
        /**
         * The default min dimension in DP for allowing split if it is not set by apps. The value
         * reflects [androidx.window.core.layout.WindowWidthSizeClass.MEDIUM].
         */
        const val DEFAULT_SPLIT_MIN_DIMENSION_DP = 600
    }

    /**
     * Defines whether an associated container should be finished together with the one that's
     * already being finished based on their current presentation mode.
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(FINISH_NEVER, FINISH_ALWAYS, FINISH_ADJACENT)
    internal annotation class SplitFinishBehavior

    /**
     * Verifies if the provided parent bounds are large enough to apply the rule.
     */
    internal fun checkParentMetrics(context: Context, parentMetrics: WindowMetrics): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return false
        }
        val bounds = Api30Impl.getBounds(parentMetrics)
        // TODO(b/257000820): Application displayMetrics should only be used as a fallback. Replace
        // with Task density after we include it in WindowMetrics.
        val density = context.resources.displayMetrics.density
        return checkParentBounds(density, bounds)
    }

    /**
     * @see checkParentMetrics
     */
    internal fun checkParentBounds(density: Float, bounds: Rect): Boolean {
        val minWidthPx = convertDpToPx(density, minWidthDp)
        val minSmallestWidthPx = convertDpToPx(density, minSmallestWidthDp)
        val validMinWidth = (minWidthDp == 0 || bounds.width() >= minWidthPx)
        val validSmallestMinWidth = (
            minSmallestWidthDp == 0 ||
                min(bounds.width(), bounds.height()) >= minSmallestWidthPx
            )
        return validMinWidth && validSmallestMinWidth
    }

    /**
     * Converts the dimension from Dp to pixels.
     */
    private fun convertDpToPx(density: Float, @IntRange(from = 0) dimensionDp: Int): Int {
        return (dimensionDp * density + 0.5f).toInt()
    }

    @RequiresApi(30)
    internal object Api30Impl {
        @DoNotInline
        fun getBounds(windowMetrics: WindowMetrics): Rect {
            return windowMetrics.bounds
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitRule) return false

        if (minWidthDp != other.minWidthDp) return false
        if (minSmallestWidthDp != other.minSmallestWidthDp) return false
        if (splitRatio != other.splitRatio) return false
        if (layoutDirection != other.layoutDirection) return false

        return true
    }

    override fun hashCode(): Int {
        var result = minWidthDp
        result = 31 * result + minSmallestWidthDp
        result = 31 * result + splitRatio.hashCode()
        result = 31 * result + layoutDirection
        return result
    }
}