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

package androidx.baselineprofile.gradle.consumer

import androidx.baselineprofile.gradle.configuration.ConfigurationManager
import androidx.baselineprofile.gradle.consumer.task.MainGenerateBaselineProfileTask
import androidx.baselineprofile.gradle.consumer.task.MergeBaselineProfileTask
import androidx.baselineprofile.gradle.consumer.task.PrintConfigurationForVariantTask
import androidx.baselineprofile.gradle.consumer.task.maybeCreateGenerateTask
import androidx.baselineprofile.gradle.utils.AgpPlugin
import androidx.baselineprofile.gradle.utils.AgpPluginId
import androidx.baselineprofile.gradle.utils.BUILD_TYPE_BASELINE_PROFILE_PREFIX
import androidx.baselineprofile.gradle.utils.CONFIGURATION_NAME_BASELINE_PROFILES
import androidx.baselineprofile.gradle.utils.INTERMEDIATES_BASE_FOLDER
import androidx.baselineprofile.gradle.utils.MAX_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.MIN_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.R8Utils
import androidx.baselineprofile.gradle.utils.RELEASE
import androidx.baselineprofile.gradle.utils.camelCase
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

/**
 * This is the consumer plugin for baseline profile generation. In order to generate baseline
 * profiles three plugins are needed: one is applied to the app or the library that should consume
 * the baseline profile when building (consumer), one is applied to the module that should supply
 * the under test app (app target) and the last one is applied to a test module containing the ui
 * test that generate the baseline profile on the device (producer).
 */
class BaselineProfileConsumerPlugin : Plugin<Project> {
    override fun apply(project: Project) = BaselineProfileConsumerAgpPlugin(project).onApply()
}

private class BaselineProfileConsumerAgpPlugin(private val project: Project) : AgpPlugin(
    project = project,
    supportedAgpPlugins = setOf(
        AgpPluginId.ID_ANDROID_APPLICATION_PLUGIN,
        AgpPluginId.ID_ANDROID_LIBRARY_PLUGIN
    ),
    minAgpVersion = MIN_AGP_VERSION_REQUIRED,
    maxAgpVersion = MAX_AGP_VERSION_REQUIRED
) {

    // List of the non debuggable build types
    private val nonDebuggableBuildTypes = mutableListOf<String>()

    // Offers quick access to configuration extension, hiding the property override and merge logic
    private val perVariantBaselineProfileExtensionManager =
        PerVariantConsumerExtensionManager(BaselineProfileConsumerExtension.register(project))

    // Manages creation of configurations
    private val configurationManager = ConfigurationManager(project)

    // Manages r8 properties
    private val r8Utils = R8Utils(project)

    // Global baseline profile configuration. Note that created here it can be directly consumed
    // in the dependencies block.
    private val mainBaselineProfileConfiguration = configurationManager.maybeCreate(
        nameParts = listOf(CONFIGURATION_NAME_BASELINE_PROFILES),
        canBeConsumed = false,
        canBeResolved = true,
        buildType = null,
        productFlavors = null
    )

    override fun onAgpPluginNotFound(pluginIds: Set<AgpPluginId>) {
        throw IllegalStateException(
            """
            The module ${project.name} does not have the `com.android.application` or
            `com.android.library` plugin applied. The `androidx.baselineprofile.consumer`
            plugin supports only android application and library modules. Please review
            your build.gradle to ensure this plugin is applied to the correct module.
            """.trimIndent()
        )
    }

    override fun onAgpPluginFound(pluginIds: Set<AgpPluginId>) {
        project.logger.debug(
            """
            [BaselineProfileConsumerPlugin] afterEvaluate check: app or library plugin was applied
            """.trimIndent()
        )
    }

    override fun onApplicationFinalizeDsl(extension: ApplicationExtension) {

        // Here we select the build types we want to process if this is an application,
        // i.e. non debuggable build types that have not been created by the app target plugin.
        // Also exclude the build types starting with baseline profile prefix, in case the app
        // target plugin is also applied.

        nonDebuggableBuildTypes.addAll(extension.buildTypes
            .filter { !it.isDebuggable && !it.name.startsWith(BUILD_TYPE_BASELINE_PROFILE_PREFIX) }
            .map { it.name }
        )
    }

    override fun onLibraryFinalizeDsl(extension: LibraryExtension) {

        // Here we select the build types we want to process if this is a library.
        // Libraries don't have a `debuggable` flag. Also we don't need to exclude build types
        // prefixed with the baseline profile prefix. Ideally on the `debug` type should be
        // excluded.

        nonDebuggableBuildTypes.addAll(extension.buildTypes
            .filter { it.name != "debug" }
            .map { it.name }
        )
    }

    @Suppress("UnstableApiUsage")
    override fun onVariants(variant: Variant) {

        // Process only the non debuggable build types we previously selected.
        if (variant.buildType !in nonDebuggableBuildTypes) return

        // This allows quick access to this variant configuration according to the override
        // and merge rules implemented in the PerVariantConsumerExtensionManager.
        val variantConfiguration = perVariantBaselineProfileExtensionManager.variant(variant)

        // For test only: this registers a print task with the configuration of the variant.
        PrintConfigurationForVariantTask.registerForVariant(
            project = project,
            variant = variant,
            variantConfig = variantConfiguration
        )

        // Sets the r8 rewrite baseline profile for the non debuggable variant.
        if (variantConfiguration.enableR8BaselineProfileRewrite) {
            r8Utils.enableR8RulesRewriteForVariant(variant)
        }

        // Check if this variant has any direct dependency
        val variantDependencies = variantConfiguration.dependencies

        // Creates the configuration to carry the specific variant artifact
        val baselineProfileConfiguration = createConfigurationForVariant(
            variant = variant,
            mainConfiguration = mainBaselineProfileConfiguration,
            hasDirectConfiguration = variantDependencies.any { it.second != null }
        )

        // Adds the custom dependencies for baseline profiles. Note that dependencies
        // for global, build type, flavor and variant specific are all merged.
        variantDependencies.forEach {
            val targetProject = it.first
            val variantName = it.second
            val targetProjectDependency = if (variantName != null) {
                val configurationName = camelCase(
                    variantName,
                    CONFIGURATION_NAME_BASELINE_PROFILES
                )
                project.dependencies.project(
                    mutableMapOf(
                        "path" to targetProject.path,
                        "configuration" to configurationName
                    )
                )
            } else {
                project.dependencyFactory.create(targetProject)
            }
            baselineProfileConfiguration.dependencies.add(targetProjectDependency)
        }

        // There are 2 different ways in which the output task can merge the baseline
        // profile rules, according to [BaselineProfileConsumerExtension#mergeIntoMain].
        // When mergeIntoMain is `true` the first variant will create a task shared across
        // all the variants to merge, while the next variants will simply add the additional
        // baseline profile artifacts, modifying the existing task.
        // When mergeIntoMain is `false` each variants has its own task with a single
        // artifact per task, specific for that variant.
        // When mergeIntoMain is not specified, it's by default true for libraries and false
        // for apps.
        val mergeIntoMain = variantConfiguration.mergeIntoMain ?: isLibraryModule()

        // TODO: When `mergeIntoMain` is true it lazily triggers the generation of all
        //  the variants for all the build types. Due to b/265438201, that fails when
        //  there are multiple build types. As temporary workaround, when `mergeIntoMain`
        //  is true, calling a generation task for a specific build type will merge
        //  profiles for all the variants of that build type and output it in the `main`
        //  folder.
        val (mergeAwareVariantName, mergeAwareVariantOutput) = if (mergeIntoMain) {
            listOf(variant.buildType ?: "", "main")
        } else {
            listOf(variant.name, variant.name)
        }

        // Creates the task to merge the baseline profile artifacts coming from
        // different configurations.
        val mergedTaskOutputDir = project
            .layout
            .buildDirectory
            .dir("$INTERMEDIATES_BASE_FOLDER/$mergeAwareVariantOutput/merged")

        val mergeTaskProvider = MergeBaselineProfileTask.maybeRegisterForMerge(
            project = project,
            variantName = mergeAwareVariantName,
            hasDependencies = baselineProfileConfiguration.allDependencies.isNotEmpty(),
            sourceProfilesFileCollection = baselineProfileConfiguration,
            outputDir = mergedTaskOutputDir,
            filterRules = variantConfiguration.filterRules,
            library = isLibraryModule()
        )

        // If `saveInSrc` is true, we create an additional task to copy the output
        // of the merge task in the src folder.
        val lastTaskProvider = if (variantConfiguration.saveInSrc) {

            val baselineProfileOutputDir = perVariantBaselineProfileExtensionManager
                .variant(variant)
                .baselineProfileOutputDir
            val srcOutputDir = project
                .layout
                .projectDirectory
                .dir("src/$mergeAwareVariantOutput/$baselineProfileOutputDir/")

            // This task copies the baseline profile generated from the merge task.
            // Note that we're reutilizing the [MergeBaselineProfileTask] because
            // if the flag `mergeIntoMain` is true tasks will have the same name
            // and we just want to add more file to copy to the same output. This is
            // already handled in the MergeBaselineProfileTask.
            val copyTaskProvider = MergeBaselineProfileTask.maybeRegisterForCopy(
                project = project,
                variantName = mergeAwareVariantName,
                library = isLibraryModule(),
                sourceDir = mergeTaskProvider.flatMap { it.baselineProfileDir },
                outputDir = project.provider { srcOutputDir },
            )

            // Applies the source path for this variant
            srcOutputDir.asFile.apply {
                mkdirs()
                variant
                    .sources
                    .baselineProfiles?.addStaticSourceDirectory(absolutePath)
            }

            // If this is an application, we need to ensure that:
            // If `automaticGenerationDuringBuild` is true, building a release build
            // should trigger the generation of the profile. This is done through a
            // dependsOn rule.
            // If `automaticGenerationDuringBuild` is false and the user calls both
            // tasks to generate and assemble, assembling the release should wait of the
            // generation to be completed. This is done through a `mustRunAfter` rule.
            // Depending on whether the flag `automaticGenerationDuringBuild` is enabled
            // Note that we cannot use the variant src set api
            // `addGeneratedSourceDirectory` since that overwrites the outputDir,
            // that would be re-set in the build dir.
            // Also this is specific for applications: doing this for a library would
            // trigger a circular task dependency since the library would require
            // the profile in order to build the aar for the sample app and generate
            // the profile.
            if (isApplicationModule()) {
                afterVariants {
                    project
                        .tasks
                        .named(camelCase("merge", variant.name, "artProfile"))
                        .configure {
                            // Sets the task dependency according to the configuration
                            // flag.
                            val automaticGeneration = perVariantBaselineProfileExtensionManager
                                .variant(variant)
                                .automaticGenerationDuringBuild

                            // TODO: this causes a circular task dependency when the producer points
                            //  to a consumer that does not have the appTarget plugin. (b/272851616)
                            if (automaticGeneration) {
                                it.dependsOn(copyTaskProvider)
                            } else {
                                it.mustRunAfter(copyTaskProvider)
                            }
                        }
                }
            }

            // In this case the last task is the copy task.
            copyTaskProvider
        } else {

            if (variantConfiguration.automaticGenerationDuringBuild) {
                // If the flag `automaticGenerationDuringBuild` is true, we can set the
                // merge task to provide generated sources for the variant, using the
                // src set variant api. This means that we don't need to manually depend
                // on the merge or prepare art profile task.
                variant
                    .sources
                    .baselineProfiles?.addGeneratedSourceDirectory(
                        taskProvider = mergeTaskProvider,
                        wiredWith = MergeBaselineProfileTask::baselineProfileDir
                    )
            } else {

                // This is the case of `saveInSrc` and `automaticGenerationDuringBuild`
                // both false, that is unsupported. In this case we simply throw an
                // error.
                if (!isGradleSyncRunning()) {
                    throw GradleException(
                        """
                The current configuration of flags `saveInSrc` and `automaticGenerationDuringBuild`
                is not supported. At least one of these should be set to `true`. Please review your
                baseline profile plugin configuration in your build.gradle.
                    """.trimIndent()
                    )
                }
            }

            // In this case the last task is the merge task.
            mergeTaskProvider
        }

        // Here we create the final generate task that triggers the whole generation
        // for this variant and all the parent tasks. For this one the child task
        // is either copy or merge, depending on the configuration.
        val variantGenerateTask = maybeCreateGenerateTask<Task>(
            project = project,
            variantName = mergeAwareVariantName,
            childGenerationTaskProvider = lastTaskProvider
        )

        // Create the build type task. For example `generateReleaseBaselineProfile`
        // The variant name is equal to the build type name if there are no flavors.
        // Note that if `mergeIntoMain` is `true` the build type task already exists.
        if (!mergeIntoMain &&
            !variant.buildType.isNullOrBlank() &&
            variant.name != variant.buildType
        ) {
            maybeCreateGenerateTask<Task>(
                project = project,
                variantName = variant.buildType!!,
                childGenerationTaskProvider = variantGenerateTask
            )
        }

        // TODO: Due to b/265438201 we cannot have a global task
        //  `generateBaselineProfile` that triggers generation for all the
        //  variants when there are multiple build types. The temporary workaround
        //  is to generate baseline profiles only for variants with the `release`
        //  build type until that bug is fixed, when running the global task
        //  `generateBaselineProfile`. This can be removed after fix.
        if (variant.buildType == RELEASE) {
            maybeCreateGenerateTask<MainGenerateBaselineProfileTask>(
                project,
                "",
                variantGenerateTask
            )
        }
    }

    private fun createConfigurationForVariant(
        variant: Variant,
        mainConfiguration: Configuration?,
        hasDirectConfiguration: Boolean
    ): Configuration {

        val variantName = variant.name
        val productFlavors = variant.productFlavors
        val flavorName = variant.flavorName ?: ""
        val buildTypeName = variant.buildType ?: ""

        val buildTypeConfiguration =
            if (buildTypeName.isNotBlank() && buildTypeName != variantName) {
                configurationManager.maybeCreate(
                    nameParts = listOf(buildTypeName, CONFIGURATION_NAME_BASELINE_PROFILES),
                    canBeResolved = true,
                    canBeConsumed = false,
                    buildType = null,
                    productFlavors = null,
                    extendFromConfigurations = listOfNotNull(mainConfiguration)
                )
            } else null

        val flavorConfiguration =
            if (flavorName.isNotBlank() && flavorName != variantName) {
                configurationManager.maybeCreate(
                    nameParts = listOf(flavorName, CONFIGURATION_NAME_BASELINE_PROFILES),
                    canBeResolved = true,
                    canBeConsumed = false,
                    buildType = null,
                    productFlavors = null,
                    extendFromConfigurations = listOfNotNull(mainConfiguration)
                )
            } else null

        // When there is direct configuration for the dependency the matching through attributes
        // is bypassed, because most likely the user meant to match a configuration that does not
        // have the same tags (for example to a different flavor or build type).

        return if (hasDirectConfiguration) {
            configurationManager.maybeCreate(
                nameParts = listOf(variantName, CONFIGURATION_NAME_BASELINE_PROFILES),
                canBeResolved = true,
                canBeConsumed = false,
                extendFromConfigurations = listOfNotNull(
                    mainConfiguration,
                    flavorConfiguration,
                    buildTypeConfiguration
                ),
                buildType = null,
                productFlavors = null
            )
        } else {
            configurationManager.maybeCreate(
                nameParts = listOf(variantName, CONFIGURATION_NAME_BASELINE_PROFILES),
                canBeResolved = true,
                canBeConsumed = false,
                extendFromConfigurations = listOfNotNull(
                    mainConfiguration,
                    flavorConfiguration,
                    buildTypeConfiguration
                ),
                buildType = buildTypeName,
                productFlavors = productFlavors
            )
        }
    }
}
