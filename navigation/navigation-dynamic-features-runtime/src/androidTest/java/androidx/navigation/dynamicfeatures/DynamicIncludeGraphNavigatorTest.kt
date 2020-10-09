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

package androidx.navigation.dynamicfeatures

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NoOpNavigator
import androidx.navigation.dynamicfeatures.shared.AndroidTestDynamicInstallManager
import androidx.navigation.dynamicfeatures.test.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`

@MediumTest
@RunWith(AndroidJUnit4::class)
class DynamicIncludeGraphNavigatorTest {

    private lateinit var navigator: DynamicIncludeGraphNavigator

    @Suppress("DEPRECATION")
    @get:Rule
    val rule = androidx.test.rule.ActivityTestRule(NavigationActivity::class.java)

    @Before
    fun setup() {
        setupInternal()
    }

    private fun setupInternal(navGraphId: Int = R.navigation.nav_graph) {
        val context = rule.activity
        val navController = NavController(context)
        val navigatorProvider = navController.navigatorProvider
        val installManager = AndroidTestDynamicInstallManager(context).also {
            `when`(it.splitInstallManager.installedModules)
                .thenReturn(setOf("test"))
        }
        navigator = DynamicIncludeGraphNavigator(
            context,
            navigatorProvider,
            navController.navInflater,
            installManager
        )
        with(navController) {
            navigatorProvider.addNavigator(
                DynamicGraphNavigator(navigatorProvider, installManager)
            )
            navigatorProvider.addNavigator(navigator)
            navigatorProvider.addNavigator(NoOpNavigator())
            setGraph(navGraphId)
        }
    }

    @Test
    fun createDestination() {
        assertNotNull(navigator.createDestination())
    }

    @Test
    fun testReplacePackagePlaceholder() {
        val context = rule.activity
        val packageName = context.packageName
        val dynamicNavGraph = navigator.createDestination().apply {
            moduleName = FEATURE_NAME
        }
        assertThat(
            dynamicNavGraph.getPackageOrDefault(
                context,
                "\${applicationId}.something" +
                    ".$FEATURE_NAME"
            )
        ).isEqualTo("$packageName.something.$FEATURE_NAME")

        assertThat(
            dynamicNavGraph.getPackageOrDefault(
                context,
                "something.\${applicationId}" +
                    ".$FEATURE_NAME"
            )
        ).isEqualTo("something.$packageName.$FEATURE_NAME")

        assertThat(
            dynamicNavGraph.getPackageOrDefault(context, null)
        ).isEqualTo("$packageName.$FEATURE_NAME")
    }

    @Test
    fun invalidGraphId() {
        try {
            setupInternal(R.navigation.nav_invalid_id)
            fail("Inflating nav_invalid_id should fail with an IllegalStateException")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().containsMatch(
                ".*" +
                    "androidx.navigation.dynamicfeatures.test:id/featureFragmentNested" +
                    ".*" +
                    "androidx.navigation.dynamicfeatures.test:id/dynamic_graph"
            )
        }
    }

    @Test
    fun onSaveState() {
        assertThat(navigator.onSaveState()).isEqualTo(Bundle.EMPTY)
    }

    @Test
    fun onRestoreState() {
        navigator.onRestoreState(Bundle.EMPTY)
    }

    @Test
    fun onRestoreState_nestedInclusion() {
        setupInternal(R.navigation.nav_graph_nested_include_dynamic)
        navigator.onRestoreState(Bundle.EMPTY)
    }

    @Test
    fun popBackStack() {
        assertThat(navigator.popBackStack()).isTrue()
    }
}

private const val FEATURE_NAME = "myfeature"
