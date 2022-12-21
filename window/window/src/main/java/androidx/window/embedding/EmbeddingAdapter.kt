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

import androidx.window.extensions.embedding.ActivityRule as OEMActivityRule
import androidx.window.extensions.embedding.ActivityRule.Builder as ActivityRuleBuilder
import androidx.window.extensions.embedding.EmbeddingRule as OEMEmbeddingRule
import androidx.window.extensions.embedding.SplitAttributes as OEMSplitAttributes
import androidx.window.extensions.embedding.SplitAttributes.SplitType as OEMSplitType
import androidx.window.extensions.embedding.SplitAttributesCalculator as OEMSplitAttributesCalculator
import androidx.window.extensions.embedding.SplitAttributesCalculator.SplitAttributesCalculatorParams as OEMSplitAttributesCalculatorParams
import androidx.window.extensions.embedding.SplitInfo as OEMSplitInfo
import androidx.window.extensions.embedding.SplitPairRule as OEMSplitPairRule
import androidx.window.extensions.embedding.SplitPairRule.Builder as SplitPairRuleBuilder
import androidx.window.extensions.embedding.SplitPlaceholderRule as OEMSplitPlaceholderRule
import androidx.window.extensions.embedding.SplitPlaceholderRule.Builder as SplitPlaceholderRuleBuilder
import androidx.window.layout.WindowMetrics as JetpackWindowMetrics
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.LayoutDirection
import android.view.WindowMetrics
import androidx.core.view.WindowInsetsCompat
import androidx.window.core.ExtensionsUtil
import androidx.window.core.PredicateAdapter
import androidx.window.embedding.EmbeddingAdapter.VendorApiLevel1Impl.setDefaultSplitAttributesCompat
import androidx.window.embedding.EmbeddingAdapter.VendorApiLevel1Impl.setFinishPrimaryWithPlaceholderCompat
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.BOTTOM_TO_TOP
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.LEFT_TO_RIGHT
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.LOCALE
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.RIGHT_TO_LEFT
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.TOP_TO_BOTTOM
import androidx.window.embedding.SplitAttributes.SplitType
import androidx.window.embedding.SplitAttributesCalculator.SplitAttributesCalculatorParams
import androidx.window.extensions.WindowExtensions
import androidx.window.extensions.embedding.SplitPairRule.FINISH_ADJACENT
import androidx.window.extensions.embedding.SplitPairRule.FINISH_ALWAYS
import androidx.window.extensions.embedding.SplitPairRule.FINISH_NEVER
import androidx.window.layout.adapter.extensions.ExtensionsWindowLayoutInfoAdapter

/**
 * Adapter class that translates data classes between Extension and Jetpack interfaces.
 */
internal class EmbeddingAdapter(
    private val predicateAdapter: PredicateAdapter
) {
    private val vendorApiLevel = ExtensionsUtil.safeVendorApiLevel

    fun translate(splitInfoList: List<OEMSplitInfo>): List<SplitInfo> {
        return splitInfoList.map(this::translate)
    }

    private fun translate(splitInfo: OEMSplitInfo): SplitInfo {
        val primaryActivityStack = splitInfo.primaryActivityStack
        val isPrimaryStackEmpty = try {
            primaryActivityStack.isEmpty
        } catch (e: NoSuchMethodError) {
            // Users may use older library which #isEmpty hasn't existed. Provide a fallback value
            // for this case to avoid crash.
            false
        }
        val primaryFragment = ActivityStack(primaryActivityStack.activities, isPrimaryStackEmpty)

        val secondaryActivityStack = splitInfo.secondaryActivityStack
        val isSecondaryStackEmpty = try {
            secondaryActivityStack.isEmpty
        } catch (e: NoSuchMethodError) {
            // Users may use older library which #isEmpty hasn't existed. Provide a fallback value
            // for this case to avoid crash.
            false
        }
        val secondaryFragment = ActivityStack(
            secondaryActivityStack.activities,
            isSecondaryStackEmpty
        )

        val splitAttributes = if (vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            translate(splitInfo.splitAttributes)
        } else {
            VendorApiLevel1Impl.getSplitAttributesCompat(splitInfo)
        }
        return SplitInfo(primaryFragment, secondaryFragment, splitAttributes)
    }

    private fun translate(splitAttributes: OEMSplitAttributes): SplitAttributes =
        SplitAttributes.Builder()
            .setSplitType(translate(splitAttributes.splitType))
            .setLayoutDirection(
                when (val layoutDirection = splitAttributes.layoutDirection) {
                    OEMSplitAttributes.LayoutDirection.LEFT_TO_RIGHT -> LEFT_TO_RIGHT
                    OEMSplitAttributes.LayoutDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT
                    OEMSplitAttributes.LayoutDirection.LOCALE -> LOCALE
                    OEMSplitAttributes.LayoutDirection.TOP_TO_BOTTOM -> TOP_TO_BOTTOM
                    OEMSplitAttributes.LayoutDirection.BOTTOM_TO_TOP -> BOTTOM_TO_TOP
                    else -> throw IllegalArgumentException(
                        "Unknown layout direction: $layoutDirection"
                    )
                }
            )
            .setAnimationBackgroundColor(splitAttributes.animationBackgroundColor)
            .build()

    private fun translate(splitType: OEMSplitType): SplitType =
        when (splitType) {
            is OEMSplitType.RatioSplitType -> translate(splitType)
            is OEMSplitType.ExpandContainersSplitType -> SplitType.expandContainers()
            is OEMSplitType.HingeSplitType -> translate(splitType)
            else -> throw IllegalArgumentException("Unsupported split type: $splitType")
        }

    private fun translate(hinge: OEMSplitType.HingeSplitType): SplitType.HingeSplitType =
        SplitType.splitByHinge(
            when (val splitType = hinge.fallbackSplitType) {
                is OEMSplitType.ExpandContainersSplitType -> SplitType.expandContainers()
                is OEMSplitType.RatioSplitType -> translate(splitType)
                else -> throw IllegalArgumentException("Unsupported split type: $splitType")
            }
        )

    private fun translate(splitRatio: OEMSplitType.RatioSplitType): SplitType.RatioSplitType =
        SplitType.ratio(splitRatio.ratio)

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private fun translateActivityPairPredicates(splitPairFilters: Set<SplitPairFilter>): Any {
        return predicateAdapter.buildPairPredicate(
            Activity::class,
            Activity::class
        ) { first: Activity, second: Activity ->
            splitPairFilters.any { filter -> filter.matchesActivityPair(first, second) }
        }
    }

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private fun translateActivityIntentPredicates(splitPairFilters: Set<SplitPairFilter>): Any {
        return predicateAdapter.buildPairPredicate(
            Activity::class,
            Intent::class
        ) { first, second ->
            splitPairFilters.any { filter -> filter.matchesActivityIntentPair(first, second) }
        }
    }

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private fun translateParentMetricsPredicate(context: Context, splitRule: SplitRule): Any =
        predicateAdapter.buildPredicate(WindowMetrics::class) { windowMetrics ->
            splitRule.checkParentMetrics(context, windowMetrics)
        }

    fun translateSplitAttributesCalculator(
        calculator: SplitAttributesCalculator
    ): OEMSplitAttributesCalculator =
        OEMSplitAttributesCalculator { oemSplitAttributesCalculatorParams ->
            translateSplitAttributes(
                calculator.computeSplitAttributesForParams(
                    translate(oemSplitAttributesCalculatorParams)
                )
            )
        }

    @SuppressLint("ClassVerificationFailure", "NewApi")
    fun translate(
        params: OEMSplitAttributesCalculatorParams
    ): SplitAttributesCalculatorParams = let {
        val taskWindowMetrics = params.parentWindowMetrics
        val taskConfiguration = params.parentConfiguration
        val defaultSplitAttributes = params.defaultSplitAttributes
        val isDefaultMinSizeSatisfied = params.isDefaultMinSizeSatisfied
        val windowLayoutInfo = params.parentWindowLayoutInfo
        val splitRuleTag = params.splitRuleTag

        val jetpackWindowMetrics = JetpackWindowMetrics(
            taskWindowMetrics.bounds,
            WindowInsetsCompat.toWindowInsetsCompat(taskWindowMetrics.windowInsets)
        )
        SplitAttributesCalculatorParams(
            jetpackWindowMetrics,
            taskConfiguration,
            translate(defaultSplitAttributes),
            isDefaultMinSizeSatisfied,
            ExtensionsWindowLayoutInfoAdapter.translate(jetpackWindowMetrics, windowLayoutInfo),
            splitRuleTag,
        )
    }

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private fun translateActivityPredicates(activityFilters: Set<ActivityFilter>): Any {
        return predicateAdapter.buildPredicate(Activity::class) { activity ->
            activityFilters.any { filter -> filter.matchesActivity(activity) }
        }
    }

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private fun translateIntentPredicates(activityFilters: Set<ActivityFilter>): Any {
        return predicateAdapter.buildPredicate(Intent::class) { intent ->
            activityFilters.any { filter -> filter.matchesIntent(intent) }
        }
    }

    private fun translateSplitPairRule(
        context: Context,
        rule: SplitPairRule,
        predicateClass: Class<*>
    ): OEMSplitPairRule {
        val builder = SplitPairRuleBuilder::class.java.getConstructor(
            predicateClass,
            predicateClass,
            predicateClass,
        ).newInstance(
            translateActivityPairPredicates(rule.filters),
            translateActivityIntentPredicates(rule.filters),
            translateParentMetricsPredicate(context, rule)
        )
            .safeSetDefaultSplitAttributes(rule.defaultSplitAttributes)
            .setShouldClearTop(rule.clearTop)
            .setFinishPrimaryWithSecondary(translateFinishBehavior(rule.finishPrimaryWithSecondary))
            .setFinishSecondaryWithPrimary(translateFinishBehavior(rule.finishSecondaryWithPrimary))
        val tag = rule.tag
        if (tag != null && vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            builder.setTag(tag)
        }
        return builder.build()
    }

    private fun SplitPairRuleBuilder.safeSetDefaultSplitAttributes(
        defaultAttrs: SplitAttributes
    ): SplitPairRuleBuilder = apply {
        if (vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            setDefaultSplitAttributes(translateSplitAttributes(defaultAttrs))
        } else {
            setDefaultSplitAttributesCompat(this@safeSetDefaultSplitAttributes, defaultAttrs)
        }
    }

    private fun translateSplitAttributes(splitAttributes: SplitAttributes): OEMSplitAttributes {
        require(vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2)
        // To workaround the "unused" error in ktlint. It is necessary to translate SplitAttributes
        // from WM Jetpack version to WM extension version.
        return androidx.window.extensions.embedding.SplitAttributes.Builder()
            .setSplitType(translateSplitType(splitAttributes.splitType))
            .setLayoutDirection(
                when (splitAttributes.layoutDirection) {
                    LOCALE -> OEMSplitAttributes.LayoutDirection.LOCALE
                    LEFT_TO_RIGHT -> OEMSplitAttributes.LayoutDirection.LEFT_TO_RIGHT
                    RIGHT_TO_LEFT -> OEMSplitAttributes.LayoutDirection.RIGHT_TO_LEFT
                    TOP_TO_BOTTOM -> OEMSplitAttributes.LayoutDirection.TOP_TO_BOTTOM
                    BOTTOM_TO_TOP -> OEMSplitAttributes.LayoutDirection.BOTTOM_TO_TOP
                    else -> throw IllegalArgumentException("Unsupported layoutDirection:" +
                        "$splitAttributes.layoutDirection"
                    )
                }
            )
            .setAnimationBackgroundColor(splitAttributes.animationBackgroundColor)
            .build()
    }

    private fun translateSplitType(splitType: SplitType): OEMSplitType {
        require(vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2)
        return when (splitType) {
            is SplitType.HingeSplitType -> translateHinge(splitType)
            is SplitType.ExpandContainersSplitType -> OEMSplitType.ExpandContainersSplitType()
            is SplitType.RatioSplitType -> translateRatio(splitType)
            else -> throw IllegalArgumentException("Unsupported splitType: $splitType")
        }
    }

    private fun translateHinge(hinge: SplitType.HingeSplitType): OEMSplitType.HingeSplitType =
        OEMSplitType.HingeSplitType(
            when (val splitType = hinge.fallbackSplitType) {
                is SplitType.ExpandContainersSplitType -> OEMSplitType.ExpandContainersSplitType()
                is SplitType.RatioSplitType -> translateRatio(splitType)
                else -> throw IllegalArgumentException("Unsupported splitType: $splitType")
            }
        )

    private fun translateRatio(splitRatio: SplitType.RatioSplitType): OEMSplitType.RatioSplitType =
        OEMSplitType.RatioSplitType(splitRatio.ratio)

    private fun translateSplitPlaceholderRule(
        context: Context,
        rule: SplitPlaceholderRule,
        predicateClass: Class<*>
    ): OEMSplitPlaceholderRule {
        val builder = SplitPlaceholderRuleBuilder::class.java.getConstructor(
            Intent::class.java,
            predicateClass,
            predicateClass,
            predicateClass
        ).newInstance(
            rule.placeholderIntent,
            translateActivityPredicates(rule.filters),
            translateIntentPredicates(rule.filters),
            translateParentMetricsPredicate(context, rule)
        )
            .setSticky(rule.isSticky)
            .safeSetFinishPrimaryWithPlaceholder(rule.finishPrimaryWithPlaceholder)
            .safeSetDefaultSplitAttributes(rule.defaultSplitAttributes)
        val tag = rule.tag
        if (tag != null && vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            builder.setTag(tag)
        }
        return builder.build()
    }

    private fun SplitPlaceholderRuleBuilder.safeSetFinishPrimaryWithPlaceholder(
        behavior: SplitRule.FinishBehavior
    ): SplitPlaceholderRuleBuilder {
        return if (
            vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2
        ) {
            setFinishPrimaryWithPlaceholder(translateFinishBehavior(behavior))
        } else {
            setFinishPrimaryWithPlaceholderCompat(
                this@safeSetFinishPrimaryWithPlaceholder,
                translateFinishBehavior(behavior)
            )
        }
    }

    private fun translateFinishBehavior(behavior: SplitRule.FinishBehavior): Int =
        when (behavior) {
            SplitRule.FinishBehavior.NEVER -> FINISH_NEVER
            SplitRule.FinishBehavior.ALWAYS -> FINISH_ALWAYS
            SplitRule.FinishBehavior.ADJACENT -> FINISH_ADJACENT
            else -> throw IllegalArgumentException("Unknown finish behavior:$behavior")
        }

    private fun SplitPlaceholderRuleBuilder.safeSetDefaultSplitAttributes(
        defaultAttrs: SplitAttributes
    ): SplitPlaceholderRuleBuilder = apply {
        return if (vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            setDefaultSplitAttributes(translateSplitAttributes(defaultAttrs))
        } else {
            setDefaultSplitAttributesCompat(this@safeSetDefaultSplitAttributes, defaultAttrs)
        }
    }

    private fun translateActivityRule(
        rule: ActivityRule,
        predicateClass: Class<*>
    ): OEMActivityRule {
        val builder = ActivityRuleBuilder::class.java.getConstructor(
            predicateClass,
            predicateClass
        ).newInstance(
            translateActivityPredicates(rule.filters),
            translateIntentPredicates(rule.filters)
        )
            .setShouldAlwaysExpand(rule.alwaysExpand)
        val tag = rule.tag
        if (tag != null && vendorApiLevel >= WindowExtensions.VENDOR_API_LEVEL_2) {
            builder.setTag(tag)
        }
        return builder.build()
    }

    fun translate(context: Context, rules: Set<EmbeddingRule>): Set<OEMEmbeddingRule> {
        val predicateClass = predicateAdapter.predicateClassOrNull() ?: return emptySet()
        return rules.map { rule ->
            when (rule) {
                is SplitPairRule -> translateSplitPairRule(context, rule, predicateClass)
                is SplitPlaceholderRule ->
                    translateSplitPlaceholderRule(context, rule, predicateClass)
                is ActivityRule -> translateActivityRule(rule, predicateClass)
                else -> throw IllegalArgumentException("Unsupported rule type")
            }
        }.toSet()
    }

    /**
     * Provides backward compatibility for Window extensions with
     * [WindowExtensions.VENDOR_API_LEVEL_1]
     * @see WindowExtensions.getVendorApiLevel
     */
    // Suppress deprecation because this object is to provide backward compatibility.
    @Suppress("DEPRECATION")
    private object VendorApiLevel1Impl {
        fun setFinishPrimaryWithPlaceholderCompat(
            builder: SplitPlaceholderRuleBuilder,
            behavior: Int
        ): SplitPlaceholderRuleBuilder = builder.setFinishPrimaryWithSecondary(behavior)

        fun setDefaultSplitAttributesCompat(
            builder: SplitPlaceholderRuleBuilder,
            defaultAttrs: SplitAttributes,
        ): SplitPlaceholderRuleBuilder {
            val (splitRatio, layoutDirection) = translateSplitAttributesCompatInternal(defaultAttrs)
            return builder // #setDefaultAttributes or SplitAttributes ctr weren't supported.
                .setSplitRatio(splitRatio)
                .setLayoutDirection(layoutDirection)
        }

        fun setDefaultSplitAttributesCompat(
            builder: SplitPairRuleBuilder,
            defaultAttrs: SplitAttributes,
        ): SplitPairRuleBuilder {
            val (splitRatio, layoutDirection) = translateSplitAttributesCompatInternal(defaultAttrs)
            return builder // #setDefaultAttributes or SplitAttributes ctr weren't supported.
                .setSplitRatio(splitRatio)
                .setLayoutDirection(layoutDirection)
        }

        private fun translateSplitAttributesCompatInternal(
            attrs: SplitAttributes
        ): Pair<Float, Int> = // Use a (Float, Integer) pair since SplitAttributes weren't supported
            if (!isSplitAttributesSupported(attrs)) {
                // Fallback to expand the secondary container if the SplitAttributes are not
                // supported.
                Pair(0.0f, LayoutDirection.LOCALE)
            } else {
                Pair(
                    attrs.splitType.value,
                    when (attrs.layoutDirection) {
                        // Legacy LayoutDirection uses LayoutDirection constants in framework APIs.
                        LOCALE -> LayoutDirection.LOCALE
                        LEFT_TO_RIGHT -> LayoutDirection.LTR
                        RIGHT_TO_LEFT -> LayoutDirection.RTL
                        else -> throw IllegalStateException("Unsupported layout direction must be" +
                            " covered in @isSplitAttributesSupported!")
                    }
                )
            }

        /**
         * Returns `true` if `attrs` is compatible with [WindowExtensions.VENDOR_API_LEVEL_1] and
         * doesn't use the new features introduced in [WindowExtensions.VENDOR_API_LEVEL_2] or
         * higher.
         */
        private fun isSplitAttributesSupported(attrs: SplitAttributes) =
            attrs.splitType is SplitType.RatioSplitType &&
                attrs.layoutDirection in arrayOf(LEFT_TO_RIGHT, RIGHT_TO_LEFT, LOCALE)

        /**
         * Obtains [SplitAttributes] from [OEMSplitInfo] with [WindowExtensions.VENDOR_API_LEVEL_1]
         */
        fun getSplitAttributesCompat(splitInfo: OEMSplitInfo): SplitAttributes =
            SplitAttributes.Builder()
                .setSplitType(SplitType.buildSplitTypeFromValue(splitInfo.splitRatio))
                .setLayoutDirection(LOCALE)
                .build()
    }
}
