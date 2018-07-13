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

package androidx.build

import androidx.build.SupportConfig.INSTRUMENTATION_RUNNER
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/**
 * Support library specific com.android.application plugin that sets common configurations needed
 * for support library test apps.
 */
class SupportAndroidTestAppPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply<AndroidXPlugin>()
        project.apply<AppPlugin>()

        val application = project.extensions.findByType(AppExtension::class.java)
                ?: throw Exception("Failed to find Android extension")

        application.compileSdkVersion(SupportConfig.CURRENT_SDK_VERSION)
        application.defaultConfig.targetSdkVersion(SupportConfig.CURRENT_SDK_VERSION)

        application.buildToolsVersion = SupportConfig.BUILD_TOOLS_VERSION

        application.defaultConfig.versionCode = 1
        application.defaultConfig.versionName = "1.0"

        application.compileOptions.setSourceCompatibility(JavaVersion.VERSION_1_8)
        application.compileOptions.setTargetCompatibility(JavaVersion.VERSION_1_8)

        application.defaultConfig.testInstrumentationRunner = INSTRUMENTATION_RUNNER
        application.testOptions.unitTests.isReturnDefaultValues = true

        // Use a local debug keystore to avoid build server issues.
        application.signingConfigs.findByName("debug")?.storeFile =
                SupportConfig.getKeystore(project)

        application.lintOptions.isAbortOnError = true
        val baseline = project.lintBaseline
        if (baseline.exists()) {
            application.lintOptions.baseline(baseline)
        }
    }
}
