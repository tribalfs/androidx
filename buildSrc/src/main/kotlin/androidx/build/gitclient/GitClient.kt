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

package androidx.build.gitclient

import androidx.build.releasenotes.getAOSPLink
import androidx.build.releasenotes.getBuganizerLink
import org.gradle.api.logging.Logger
import java.io.File
import java.util.concurrent.TimeUnit

interface GitClient {
    fun findChangedFilesSince(
        sha: String,
        top: String = "HEAD",
        includeUncommitted: Boolean = false
    ): List<String>
    fun findPreviousMergeCL(): String?

    fun getGitLog(
        gitCommitRange: GitCommitRange,
        keepMerges: Boolean,
        fullProjectDir: File
    ): List<Commit>

    /**
     * Abstraction for running execution commands for testability
     */
    interface CommandRunner {
        /**
         * Executes the given shell command and returns the stdout as a string.
         */
        fun execute(command: String): String
        /**
         * Executes the given shell command and returns the stdout by lines.
         */
        fun executeAndParse(command: String): List<String>
    }
}
/**
 * A simple git client that uses system process commands to communicate with the git setup in the
 * given working directory.
 */
class GitClientImpl(
    /**
     * The root location for git
     */
    private val workingDir: File,
    private val logger: Logger? = null,
    private val commandRunner: GitClient.CommandRunner = RealCommandRunner(
            workingDir = workingDir,
            logger = logger
    )
) : GitClient {

    private val gitRoot: File = findGitDirInParentFilepath(workingDir) ?: workingDir

    /**
     * Finds changed file paths since the given sha
     */
    override fun findChangedFilesSince(
        sha: String,
        top: String,
        includeUncommitted: Boolean
    ): List<String> {
        // use this if we don't want local changes
        return commandRunner.executeAndParse(if (includeUncommitted) {
            "$CHANGED_FILES_CMD_PREFIX $sha"
        } else {
            "$CHANGED_FILES_CMD_PREFIX $top $sha"
        })
    }

    /**
     * checks the history to find the first merge CL.
     */
    override fun findPreviousMergeCL(): String? {
        return commandRunner.executeAndParse(PREV_MERGE_CMD)
                .firstOrNull()
                ?.split(" ")
                ?.firstOrNull()
    }

    private fun findGitDirInParentFilepath(filepath: File): File? {
        var curDirectory: File = filepath
        while (curDirectory.path != "/") {
            if (File("$curDirectory/.git").exists()) {
                return curDirectory
            }
            curDirectory = curDirectory.parentFile
        }
        return null
    }

    private fun parseCommitLogString(
        commitLogString: String,
        commitStartDelimiter: String,
        commitSHADelimiter: String,
        subjectDelimiter: String,
        authorEmailDelimiter: String,
        localProjectDir: String
    ): List<Commit> {
        // Split commits string out into individual commits (note: this removes the deliminter)
        val gitLogStringList: List<String>? = commitLogString.split(commitStartDelimiter)
        var commitLog: MutableList<Commit> = mutableListOf()
        gitLogStringList?.filter { gitCommit ->
            gitCommit.trim() != ""
        }?.forEach { gitCommit ->
            commitLog.add(
                Commit(
                    gitCommit,
                    localProjectDir,
                    commitSHADelimiter = commitSHADelimiter,
                    subjectDelimiter = subjectDelimiter,
                    authorEmailDelimiter = authorEmailDelimiter
                ))
        }
        return commitLog.toList()
    }

    /**
     * Converts a diff log command into a [List<Commit>]
     *
     * @param gitCommitRange the [GitCommitRange] that defines the parameters of the git log command
     * @param keepMerges boolean for whether or not to add merges to the return [List<Commit>].
     * @param fullProjectDir a [File] object that represents the full project directory.
     */
    override fun getGitLog(
        gitCommitRange: GitCommitRange,
        keepMerges: Boolean,
        fullProjectDir: File
    ): List<Commit> {
        val commitStartDelimiter: String = "_CommitStart"
        val commitSHADelimiter: String = "_CommitSHA:"
        val subjectDelimiter: String = "_Subject:"
        val authorEmailDelimiter: String = "_Author:"
        val dateDelimiter: String = "_Date:"
        val bodyDelimiter: String = "_Body:"
        val localProjectDir: String = fullProjectDir.toString()
            .removePrefix(gitRoot.toString())

        var gitLogOptions: String =
            "--pretty=format:$commitStartDelimiter%n" +
                    "$commitSHADelimiter%H%n" +
                    "$authorEmailDelimiter%ae%n" +
                    "$dateDelimiter%ad%n" +
                    "$subjectDelimiter%s%n" +
                    "$bodyDelimiter%b" +
                    if (!keepMerges) {
                        " --no-merges"
                    } else {
                        ""
                    }
        var gitLogCmd: String
        if (gitCommitRange.sha != "") {
            gitLogCmd = "$GIT_LOG_CMD_PREFIX $gitLogOptions " +
                    "${gitCommitRange.sha}..${gitCommitRange.top} $fullProjectDir"
        } else {
            gitLogCmd = "$GIT_LOG_CMD_PREFIX $gitLogOptions -n ${gitCommitRange.n} $fullProjectDir"
        }
        val gitLogString: String = commandRunner.execute(gitLogCmd)
        return parseCommitLogString(
            gitLogString,
            commitStartDelimiter,
            commitSHADelimiter,
            subjectDelimiter,
            authorEmailDelimiter,
            localProjectDir
        )
    }

    private class RealCommandRunner(
        private val workingDir: File,
        private val logger: Logger?
    ) : GitClient.CommandRunner {
        override fun execute(command: String): String {
            val parts = command.split("\\s".toRegex())
            logger?.info("running command $command")
            val proc = ProcessBuilder(*parts.toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            proc.waitFor(1, TimeUnit.MINUTES)
            val response = proc
                .inputStream
                .bufferedReader()
                .readText()
            logger?.info("Response: $response")
            return response
        }
        override fun executeAndParse(command: String): List<String> {
            val response = execute(command)
                .split(System.lineSeparator())
                .filterNot {
                    it.isEmpty()
                }
            return response
        }
    }

    companion object {
        const val PREV_MERGE_CMD = "git log -1 --merges --oneline"
        const val CHANGED_FILES_CMD_PREFIX = "git diff --name-only"
        const val GIT_LOG_CMD_PREFIX = "git log --name-only"
    }
}

enum class CommitType {
    NEW_FEATURE, API_CHANGE, BUG_FIX, EXTERNAL_CONTRIBUTION;
    companion object {
        fun getTitle(commitType: CommitType): String {
            return when (commitType) {
                NEW_FEATURE -> "New Features"
                API_CHANGE -> "API Changes"
                BUG_FIX -> "Bug Fixes"
                EXTERNAL_CONTRIBUTION -> "External Contribution"
            }
        }
    }
}

/**
 * Defines the parameters for a git log command
 *
 * @property top the last SHA included in the git log.  Defaults to HEAD
 * @property sha the SHA at which the git log starts. Set to an empty string to use [n]
 * @property n a count of how many commits to go back to.  Only used when [sha] is an empty string
 */
data class GitCommitRange(
    val top: String = "HEAD",
    val sha: String = "",
    val n: Int = 0
)

/**
 * Class implementation of a git commit.  It uses the input delimiters to parse the commit
 *
 * @property gitCommit a string representation of a git commit
 * @property projectDir the project directory for which to parse file paths from a commit
 * @property commitSHADelimiter the term to use to search for the commit SHA
 * @property subjectDelimiter the term to use to search for the subject (aka commit summary)
 * @property changeIdDelimiter the term to use to search for the change-id in the body of the commit
 *           message
 * @property authorEmailDelimiter the term to use to search for the author email
 */
data class Commit(
    val gitCommit: String,
    val projectDir: String,
    private val commitSHADelimiter: String = "_CommitSHA:",
    private val subjectDelimiter: String = "_Subject:",
    private val authorEmailDelimiter: String = "_Author:"
) {
    private val changeIdDelimiter: String = "Change-Id:"
    var bugs: MutableList<Int> = mutableListOf()
    var files: MutableList<String> = mutableListOf()
    var sha: String = ""
    var authorEmail: String = ""
    var changeId: String = ""
    var summary: String = ""
    var type: CommitType = CommitType.BUG_FIX

    init {
        val listedCommit: List<String> = gitCommit.split('\n')
        listedCommit.filter { line -> line.trim() != "" }.forEach { line ->
            if (commitSHADelimiter in line) {
                getSHAFromGitLine(line)
            }
            if (subjectDelimiter in line) {
                getSummary(line)
            }
            if (changeIdDelimiter in line) {
                getChangeIdFromGitLine(line)
            }
            if (authorEmailDelimiter in line) {
                getAuthorEmailFromGitLine(line)
            }
            if ("Bug:" in line ||
                "b/" in line ||
                "bug:" in line ||
                "Fixes:" in line ||
                "fixes b/" in line
            ) {
                getBugsFromGitLine(line)
            }
            if (projectDir.trim('/') in line) {
                getFileFromGitLine(line)
            }
        }
    }

    private fun isExternalAuthorEmail(authorEmail: String): Boolean {
        return !(authorEmail.contains("@google.com"))
    }

    /**
     * Parses SHAs from git commit line, with the format:
     * [Commit.commitSHADelimiter] <commitSHA>
     */
    private fun getSHAFromGitLine(line: String) {
        sha = line.substringAfter(commitSHADelimiter).trim()
    }

    /**
     * Parses subject from git commit line, with the format:
     * [Commit.subjectDelimiter]<commit subject>
     */
    private fun getSummary(line: String) {
        summary = line.substringAfter(subjectDelimiter).trim()
    }

    /**
     * Parses commit Change-Id lines, with the format:
     * `commit.changeIdDelimiter` <changeId>
     */
    private fun getChangeIdFromGitLine(line: String) {
        changeId = line.substringAfter(changeIdDelimiter).trim()
    }

    /**
     * Parses commit author lines, with the format:
     * [Commit.authorEmailDelimiter]email@google.com
     */
    private fun getAuthorEmailFromGitLine(line: String) {
        authorEmail = line.substringAfter(authorEmailDelimiter).trim()
        if (isExternalAuthorEmail(authorEmail)) {
            type = CommitType.EXTERNAL_CONTRIBUTION
        }
    }

    /**
     * Parses filepath to get changed files from commit, with the format:
     * {project_directory}/{filepath}
     */
    private fun getFileFromGitLine(filepath: String) {
        files.add(filepath.trim())
        if (filepath.contains("current.txt") && type != CommitType.EXTERNAL_CONTRIBUTION) {
            type = CommitType.API_CHANGE
        }
    }

    /**
     *  Parses bugs from a git commit message line
     */
    private fun getBugsFromGitLine(line: String) {
        var formattedLine = line.replace("b/", " ")
        formattedLine = formattedLine.replace(":", " ")
        formattedLine = formattedLine.replace(",", " ")
        var words: List<String> = formattedLine.split(' ')
        words.forEach { word ->
            var possibleBug: Int? = word.toIntOrNull()
            if (possibleBug != null && possibleBug > 1000) {
                bugs.add(possibleBug)
            }
        }
    }

    override fun toString(): String {
        var commitString: String = summary
        commitString += " ${getAOSPLink(changeId)}"
        bugs.forEach { bug ->
            commitString += " ${getBuganizerLink(bug)}"
        }
        return commitString
    }
}
