/*
 * Copyright 2017 The Android Open Source Project
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

package android.arch.navigation.safe.args.generator

import android.arch.navigation.safe.args.generator.models.Action
import android.arch.navigation.safe.args.generator.models.Argument
import android.arch.navigation.safe.args.generator.models.Destination
import android.arch.navigation.safe.args.generator.models.Id
import com.squareup.javapoet.ClassName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail

@RunWith(JUnit4::class)
class NavArgumentResolverTest {

    private fun id(id: String) = Id("a.b", id)

    private fun createTemplateDestination(name: String) =
            Destination(
                    id(name), ClassName.get("foo", "Fragment${name.capitalize()}"), "test",
                    listOf(
                            Argument("arg1", StringType),
                            Argument("arg2", StringType, "foo")
                    ), emptyList())

    @Test
    fun test() {
        val dest1Template = createTemplateDestination("first")
        val dest2Template = createTemplateDestination("second")
        val outerScopeAction = Action(id("toOuterScope"), id("outerScope"),
                listOf(Argument("boo", StringType)))
        val dest1 = dest1Template.copy(actions = listOf(Action(id("action1"), dest2Template.id),
                outerScopeAction))
        val dest2 = dest2Template.copy(actions = listOf(Action(id("action2"), dest1Template.id,
                listOf(Argument("arg1", StringType, "actionValue"),
                        Argument("actionArg", StringType)))))

        val topLevel = Destination(null, null, "test",
                emptyList(), emptyList(), listOf(dest1, dest2))

        val resolveArguments = resolveArguments(topLevel)
        assertThat(resolveArguments.nested.size, `is`(2))

        val resolvedAction1 = Action(id("action1"), dest2Template.id, dest2.args)
        assertThat(resolveArguments.nested[0].actions, `is`(listOf(resolvedAction1,
                outerScopeAction)))

        val resolvedAction2 = Action(id("action2"), dest1Template.id, listOf(
                Argument("arg1", StringType, "actionValue"),
                Argument("actionArg", StringType),
                Argument("arg2", StringType, "foo")
        ))
        assertThat(resolveArguments.nested[1].actions, `is`(listOf(resolvedAction2)))
    }

    @Test
    fun testIncompatibleTypes() {
        val dest1 = createTemplateDestination("first")
        val invalidAction = Action(id("action"), dest1.id, listOf(
                Argument("arg2", IntegerType, "11"),
                Argument("arg1", StringType)
        ))

        val topLevel = Destination(null, null, "test", emptyList(), listOf(invalidAction),
                listOf(dest1))

        try {
            resolveArguments(topLevel)
            fail()
        } catch (ex: IllegalArgumentException) {
            // expected error
        }
    }
}