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

import androidx.baselineprofile.gradle.utils.ANDROID_APPLICATION_PLUGIN
import androidx.baselineprofile.gradle.utils.ANDROID_LIBRARY_PLUGIN
import androidx.baselineprofile.gradle.utils.ANDROID_TEST_PLUGIN
import androidx.baselineprofile.gradle.utils.BaselineProfileProjectSetupRule
import androidx.baselineprofile.gradle.utils.Fixtures
import androidx.baselineprofile.gradle.utils.VariantProfile
import androidx.baselineprofile.gradle.utils.build
import androidx.baselineprofile.gradle.utils.buildAndAssertThatOutput
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BaselineProfileConsumerPluginTest {

    // To test the consumer plugin we need a module that exposes a baselineprofile configuration
    // to be consumed. This is why we'll be using 2 projects. The producer project build gradle
    // is generated ad hoc in the tests that require it in order to supply mock profiles.

    companion object {
        private const val EXPECTED_PROFILE_FOLDER = "generated/baselineProfiles"
    }

    @get:Rule
    val projectSetup = BaselineProfileProjectSetupRule()

    private val gradleRunner by lazy { projectSetup.consumer.gradleRunner }

    private fun baselineProfileFile(variantName: String) = File(
        projectSetup.consumer.rootDir,
        "src/$variantName/$EXPECTED_PROFILE_FOLDER/baseline-prof.txt"
    )

    private fun readBaselineProfileFileContent(variantName: String): List<String> =
        baselineProfileFile(variantName).readLines()

    @Test
    fun testGenerateTaskWithNoFlavors() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            dependencyOnProducerProject = true,
            flavors = false
        )
        projectSetup.producer.setupWithoutFlavors(
            releaseProfileLines = listOf(
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_1,
                Fixtures.CLASS_2_METHOD_1,
                Fixtures.CLASS_2
            )
        )

        gradleRunner
            .withArguments("generateBaselineProfile", "--stacktrace")
            .build()

        assertThat(readBaselineProfileFileContent("main"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1,
            )
    }

    @Test
    fun testGenerateTaskWithFlavorsAndDefaultMerge() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            dependencyOnProducerProject = true
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // Asserts that all per-variant, per-flavor and per-build type tasks are being generated.
        gradleRunner.buildAndAssertThatOutput("tasks") {
            contains("generateReleaseBaselineProfile - ")
            contains("generateFreeReleaseBaselineProfile - ")
            contains("generatePaidReleaseBaselineProfile - ")
        }

        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .build()

        assertThat(readBaselineProfileFileContent("freeRelease"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
            )

        assertThat(readBaselineProfileFileContent("paidRelease"))
            .containsExactly(
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1,
            )
    }

    @Test
    fun testGenerateTaskWithFlavorsAndMergeAll() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            dependencyOnProducerProject = true,
            baselineProfileBlock = """
                mergeIntoMain = true
            """.trimIndent()
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2)
        )

        // Asserts that all per-variant, per-flavor and per-build type tasks are being generated.
        gradleRunner.buildAndAssertThatOutput("tasks") {
            contains("generateBaselineProfile - ")
            contains("generateReleaseBaselineProfile - ")
            doesNotContain("generateFreeReleaseBaselineProfile - ")
            doesNotContain("generatePaidReleaseBaselineProfile - ")
        }

        gradleRunner
            .withArguments("generateBaselineProfile", "--stacktrace")
            .build()

        assertThat(readBaselineProfileFileContent("main"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1,
            )
    }

    @Test
    fun testPluginAppliedToLibraryModule() {
        projectSetup.producer.setup()
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            addAppTargetPlugin = false,
            dependencyOnProducerProject = true
        )
        gradleRunner
            .withArguments("generateBaselineProfile", "--stacktrace")
            .build()
        // This should not fail.
    }

    @Test
    fun testPluginAppliedToNonApplicationAndNonLibraryModule() {
        projectSetup.producer.setup()
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_TEST_PLUGIN,
            addAppTargetPlugin = false,
            dependencyOnProducerProject = true
        )

        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .buildAndFail()
    }

    @Test
    fun testSrcSetAreAddedToVariants() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2)
        )
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            dependencyOnProducerProject = true,
            baselineProfileBlock = """
                enableR8BaselineProfileRewrite = false
            """.trimIndent(),
            additionalGradleCodeBlock = """
                androidComponents {
                    onVariants(selector()) { variant ->
                        tasks.register(variant.name + "Print", PrintTask) { t ->
                            t.text.set(variant.sources.baselineProfiles?.all?.get().toString())
                        }
                    }
                }
            """.trimIndent()
        )

        arrayOf("freeRelease", "paidRelease")
            .forEach {

                // Expected src set location. Note that src sets are not added if the folder does
                // not exist so we need to create it.
                val expected =
                    File(
                        projectSetup.consumer.rootDir,
                        "src/$it/$EXPECTED_PROFILE_FOLDER"
                    )
                        .apply {
                            mkdirs()
                            deleteOnExit()
                        }

                gradleRunner.buildAndAssertThatOutput("${it}Print") {
                    contains(expected.absolutePath)
                }
            }
    }

    @Test
    fun testWhenPluginIsAppliedAndNoDependencyIsSetShouldFailWithErrorMsg() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = false,
            dependencyOnProducerProject = false
        )

        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .buildAndFail()
            .output
            .replace("\n", " ")
            .also {
                assertThat(it).contains(
                    "The baseline profile consumer plugin is applied to " +
                        "this module but no dependency has been set"
                )
            }
    }

    @Test
    fun testR8RewriteBaselineProfilePropertySet() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2)
        )
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            dependencyOnProducerProject = true,
            flavors = true,
            buildTypeAnotherRelease = true,
            additionalGradleCodeBlock = """
                androidComponents {
                    onVariants(selector()) { variant ->
                        println(variant.name)
                        tasks.register("print" + variant.name, PrintTask) { t ->
                            def prop = "android.experimental.art-profile-r8-rewriting"
                            if (prop in variant.experimentalProperties) {
                                def value = variant.experimentalProperties[prop].get().toString()
                                t.text.set( "r8-rw=" + value)
                            } else {
                                t.text.set( "r8-rw=false")
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        arrayOf(
            "printFreeRelease",
            "printPaidRelease",
            "printFreeAnotherRelease",
            "printPaidAnotherRelease",
        ).forEach { gradleRunner.buildAndAssertThatOutput(it) { contains("r8-rw=false") } }
    }

    @Test
    fun testFilterAndSortAndMerge() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            flavors = true,
            baselineProfileBlock = """
                filter {
                    include("com.sample.Utils")
                }
            """.trimIndent()
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_1_METHOD_2,
                Fixtures.CLASS_1,
            ),
            paidReleaseProfileLines = listOf(
                Fixtures.CLASS_2_METHOD_1,
                Fixtures.CLASS_2_METHOD_2,
                Fixtures.CLASS_2_METHOD_3,
                Fixtures.CLASS_2_METHOD_4,
                Fixtures.CLASS_2_METHOD_5,
                Fixtures.CLASS_2,
            )
        )

        gradleRunner
            .withArguments("generateBaselineProfile", "--stacktrace")
            .build()

        // In the final output there should be :
        //  - one single file in src/main/generatedBaselineProfiles because merge = `all`.
        //  - There should be only the Utils class [CLASS_2] because of the include filter.
        //  - The method `someOtherMethod` [CLASS_2_METHOD_3] should be included only once.
        assertThat(readBaselineProfileFileContent("main"))
            .containsExactly(
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1,
                Fixtures.CLASS_2_METHOD_2,
                Fixtures.CLASS_2_METHOD_3,
            )
    }

    @Test
    fun testSaveInSrcTrueAndAutomaticGenerationDuringBuildTrue() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            baselineProfileBlock = """
                saveInSrc = true
                automaticGenerationDuringBuild = true
            """.trimIndent()
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // Asserts that assembling release triggers generation of profile
        gradleRunner.buildAndAssertThatOutput("assembleFreeRelease", "--dry-run") {
            arrayOf(
                "mergeFreeReleaseBaselineProfile",
                "copyFreeReleaseBaselineProfileIntoSrc",
                "mergeFreeReleaseArtProfile",
                "compileFreeReleaseArtProfile",
                "assembleFreeRelease"
            ).forEach { contains(":${projectSetup.consumer.name}:$it") }
        }

        // Asserts that the profile is generated in the src folder
        gradleRunner.build("generateFreeReleaseBaselineProfile") {
            assertThat(readBaselineProfileFileContent("freeRelease"))
                .containsExactly(
                    Fixtures.CLASS_1,
                    Fixtures.CLASS_1_METHOD_1,
                )
        }
    }

    @Test
    fun testSaveInSrcTrueAndAutomaticGenerationDuringBuildFalse() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            baselineProfileBlock = """
                saveInSrc = true
                automaticGenerationDuringBuild = false
            """.trimIndent()
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // Asserts that assembling release does not trigger generation of profile
        gradleRunner.buildAndAssertThatOutput("assembleFreeRelease", "--dry-run") {
            arrayOf(
                "mergeFreeReleaseBaselineProfile",
                "copyFreeReleaseBaselineProfileIntoSrc"
            ).forEach { doesNotContain(":${projectSetup.consumer.name}:$it") }
            arrayOf(
                "mergeFreeReleaseArtProfile",
                "compileFreeReleaseArtProfile",
                "assembleFreeRelease"
            ).forEach { contains(":${projectSetup.consumer.name}:$it") }
        }

        // Asserts that the profile is generated in the src folder
        gradleRunner.build("generateFreeReleaseBaselineProfile") {
            assertThat(readBaselineProfileFileContent("freeRelease"))
                .containsExactly(
                    Fixtures.CLASS_1,
                    Fixtures.CLASS_1_METHOD_1,
                )
        }
    }

    @Test
    fun testSaveInSrcFalseAndAutomaticGenerationDuringBuildTrue() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            baselineProfileBlock = """
                saveInSrc = false
                automaticGenerationDuringBuild = true
            """.trimIndent()
        )
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // Asserts that assembling release triggers generation of profile
        gradleRunner.buildAndAssertThatOutput("assembleFreeRelease", "--dry-run") {
            arrayOf(
                "mergeFreeReleaseBaselineProfile",
                "mergeFreeReleaseArtProfile",
                "compileFreeReleaseArtProfile",
                "assembleFreeRelease"
            ).forEach { contains(":${projectSetup.consumer.name}:$it") }
            doesNotContain(
                ":${projectSetup.consumer.name}:copyFreeReleaseBaselineProfileIntoSrc"
            )
        }

        // Asserts that the profile is not generated in the src folder
        gradleRunner.build("generateFreeReleaseBaselineProfile") {}

        val profileFile = baselineProfileFile("freeRelease")
        assertThat(profileFile.exists()).isFalse()
    }

    @Test
    fun testSaveInSrcFalseAndAutomaticGenerationDuringBuildFalse() {
        projectSetup.producer.setup()
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            baselineProfileBlock = """
                saveInSrc = false
                automaticGenerationDuringBuild = false
            """.trimIndent()
        )
        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .buildAndFail()
            .output
            .replace(System.lineSeparator(), " ")
            .also {
                assertThat(it)
                    .contains(
                        "The current configuration of flags `saveInSrc` and " +
                            "`automaticGenerationDuringBuild` is not supported"
                    )
            }
    }

    @Test
    fun testWhenFiltersFilterOutAllTheProfileRules() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            baselineProfileBlock = """
                filter { include("nothing.**") }
            """.trimIndent()
        )
        projectSetup.producer.setupWithoutFlavors(
            releaseProfileLines = listOf(
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_1
            )
        )

        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .buildAndFail()
            .output
            .replace(System.lineSeparator(), " ")
            .also {
                assertThat(it)
                    .contains(
                        "The baseline profile consumer plugin is configured with filters that " +
                            "exclude all the profile rules"
                    )
            }
    }

    @Test
    fun testWhenProfileProducerProducesEmptyProfile() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN
        )
        projectSetup.producer.setupWithoutFlavors(
            releaseProfileLines = listOf()
        )
        gradleRunner.buildAndAssertThatOutput("generateReleaseBaselineProfile") {
            contains("No baseline profile rules were generated")
        }
    }

    @Test
    fun testVariantConfigurationOverrideForFlavors() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            flavors = true,
            baselineProfileBlock = """

                // Global configuration
                enableR8BaselineProfileRewrite = false
                saveInSrc = true
                automaticGenerationDuringBuild = false
                baselineProfileOutputDir = "generated/baselineProfiles"
                mergeIntoMain = true

                // Per variant configuration overrides global configuration.
                variants {
                    free {
                        enableR8BaselineProfileRewrite = true
                        saveInSrc = false
                        automaticGenerationDuringBuild = true
                        baselineProfileOutputDir = "somefolder"
                        mergeIntoMain = false
                    }
                    paidRelease {
                        enableR8BaselineProfileRewrite = true
                        saveInSrc = false
                        automaticGenerationDuringBuild = true
                        baselineProfileOutputDir = "someOtherfolder"
                        mergeIntoMain = false
                    }
                }

            """.trimIndent()
        )

        gradleRunner.buildAndAssertThatOutput(
            "printBaselineProfileExtensionForVariantFreeRelease"
        ) {
            contains("enableR8BaselineProfileRewrite=`true`")
            contains("saveInSrc=`false`")
            contains("automaticGenerationDuringBuild=`true`")
            contains("baselineProfileOutputDir=`somefolder`")
            contains("mergeIntoMain=`false`")
        }

        gradleRunner.buildAndAssertThatOutput(
            "printBaselineProfileExtensionForVariantPaidRelease"
        ) {
            contains("enableR8BaselineProfileRewrite=`true`")
            contains("saveInSrc=`false`")
            contains("automaticGenerationDuringBuild=`true`")
            contains("baselineProfileOutputDir=`someOtherfolder`")
            contains("mergeIntoMain=`false`")
        }
    }

    @Test
    fun testVariantConfigurationOverrideForBuildTypes() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            flavors = true,
            baselineProfileBlock = """

                // Global configuration
                enableR8BaselineProfileRewrite = false
                saveInSrc = true
                automaticGenerationDuringBuild = false
                baselineProfileOutputDir = "generated/baselineProfiles"
                mergeIntoMain = true

                // Per variant configuration overrides global configuration.
                variants {
                    release {
                        enableR8BaselineProfileRewrite = true
                        saveInSrc = false
                        automaticGenerationDuringBuild = true
                        baselineProfileOutputDir = "myReleaseFolder"
                        mergeIntoMain = false
                    }
                    paidRelease {
                        enableR8BaselineProfileRewrite = true
                        saveInSrc = false
                        automaticGenerationDuringBuild = true
                        baselineProfileOutputDir = "someOtherfolder"
                        mergeIntoMain = false
                    }
                }

            """.trimIndent()
        )

        gradleRunner.buildAndAssertThatOutput(
            "printBaselineProfileExtensionForVariantFreeRelease"
        ) {
            contains("enableR8BaselineProfileRewrite=`true`")
            contains("saveInSrc=`false`")
            contains("automaticGenerationDuringBuild=`true`")
            contains("baselineProfileOutputDir=`myReleaseFolder`")
            contains("mergeIntoMain=`false`")
        }

        gradleRunner.buildAndAssertThatOutput(
            "printBaselineProfileExtensionForVariantPaidRelease"
        ) {
            contains("enableR8BaselineProfileRewrite=`true`")
            contains("saveInSrc=`false`")
            contains("automaticGenerationDuringBuild=`true`")
            contains("baselineProfileOutputDir=`someOtherfolder`")
            contains("mergeIntoMain=`false`")
        }
    }

    @Test
    fun testVariantConfigurationOverrideForFlavorsAndBuildType() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_LIBRARY_PLUGIN,
            flavors = true,
            baselineProfileBlock = """
                variants {
                    free {
                        saveInSrc = true
                    }
                    release {
                        saveInSrc = false
                    }
                }

            """.trimIndent()
        )
        gradleRunner
            .withArguments("printBaselineProfileExtensionForVariantFreeRelease", "--stacktrace")
            .buildAndFail()
            .output
            .let {
                assertThat(it)
                    .contains("The per-variant configuration for baseline profiles is ambiguous")
            }
    }

    @Test
    fun testVariantDependenciesWithFlavors() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // In this setup no dependency is being added through the dependency block.
        // Instead dependencies are being added through per-variant configuration block.
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            dependencyOnProducerProject = false,
            baselineProfileBlock = """
                variants {
                    free {
                        from(project(":${projectSetup.producer.name}"))
                    }
                    paid {
                        from(project(":${projectSetup.producer.name}"))
                    }
                }

            """.trimIndent()
        )
        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .build()

        assertThat(readBaselineProfileFileContent("freeRelease"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
            )
        assertThat(readBaselineProfileFileContent("paidRelease"))
            .containsExactly(
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1,
            )
    }

    @Test
    fun testVariantDependenciesWithVariantsAndDirectConfiguration() {
        projectSetup.producer.setupWithFreeAndPaidFlavors(
            freeReleaseProfileLines = listOf(Fixtures.CLASS_1_METHOD_1, Fixtures.CLASS_1),
            paidReleaseProfileLines = listOf(Fixtures.CLASS_2_METHOD_1, Fixtures.CLASS_2),
        )

        // In this setup no dependency is being added through the dependency block.
        // Instead dependencies are being added through per-variant configuration block.
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN,
            flavors = true,
            dependencyOnProducerProject = false,
            baselineProfileBlock = """
                variants {
                    freeRelease {
                        from(project(":${projectSetup.producer.name}"))
                    }
                    paidRelease {
                        from(project(":${projectSetup.producer.name}"), "freeRelease")
                    }
                }

            """.trimIndent()
        )
        gradleRunner
            .withArguments("generateReleaseBaselineProfile", "--stacktrace")
            .build()

        assertThat(readBaselineProfileFileContent("freeRelease"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
            )

        // This output should be the same of free release
        assertThat(readBaselineProfileFileContent("paidRelease"))
            .containsExactly(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
            )
    }

    @Test
    fun testPartialResults() {
        projectSetup.consumer.setup(
            androidPlugin = ANDROID_APPLICATION_PLUGIN
        )

        // Function to setup the producer, run the generate profile command and assert output
        val setupProducerGenerateAndAssert: (
            Boolean,
            Map<String, List<String>>,
            List<String>
        ) -> (Unit) = { partial, mapFileToProfile, finalProfileAssertList ->

            projectSetup.producer.setup(
                variantProfiles = listOf(
                    VariantProfile(
                        flavor = null,
                        buildType = "release",
                        profileFileLines = mapFileToProfile
                    )
                )
            )

            val args = mutableListOf("generateBaselineProfile")
            if (partial) args.add("-Pandroid.testInstrumentationRunnerArguments.class=someClass")
            projectSetup.consumer.gradleRunner.build(*args.toTypedArray()) { }

            assertThat(readBaselineProfileFileContent("release"))
                .containsExactly(*finalProfileAssertList.toTypedArray())
        }

        // Full generation, 2 new tests.
        setupProducerGenerateAndAssert(
            false,
            mapOf(
                "myTest1" to listOf(Fixtures.CLASS_1, Fixtures.CLASS_1_METHOD_1),
                "myTest2" to listOf(Fixtures.CLASS_2, Fixtures.CLASS_2_METHOD_1)
            ),
            listOf(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1
            )
        )

        // Partial generation, modify 1 test.
        setupProducerGenerateAndAssert(
            true,
            mapOf(
                "myTest1" to listOf(Fixtures.CLASS_3, Fixtures.CLASS_3_METHOD_1)
            ),
            listOf(
                Fixtures.CLASS_3,
                Fixtures.CLASS_3_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1
            )
        )

        // Partial generation, add 1 test.
        setupProducerGenerateAndAssert(
            true,
            mapOf(
                "myTest3" to listOf(Fixtures.CLASS_4, Fixtures.CLASS_4_METHOD_1)
            ),
            listOf(
                Fixtures.CLASS_3,
                Fixtures.CLASS_3_METHOD_1,
                Fixtures.CLASS_4,
                Fixtures.CLASS_4_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1
            )
        )

        // Full generation, 2 new tests.
        setupProducerGenerateAndAssert(
            false,
            mapOf(
                "myTest1-new" to listOf(Fixtures.CLASS_1, Fixtures.CLASS_1_METHOD_1),
                "myTest2-new" to listOf(Fixtures.CLASS_2, Fixtures.CLASS_2_METHOD_1)
            ),
            listOf(
                Fixtures.CLASS_1,
                Fixtures.CLASS_1_METHOD_1,
                Fixtures.CLASS_2,
                Fixtures.CLASS_2_METHOD_1
            )
        )
    }
}
