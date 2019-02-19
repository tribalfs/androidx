package org.jetbrains.kotlin.r4a

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.r4a.analysis.ComponentMetadata
import org.jetbrains.kotlin.r4a.analysis.R4ADefaultErrorMessages
import org.jetbrains.kotlin.r4a.analysis.R4AErrors
import org.jetbrains.kotlin.r4a.analysis.R4AWritableSlices.COMPOSABLE_ANALYSIS
import org.jetbrains.kotlin.resolve.AdditionalAnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.TargetPlatform
import org.jetbrains.kotlin.resolve.calls.checkers.AdditionalTypeChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.context.CallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.inline.InlineUtil.isInlinedArgument
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.upperIfFlexible
import org.jetbrains.kotlin.util.OperatorNameConventions

class ComposableAnnotationChecker(val mode: Mode = DEFAULT_MODE) : CallChecker, DeclarationChecker,
    AdditionalTypeChecker, AdditionalAnnotationChecker, StorageComponentContainerContributor {

    enum class Mode {
        /** @Composable annotations are enforced when inferred/specified **/
        CHECKED,
        /** @Composable annotations are explicitly required on all composables **/
        STRICT,
        /** @Composable annotations are explicitly required and are not subtypes of their unannotated types **/
        PEDANTIC
    }

    companion object {
        val DEFAULT_MODE = Mode.STRICT

        fun get(project: Project): ComposableAnnotationChecker {
            return StorageComponentContainerContributor.getInstances(project).single { it is ComposableAnnotationChecker } as ComposableAnnotationChecker
        }
    }

    enum class Composability { NOT_COMPOSABLE, INFERRED, MARKED }

    public fun shouldInvokeAsTag(trace: BindingTrace, resolvedCall: ResolvedCall<*>): Boolean {
        if (resolvedCall is VariableAsFunctionResolvedCall) {
            if(resolvedCall.variableCall.candidateDescriptor.type.hasComposableAnnotation()) return true
            if(resolvedCall.variableCall.candidateDescriptor.isComposableFromChildrenAnnotation()) return true
            if(resolvedCall.functionCall.resultingDescriptor.hasComposableAnnotation()) return true
            return false
        }
        val candidateDescriptor = resolvedCall.candidateDescriptor
        if (candidateDescriptor is FunctionDescriptor) {
            if (candidateDescriptor.isOperator && candidateDescriptor.name == OperatorNameConventions.INVOKE) {
                if (resolvedCall.dispatchReceiver?.type?.hasComposableAnnotation() == true) {
                    return true
                }
            }
        }
        if(candidateDescriptor is FunctionDescriptor) {
            when(analyze(trace, candidateDescriptor)) {
                Composability.NOT_COMPOSABLE -> return false
                Composability.INFERRED -> return true
                Composability.MARKED -> return true
            }
        }
        if(candidateDescriptor is ValueParameterDescriptor) {
            if(candidateDescriptor.isComposableFromChildrenAnnotation()) return true
            return candidateDescriptor.type.hasComposableAnnotation()
        }
        if(candidateDescriptor is LocalVariableDescriptor) {
            return candidateDescriptor.type.hasComposableAnnotation()
        }
        if(candidateDescriptor is PropertyDescriptor) {
            if(candidateDescriptor.isComposableFromChildrenAnnotation()) return true
            return candidateDescriptor.type.hasComposableAnnotation()
        }
        return candidateDescriptor.hasComposableAnnotation()
    }

    fun analyze(trace: BindingTrace, descriptor: FunctionDescriptor): Composability {
        val psi = descriptor.findPsi() as? KtElement
        psi?.let { trace.bindingContext.get(COMPOSABLE_ANALYSIS, it)?.let { return it } }
        if (descriptor is FunctionDescriptor && descriptor.name == Name.identifier("compose") && descriptor.containingDeclaration is ClassDescriptor && ComponentMetadata.isR4AComponent(
                descriptor.containingDeclaration
            )
        ) return Composability.MARKED
        var composability = Composability.NOT_COMPOSABLE
        when (descriptor) {
            is VariableDescriptor -> if(descriptor.hasComposableAnnotation() || descriptor.type.hasComposableAnnotation()) composability = Composability.MARKED
            is ConstructorDescriptor -> if(descriptor.hasComposableAnnotation()) composability = Composability.MARKED
            is JavaMethodDescriptor -> if(descriptor.hasComposableAnnotation()) composability = Composability.MARKED
            else -> if(descriptor.hasComposableAnnotation()) composability = Composability.MARKED
        }
        (descriptor.findPsi() as? KtElement)?.let { element -> composability = analyzeFunctionContents(trace, element, composability) }
        psi?.let { trace.record(COMPOSABLE_ANALYSIS, it, composability) }
        return composability
    }

    private fun analyzeFunctionContents(trace: BindingTrace, element: KtElement, signatureComposability: Composability): Composability {
        var composability = signatureComposability
        var localKtx = false
        var isInlineLambda = false
        element.accept(object : KtTreeVisitorVoid() {
            override fun visitKtxElement(element: KtxElement) {
                localKtx = true
            }

            override fun visitDeclaration(dcl: KtDeclaration) {
                if (dcl == element) {
                    super.visitDeclaration(dcl)
                }
            }

            override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                val isInlineable = isInlinedArgument(lambdaExpression.functionLiteral, trace.bindingContext, true)
                if (isInlineable && lambdaExpression == element) isInlineLambda = true
                if (isInlineable || lambdaExpression == element) super.visitLambdaExpression(lambdaExpression)
            }
        }, null)
        if (localKtx && !isInlineLambda && composability != Composability.MARKED && (mode == Mode.STRICT || mode == Mode.PEDANTIC)) {
            val reportElement = when (element) {
                is KtNamedFunction -> element.nameIdentifier ?: element
                else -> element
            }
            trace.reportFromPlugin(R4AErrors.KTX_IN_NON_COMPOSABLE.on(reportElement), R4ADefaultErrorMessages)
        }
        if(localKtx && composability == Composability.NOT_COMPOSABLE) composability = Composability.INFERRED
        return composability
    }

    /**
     * Analyze a KtElement
     *  - Determine if it is @Compoasble (eg. the element or inferred type has an @Composable annotation)
     *  - Update the binding context to cache analysis results
     *  - Report errors (eg. KTX tags occur in a non-composable, invocations of an @Composable, etc)
     *  - Return true if element is @Composable, else false
     */
    fun analyze(trace: BindingTrace, element: KtElement, type: KotlinType?): Composability {
        trace.bindingContext.get(COMPOSABLE_ANALYSIS, element)?.let { return it }

        var composability = Composability.NOT_COMPOSABLE

        if (element is KtClass) {
            val descriptor = trace.bindingContext.get(BindingContext.CLASS, element) ?: element.getResolutionFacade().resolveToDescriptor(
                element,
                BodyResolveMode.FULL
            ) as ClassDescriptor
            val annotationEntry = element.annotationEntries.singleOrNull {
                trace.bindingContext.get(BindingContext.ANNOTATION, it)?.isComposableAnnotation ?: false
            }
            if (annotationEntry != null && !ComponentMetadata.isR4AComponent(descriptor)) {
                trace.report(Errors.WRONG_ANNOTATION_TARGET.on(annotationEntry, "class which does not extend com.google.r4a.Component"))
            }
            if (ComponentMetadata.isR4AComponent(descriptor)) {
                composability += Composability.MARKED
            }
        }
        if (element is KtParameter) {
            val childrenAnnotation = element.annotationEntries
                .mapNotNull { trace.bindingContext.get(BindingContext.ANNOTATION, it) }
                .singleOrNull { it.isComposableChildrenAnnotation }

            if (childrenAnnotation != null) {
                composability += Composability.MARKED
            }

            val composableAnnotation = element
                .typeReference
                ?.annotationEntries
                ?.mapNotNull { trace.bindingContext.get(BindingContext.ANNOTATION, it) }
                ?.singleOrNull { it.isComposableAnnotation }

            if (composableAnnotation != null) {
                composability += Composability.MARKED
            }
        }
        if (element is KtParameter) {
            val childrenAnnotation = element.annotationEntries
                .mapNotNull { trace.bindingContext.get(BindingContext.ANNOTATION, it) }
                .singleOrNull { it.isComposableChildrenAnnotation }

            if (childrenAnnotation != null) {
                composability += Composability.MARKED
            }

            val composableAnnotation = element
                .typeReference
                ?.annotationEntries
                ?.mapNotNull { trace.bindingContext.get(BindingContext.ANNOTATION, it) }
                ?.singleOrNull { it.isComposableAnnotation }

            if (composableAnnotation != null) {
                composability += Composability.MARKED
            }
        }

        //if (candidateDescriptor.type.arguments.size != 1 || !candidateDescriptor.type.arguments[0].type.isUnit()) return false
        if (
            type != null &&
            type !== TypeUtils.NO_EXPECTED_TYPE &&
            type.hasComposableAnnotation()
        ) {
            composability += Composability.MARKED
        }
        val parent = element.parent
        val annotations = when {
            element is KtNamedFunction -> element.annotationEntries
            parent is KtAnnotatedExpression -> parent.annotationEntries
            element is KtProperty -> element.annotationEntries
            element is KtParameter -> element.typeReference?.annotationEntries ?: emptyList()
            else -> emptyList()
        }

        for (entry in annotations) {
            val descriptor = trace.bindingContext.get(BindingContext.ANNOTATION, entry) ?: continue
            if (descriptor.isComposableAnnotation) {
                composability += Composability.MARKED
            }
            if (descriptor.isComposableChildrenAnnotation) {
                composability += Composability.MARKED
            }
        }

        if (element is KtLambdaExpression || element is KtFunction) {
            composability = analyzeFunctionContents(trace, element, composability)
        }

        trace.record(COMPOSABLE_ANALYSIS, element, composability)
        return composability
    }

    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor
    ) {
        if (platform != JvmPlatform) return
        container.useInstance(this)
    }

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        when(descriptor) {
            is ClassDescriptor -> {
                val trace = context.trace
                val element = descriptor.findPsi()
                if (element is KtClass) {
                    val descriptor =
                        trace.bindingContext.get(BindingContext.CLASS, element) ?: element.getResolutionFacade().resolveToDescriptor(
                            element,
                            BodyResolveMode.FULL
                        ) as ClassDescriptor
                    val composableAnnotationEntry = element.annotationEntries.singleOrNull {
                        trace.bindingContext.get(BindingContext.ANNOTATION, it)?.isComposableAnnotation ?: false
                    }
                    if (composableAnnotationEntry != null && !ComponentMetadata.isR4AComponent(descriptor)) {
                        trace.report(Errors.WRONG_ANNOTATION_TARGET.on(composableAnnotationEntry, "class which does not extend com.google.r4a.Component"))
                    }
                }
            }
            is PropertyDescriptor -> {}
            is LocalVariableDescriptor -> {}
            is FunctionDescriptor -> analyze(context.trace, descriptor)
            else ->
                throw Error("currently unsupported "+descriptor.javaClass)
        }
    }

    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {

        val shouldBeTag = shouldInvokeAsTag(context.trace, resolvedCall)
        // NOTE: we return early here because the KtxCallResolver does its own composable checking (by delegating to this class) and has
        // more context to make the right call.
        if (reportOn.parent is KtxElement) return
        if (context.resolutionContext is CallResolutionContext && resolvedCall is ResolvedCallImpl && (context.resolutionContext as CallResolutionContext).call.callElement is KtCallExpression) {
            if (shouldBeTag) {
                context.trace.reportFromPlugin(
                    R4AErrors.SVC_INVOCATION.on(
                        reportOn as KtElement,
                        resolvedCall.candidateDescriptor.name.asString()
                    ), R4ADefaultErrorMessages
                )
            }
        }
    }

    override fun checkType(
        expression: KtExpression,
        expressionType: KotlinType,
        expressionTypeWithSmartCast: KotlinType,
        c: ResolutionContext<*>
    ) {
        if (mode != Mode.PEDANTIC) return
        if (expression is KtLambdaExpression) {
            val expectedType = c.expectedType
            if (expectedType === TypeUtils.NO_EXPECTED_TYPE) return
            val expectedComposable = expectedType.hasComposableAnnotation()
            val composability = analyze(c.trace, expression, c.expectedType)
            if ((expectedComposable && composability == Composability.NOT_COMPOSABLE) || (!expectedComposable && composability == Composability.MARKED)) {
                val isInlineable = isInlinedArgument(expression.functionLiteral, c.trace.bindingContext, true)
                if (isInlineable) return

                val reportOn = if (expression.parent is KtAnnotatedExpression) expression.parent as KtExpression else expression
                c.trace.report(Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.on(reportOn, expectedType, expressionTypeWithSmartCast))
            }
            return
        } else {
            val expectedType = c.expectedType

            if (expectedType === TypeUtils.NO_EXPECTED_TYPE) return
            if (expectedType === TypeUtils.UNIT_EXPECTED_TYPE) return

            val nullableAnyType = expectedType.builtIns.nullableAnyType
            val anyType = expectedType.builtIns.anyType

            if (anyType == expectedType.lowerIfFlexible() && nullableAnyType == expectedType.upperIfFlexible()) return

            val expectedComposable = expectedType.hasComposableAnnotation()
            val isComposable = expressionType.hasComposableAnnotation()

            if (expectedComposable != isComposable) {
                val reportOn = if (expression.parent is KtAnnotatedExpression) expression.parent as KtExpression else expression
                c.trace.report(Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.on(reportOn, expectedType, expressionTypeWithSmartCast))
            }
            return
        }
    }


    override fun checkEntries(entries: List<KtAnnotationEntry>, actualTargets: List<KotlinTarget>, trace: BindingTrace) {
        val entry = entries.singleOrNull {
            trace.bindingContext.get(BindingContext.ANNOTATION, it)?.isComposableAnnotation ?: false
        }
        if ((entry?.parent as? KtAnnotatedExpression)?.baseExpression is KtObjectLiteralExpression) {
            trace.report(Errors.WRONG_ANNOTATION_TARGET.on(entry, "class which does not extend com.google.r4a.Component"))
        }
    }

    operator fun Composability.plus(rhs: Composability): Composability = if (this > rhs) this else rhs
}
