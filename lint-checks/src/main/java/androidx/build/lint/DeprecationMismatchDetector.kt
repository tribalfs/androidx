/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.build.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiComment
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.getContainingUClass

class DeprecationMismatchDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UDeclaration::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return DeprecationChecker(context)
    }

    private inner class DeprecationChecker(val context: JavaContext) : UElementHandler() {
        override fun visitDeclaration(node: UDeclaration) {
            // This check is only applicable for Java, the Kotlin @Deprecated has a message field
            if (isKotlin(node.lang)) return

            // This check is for API elements, not anonymous class declarations
            if (node is UAnonymousClass) return
            if (node is UMethod && node.name == "<anon-init>") return

            // Not necessary if the element isn't public API
            if (!applicableVisibilities.contains(node.visibility)) return

            // Check if @deprecated and @Deprecated don't match
            val hasDeprecatedDocTag = node.comments.any { it.text.contains("@deprecated") }
            val hasDeprecatedAnnotation = node.hasAnnotation(DEPRECATED_ANNOTATION)
            if (hasDeprecatedDocTag == hasDeprecatedAnnotation) return

            // Proto-generated files are not part of the public API surface
            if (node.containingFile.children.filterIsInstance<PsiComment>().any {
                it.text.contains("Generated by the protocol buffer compiler.  DO NOT EDIT!")
            }) return

            // Methods that override deprecated methods can inherit docs from the original method
            if (node is UMethod && node.hasAnnotation(OVERRIDE_ANNOTATION) &&
                (node.comments.isEmpty() ||
                    node.comments.any { it.text.contains("@inheritDoc") })) return

            // @RestrictTo elements aren't part of the public API surface
            if (node.hasAnnotation(RESTRICT_TO_ANNOTATION) ||
                node.getContainingUClass()?.hasAnnotation(RESTRICT_TO_ANNOTATION) == true) return

            // The mismatch is in a public API, report the error
            val baseIncident = Incident(context)
                .issue(ISSUE)
                .location(context.getLocation(node, LocationType.NAME))
                .scope(node)

            val incident = if (hasDeprecatedAnnotation) {
                // No auto-fix for this case since developers should write a comment with details
                baseIncident
                    .message("Items annotated with @Deprecated must have a @deprecated doc tag")
            } else {
                val fix = fix()
                    .name("Annotate with @Deprecated")
                    .annotate(DEPRECATED_ANNOTATION, context, node)
                    .autoFix()
                    .build()

                baseIncident
                    .fix(fix)
                    .message("Items with a @deprecated doc tag must be annotated with @Deprecated")
            }

            context.report(incident)
        }
    }

    companion object {
        val ISSUE = Issue.create(
            "DeprecationMismatch",
            "@Deprecated (annotation) and @deprecated (doc tag) must go together",
            "A deprecated API should both be annotated with @Deprecated and have a " +
                "@deprecated doc tag.",
            Category.CORRECTNESS, 5, Severity.ERROR,
            Implementation(DeprecationMismatchDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private const val DEPRECATED_ANNOTATION = "java.lang.Deprecated"
        private const val RESTRICT_TO_ANNOTATION = "androidx.annotation.RestrictTo"
        private const val OVERRIDE_ANNOTATION = "java.lang.Override"
        private val applicableVisibilities = listOf(UastVisibility.PUBLIC, UastVisibility.PROTECTED)
    }
}
