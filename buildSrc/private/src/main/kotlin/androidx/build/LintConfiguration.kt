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

import com.android.build.api.dsl.Lint
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.VariantInputs
import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

fun Project.configureNonAndroidProjectForLint(extension: AndroidXExtension) {
    apply(mapOf("plugin" to "com.android.lint"))

    // Create fake variant tasks since that is what is invoked by developers.
    val lintTask = tasks.named("lint")
    tasks.register("lintDebug") {
        it.dependsOn(lintTask)
        it.enabled = false
    }
    tasks.register("lintAnalyzeDebug") {
        it.enabled = false
    }
    tasks.register("lintRelease") {
        it.dependsOn(lintTask)
        it.enabled = false
    }
    addToBuildOnServer(lintTask)

    val lint = extensions.getByType<Lint>()
    // Support the lint standalone plugin case which, as yet, lacks AndroidComponents finalizeDsl
    afterEvaluate { configureLint(lint, extension, true) }
}

fun Project.configureAndroidProjectForLint(
    lint: Lint,
    extension: AndroidXExtension,
    isLibrary: Boolean
) {
    project.afterEvaluate {
        // makes sure that the lintDebug task will exist, so we can find it by name
        setUpLintDebugIfNeeded()
    }
    tasks.register("lintAnalyze") {
        it.enabled = false
    }
    configureLint(lint, extension, isLibrary)
    tasks.named("lint").configure { task ->
        // We already run lintDebug, we don't need to run lint which lints the release variant
        task.enabled = false
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

/**
 * Installs AIDL source directories on lint tasks. Adapted from AndroidXComposeImplPlugin's
 * `configureLintForMultiplatformLibrary` extension function. See b/189250111 for feature request.
 *
 * The `UnstableAidlAnnotationDetector` check from `lint-checks` requires that _only_ unstable AIDL
 * files are passed to Lint, e.g. files in the AGP-defined `aidl` source set but not files in the
 * Stable AIDL plugin-defined `stableAidl` source set. If we decide to lint Stable AIDL files, we'll
 * need some other way to distinguish stable from unstable AIDL.
 */
fun Project.configureLintForAidl() {
    afterEvaluate {
        val extension = project.extensions.findByType<BaseExtension>() ?: return@afterEvaluate
        if (extension.buildFeatures.aidl != true) return@afterEvaluate

        val mainAidl = extension.sourceSets.getByName("main").aidl.getSourceFiles()

        /**
         * Helper function to add the missing sourcesets to this [VariantInputs]
         */
        fun VariantInputs.addSourceSets() {
            // Each variant has a source provider for the variant (such as debug) and the 'main'
            // variant. The actual files that Lint will run on is both of these providers
            // combined - so we can just add the dependencies to the first we see.
            val variantAidl = extension.sourceSets.getByName(name.get()).aidl.getSourceFiles()
            val sourceProvider = sourceProviders.get().firstOrNull() ?: return
            sourceProvider.javaDirectories.withChangesAllowed {
                from(mainAidl, variantAidl)
            }
        }

        // Lint for libraries is split into two tasks - analysis, and reporting. We need to
        // add the new sources to both, so all parts of the pipeline are aware.
        project.tasks.withType<AndroidLintAnalysisTask>().configureEach {
            it.variantInputs.addSourceSets()
        }

        project.tasks.withType<AndroidLintTask>().configureEach {
            it.variantInputs.addSourceSets()
        }

        // Also configure the model writing task, so that we don't run into mismatches between
        // analyzed sources in one module and a downstream module
        project.tasks.withType<LintModelWriterTask>().configureEach {
            it.variantInputs.addSourceSets()
        }
    }
}

fun Project.configureLint(lint: Lint, extension: AndroidXExtension, isLibrary: Boolean) {
    val lintChecksProject = project.rootProject.findProject(":lint-checks")
        ?: if (allowMissingLintProject()) {
            return
        } else {
            throw GradleException("Project :lint-checks does not exist")
        }

    project.dependencies.add("lintChecks", lintChecksProject)

    project.configureLintForAidl()

    // The purpose of this specific project is to test that lint is running, so
    // it contains expected violations that we do not want to trigger a build failure
    val isTestingLintItself = (project.path == ":lint-checks:integration-tests")

    lint.apply {
        // Skip lintVital tasks on assemble. We explicitly run lintRelease for libraries.
        checkReleaseBuilds = false
    }

    tasks.withType(AndroidLintTask::class.java).configureEach { task ->
        // Remove the lint and column attributes from generated lint baseline XML.
        if (task.name.startsWith("updateLintBaseline")) {
            task.doLast {
                task.projectInputs.lintOptions.baseline.orNull?.asFile?.let { file ->
                    if (file.exists()) {
                        file.writeText(removeLineAndColumnAttributes(file.readText()))
                    }
                }
            }
        }
    }

    // Lint is configured entirely in finalizeDsl so that individual projects cannot easily
    // disable individual checks in the DSL for any reason.
    lint.apply {
        if (!isTestingLintItself) {
            abortOnError = true
        }
        ignoreWarnings = true

        // Run lint on tests. Uses top-level lint.xml to specify checks.
        checkTestSources = true

        // Write output directly to the console (and nowhere else).
        textReport = true
        htmlReport = false

        // Format output for convenience.
        explainIssues = true
        noLines = false
        quiet = true

        // We run lint on each library, so we don't want transitive checking of each dependency
        checkDependencies = false

        if (extension.type.allowCallingVisibleForTestsApis) {
            // Test libraries are allowed to call @VisibleForTests code
            disable.add("VisibleForTests")
        } else {
            fatal.add("VisibleForTests")
        }

        // Reenable after b/238892319 is resolved
        disable.add("NotificationPermission")

        // Disable dependency checks that suggest to change them. We want libraries to be
        // intentional with their dependency version bumps.
        disable.add("KtxExtensionAvailable")
        disable.add("GradleDependency")

        // Disable a check that's only relevant for real apps. For our test apps we're not
        // concerned with drawables potentially being a little bit blurry
        disable.add("IconMissingDensityFolder")

        // Disable until it works for our projects, b/171986505
        disable.add("JavaPluginLanguageLevel")

        // Explicitly disable StopShip check (see b/244617216)
        disable.add("StopShip")

        // Broken in 7.0.0-alpha15 due to b/180408990
        disable.add("RestrictedApi")

        // Disable until ag/19949626 goes in (b/261918265)
        disable.add("MissingQuantity")

        // Provide stricter enforcement for project types intended to run on a device.
        if (extension.type.compilationTarget == CompilationTarget.DEVICE) {
            fatal.add("Assert")
            fatal.add("NewApi")
            fatal.add("ObsoleteSdkInt")
            fatal.add("NoHardKeywords")
            fatal.add("UnusedResources")
            fatal.add("KotlinPropertyAccess")
            fatal.add("LambdaLast")
            fatal.add("UnknownNullness")

            // Only override if not set explicitly.
            // Some Kotlin projects may wish to disable this.
            if (
                isLibrary &&
                !disable.contains("SyntheticAccessor") &&
                extension.type != LibraryType.SAMPLES
            ) {
                fatal.add("SyntheticAccessor")
            }

            // Only check for missing translations in finalized (beta and later) modules.
            if (extension.mavenVersion?.isFinalApi() == true) {
                fatal.add("MissingTranslation")
            } else {
                disable.add("MissingTranslation")
            }
        } else {
            disable.add("BanUncheckedReflection")
        }

        // Broken in 7.0.0-alpha15 due to b/187343720
        disable.add("UnusedResources")

        // Disable NullAnnotationGroup check for :compose:ui:ui-text (b/233788571)
        if (isLibrary && project.group == "androidx.compose.ui" && project.name == "ui-text") {
            disable.add("NullAnnotationGroup")
        }

        if (extension.type == LibraryType.SAMPLES) {
            // TODO: b/190833328 remove if / when AGP will analyze dependencies by default
            //  This is needed because SampledAnnotationDetector uses partial analysis, and
            //  hence requires dependencies to be analyzed.
            checkDependencies = true
        }

        // Only run certain checks where API tracking is important.
        if (extension.type.checkApi is RunApiTasks.No) {
            disable.add("IllegalExperimentalApiUsage")
        }

        // If the project has not overridden the lint config, set the default one.
        if (lintConfig == null) {
            val lintXmlPath = if (extension.type == LibraryType.SAMPLES) {
                "buildSrc/lint_samples.xml"
            } else {
                "buildSrc/lint.xml"
            }
            // suppress warnings more specifically than issue-wide severity (regexes)
            // Currently suppresses warnings from baseline files working as intended
            lintConfig = File(project.getSupportRootFolder(), lintXmlPath)
        }

        baseline = lintBaseline.get().asFile
    }
}

/**
 * Lint on multiplatform  projects is only applied to Java code and android source sets. To force it
 * to run on JVM code, we add the java source sets that lint looks for, but use the sources
 * directories of the JVM source sets if they exist.
 */
fun Project.configureLintForMultiplatform(extension: AndroidXExtension) = afterEvaluate {
    // if lint has been applied through some other mechanism, this step is unnecessary
    runCatching { project.tasks.named("lint") }.onSuccess { return@afterEvaluate }
    val jvmTarget = project.multiplatformExtension?.targets?.findByName("jvm")
        ?: return@afterEvaluate
    val runtimeConfiguration = project.configurations.findByName("jvmRuntimeElements")
        ?: return@afterEvaluate
    val apiConfiguration = project.configurations.findByName("jvmApiElements")
        ?: return@afterEvaluate
    val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
        ?: return@afterEvaluate
    project.configurations.maybeCreate("runtimeElements").apply {
        extendsFrom(runtimeConfiguration)
    }
    project.configurations.maybeCreate("apiElements").apply {
        extendsFrom(apiConfiguration)
    }
    val mainSourceSets = jvmTarget
        .compilations
        .getByName("main")
        .kotlinSourceSets
    val testSourceSets = jvmTarget
        .compilations
        .getByName("test")
        .kotlinSourceSets
    javaExtension.sourceSets.maybeCreate("main").apply {
        java.setSrcDirs(mainSourceSets.flatMap { it.kotlin.srcDirs })
        java.classesDirectory
    }
    javaExtension.sourceSets.maybeCreate("test").apply {
        java.srcDirs.addAll(testSourceSets.flatMap { it.kotlin.srcDirs })
    }
    project.configureNonAndroidProjectForLint(extension)

    // Disable classfile based checks because lint cannot find the classfiles for multiplatform
    // projects, and SourceSet.java.classesDirectory is not configurable. This is not ideal, but
    // better than having no lint checks at all.
    extensions.getByType<Lint>().disable.add("LintError")
}

/**
 * Lint uses [ConfigurableFileCollection.disallowChanges] during initialization, which prevents
 * modifying the file collection separately (there is no time to configure it before AGP has
 * initialized and disallowed changes). This uses reflection to temporarily allow changes, and
 * apply [block].
 */
private fun ConfigurableFileCollection.withChangesAllowed(
    block: ConfigurableFileCollection.() -> Unit
) {
    val disallowChanges = this::class.java.getDeclaredField("disallowChanges")
    disallowChanges.isAccessible = true
    disallowChanges.set(this, false)
    block()
    disallowChanges.set(this, true)
}

val Project.lintBaseline: RegularFileProperty get() =
    project.objects.fileProperty().fileValue(File(projectDir, "/lint-baseline.xml"))
