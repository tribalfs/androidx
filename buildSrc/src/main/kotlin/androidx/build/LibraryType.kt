/*
 * Copyright 2020 The Android Open Source Project
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

import org.gradle.api.Project

/**
 * LibraryType represents the purpose and type of a library, whether it is a conventional library,
 * a set of samples showing how to use a conventional library, a set of lint rules for using a
 * conventional library, or any other type of published project.
 *
 * LibraryType collects a set of properties together, to make the "why" more clear and to simplify
 * setting these properties for library developers, so that only a single enum inferrable from
 * the purpose of the library needs to be set, rather than a variety of more arcane options.
 *
 * These properties are as follows:
 * LibraryType.publish represents how the library is published to GMaven
 * LibraryType.sourceJars represents whether we publish the source code for the library to GMaven
 *      in a way accessible to download, such as by Android Studio
 * LibraryType.generateDocs represents whether we generate documentation from the library to put on
 *      developer.android.com
 * LibraryType.checkApi represents whether we enforce API compatibility of the library according
 *      to our semantic versioning protocol
 *
 * The possible values of LibraryType are as follows:
 * PUBLISHED_LIBRARY: a conventional library, published, sourced, documented, and versioned
 * SAMPLES: a library of samples, published as additional properties to a conventional library,
 *      including published source. Documented in a special way, not API tracked.
 * LINT: a library of lint rules for using a conventional library. Published through lintPublish as
 *      part of an AAR, not published standalone
 * ANNOTATION_PROCESSOR: a library consisting of an annotation processor. Used only while compiling.
 * UNSET: a library that has not yet been migrated to using LibraryType. Should never be used.
 *
 * TODO: potential future LibraryTypes:
 * KOTLIN_ONLY_LIBRARY: like PUBLISHED_LIBRARY, but not intended for use from java. ktx and compose.
 * INTERNAL_TEST: compilationTarget varies? Everything else is default.
 *
 */
enum class LibraryType(
    val publish: Publish = Publish.NONE,
    val sourceJars: Boolean = false,
    val generateDocs: Boolean = false,
    val checkApi: RunApiTasks = RunApiTasks.No("Unknown Library Type"),
    val compilationTarget: CompilationTarget = CompilationTarget.DEVICE
) {
    PUBLISHED_LIBRARY(Publish.SNAPSHOT_AND_RELEASE, true, true, RunApiTasks.Yes()),
    SAMPLES(Publish.SNAPSHOT_AND_RELEASE, true, false, RunApiTasks.No("Sample Library")),
    LINT(Publish.NONE, false, false, RunApiTasks.No("Lint Library"), CompilationTarget.HOST),
    ANNOTATION_PROCESSOR(
        Publish.SNAPSHOT_AND_RELEASE, false, true,
        RunApiTasks.No("Annotation Processor"), CompilationTarget.HOST
    ),
    UNSET()
}

fun Project.isSamplesProject(): Boolean {
    return project.extensions.findByType(AndroidXExtension::class.java)?.type == LibraryType.SAMPLES
}

enum class CompilationTarget {
    /** This library is meant to run on the host machine (like an annotation processor). */
    HOST,
    /** This library is meant to run on an Android device. */
    DEVICE
}

/**
 * Publish Enum:
 * Publish.NONE -> Generates no aritfacts; does not generate snapshot artifacts
 *                 or releasable maven artifacts
 * Publish.SNAPSHOT_ONLY -> Only generates snapshot artifacts
 * Publish.SNAPSHOT_AND_RELEASE -> Generates both snapshot artifacts and releasable maven artifact
 *
 * TODO: should we introduce a Publish.lintPublish?
 */
enum class Publish {
    NONE, SNAPSHOT_ONLY, SNAPSHOT_AND_RELEASE;

    fun shouldRelease() = this == SNAPSHOT_AND_RELEASE
    fun shouldPublish() = this == SNAPSHOT_ONLY || this == SNAPSHOT_AND_RELEASE
}

sealed class RunApiTasks {
    /** Automatically determine whether API tasks should be run. */
    object Auto : RunApiTasks()
    /** Always run API tasks regardless of other project properties. */
    data class Yes(val reason: String? = null) : RunApiTasks()
    /** Do not run any API tasks. */
    data class No(val reason: String) : RunApiTasks()
}
