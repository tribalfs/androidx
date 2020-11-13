/*
 * Copyright 2020 The Android Open Source Project
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

@file:Suppress("UnstableApiUsage")

package androidx.compose.ui.lint

import androidx.compose.ui.lint.ModifierDeclarationDetector.Companion.ModifierFactoryReturnType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.UMethod

/**
 * [Detector] that checks functions returning Modifiers for consistency with guidelines.
 *
 * - Modifier factory functions must return Modifier as their type, and not a subclass of Modifier
 * - Modifier factory functions must be defined as an extension on Modifier to allow fluent chaining
 */
class ModifierDeclarationDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            // Ignore functions that do not return
            val returnType = node.returnType ?: return

            // Ignore functions that do not return Modifier or something implementing Modifier
            if (!InheritanceUtil.isInheritor(returnType, ModifierFqn)) return

            val source = node.sourcePsi

            // If this node is a property that is a constructor parameter, ignore it.
            if (source is KtParameter) return

            // Ignore properties in some cases
            if (source is KtProperty) {
                // If this node is inside a class, ignore it.
                if (source.containingClass() != null) return
                // If this node is a var, ignore it.
                if (source.isVar) return
                // If this node is a val with no getter, ignore it.
                if (source.getter == null) return
            }
            if (source is KtPropertyAccessor) {
                // If this node is inside a class, ignore it.
                if (source.property.containingClass() != null) return
                // If this node is a getter on a var, ignore it.
                if (source.property.isVar) return
            }

            node.checkReturnType(context, returnType)
            node.checkReceiver(context)
        }
    }

    companion object {
        val ModifierFactoryReturnType = Issue.create(
            "ModifierFactoryReturnType",
            "Modifier factory functions must return Modifier",
            "Modifier factory functions must return Modifier as their type, and not a " +
                "subtype of Modifier (such as Modifier.Element).",
            Category.CORRECTNESS, 3, Severity.ERROR,
            Implementation(
                ModifierDeclarationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ModifierFactoryExtensionFunction = Issue.create(
            "ModifierFactoryExtensionFunction",
            "Modifier factory functions must be extensions on Modifier",
            "Modifier factory functions must be defined as extension functions on" +
                " Modifier to allow modifiers to be fluently chained.",
            Category.CORRECTNESS, 3, Severity.ERROR,
            Implementation(
                ModifierDeclarationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}

/**
 * @see [ModifierDeclarationDetector.ModifierFactoryExtensionFunction]
 */
private fun UMethod.checkReceiver(context: JavaContext) {
    fun report(lintFix: LintFix? = null) {
        context.report(
            ModifierDeclarationDetector.ModifierFactoryExtensionFunction,
            this,
            context.getNameLocation(this),
            "Modifier factory functions must be extensions on Modifier",
            lintFix
        )
    }

    val source = when (val source = sourcePsi) {
        is KtFunction -> source
        is KtPropertyAccessor -> source.property
        else -> return
    }

    val receiverTypeReference = source.receiverTypeReference

    // No receiver
    if (receiverTypeReference == null) {
        val name = source.nameIdentifier!!.text
        report(
            LintFix.create()
                .replace()
                .name("Add Modifier receiver")
                .range(context.getLocation(source))
                .text(name)
                .with("$ModifierShortName.$name")
                .autoFix()
                .build()
        )
    } else {
        val receiverType = (receiverTypeReference.typeElement as KtUserType).referencedName
        // A receiver that isn't Modifier
        if (receiverType != ModifierShortName) {
            report(
                LintFix.create()
                    .replace()
                    .name("Change receiver to Modifier")
                    .range(context.getLocation(source))
                    .text(receiverType)
                    .with(ModifierShortName)
                    .autoFix()
                    .build()
            )
        }
    }
}

/**
 * @see [ModifierDeclarationDetector.ModifierFactoryReturnType]
 */
private fun UMethod.checkReturnType(context: JavaContext, returnType: PsiType) {
    fun report(lintFix: LintFix? = null) {
        context.report(
            ModifierFactoryReturnType,
            this,
            context.getNameLocation(this),
            "Modifier factory functions must have a return type of Modifier",
            lintFix
        )
    }

    if (returnType.canonicalText == ModifierFqn) return

    val source = sourcePsi
    if (source is KtCallableDeclaration && source.returnTypeString != null) {
        // Function declaration with an explicit return type, such as
        // `fun foo(): Modifier.element = Bar`. Replace the type with `Modifier`.
        report(
            LintFix.create()
                .replace()
                .name("Change return type to Modifier")
                .range(context.getLocation(this))
                .text(source.returnTypeString)
                .with(ModifierShortName)
                .autoFix()
                .build()
        )
        return
    }
    if (source is KtPropertyAccessor) {
        // Getter declaration with an explicit return type on the getter, such as
        // `val foo get(): Modifier.Element = Bar`. Replace the type with `Modifier`.
        val getterReturnType = source.returnTypeReference?.text

        if (getterReturnType != null) {
            report(
                LintFix.create()
                    .replace()
                    .name("Change return type to Modifier")
                    .range(context.getLocation(this))
                    .text(getterReturnType)
                    .with(ModifierShortName)
                    .autoFix()
                    .build()
            )
            return
        }
        // Getter declaration with an implicit return type from the property, such as
        // `val foo: Modifier.Element get() = Bar`. Replace the type with `Modifier`.
        val propertyType = source.property.returnTypeString

        if (propertyType != null) {
            report(
                LintFix.create()
                    .replace()
                    .name("Change return type to Modifier")
                    .range(context.getLocation(source.property))
                    .text(propertyType)
                    .with(ModifierShortName)
                    .autoFix()
                    .build()
            )
            return
        }
    }
    if (source is KtDeclarationWithBody) {
        // Declaration without an explicit return type, such as `fun foo() = Bar`
        // or val foo get() = Bar
        // Replace the `=` with `: Modifier =`
        report(
            LintFix.create()
                .replace()
                .name("Add explicit Modifier return type")
                .range(context.getLocation(this))
                .pattern("[ \\t\\n]+=")
                .with(": $ModifierShortName =")
                .autoFix()
                .build()
        )
        return
    }
}

private const val ModifierFqn = "androidx.compose.ui.Modifier"
private val ModifierShortName = ModifierFqn.split(".").last()

/**
 * TODO: UMethod.returnTypeReference is not available in LINT_API_MIN, so instead use this with a
 * [KtCallableDeclaration].
 * See [org.jetbrains.uast.kotlin.declarations.KotlinUMethod.returnTypeReference] on newer UAST
 * versions.
 */
private val KtCallableDeclaration.returnTypeString: String?
    get() {
        return typeReference?.text
    }
