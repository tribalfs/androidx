/*
 * Copyright 2018 The Android Open Source Project
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

// This file creates tasks for generating documentation using Dokka
// TODO: after DiffAndDocs and Doclava are fully obsoleted and removed, rename this from Dokka to just Docs
package androidx.build.dokka

import java.io.File
import androidx.build.DiffAndDocs
import androidx.build.getBuildId
import androidx.build.getDistributionDirectory
import androidx.build.SupportLibraryExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.apply
import org.jetbrains.dokka.gradle.DokkaAndroidPlugin
import org.jetbrains.dokka.gradle.DokkaAndroidTask
import org.jetbrains.dokka.gradle.PackageOptions

object Dokka {
    fun createDocsTask(
        taskName: String,
        project: Project,
        hiddenPackages: List<String>,
        archiveTaskName: String
    ) {
        project.apply<DokkaAndroidPlugin>()
        if (project.name != "support" && project.name != "docs-runner") {
            throw Exception("Illegal project passed to createDocsTask: " + project.name)
        }
        val docsTask = project.tasks.create(taskName, DokkaAndroidTask::class.java) { docsTask ->
            docsTask.moduleName = project.name
            docsTask.outputDirectory = File(project.buildDir, taskName).absolutePath
            docsTask.outputFormat = "dac"
            docsTask.outlineRoot = "androidx/"
            docsTask.dacRoot = "/reference/kotlin"
            docsTask.moduleName = ""
            for (hiddenPackage in hiddenPackages) {
                val opts = PackageOptions()
                opts.prefix = hiddenPackage
                opts.suppress = true
                docsTask.perPackageOptions.add(opts)
            }
        }

        project.tasks.create(archiveTaskName, Zip::class.java) { zipTask ->
            zipTask.dependsOn(docsTask)
            zipTask.description = "Generates documentation artifact for pushing to " +
                "developer.android.com"
            zipTask.from(docsTask.outputDirectory) { copySpec ->
                copySpec.into("reference/kotlin")
            }
            zipTask.baseName = taskName
            zipTask.version = getBuildId()
            zipTask.destinationDir = project.getDistributionDirectory()
        }
    }

    fun registerAndroidProject(
        project: Project,
        library: LibraryExtension,
        extension: SupportLibraryExtension
    ) {
        DiffAndDocs.get(project).registerPrebuilts(extension)
        DokkaPublicDocs.registerProject(project, extension)
        DokkaSourceDocs.registerAndroidProject(project, library, extension)
    }

    fun registerJavaProject(
        project: Project,
        extension: SupportLibraryExtension
    ) {
        DiffAndDocs.get(project).registerPrebuilts(extension)
        DokkaPublicDocs.registerProject(project, extension)
        DokkaSourceDocs.registerJavaProject(project, extension)
    }
}
