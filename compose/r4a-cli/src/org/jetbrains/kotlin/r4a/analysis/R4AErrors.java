package org.jetbrains.kotlin.r4a.analysis;

import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor;
import org.jetbrains.kotlin.diagnostics.*;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtSimpleNameExpression;
import org.jetbrains.kotlin.psi.KtxAttribute;
import org.jetbrains.kotlin.psi.KtxElement;
import org.jetbrains.kotlin.types.KotlinType;

import java.util.Collection;

import static org.jetbrains.kotlin.diagnostics.Severity.ERROR;
import static org.jetbrains.kotlin.diagnostics.Severity.WARNING;

public interface R4AErrors {
    DiagnosticFactory0<PsiElement> DUPLICATE_ATTRIBUTE = DiagnosticFactory0.create(ERROR);
    DiagnosticFactory0<PsiElement> OPEN_COMPONENT = DiagnosticFactory0.create(ERROR);

    DiagnosticFactory3<KtxAttribute, DeclarationDescriptor, String, KotlinType> UNRESOLVED_ATTRIBUTE_KEY = DiagnosticFactory3.create(ERROR);
    DiagnosticFactory2<KtElement, KotlinType, Collection<KotlinType>> MISMATCHED_ATTRIBUTE_TYPE = DiagnosticFactory2.create(ERROR);
    DiagnosticFactory1<KtElement, Collection<KotlinType>> UNRESOLVED_CHILDREN = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory1<KtxAttribute, SimpleFunctionDescriptor>
            MISMATCHED_ATTRIBUTE_TYPE_NO_SINGLE_PARAM_SETTER_FNS = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory1<KtxElement, Collection<DeclarationDescriptor>> MISSING_REQUIRED_ATTRIBUTES = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory2<KtElement, KotlinType, Collection<KotlinType>> INVALID_TAG_TYPE = DiagnosticFactory2.create(ERROR);
    DiagnosticFactory1<KtElement, Collection<KotlinType>> INVALID_TAG_DESCRIPTOR = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory1<KtElement, String> SVC_INVOCATION = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory1<KtElement, String> CHILDREN_INVOCATION = DiagnosticFactory1.create(WARNING);
    DiagnosticFactory1<KtElement, String> CHILDREN_NOT_COMPOSABLE = DiagnosticFactory1.create(WARNING);
    DiagnosticFactory2<KtElement, String, String> NON_COMPOSABLE_INVOCATION = DiagnosticFactory2.create(WARNING);
    DiagnosticFactory0<KtElement> SUSPEND_FUNCTION_USED_AS_SFC = DiagnosticFactory0.create(ERROR);
    DiagnosticFactory0<PsiElement> KTX_IN_NON_COMPOSABLE = DiagnosticFactory0.create(WARNING);
    DiagnosticFactory0<KtElement> INVALID_TYPE_SIGNATURE_SFC = DiagnosticFactory0.create(ERROR);
    DiagnosticFactory0<KtxElement> UNRESOLVED_TAG = DiagnosticFactory0.create(ERROR);
    DiagnosticFactory1<KtElement, String> CHILDREN_ATTR_USED_AS_BODY_AND_KEYED_ATTRIBUTE = DiagnosticFactory1.create(ERROR);
    DiagnosticFactory0<KtxElement> CHILDREN_PROVIDED_BUT_NO_CHILDREN_DECLARED = DiagnosticFactory0.create(ERROR);
    DiagnosticFactory0<KtxElement> MISSING_REQUIRED_CHILDREN = DiagnosticFactory0.create(ERROR);


    @SuppressWarnings("UnusedDeclaration")
    Object _initializer = new Object() {
        {
            Errors.Initializer.initializeFactoryNames(R4AErrors.class);
        }
    };

}
