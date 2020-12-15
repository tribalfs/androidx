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

package androidx.navigation.dynamicfeatures.fragment

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.navigation.dynamicfeatures.fragment.test.R
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.testutils.withActivity
import com.google.android.play.core.splitinstall.SplitInstallManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
public class DynamicNavHostFragmentTest {

    @Suppress("DEPRECATION")
    @get:Rule
    public val activityTestRule: ActivityScenarioRule<NavigationActivity> = ActivityScenarioRule(
        NavigationActivity::class.java
    )

    @Test
    public fun createSplitInstallManager() {
        val fragment = TestDynamicNavHostFragment()
        with(ActivityScenario.launch(NavigationActivity::class.java)) {
            withActivity {
                supportFragmentManager.beginTransaction()
                    .add(R.id.nav_host, fragment, null)
                    .setPrimaryNavigationFragment(fragment)
                    .commitNow()
            }
        }
        assertEquals(fragment.createSplitInstallManager(), fragment.createSplitInstallManager())
    }

    @Test
    public fun create_noArgs() {
        val fragment = DynamicNavHostFragment.create(R.id.nav_host)
        assertEquals(fragment.arguments!!.size(), 1)
    }

    @Test
    public fun create_withArgs() {
        val fragment = DynamicNavHostFragment.create(
            R.id.nav_host,
            Bundle().apply {
                putInt("Test", 1)
            }
        )
        assertEquals(fragment.arguments!!.size(), 2)
    }
}

public class NavigationActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.dynamic_activity_layout)
        super.onCreate(savedInstanceState)
    }
}

public class TestDynamicNavHostFragment : DynamicNavHostFragment() {
    public override fun createSplitInstallManager(): SplitInstallManager =
        super.createSplitInstallManager()
}
