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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.Properties

private const val MAIN_DIR = "androidx/navigation/testapp"

private const val NEXT_DIRECTIONS = "$MAIN_DIR/NextFragmentDirections.java"
private const val NEXT_ARGUMENTS = "$MAIN_DIR/NextFragmentArgs.java"
private const val NEXT_ARGUMENTS_KT = "$MAIN_DIR/NextFragmentArgs.kt"
private const val MAIN_DIRECTIONS = "$MAIN_DIR/MainFragmentDirections.java"
private const val MAIN_DIRECTIONS_KT = "$MAIN_DIR/MainFragmentDirections.kt"
private const val MODIFIED_NEXT_DIRECTIONS = "$MAIN_DIR/ModifiedNextFragmentDirections.java"
private const val ADDITIONAL_DIRECTIONS = "$MAIN_DIR/AdditionalFragmentDirections.java"
private const val FOO_DIRECTIONS = "$MAIN_DIR/foo/FooFragmentDirections.java"
private const val FEATURE_DIRECTIONS = "$MAIN_DIR/FeatureFragmentDirections.java"
private const val LIBRARY_DIRECTIONS = "$MAIN_DIR/LibraryFragmentDirections.java"
private const val FOO_DYNAMIC_DIRECTIONS =
        "safe/gradle/test/app/safe/app/foo/DynFooFeatureFragmentDirections.java"
private const val NOTFOO_DYNAMIC_DIRECTIONS = "$MAIN_DIR/DynFeatureFragmentDirections.java"

private const val NAV_RESOURCES = "src/main/res/navigation"
private const val SEC = 1000L

// Does not work in the Android Studio
@RunWith(JUnit4::class)
class PluginTest {

    @Suppress("MemberVisibilityCanBePrivate")
    @get:Rule
    val testProjectDir = TemporaryFolder()

    private var buildFile: File = File("")
    private var prebuiltsRepo = ""
    private var compileSdkVersion = ""
    private var buildToolsVersion = ""
    private var minSdkVersion = ""
    private var debugKeystore = ""
    private var navigationCommon = ""
    private var kotlinStblib = ""

    private fun projectRoot(): File = testProjectDir.root

    private fun assertGenerated(name: String, prefix: String? = null): File {
        return prefix?.let { assertExists(name, true, it) } ?: assertExists(name, true)
    }

    private fun assertNotGenerated(name: String, prefix: String? = null): File {
        return prefix?.let { assertExists(name, false, it) } ?: assertExists(name, false)
    }

    private fun assertExists(name: String, ex: Boolean, prefix: String = ""): File {
        val generatedFile = File(projectRoot(), "${prefix}build/$GENERATED_PATH/$name")
        assertThat(generatedFile.exists(), `is`(ex))
        return generatedFile
    }

    private fun navResource(name: String) = File(projectRoot(), "$NAV_RESOURCES/$name")

    private fun gradleBuilder(vararg args: String) = GradleRunner.create()
            .withProjectDir(projectRoot()).withPluginClasspath().withArguments(*args)

    private fun runGradle(vararg args: String) = gradleBuilder(*args).build()
    private fun runAndFailGradle(vararg args: String) = gradleBuilder(*args).buildAndFail()

    @Before
    fun setup() {
        projectRoot().mkdirs()
        buildFile = File(projectRoot(), "build.gradle")
        buildFile.createNewFile()
        // copy local.properties
        val appToolkitProperties = File("../../local.properties")
        if (appToolkitProperties.exists()) {
            appToolkitProperties.copyTo(File(projectRoot(), "local.properties"), overwrite = true)
        } else {
            File("../../local.properties").copyTo(
                    File(projectRoot(), "local.properties"), overwrite = true)
        }
        val stream = PluginTest::class.java.classLoader.getResourceAsStream("sdk.prop")
        val properties = Properties()
        properties.load(stream)
        prebuiltsRepo = properties.getProperty("prebuiltsRepo")
        compileSdkVersion = properties.getProperty("compileSdkVersion")
        buildToolsVersion = properties.getProperty("buildToolsVersion")
        minSdkVersion = properties.getProperty("minSdkVersion")
        debugKeystore = properties.getProperty("debugKeystore")
        navigationCommon = properties.getProperty("navigationCommon")
        kotlinStblib = properties.getProperty("kotlinStdlib")
    }

    private fun setupSimpleBuildGradle() {
        testData("app-project").copyRecursively(projectRoot())
        buildFile.writeText("""
            plugins {
                id('com.android.application')
                id('androidx.navigation.safeargs')
            }

            repositories {
                maven { url "$prebuiltsRepo/androidx/external" }
                maven { url "$prebuiltsRepo/androidx/internal" }
            }

            android {
                compileSdkVersion $compileSdkVersion
                buildToolsVersion "$buildToolsVersion"

                defaultConfig {
                    minSdkVersion $minSdkVersion
                }

                signingConfigs {
                    debug {
                        storeFile file("$debugKeystore")
                    }
                }
            }

            dependencies {
                implementation "$navigationCommon"
            }
        """.trimIndent())
    }

    private fun setupMultiModuleBuildGradle() {
        testData("multimodule-project").copyRecursively(projectRoot())
        buildFile.writeText("""
            buildscript {
                ext.compileSdk = $compileSdkVersion
                ext.buildTools = "$buildToolsVersion"
                ext.minSdk = $minSdkVersion
                ext.debugKeystoreFile = "$debugKeystore"
                ext.navigationCommonDep = "$navigationCommon"
            }

            allprojects {
                repositories {
                    maven { url "$prebuiltsRepo/androidx/external" }
                    maven { url "$prebuiltsRepo/androidx/internal" }
                }
            }
        """.trimIndent())
    }

    @Test
    fun runGenerateTask() {
        testData("app-project").copyRecursively(projectRoot())
        buildFile.writeText("""
            plugins {
                id('com.android.application')
                id('androidx.navigation.safeargs')
            }

            repositories {
                maven { url "$prebuiltsRepo/androidx/external" }
                maven { url "$prebuiltsRepo/androidx/internal" }
            }

            android {
                compileSdkVersion $compileSdkVersion
                buildToolsVersion "$buildToolsVersion"
                flavorDimensions "mode"
                productFlavors {
                    foo {
                        dimension "mode"
                        applicationIdSuffix ".foo"
                    }
                    notfoo {
                        dimension "mode"
                    }

                }

                defaultConfig {
                    minSdkVersion $minSdkVersion
                }

                signingConfigs {
                    debug {
                        storeFile file("$debugKeystore")
                    }
                }
            }

            dependencies {
                implementation "$navigationCommon"
            }
        """.trimIndent())

        runGradle("assembleNotfooDebug", "assembleFooDebug")
                .assertSuccessfulTask("assembleNotfooDebug")
                .assertSuccessfulTask("assembleFooDebug")

        assertGenerated("notfoo/debug/$NEXT_DIRECTIONS")
        assertGenerated("notfoo/debug/$NEXT_ARGUMENTS")
        assertNotGenerated("foo/debug/$NEXT_DIRECTIONS")
        assertGenerated("foo/debug/$FOO_DIRECTIONS")
    }

    @Test
    fun runGenerateTaskForKotlin() {
        testData("app-project-kotlin").copyRecursively(projectRoot())
        buildFile.writeText("""
            plugins {
                id('com.android.application')
                id('kotlin-android')
                id('androidx.navigation.safeargs.kotlin')
            }

            repositories {
                maven { url "$prebuiltsRepo/androidx/external" }
                maven { url "$prebuiltsRepo/androidx/internal" }
            }

            android {
                compileSdkVersion $compileSdkVersion
                buildToolsVersion "$buildToolsVersion"

                defaultConfig {
                    minSdkVersion $minSdkVersion
                }

                signingConfigs {
                    debug {
                        storeFile file("$debugKeystore")
                    }
                }
            }

            dependencies {
                implementation "$kotlinStblib"
                implementation "$navigationCommon"
            }
        """.trimIndent())

        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")

        assertGenerated("debug/$NEXT_ARGUMENTS_KT")
        assertGenerated("debug/$NEXT_ARGUMENTS_KT")
        assertGenerated("debug/$MAIN_DIRECTIONS_KT")
    }

    @Test
    fun incrementalAdd() {
        setupSimpleBuildGradle()
        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        val nextLastMod = assertGenerated("debug/$NEXT_DIRECTIONS").lastModified()

        testData("incremental-test-data/add_nav.xml").copyTo(navResource("add_nav.xml"))

        // lastModified has one second precision on certain platforms and jdk versions
        // so sleep for a second
        Thread.sleep(SEC)
        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        assertGenerated("debug/$ADDITIONAL_DIRECTIONS")
        val newNextLastMod = assertGenerated("debug/$NEXT_DIRECTIONS").lastModified()
        assertThat(newNextLastMod, `is`(nextLastMod))
    }

    @Test
    fun incrementalModify() {
        setupSimpleBuildGradle()
        testData("incremental-test-data/add_nav.xml").copyTo(navResource("add_nav.xml"))

        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        val mainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        val additionalLastMod = assertGenerated("debug/$ADDITIONAL_DIRECTIONS").lastModified()
        assertGenerated("debug/$NEXT_DIRECTIONS")

        testData("incremental-test-data/modified_nav.xml").copyTo(navResource("nav_test.xml"), true)

        // lastModified has one second precision on certain platforms and jdk versions
        // so sleep for a second
        Thread.sleep(SEC)
        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        val newMainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        // main directions were regenerated
        assertThat(newMainLastMod, not(mainLastMod))

        // but additional directions weren't touched
        val newAdditionalLastMod = assertGenerated("debug/$ADDITIONAL_DIRECTIONS").lastModified()
        assertThat(newAdditionalLastMod, `is`(additionalLastMod))

        assertGenerated("debug/$MODIFIED_NEXT_DIRECTIONS")
        assertNotGenerated("debug/$NEXT_DIRECTIONS")
    }

    @Test
    fun incrementalRemove() {
        setupSimpleBuildGradle()
        testData("incremental-test-data/add_nav.xml").copyTo(navResource("add_nav.xml"))

        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        val mainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        assertGenerated("debug/$ADDITIONAL_DIRECTIONS")

        val wasRemoved = navResource("add_nav.xml").delete()
        assertThat(wasRemoved, `is`(true))

        // lastModified has one second precision on certain platforms and jdk versions
        // so sleep for a second
        Thread.sleep(SEC)
        runGradle("assembleDebug").assertSuccessfulTask("assembleDebug")
        val newMainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        // main directions weren't touched
        assertThat(newMainLastMod, `is`(mainLastMod))

        // but additional directions are removed
        assertNotGenerated("debug/$ADDITIONAL_DIRECTIONS")
    }

    @Test
    fun invalidModify() {
        setupSimpleBuildGradle()
        testData("incremental-test-data/add_nav.xml").copyTo(navResource("add_nav.xml"))
        runGradle("generateSafeArgsDebug").assertSuccessfulTask("generateSafeArgsDebug")
        val step1MainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        val step1AdditionalLastMod = assertGenerated("debug/$ADDITIONAL_DIRECTIONS").lastModified()
        assertGenerated("debug/$NEXT_DIRECTIONS")

        testData("invalid/failing_nav.xml").copyTo(navResource("nav_test.xml"), true)
        Thread.sleep(SEC)
        runAndFailGradle("generateSafeArgsDebug").assertFailingTask("generateSafeArgsDebug")
        val step2MainLastMod = assertGenerated("debug/$MAIN_DIRECTIONS").lastModified()
        // main directions were regenerated
        assertThat(step2MainLastMod, not(step1MainLastMod))

        // but additional directions weren't touched
        val step2AdditionalLastMod = assertGenerated("debug/$ADDITIONAL_DIRECTIONS").lastModified()
        assertThat(step2AdditionalLastMod, `is`(step1AdditionalLastMod))

        val step2ModifiedTime = assertGenerated("debug/$MODIFIED_NEXT_DIRECTIONS").lastModified()
        assertNotGenerated("debug/$NEXT_DIRECTIONS")

        testData("incremental-test-data/modified_nav.xml").copyTo(navResource("nav_test.xml"), true)
        Thread.sleep(SEC)
        runGradle("generateSafeArgsDebug").assertSuccessfulTask("generateSafeArgsDebug")

        // additional directions are touched because once task failed,
        // gradle next time makes full run
        val step3AdditionalLastMod = assertGenerated("debug/$ADDITIONAL_DIRECTIONS").lastModified()
        assertThat(step3AdditionalLastMod, not(step2AdditionalLastMod))

        val step3ModifiedTime = assertGenerated("debug/$MODIFIED_NEXT_DIRECTIONS").lastModified()
        assertThat(step2ModifiedTime, not(step3ModifiedTime))
    }

    @Test
    fun generateForFeature() {
        setupMultiModuleBuildGradle()
        runGradle(
                ":feature:assembleFooDebugFeature",
                ":feature:assembleNotfooDebugFeature"
        )
                .assertSuccessfulTask("feature:assembleNotfooDebugFeature")
                .assertSuccessfulTask("feature:assembleFooDebugFeature")

        assertGenerated("foo/debug/$FEATURE_DIRECTIONS", "feature/")
        assertGenerated("notfoo/debug/$FEATURE_DIRECTIONS", "feature/")
    }

    @Test
    fun generateForLibrary() {
        setupMultiModuleBuildGradle()
        runGradle(
                ":library:assembleFooDebug",
                ":library:assembleNotfooDebug"
        )
                .assertSuccessfulTask("library:assembleNotfooDebug")
                .assertSuccessfulTask("library:assembleFooDebug")

        assertGenerated("foo/debug/$LIBRARY_DIRECTIONS", "library/")
        assertGenerated("notfoo/debug/$LIBRARY_DIRECTIONS", "library/")
    }

    @Test
    fun generateForBaseFeature() {
        setupMultiModuleBuildGradle()
        runGradle(
                ":base:assembleFooDebugFeature",
                ":base:assembleNotfooDebugFeature"
        )
                .assertSuccessfulTask("base:assembleNotfooDebugFeature")
                .assertSuccessfulTask("base:assembleFooDebugFeature")

        assertGenerated("foo/debug/$MAIN_DIRECTIONS", "base/")
        assertGenerated("notfoo/debug/$MAIN_DIRECTIONS", "base/")
        assertGenerated("foo/debug/$NEXT_DIRECTIONS", "base/")
        assertGenerated("notfoo/debug/$NEXT_DIRECTIONS", "base/")
    }

    @Test
    fun generateForDynamicFeature() {
        setupMultiModuleBuildGradle()
        runGradle(
                ":dynamic_feature:assembleFooDebug",
                ":dynamic_feature:assembleNotfooDebug"
        )
                .assertSuccessfulTask("dynamic_feature:assembleNotfooDebug")
                .assertSuccessfulTask("dynamic_feature:assembleFooDebug")

        assertGenerated("notfoo/debug/$NOTFOO_DYNAMIC_DIRECTIONS", "dynamic_feature/")
        assertNotGenerated("foo/debug/$NOTFOO_DYNAMIC_DIRECTIONS", "dynamic_feature/")
        assertGenerated("foo/debug/$FOO_DYNAMIC_DIRECTIONS", "dynamic_feature/")
        assertNotGenerated("notfoo/debug/$FOO_DYNAMIC_DIRECTIONS", "dynamic_feature/")
    }
}

private fun testData(name: String) = File("src/test/test-data", name)

private fun BuildResult.assertSuccessfulTask(name: String): BuildResult {
    assertThat(task(":$name")!!.outcome, `is`(TaskOutcome.SUCCESS))
    return this
}

private fun BuildResult.assertFailingTask(name: String): BuildResult {
    assertThat(task(":$name")!!.outcome, `is`(TaskOutcome.FAILED))
    return this
}