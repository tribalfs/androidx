/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.benchmark.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

class BenchmarkPlugin : Plugin<Project> {
    private var foundAndroidPlugin = false

    override fun apply(project: Project) {
        // NOTE: Although none of the configuration code depends on a reference to the Android
        // plugin here, there is some implicit coupling behind the scenes, which ensures that the
        // required BaseExtension from AGP can be found by registering project configuration as a
        // PluginManager callback.

        project.pluginManager.withPlugin("com.android.application") {
            configureWithAndroidPlugin(project)
        }

        project.pluginManager.withPlugin("com.android.library") {
            configureWithAndroidPlugin(project)
        }

        // Verify that the configuration from this plugin dependent on AGP was successfully applied.
        project.afterEvaluate {
            if (!foundAndroidPlugin) {
                throw StopExecutionException(
                    """A required plugin, com.android.application or com.android.library was not
                        found. The androidx.benchmark plugin currently only supports android
                        application or library modules. Ensure that a required plugin is applied
                        in the project build.gradle file."""
                        .trimIndent()
                )
            }
        }
    }

    private fun configureWithAndroidPlugin(project: Project) {
        if (!foundAndroidPlugin) {
            foundAndroidPlugin = true
            val extension = project.extensions.getByType(TestedExtension::class.java)
            configureWithAndroidExtension(project, extension)
        }
    }

    private fun configureWithAndroidExtension(project: Project, extension: TestedExtension) {
        val defaultConfig = extension.defaultConfig
        val testInstrumentationArgs = defaultConfig.testInstrumentationRunnerArguments

        defaultConfig.testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // disable overhead from test coverage by default, even if we use a debug variant
        extension.buildTypes.getByName("debug").isTestCoverageEnabled = false

        // Registering this block as a configureEach callback is only necessary because Studio skips
        // Gradle if there are no changes, which stops this plugin from being re-applied.
        var enabledOutput = false
        project.configurations.configureEach {
            if (!enabledOutput &&
                !project.rootProject.hasProperty("android.injected.invoked.from.ide") &&
                !testInstrumentationArgs.containsKey("androidx.benchmark.output.enable")
            ) {
                enabledOutput = true

                // NOTE: This argument is checked by ResultWriter to enable CI reports.
                defaultConfig.testInstrumentationRunnerArgument(
                    "androidx.benchmark.output.enable",
                    "true"
                )

                if (!testInstrumentationArgs.containsKey("additionalTestOutputDir")) {
                    defaultConfig.testInstrumentationRunnerArgument("no-isolated-storage", "1")
                }
            }
        }

        if (project.rootProject.tasks.findByName("lockClocks") == null) {
            project.rootProject.tasks.register("lockClocks", LockClocksTask::class.java).configure {
                it.adbPath.set(extension.adbExecutable.absolutePath)
            }
        }

        if (project.rootProject.tasks.findByName("unlockClocks") == null) {
            project.rootProject.tasks.register("unlockClocks", UnlockClocksTask::class.java)
                .configure {
                    it.adbPath.set(extension.adbExecutable.absolutePath)
                }
        }

        val extensionVariants = when (extension) {
            is AppExtension -> extension.applicationVariants
            is LibraryExtension -> extension.libraryVariants
            else -> throw StopExecutionException(
                """Missing required Android extension in project ${project.name}, this typically
                    means you are missing the required com.android.application or
                    com.android.library plugins or they could not be found. The
                    androidx.benchmark plugin currently only supports android application or
                    library modules. Ensure that the required plugin is applied in the project
                    build.gradle file.""".trimIndent()
            )
        }

        // NOTE: .all here is a Gradle API, which will run the callback passed to it after the
        // extension variants have been resolved.
        var applied = false
        extensionVariants.all {
            if (!applied && !testInstrumentationArgs.containsKey("additionalTestOutputDir")) {
                applied = true

                // Only enable pulling benchmark data through this plugin on older versions of AGP
                // that do not yet enable this flag.
                project.tasks.register("benchmarkReport", BenchmarkReportTask::class.java)
                    .configure {
                        it.adbPath.set(extension.adbExecutable.absolutePath)
                        it.dependsOn(project.tasks.named("connectedAndroidTest"))
                    }

                project.tasks.named("connectedAndroidTest").configure {
                    // The task benchmarkReport must be registered by this point, and is responsible
                    // for pulling report data from all connected devices onto host machine through
                    // adb.
                    it.finalizedBy("benchmarkReport")
                }
            }
        }
    }
}
