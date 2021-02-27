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

package androidx.navigation.dynamicfeatures

import android.app.Activity
import android.content.Intent
import androidx.navigation.NavigatorProvider
import androidx.navigation.NoOpNavigator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.android.play.core.splitinstall.SplitInstallManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
/* ktlint-disable no-unused-imports */ // https://github.com/pinterest/ktlint/issues/937
import org.mockito.Mockito.`when` as mockWhen
/* ktlint-enable unused-imports */

@SmallTest
@RunWith(AndroidJUnit4::class)
class DynamicActivityNavigatorTest {

    private lateinit var navigator: DynamicActivityNavigator
    private lateinit var installManager: DynamicInstallManager
    private lateinit var splitInstallManager: SplitInstallManager
    private lateinit var provider: NavigatorProvider
    private lateinit var noOpNavigator: NoOpNavigator
    private lateinit var dynamicDestination: DynamicActivityNavigator.Destination

    @Suppress("DEPRECATION")
    @get:Rule
    val activityTestRule = androidx.test.rule.ActivityTestRule(NavigationActivity::class.java)

    @Before
    fun setup() {
        splitInstallManager = mock(SplitInstallManager::class.java)
        installManager = DynamicInstallManager(activityTestRule.activity, splitInstallManager)
        navigator = DynamicActivityNavigator(activityTestRule.activity, installManager)
        provider = NavigatorProvider()
        noOpNavigator = NoOpNavigator()
        provider.addNavigator(noOpNavigator)
        dynamicDestination = navigator.createDestination()
        dynamicDestination.setIntent(
            Intent(activityTestRule.activity, DestinationActivity::class.java)
        )
    }

    @Test
    fun navigate_DynamicActivityDestination() {
        navigator.navigate(dynamicDestination, null, null, null)
    }

    @Test(expected = IllegalStateException::class)
    fun navigate_DynamicActivityDestination_NoDynamicNavGraph() {
        @Suppress("UNUSED_VARIABLE")
        val destination = DynamicActivityNavigator.Destination(NavigatorProvider())
        val navDestination = mock(DynamicActivityNavigator.Destination::class.java).apply {
            mockWhen(moduleName).thenReturn("module")
            setIntent(Intent(activityTestRule.activity, DestinationActivity::class.java))
        }
        navigator.navigate(navDestination, null, null, null)
    }

    @Test
    fun createDestination() {
        assertNotNull(navigator.createDestination())
    }
}

class NavigationActivity : Activity()

class DestinationActivity : Activity()
