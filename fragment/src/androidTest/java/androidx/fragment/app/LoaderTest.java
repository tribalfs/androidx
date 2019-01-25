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

package androidx.fragment.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.test.LoaderActivity;
import androidx.fragment.test.R;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.testutils.FragmentActivityUtils;
import androidx.testutils.RecreatedActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.WeakReference;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class LoaderTest {
    @Rule
    public ActivityTestRule<LoaderActivity> mActivityRule =
            new ActivityTestRule<>(LoaderActivity.class);

    /**
     * Test to ensure that there is no Activity leak due to Loader
     */
    @Test
    public void testLeak() throws Throwable {
        // Restart the activity because mActivityRule keeps a strong reference to the
        // old activity.
        LoaderActivity activity = FragmentActivityUtils.recreateActivity(mActivityRule,
                mActivityRule.getActivity());

        LoaderFragment fragment = new LoaderFragment();
        FragmentManager fm = activity.getSupportFragmentManager();

        fm.beginTransaction()
                .add(fragment, "1")
                .commit();

        FragmentTestUtil.executePendingTransactions(mActivityRule, fm);

        fm.beginTransaction()
                .remove(fragment)
                .addToBackStack(null)
                .commit();

        FragmentTestUtil.executePendingTransactions(mActivityRule, fm);
        fm = null; // clear it so that it can be released

        WeakReference<RecreatedActivity> weakActivity =
                new WeakReference<>(LoaderActivity.sActivity);

        activity = FragmentActivityUtils.recreateActivity(mActivityRule, activity);

        // Wait for everything to settle. We have to make sure that the old Activity
        // is ready to be collected.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        FragmentTestUtil.waitForExecution(mActivityRule);

        // Force a garbage collection.
        FragmentTestUtil.forceGC();
        assertNull(weakActivity.get());
    }

    /**
     * When a LoaderManager is reused, it should notify in onResume
     */
    @Test
    public void startWhenReused() throws Throwable {
        LoaderActivity activity = mActivityRule.getActivity();

        assertEquals("Loaded!", activity.textView.getText().toString());

        activity = FragmentActivityUtils.recreateActivity(mActivityRule, activity);

        FragmentTestUtil.waitForExecution(mActivityRule);

        // After orientation change, the text should still be loaded properly
        assertEquals("Loaded!", activity.textView.getText().toString());
    }

    @Test
    public void testRedeliverWhenReattached() throws Throwable {
        LoaderActivity activity = mActivityRule.getActivity();

        FragmentManager fm = activity.getSupportFragmentManager();

        LoaderActivity.TextLoaderFragment fragment =
                (LoaderActivity.TextLoaderFragment) fm.findFragmentById(R.id.fragmentContainer);

        assertNotNull(fragment);
        assertEquals("Loaded!", fragment.textView.getText().toString());

        fm.beginTransaction()
                .detach(fragment)
                .commit();

        FragmentTestUtil.executePendingTransactions(mActivityRule, fm);

        fm.beginTransaction()
                .attach(fragment)
                .commit();

        FragmentTestUtil.executePendingTransactions(mActivityRule, fm);

        assertEquals("Loaded!", fragment.textView.getText().toString());
    }

    public static class LoaderFragment extends Fragment implements
            LoaderManager.LoaderCallbacks<Boolean> {
        private static final int LOADER_ID = 1;

        @Override
        public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
        }

        @NonNull
        @Override
        public Loader<Boolean> onCreateLoader(int id, @Nullable Bundle args) {
            return new SimpleLoader(requireContext());
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Boolean> loader, Boolean data) {
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Boolean> loader) {
        }

        static class SimpleLoader extends Loader<Boolean> {

            SimpleLoader(@NonNull Context context) {
                super(context);
            }

            @Override
            protected void onStartLoading() {
                deliverResult(true);
            }
        }
    }
}
