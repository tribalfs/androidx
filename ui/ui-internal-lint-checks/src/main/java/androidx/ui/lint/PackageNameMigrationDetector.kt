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
import org.jetbrains.uast.UFile

/**
 * Simple lint check that prevents using old package names (map defined in
 * [PackageNameMigrationMap]) after a library has migrated to the new name.
 *
 * TODO: b/160233169 remove this lint check after the migration has finished.
 */
class PackageNameMigrationDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val packageName = node.packageName

            if (packageName in PackageNameMigrationMap) {
                val newPackageName = PackageNameMigrationMap[packageName]
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "The package name '$packageName' has been migrated to '$newPackageName', " +
                            "please update the package name of this file accordingly."
                )
            }
        }
    }
    companion object {
        private val PackageNameMigrationMap: Map<String, String> = mapOf(
            // placeholder package name used in PackageNameMigrationDetectorTest, since the
            // migration has not started yet
            "androidx.ui.foo" to "androidx.compose.foo"
        )

        val ISSUE = Issue.create(
            "PackageNameMigration",
            "Using an old package name that has recently been migrated to androidx.compose",
            "As part of a large migration from androidx.ui to androidx.compose, package names " +
                    "across all libraries are being refactored. If you are seeing this Lint " +
                    "error, you are adding new files to the old package name, once the rest of " +
                    "the library has migrated to the new package name.",
            Category.PERFORMANCE, 5, Severity.ERROR,
            Implementation(
                PackageNameMigrationDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
