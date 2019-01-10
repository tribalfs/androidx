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

package androidx.navigation.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import androidx.navigation.NavArgs
import androidx.navigation.fragment.ktx.test.R
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

@SmallTest
class ActivityTest {
    @get:Rule val activityRule = ActivityTestRule<TestActivity>(TestActivity::class.java)
    private val fragmentManager get() = activityRule.activity.supportFragmentManager
    private val contentFragment get() = fragmentManager.findFragmentById(android.R.id.content)!!

    @UiThreadTest
    @Test fun findNavController() {
        val navHostFragment = NavHostFragment.create(R.navigation.test_graph)
        fragmentManager.beginTransaction()
                .add(android.R.id.content, navHostFragment)
                .commitNow()

        val foundNavController = contentFragment.findNavController()
        assertTrue("Fragment should have NavController set",
                foundNavController == navHostFragment.navController)
    }

    @UiThreadTest
    @Test fun findNavControllerNull() {
        fragmentManager.beginTransaction()
                .add(android.R.id.content, TestFragment())
                .commitNow()
        try {
            contentFragment.findNavController()
            fail("findNavController should throw IllegalStateException if a NavController " +
                    "was not set")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @UiThreadTest
    @Test fun navArgsLazy() {
        val navHostFragment = NavHostFragment.create(R.navigation.test_graph)
        fragmentManager.beginTransaction()
            .add(android.R.id.content, navHostFragment)
            .commitNow()

        // TODO Create a real API to get the current Fragment b/119800853
        val testFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
                as TestFragment
        assertThat(testFragment.args)
            .isNotNull()
        assertThat(testFragment.args.bundle["test"])
            .isEqualTo("test")
    }
}

class TestActivity : FragmentActivity()
/**
 * It is a lot harder to test generated NavArgs classes, so
 * we'll just fake one that has the same Bundle constructor
 * that NavArgsLazy expects
 */
data class FakeTestArgs(val bundle: Bundle) : NavArgs
class TestFragment : Fragment() {
    val args: FakeTestArgs by navArgs()
}
