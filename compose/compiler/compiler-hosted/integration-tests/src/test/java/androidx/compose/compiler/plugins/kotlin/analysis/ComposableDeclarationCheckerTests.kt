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

package androidx.compose.compiler.plugins.kotlin.analysis

import androidx.compose.compiler.plugins.kotlin.AbstractComposeDiagnosticsTest

/**
 * We're strongly considering supporting try-catch-finally blocks in the future.
 * If/when we do support them, these tests should be deleted.
 */
class ComposableDeclarationCheckerTests : AbstractComposeDiagnosticsTest() {
    fun testPropertyWithInitializer() {
        doTest(
            """
            import androidx.compose.runtime.Composable

            @Composable
            val <!COMPOSABLE_PROPERTY_BACKING_FIELD!>foo<!>: Int = 123
        """)
    }

    fun testPropertyWithJustGetter() {
        doTest(
            """
            import androidx.compose.runtime.Composable

            @Composable
            val bar: Int get() = 123
        """)
    }

    fun testPropertyWithGetterAndSetter() {
        doTest(
            """
            import androidx.compose.runtime.Composable

            @Composable
            var <!COMPOSABLE_VAR!>bam<!>: Int 
                get() { return 123 }
                set(value) { print(value) }
        """)
    }

    fun testSuspendComposable() {
        doTest(
            """
            import androidx.compose.runtime.Composable

            @Composable suspend fun <!COMPOSABLE_SUSPEND_FUN!>Foo<!>() {}

            fun acceptSuspend(fn: suspend () -> Unit) { print(fn) }
            fun acceptComposableSuspend(fn: @Composable suspend () -> Unit) { print(fn.hashCode()) }

            val foo: suspend () -> Unit = <!TYPE_MISMATCH!>@Composable {}<!>
            val bar: suspend () -> Unit = {}
            fun Test() {
                val composableLambda = @Composable {}
                acceptSuspend <!TYPE_MISMATCH!>@Composable {}<!>
                acceptComposableSuspend @Composable {}
                acceptComposableSuspend(<!UNSUPPORTED_FEATURE!>composableLambda<!>)
                acceptSuspend(<!COMPOSABLE_SUSPEND_FUN, TYPE_MISMATCH!>@Composable suspend fun() { }<!>)
            }
        """)
    }
}
