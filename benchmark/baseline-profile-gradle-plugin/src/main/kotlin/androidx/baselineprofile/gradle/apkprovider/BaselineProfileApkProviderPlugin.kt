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

package androidx.baselineprofile.gradle.apkprovider

import androidx.baselineprofile.gradle.utils.BUILD_TYPE_BASELINE_PROFILE_PREFIX
import androidx.baselineprofile.gradle.utils.checkAgpVersion
import androidx.baselineprofile.gradle.utils.createExtendedBuildTypes
import androidx.baselineprofile.gradle.utils.isGradleSyncRunning
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This is the apk provider plugin for baseline profile generation. In order to generate baseline
 * profiles three plugins are needed: one is applied to the app or the library that should consume
 * the baseline profile when building (consumer), one is applied to the project that should supply
 * the test apk (apk provider) and the last one is applied to a test module containing the ui
 * test that generate the baseline profile on the device (producer).
 */
class BaselineProfileApkProviderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        var foundAppPlugin = false
        project.pluginManager.withPlugin("com.android.application") {
            foundAppPlugin = true
            configureWithAndroidPlugin(project = project)
        }
        var foundLibraryPlugin = false
        project.pluginManager.withPlugin("com.android.library") {
            foundLibraryPlugin = true
        }

        // Only used to verify that the android application plugin has been applied.
        // Note that we don't want to throw any exception if gradle sync is in progress.
        project.afterEvaluate {
            if (!project.isGradleSyncRunning()) {
                if (!foundAppPlugin) {

                    // Check whether the library plugin was applied instead. If that's the case
                    // it's possible the developer meant to generate a baseline profile for a
                    // library and we can give further information.
                    throw IllegalStateException(
                        if (!foundLibraryPlugin) {
                            """
                    The module ${project.name} does not have the `com.android.application` plugin
                    applied. The `androidx.baselineprofile.apkprovider` plugin supports only
                    android application modules. Please review your build.gradle to ensure this
                    plugin is applied to the correct module.
                    """.trimIndent()
                        } else {
                            """
                    The module ${project.name} does not have the `com.android.application` plugin
                    but has the `com.android.library` plugin. If you're trying to generate a
                    baseline profile for a library, you'll need to apply the
                    `androidx.baselineprofile.apkprovider` to an android application that
                    has the `com.android.application` plugin applied. This should be a sample app
                    running the code of the library for which you want to generate the profile.
                    Please review your build.gradle to ensure this plugin is applied to the
                    correct module.
                    """.trimIndent()
                        }
                    )
                }
                project.logger.debug(
                    """
                    [BaselineProfileApkProviderPlugin] afterEvaluate check: app plugin was applied
                    """.trimIndent()
                )
            }
        }
    }

    private fun configureWithAndroidPlugin(project: Project) {

        // Checks that the required AGP version is applied to this project.
        project.checkAgpVersion()

        // Create the non obfuscated release build types from the existing release ones.
        // We want to extend all the current release build types based on isDebuggable flag.
        project
            .extensions
            .getByType(ApplicationAndroidComponentsExtension::class.java)
            .finalizeDsl { applicationExtension ->

                val debugBuildType = applicationExtension.buildTypes.getByName("debug")

                // Creates the baseline profile build types
                createExtendedBuildTypes(
                    project = project,
                    extension = applicationExtension,
                    newBuildTypePrefix = BUILD_TYPE_BASELINE_PROFILE_PREFIX,
                    filterBlock = {
                        // Create baseline profile build types only for non debuggable builds.
                        !it.isDebuggable
                    },
                    configureBlock = {
                        isJniDebuggable = false
                        isDebuggable = false
                        isMinifyEnabled = false
                        isShrinkResources = false
                        isProfileable = true
                        signingConfig = debugBuildType.signingConfig
                        enableAndroidTestCoverage = false
                        enableUnitTestCoverage = false
                    }
                )
            }
    }
}
