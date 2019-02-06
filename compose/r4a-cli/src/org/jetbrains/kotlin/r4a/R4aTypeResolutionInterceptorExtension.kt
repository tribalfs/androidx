/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a

import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.extensions.TypeResolutionInterceptorExtension
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext

/**
 * If a lambda is marked as `@Composable`, then the inferred type should become `@Composable`
 */
class R4aTypeResolutionInterceptorExtension : TypeResolutionInterceptorExtension {
    override fun interceptType(element: KtElement, context: ExpressionTypingContext, resultType: KotlinType): KotlinType {
        @Suppress("SENSELESS_COMPARISON")
        if (resultType == null || resultType === TypeUtils.NO_EXPECTED_TYPE) return resultType
        if (element !is KtLambdaExpression) return resultType
        val module = context.scope.ownerDescriptor.module
        val checker =
            StorageComponentContainerContributor.getInstances(element.project).single { it is ComposableAnnotationChecker } as ComposableAnnotationChecker
        @Suppress("USELESS_IS_CHECK")
        if (element is KtLambdaExpression && (context.expectedType.hasComposableAnnotation() || checker.analyze(
                context.trace,
                element,
                resultType
            ) != ComposableAnnotationChecker.Composability.NOT_COMPOSABLE)
        ) {
            return resultType.makeComposable(module)
        }
        return resultType
    }
}
