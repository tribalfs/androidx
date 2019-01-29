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

package androidx.navigation.safeargs.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import groovy.util.XmlSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File
import javax.inject.Inject

private const val PLUGIN_DIRNAME = "navigation-args"
internal const val GENERATED_PATH = "generated/source/$PLUGIN_DIRNAME"
internal const val INCREMENTAL_PATH = "intermediates/incremental"

abstract class SafeArgsPlugin protected constructor(
    val providerFactory: ProviderFactory
) : Plugin<Project> {

    abstract val generateKotlin: Boolean

    private fun forEachVariant(extension: BaseExtension, action: (BaseVariant) -> Unit) {
        when {
            extension is AppExtension -> extension.applicationVariants.all(action)
            extension is LibraryExtension -> {
                extension.libraryVariants.all(action)
                if (extension is FeatureExtension) {
                    extension.featureVariants.all(action)
                }
            }
            else -> throw GradleException("safeargs plugin must be used with android app," +
                    "library or feature plugin")
        }
    }

    override fun apply(project: Project) {
        val extension = project.extensions.findByType(BaseExtension::class.java)
                ?: throw GradleException("safeargs plugin must be used with android plugin")
        val isKotlinProject =
            project.extensions.findByType(KotlinProjectExtension::class.java) != null
        if (!isKotlinProject && generateKotlin) {
            throw GradleException(
                "androidx.navigation.safeargs.kotlin plugin must be used with kotlin plugin")
        }
        forEachVariant(extension) { variant ->
            val task = project.tasks.create(
                "generateSafeArgs${variant.name.capitalize()}",
                ArgumentsGenerationTask::class.java
            ) { task ->
                setApplicationId(task, variant)
                task.rFilePackage = variant.rFilePackage()
                task.navigationFiles = navigationFiles(variant)
                task.outputDir = File(project.buildDir, "$GENERATED_PATH/${variant.dirName}")
                task.incrementalFolder = File(project.buildDir, "$INCREMENTAL_PATH/${task.name}")
                task.useAndroidX = (project.findProperty("android.useAndroidX") == "true")
                task.generateKotlin = generateKotlin
            }
            task.applicationIdResource?.let { task.dependsOn(it) }
            variant.registerJavaGeneratingTask(task, task.outputDir)
        }
    }

    /**
     * Sets the android project application id into the task.
     */
    private fun setApplicationId(task: ArgumentsGenerationTask, variant: BaseVariant) {
        val appIdTextResource = variant.applicationIdTextResource
        if (appIdTextResource != null) {
            task.applicationIdResource = appIdTextResource
        } else {
            // getApplicationIdTextResource() returned null, fallback to getApplicationId()
            task.applicationId = variant.applicationId
        }
    }

    private fun BaseVariant.rFilePackage() = providerFactory.provider {
        val mainSourceSet = sourceSets.find { it.name == "main" }
        val sourceSet = mainSourceSet ?: sourceSets[0]
        val manifest = sourceSet.manifestFile
        val parsed = XmlSlurper(false, false).parse(manifest)
        parsed.getProperty("@package").toString()
    }

    private fun navigationFiles(variant: BaseVariant) = providerFactory.provider {
        variant.sourceSets
            .flatMap { it.resDirectories }
            .mapNotNull {
                File(it, "navigation").let { navFolder ->
                    if (navFolder.exists() && navFolder.isDirectory) navFolder else null
                }
            }
            .flatMap { navFolder -> navFolder.listFiles().asIterable() }
            .filter { file -> file.isFile }
            .groupBy { file -> file.name }
            .map { entry -> entry.value.last() }
    }
}

@Suppress("unused")
class SafeArgsJavaPlugin @Inject constructor(
    providerFactory: ProviderFactory
) : SafeArgsPlugin(providerFactory) {

    override val generateKotlin = false
}

@Suppress("unused")
class SafeArgsKotlinPlugin @Inject constructor(
    providerFactory: ProviderFactory
) : SafeArgsPlugin(providerFactory) {

    override val generateKotlin = true
}