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

package androidx.ui.test

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.random.Random

/**
 * Test rule that initiates a [RandomTextGenerator] using a different seed based on the class and
 * function name. This way each function will have a different text generated, but at each run
 * the same function will get the same text.
 *
 * This will ensure that the execution order of a test class or functions in a test class does
 * not affect others because of the native text layout cache.
 */
class RandomTextGeneratorTestRule(private val alphabet: Alphabet = Alphabet.Latin) : TestRule {
    private lateinit var textGenerator: RandomTextGenerator

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                // gives the full class and function name including the parameters
                val fullName = "${description.className}#${description.methodName}"

                textGenerator = RandomTextGenerator(alphabet, Random(fullName.hashCode()))

                base.evaluate()
            }
        }

    fun <T> generator(block: (generator: RandomTextGenerator) -> T): T {
        return block(textGenerator)
    }
}
