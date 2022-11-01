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

import android.content.Intent
import androidx.annotation.IntRange
import androidx.core.util.Preconditions.checkArgument
import androidx.core.util.Preconditions.checkArgumentNonnegative
import androidx.window.embedding.SplitRule.FinishBehavior.Companion.ALWAYS
import androidx.window.embedding.SplitRule.FinishBehavior.Companion.NEVER

/**
 * Configuration rules for split placeholders.
 */
class SplitPlaceholderRule : SplitRule {

    /**
     * Filters used to choose when to apply this rule. The rule may be used if any one of the
     * provided filters matches.
     */
    val filters: Set<ActivityFilter>

    /**
     * Intent to launch the placeholder activity.
     */
    val placeholderIntent: Intent

    /**
     * Determines whether the placeholder will show on top in a smaller window size after it first
     * appeared in a split with sufficient minimum width.
     */
    val isSticky: Boolean

    /**
     * Determines what happens with the primary container when all activities are finished in the
     * associated placeholder container.
     *
     * **Note** that it is not valid to set [SplitRule.FinishBehavior.NEVER]
     * @see SplitRule.FinishBehavior
     */
    val finishPrimaryWithPlaceholder: FinishBehavior

    internal constructor(
        tag: String? = null,
        filters: Set<ActivityFilter>,
        placeholderIntent: Intent,
        isSticky: Boolean,
        finishPrimaryWithPlaceholder: FinishBehavior = ALWAYS,
        @IntRange(from = 0) minWidthDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP,
        @IntRange(from = 0) minHeightDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP,
        @IntRange(from = 0) minSmallestWidthDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP,
        defaultSplitAttributes: SplitAttributes,
    ) : super(tag, minWidthDp, minHeightDp, minSmallestWidthDp, defaultSplitAttributes) {
        checkArgumentNonnegative(minWidthDp, "minWidthDp must be non-negative")
        checkArgumentNonnegative(minHeightDp, "minHeightDp must be non-negative")
        checkArgumentNonnegative(minSmallestWidthDp, "minSmallestWidthDp must be non-negative")
        checkArgument(finishPrimaryWithPlaceholder != NEVER,
            "NEVER is not a valid configuration for SplitPlaceholderRule. " +
                "Please use FINISH_ALWAYS or FINISH_ADJACENT instead or refer to the current API.")
        this.filters = filters.toSet()
        this.placeholderIntent = placeholderIntent
        this.isSticky = isSticky
        this.finishPrimaryWithPlaceholder = finishPrimaryWithPlaceholder
    }

    /**
     * Builder for [SplitPlaceholderRule].
     * @param filters See [SplitPlaceholderRule.filters].
     * @param placeholderIntent See [SplitPlaceholderRule.placeholderIntent].
     */
    class Builder(
        private val filters: Set<ActivityFilter>,
        private val placeholderIntent: Intent
    ) {
        @IntRange(from = 0)
        private var minWidthDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP
        @IntRange(from = 0)
        private var minHeightDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP
        @IntRange(from = 0)
        private var minSmallestWidthDp: Int = DEFAULT_SPLIT_MIN_DIMENSION_DP
        private var finishPrimaryWithPlaceholder: FinishBehavior = ALWAYS
        private var isSticky: Boolean = false
        private var defaultSplitAttributes: SplitAttributes = SplitAttributes.Builder().build()
        private var tag: String? = null

        /**
         * @see SplitPlaceholderRule.minWidthDp
         */
        fun setMinWidthDp(@IntRange(from = 0) minWidthDp: Int): Builder =
            apply { this.minWidthDp = minWidthDp }

        /**
         * @see SplitPlaceholderRule.minHeightDp
         */
        fun setMinHeightDp(@IntRange(from = 0) minHeightDp: Int): Builder =
            apply { this.minHeightDp = minHeightDp }

        /**
         * @see SplitPlaceholderRule.minSmallestWidthDp
         */
        fun setMinSmallestWidthDp(@IntRange(from = 0) minSmallestWidthDp: Int): Builder =
            apply { this.minSmallestWidthDp = minSmallestWidthDp }

        /**
         * @see SplitPlaceholderRule.finishPrimaryWithPlaceholder
         */
        fun setFinishPrimaryWithPlaceholder(finishPrimaryWithPlaceholder: FinishBehavior): Builder =
            apply {
               this.finishPrimaryWithPlaceholder = finishPrimaryWithPlaceholder
            }

        /**
         * @see SplitPlaceholderRule.isSticky
         */
        fun setSticky(isSticky: Boolean): Builder =
            apply { this.isSticky = isSticky }

        /** @see SplitPlaceholderRule.defaultSplitAttributes */
        fun setDefaultSplitAttributes(defaultSplitAttributes: SplitAttributes): Builder =
            apply { this.defaultSplitAttributes = defaultSplitAttributes }

        /** @see SplitPlaceholderRule.tag */
        fun setTag(tag: String?): Builder =
            apply { this.tag = tag }

        fun build() = SplitPlaceholderRule(
            tag,
            filters,
            placeholderIntent,
            isSticky,
            finishPrimaryWithPlaceholder,
            minWidthDp,
            minHeightDp,
            minSmallestWidthDp,
            defaultSplitAttributes,
        )
    }

    /**
     * Creates a new immutable instance by adding a filter to the set.
     * @see filters
     */
    internal operator fun plus(filter: ActivityFilter): SplitPlaceholderRule {
        val newSet = mutableSetOf<ActivityFilter>()
        newSet.addAll(filters)
        newSet.add(filter)
        return Builder(newSet.toSet(), placeholderIntent)
            .setTag(tag)
            .setMinWidthDp(minWidthDp)
            .setMinHeightDp(minHeightDp)
            .setMinSmallestWidthDp(minSmallestWidthDp)
            .setSticky(isSticky)
            .setFinishPrimaryWithPlaceholder(finishPrimaryWithPlaceholder)
            .setDefaultSplitAttributes(defaultSplitAttributes)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitPlaceholderRule) return false
        if (!super.equals(other)) return false

        if (placeholderIntent != other.placeholderIntent) return false
        if (isSticky != other.isSticky) return false
        if (finishPrimaryWithPlaceholder != other.finishPrimaryWithPlaceholder) return false
        if (filters != other.filters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + placeholderIntent.hashCode()
        result = 31 * result + isSticky.hashCode()
        result = 31 * result + finishPrimaryWithPlaceholder.hashCode()
        result = 31 * result + filters.hashCode()
        return result
    }

    override fun toString(): String =
         "SplitPlaceholderRule{" +
             "tag=$tag" +
             ", defaultSplitAttributes=$defaultSplitAttributes" +
             ", minWidthDp=$minWidthDp" +
             ", minHeightDp=$minHeightDp" +
             ", minSmallestWidthDp=$minSmallestWidthDp" +
             ", placeholderIntent=$placeholderIntent" +
             ", isSticky=$isSticky" +
             ", finishPrimaryWithPlaceholder=$finishPrimaryWithPlaceholder" +
             ", filters=$filters" +
             "}"
}