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

import androidx.build.AndroidXExtension
import androidx.build.DiffAndDocs
import androidx.build.dependencies.GUAVA_VERSION
import androidx.build.getBuildId
import androidx.build.getDistributionDirectory
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.apply
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaAndroidPlugin
import org.jetbrains.dokka.gradle.DokkaAndroidTask
import org.jetbrains.dokka.gradle.PackageOptions
import java.io.File
import java.net.URL

object Dokka {
    fun generatorTaskNameForType(docsType: String, language: String = ""): String {
        val formattedLangauage = language.toLowerCase().capitalize()
        return "dokka${formattedLangauage}${docsType}Docs"
    }

    fun archiveTaskNameForType(docsType: String): String {
        return "dist${docsType}DokkaDocs"
    }
    fun createDocsTask(
        docsType: String, // "public" or "tipOfTree"
        project: Project,
        hiddenPackages: List<String>
    ) {
        val archiveTaskName = archiveTaskNameForType(docsType)
        project.apply<DokkaAndroidPlugin>()
        // We don't use the `dokka` task, but it normally appears in `./gradlew tasks`
        // so replace it with a new task that doesn't show up and doesn't do anything
        project.tasks.replace("dokka")
        if (project.name != "support" && project.name != "docs-runner") {
            throw Exception("Illegal project passed to createDocsTask: " + project.name)
        }

        val kotlinDocsTask = createDokkaTask(project,
            docsType,
            hiddenPackages,
            "Kotlin",
            "dac",
            "/reference/kotlin")
        val javaDocsTask = createDokkaTask(project,
            docsType,
            hiddenPackages,
            "Java",
            "dac-as-java",
            "/reference/")

        project.tasks.register(archiveTaskName, Zip::class.java) { zipTask ->

            zipTask.dependsOn(javaDocsTask)
            zipTask.from(javaDocsTask.map { it.outputDirectory }) { copySpec ->
                copySpec.into("reference/java")
            }

            zipTask.dependsOn(kotlinDocsTask)
            zipTask.from(kotlinDocsTask.map { it.outputDirectory }) { copySpec ->
                copySpec.into("reference/kotlin")
            }

            val buildId = getBuildId()
            val archiveBaseName = generatorTaskNameForType(docsType)
            zipTask.archiveBaseName.set(archiveBaseName)
            zipTask.archiveVersion.set(buildId)
            zipTask.destinationDirectory.set(project.getDistributionDirectory())
            val filePath = "${project.getDistributionDirectory().canonicalPath}/"
            val fileName = "$archiveBaseName-$buildId.zip"
            zipTask.description = "Zips $docsType documentation (generated via " +
                "Dokka in the style of d.android.com) into ${filePath + fileName}"
            zipTask.group = JavaBasePlugin.DOCUMENTATION_GROUP
        }
    }

    private fun createDokkaTask(
        project: Project,
        docsType: String,
        hiddenPackages: List<String>,
        language: String,
        outputFormat: String,
        dacRoot: String
    ): TaskProvider<DokkaAndroidTask> {

        val docTaskName = generatorTaskNameForType(docsType, language)

        val guavaDocLink = DokkaConfiguration.ExternalDocumentationLink.Builder().apply {
            this.url = URL("https://guava.dev/releases/$GUAVA_VERSION/api/docs/")
            // Guava documentation doesn't have the necessary package-list file to provide the packages
            // to Dokka so we have to host a file internally as a workaround
            this.packageListUrl = project.projectDir.toPath()
                .resolve("guava-package.list").toUri().toURL()
        }.build()

        return project.tasks.register(docTaskName, DokkaAndroidTask::class.java) { task ->
            task.moduleName = project.name
            task.outputDirectory = File(project.buildDir, docTaskName).absolutePath
            task.description = "Generates $docsType $language documentation in the style of " +
                    "d.android.com.  Places docs in ${task.outputDirectory}"
            task.outputFormat = outputFormat
            task.outlineRoot = "androidx/"
            task.dacRoot = dacRoot
            task.moduleName = ""
            task.externalDocumentationLinks.add(guavaDocLink)
            for (hiddenPackage in hiddenPackages) {
                val opts = PackageOptions()
                opts.prefix = hiddenPackage
                opts.suppress = true
                task.perPackageOptions.add(opts)
            }
        }
    }

    fun Project.configureAndroidProjectForDokka(
        library: LibraryExtension,
        extension: AndroidXExtension
    ) {
        afterEvaluate {
            if (name != "docs-runner") {
                DiffAndDocs.get(this).registerAndroidProject(library, extension)
            }

            DokkaPublicDocs.registerProject(this, extension)
            DokkaSourceDocs.registerAndroidProject(this, library, extension)
        }
    }

    fun Project.configureJavaProjectForDokka(extension: AndroidXExtension) {
        afterEvaluate {
            if (name != "docs-runner") {
                DiffAndDocs.get(this).registerJavaProject(this, extension)
            }
            DokkaPublicDocs.registerProject(this, extension)
            DokkaSourceDocs.registerJavaProject(this, extension)
        }
    }
}
