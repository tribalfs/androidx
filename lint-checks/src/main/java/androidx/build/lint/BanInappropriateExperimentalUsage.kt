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

package androidx.build.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.io.FileNotFoundException
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.kotlin.KotlinUVarargExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.toUElement

/**
 * Prevents usage of experimental annotations outside the groups in which they were defined.
 */
class BanInappropriateExperimentalUsage : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return AnnotationChecker(context)
    }

    private inner class AnnotationChecker(val context: JavaContext) : UElementHandler() {
        val atomicGroupList: List<String> by lazy { loadAtomicLibraryGroupList() }

        override fun visitAnnotation(node: UAnnotation) {
            val signature = node.qualifiedName

            if (DEBUG) {
                if (APPLICABLE_ANNOTATIONS.contains(signature) && node.sourcePsi != null) {
                    (node.uastParent as? UClass)?.let { annotation ->
                        println(
                            "${context.driver.mode}: declared ${annotation.qualifiedName} in " +
                                "${context.project}"
                        )
                    }
                }
            }

            /**
             * If the annotation under evaluation is [kotlin.OptIn], extract and evaluate the
             * annotation(s) referenced by @OptIn - denoted by [kotlin.OptIn.markerClass].
             */
            if (signature != null && signature == KOTLIN_OPT_IN_ANNOTATION) {
                if (DEBUG) {
                    println("Processing $KOTLIN_OPT_IN_ANNOTATION annotation")
                }

                val markerClass: UExpression? = node.findAttributeValue("markerClass")
                if (markerClass != null) {
                    getUElementsFromOptInMarkerClass(markerClass).forEach { uElement ->
                        inspectAnnotation(uElement, node)
                    }
                }

                /**
                 * [kotlin.OptIn] has no effect if [kotlin.OptIn.markerClass] isn't provided.
                 * Similarly, if [getUElementsFromOptInMarkerClass] returns an empty list then
                 * there isn't anything more to inspect.
                 *
                 * In both of these cases we can stop processing here.
                 */
                return
            }

            inspectAnnotation(node.resolveToUElement(), node)
        }

        private fun getUElementsFromOptInMarkerClass(markerClass: UExpression): List<UElement> {
            val elements = ArrayList<UElement?>()

            when (markerClass) {
                is UClassLiteralExpression -> { // opting in to single annotation
                    elements.add(markerClass.toUElement())
                }
                is KotlinUVarargExpression -> { // opting in to multiple annotations
                    val expressions: List<UExpression> = markerClass.valueArguments
                    for (expression in expressions) {
                        val uElement = (expression as UClassLiteralExpression).toUElement()
                        elements.add(uElement)
                    }
                }
                else -> {
                    // do nothing
                }
            }

            return elements.filterNotNull()
        }

        private fun UClassLiteralExpression.toUElement(): UElement? {
            val psiType = this.type
            val psiClass = context.evaluator.getTypeClass(psiType)
            return psiClass.toUElement()
        }

        // If we find an usage of an experimentally-declared annotation, check it.
        private fun inspectAnnotation(annotation: UElement?, node: UAnnotation) {
            if (annotation is UAnnotated) {
                val annotations = context.evaluator.getAllAnnotations(annotation, false)
                if (annotations.any { APPLICABLE_ANNOTATIONS.contains(it.qualifiedName) }) {
                    if (DEBUG) {
                        println(
                            "${context.driver.mode}: used ${node.qualifiedName} in " +
                                "${context.project}"
                        )
                    }
                    verifyUsageOfElementIsWithinSameGroup(
                        context,
                        node,
                        annotation,
                        ISSUE,
                        atomicGroupList
                    )
                }
            }
        }

        private fun loadAtomicLibraryGroupList(): List<String> {
            val fileStream = this::class.java.classLoader
                .getResourceAsStream(ATOMIC_LIBRARY_GROUPS_FILENAME)
                ?: throw FileNotFoundException(
                    "Couldn't find atomic library group file $ATOMIC_LIBRARY_GROUPS_FILENAME" +
                        " within lint-checks.jar")

            val atomicLibraryGroupsString = fileStream.bufferedReader().use { it.readText() }
            if (atomicLibraryGroupsString.isEmpty()) {
                throw RuntimeException("Atomic library group file should not be empty")
            }

            return atomicLibraryGroupsString.split("\n")
        }
    }

    fun verifyUsageOfElementIsWithinSameGroup(
        context: JavaContext,
        usage: UElement,
        annotation: UElement,
        issue: Issue,
        atomicGroupList: List<String>,
    ) {
        val evaluator = context.evaluator
        val usageCoordinates = evaluator.getLibrary(usage) ?: context.project.mavenCoordinate
        val usageGroupId = usageCoordinates?.groupId
        val annotationGroup = evaluator.getLibrary(annotation) ?: return
        val annotationGroupId = annotationGroup.groupId

        val isUsedInSameGroup = annotationGroupId == usageGroupId
        val isUsedInDifferentArtifact = usageCoordinates.artifactId != annotationGroup.artifactId
        val isAtomic = atomicGroupList.contains(usageGroupId)
        if (!isUsedInSameGroup || (isUsedInSameGroup && isUsedInDifferentArtifact && !isAtomic)) {
            if (DEBUG) {
                println(
                    "${context.driver.mode}: report usage of $annotationGroupId in $usageGroupId"
                )
            }
            Incident(context)
                .issue(issue)
                .at(usage)
                .message(
                    "`Experimental` and `RequiresOptIn` APIs may only be used within the " +
                        "same-version group where they were defined."
                )
                .report()
        }
    }

    companion object {
        private const val DEBUG = false

        /**
         * Even though Kotlin's [Experimental] annotation is deprecated in favor of [RequiresOptIn],
         * we still want to check for its use in Lint.
         */
        private const val KOTLIN_EXPERIMENTAL_ANNOTATION = "kotlin.Experimental"

        private const val KOTLIN_OPT_IN_ANNOTATION = "kotlin.OptIn"
        private const val KOTLIN_REQUIRES_OPT_IN_ANNOTATION = "kotlin.RequiresOptIn"
        private const val JAVA_EXPERIMENTAL_ANNOTATION =
            "androidx.annotation.experimental.Experimental"
        private const val JAVA_REQUIRES_OPT_IN_ANNOTATION =
            "androidx.annotation.RequiresOptIn"

        private val APPLICABLE_ANNOTATIONS = listOf(
            JAVA_EXPERIMENTAL_ANNOTATION,
            KOTLIN_EXPERIMENTAL_ANNOTATION,
            JAVA_REQUIRES_OPT_IN_ANNOTATION,
            KOTLIN_REQUIRES_OPT_IN_ANNOTATION,
        )

        // This must match the definition in ExportAtomicLibraryGroupsToTextTask
        const val ATOMIC_LIBRARY_GROUPS_FILENAME = "atomic-library-groups.txt"

        val ISSUE = Issue.create(
            id = "IllegalExperimentalApiUsage",
            briefDescription = "Using experimental API from separately versioned library",
            explanation = "Annotations meta-annotated with `@RequiresOptIn` or `@Experimental` " +
                "may only be referenced from within the same-version group in which they were " +
                "defined.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                BanInappropriateExperimentalUsage::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
