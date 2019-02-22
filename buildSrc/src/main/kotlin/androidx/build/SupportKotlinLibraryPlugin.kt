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

import androidx.build.dokka.Dokka
import androidx.build.metalava.Metalava
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class SupportKotlinLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply<AndroidXPlugin>()

        val supportLibraryExtension = project.extensions.create("supportLibrary",
                SupportLibraryExtension::class.java, project)
        project.configureMavenArtifactUpload(supportLibraryExtension)
        project.apply(mapOf("plugin" to "kotlin"))
        project.apply(mapOf("plugin" to "kotlin-kapt"))

        project.configureNonAndroidProjectForLint(supportLibraryExtension)

        project.afterEvaluate {
            Metalava.registerJavaProject(project, supportLibraryExtension)
            Dokka.registerJavaProject(project, supportLibraryExtension)
        }
    }
}
