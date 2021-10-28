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

import androidx.build.dependencyTracker.AffectedModuleDetector
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.google.common.io.Files
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.util.Locale

/**
 * Setting this property means that lint will update lint-baseline.xml if it exists.
 */
private const val UPDATE_LINT_BASELINE = "updateLintBaseline"

/**
 * Name of the service we use to limit the number of concurrent executions of lint
 */
public const val LINT_SERVICE_NAME = "androidxLintService"

/**
 * Property used by Lint to continue creating baselines without failing lint, normally set by:
 * -Dlint.baselines.continue=true from command line.
 */
private const val LINT_BASELINE_CONTINUE = "lint.baselines.continue"

// service for limiting the number of concurrent lint tasks
interface AndroidXLintService : BuildService<BuildServiceParameters.None>

fun Project.configureRootProjectForLint() {
    // determine many lint tasks to run in parallel
    val memoryPerTask = 512 * 1024 * 1024
    val maxLintMemory = Runtime.getRuntime().maxMemory() * 0.75 // save memory for other things too
    val maxNumParallelUsages = Math.max(1, (maxLintMemory / memoryPerTask).toInt())

    project.gradle.sharedServices.registerIfAbsent(
        LINT_SERVICE_NAME,
        AndroidXLintService::class.java,
        { spec ->
            spec.maxParallelUsages.set(maxNumParallelUsages)
        }
    )
}

fun Project.configureNonAndroidProjectForLint(extension: AndroidXExtension) {
    apply(mapOf("plugin" to "com.android.lint"))

    // Create fake variant tasks since that is what is invoked by developers.
    val lintTask = tasks.named("lint")
    lintTask.configure { task ->
        AffectedModuleDetector.configureTaskGuard(task)
    }
    afterEvaluate {
        tasks.named("lintAnalyze").configure { task ->
            AffectedModuleDetector.configureTaskGuard(task)
        }
        /* TODO: uncomment when we upgrade to AGP 7.1.0-alpha04
        tasks.named("lintReport").configure { task ->
            AffectedModuleDetector.configureTaskGuard(task)
        }*/
    }
    tasks.register("lintDebug") {
        it.dependsOn(lintTask)
        it.enabled = false
    }
    tasks.register("lintAnalyzeDebug") {
        it.dependsOn(lintTask)
        it.enabled = false
    }
    tasks.register("lintRelease") {
        it.dependsOn(lintTask)
        it.enabled = false
    }
    addToBuildOnServer(lintTask)

    val lintOptions = extensions.getByType<LintOptions>()
    configureLint(lintOptions, extension)
}

fun Project.configureAndroidProjectForLint(lintOptions: LintOptions, extension: AndroidXExtension) {
    project.afterEvaluate {
        // makes sure that the lintDebug task will exist, so we can find it by name
        setUpLintDebugIfNeeded()
    }
    tasks.register("lintAnalyze") {
        it.dependsOn("lintDebug")
        it.enabled = false
    }
    configureLint(lintOptions, extension)
    tasks.named("lint").configure { task ->
        // We already run lintDebug, we don't need to run lint which lints the release variant
        task.enabled = false
    }
    afterEvaluate {
        for (variant in project.agpVariants) {
            tasks.named(
                "lint${variant.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }}"
            ).configure { task ->
                AffectedModuleDetector.configureTaskGuard(task)
            }
            tasks.named(
                "lintAnalyze${variant.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }}"
            ).configure { task ->
                AffectedModuleDetector.configureTaskGuard(task)
            }
            /* TODO: uncomment when we upgrade to AGP 7.1.0-alpha04
            tasks.named("lintReport${variant.name.capitalize(Locale.US)}").configure { task ->
                AffectedModuleDetector.configureTaskGuard(task)
            }*/
        }
    }
}

private fun Project.setUpLintDebugIfNeeded() {
    val variants = project.agpVariants
    val variantNames = variants.map { v -> v.name }
    if (!variantNames.contains("debug")) {
        tasks.register("lintDebug") {
            for (variantName in variantNames) {
                if (variantName.lowercase(Locale.US).contains("debug")) {
                    it.dependsOn(
                        tasks.named(
                            "lint${variantName.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                            }}"
                        )
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION") // lintOptions methods
fun Project.configureLint(lintOptions: LintOptions, extension: AndroidXExtension) {
    project.dependencies.add(
        "lintChecks",
        project.rootProject.project(":lint-checks")
    )

    // The purpose of this specific project is to test that lint is running, so
    // it contains expected violations that we do not want to trigger a build failure
    val isTestingLintItself = (project.path == ":lint-checks:integration-tests")

    // If -PupdateLintBaseline was set we should update the baseline if it exists
    val updateLintBaseline = project.providers.gradleProperty(UPDATE_LINT_BASELINE)
        .forUseAtConfigurationTime().isPresent() && !isTestingLintItself

    lintOptions.apply {
        // Skip lintVital tasks on assemble. We explicitly run lintRelease for libraries.
        isCheckReleaseBuilds = false
    }

    // task to delete autogenerated baselines before running lint which might regenerate them
    val removeBaselineTask = project.tasks.register(
        "removeLintBaseline",
        RemoveBaselineTask::class.java,
    ) { deleteTask ->
        deleteTask.baselineFile.set(generatedLintBaseline)
    }

    // task to copy autogenerated baselines back into the source tree
    val updateBaselineTask = project.tasks.register(
        "updateLintBaseline",
        UpdateBaselineTask::class.java,
    ) { copyTask ->
        copyTask.source.set(generatedLintBaseline)
        copyTask.dest.set(lintBaseline)
    }

    tasks.withType(AndroidLintAnalysisTask::class.java).configureEach { task ->

        // don't run too many copies of lint at once due to memory limitations
        task.usesService(
            task.project.gradle.sharedServices.registrations.getByName(LINT_SERVICE_NAME).service
        )

        if (updateLintBaseline) {
            task.doFirst {
                // if we are updating baselines,
                // then we don't want the creation of a new baseline to be considered a failure
                System.setProperty(LINT_BASELINE_CONTINUE, "true")
            }

            // delete the autogenerated baseline before running lint
            task.dependsOn(removeBaselineTask)
        }
    }
    if (updateLintBaseline) {
        tasks.withType(AndroidLintTextOutputTask::class.java).configureEach { task ->
            task.doFirst {
                // if we are updating baselines,
                // then we don't want the creation of a new baseline to be considered a failure
                System.setProperty(LINT_BASELINE_CONTINUE, "true")
            }
        }
        tasks.withType(AndroidLintTask::class.java).configureEach { task ->
            // after we're done generating new baselines, we copy them back into the source tree
            task.finalizedBy(updateBaselineTask)
        }
    }

    // Lint is configured entirely in finalizeDsl so that individual projects cannot easily
    // disable individual checks in the DSL for any reason.
    val finalizeDsl: () -> Unit = {
        lintOptions.apply {
            if (!isTestingLintItself) {
                isAbortOnError = true
            }
            isIgnoreWarnings = true

            // Run lint on tests. Uses top-level lint.xml to specify checks.
            isCheckTestSources = true

            // Write output directly to the console (and nowhere else).
            textReport = true
            htmlReport = false

            // Format output for convenience.
            isExplainIssues = true
            isNoLines = false
            isQuiet = true

            // We run lint on each library, so we don't want transitive checking of each dependency
            isCheckDependencies = false

            fatal("VisibleForTests")

            // Disable dependency checks that suggest to change them. We want libraries to be
            // intentional with their dependency version bumps.
            disable("KtxExtensionAvailable")
            disable("GradleDependency")

            // Disable a check that's only relevant for real apps. For our test apps we're not
            // concerned with drawables potentially being a little bit blurry
            disable("IconMissingDensityFolder")

            // Disable a check that's only triggered by translation updates which are
            // outside of library owners' control, b/174655193
            disable("UnusedQuantity")

            // Disable until it works for our projects, b/171986505
            disable("JavaPluginLanguageLevel")

            // Disable the TODO check until we have a policy that requires it.
            disable("StopShip")

            // Broken in 7.0.0-alpha15 due to b/180408990
            disable("RestrictedApi")

            // Broken in 7.0.0-alpha15 due to b/187508590
            disable("InvalidPackage")

            // Reenable after upgradingto 7.1.0-beta01
            disable("SupportAnnotationUsage")

            // Provide stricter enforcement for project types intended to run on a device.
            if (extension.type.compilationTarget == CompilationTarget.DEVICE) {
                fatal("Assert")
                fatal("NewApi")
                fatal("ObsoleteSdkInt")
                fatal("NoHardKeywords")
                fatal("UnusedResources")
                fatal("KotlinPropertyAccess")
                fatal("LambdaLast")
                fatal("UnknownNullness")

                // Only override if not set explicitly.
                // Some Kotlin projects may wish to disable this.
                if (
                    severityOverrides!!["SyntheticAccessor"] == null &&
                    extension.type != LibraryType.SAMPLES
                ) {
                    fatal("SyntheticAccessor")
                }

                // Only check for missing translations in finalized (beta and later) modules.
                if (extension.mavenVersion?.isFinalApi() == true) {
                    fatal("MissingTranslation")
                } else {
                    disable("MissingTranslation")
                }
            } else {
                disable("BanUncheckedReflection")
            }

            // Broken in 7.0.0-alpha15 due to b/187343720
            disable("UnusedResources")

            if (extension.type == LibraryType.SAMPLES) {
                // TODO: b/190833328 remove if / when AGP will analyze dependencies by default
                //  This is needed because SampledAnnotationDetector uses partial analysis, and
                //  hence requires dependencies to be analyzed.
                isCheckDependencies = true
                // TODO: baselines from dependencies aren't used when we run lint with
                //  isCheckDependencies = true. NewApi was recently enabled for tests, and so
                //  there are a large amount of baselined issues that would be reported here
                //  again, and we don't want to add them to the baseline for the sample modules.
                //  Instead just temporarily disable this lint check until the underlying issues
                //  are fixed.
                disable("NewApi")
            }

            // Only run certain checks where API tracking is important.
            if (extension.type.checkApi is RunApiTasks.No) {
                disable("IllegalExperimentalApiUsage")
            }

            // If the project has not overridden the lint config, set the default one.
            if (lintConfig == null) {
                // suppress warnings more specifically than issue-wide severity (regexes)
                // Currently suppresses warnings from baseline files working as intended
                lintConfig = File(project.getSupportRootFolder(), "buildSrc/lint.xml")
            }

            // Ideally, teams aren't able to add new violations to a baseline file; they should only
            // be able to burn down existing violations. That's hard to enforce, though, so we'll
            // generally allow teams to update their baseline files with a publicly-known flag.
            if (updateLintBaseline) {
                // Continue generating baselines regardless of errors.
                isAbortOnError = false

                // Avoid printing every single lint error to the terminal.
                textReport = false
            }

            // If we give lint the filepath of a baseline file, then:
            //   If the file does not exist, lint will write new violations to it
            //   If the file does exist, lint will read extemptions from it

            // So, if we want to update the baselines, we need to give lint an empty location
            // to save to.

            // If we're not updating the baselines, then we want lint to check for new errors.
            // This requires us only pass a baseline to lint if one already exists.
            if (updateLintBaseline) {
                baseline(generatedLintBaseline)
            } else if (lintBaseline.exists()) {
                baseline(lintBaseline)
            }
        }
    }

    val androidComponents = extensions.findByType(
        AndroidComponentsExtension::class.java
    )
    if (null != androidComponents) {
        @Suppress("UnstableApiUsage")
        androidComponents.finalizeDsl { finalizeDsl() }
    } else {
        // Support the lint standalone plugin case which, as yet, lacks AndroidComponents DSL
        afterEvaluate { finalizeDsl() }
    }
}

val Project.lintBaseline get() = File(projectDir, "/lint-baseline.xml")
val Project.generatedLintBaseline get() = File(project.buildDir, "/generated-lint-baseline.xml")

/**
 * Task that copies the generated line baseline
 * If an input baseline file has no issues, it is considered to be nonexistent
 */
abstract class UpdateBaselineTask : DefaultTask() {
    @get:InputFiles // allows missing input file
    abstract val source: RegularFileProperty

    @get:OutputFile
    abstract val dest: RegularFileProperty

    @TaskAction
    fun copyBaseline() {
        val source = source.get().asFile
        val dest = dest.get().asFile
        val sourceText = if (source.exists()) {
            source.readText()
        } else {
            ""
        }
        var sourceHasIssues =
            if (source.exists()) {
                // Does the baseline contain any issues?
                source.reader().useLines { lines ->
                    lines.any { line ->
                        line.endsWith("<issue")
                    }
                }
            } else {
                false
            }
        val destNonempty = dest.exists()
        val changing = (sourceHasIssues != destNonempty) ||
            (sourceHasIssues && sourceText != dest.readText())
        if (changing) {
            if (sourceHasIssues) {
                Files.copy(source, dest)
                println("Updated baseline file ${dest.path}")
            } else {
                dest.delete()
                println("Deleted baseline file ${dest.path}")
            }
        }
    }
}

/**
 * Task that removes the specified lint baseline file.
 */
abstract class RemoveBaselineTask : DefaultTask() {
    @get:InputFiles // allows missing files
    abstract val baselineFile: RegularFileProperty

    @TaskAction
    fun removeBaseline() {
        val lintBaseline = baselineFile.get().asFile
        if (lintBaseline.exists()) {
            lintBaseline.delete()
        }
    }
}
