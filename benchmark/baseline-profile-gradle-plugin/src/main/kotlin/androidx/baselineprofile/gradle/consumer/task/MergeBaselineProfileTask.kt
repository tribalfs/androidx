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

package androidx.baselineprofile.gradle.consumer.task

import androidx.baselineprofile.gradle.consumer.RuleType
import androidx.baselineprofile.gradle.utils.TASK_NAME_SUFFIX
import androidx.baselineprofile.gradle.utils.maybeRegister
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * Collects all the baseline profile artifacts generated by all the producer configurations and
 * merges them into one, sorting and ensuring that there are no duplicated lines.
 *
 * The format of the profile is a simple list of classes and methods loaded in memory when
 * executing a test, expressed in JVM format. Duplicates can arise when multiple tests cover the
 * same code: for example when having 2 tests both covering the startup path and then doing
 * something else, both will have startup classes and methods. There is no harm in having this
 * duplication but mostly the profile file will be unnecessarily larger.
 */
@CacheableTask
abstract class MergeBaselineProfileTask : DefaultTask() {

    companion object {

        private const val MERGE_TASK_NAME = "merge"
        private const val COPY_TASK_NAME = "copy"

        // Filename parts to differentiate how to use the profile rules
        private const val FILENAME_MATCHER_BASELINE_PROFILE = "baseline-prof"
        private const val FILENAME_MATCHER_STARTUP_PROFILE = "startup-prof"

        // The output file for the HRF baseline profile file in `src/main`
        private const val BASELINE_PROFILE_FILENAME = "baseline-prof.txt"
        private const val STARTUP_PROFILE_FILENAME = "startup-prof.txt"

        internal fun maybeRegisterForMerge(
            project: Project,
            variantName: String,
            mergeAwareTaskName: String,
            hasDependencies: Boolean = false,
            library: Boolean,
            sourceProfilesFileCollection: FileCollection,
            outputDir: Provider<Directory>,
            filterRules: List<Pair<RuleType, String>> = listOf(),
            isLastTask: Boolean
        ): TaskProvider<MergeBaselineProfileTask> {
            return project
                .tasks
                .maybeRegister(MERGE_TASK_NAME, mergeAwareTaskName, TASK_NAME_SUFFIX) { task ->

                    // Sets whether or not baseline profile dependencies have been set.
                    // If they haven't, the task will fail at execution time.
                    task.hasDependencies.set(hasDependencies)

                    // Sets the name of this variant to print it in error messages.
                    task.variantName.set(variantName)

                    // These are all the configurations this task depends on,
                    // in order to consume their artifacts. Note that if this task already
                    // exist (for example if `merge` is `all`) the new artifact will be
                    // added to the existing list.
                    task.baselineProfileFileCollection.from.add(sourceProfilesFileCollection)

                    // This is the task output for the generated baseline profile. Output
                    // is always stored in the intermediates
                    task.baselineProfileDir.set(outputDir)

                    // Sets the package filter rules. Note that if this task already exists
                    // because of a mergeIntoMain rule, rules are added to the existing ones.
                    task.filterRules.addAll(filterRules)

                    // Sets whether this task has been configured for a library. In this case,
                    // startup profiles are not handled.
                    task.library.set(library)

                    // Determines whether this is the last task to be executed. This flag is used
                    // exclusively for logging purposes.
                    task.lastTask.set(isLastTask)
                }
        }

        internal fun maybeRegisterForCopy(
            project: Project,
            variantName: String,
            mergeAwareTaskName: String,
            library: Boolean,
            sourceDir: Provider<Directory>,
            outputDir: Provider<Directory>,
            isLastTask: Boolean
        ): TaskProvider<MergeBaselineProfileTask> {
            return project
                .tasks
                .maybeRegister(
                    COPY_TASK_NAME,
                    mergeAwareTaskName,
                    "baselineProfileIntoSrc"
                ) { task ->
                    task.baselineProfileFileCollection.from.add(sourceDir)
                    task.baselineProfileDir.set(outputDir)
                    task.library.set(library)
                    task.variantName.set(variantName)

                    // Determines whether this is the last task to be executed. This flag is used
                    // exclusively for logging purposes.
                    task.lastTask.set(isLastTask)
                }
        }
    }

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val lastTask: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val hasDependencies: Property<Boolean>

    @get: Input
    abstract val library: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baselineProfileFileCollection: ConfigurableFileCollection

    @get:Input
    abstract val filterRules: ListProperty<Pair<RuleType, String>>

    @get:OutputDirectory
    abstract val baselineProfileDir: DirectoryProperty

    @TaskAction
    fun exec() {

        if (hasDependencies.isPresent && !hasDependencies.get()) {
            throw GradleException(
                """
                The baseline profile consumer plugin is applied to this module but no dependency
                has been set. Please review the configuration of build.gradle for this module
                making sure that a `baselineProfile` dependency exists and points to a valid
                `com.android.test` module that has the `androidx.baselineprofile` or
                `androidx.baselineprofile.producer` plugin applied.
                """.trimIndent()
            )
        }

        // Rules are sorted for package depth and excludes are always evaluated first.
        val rules = filterRules
            .get()
            .sortedWith(
                compareBy<Pair<RuleType, String>> { r ->
                    r.second.split(".").size
                }.thenComparing { r ->
                    if (r.first == RuleType.INCLUDE) 0 else 1
                }.reversed()
            )

        // Read the profile rules from the file collection that contains the profile artifacts from
        // all the configurations for this variant and merge them in a single list.
        val profileRules = baselineProfileFileCollection.files
            .readLines {
                FILENAME_MATCHER_BASELINE_PROFILE in it.name ||
                    FILENAME_MATCHER_STARTUP_PROFILE in it.name
            }

        if (variantName.isPresent && profileRules.isEmpty()) {
            logger.warn(
                """
                No baseline profile rules were generated for the variant `${variantName.get()}`.
                This is most likely because there are no instrumentation test for it. If this
                is not intentional check that tests for this variant exist in the `baselineProfile`
                dependency module.
            """.trimIndent()
            )
        }

        // The profile rules here are:
        // - sorted (since we group by class later, we want the input to the group by operation not
        //      to be influenced by reading order)
        // - group by class and method (ignoring flag) and for each group keep only the first value
        // - apply the filters
        // - sort with comparator
        val filteredProfileRules = profileRules
            .sorted()
            .asSequence()
            .mapNotNull { ProfileRule.parse(it) }
            .groupBy { it.classDescriptor + it.methodDescriptor }
            .map { it.value[0] }
            .filter {

                // If no rules are specified, always include this line.
                if (rules.isEmpty()) return@filter true

                // Otherwise rules are evaluated in the order they've been sorted previously.
                for (r in rules) {
                    if (r.matches(it.fullClassName)) {
                        return@filter r.isInclude()
                    }
                }

                // If the rules were all excludes and nothing matched, we can include this line
                // otherwise exclude it.
                return@filter !rules.any { r -> r.isInclude() }
            }
            .sortedWith(ProfileRule.comparator)
            .map { it.underlying }

        // Check if the filters filtered out all the rules.
        if (profileRules.isNotEmpty() && filteredProfileRules.isEmpty() && rules.isNotEmpty()) {
            throw GradleException(
                """
                The baseline profile consumer plugin is configured with filters that exclude all
                the profile rules. Please review your build.gradle configuration and make sure your
                filters don't exclude all the baseline profile rules.
            """.trimIndent()
            )
        }

        baselineProfileDir
            .file(BASELINE_PROFILE_FILENAME)
            .get()
            .asFile
            .apply {
                delete()
                if (filteredProfileRules.isNotEmpty()) {
                    writeText(filteredProfileRules.joinToString(System.lineSeparator()))
                    if (lastTask.get()) {
                        logger.warn(
                            """
                            A baseline profile was generated for the variant `${variantName.get()}`:
                            file:///$absolutePath
                        """.trimIndent()
                        )
                    }
                }
            }

        // If this is a library we can stop here and don't manage the startup profiles.
        if (library.get()) {
            return
        }

        // Same process with startup profiles.
        val startupRules = baselineProfileFileCollection.files
            .readLines { FILENAME_MATCHER_STARTUP_PROFILE in it.name }

        if (variantName.isPresent && startupRules.isEmpty()) {
            logger.warn(
                """
                No startup profile rules were generated for the variant `${variantName.get()}`.
                This is most likely because there are no instrumentation test with baseline profile
                rule, which specify `includeInStartupProfile = true`. If this is not intentional
                check that tests for this variant exist in the `baselineProfile` dependency module.
            """.trimIndent()
            )
        }

        // Use same sorting without filter for startup profiles.
        val sortedProfileRules = startupRules
            .asSequence()
            .sorted()
            .mapNotNull { ProfileRule.parse(it) }
            .groupBy { it.classDescriptor + it.methodDescriptor }
            .map { it.value[0] }
            .sortedWith(ProfileRule.comparator)
            .map { it.underlying }
            .toList()

        baselineProfileDir
            .file(STARTUP_PROFILE_FILENAME)
            .get()
            .asFile
            .apply {
                delete()
                if (sortedProfileRules.isNotEmpty()) {
                    writeText(sortedProfileRules.joinToString(System.lineSeparator()))
                    if (lastTask.get()) {
                        logger.warn(
                            """
                            A startup profile was generated for the variant `${variantName.get()}`:
                            file:///$absolutePath
                        """.trimIndent()
                        )
                    }
                }
            }
    }

    private fun Pair<RuleType, String>.isInclude(): Boolean = first == RuleType.INCLUDE

    private fun Pair<RuleType, String>.matches(fullClassName: String): Boolean {
        val rule = second
        return when {
            rule.endsWith(".**") -> {
                // This matches package and subpackages
                val pkg = fullClassName.split(".").dropLast(1).joinToString(".")
                val rulePkg = rule.dropLast(3)
                pkg.startsWith(rulePkg)
            }

            rule.endsWith(".*") -> {
                // This matches only the package
                val pkgParts = fullClassName.split(".").dropLast(1)
                val pkg = pkgParts.joinToString(".")
                val rulePkg = rule.dropLast(2)
                val ruleParts = rulePkg.split(".")
                pkg.startsWith(rulePkg) && ruleParts.size == pkgParts.size
            }

            else -> {
                // This matches only the specific class name
                fullClassName == rule
            }
        }
    }

    private fun Iterable<File>.readLines(filterBlock: (File) -> (Boolean)): List<String> = this
        .flatMap {
            if (it.isFile) {
                listOf(it)
            } else {
                listOf(*(it.listFiles() ?: arrayOf()))
            }
        }
        .filter(filterBlock)
        .flatMap { it.readLines() }
}
