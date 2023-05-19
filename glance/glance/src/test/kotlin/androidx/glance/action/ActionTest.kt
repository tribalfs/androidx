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

package androidx.glance.action

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceModifier
import androidx.glance.findModifier
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActionTest {

    private lateinit var fakeCoroutineScope: TestScope
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        fakeCoroutineScope = TestScope()
    }

    @OptIn(ExperimentalGlanceApi::class)
    @Test
    fun testStartActivity() {
        val modifiers = GlanceModifier.clickable(
            actionStartActivity(TestActivity::class.java, activityOptions = Bundle.EMPTY)
        )
        val modifier = checkNotNull(modifiers.findModifier<ActionModifier>())
        assertIs<StartActivityClassAction>(modifier.action)
        assertThat((modifier.action as StartActivityClassAction).activityOptions).isNotNull()
    }

    @Test
    fun testLaunchFromComponent() = fakeCoroutineScope.runTest {
        val c = ComponentName("androidx.glance.action", "androidx.glance.action.TestActivity")

        val modifiers = GlanceModifier.clickable(actionStartActivity(c))
        val modifier = checkNotNull(modifiers.findModifier<ActionModifier>())
        val action = assertIs<StartActivityComponentAction>(modifier.action)
        val component = assertNotNull(action.componentName)

        assertThat(component).isEqualTo(c)
    }

    @Test
    fun testLaunchFromComponentWithContext() = fakeCoroutineScope.runTest {
        val c = ComponentName(context, "androidx.glance.action.TestActivity")

        val modifiers = GlanceModifier.clickable(actionStartActivity(c))
        val modifier = checkNotNull(modifiers.findModifier<ActionModifier>())
        val action = assertIs<StartActivityComponentAction>(modifier.action)
        val component = assertNotNull(action.componentName)

        assertThat(component).isEqualTo(c)
    }
}
