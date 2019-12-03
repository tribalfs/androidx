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

package androidx.build.metalava

import androidx.build.checkapi.ApiLocation
import androidx.build.java.JavaCompileInputs
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.WorkParameters
import java.io.File
import javax.inject.Inject

// MetalavaRunner stores common configuration for executing Metalava

fun runMetalavaWithArgs(metalavaJar: File, args: List<String>, workerExecutor: WorkerExecutor) {
    val allArgs = listOf(
        "--no-banner",
        "--hide",
        "HiddenSuperclass" // We allow having a hidden parent class
    ) + args

    val workQueue = workerExecutor.noIsolation()
    workQueue.submit(MetalavaWorkAction::class.java) { parameters ->
        parameters.getArgs().set(allArgs)
        parameters.getMetalavaJar().set(metalavaJar)
    }
}

interface MetalavaParams : WorkParameters {
    fun getArgs(): ListProperty<String>
    fun getMetalavaJar(): Property<File>
}

abstract class MetalavaWorkAction @Inject constructor (
    private val execOperations: ExecOperations
) : WorkAction<MetalavaParams> {

    override fun execute() {
        val allArgs = getParameters().getArgs().get()
        val metalavaJar = getParameters().getMetalavaJar().get()

        execOperations.javaexec {
            it.classpath(metalavaJar)
            it.main = "com.android.tools.metalava.Driver"
            it.args = allArgs
        }
    }
}

fun Project.getMetalavaJar(): File {
    return getMetalavaConfiguration().resolvedConfiguration.files.iterator().next()
}

fun Project.getMetalavaConfiguration(): Configuration {
    return configurations.findByName("metalava") ?: configurations.create("metalava") {
        val dependency = dependencies.create("com.android:metalava:1.3.0:shadow@jar")
        it.dependencies.add(dependency)
    }
}

// Metalava arguments to hide all experimental API surfaces.
val HIDE_EXPERIMENTAL_ARGS: List<String> = listOf(
    "--hide-annotation", "androidx.annotation.experimental.Experimental",
    "--hide-annotation", "kotlin.Experimental",
    "--hide-meta-annotation", "androidx.annotation.experimental.Experimental",
    "--hide-meta-annotation", "kotlin.Experimental"
)

val API_LINT_ARGS: List<String> = listOf(
    "--api-lint",
    "--hide",
    listOf(
        // The list of checks that are hidden as they are not useful in androidx
        "Enum", // Enums are allowed to be use in androidx
        "CallbackInterface", // With target Java 8, we have default methods
        "ProtectedMember", // We allow using protected members in androidx
        "ManagerLookup", // Managers in androidx are not the same as platfrom services
        "ManagerConstructor",
        "RethrowRemoteException", // This check is for calls into system_server
        "PackageLayering", // This check is not relevant to androidx.* code.
        "UserHandle", // This check is not relevant to androidx.* code.
        "ParcelableList", // This check is only relevant to android platform that has managers.

        // List of checks that have bugs, but should be enabled once fixed.
        "GetterSetterNames", // b/135498039
        "StaticUtils", // b/135489083
        "AllUpper", // b/135708486
        "StartWithLower", // b/135710527

        // The list of checks that are API lint warnings and are yet to be enabled
        "TopLevelBuilder",
        "BuilderSetStyle",
        "ExecutorRegistration",
        "NotCloseable",
        "UserHandleName",
        "UseIcu",
        "NoByteOrShort",
        "CommonArgsFirst",
        "SamShouldBeLast",
        "MissingJvmStatic",

        // We should only treat these as warnings
        "IntentBuilderName",
        "OnNameExpected"
    ).joinToString(),
    "--error",
    listOf(
        "MinMaxConstant",
        "MissingBuild",
        "SetterReturnsThis",
        "OverlappingConstants",
        "IllegalStateException",
        "ListenerLast",
        "StreamFiles",
        "AbstractInner",
        "ArrayReturn",
        "MethodNameTense"
    ).joinToString()
)

sealed class GenerateApiMode {
    object PublicApi : GenerateApiMode()
    object RestrictedApi : GenerateApiMode()
    object ExperimentalApi : GenerateApiMode()
}

sealed class ApiLintMode {
    class CheckBaseline(val apiLintBaseline: File) : ApiLintMode()
    object Skip : ApiLintMode()
}

// Generates all of the specified api files
fun Project.generateApi(
    files: JavaCompileInputs,
    apiLocation: ApiLocation,
    tempDir: File,
    apiLintMode: ApiLintMode,
    includeRestrictedApis: Boolean,
    workerExecutor: WorkerExecutor
) {
    generateApi(files.bootClasspath, files.dependencyClasspath, files.sourcePaths.files,
        apiLocation.publicApiFile, tempDir, GenerateApiMode.PublicApi, apiLintMode, workerExecutor)
    generateApi(files.bootClasspath, files.dependencyClasspath, files.sourcePaths.files,
        apiLocation.experimentalApiFile, tempDir, GenerateApiMode.ExperimentalApi, apiLintMode,
        workerExecutor)
    if (includeRestrictedApis) {
        generateApi(files.bootClasspath, files.dependencyClasspath, files.sourcePaths.files,
            apiLocation.restrictedApiFile, tempDir, GenerateApiMode.RestrictedApi, ApiLintMode.Skip,
            workerExecutor)
    }
}

// Generates the specified api file
fun Project.generateApi(
    bootClasspath: Collection<File>,
    dependencyClasspath: FileCollection,
    sourcePaths: Collection<File>,
    outputFile: File,
    tempDir: File,
    generateApiMode: GenerateApiMode,
    apiLintMode: ApiLintMode,
    workerExecutor: WorkerExecutor
) {
    val tempOutputFile = if (generateApiMode is GenerateApiMode.RestrictedApi) {
        File(tempDir, outputFile.name + ".tmp")
    } else {
        outputFile
    }

    // generate public API txt
    val args = mutableListOf(
        "--classpath",
        (bootClasspath + dependencyClasspath.files).joinToString(File.pathSeparator),

        "--source-path",
        sourcePaths.filter { it.exists() }.joinToString(File.pathSeparator),

        "--api",
        tempOutputFile.toString(),

        "--format=v3",
        "--output-kotlin-nulls=yes"
    )

    when (generateApiMode) {
        is GenerateApiMode.PublicApi -> {
            args += HIDE_EXPERIMENTAL_ARGS
        }
        is GenerateApiMode.RestrictedApi -> {
            // Show restricted APIs despite @hide.
            args += listOf(
                "--show-annotation", "androidx.annotation.RestrictTo",
                "--show-unannotated"
            )
            args += HIDE_EXPERIMENTAL_ARGS
        }
        is GenerateApiMode.ExperimentalApi -> {
            // No additional args needed.
        }
    }

    when (apiLintMode) {
        is ApiLintMode.CheckBaseline -> {
            args += API_LINT_ARGS
            if (apiLintMode.apiLintBaseline.exists()) {
                args += listOf("--baseline", apiLintMode.apiLintBaseline.toString())
            }
            args.addAll(listOf(
                "--error",
                "DeprecationMismatch", // Enforce deprecation mismatch
                "--error",
                "ReferencesDeprecated"
            ))
        }
        is ApiLintMode.Skip -> {
            args.addAll(listOf(
                "--hide",
                "DeprecationMismatch",
                "--hide",
                "UnhiddenSystemApi",
                "--hide",
                "ReferencesHidden",
                "--hide",
                "ReferencesDeprecated"
            ))
        }
    }

    if (generateApiMode is GenerateApiMode.RestrictedApi) {
        runMetalavaWithArgs(getMetalavaJar(), args, workerExecutor)
        // TODO(119617147): when we no longer need to fix Metalava's output, remove this "await"
        workerExecutor.await()
        removeRestrictToLibraryLines(tempOutputFile, outputFile)
    } else {
        runMetalavaWithArgs(getMetalavaJar(), args, workerExecutor)
    }
}

// until b/119617147 is done, remove lines containing "@RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)"
private fun removeRestrictToLibraryLines(inputFile: File, outputFile: File) {
    val outputBuilder = StringBuilder()
    val lines = inputFile.readLines()
    var skipScopeUntil: String? = null
    for (line in lines) {
        val skip = line.contains("@RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)")
        if (skip && line.endsWith("{")) {
            skipScopeUntil = line.commonPrefixWith("    ") + "}"
        }
        if (!skip && skipScopeUntil == null) {
            outputBuilder.append(line)
            outputBuilder.append("\n")
        }
        if (line == skipScopeUntil) {
            skipScopeUntil = null
        }
    }
    if (skipScopeUntil != null) {
        throw GradleException("Skipping until `$skipScopeUntil`, but found EOF")
    }
    outputFile.writeText(outputBuilder.toString())
}
