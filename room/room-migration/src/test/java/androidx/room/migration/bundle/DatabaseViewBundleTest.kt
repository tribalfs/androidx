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

package androidx.room.migration.bundle

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DatabaseViewBundleTest {
    @Test
    fun basic() {
        val bundle = DatabaseViewBundle("abc", "def")
        val other = DatabaseViewBundle("abc", "def")
        assertThat(bundle.isSchemaEqual(other), `is`(true))
        assertThat(bundle.viewName, `is`("abc"))
        assertThat(bundle.createSql, `is`("def"))
    }
}
