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

package androidx.appcompat.app;

import static androidx.appcompat.testutils.NightModeUtils.assertConfigurationNightModeEquals;
import static androidx.appcompat.testutils.NightModeUtils.setLocalNightModeAndWait;
import static androidx.appcompat.testutils.TestUtilsActions.rotateScreenOrientation;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NightModeOrientationConfigChangesTestCase {
    @Rule
    public final ActivityTestRule<NightModeOrientationConfigChangesActivity> mActivityTestRule;

    public NightModeOrientationConfigChangesTestCase() {
        mActivityTestRule = new ActivityTestRule<>(NightModeOrientationConfigChangesActivity.class);
    }

    @Before
    public void setup() {
        // By default we'll set the night mode to NO, which allows us to make better
        // assumptions in the test below
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test
    public void testRotateDoesNotRecreateActivity() throws Throwable {
        // Set local night mode to YES
        setLocalNightModeAndWait(mActivityTestRule, AppCompatDelegate.MODE_NIGHT_YES);

        final Activity activity = mActivityTestRule.getActivity();

        // Assert that the current Activity is 'dark'
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES,
                activity.getResources().getConfiguration());

        // Now rotate the device
        onView(withId(android.R.id.content)).perform(rotateScreenOrientation(activity));

        // And assert that we have the same Activity, and thus was not recreated
        assertSame(activity, mActivityTestRule.getActivity());
    }
}
