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

package androidx.lifecycle.lint

import androidx.lifecycle.lint.LifecycleWhenChecks.Companion.ISSUE
import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.elements.KtLightModifierList
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val CONTINUATION = "kotlin.coroutines.experimental.Continuation<? super kotlin.Unit>"

internal val VIEW_ERROR_MESSAGE =
    "Unsafe View from finally/catch block inside of Lifecycle.when* scope"

internal val APPLICABLE_METHOD_NAMES = listOf("whenStarted")

class LifecycleWhenChecks : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames() = APPLICABLE_METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val valueArguments = node.valueArguments
        if (valueArguments.size != 1 || !method.isLifecycleWhenExtension(context)) {
            return
        }
        (valueArguments[0] as? ULambdaExpression)?.body?.accept(LifecycleWhenVisitor(context))
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnsafeLifecycleWhenUsage",
            briefDescription = "Unsafe UI operation in finally/catch of " +
                    "Lifecycle.whenStarted of similar method",
            explanation = """If the `Lifecycle` is destroyed within the block of \
                    `Lifecycle.whenStarted` or any similar `Lifecycle.when` method is suspended, \
                    the block will be cancelled, which will also cancel any child coroutine \
                    launched inside the block. As as a result, If you have a try finally block \
                    in your code, the finally might run after the Lifecycle moves outside \
                    the desired state. It is recommended to check the `Lifecycle.isAtLeast` \
                    before accessing UI in finally block. Similarly, \
                    if you have a catch statement that might catch `CancellationException`, \
                    you should check the `Lifecycle.isAtLeast` before accessing the UI. See \
                    documentation of `Lifecycle.whenStateAtLeast` for more details""",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(LifecycleWhenChecks::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true
        )
    }
}

internal class LifecycleWhenVisitor(private val context: JavaContext) : AbstractUastVisitor() {

    /**
     * any state -> [IN_TRY], when we enter in try block  and previous state is saved on stack
     *
     * [IN_TRY] -> [AFTER_SUSPEND_TRY] when we enter in finally /catch block after of try block
     * *with* suspend call.
     *
     * [IN_TRY] -> *previously saved state* when we enter in finally /catch block after of try block
     * *without*  suspend call.
     *
     * [AFTER_SUSPEND_TRY] -> *previously saved state* after finally / catch block
     */
    private enum class State { DEFAULT, IN_TRY, AFTER_SUSPEND_TRY }
    private var state = State.DEFAULT
    private var hasSuspendCall = false

    override fun visitTryExpression(node: UTryExpression): Boolean {
        val prevTryState = state
        val prevHasSuspendCall = hasSuspendCall
        hasSuspendCall = false
        state = State.IN_TRY
        node.tryClause.accept(this)
        // TODO: support catch
        state = if (hasSuspendCall) State.AFTER_SUSPEND_TRY else prevTryState
        node.finallyClause?.accept(this)
        state = prevTryState
        hasSuspendCall = (state != State.DEFAULT) and (hasSuspendCall or prevHasSuspendCall)
        return true
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
        val psiMethod = node.resolve() ?: return super.visitCallExpression(node)

        val isSuspend = psiMethod.isSuspend()
        if (isSuspend) {
            // go inside and check it doesn't access
            (psiMethod.toUElement() as? UMethod)?.uastBody?.accept(this)
        }
        hasSuspendCall = hasSuspendCall or isSuspend

        if (state == State.AFTER_SUSPEND_TRY) {
            val receiverClass = PsiTypesUtil.getPsiClass(node.receiverType)
            if (context.evaluator.extendsClass(receiverClass, SdkConstants.CLASS_VIEW, false)) {
                context.report(ISSUE, node, context.getLocation(node), VIEW_ERROR_MESSAGE)
            }
        }
        return super.visitCallExpression(node)
    }

    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
        // we probably should actually look at contracts,
        // because only `callsInPlace` lambdas inherit coroutine scope. But contracts aren't stable
        // yet =(
        // if lambda is suspending it means something else defined its scope
        return node.isSuspendLambda(context) || super.visitLambdaExpression(node)
    }

    // ignore classes defined inline
    override fun visitClass(node: UClass) = true

    // ignore fun defined inline
    override fun visitDeclaration(node: UDeclaration) = true
}

private const val DISPATCHER_CLASS_NAME = "androidx.lifecycle.PausingDispatcherKt"

private fun PsiMethod.isLifecycleWhenExtension(context: JavaContext): Boolean {
    return name in APPLICABLE_METHOD_NAMES &&
            context.evaluator.isMemberInClass(this, DISPATCHER_CLASS_NAME) &&
            context.evaluator.isStatic(this)
}

// TODO: find a better way!
private fun ULambdaExpression.isSuspendLambda(context: JavaContext): Boolean {
    val expressionClass = getExpressionType() as? PsiClassType ?: return false
    val params = expressionClass.parameters
    // suspend functions are FunctionN<*, Continuation, Obj>
    if (params.size < 2) {
        return false
    }
    val superBound = (params[params.size - 2] as? PsiWildcardType)?.superBound as? PsiClassType
    return if (superBound != null) {
        context.evaluator.getQualifiedName(superBound) == CONTINUATION
    } else {
        false
    }
}

private fun PsiMethod.isSuspend(): Boolean {
    val modifiers = modifierList as? KtLightModifierList<*>
    return modifiers?.kotlinOrigin?.hasModifier(KtTokens.SUSPEND_KEYWORD) ?: false
}