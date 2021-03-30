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

package androidx.fragment.app

import android.os.Bundle
import androidx.fragment.app.test.FragmentTestActivity
import androidx.fragment.test.R
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.testutils.withActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SaveRestoreBackStackTest {

    @Test
    fun saveBackStack() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StrictFragment()

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            fm.saveBackStack("replacement")
            executePendingTransactions()

            assertWithMessage("Saved Fragments should have their state saved")
                .that(fragmentReplacement.calledOnSaveInstanceState)
                .isTrue()

            // Saved Fragments should be destroyed
            assertWithMessage("Saved Fragments should be destroyed")
                .that(fragmentReplacement.calledOnDestroy)
                .isTrue()
        }
    }

    @Test
    fun savePreviouslyReferencedFragment() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StrictFragment()

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .remove(fragmentBase)
                .add(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            try {
                withActivity {
                    fm.saveBackStack("replacement")
                    fm.executePendingTransactions()
                }
                fail("executePendingTransactions() should fail with an IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertThat(e)
                    .hasMessageThat()
                    .startsWith(
                        "saveBackStack(\"replacement\") must be self contained and not " +
                            "reference fragments from non-saved FragmentTransactions."
                    )
            }
        }
    }

    @Test
    fun saveNonReorderingAllowedTransaction() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StrictFragment()

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            try {
                withActivity {
                    fm.saveBackStack("replacement")
                    fm.executePendingTransactions()
                }
                fail("executePendingTransactions() should fail with an IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertThat(e)
                    .hasMessageThat()
                    .startsWith(
                        "saveBackStack(\"replacement\") included FragmentTransactions must use " +
                            "setReorderingAllowed(true) to ensure that the back stack can be " +
                            "restored as an atomic operation."
                    )
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun saveRetainedFragment() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StrictFragment()
            fragmentReplacement.retainInstance = true

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            try {
                withActivity {
                    fm.saveBackStack("replacement")
                    fm.executePendingTransactions()
                }
                fail("executePendingTransactions() should fail with an IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertThat(e)
                    .hasMessageThat()
                    .startsWith(
                        "saveBackStack(\"replacement\") must not contain retained fragments. " +
                            "Found direct reference to retained fragment "
                    )
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun saveRetainedChildFragment() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StrictFragment()

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            fragmentReplacement.childFragmentManager.beginTransaction()
                .add(StrictFragment().apply { retainInstance = true }, "retained")
                .commit()
            executePendingTransactions(fragmentReplacement.childFragmentManager)

            try {
                withActivity {
                    fm.saveBackStack("replacement")
                    fm.executePendingTransactions()
                }
                fail("executePendingTransactions() should fail with an IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertThat(e)
                    .hasMessageThat()
                    .startsWith(
                        "saveBackStack(\"replacement\") must not contain retained fragments. " +
                            "Found retained child fragment "
                    )
            }
        }
    }

    @Test
    fun restoreBackStack() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            val fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StateSaveFragment("saved", "unsaved")

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            fm.saveBackStack("replacement")
            executePendingTransactions()

            assertWithMessage("Saved Fragments should have their state saved")
                .that(fragmentReplacement.calledOnSaveInstanceState)
                .isTrue()

            // Saved Fragments should be destroyed
            assertWithMessage("Saved Fragments should be destroyed")
                .that(fragmentReplacement.calledOnDestroy)
                .isTrue()

            fm.restoreBackStack("replacement")
            executePendingTransactions()

            val newFragmentReplacement = fm.findFragmentById(R.id.content)

            assertThat(newFragmentReplacement).isInstanceOf(StateSaveFragment::class.java)

            val stateSavedReplacement = newFragmentReplacement as StateSaveFragment

            // Assert that restored fragment has its saved state restored
            assertThat(stateSavedReplacement.savedState).isEqualTo("saved")
            assertThat(stateSavedReplacement.unsavedState).isNull()
        }
    }

    @Test
    fun restoreBackStackAfterRecreate() {
        with(ActivityScenario.launch(FragmentTestActivity::class.java)) {
            var fm = withActivity {
                supportFragmentManager
            }
            val fragmentBase = StrictFragment()
            val fragmentReplacement = StateSaveFragment("saved", "unsaved")

            fm.beginTransaction()
                .add(R.id.content, fragmentBase)
                .commit()
            executePendingTransactions()

            fm.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragmentReplacement)
                .addToBackStack("replacement")
                .commit()
            executePendingTransactions()

            fm.saveBackStack("replacement")
            executePendingTransactions()

            assertWithMessage("Saved Fragments should have their state saved")
                .that(fragmentReplacement.calledOnSaveInstanceState)
                .isTrue()

            // Saved Fragments should be destroyed
            assertWithMessage("Saved Fragments should be destroyed")
                .that(fragmentReplacement.calledOnDestroy)
                .isTrue()

            // Now recreate the whole activity while the state of the back stack is saved
            recreate()

            fm = withActivity {
                supportFragmentManager
            }

            fm.restoreBackStack("replacement")
            executePendingTransactions()

            val newFragmentReplacement = fm.findFragmentById(R.id.content)

            assertThat(newFragmentReplacement).isInstanceOf(StateSaveFragment::class.java)

            val stateSavedReplacement = newFragmentReplacement as StateSaveFragment

            // Assert that restored fragment has its saved state restored
            assertThat(stateSavedReplacement.savedState).isEqualTo("saved")
            assertThat(stateSavedReplacement.unsavedState).isNull()
        }
    }

    public class StateSaveFragment(
        public var savedState: String? = null,
        public val unsavedState: String? = null
    ) : StrictFragment() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (savedInstanceState != null) {
                savedState = savedInstanceState.getString(STATE_KEY)
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putString(STATE_KEY, savedState)
        }

        private companion object {
            private const val STATE_KEY = "state"
        }
    }
}
