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

package androidx.build.dependencyTracker

import androidx.build.dependencyTracker.AffectedModuleDetector.Companion.ENABLE_ARG
import androidx.build.getDistributionDirectory
import androidx.build.gradle.isRoot
import androidx.build.isRunningOnBuildServer
import org.gradle.BuildAdapter
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger

/**
 * The subsets we allow the projects to be partitioned into.
 * This is to allow more granular testing. Specifically, to enable running large tests on
 * CHANGED_PROJECTS, and
 *
 * The subsets are:
 *  CHANGED_PROJECTS -- The containing projects for any files that were changed in this CL.
 *
 *  DEPENDENT_PROJECTS -- Any projects that have a dependency on any of the projects
 *      in the CHANGED_PROJECTS set.
 *
 *  ALL_AFFECTED_PROJECTS -- The union of CHANGED_PROJECTS and DEPENDENT_PROJECTS,
 *      which encompasses all projects that could possibly break due to the changes.
 */
internal enum class ProjectSubset { DEPENDENT_PROJECTS, CHANGED_PROJECTS, ALL_AFFECTED_PROJECTS }

/**
 * A utility class that can discover which files are changed based on git history.
 *
 * To enable this, you need to pass [ENABLE_ARG] into the build as a command line parameter
 * (-P<name>)
 *
 * Passing [DEPENDENT_PROJECTS_ARG] will result in only DEPENDENT_PROJECTS being returned (see enum)
 * Passing [CHANGED_PROJECTS_ARG] will behave likewise.
 *
 * If neither of those are passed, [ALL_AFFECTED_PROJECTS] is returned.
 *
 * Currently, it checks git logs to find last merge CL to discover where the anchor CL is.
 *
 * Eventually, we'll move to the props passed down by the build system when it is available.
 *
 * Since this needs to check project dependency graph to work, it cannot be accessed before
 * all projects are loaded. Doing so will throw an exception.
 */
abstract class AffectedModuleDetector {
    /**
     * Returns whether this project was affected by current changes..
     */
    abstract fun shouldInclude(project: Project): Boolean

    companion object {
        private const val ROOT_PROP_NAME = "affectedModuleDetector"
        private const val LOG_FILE_NAME = "affected_module_detector_log.txt"
        private const val ENABLE_ARG = "androidx.enableAffectedModuleDetection"
        private const val DEPENDENT_PROJECTS_ARG = "androidx.dependentProjects"
        private const val CHANGED_PROJECTS_ARG = "androidx.changedProjects"
        @JvmStatic
        fun configure(gradle: Gradle, rootProject: Project) {
            val enabled = rootProject.hasProperty(ENABLE_ARG)
            val subset = when {
                rootProject.hasProperty(DEPENDENT_PROJECTS_ARG) -> ProjectSubset.DEPENDENT_PROJECTS
                rootProject.hasProperty(CHANGED_PROJECTS_ARG)
                    -> ProjectSubset.CHANGED_PROJECTS
                else -> ProjectSubset.ALL_AFFECTED_PROJECTS
            }
            val inBuildServer = isRunningOnBuildServer()
            if (!enabled && !inBuildServer) {
                setInstance(rootProject, AcceptAll())
                return
            }
            val logger = ToStringLogger.createWithLifecycle(gradle) { log ->
                val distDir = rootProject.getDistributionDirectory()
                distDir.let {
                    val outputFile = it.resolve(LOG_FILE_NAME)
                    outputFile.writeText(log)
                    println("wrote dependency log to ${outputFile.absolutePath}")
                }
            }
            logger.info("setup: enabled: $enabled, inBuildServer: $inBuildServer")
            gradle.addBuildListener(object : BuildAdapter() {
                override fun projectsEvaluated(gradle: Gradle?) {
                    logger.lifecycle("projects evaluated")
                    AffectedModuleDetectorImpl(
                            rootProject = rootProject,
                            logger = logger,
                            ignoreUnknownProjects = false,
                            projectSubset = subset
                    ).also {
                        if (!enabled) {
                            logger.info("swapping with accept all")
                            // doing it just for testing
                            setInstance(rootProject, AcceptAll(it, logger))
                        } else {
                            logger.info("using real detector")
                            setInstance(rootProject, it)
                        }
                    }
                }
            })
        }

        private fun setInstance(
            rootProject: Project,
            detector: AffectedModuleDetector
        ) {
            if (!rootProject.isRoot) {
                throw IllegalArgumentException("this should've been the root project")
            }
            rootProject.extensions.add(ROOT_PROP_NAME, detector)
        }

        private fun getInstance(project: Project): AffectedModuleDetector? {
            val extensions = project.rootProject.extensions
            return extensions.getByName(ROOT_PROP_NAME) as? AffectedModuleDetector
        }

        private fun getOrThrow(project: Project): AffectedModuleDetector {
            return getInstance(project) ?: throw GradleException(
                    """
                        Tried to get affected module detector too early.
                        You cannot access it until all projects are evaluated.
                    """.trimIndent())
        }

        /**
         * Call this method to configure the given task to execute only if the owner project
         * is affected by current changes
         */
        @Throws(GradleException::class)
        @JvmStatic
        fun configureTaskGuard(task: Task) {
            task.onlyIf {
                getOrThrow(task.project).shouldInclude(task.project)
            }
        }
    }
}

/**
 * Implementation that accepts everything without checking.
 */
private class AcceptAll(
    private val wrapped: AffectedModuleDetector? = null,
    private val logger: Logger? = null
) : AffectedModuleDetector() {
    override fun shouldInclude(project: Project): Boolean {
        val wrappedResult = wrapped?.shouldInclude(project)
        logger?.info("[AcceptAll] wrapper returned $wrappedResult but i'll return true")
        return true
    }
}

/**
 * Real implementation that checks git logs to decide what is affected.
 *
 * If any file outside a module is changed, we assume everything has changed.
 *
 * When a file in a module is changed, all modules that depend on it are considered as changed.
 */
internal class AffectedModuleDetectorImpl constructor(
    private val rootProject: Project,
    private val logger: Logger?,
        // used for debugging purposes when we want to ignore non module files
    private val ignoreUnknownProjects: Boolean = false,
    private val projectSubset: ProjectSubset = ProjectSubset.ALL_AFFECTED_PROJECTS,
    private val injectedGitClient: GitClient? = null
) : AffectedModuleDetector() {
    private val git by lazy {
        injectedGitClient ?: GitClientImpl(rootProject.projectDir, logger)
    }

    private val dependencyTracker by lazy {
        DependencyTracker(rootProject, logger)
    }

    private val allProjects by lazy {
        rootProject.subprojects.toSet()
    }

    private val projectGraph by lazy {
        ProjectGraph(rootProject, logger)
    }

    val affectedProjects by lazy {
        findLocallyAffectedProjects()
    }

    override fun shouldInclude(project: Project): Boolean {
        return (project.isRoot || affectedProjects.contains(project)).also {
            logger?.info("checking whether i should include ${project.path} and my answer is $it")
        }
    }

    /**
     * By default, finds all modules that are affected by current changes
     *
     * With param dependentProjects, finds only modules dependent on directly changed modules
     *
     * With param changedProjects, finds only directly changed modules
     *
     * If it cannot determine the containing module for a file (e.g. buildSrc or root), it
     * defaults to all projects unless [ignoreUnknownProjects] is set to true.
     */
    private fun findLocallyAffectedProjects(): Set<Project> {
        val lastMergeSha = git.findPreviousMergeCL() ?: return allProjects
        val changedFiles = git.findChangedFilesSince(
                sha = lastMergeSha,
                includeUncommitted = true)
        if (changedFiles.isEmpty()) {
            logger?.info("Cannot find any changed files after last merge, will run all")
            return allProjects
        }
        val containingProjects = changedFiles
                .map(::findContainingProject)
                .let {
                    if (ignoreUnknownProjects) {
                        it.filterNotNull()
                    } else {
                        it
                    }
                }
        if (containingProjects.any { it == null }) {
            logger?.info("couldn't find containing file for some projects, returning all projects")
            logger?.info(
                    """
                        if i was going to check for what i've found, i would've returned
                        ${expandToDependents(containingProjects.filterNotNull())}
                    """.trimIndent()
            )
            return allProjects
        }
        val alwaysBuild = rootProject.subprojects.filter { project ->
            ALWAYS_BUILD.any {
                project.name.contains(it)
            }
        }.toSet()

        return alwaysBuild + when (projectSubset) {
            ProjectSubset.DEPENDENT_PROJECTS
                -> expandToDependents(containingProjects) - containingProjects.filterNotNull()
            ProjectSubset.CHANGED_PROJECTS
                -> (containingProjects).filterNotNull().toSet()
            else -> expandToDependents(containingProjects)
        }
    }

    private fun expandToDependents(containingProjects: List<Project?>): Set<Project> {
        return containingProjects.flatMapTo(mutableSetOf()) {
            dependencyTracker.findAllDependents(it!!)
        }
    }

    private fun findContainingProject(filePath: String): Project? {
        return projectGraph.findContainingProject(filePath).also {
            logger?.info("search result for $filePath resulted in ${it?.path}")
        }
    }

    companion object {
        // dummy test to ensure no failure due to "no instrumentation. See b/112645580
        // and b/126377106
        private val ALWAYS_BUILD = setOf("dumb-test")
    }
}