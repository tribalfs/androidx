/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.room.processor

import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.processing.XDeclaredType
import androidx.room.processing.XMethodElement
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_UPDATE_RESULT_ADAPTER
import androidx.room.processor.ProcessorErrors.UPDATE_MISSING_PARAMS
import androidx.room.vo.UpdateMethod
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import toJFO

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@RunWith(JUnit4::class)
class UpdateMethodProcessorTest : ShortcutMethodProcessorTest<UpdateMethod>(Update::class) {
    override fun invalidReturnTypeError(): String = CANNOT_FIND_UPDATE_RESULT_ADAPTER

    override fun noParamsError(): String = UPDATE_MISSING_PARAMS

    override fun process(
        baseContext: Context,
        containing: XDeclaredType,
        executableElement: XMethodElement
    ): UpdateMethod {
        return UpdateMethodProcessor(baseContext, containing, executableElement).process()
    }

    @Test
    fun goodConflict() {
        singleShortcutMethod(
                """
                @Update(onConflict = OnConflictStrategy.REPLACE)
                abstract public void foo(User user);
                """) { shortcut, _ ->
            assertThat(shortcut.onConflictStrategy, `is`(OnConflictStrategy.REPLACE))
        }.compilesWithoutError()
    }

    @Test
    fun badConflict() {
        singleShortcutMethod(
                """
                @Update(onConflict = -1)
                abstract public void foo(User user);
                """) { _, _ ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.INVALID_ON_CONFLICT_VALUE)
    }

    @Test
    fun targetEntityMissingPrimaryKey() {
        val usernameJfo = """
            package foo.bar;
            import androidx.room.*;

            public class Username {
                String name;
            }
        """.toJFO("foo.bar.Username")
        singleShortcutMethod(
            """
                @Update(entity = User.class)
                abstract public int foo(Username username);
                """,
            additionalJFOs = listOf(usernameJfo)) { _, _ ->
        }.failsToCompile().withErrorContaining(
            ProcessorErrors.missingPrimaryKeysInPartialEntityForUpdate(
                partialEntityName = "foo.bar.Username",
                primaryKeyNames = listOf("uid")))
    }
}
