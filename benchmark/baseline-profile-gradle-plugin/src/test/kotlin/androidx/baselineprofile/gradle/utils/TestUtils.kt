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
import org.gradle.testkit.runner.GradleRunner

internal const val GRADLE_CODE_PRINT_TASK = """
    abstract class PrintTask extends DefaultTask {
        @Input abstract Property<String> getText()
        @TaskAction void exec() { println(getText().get()) }
    }

"""

internal fun GradleRunner.build(taskName: String, block: (String) -> (Unit)) {
    this
        .withArguments(taskName, "--stacktrace")
        .build()
        .output
        .let(block)
}

internal fun GradleRunner.buildAndAssertThatOutput(
    taskName: String,
    assertBlock: StringSubject.() -> (Unit)
) {
    this.build(taskName) { assertBlock(assertThat(it)) }
}
