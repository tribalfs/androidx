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

package androidx.build.uptodatedness

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskExecutionResult

/**
 * Validates that all tasks (except a temporary exception list) are considered up-to-date.
 * The expected usage of this is that the user will invoke a build with the
 * TaskUpToDateValidator disabled, and then reinvoke the same build with the TaskUpToDateValidator
 * enabled. If the second build actually runs any tasks, then some tasks don't have the correct
 * inputs/outputs declared and are running more often than necessary.
 */

const val DISALLOW_TASK_EXECUTION_FLAG_NAME = "disallowExecution"

// Temporary set of exempt tasks that are known to still be out-of-date after running once
// Entries in this set may be task names (like assembleRelease) or task paths
// (like :core:core:assembleRelease)
// Entries in this set do still get rerun because they might produce files that are needed by
// subsequent tasks
val ALLOW_RERUNNING_TASKS = setOf(
    "analyticsRecordingRelease",
    "buildOnServer",
    "checkExternalLicenses",
    "createArchive",
    "createDiffArchiveForAll",
    "createProjectZip",
    "externalNativeBuildDebug",
    "externalNativeBuildRelease",
    "generateDebugUnitTestConfig",
    "generateJsonModelDebug",
    "generateJsonModelRelease",
    "generateMetadataFileForAndroidDebugPublication",
    "generateMetadataFileForAndroidReleasePublication",
    "generateMetadataFileForDesktopPublication",
    "generateMetadataFileForJvmPublication",
    "generateMetadataFileForJvmlinux-x64Publication",
    "generateMetadataFileForJvmmacos-x64Publication",
    "generateMetadataFileForJvmmacos-arm64Publication",
    "generateMetadataFileForJvmwindows-x64Publication",
    "generateMetadataFileForJvmallPublication",
    "generateMetadataFileForMavenPublication",
    "generateMetadataFileForMetadataPublication",
    "generateMetadataFileForKotlinMultiplatformPublication",
    "generateMetadataFileForPluginMavenPublication",
    "generatePomFileForBenchmarkPluginMarkerMavenPublication",
    "generatePomFileForAndroidDebugPublication",
    "generatePomFileForAndroidReleasePublication",
    "generatePomFileForDesktopPublication",
    "generatePomFileForJvmlinux-x64Publication",
    "generatePomFileForJvmmacos-x64Publication",
    "generatePomFileForJvmmacos-arm64Publication",
    "generatePomFileForJvmwindows-x64Publication",
    "generatePomFileForJvmallPublication",
    "generatePomFileForJvmPublication",
    "generatePomFileForKotlinMultiplatformPublication",
    "generatePomFileForMavenPublication",
    "generatePomFileForPluginMavenPublication",
    "generatePomFileForMetadataPublication",
    "generatePomFileForSafeargsJavaPluginMarkerMavenPublication",
    "generatePomFileForSafeargsKotlinPluginMarkerMavenPublication",
    "partiallyDejetifyArchive",
    "publishBenchmarkPluginMarkerMavenPublicationToMavenRepository",
    "publishAndroidDebugPublicationToMavenRepository",
    "publishAndroidReleasePublicationToMavenRepository",
    "publishDesktopPublicationToMavenRepository",
    "publishJvmPublicationToMavenRepository",
    "publishJvmlinux-x64PublicationToMavenRepository",
    "publishJvmmacos-x64PublicationToMavenRepository",
    "publishJvmmacos-arm64PublicationToMavenRepository",
    "publishJvmwindows-x64PublicationToMavenRepository",
    "publishJvmallPublicationToMavenRepository",
    "publishKotlinMultiplatformPublicationToMavenRepository",
    "publishMavenPublicationToMavenRepository",
    "publishMetadataPublicationToMavenRepository",
    "publishPluginMavenPublicationToMavenRepository",
    "publishSafeargsJavaPluginMarkerMavenPublicationToMavenRepository",
    "publishSafeargsKotlinPluginMarkerMavenPublicationToMavenRepository",
    /**
     * relocateShadowJar is used to configure the ShadowJar hence it does not have any outputs.
     * https://github.com/johnrengelman/shadow/issues/561
     */
    "relocateShadowJar",
    "testDebugUnitTest",
    "stripArchiveForPartialDejetification",
    "verifyDependencyVersions",
    "zipConstrainedTestConfigsWithApks",
    "zipTestConfigsWithApks",
    "zipHtmlResultsOfTestDebugUnitTest",
    "zipXmlResultsOfTestDebugUnitTest",

    ":camera:integration-tests:camera-testapp-core:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-core:packageDebug",
    ":camera:integration-tests:camera-testapp-extensions:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-extensions:packageDebug",
    ":camera:integration-tests:camera-testapp-extensions:GenerateTestConfigurationdebugAndroidTest",
    ":camera:integration-tests:camera-testapp-uiwidgets:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-uiwidgets:packageDebug",
    ":camera:integration-tests:camera-testapp-core:GenerateTestConfigurationdebug",
    ":camera:integration-tests:camera-testapp-core:GenerateTestConfigurationdebugAndroidTest",
    ":camera:integration-tests:camera-testapp-view:GenerateTestConfigurationdebug",
    ":camera:integration-tests:camera-testapp-view:GenerateTestConfigurationdebugAndroidTest",
    ":camera:integration-tests:camera-testapp-view:mergeLibDexDebug",
    ":camera:integration-tests:camera-testapp-view:packageDebug",
    "configureCMakeDebug",
    "buildCMakeDebug",
    "configureCMakeRelWithDebInfo",
    "buildCMakeRelWithDebInfo",
    ":appsearch:appsearch-local-storage:buildCMakeDebug[icing]",
    ":appsearch:appsearch-local-storage:buildCMakeRelWithDebInfo[icing]",
    ":hilt:hilt-navigation-compose:kaptGenerateStubsDebugKotlin",
    ":hilt:hilt-navigation-compose:kaptGenerateStubsReleaseKotlin",
    ":lint-checks:integration-tests:copyDebugAndroidLintReports",

    // https://github.com/gradle/gradle/issues/17262
    ":doclava:compileJava",
    ":doclava:processResources",
    ":doclava:jar"
)

// Additional tasks that are expected to be temporarily out-of-date after running once
// Tasks in this set we don't even try to rerun, because they're known to be unnecessary
val DONT_TRY_RERUNNING_TASKS = setOf(
    ":buildSrc-tests:project-subsets:test",
    "listTaskOutputs",
    "validateProperties",
    "tasks",

    ":benchmark:benchmark-macro:generateReleaseProtos",
    ":benchmark:benchmark-macro:generateDebugProtos",
    ":benchmark:benchmark-macro:compileReleaseKotlin",
    ":benchmark:benchmark-macro:compileDebugKotlin",
    ":benchmark:benchmark-macro:compileReleaseJavaWithJavac",
    ":benchmark:benchmark-macro:compileDebugJavaWithJavac",
    ":benchmark:benchmark-macro:extractReleaseAnnotations",
    ":benchmark:benchmark-macro:extractDebugAnnotations",
    ":benchmark:benchmark-macro:generateApi",
    ":benchmark:benchmark-macro:runErrorProne",
    ":benchmark:benchmark-macro:lintAnalyzeDebug",
    ":benchmark:benchmark-macro:lintDebug",

    // More information about the fact that these dokka tasks rerun can be found at b/167569304
    "dokkaKotlinDocs",
    "zipDokkaDocs",
    "dackkaDocs",

    // Flakily not up-to-date, b/176120659
    "doclavaDocs",

    // We should be able to remove these entries when b/160392650 is fixed
    "lint",
    "lintAnalyzeDebug",
    "lintDebug",
    "lintVitalRelease",
)

class TaskUpToDateValidator :
    BuildService<TaskUpToDateValidator.Parameters>, OperationCompletionListener {
    open class Parameters : BuildServiceParameters
    override fun getParameters(): Parameters {
        return Parameters()
    }

    override fun onFinish(event: FinishEvent) {
        val result = event.result
        if (result is TaskExecutionResult) {
            val name = event.descriptor.name
            val executionReasons = result.executionReasons
            if (executionReasons.isNullOrEmpty()) {
                // empty list means task was actually up-to-date, see docs for
                // TaskExecutionResult.executionReasons
                // null list means the task already failed, so we'll skip emitting our error
                return
            }
            if (!isAllowedToRerunTask(name)) {
                throw GradleException(
                    "Ran two consecutive builds of the same tasks, and in the " +
                        "second build, observed:\n" +
                        "task $name not UP-TO-DATE. It was out-of-date because:\n" +
                        "${result.executionReasons}"
                )
            }
        }
    }

    companion object {
        private fun shouldValidate(project: Project): Boolean {
            return project.providers.gradleProperty(DISALLOW_TASK_EXECUTION_FLAG_NAME)
                .forUseAtConfigurationTime().isPresent()
        }

        private fun isAllowedToRerunTask(taskPath: String): Boolean {
            if (ALLOW_RERUNNING_TASKS.contains(taskPath)) {
                return true
            }
            val colonIndex = taskPath.lastIndexOf(":")
            if (colonIndex >= 0) {
                val taskName = taskPath.substring(colonIndex + 1)
                if (ALLOW_RERUNNING_TASKS.contains(taskName)) {
                    return true
                }
            }
            return false
        }

        private fun shouldTryRerunningTask(task: Task): Boolean {
            return !(
                DONT_TRY_RERUNNING_TASKS.contains(task.name) ||
                    DONT_TRY_RERUNNING_TASKS.contains(task.path)
                )
        }

        fun setup(rootProject: Project, registry: BuildEventsListenerRegistry) {
            if (!shouldValidate(rootProject)) {
                return
            }
            // create listener for validating that any task that reran was expected to rerun
            val validatorProvider = rootProject.getGradle().getSharedServices()
                .registerIfAbsent(
                    "TaskUpToDateValidator",
                    TaskUpToDateValidator::class.java,
                    { _ -> }
                )
            registry.onTaskCompletion(validatorProvider)

            // skip rerunning tasks that are known to be unnecessary to rerun
            rootProject.allprojects { subproject ->
                subproject.tasks.configureEach { task ->
                    task.onlyIf { shouldTryRerunningTask(task) }
                }
            }
        }
    }
}
