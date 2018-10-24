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

package androidx.build.metalava

import androidx.build.AndroidXPlugin.Companion.BUILD_ON_SERVER_TASK
import androidx.build.SupportLibraryExtension
import androidx.build.androidJarFile
import androidx.build.checkapi.getCurrentApiFile
import androidx.build.checkapi.getRequiredCompatibilityApiFile
import androidx.build.checkapi.hasApiFolder
import androidx.build.checkapi.hasApiTasks
import androidx.build.java.JavaCompileInputs
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.getPlugin

object Metalava {
    private fun Project.createMetalavaConfiguration(): Configuration {
        return configurations.create("metalava") {
            val dependency = dependencies.create("com.android:metalava:1.1.2-SNAPSHOT:shadow@jar")
            it.dependencies.add(dependency)
        }
    }

    fun registerAndroidProject(
        project: Project,
        library: LibraryExtension,
        extension: SupportLibraryExtension
    ) {
        if (!hasApiTasks(project, extension)) {
            return
        }

        val metalavaConfiguration = project.createMetalavaConfiguration()

        library.libraryVariants.all { variant ->
            if (variant.name == "release") {
                if (!project.hasApiFolder()) {
                    project.logger.info(
                        "Project ${project.name} doesn't have an api folder, ignoring API tasks.")
                    return@all
                }

                val apiTxt = project.getCurrentApiFile()

                val javaInputs = JavaCompileInputs.fromLibraryVariant(library, variant)

                val checkApi = project.tasks.create("checkApi", CheckApiTask::class.java) { task ->
                    task.configuration = metalavaConfiguration
                    task.apiTxtFile = apiTxt
                    task.dependsOn(metalavaConfiguration)
                }
                applyInputs(javaInputs, checkApi)

                project.tasks.getByName("check").dependsOn(checkApi)
                project.rootProject.tasks.getByName(BUILD_ON_SERVER_TASK).dependsOn(checkApi)

                val updateApi = project.tasks.create("updateApi", UpdateApiTask::class.java) { task ->
                    task.configuration = metalavaConfiguration
                    task.apiTxtFile = apiTxt
                    task.dependsOn(metalavaConfiguration)
                }
                applyInputs(javaInputs, updateApi)
            }
        }
    }

    fun registerJavaProject(
        project: Project,
        extension: SupportLibraryExtension
    ) {
        if (!hasApiTasks(project, extension)) {
            return
        }
        if (!project.hasApiFolder()) {
            project.logger.info(
                    "Project ${project.name} doesn't have an api folder, ignoring API tasks.")
            return
        }

        val metalavaConfiguration = project.createMetalavaConfiguration()

        val javaPluginConvention = project.convention.getPlugin<JavaPluginConvention>()
        val mainSourceSet = javaPluginConvention.sourceSets.getByName("main")

        val currentApiFile = project.getCurrentApiFile()

        val javaInputs = JavaCompileInputs.fromSourceSet(mainSourceSet, project)

        val checkApi = project.tasks.create("checkApi", CheckApiTask::class.java) { task ->
            task.configuration = metalavaConfiguration
            task.apiTxtFile = currentApiFile
            task.dependsOn(metalavaConfiguration)
        }
        applyInputs(javaInputs, checkApi)

        val lastReleasedApiFile = project.getRequiredCompatibilityApiFile()
        if (lastReleasedApiFile != null) {
            val checkApiRelease = project.tasks.create("checkApiRelease", CheckApiTask::class.java) { task ->
                 task.configuration = metalavaConfiguration
                 task.apiTxtFile = lastReleasedApiFile
                 task.allowApiAdditions = true
                 task.dependsOn(metalavaConfiguration)
             }
             applyInputs(javaInputs, checkApiRelease)
             checkApi.dependsOn(checkApiRelease)
        }

        val updateApi = project.tasks.create("updateApi", UpdateApiTask::class.java) { task ->
            task.configuration = metalavaConfiguration
            task.apiTxtFile = currentApiFile
            task.dependsOn(metalavaConfiguration)
        }
        applyInputs(javaInputs, updateApi)

        project.tasks.getByName("check").dependsOn(checkApi)
        project.rootProject.tasks.getByName(BUILD_ON_SERVER_TASK).dependsOn(checkApi)
    }

    fun applyInputs(inputs: JavaCompileInputs, task: MetalavaTask) {
        task.sourcePaths = inputs.sourcePaths
        task.dependencyClasspath = inputs.dependencyClasspath
        task.bootClasspath = inputs.bootClasspath
    }
}
