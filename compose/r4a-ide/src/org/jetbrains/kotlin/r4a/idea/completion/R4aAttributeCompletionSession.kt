package org.jetbrains.kotlin.r4a.idea.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.completion.CompletionSessionConfiguration
import org.jetbrains.kotlin.idea.completion.ToFromOriginalFileMapper
import org.jetbrains.kotlin.idea.completion.isArtificialImportAliasedDescriptor
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtxAttribute
import org.jetbrains.kotlin.psi.KtxElement
import org.jetbrains.kotlin.psi.KtxLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.r4a.analysis.R4AWritableSlices
import org.jetbrains.kotlin.r4a.idea.AttributeInfo
import org.jetbrains.kotlin.r4a.idea.AttributeInfoExtractor
import org.jetbrains.kotlin.r4a.idea.parentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.DescriptorUtils

class R4aAttributeCompletionSession(
    configuration: CompletionSessionConfiguration,
    parameters: CompletionParameters,
    toFromOriginalFileMapper: ToFromOriginalFileMapper,
    resultSet: CompletionResultSet
) : BaseR4aCompletionSession(configuration, parameters, toFromOriginalFileMapper, resultSet) {

    private val elementExpr = cursorElement.parentOfType<KtxElement>() ?: error("no ktx element found")
    private val usedAttributes = elementExpr.getChildrenOfType<KtxAttribute>()
    private val hasChildrenLambda = elementExpr.getChildrenOfType<KtxLambdaExpression>().isNotEmpty()
    private val usedAttributesNameSet = usedAttributes.mapNotNull { attr -> attr.key?.getIdentifier()?.text }.toSet()

    private val nullableTagInfo = bindingContext.get(R4AWritableSlices.KTX_TAG_INFO, elementExpr)

    fun isValid() = nullableTagInfo != null

    private val tagInfo get() = nullableTagInfo ?: error("no tag info found on element. Call isValid() before using this class")

    private val attributeInfoExtractor = AttributeInfoExtractor(
        file = file,
        visibilityFilter = { it.isVisibleDescriptor() },
        declarationTranslator = { toFromOriginalFileMapper.toSyntheticFile(it) },
        tagInfo = nullableTagInfo
    )

    private fun AttributeInfo.constructLookupElement(): LookupElement? {
        val extensionType = extensionType
        val contributingDescriptor = contributingDescriptor

        if (usedAttributesNameSet.contains(name)) return null

        // if the user has defined a chilren lambda already, there is no need to add a children attribute to the
        // autocomplete list
        if (hasChildrenLambda && isChildren) return null

        // NOTE(lmr): if the attribute is a setter function, the parameter name can often be useful. we should consider using its
        // name somewhere in here: (attr.descriptor as SimpleFunctionDescriptor).valueParameters.single().name

        var isImmediate = isImmediate

        var myTailText = "=..."

        if (isExtension && extensionType != null) {
            if (extensionType == tagInfo.instanceType) {
                isImmediate = true
            }
            val klass = SHORT_NAMES_RENDERER.renderType(extensionType)
            myTailText += " (extension on $klass)"
        }

        if (!isImmediate && !isExtension && contributingDescriptor != null) {
            val klass = SHORT_NAMES_RENDERER.renderClassifierName(contributingDescriptor)
            myTailText += " (from $klass)"
        }

        if (isChildren) {
            myTailText += " (@Children)"
        }
        if (isRequired) {
            myTailText += " (required)"
        }
        if (isPivotal) {
            myTailText += " (pivotal)"
        }

        val renderer = object : LookupElementRenderer<LookupElement>() {
            override fun renderElement(element: LookupElement?, presentation: LookupElementPresentation?) {
                if (presentation == null) return
                with(presentation) {
                    itemText = name
                    isItemTextBold = when {
                        isImmediate || isRequired -> true
                        else -> false
                    }
                    itemTextForeground = when {
                        isPivotal -> JBColor.RED
                        isRequired -> JBColor.BLUE
                        isImmediate -> JBColor.BLACK
                        else -> JBColor.darkGray
                    }
                    typeText = SHORT_NAMES_RENDERER.renderType(type)
                    if (isExtension) {
                        appendTailTextItalic(myTailText, true)
                    } else {
                        appendTailText(myTailText, true)
                    }
                }
            }
        }

        return LookupElementBuilder
            .create(this, name)
            .withRenderer(renderer)
            .withInsertHandler(attributeInsertionHandler)
            .apply {
                putUserData(DESCRIPTOR, descriptor)
                putUserData(Weighers.ATTRIBUTE_INFO_KEY, this@constructLookupElement)
            }
    }

    override fun doComplete() {
        attributeInfoExtractor.extract(tagInfo.referrableDescriptor) { attributeInfo ->
            collector.addElements(attributeInfo.mapNotNull { it.constructLookupElement() }.asIterable())
            flushToResultSet()
        }
    }

    override fun createSorter(): CompletionSorter {
        return super.createSorter()
            .weighAfter(
                "prefix",
                Weighers.IsRequiredAttribute,
                Weighers.IsImmediateAttribute,
                Weighers.IsImported,
                Weighers.IsChildAttribute
            )
    }

    private val attributeInsertionHandler = InsertHandler<LookupElement> { context, item ->
        val document = context.document
        val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
        psiDocumentManager.commitAllDocuments()
        item.getUserData(DESCRIPTOR)?.let { descriptor ->
            if (DescriptorUtils.isTopLevelDeclaration(descriptor) && !descriptor.isArtificialImportAliasedDescriptor) {
                ImportInsertHelper.getInstance(context.project)
                    .importDescriptor(context.file as KtFile, descriptor)
            }
        }
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

        val token = context.file.findElementAt(context.startOffset) ?: return@InsertHandler
        val ktxAttribute = token.parent.parent as? KtxAttribute ?: return@InsertHandler

        // If the attribute has an equals sign already, then we don't need to insert it...
        if (ktxAttribute.node.findChildByType(KtTokens.EQ) != null) return@InsertHandler

        // NOTE: If we introduce attribute "punning", we will need to do the right thing here...

        val tailOffset = context.tailOffset
        val moveCaret = context.editor.caretModel.offset == tailOffset
        val textToInsert = "="
        document.insertString(tailOffset, textToInsert)

        if (moveCaret) {
            // move after the '='
            context.editor.caretModel.moveToOffset(tailOffset + 1)

            // since they just created a new attribute and need to fill out the expression, we might as well open up the autocomplete!
            AutoPopupController.getInstance(context.project)?.scheduleAutoPopup(context.editor)
        }
    }

    companion object {
        val DESCRIPTOR = Key<DeclarationDescriptor>("r4a.descriptor")
        private val SHORT_NAMES_RENDERER = DescriptorRenderer.SHORT_NAMES_IN_TYPES.withOptions { parameterNamesInFunctionalTypes = false }
    }
}

