/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.fragment.app

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.fragment.app.test.FragmentTestActivity
import androidx.fragment.test.R
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.testutils.withActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ProviderCallbackTest {

    @Test
    fun onConfigurationChanged() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity { supportFragmentManager }
            val fragment = CallbackFragment()

            withActivity {
                fm.beginTransaction()
                    .replace(R.id.content, fragment)
                    .commitNow()
            }

            withActivity {
                val newConfig = Configuration(resources.configuration)
                onConfigurationChanged(newConfig)
            }
            assertThat(fragment.configChangedCount).isEqualTo(1)
        }
    }

    @Test
    fun onConfigurationChangedNestedFragments() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity { supportFragmentManager }
            val parent = StrictViewFragment(R.layout.fragment_container_view)
            val child = CallbackFragment()

            withActivity {
                fm.beginTransaction()
                    .replace(R.id.content, parent)
                    .commitNow()

                parent.childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, child)
                    .commitNow()
            }

            withActivity {
                val newConfig = Configuration(resources.configuration)
                onConfigurationChanged(newConfig)
            }
            assertThat(child.configChangedCount).isEqualTo(1)
        }
    }

    @Test
    fun onConfigurationChangedNestedFragmentsOnBackStack() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val parent = StrictViewFragment(R.layout.fragment_container_view)
            val child = CallbackFragment()
            val replacementChild = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, parent)
                    .setReorderingAllowed(true)
                    .commitNow()

                parent.childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, child)
                    .setReorderingAllowed(true)
                    .commitNow()

                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, replacementChild)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit()
                supportFragmentManager.executePendingTransactions()

                val newConfig = Configuration(resources.configuration)
                onConfigurationChanged(newConfig)
            }

            assertThat(child.configChangedCount).isEqualTo(0)
            assertThat(replacementChild.configChangedCount).isEqualTo(1)
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun onMultiWindowModeChanged() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fragment = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .commitNow()

                val newConfig = Configuration(resources.configuration)
                onMultiWindowModeChanged(true, newConfig)
            }
            assertThat(fragment.multiWindowChangedCount).isEqualTo(1)
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun onMultiWindowModeChangedNestedFragments() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val parent = StrictViewFragment(R.layout.fragment_container_view)
            val child = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, parent)
                    .commitNow()

                parent.childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, child)
                    .commitNow()

                val newConfig = Configuration(resources.configuration)
                onMultiWindowModeChanged(true, newConfig)
            }

            assertThat(child.multiWindowChangedCount).isEqualTo(1)
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Suppress("DEPRECATION")
    @Test
    fun onPictureInPictureModeChanged() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fragment = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .commitNow()

                val newConfig = Configuration(resources.configuration)
                onPictureInPictureModeChanged(true, newConfig)
            }
            assertThat(fragment.pictureModeChangedCount).isEqualTo(1)
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun onPictureInPictureModeChangedNestedFragments() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val parent = StrictViewFragment(R.layout.fragment_container_view)
            val child = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, parent)
                    .commitNow()

                parent.childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, child)
                    .commitNow()

                val newConfig = Configuration(resources.configuration)
                onPictureInPictureModeChanged(true, newConfig)
            }

            assertThat(child.pictureModeChangedCount).isEqualTo(1)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun onLowMemory() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fragment = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, fragment)
                    .commitNow()

                onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
            assertThat(fragment.onLowMemoryCount).isEqualTo(1)
        }
    }

    @Test
    fun onLowMemoryNestedFragments() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val parent = StrictViewFragment(R.layout.fragment_container_view)
            val child = CallbackFragment()

            withActivity {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content, parent)
                    .commitNow()

                parent.childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, child)
                    .commitNow()

                onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }

            assertThat(child.onLowMemoryCount).isEqualTo(1)
        }
    }
}

class CallbackFragment : StrictViewFragment() {
    var configChangedCount = 0
    var multiWindowChangedCount = 0
    var pictureModeChangedCount = 0
    var onLowMemoryCount = 0

    override fun onConfigurationChanged(newConfig: Configuration) {
        configChangedCount++
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        multiWindowChangedCount++
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        pictureModeChangedCount++
    }

    override fun onLowMemory() {
        onLowMemoryCount++
    }
}
