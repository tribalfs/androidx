/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.build

import androidx.build.gitclient.Commit
import androidx.build.gitclient.GitClientImpl
import androidx.build.gitclient.GitCommitRange
import androidx.build.jetpad.LibraryBuildInfoFile
import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.ArrayList

/**
 * This task generates a library build information file containing the artifactId, groupId, and
 * version of public androidx dependencies and release checklist of the library for consumption
 * by the Jetpack Release Service (JetPad).
 */
abstract class CreateLibraryBuildInfoFileTask : DefaultTask() {
    init {
        group = "Help"
        description = "Generates a file containing library build information serialized to json"
    }

    @get:OutputFile
    abstract val outputFile: Property<File>

    @get:Input
    abstract val artifactId: Property<String>

    @get:Input
    abstract val groupId: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:Optional
    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:Input
    abstract val projectDir: Property<String>

    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val groupIdRequiresSameVersion: Property<Boolean>

    @get:Input
    abstract val groupZipPath: Property<String>

    @get:Input
    abstract val projectZipPath: Property<String>

    /**
     * the local project directory without the full framework/support root directory path
     */
    @get:Input
    abstract val projectSpecificDirectory: Property<String>

    private fun writeJsonToFile(info: LibraryBuildInfoFile) {
        val resolvedOutputFile: File = outputFile.get()
        val outputDir = resolvedOutputFile.parentFile
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw RuntimeException(
                    "Failed to create " +
                        "output directory: $outputDir"
                )
            }
        }
        if (!resolvedOutputFile.exists()) {
            if (!resolvedOutputFile.createNewFile()) {
                throw RuntimeException(
                    "Failed to create output dependency dump file: $outputFile"
                )
            }
        }

        // Create json object from the artifact instance
        val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
        val serializedInfo: String = gson.toJson(info)
        resolvedOutputFile.writeText(serializedInfo)
    }

    private fun resolveAndCollectDependencies(): LibraryBuildInfoFile {
        val libraryBuildInfoFile = LibraryBuildInfoFile()
        libraryBuildInfoFile.artifactId = artifactId.get()
        libraryBuildInfoFile.groupId = groupId.get()
        libraryBuildInfoFile.version = version.get()
        libraryBuildInfoFile.path = projectDir.get()
        libraryBuildInfoFile.sha = commit.get()
        libraryBuildInfoFile.groupIdRequiresSameVersion = groupIdRequiresSameVersion.get()
        libraryBuildInfoFile.groupZipPath = groupZipPath.get()
        libraryBuildInfoFile.projectZipPath = projectZipPath.get()
        val libraryDependencies = ArrayList<LibraryBuildInfoFile.Dependency>()
        val checks = ArrayList<LibraryBuildInfoFile.Check>()
        libraryBuildInfoFile.checks = checks
        project.configurations.filter {
            it.name == "releaseRuntimeElements"
        }.forEach { configuration ->
            configuration.allDependencies.forEach { dep ->
                // For Kotlin libraries, populate the Kotlin version. Note that
                // we assume all of androidx uses the same version of the Kotlin
                // stdlib.
                if (dep.group.toString().startsWith("org.jetbrains.kotlin")) {
                    libraryBuildInfoFile.kotlinVersion = kotlinVersion.getOrNull()
                }
                // Only consider androidx dependencies
                if (dep.group != null &&
                    dep.group.toString().startsWith("androidx.") &&
                    !dep.group.toString().startsWith("androidx.test")
                ) {
                    val androidXPublishedDependency = LibraryBuildInfoFile().Dependency()
                    androidXPublishedDependency.artifactId = dep.name.toString()
                    androidXPublishedDependency.groupId = dep.group.toString()
                    androidXPublishedDependency.version = dep.version.toString()
                    androidXPublishedDependency.isTipOfTree = dep is ProjectDependency
                    addDependencyToListIfNotAlreadyAdded(
                        libraryDependencies,
                        androidXPublishedDependency
                    )
                }
            }
        }
        libraryBuildInfoFile.dependencies = libraryDependencies
        return libraryBuildInfoFile
    }

    private fun addDependencyToListIfNotAlreadyAdded(
        dependencyList: ArrayList<LibraryBuildInfoFile.Dependency>,
        dependency: LibraryBuildInfoFile.Dependency
    ) {
        for (existingDependency in dependencyList) {
            if (existingDependency.groupId == dependency.groupId &&
                existingDependency.artifactId == dependency.artifactId &&
                existingDependency.version == dependency.version &&
                existingDependency.isTipOfTree == dependency.isTipOfTree
            ) {
                return
            }
        }
        dependencyList.add(dependency)
    }

    /**
     * Task: createLibraryBuildInfoFile
     * Iterates through each configuration of the project and builds the set of all dependencies.
     * Then adds each dependency to the Artifact class as a project or prebuilt dependency.  Finally,
     * writes these dependencies to a json file as a json object.
     */
    @TaskAction
    fun createLibraryBuildInfoFile() {
        val resolvedArtifact = resolveAndCollectDependencies()
        writeJsonToFile(resolvedArtifact)
    }

    companion object {
        const val TASK_NAME = "createLibraryBuildInfoFiles"

        fun setup(project: Project, extension: AndroidXExtension):
            TaskProvider<CreateLibraryBuildInfoFileTask> {
                return project.tasks.register(
                    TASK_NAME,
                    CreateLibraryBuildInfoFileTask::class.java
                ) {
                    val group = project.group.toString()
                    val name = project.name.toString()
                    it.outputFile.set(
                        File(
                            project.getBuildInfoDirectory(),
                            "${group}_${name}_build_info.txt"
                        )
                    )
                    it.artifactId.set(name)
                    it.groupId.set(group)
                    it.version.set(project.version.toString())
                    it.kotlinVersion.set(androidx.build.dependencies.kotlinVersion)
                    it.projectDir.set(
                        project.projectDir.absolutePath.removePrefix(
                            project.getSupportRootFolder().absolutePath
                        )
                    )
                    it.commit.set(
                        project.provider({
                            project.getFrameworksSupportCommitShaAtHead()
                        })
                    )
                    it.groupIdRequiresSameVersion.set(extension.mavenGroup?.requireSameVersion)
                    it.groupZipPath.set(project.getGroupZipPath())
                    it.projectZipPath.set(project.getProjectZipPath())

                    // Note:
                    // `project.projectDir.toString().removePrefix(project.rootDir.toString())`
                    // does not work because the project rootDir is not guaranteed to be a
                    // substring of the projectDir
                    it.projectSpecificDirectory.set(
                        project.projectDir.absolutePath.removePrefix(
                            project.getSupportRootFolder().absolutePath
                        )
                    )
                }
            }

        /* For androidx release notes, the most common use case is to track and publish the last sha
         * of the build that is released.  Thus, we use frameworks/support to get the sha
         */
        private fun Project.getFrameworksSupportCommitShaAtHead(): String {
            val commitList: List<Commit> = GitClientImpl(project.getSupportRootFolder(), logger)
                .getGitLog(
                    GitCommitRange(
                        fromExclusive = "",
                        untilInclusive = "HEAD",
                        n = 1
                    ),
                    keepMerges = true,
                    fullProjectDir = getSupportRootFolder()
                )
            if (commitList.isEmpty()) {
                throw RuntimeException("Failed to find git commit for HEAD!")
            }
            return commitList.first().sha
        }
    }
}
