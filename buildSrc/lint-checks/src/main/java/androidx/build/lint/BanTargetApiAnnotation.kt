/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.detector.api.AnnotationUsageType

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import java.util.Collections

class BanTargetApiAnnotation : Detector(), Detector.UastScanner {

    override fun applicableAnnotations(): List<String>? {
        return Collections.singletonList("android.annotation.TargetApi")
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        if (type == AnnotationUsageType.METHOD_CALL) {
            context.report(ISSUE, annotation, context.getNameLocation(annotation),
                    "Uses @TargetApi annotation")
        }
    }

    companion object {
        val ISSUE = Issue.create("BanTargetApiAnnotation",
                "Uses @TargetApi annotation",
                "Use of @TargetApi annotation is not allowed, please consider " +
                        "using the @RequiresApi annotation instead.",
                Category.CORRECTNESS, 5, Severity.ERROR,
                Implementation(BanTargetApiAnnotation::class.java, Scope.JAVA_FILE_SCOPE))
    }
}
