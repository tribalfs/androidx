/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.fragment.app.strictmode

import androidx.fragment.app.StrictFragment
import androidx.fragment.app.executePendingTransactions
import androidx.fragment.app.test.FragmentTestActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.testutils.withActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
public class FragmentStrictModeTest {
    private lateinit var originalPolicy: FragmentStrictMode.Policy

    @Before
    public fun setup() {
        originalPolicy = FragmentStrictMode.getDefaultPolicy()
    }

    @After
    public fun teardown() {
        FragmentStrictMode.setDefaultPolicy(originalPolicy)
    }

    @Test
    public fun penaltyDeath() {
        val policy = FragmentStrictMode.Policy.Builder()
            .penaltyDeath()
            .build()
        FragmentStrictMode.setDefaultPolicy(policy)

        var violation: Violation? = null
        try {
            FragmentStrictMode.onPolicyViolation(StrictFragment(), object : Violation() {})
        } catch (thrown: Violation) {
            violation = thrown
        }
        assertWithMessage("No exception thrown on policy violation").that(violation).isNotNull()
    }

    @Test
    public fun policyHierarchy() {
        var lastTriggeredPolicy = ""
        val violation = object : Violation() {}

        fun policy(name: String) = FragmentStrictMode.Policy.Builder()
            .penaltyListener { lastTriggeredPolicy = name }
            .build()

        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fragmentManager = withActivity { supportFragmentManager }

            val parentFragment = StrictFragment()
            fragmentManager.beginTransaction()
                .add(parentFragment, "parentFragment")
                .commit()
            executePendingTransactions()

            val childFragment = StrictFragment()
            parentFragment.childFragmentManager.beginTransaction()
                .add(childFragment, "childFragment")
                .commit()
            executePendingTransactions()

            FragmentStrictMode.setDefaultPolicy(policy("Default policy"))
            FragmentStrictMode.onPolicyViolation(childFragment, violation)
            assertThat(lastTriggeredPolicy).isEqualTo("Default policy")

            fragmentManager.strictModePolicy = policy("Parent policy")
            FragmentStrictMode.onPolicyViolation(childFragment, violation)
            assertThat(lastTriggeredPolicy).isEqualTo("Parent policy")

            parentFragment.childFragmentManager.strictModePolicy = policy("Child policy")
            FragmentStrictMode.onPolicyViolation(childFragment, violation)
            assertThat(lastTriggeredPolicy).isEqualTo("Child policy")
        }
    }
}
