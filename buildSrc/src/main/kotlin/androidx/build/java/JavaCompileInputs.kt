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

package androidx.build.java

import androidx.build.androidJarFile
import androidx.build.multiplatformExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.SourceKind
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import java.io.File

// JavaCompileInputs contains the information required to compile Java/Kotlin code
// This can be helpful for creating Metalava and Dokka tasks with the same settings
data class JavaCompileInputs(
    // Source files to process
    val sourcePaths: FileCollection,

    // Dependencies of [sourcePaths].
    val dependencyClasspath: FileCollection,

    // Android's boot classpath.
    val bootClasspath: Collection<File>
) {
    companion object {
        // Constructs a JavaCompileInputs from a library and its variant
        fun fromLibraryVariant(
            library: LibraryExtension,
            variant: BaseVariant,
            project: Project
        ): JavaCompileInputs {
            val sourceCollection = project.files(getSourcePaths(variant, project))
            sourceCollection.builtBy(variant.javaCompileProvider)
            val dependencyClasspath = variant.compileConfiguration.incoming.artifactView { config ->
                config.attributes { container ->
                    container.attribute(Attribute.of("artifactType", String::class.java), "jar")
                }
            }.artifacts.artifactFiles

            return JavaCompileInputs(
                sourceCollection,
                dependencyClasspath,
                library.bootClasspath
            )
        }

        // Constructs a JavaCompileInputs from a sourceset
        fun fromSourceSet(sourceSet: SourceSet, project: Project): JavaCompileInputs {
            val sourcePaths: Collection<File> = sourceSet.allSource.srcDirs
            val dependencyClasspath = sourceSet.compileClasspath
            return fromSourcesAndDeps(sourcePaths, dependencyClasspath, project)
        }

        fun fromSourcesAndDeps(
            sourcePaths: Collection<File>,
            dependencyClasspath: FileCollection,
            project: Project
        ): JavaCompileInputs {
            val bootClasspath: Collection<File> = androidJarFile(project).files
            val sourceCollection = project.files(sourcePaths)
            return JavaCompileInputs(sourceCollection, dependencyClasspath, bootClasspath)
        }

        private fun getSourcePaths(variant: BaseVariant, project: Project): Collection<File> {
            // If the project has the kotlin-multiplatform plugin, we want to return a combined
            // collection of all the source files inside '*main' source sets. I.e, given a module
            // with a common and Android source set, this will look inside commonMain and
            // androidMain.
            return project.multiplatformExtension?.run {
                sourceSets
                    .filter { it.name.contains("main", ignoreCase = true) }
                    .flatMap { it.kotlin.sourceDirectories }
                    .also { require(it.isNotEmpty()) }
            } ?: variant
                .getSourceFolders(SourceKind.JAVA)
                .map { folder -> folder.dir }
        }
    }
}
