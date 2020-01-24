/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.startup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class AppInitializerTest {

    private lateinit var context: Context
    private lateinit var appInitializer: AppInitializer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appInitializer = AppInitializer(context)
    }

    @Test
    fun basicUsageTest() {
        appInitializer.initializeComponents(listOf<Class<*>>(InitializerNoDependencies::class.java))
        assertTrue(appInitializer.mInitialized.containsKey(InitializerNoDependencies::class.java))
    }

    @Test
    fun basicInitializationTest() {
        val initializing = mutableSetOf<Class<*>>()
        val components = listOf<Class<*>>(InitializerNoDependencies::class.java)
        appInitializer.doInitialize(components, initializing)
        assertThat(initializing.size, `is`(0))
        assertThat(appInitializer.mInitialized.size, `is`(1))
        for (component in components) {
            assertTrue(appInitializer.mInitialized.containsKey(component))
        }
    }

    @Test
    fun initializationWithDependencies() {
        val initializing = mutableSetOf<Class<*>>()
        val components = listOf<Class<*>>(InitializerWithDependency::class.java)
        appInitializer.doInitialize(components, initializing)
        assertThat(initializing.size, `is`(0))
        assertThat(appInitializer.mInitialized.size, `is`(2))
        assertTrue(appInitializer.mInitialized.containsKey(InitializerNoDependencies::class.java))
        assertTrue(appInitializer.mInitialized.containsKey(InitializerWithDependency::class.java))
    }

    @Test
    fun initializationWithCyclicDependencies() {
        val initializing = mutableSetOf<Class<*>>()
        val components = listOf<Class<*>>(CyclicDependencyInitializer::class.java)
        try {
            appInitializer.doInitialize(components, initializing)
            fail()
        } catch (exception: StartupException) {
            assertThat(exception.localizedMessage, containsString("Cycle detected."))
        }
    }
}
