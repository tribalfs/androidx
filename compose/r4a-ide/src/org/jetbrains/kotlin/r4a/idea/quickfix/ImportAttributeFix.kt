package org.jetbrains.kotlin.r4a.idea.quickfix

import com.intellij.codeInsight.ImportFilter
import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.util.getResolveScope
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.getFileResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.r4a.R4aUtils
import org.jetbrains.kotlin.r4a.analysis.R4AWritableSlices
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.scopes.utils.findFunction
import org.jetbrains.kotlin.resolve.scopes.utils.findVariable
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.isUnit

class ImportAttributeFix(expression: KtSimpleNameExpression) : R4aImportFix(expression) {
    private val ktxAttribute = expression.parent as KtxAttribute

    override fun computeSuggestions(): List<ImportVariant> {
        val ktxElement = ktxAttribute.parent as? KtxElement ?: return emptyList()
        if (!ktxAttribute.isValid) return emptyList()
        if (ktxAttribute.containingFile !is KtFile) return emptyList()

        val file = ktxAttribute.containingKtFile

        val name = ktxAttribute.name ?: return emptyList()

        val bindingContext = ktxAttribute.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)

        val tagInfo = bindingContext[R4AWritableSlices.KTX_TAG_INFO, ktxElement] ?: return emptyList()
        val expectedTypeInfo = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, ktxAttribute.value ?: ktxAttribute.key]

        val instanceType = tagInfo.instanceType ?: return emptyList()

        val searchScope = getResolveScope(file)

        val resolutionFacade = file.getResolutionFacade()

        val topLevelScope = resolutionFacade.getFileResolutionScope(file)

        val callTypeAndReceiver = CallTypeAndReceiver.DOT(ktxElement)

        fun isVisible(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(
                    ktxAttribute,
                    callTypeAndReceiver.receiver as? KtExpression,
                    bindingContext,
                    resolutionFacade
                )
            }

            return true
        }

        fun isValid(descriptor: DeclarationDescriptor): Boolean {
            return when (descriptor) {
                is FunctionDescriptor -> when {
                    descriptor.valueParameters.size != 1 -> false
                    descriptor.returnType?.isUnit() != true -> false
                    R4aUtils.propertyNameFromSetterMethod(descriptor.name.asString()) == name -> true
                    else -> false
                }
                else -> false
            }
        }

        val type = expectedTypeInfo?.type

        fun isCorrectType(descriptor: DeclarationDescriptor): Boolean {
            if (type == null) return true
            return when (descriptor) {
                is FunctionDescriptor -> type.isSubtypeOf(descriptor.valueParameters.first().type)
                else -> false
            }
        }

        fun shouldShow(descriptor: DeclarationDescriptor): Boolean {
            if (!ImportFilter.shouldImport(file, descriptor.fqNameSafe.asString())) return false

            if (isAlreadyImported(descriptor, topLevelScope, descriptor.fqNameSafe)) return false

            return true
        }

        val indicesHelper = KotlinIndicesHelper(resolutionFacade, searchScope, ::isVisible, file = file)

        val candidates = indicesHelper.getCallableTopLevelExtensions(
            callTypeAndReceiver = callTypeAndReceiver,
            receiverTypes = listOf(instanceType),
            nameFilter = { true },
            declarationFilter = { true }
        )
            .asSequence()
            .filter { isValid(it) }
            .filter { isVisible(it) }
            .filter { shouldShow(it) }
            .toList()

        val filteredByTypeCandidates = candidates.filter { isCorrectType(it) }

        val finalCandidates = if (filteredByTypeCandidates.isNotEmpty()) filteredByTypeCandidates else candidates

        return finalCandidates.map { ImportVariant.forAttribute(it) }
    }

    private fun isAlreadyImported(target: DeclarationDescriptor, topLevelScope: LexicalScope, targetFqName: FqName): Boolean {
        val name = target.name
        return when (target) {
            is ClassifierDescriptorWithTypeParameters -> {
                val classifier = topLevelScope.findClassifier(name, NoLookupLocation.FROM_IDE)
                classifier?.importableFqName == targetFqName
            }

            is FunctionDescriptor ->
                topLevelScope.findFunction(name, NoLookupLocation.FROM_IDE) { it.importableFqName == targetFqName } != null

            is PropertyDescriptor ->
                topLevelScope.findVariable(name, NoLookupLocation.FROM_IDE) { it.importableFqName == targetFqName } != null

            else -> false
        }
    }


    companion object MyFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtSimpleNameExpression ?: return null
            val attribute = element.parent as? KtxAttribute ?: return null
            if (element != attribute.key) return null
            return ImportAttributeFix(element).apply { collectSuggestions() }
        }
    }
}