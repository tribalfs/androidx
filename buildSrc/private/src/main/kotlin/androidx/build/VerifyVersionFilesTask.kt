/*
 * Copyright 2019 The Android Open Source Project
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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.gradle.api.tasks.CacheableTask

/**
 * Task for verifying version files in Androidx artifacts
 *
 */
@CacheableTask
open class VerifyVersionFilesTask : DefaultTask() {

    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var repositoryDirectory: File

    @TaskAction
    fun verifyVersionFilesPresent() {
        repositoryDirectory.walk().forEach { file ->
            var expectedGroup = "androidx"
            if (file.path.contains("/androidx/build/support_repo/libyuv/libyuv/"))
                expectedGroup = "libyuv" // external library that we don't publish
            if (file.extension == "aar") {
                val inputStream = FileInputStream(file)
                val aarFileInputStream = ZipInputStream(inputStream)
                var entry: ZipEntry? = aarFileInputStream.nextEntry
                while (entry != null) {
                    if (entry.name == "classes.jar") {
                        var foundVersionFile = false
                        val classesJarInputStream = ZipInputStream(aarFileInputStream)
                        var jarEntry = classesJarInputStream.nextEntry
                        while (jarEntry != null) {
                            if (jarEntry.name.startsWith("META-INF/$expectedGroup.") &&
                                jarEntry.name.endsWith(".version")
                            ) {
                                foundVersionFile = true
                                break
                            }
                            jarEntry = classesJarInputStream.nextEntry
                        }
                        if (!foundVersionFile) {
                            throw Exception(
                                "Missing classes.jar/META-INF/$expectedGroup.*version " +
                                "file in ${file.absolutePath}"
                            )
                        }
                        break
                    }
                    entry = aarFileInputStream.nextEntry
                }
            }
        }
    }
}
