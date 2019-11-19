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

package androidx.ui.tooling

import android.app.Activity
import android.os.Bundle
import androidx.test.rule.ActivityTestRule
import androidx.ui.tooling.preview.ComposeViewAdapter
import androidx.ui.tooling.test.R
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposeViewAdapterTest {
    @get:Rule
    val activityTestRule = ActivityTestRule<TestActivity>(TestActivity::class.java)

    private lateinit var composeViewAdapter: ComposeViewAdapter

    @Before
    fun setup() {
        composeViewAdapter =
            activityTestRule.activity.findViewById<ComposeViewAdapter>(R.id.compose_view_adapter)
    }

    @After
    fun tearDown() {
        activityTestRule.runOnUiThread {
            composeViewAdapter.dispose()
        }
    }

    @Test
    fun instantiateComposeViewAdapter() {
        activityTestRule.runOnUiThread {
            composeViewAdapter.init(
                "androidx.ui.tooling.SimpleComposablePreviewKt",
                "SimpleComposablePreview",
                debugViewInfos = true
            )
        }

        activityTestRule.runOnUiThread {
            assertTrue(composeViewAdapter.viewInfos.isNotEmpty())
        }
    }

    @Test
    fun instantiatePrivateComposeViewAdapter() {
        activityTestRule.runOnUiThread {
            composeViewAdapter.init(
                "androidx.ui.tooling.SimpleComposablePreviewKt",
                "PrivateSimpleComposablePreview",
                debugViewInfos = true
            )
        }

        activityTestRule.runOnUiThread {
            assertTrue(composeViewAdapter.viewInfos.isNotEmpty())
        }
    }

    companion object {
        class TestActivity : Activity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.compose_adapter_test)
            }
        }
    }
}