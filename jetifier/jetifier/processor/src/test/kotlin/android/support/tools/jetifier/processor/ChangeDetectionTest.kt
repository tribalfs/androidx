/*
 * Copyright 2017 The Android Open Source Project
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

package android.support.tools.jetifier.processor

import android.support.tools.jetifier.core.PackageMap
import android.support.tools.jetifier.core.RewriteRule
import android.support.tools.jetifier.processor.archive.Archive
import android.support.tools.jetifier.processor.archive.ArchiveFile
import android.support.tools.jetifier.core.config.Config
import android.support.tools.jetifier.core.type.TypesMap
import android.support.tools.jetifier.core.type.JavaType
import android.support.tools.jetifier.core.pom.PomDependency
import android.support.tools.jetifier.core.pom.PomRewriteRule
import android.support.tools.jetifier.core.proguard.ProGuardTypesMap
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Tests that transformed artifacts are properly marked as changed / unchanged base on whether there
 * was something to rewrite or not.
 */
class ChangeDetectionTest {
    private val emptyConfig = Config(
        restrictToPackagePrefixes = emptyList(),
        rewriteRules = emptyList(),
        slRules = emptyList(),
        pomRewriteRules = emptySet(),
        typesMap = TypesMap.EMPTY,
        proGuardMap = ProGuardTypesMap.EMPTY,
        packageMap = PackageMap.EMPTY
    )

    private val prefRewriteConfig = Config(
        restrictToPackagePrefixes = listOf("android/support/v7/preference"),
        rewriteRules =
        listOf(
            RewriteRule(from = "android/support/v7/preference/Preference(.+)", to = "ignore"),
            RewriteRule(from = "(.*)/R(.*)", to = "ignore")
        ),
        slRules = emptyList(),
        pomRewriteRules = setOf(
            PomRewriteRule(
                PomDependency(
                    groupId = "supportGroup", artifactId = "supportArtifact", version = "4.0"),
                setOf(
                    PomDependency(
                        groupId = "testGroup", artifactId = "testArtifact", version = "1.0")
                )
        )),
        typesMap = TypesMap(mapOf(
            JavaType("android/support/v7/preference/Preference")
                to JavaType("android/test/pref/Preference")
        )),
        proGuardMap = ProGuardTypesMap.EMPTY,
        packageMap = PackageMap.EMPTY
    )

    @Test
    fun xmlRewrite_archiveChanged() {
        testChange(
            config = prefRewriteConfig,
            fileContent =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<android.support.v7.preference.Preference/>",
            fileName = "test.xml",
            areChangesExpected = true
        )
    }

    @Test
    fun xmlRewrite_archiveNotChanged() {
        testChange(
            config = emptyConfig,
            fileContent =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<android.support.v7.preference.Preference/>",
            fileName = "test.xml",
            areChangesExpected = false
        )
    }

    @Test
    fun proGuard_archiveChanged() {
        testChange(
            config = prefRewriteConfig,
            fileContent =
                "-keep public class * extends android.support.v7.preference.Preference { \n" +
                "  <fields>; \n" +
                "}",
            fileName = "proguard.txt",
            areChangesExpected = true
        )
    }

    @Test
    fun proGuard_archiveNotChanged() {
        testChange(
            config = emptyConfig,
            fileContent =
                "-keep public class * extends android.support.v7.preference.Preference { \n" +
                "  <fields>; \n" +
                "}",
            fileName = "test.xml",
            areChangesExpected = false
        )
    }

    @Test
    fun pom_archiveChanged() {
        testChange(
            config = prefRewriteConfig,
            fileContent =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
                "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0" +
                "  http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>supportGroup</groupId>\n" +
                "      <artifactId>supportArtifact</artifactId>\n" +
                "      <version>4.0</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>" +
                "</project>\n",
            fileName = "pom.xml",
            areChangesExpected = true
        )
    }

    @Test
    fun pom_archiveNotChanged() {
        testChange(
            config = emptyConfig,
            fileContent =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
                "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0" +
                "  http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>supportGroup</groupId>\n" +
                "      <artifactId>supportArtifact</artifactId>\n" +
                "      <version>4.0</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>" +
                "</project>\n",
            fileName = "pom.xml",
            areChangesExpected = true
        )
    }

    @Test
    fun javaClass_archiveChanged() {
        val inputClassPath = "/changeDetectionTest/testPreference.class"
        val inputFile = File(javaClass.getResource(inputClassPath).file)

        testChange(
            config = prefRewriteConfig,
            file = ArchiveFile(Paths.get("/", "preference.class"), inputFile.readBytes()),
            areChangesExpected = true
        )
    }

    @Test
    fun javaClass_archiveNotChanged() {
        val inputClassPath = "/changeDetectionTest/testPreference.class"
        val inputFile = File(javaClass.getResource(inputClassPath).file)

        testChange(
            config = emptyConfig,
            file = ArchiveFile(Paths.get("/", "preference.class"), inputFile.readBytes()),
            areChangesExpected = false
        )
    }

    private fun testChange(
        config: Config,
        fileContent: String,
        fileName: String,
        areChangesExpected: Boolean
    ) {
        testChange(
            config = config,
            file = ArchiveFile(Paths.get("/", fileName), fileContent.toByteArray()),
            areChangesExpected = areChangesExpected)
    }

    /**
     * Runs the whole transformation process over the given file and verifies if the parent
     * artifacts was properly marked as changed / unchanged base on [areChangesExpected] param.
     */
    private fun testChange(
        config: Config,
        file: ArchiveFile,
        areChangesExpected: Boolean
    ) {
        val archive = Archive(Paths.get("some/path"), listOf(file))
        val sourceArchive = archive.writeSelfToFile(Files.createTempFile("test", ".zip"))

        val expectedFileIfRefactored = Files.createTempFile("testRefactored", ".zip")
        val processor = Processor.createProcessor(config)
        val resultFiles = processor.transform(
            setOf(FileMapping(sourceArchive, expectedFileIfRefactored.toFile())),
            copyUnmodifiedLibsAlso = false)

        if (areChangesExpected) {
            Truth.assertThat(resultFiles).containsExactly(expectedFileIfRefactored.toFile())
        } else {
            Truth.assertThat(resultFiles).containsExactly(sourceArchive)
        }
    }
}