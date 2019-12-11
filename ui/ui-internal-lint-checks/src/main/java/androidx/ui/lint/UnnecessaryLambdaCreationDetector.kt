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

package androidx.ui.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.kotlin.KotlinUBlockExpression
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.kotlin.KotlinUImplicitReturnExpression

/**
 * Lint [Detector] to ensure that we are not creating extra lambdas just to emit already captured
 * lambdas inside Compose code. For example:
 * ```
 * val lambda = @Composable {}
 * Foo {
 *     lambda()
 * }
 * ```
 *
 * Can just be inlined to:
 * ```
 * Foo(lambda)
 * ```
 *
 * This helps avoid object allocation but more importantly helps us avoid extra code generation
 * around composable lambdas.
 */
class UnnecessaryLambdaCreationDetector : Detector(), SourceCodeScanner {
    override fun createUastHandler(context: JavaContext) = UnnecessaryLambdaCreationHandler(context)

    override fun getApplicableUastTypes() = listOf(ULambdaExpression::class.java)

    /**
     * This handler visits every lambda expression and reports an issue if the following criteria
     * (in order) hold true:
     *
     * 1. There is only one expression inside the lambda.
     * 2. The expression is a function call
     * 3. The lambda is being invoked as part of a function call, and not as a property assignment
     *    such as val foo = @Composable {}
     * 4. The receiver type of the function call is `Function0` (i.e, we are invoking something
     *    that matches `() -> Unit` - this both avoids non-lambda invocations but also makes sure
     *    that we don't warn for lambdas that have parameters, such as @Composable() (Int) -> Unit
     *    - this cannot be inlined.)
     * 5. The outer function call that contains this lambda is not a call to a `ComponentNode`
     *    (because these are technically constructor invocations that we just intercept calls to
     *    there is no way to avoid using a trailing lambda for this)
     * 6. The lambda is not being passed as a parameter, for example `Foo { lambda -> lambda() }`
     */
    class UnnecessaryLambdaCreationHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitLambdaExpression(node: ULambdaExpression) {
            val expressions = (node.body as? KotlinUBlockExpression)?.expressions ?: return

            if (expressions.size != 1) return

            val expression = when (val expr = expressions.first()) {
                is KotlinUFunctionCallExpression -> expr
                is KotlinUImplicitReturnExpression ->
                    expr.returnExpression as? KotlinUFunctionCallExpression
                else -> null
            } ?: return

            // We want to make sure this lambda is being invoked in the context of a function call,
            // and not as a property assignment.
            val parentExpression = node.uastParent!!.sourcePsi as? KtCallExpression ?: return

            // If the expression has no receiver, it is not a lambda invocation
            val functionType = expression.receiverType as? PsiClassReferenceType ?: return

            // Find the functional type of the parent argument, for example () -> Unit (Function0)
            val argumentType = node.getExpressionType() as? PsiClassReferenceType ?: return

            // Return if the receiver of the lambda argument and the lambda itself don't match. This
            // happens if the functional types are different, for example a lambda with 0 parameters
            // (Function0) and a lambda with 1 parameter (Function1). Similarly for two lambdas
            // with 0 parameters, but one that has a receiver scope (SomeScope.() -> Unit).
            if (functionType != argumentType) return

            // Unfortunately if the types come from a separate module, we don't have access to
            // the type information in the function / argument, so instead we just get an error
            // type. If both compare equally, and they are reporting an error type, we cannot do
            // anything about this so just skip warning. This will only happen if there _are_
            // types, i.e a scoped / parameterized function type, so it's rare enough that it
            // shouldn't matter that much in practice.
            if (functionType.reference.canonicalText.contains(NonExistentClass)) return

            if (parentExpression.isComponentNodeInvocation()) return

            val lambdaName = expression.methodIdentifier!!.name
            if (node.valueParameters.any { it.name == lambdaName }) return

            context.report(
                ISSUE,
                node,
                context.getNameLocation(expression),
                "Creating an unnecessary lambda to emit a captured lambda"
            )
        }
    }

    companion object {
        private fun KtCallExpression.isComponentNodeInvocation() =
            referenceExpression()!!.text.endsWith("Node")

        private const val NonExistentClass = "error.NonExistentClass"

        private const val explanation =
            "Creating this extra lambda instead of just passing the already captured lambda means" +
                    " that during code generation the Compose compiler will insert code around " +
                    "this lambda to track invalidations. This adds some extra runtime cost so you" +
                    " should instead just directly pass the lambda as a parameter to the function."

        val ISSUE = Issue.create(
            "UnnecessaryLambdaCreation",
            "Creating an unnecessary lambda to emit a captured lambda",
            explanation,
            Category.PERFORMANCE, 5, Severity.ERROR,
            Implementation(
                UnnecessaryLambdaCreationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
