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

package androidx.fragment.app

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.test.LoaderActivity
import androidx.fragment.test.R
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.testutils.FragmentActivityUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
@MediumTest
class LoaderTest {
    @get:Rule
    val activityRule = ActivityTestRule(LoaderActivity::class.java)

    /**
     * Test to ensure that there is no Activity leak due to Loader
     */
    @Test
    fun testLeak() {
        // Restart the activity because activityRule keeps a strong reference to the
        // old activity.
        val activity = FragmentActivityUtils.recreateActivity(activityRule, activityRule.activity)

        val fragment = LoaderFragment()
        val fm: FragmentManager = activity.supportFragmentManager

        fm.beginTransaction()
            .add(fragment, "1")
            .commit()

        FragmentTestUtil.executePendingTransactions(activityRule, fm)

        fm.beginTransaction()
            .remove(fragment)
            .addToBackStack(null)
            .commit()

        FragmentTestUtil.executePendingTransactions(activityRule, fm)

        val weakActivity = WeakReference(LoaderActivity.activity)

        // Wait for everything to settle. We have to make sure that the old Activity
        // is ready to be collected.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        FragmentTestUtil.waitForExecution(activityRule)

        // Force a garbage collection.
        FragmentTestUtil.forceGC()
        assertThat(weakActivity.get()).isNull()
    }

    /**
     * When a LoaderManager is reused, it should notify in onResume
     */
    @Test
    fun startWhenReused() {
        var activity = activityRule.activity

        assertThat(activity.textView.text).isEqualTo("Loaded!")

        activity = FragmentActivityUtils.recreateActivity(activityRule, activity)

        FragmentTestUtil.waitForExecution(activityRule)

        // After orientation change, the text should still be loaded properly
        assertThat(activity.textView.text).isEqualTo("Loaded!")
    }

    @Test
    fun testRedeliverWhenReattached() {
        val activity = activityRule.activity

        val fm = activity.supportFragmentManager

        val fragment =
            fm.findFragmentById(R.id.fragmentContainer) as LoaderActivity.TextLoaderFragment

        assertThat(fragment).isNotNull()
        assertThat(fragment.textView.text).isEqualTo("Loaded!")

        fm.beginTransaction()
            .detach(fragment)
            .commit()

        FragmentTestUtil.executePendingTransactions(activityRule, fm)

        fm.beginTransaction()
            .attach(fragment)
            .commit()

        FragmentTestUtil.executePendingTransactions(activityRule, fm)

        assertThat(fragment.textView.text).isEqualTo("Loaded!")
    }

    class LoaderFragment : Fragment(), LoaderManager.LoaderCallbacks<Boolean> {

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
        }

        override fun onCreateLoader(id: Int, args: Bundle?):
                Loader<Boolean> = SimpleLoader(requireContext())

        override fun onLoadFinished(loader: Loader<Boolean>, data: Boolean?) {}

        override fun onLoaderReset(loader: Loader<Boolean>) {}

        internal class SimpleLoader(context: Context) : Loader<Boolean>(context) {
            override fun onStartLoading() {
                deliverResult(true)
            }
        }

        companion object {
            private const val LOADER_ID = 1
        }
    }
}
