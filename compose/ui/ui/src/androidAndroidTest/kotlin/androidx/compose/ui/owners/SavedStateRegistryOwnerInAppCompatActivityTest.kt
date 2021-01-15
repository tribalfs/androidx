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

package androidx.compose.ui.owners

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.AmbientSavedStateRegistryOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.setContent
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class SavedStateRegistryOwnerInAppCompatActivityTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val activityTestRule = androidx.test.rule.ActivityTestRule(
        AppCompatActivity::class.java
    )
    private lateinit var activity: AppCompatActivity

    @Before
    fun setup() {
        activity = activityTestRule.activity
    }

    @Test
    fun ownerIsAvailable() {
        val latch = CountDownLatch(1)
        var owner: SavedStateRegistryOwner? = null

        activityTestRule.runOnUiThread {
            activity.setContent {
                owner = AmbientSavedStateRegistryOwner.current
                latch.countDown()
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(activity, owner)
    }

    @Test
    fun ownerIsAvailableWhenComposedIntoView() {
        val latch = CountDownLatch(1)
        var owner: SavedStateRegistryOwner? = null

        activityTestRule.runOnUiThread {
            val view = ComposeView(activity)
            activity.setContentView(view)
            view.setContent {
                owner = AmbientSavedStateRegistryOwner.current
                latch.countDown()
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(activity, owner)
    }
}
