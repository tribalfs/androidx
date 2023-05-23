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

package androidx.baselineprofile.gradle.utils

import com.google.common.truth.StringSubject
import com.google.common.truth.Truth.assertThat
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.testkit.runner.GradleRunner

internal val GRADLE_CODE_PRINT_TASK = """
    abstract class DisplaySourceSets extends DefaultTask {
        @Input abstract ListProperty<Directory> getSrcs()
        @TaskAction void exec() {
            srcs.get().forEach { directory -> println(directory) }
        }
    }

    abstract class PrintTask extends DefaultTask {
        @Input abstract Property<String> getText()
        @TaskAction void exec() { println(getText().get()) }
    }

    androidComponents {
        def agpVersion = pluginVersion.major + "." + pluginVersion.minor + "." + pluginVersion.micro
        if (pluginVersion.previewType != null) {
            agpVersion += "-" + pluginVersion.previewType + pluginVersion.preview
        }
        println("agpVersion=" + agpVersion)
    }

    """.trimIndent()

internal fun GradleRunner.build(vararg arguments: String, block: (String) -> (Unit)) = this
    .withArguments(*arguments, "--stacktrace")
    .build()
    .output
    .also(block)

internal fun GradleRunner.buildAndFail(vararg arguments: String, block: (String) -> (Unit)) = this
    .withArguments(*arguments, "--stacktrace")
    .buildAndFail()
    .output
    .also(block)

internal fun GradleRunner.buildAndAssertThatOutput(
    vararg arguments: String,
    assertBlock: StringSubject.() -> (Unit)
) {
    this.build(*arguments) { assertBlock(assertThat(it)) }
}

internal fun GradleRunner.buildAndFailAndAssertThatOutput(
    vararg arguments: String,
    assertBlock: StringSubject.() -> (Unit)
) {
    this.buildAndFail(*arguments) { assertBlock(assertThat(it)) }
}

internal fun List<String>.requireInOrder(
    vararg strings: String,
    evaluate: (String, String) -> (Boolean) = { line, nextToFind -> line.startsWith(nextToFind) },
): List<String> {
    val remaining = mutableListOf(*strings)
    for (string in strings) {
        val next = remaining.firstOrNull() ?: break
        if (evaluate(string, next)) remaining.remove(next)
    }
    return remaining
}

internal fun List<String>.require(
    vararg strings: String,
    evaluate: (String, String) -> (Boolean) = { line, nextToFind -> line.startsWith(nextToFind) },
): Set<String> {
    val remaining = mutableSetOf(*strings)
    for (string in strings) {
        if (remaining.isEmpty()) break
        val iter = remaining.iterator()
        while (iter.hasNext()) iter.next().run {
            if (evaluate(string, this)) {
                iter.remove()
                return@run
            }
        }
    }
    return remaining
}

fun camelCase(vararg strings: String): String {
    if (strings.isEmpty()) return ""
    return StringBuilder().apply {
        var shouldCapitalize = false
        for (str in strings.filter { it.isNotBlank() }) {
            append(if (shouldCapitalize) str.capitalized() else str)
            shouldCapitalize = true
        }
    }.toString()
}
