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

import androidx.benchmark.gradle.BenchmarkPlugin
import androidx.build.SupportConfig.BUILD_TOOLS_VERSION
import androidx.build.SupportConfig.COMPILE_SDK_VERSION
import androidx.build.SupportConfig.DEFAULT_MIN_SDK_VERSION
import androidx.build.SupportConfig.INSTRUMENTATION_RUNNER
import androidx.build.SupportConfig.TARGET_SDK_VERSION
import androidx.build.checkapi.ApiType
import androidx.build.checkapi.getApiLocation
import androidx.build.checkapi.getRequiredCompatibilityApiFileFromDir
import androidx.build.checkapi.hasApiFolder
import androidx.build.dependencyTracker.AffectedModuleDetector
import androidx.build.dokka.Dokka.configureAndroidProjectForDokka
import androidx.build.dokka.Dokka.configureJavaProjectForDokka
import androidx.build.dokka.DokkaPublicDocs
import androidx.build.dokka.DokkaSourceDocs
import androidx.build.gmaven.GMavenVersionChecker
import androidx.build.gradle.getByType
import androidx.build.gradle.isRoot
import androidx.build.jacoco.Jacoco
import androidx.build.license.CheckExternalDependencyLicensesTask
import androidx.build.license.configureExternalDependencyLicenseCheck
import androidx.build.metalava.CREATE_STUB_API_JAR_TASK
import androidx.build.metalava.MetalavaTasks.configureAndroidProjectForMetalava
import androidx.build.metalava.MetalavaTasks.configureJavaProjectForMetalava
import androidx.build.metalava.UpdateApiTask
import androidx.build.releasenotes.GenerateReleaseNotesTask
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.ApkVariant
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Setting this property indicates that a build is being performed to check for forward
 * compatibility.
 */
const val USE_MAX_DEP_VERSIONS = "useMaxDepVersions"
const val BUILD_INFO_DIR = "build-info"
const val BUILD_ON_SERVER_DEPENDENT_ACTIONS = "buildOnServerDependentActions"

/**
 * A plugin which enables all of the Gradle customizations for AndroidX.
 * This plugin reacts to other plugins being added and adds required and optional functionality.
 */
class AndroidXPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // This has to be first due to bad behavior by DiffAndDocs which is triggered on the root
        // project. It calls evaluationDependsOn on each subproject. This eagerly causes evaluation
        // *during* the root build.gradle evaluation. The subproject then applies this plugin (while
        // we're still halfway through applying it on the root). The check licenses code runs on the
        // subproject which then looks for the root project task to add itself as a dependency of.
        // Without the root project having created the task prior to DiffAndDocs running this fails.
        // TODO do not use evaluationDependsOn in DiffAndDocs to break this cycle!
        project.configureExternalDependencyLicenseCheck()

        val androidXExtension =
            project.extensions.create("androidx", AndroidXExtension::class.java, project)

        // This has to be first due to bad behavior by DiffAndDocs. It fails if this configuration
        // is called after DiffAndDocs.configureDiffAndDocs. b/129762955
        project.configureMavenArtifactUpload(androidXExtension)

        if (project.isRoot) {
            project.configureRootProject()
        }

        project.plugins.all { plugin ->
            when (plugin) {
                is JavaPlugin,
                is JavaLibraryPlugin -> {
                    project.configureErrorProneForJava()
                    project.configureSourceJarForJava()
                    val convention = project.convention.getPlugin<JavaPluginConvention>()
                    convention.apply {
                        sourceCompatibility = VERSION_1_8
                        targetCompatibility = VERSION_1_8
                    }

                    project.hideJavadocTask()
                    val verifyDependencyVersionsTask = project.createVerifyDependencyVersionsTask()
                    verifyDependencyVersionsTask?.configure { task ->
                        task.dependsOn(project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME))
                    }
                    project.addCreateLibraryBuildInfoFileTask(androidXExtension)
                    if (verifyDependencyVersionsTask != null) {
                        project.createCheckReleaseReadyTask(listOf(verifyDependencyVersionsTask))
                    }
                    project.createGenerateReleaseNotesTask()
                    project.configureNonAndroidProjectForLint(androidXExtension)
                    project.configureJavaProjectForDokka(androidXExtension)
                    project.configureJavaProjectForMetalava(androidXExtension)
                    project.configureJacoco()
                    project.addToProjectMap(androidXExtension)
                    // workaround for b/120487939
                    project.configurations.all { configuration ->
                        configuration.resolutionStrategy.preferProjectModules()
                    }
                }
                is LibraryPlugin -> {
                    val extension = project.extensions.getByType<LibraryExtension>().apply {
                        configureAndroidCommonOptions(project, androidXExtension)
                        configureAndroidLibraryOptions(project, androidXExtension)
                    }
                    project.configureSourceJarForAndroid(extension)
                    project.configureVersionFileWriter(extension, androidXExtension)
                    project.configureResourceApiChecks(extension)
                    project.addCreateLibraryBuildInfoFileTask(androidXExtension)
                    val verifyDependencyVersionsTask = project.createVerifyDependencyVersionsTask()
                    val checkReleaseReadyTasks = mutableListOf<TaskProvider<out Task>>()
                    if (verifyDependencyVersionsTask != null) {
                        checkReleaseReadyTasks.add(verifyDependencyVersionsTask)
                    }
                    if (checkReleaseReadyTasks.isNotEmpty()) {
                        project.createCheckReleaseReadyTask(checkReleaseReadyTasks)
                    }
                    project.createGenerateReleaseNotesTask()
                    val reportLibraryMetrics = project.tasks.register(
                        REPORT_LIBRARY_METRICS, ReportLibraryMetricsTask::class.java
                    )

                    extension.defaultPublishVariant { libraryVariant ->
                        reportLibraryMetrics.configure {
                            it.debugVariants.add(libraryVariant)
                        }

                        verifyDependencyVersionsTask?.configure { task ->
                            task.dependsOn(libraryVariant.javaCompileProvider)
                        }
                        libraryVariant.javaCompileProvider.configure { task ->
                            project.runIfPartOfBuildOnServer {
                                task.options.compilerArgs.add("-Werror")
                                task.options.compilerArgs.add("-Xlint:unchecked")
                            }
                        }
                    }
                    project.configureLint(extension.lintOptions, androidXExtension)
                    project.configureAndroidProjectForDokka(extension, androidXExtension)
                    project.configureAndroidProjectForMetalava(extension, androidXExtension)
                    project.addToProjectMap(androidXExtension)
                }
                is AppPlugin -> {
                    project.extensions.getByType<AppExtension>().apply {
                        configureAndroidCommonOptions(project, androidXExtension)
                        configureAndroidApplicationOptions(project)
                    }
                }
                is KotlinBasePluginWrapper -> {
                    project.tasks.withType(KotlinCompile::class.java).configureEach { compile ->
                        compile.kotlinOptions.jvmTarget = "1.8"
                        project.runIfPartOfBuildOnServer {
                            compile.kotlinOptions.allWarningsAsErrors = true
                        }
                    }
                    if (plugin is KotlinMultiplatformPluginWrapper) {
                        project.extensions.findByType<LibraryExtension>()?.apply {
                            configureAndroidLibraryWithMultiplatformPluginOptions()
                        }
                    }
                }
            }
        }

        project.configureKtlint()

        // Disable timestamps and ensure filesystem-independent archive ordering to maximize
        // cross-machine byte-for-byte reproducibility of artifacts.
        project.tasks.withType(Jar::class.java).configureEach { task ->
            task.isReproducibleFileOrder = true
            task.isPreserveFileTimestamps = false
        }

        // copy host side test results to DIST
        project.tasks.withType(Test::class.java) { task ->
            val report = task.reports.junitXml
            if (report.isEnabled) {
                val zipTask = project.tasks.register(
                    "zipResultsOf${task.name.capitalize()}",
                    Zip::class.java
                ) {
                    it.destinationDirectory.set(project.getHostTestResultDirectory())
                    // first one is always :, drop it.
                    it.archiveFileName.set(
                        "${project.path.split(":").joinToString("_").substring(1)}.zip")
                }
                if (isRunningOnBuildServer()) {
                    task.ignoreFailures = true
                }
                task.finalizedBy(zipTask)
                task.doFirst {
                    zipTask.configure {
                        it.from(report.destination)
                    }
                }
            }
        }
    }

    private fun Project.configureRootProject() {
        if (isRunningOnBuildServer()) {
            gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS
        }
        val createAggregateBuildInfoFilesTask =
            tasks.register(CREATE_AGGREGATE_BUILD_INFO_FILES_TASK,
                CreateAggregateLibraryBuildInfoFileTask::class.java)
        val createLibraryBuildInfoFilesTask =
            tasks.register(CREATE_LIBRARY_BUILD_INFO_FILES_TASK)

        extra.set("versionChecker", GMavenVersionChecker(logger))
        val createArchiveTask = Release.getGlobalFullZipTask(this)

        val buildOnServerTask = tasks.create(BUILD_ON_SERVER_TASK, BuildOnServer::class.java)
        buildOnServerTask.dependsOn(createArchiveTask)
        buildOnServerTask.dependsOn(createAggregateBuildInfoFilesTask)
        buildOnServerTask.dependsOn(createLibraryBuildInfoFilesTask)

        val partiallyDejetifyArchiveTask = partiallyDejetifyArchiveTask(
            createArchiveTask.get().archiveFile)
        if (partiallyDejetifyArchiveTask != null)
            buildOnServerTask.dependsOn(partiallyDejetifyArchiveTask)

        val projectModules = ConcurrentHashMap<String, String>()
        extra.set("projects", projectModules)
        tasks.all { task ->
            if (task.name.startsWith(Release.DIFF_TASK_PREFIX) ||
                    "distDocs" == task.name ||
                    CheckExternalDependencyLicensesTask.TASK_NAME == task.name) {
                buildOnServerTask.dependsOn(task)
            }
        }
        subprojects { project ->
            if (project.path == ":docs-runner") {
                project.tasks.all { task ->
                    if (DokkaPublicDocs.ARCHIVE_TASK_NAME == task.name ||
                        DokkaSourceDocs.ARCHIVE_TASK_NAME == task.name) {
                        buildOnServerTask.dependsOn(task)
                    }
                }
                return@subprojects
            }
            project.tasks.all { task ->
                if ("assembleAndroidTest" == task.name ||
                        "assembleDebug" == task.name ||
                        ERROR_PRONE_TASK == task.name ||
                        "jar" == task.name ||
                        "verifyDependencyVersions" == task.name ||
                        "reportLibraryMetrics" == task.name ||
                        CREATE_STUB_API_JAR_TASK == task.name ||
                        BUILD_ON_SERVER_TASK == task.name ||
                        ("lintDebug" == task.name &&
                        !project.rootProject.hasProperty(USE_MAX_DEP_VERSIONS))) {
                    buildOnServerTask.dependsOn(task)
                }
            }
        }

        if (partiallyDejetifyArchiveTask != null) {
            project(":jetifier-standalone").afterEvaluate { standAloneProject ->
                partiallyDejetifyArchiveTask.configure {
                    it.dependsOn(standAloneProject.tasks.named("installDist"))
                }
                createArchiveTask.configure {
                    it.dependsOn(standAloneProject.tasks.named("dist"))
                }
            }
        }

        val createCoverageJarTask = Jacoco.createCoverageJarTask(this)
        buildOnServerTask.dependsOn(createCoverageJarTask)

        tasks.register(BUILD_TEST_APKS) {
            it.dependsOn(createCoverageJarTask)
        }

        val rootProjectDir = SupportConfig.getSupportRoot(rootProject).canonicalFile
        val allDocsTask = DiffAndDocs.configureDiffAndDocs(this, rootProjectDir,
                DacOptions("androidx", "ANDROIDX_DATA"),
                listOf(RELEASE_RULE))
        buildOnServerTask.dependsOn(allDocsTask)

        val jacocoUberJar = Jacoco.createUberJarTask(this)
        buildOnServerTask.dependsOn(jacocoUberJar)
        val checkSameVersionLibraryGroupsTask = tasks.register(
            CHECK_SAME_VERSION_LIBRARY_GROUPS,
            CheckSameVersionLibraryGroupsTask::class.java)
        buildOnServerTask.dependsOn(checkSameVersionLibraryGroupsTask)

        AffectedModuleDetector.configure(gradle, this)

        // If useMaxDepVersions is set, iterate through all the project and substitute any androidx
        // artifact dependency with the local tip of tree version of the library.
        if (hasProperty(USE_MAX_DEP_VERSIONS)) {
            // This requires evaluating all sub-projects to create the module:project map
            // and project dependencies.
            evaluationDependsOnChildren()
            subprojects { project ->
                project.configurations.all { configuration ->
                    project.afterEvaluate {
                        val androidXExtension =
                            project.extensions.getByType(AndroidXExtension::class.java)
                        // Substitute only for debug configurations/tasks only because we can not
                        // change release dependencies after evaluation. Test hooks, buildOnServer
                        // and buildTestApks use the debug configurations as well.
                        if (androidXExtension.publish.shouldRelease() &&
                            configuration.name.toLowerCase().contains("debug")
                        ) {
                            configuration.resolutionStrategy.dependencySubstitution.apply {
                                for (e in projectModules) {
                                    substitute(module(e.key)).with(project(e.value))
                                }
                            }
                        }
                    }
                }
            }
        }
        // If we are running buildOnServer, run all actions in buildOnServerDependentActions after
        // the task graph has been resolved, before we are in the execution phase.
        project.gradle.taskGraph.whenReady { taskExecutionGraph ->
            // hasTask requires the task path, so we are looking for the root :buildOnServer task
            if (taskExecutionGraph.hasTask(":$BUILD_ON_SERVER_TASK")) {
                buildOnServerDependentActions.forEach { it() }
            }
        }
    }

    private fun TestedExtension.configureAndroidCommonOptions(
        project: Project,
        androidXExtension: AndroidXExtension
    ) {
        compileOptions.apply {
            sourceCompatibility = VERSION_1_8
            targetCompatibility = VERSION_1_8
        }

        // Force AGP to use our version of JaCoCo
        jacoco.version = Jacoco.VERSION
        compileSdkVersion(COMPILE_SDK_VERSION)
        buildToolsVersion = BUILD_TOOLS_VERSION
        defaultConfig.targetSdkVersion(TARGET_SDK_VERSION)

        defaultConfig.testInstrumentationRunner = INSTRUMENTATION_RUNNER

        // Enable code coverage for debug builds only if we are not running inside the IDE, since
        // enabling coverage reports breaks the method parameter resolution in the IDE debugger.
        buildTypes.getByName("debug").isTestCoverageEnabled =
            !project.hasProperty("android.injected.invoked.from.ide")

        // Pass the --no-window-animation flag with a hack (b/138120842)
        // NOTE - We're exploiting the fact that anything after a space in the value of a
        // instrumentation runner argument is passed raw to the `am instrument` command.
        // NOTE - instrumentation args aren't respected by CI - window animations are
        // disabled there separately
        defaultConfig.testInstrumentationRunnerArgument("thisisignored",
            "thisisignored --no-window-animation")
        testOptions.unitTests.isReturnDefaultValues = true

        defaultConfig.minSdkVersion(DEFAULT_MIN_SDK_VERSION)
        project.afterEvaluate {
            val minSdkVersion = defaultConfig.minSdkVersion.apiLevel
            check(minSdkVersion >= DEFAULT_MIN_SDK_VERSION) {
                "minSdkVersion $minSdkVersion lower than the default of $DEFAULT_MIN_SDK_VERSION"
            }
            project.configurations.all { configuration ->
                configuration.resolutionStrategy.eachDependency { dep ->
                    val target = dep.target
                    val version = target.version
                    // Enforce the ban on declaring dependencies with version ranges.
                    if (version != null && Version.isDependencyRange(version)) {
                        throw IllegalArgumentException(
                                "Dependency ${dep.target} declares its version as " +
                                        "version range ${dep.target.version} however the use of " +
                                        "version ranges is not allowed, please update the " +
                                        "dependency to list a fixed version.")
                    }
                }
            }

            if (androidXExtension.compilationTarget != CompilationTarget.DEVICE) {
                throw IllegalStateException(
                    "Android libraries must use a compilation target of DEVICE")
            }
        }

        val debugSigningConfig = signingConfigs.getByName("debug")
        // Use a local debug keystore to avoid build server issues.
        debugSigningConfig.storeFile = SupportConfig.getKeystore(project)
        buildTypes.all { buildType ->
            // Sign all the builds (including release) with debug key
            buildType.signingConfig = debugSigningConfig
        }

        // Disable generating BuildConfig.java
        // TODO remove after https://issuetracker.google.com/72050365
        variants.all { variant ->
            variant.generateBuildConfigProvider.configure {
                it.enabled = false
            }
        }

        project.configureErrorProneForAndroid(variants)

        // Set the officially published version to be the debug version with minimum dependency
        // versions.
        defaultPublishConfig(Release.DEFAULT_PUBLISH_CONFIG)

        // workaround for b/120487939
        project.configurations.all { configuration ->
            // Gradle seems to crash on androidtest configurations
            // preferring project modules...
            if (!configuration.name.toLowerCase().contains("androidtest")) {
                configuration.resolutionStrategy.preferProjectModules()
            }
        }

        Jacoco.registerClassFilesTask(project, this)

        val buildTestApksTask = project.rootProject.tasks.named(BUILD_TEST_APKS)
        testVariants.all { variant ->
            buildTestApksTask.configure {
                it.dependsOn(variant.assembleProvider)
            }
            variant.configureApkCopy(project, this, true)
        }
    }

    private fun hasAndroidTestSourceCode(project: Project, extension: TestedExtension): Boolean {
        // check Java androidTest source set
        extension.sourceSets.findByName("androidTest")?.let { sourceSet ->
            if (!sourceSet.java.sourceFiles.isEmpty) return true
        }

        // check kotlin-android androidTest source set
        project.extensions.findByType(KotlinAndroidProjectExtension::class.java)
            ?.sourceSets?.findByName("androidTest")?.let {
            if (it.kotlin.files.isNotEmpty()) return true
        }

        // check kotlin-multiplatform androidAndroidTest source set
        project.multiplatformExtension?.apply {
            sourceSets.findByName("androidAndroidTest")?.let {
                if (it.kotlin.files.isNotEmpty()) return true
            }
        }

        return false
    }

    private fun ApkVariant.configureApkCopy(
        project: Project,
        extension: TestedExtension,
        testApk: Boolean
    ) {
        packageApplicationProvider.configure { packageTask ->
            AffectedModuleDetector.configureTaskGuard(packageTask)
            packageTask.doLast {
                // Skip copying AndroidTest apks if they have no source code (no tests to run).
                if (testApk && !hasAndroidTestSourceCode(project, extension)) return@doLast

                project.copy {
                    it.from(packageTask.outputDirectory)
                    it.include("*.apk")
                    it.into(File(project.getDistributionDirectory(), "apks"))
                    it.rename { fileName ->
                        if (fileName.contains("media-compat-test") ||
                            fileName.contains("media2-test")) {
                            // Exclude media-compat-test-* and media2-test-* modules from
                            // existing support library presubmit tests.
                            fileName.replace("-debug-androidTest", "")
                        } else if (project.plugins.hasPlugin(BenchmarkPlugin::class.java)) {
                            // Exclude '-benchmark' modules from correctness tests
                            fileName.replace("-androidTest", "-androidBenchmark")
                        } else {
                            // multiple modules may have the same name so prefix the name with
                            // the module's path to ensure it is unique.
                            // e.g. palette-v7-debug-androidTest.apk becomes
                            // support-palette-v7_palette-v7-debug-androidTest.apk
                            "${project.path.replace(':', '-').substring(1)}_$fileName"
                        }
                    }
                }
            }
        }
    }

    private fun LibraryExtension.configureAndroidLibraryOptions(
        project: Project,
        androidXExtension: AndroidXExtension
    ) {

        project.configurations.all { config ->
            val isTestConfig = config.name.toLowerCase().contains("test")

            config.dependencyConstraints.configureEach { dependencyConstraint ->
                dependencyConstraint.apply {
                    // Remove strict constraints on test dependencies and listenablefuture:1.0
                    if (isTestConfig ||
                        group == "com.google.guava" &&
                        name == "listenablefuture" &&
                        version == "1.0") {
                        version { versionConstraint ->
                            versionConstraint.strictly("")
                        }
                    }
                }
            }
        }

        project.afterEvaluate {
            defaultPublishVariant { libraryVariant ->
                libraryVariant.javaCompileProvider.configure { javaCompile ->
                    if (androidXExtension.failOnDeprecationWarnings) {
                        javaCompile.options.compilerArgs.add("-Xlint:deprecation")
                    }
                }
            }
        }
    }

    private fun TestedExtension.configureAndroidLibraryWithMultiplatformPluginOptions() {
        sourceSets.findByName("main")!!.manifest.srcFile("src/androidMain/AndroidManifest.xml")
        sourceSets.findByName("androidTest")!!
            .manifest.srcFile("src/androidAndroidTest/AndroidManifest.xml")
    }

    private fun AppExtension.configureAndroidApplicationOptions(project: Project) {
        defaultConfig.apply {
            versionCode = 1
            versionName = "1.0"
        }

        lintOptions.apply {
            isAbortOnError = true

            val baseline = project.lintBaseline
            if (baseline.exists()) {
                baseline(baseline)
            }
        }

        val buildTestApksTask = project.rootProject.tasks.named(BUILD_TEST_APKS)
        applicationVariants.all { variant ->
            if (variant.buildType.name == "debug") {
                buildTestApksTask.configure {
                    it.dependsOn(variant.assembleProvider)
                }
            }
            variant.configureApkCopy(project, this, false)
        }
    }

    private fun Project.createVerifyDependencyVersionsTask():
            TaskProvider<VerifyDependencyVersionsTask>? {
        /**
         * Ignore -PuseMaxDepVersions when verifying dependency versions because it is a
         * hypothetical build which is only intended to check for forward compatibility.
         */
        if (hasProperty(USE_MAX_DEP_VERSIONS)) {
            return null
        }

        return tasks.register("verifyDependencyVersions",
                VerifyDependencyVersionsTask::class.java)
    }

    // Task that creates release notes
    private fun Project.createGenerateReleaseNotesTask() {
        tasks.register(
            GENERATE_RELEASE_NOTES_TASK,
            GenerateReleaseNotesTask::class.java
        ) { task ->
            if (project.hasProperty("startCommit")) {
                task.startSHA = project.property("startCommit").toString()
            } else {
                task.startSHA = ""
            }
            if (project.hasProperty("endCommit")) {
                task.endSHA = project.property("endCommit").toString()
            } else {
                task.endSHA = "HEAD"
            }
            val formatter = DateTimeFormatter.ofPattern("MM-d-yyyy")
            if (project.hasProperty("releaseDate")) {
                task.date = LocalDate.parse(project.property("releaseDate").toString(), formatter)
            } else {
                task.date = LocalDate.now()
            }
            task.outputFile.set(
                File(
                    project.getReleaseNotesDirectory(),
                    "${group}_${name}_release_notes.txt"
                )
            )
        }
    }

    // Task that creates a json file of a project's dependencies
    private fun Project.addCreateLibraryBuildInfoFileTask(extension: AndroidXExtension) {
        afterEvaluate {
            if (extension.publish.shouldRelease()) {
                // Only generate build info files for published libraries.
                val task = tasks.register(
                    CREATE_LIBRARY_BUILD_INFO_FILES_TASK,
                    CreateLibraryBuildInfoFileTask::class.java
                ) {
                    it.outputFile.set(File(project.getBuildInfoDirectory(),
                        "${group}_${name}_build_info.txt"))
                }
                rootProject.tasks.named(CREATE_LIBRARY_BUILD_INFO_FILES_TASK).configure {
                    it.dependsOn(task)
                }
                addTaskToAggregateBuildInfoFileTask(task)
            }
        }
    }

    private fun Project.addTaskToAggregateBuildInfoFileTask(
        task: TaskProvider<CreateLibraryBuildInfoFileTask>
    ) {
        rootProject.tasks.named(CREATE_AGGREGATE_BUILD_INFO_FILES_TASK).configure {
            var aggregateLibraryBuildInfoFileTask: CreateAggregateLibraryBuildInfoFileTask = it
                    as CreateAggregateLibraryBuildInfoFileTask
            aggregateLibraryBuildInfoFileTask.dependsOn(task)
            aggregateLibraryBuildInfoFileTask.libraryBuildInfoFiles.add(
                task.flatMap { it.outputFile }
            )
        }
    }

    private fun Project.configureJacoco() {
        apply(plugin = "jacoco")
        configure<JacocoPluginExtension> {
            toolVersion = Jacoco.VERSION
        }

        tasks.withType(JacocoReport::class.java).configureEach { task ->
            task.reports {
                it.xml.isEnabled = true
                it.html.isEnabled = false
                it.csv.isEnabled = false

                it.xml.destination = File(getHostTestCoverageDirectory(),
                    "${path.replace(':', '-').substring(1)}.xml")
            }
        }
    }

    companion object {
        const val BUILD_ON_SERVER_TASK = "buildOnServer"
        const val BUILD_TEST_APKS = "buildTestApks"
        const val CHECK_RELEASE_READY_TASK = "checkReleaseReady"
        const val CHECK_SAME_VERSION_LIBRARY_GROUPS = "checkSameVersionLibraryGroups"
        const val CREATE_LIBRARY_BUILD_INFO_FILES_TASK = "createLibraryBuildInfoFiles"
        const val CREATE_AGGREGATE_BUILD_INFO_FILES_TASK = "createAggregateBuildInfoFiles"
        const val GENERATE_RELEASE_NOTES_TASK = "generateReleaseNotes"
        const val REPORT_LIBRARY_METRICS = "reportLibraryMetrics"
    }
}

fun Project.hideJavadocTask() {
    // Most tasks named "javadoc" are unused
    // So, few tasks named "javadoc" are interesting to developers
    // So, we don't want "javadoc" to appear in the output of `./gradlew tasks`
    // So, we set the group to null for any task named "javadoc"
    tasks.all { task ->
        if (task.name == "javadoc") {
            task.group = null
        }
    }
}

fun Project.addToProjectMap(extension: AndroidXExtension) {
    afterEvaluate {
        if (extension.publish.shouldRelease()) {
            val group = extension.mavenGroup?.group
            if (group != null) {
                val module = "$group:$name"
                @Suppress("UNCHECKED_CAST")
                val projectModules = getProjectsMap()
                projectModules[module] = path
            }
        }
    }
}

val Project.multiplatformExtension
    get() = extensions.findByType(KotlinMultiplatformExtension::class.java)

private fun Project.createCheckResourceApiTask(): DefaultTask {
    return tasks.createWithConfig("checkResourceApi",
            CheckResourceApiTask::class.java) {
        newApiFile = getGenerateResourceApiFile()
        oldApiFile = getApiLocation().resourceFile
    }
}

private fun Project.createCheckReleaseReadyTask(taskProviderList: List<TaskProvider<out Task>>) {
    tasks.register(AndroidXPlugin.CHECK_RELEASE_READY_TASK) {
        for (taskProvider in taskProviderList) {
            it.dependsOn(taskProvider)
        }
    }
}

private fun Project.createUpdateResourceApiTask(): DefaultTask {
    return tasks.createWithConfig("updateResourceApi", UpdateResourceApiTask::class.java) {
        newApiFile = getGenerateResourceApiFile()
        oldApiFile = getRequiredCompatibilityApiFileFromDir(File(projectDir, "api/"),
                version(), ApiType.RESOURCEAPI)
        destApiFile = getApiLocation().resourceFile
    }
}

@Suppress("UNCHECKED_CAST")
fun Project.getProjectsMap(): ConcurrentHashMap<String, String> {
    return rootProject.extra.get("projects") as ConcurrentHashMap<String, String>
}

private fun Project.configureResourceApiChecks(extension: LibraryExtension) {
    afterEvaluate {
        if (hasApiFolder()) {
            val checkResourceApiTask = createCheckResourceApiTask()
            val updateResourceApiTask = createUpdateResourceApiTask()

            extension.defaultPublishVariant { libraryVariant ->
                // Check and update resource api tasks rely compile to generate public.txt
                checkResourceApiTask.dependsOn(libraryVariant.javaCompileProvider)
                updateResourceApiTask.dependsOn(libraryVariant.javaCompileProvider)
            }
            tasks.withType(UpdateApiTask::class.java).configureEach { task ->
                task.dependsOn(updateResourceApiTask)
            }
            rootProject.tasks.named(AndroidXPlugin.BUILD_ON_SERVER_TASK).configure { task ->
                task.dependsOn(checkResourceApiTask)
            }
        }
    }
}

private fun Project.getGenerateResourceApiFile(): File {
    return File(buildDir, "intermediates/public_res/release" +
            "/packageReleaseResources/public.txt")
}

/**
 * Delays execution of the given [action] until the task graph is ready, and we know whether
 * we are running buildOnServer
 */
private fun Project.runIfPartOfBuildOnServer(action: () -> Unit) {
    buildOnServerDependentActions.add(action)
}

/**
 * A list of configuration actions that will only be applied if buildOnServer is part of
 * the task graph, essentially when we are running ./gradlew buildOnServer
 */
private val Project.buildOnServerDependentActions: MutableList<() -> Unit> get() {
    val extraProperties = rootProject.extensions.extraProperties
    if (!extraProperties.has(BUILD_ON_SERVER_DEPENDENT_ACTIONS)) {
        extraProperties.set(BUILD_ON_SERVER_DEPENDENT_ACTIONS, mutableListOf<() -> Unit>())
    }
    @Suppress("UNCHECKED_CAST")
    return extraProperties.get(BUILD_ON_SERVER_DEPENDENT_ACTIONS) as MutableList<() -> Unit>
}
