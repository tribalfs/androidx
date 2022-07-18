/*
 * Copyright (C) 2018 The Android Open Source Project
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

package androidx.testutils

import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.navigation.testing.TestNavigatorState
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestNavigatorTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun backStack() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val testNavigator = TestNavigator()
        val state = TestNavigatorState()
        testNavigator.onAttach(state)
        val destination = testNavigator.createDestination()
        val args = Bundle()
        testNavigator.navigate(listOf(state.createBackStackEntry(destination, args)), null, null)
        assertWithMessage("TestNavigator back stack size is 1 after navigate")
            .that(testNavigator.backStack.size)
            .isEqualTo(1)
        val current = testNavigator.current
        assertWithMessage("last() returns last destination navigated to")
            .that(current.destination)
            .isEqualTo(destination)
        assertWithMessage("last() returns a non-null arguments Bundle when arguments are set")
            .that(current.arguments)
            .isNotNull()
    }
}
