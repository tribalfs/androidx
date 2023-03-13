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

package androidx.baselineprofile.gradle.utils

import androidx.testutils.gradle.ProjectSetupRule
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal const val ANDROID_APPLICATION_PLUGIN = "com.android.application"
internal const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
internal const val ANDROID_TEST_PLUGIN = "com.android.test"

class BaselineProfileProjectSetupRule : ExternalResource() {

    /**
     * Root folder for the project setup that contains 3 modules.
     */
    val rootFolder = TemporaryFolder().also { it.create() }

    /**
     * Represents a module with the app target plugin applied.
     */
    val appTarget by lazy {
        AppTargetModule(
            rule = appTargetSetupRule,
            name = appTargetName,
        )
    }

    /**
     * Represents a module with the consumer plugin applied.
     */
    val consumer by lazy {
        ConsumerModule(
            rule = consumerSetupRule,
            name = consumerName,
            producerName = producerName
        )
    }

    /**
     * Represents a module with the producer plugin applied.
     */
    val producer by lazy {
        ProducerModule(
            rule = producerSetupRule,
            name = producerName,
            tempFolder = tempFolder,
            consumerName = consumerName
        )
    }

    // Temp folder for temp generated files that need to be referenced by a module.
    private val tempFolder by lazy { File(rootFolder.root, "temp").apply { mkdirs() } }

    // Project setup rules
    private val appTargetSetupRule by lazy { ProjectSetupRule(rootFolder.root) }
    private val consumerSetupRule by lazy { ProjectSetupRule(rootFolder.root) }
    private val producerSetupRule by lazy { ProjectSetupRule(rootFolder.root) }

    // Module names (generated automatically)
    private val appTargetName: String by lazy {
        appTargetSetupRule.rootDir.relativeTo(rootFolder.root).name
    }
    private val consumerName: String by lazy {
        consumerSetupRule.rootDir.relativeTo(rootFolder.root).name
    }
    private val producerName: String by lazy {
        producerSetupRule.rootDir.relativeTo(rootFolder.root).name
    }

    override fun apply(base: Statement, description: Description): Statement {
        return RuleChain
            .outerRule(appTargetSetupRule)
            .around(producerSetupRule)
            .around(consumerSetupRule)
            .around { b, _ -> applyInternal(b) }
            .apply(base, description)
    }

    private fun applyInternal(base: Statement) = object : Statement() {
        override fun evaluate() {

            // Creates the main settings.gradle
            rootFolder.newFile("settings.gradle").writeText(
                """
                include '$appTargetName'
                include '$producerName'
                include '$consumerName'
            """.trimIndent()
            )

            // Copies test project data
            mapOf(
                "app-target" to appTargetSetupRule,
                "consumer" to consumerSetupRule,
                "producer" to producerSetupRule
            ).forEach { (folder, project) ->
                File("src/test/test-data", folder)
                    .apply { deleteOnExit() }
                    .copyRecursively(project.rootDir)
            }

            base.evaluate()
        }
    }
}

data class VariantProfile(
    val flavor: String?,
    val buildType: String = "release",
    val profileLines: List<String> = listOf()
) {
    val nonMinifiedVariant = "${flavor ?: ""}NonMinified${buildType.capitalized()}"
}

interface Module {

    val name: String
    val rule: ProjectSetupRule
    val rootDir: File
        get() = rule.rootDir
    val gradleRunner: GradleRunner
        get() = GradleRunner.create().withProjectDir(rule.rootDir).withPluginClasspath()

    fun setBuildGradle(buildGradleContent: String) =
        rule.writeDefaultBuildGradle(
            prefix = buildGradleContent,
            suffix = """
                $GRADLE_CODE_PRINT_TASK
            """.trimIndent()
        )
}

class AppTargetModule(
    override val rule: ProjectSetupRule,
    override val name: String,
) : Module

class ProducerModule(
    override val rule: ProjectSetupRule,
    override val name: String,
    private val tempFolder: File,
    private val consumerName: String
) : Module {

    fun setupWithFreeAndPaidFlavors(
        freeReleaseProfileLines: List<String>,
        paidReleaseProfileLines: List<String>,
    ) {
        setup(
            variantProfiles = listOf(
                VariantProfile(
                    flavor = "free",
                    buildType = "release",
                    profileLines = freeReleaseProfileLines
                ),
                VariantProfile(
                    flavor = "paid",
                    buildType = "release",
                    profileLines = paidReleaseProfileLines
                ),
            )
        )
    }

    fun setup(
        variantProfiles: List<VariantProfile> = listOf(
            VariantProfile(
                flavor = null,
                buildType = "release",
                profileLines = listOf(
                    Fixtures.CLASS_1_METHOD_1,
                    Fixtures.CLASS_2_METHOD_2,
                    Fixtures.CLASS_2,
                    Fixtures.CLASS_1
                )
            )
        ),
        baselineProfileBlock: String = "",
        additionalGradleCodeBlock: String = "",
    ) {
        val flavorsBlock = """
            productFlavors {
                flavorDimensions = ["version"]
                ${
            variantProfiles
                .filter { !it.flavor.isNullOrBlank() }
                .joinToString("\n") { " ${it.flavor} { dimension \"version\" } " }
        }
            }
        """.trimIndent()

        val buildTypesBlock = """
            buildTypes {
                ${
            variantProfiles
                .filter { it.buildType.isNotBlank() && it.buildType != "release" }
                .joinToString("\n") { " ${it.buildType} { initWith(debug) } " }
        }
            }
        """.trimIndent()

        val disableConnectedAndroidTestsBlock = variantProfiles.joinToString("\n") {

            // Creates a folder to use as results dir
            val outputDir = File(tempFolder, it.nonMinifiedVariant).apply { mkdirs() }

            // Writes the fake test result proto in it, with the given lines
            writeFakeTestResultsProto(
                outputDir = outputDir,
                profileLines = it.profileLines
            )

            // Gradle script to injects a fake and disable the actual task execution for
            // android test
            """
            afterEvaluate {
                project.tasks.named("connected${it.nonMinifiedVariant.capitalized()}AndroidTest") {
                    it.resultsDir.set(new File("${outputDir.absolutePath}"))
                    onlyIf { false }
                }
            }

                """.trimIndent()
        }

        setBuildGradle(
            """
                plugins {
                    id("com.android.test")
                    id("androidx.baselineprofile.producer")
                }

                android {
                    $flavorsBlock

                    $buildTypesBlock

                    namespace 'com.example.namespace.test'
                    targetProjectPath = ":$consumerName"
                }

                dependencies {
                }

                baselineProfile {
                    $baselineProfileBlock
                }

                $disableConnectedAndroidTestsBlock

                $additionalGradleCodeBlock

            """.trimIndent()
        )
    }

    private fun writeFakeTestResultsProto(
        outputDir: File,
        profileLines: List<String>
    ) {

        val generatedProfileFile = File
            .createTempFile("fake-baseline-prof-", ".txt")
            .apply { writeText(profileLines.joinToString(System.lineSeparator())) }

        val testResultProto = TestResultProto.TestResult.newBuilder()
            .addOutputArtifact(
                TestArtifactProto.Artifact.newBuilder()
                    .setLabel(
                        LabelProto.Label.newBuilder()
                            .setLabel("additionaltestoutput.benchmark.trace")
                            .build()
                    )
                    .setSourcePath(
                        PathProto.Path.newBuilder()
                            .setPath(generatedProfileFile.absolutePath)
                            .build()
                    )
                    .build()
            )
            .build()

        val testSuiteResultProto = TestSuiteResultProto.TestSuiteResult.newBuilder()
            .setTestStatus(TestStatusProto.TestStatus.PASSED)
            .addTestResult(testResultProto)
            .build()

        File(outputDir, "test-result.pb")
            .apply { outputStream().use { testSuiteResultProto.writeTo(it) } }
    }
}

class ConsumerModule(
    override val rule: ProjectSetupRule,
    override val name: String,
    private val producerName: String
) : Module {

    fun setup(
        androidPlugin: String,
        flavors: Boolean = false,
        dependencyOnProducerProject: Boolean = true,
        buildTypeAnotherRelease: Boolean = false,
        addAppTargetPlugin: Boolean = androidPlugin == ANDROID_APPLICATION_PLUGIN,
        baselineProfileBlock: String = "",
        additionalGradleCodeBlock: String = "",
    ) {
        val flavorsBlock = """
            productFlavors {
                flavorDimensions = ["version"]
                free { dimension "version" }
                paid { dimension "version" }
            }

        """.trimIndent()

        val buildTypeAnotherReleaseBlock = """
            buildTypes {
                anotherRelease { initWith(release) }
            }

        """.trimIndent()

        val dependencyOnProducerProjectBlock = """
            dependencies {
                baselineProfile(project(":$producerName"))
            }

        """.trimIndent()

        setBuildGradle(
            """
                plugins {
                    id("$androidPlugin")
                    id("androidx.baselineprofile.consumer")
                    ${if (addAppTargetPlugin) "id(\"androidx.baselineprofile.apptarget\")" else ""}
                }
                android {
                    namespace 'com.example.namespace'
                    ${if (flavors) flavorsBlock else ""}
                    ${if (buildTypeAnotherRelease) buildTypeAnotherReleaseBlock else ""}
                }

               ${if (dependencyOnProducerProject) dependencyOnProducerProjectBlock else ""}

                baselineProfile {
                    $baselineProfileBlock
                }

                $additionalGradleCodeBlock

            """.trimIndent()
        )
    }
}
