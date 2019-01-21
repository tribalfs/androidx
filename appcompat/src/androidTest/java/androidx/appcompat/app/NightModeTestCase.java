/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.appcompat.app.NightModeActivity.TOP_ACTIVITY;
import static androidx.appcompat.testutils.NightModeUtils.assertConfigurationNightModeEquals;
import static androidx.appcompat.testutils.NightModeUtils.setLocalNightModeAndWait;
import static androidx.appcompat.testutils.TestUtilsActions.rotateScreenOrientation;
import static androidx.appcompat.testutils.TestUtilsMatchers.isBackground;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.webkit.WebView;

import androidx.appcompat.test.R;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.testutils.AppCompatActivityUtils;
import androidx.testutils.RecreatedAppCompatActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class NightModeTestCase {
    @Rule
    public final ActivityTestRule<NightModeActivity> mActivityTestRule;

    private static final String STRING_DAY = "DAY";
    private static final String STRING_NIGHT = "NIGHT";

    public NightModeTestCase() {
        mActivityTestRule = new ActivityTestRule<>(NightModeActivity.class);
    }

    @Before
    public void setup() {
        // By default we'll set the night mode to NO, which allows us to make better
        // assumptions in the test below
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test
    public void testLocalDayNightModeRecreatesActivity() throws Throwable {
        // Verify first that we're in day mode
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_DAY)));

        // Now force the local night mode to be yes (aka night mode)
        setLocalNightModeAndWaitForRecreate(
                mActivityTestRule.getActivity(), AppCompatDelegate.MODE_NIGHT_YES);

        // Assert that the new local night mode is returned
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                mActivityTestRule.getActivity().getDelegate().getLocalNightMode());

        // Now check the text has changed, signifying that night resources are being used
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_NIGHT)));
    }

    @Test
    public void testSwitchingYesToFollowSystem() throws Throwable {
        // Verify first that we're in day mode
        onView(withId(R.id.text_night_mode))
                .check(matches(withText(STRING_DAY)));

        // Now force the local night mode to be yes (aka night mode)
        setLocalNightModeAndWaitForRecreate(
                mActivityTestRule.getActivity(), AppCompatDelegate.MODE_NIGHT_YES);

        // Now check the text has changed, signifying that night resources are being used
        onView(withId(R.id.text_night_mode))
                .check(matches(withText(STRING_NIGHT)));

        // Now force the local night mode to be FOLLOW_SYSTEM, which should go back to DAY
        setLocalNightModeAndWaitForRecreate(
                mActivityTestRule.getActivity(), AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // Now check the text has changed, signifying that night resources are being used
        onView(withId(R.id.text_night_mode))
                .check(matches(withText(STRING_DAY)));
    }

    @Test
    public void testColorConvertedDrawableChangesWithNightMode() throws Throwable {
        final NightModeActivity activity = mActivityTestRule.getActivity();
        final int dayColor = ContextCompat.getColor(activity, R.color.color_sky_day);
        final int nightColor = ContextCompat.getColor(activity, R.color.color_sky_night);

        // Loop through and switching from day to night and vice-versa multiple times. It needs
        // to be looped since the issue is with drawable caching, therefore we need to prime the
        // cache for the issue to happen
        for (int i = 0; i < 5; i++) {
            // First force it to not be night mode
            setLocalNightModeAndWaitForRecreate(TOP_ACTIVITY, AppCompatDelegate.MODE_NIGHT_NO);
            // ... and verify first that we're in day mode
            onView(withId(R.id.view_background)).check(matches(isBackground(dayColor)));

            // Now force the local night mode to be yes (aka night mode)
            setLocalNightModeAndWaitForRecreate(TOP_ACTIVITY, AppCompatDelegate.MODE_NIGHT_YES);
            // ... and verify first that we're in night mode
            onView(withId(R.id.view_background)).check(matches(isBackground(nightColor)));
        }
    }

    @Test
    public void testNightModeAutoTimeRecreatesOnTimeChange() throws Throwable {
        // Create a fake TwilightManager and set it as the app instance
        final FakeTwilightManager twilightManager = new FakeTwilightManager();
        TwilightManager.setInstance(twilightManager);

        final NightModeActivity activity = mActivityTestRule.getActivity();

        // Verify that we're currently in day mode
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_DAY)));

        // Set MODE_NIGHT_AUTO so that we will change to night mode automatically
        final NightModeActivity newActivity = setLocalNightModeAndWaitForRecreate(activity,
                        AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
        final AppCompatDelegateImpl newDelegate =
                (AppCompatDelegateImpl) newActivity.getDelegate();

        // Update the fake twilight manager to be in night and trigger a fake 'time' change
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                twilightManager.setIsNight(true);
                newDelegate.getAutoTimeNightModeManager().onChange();
            }
        });

        RecreatedAppCompatActivity.sResumed = new CountDownLatch(1);
        assertTrue(RecreatedAppCompatActivity.sResumed.await(1, TimeUnit.SECONDS));
        // At this point recreate that has been triggered by onChange call
        // has completed

        // Check that the text has changed, signifying that night resources are being used
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_NIGHT)));
    }

    @Test
    public void testNightModeAutoTimeRecreatesOnResume() throws Throwable {
        // Create a fake TwilightManager and set it as the app instance
        final FakeTwilightManager twilightManager = new FakeTwilightManager();
        TwilightManager.setInstance(twilightManager);

        // Set MODE_NIGHT_AUTO_TIME so that we will change to night mode automatically
        setLocalNightModeAndWaitForRecreate(mActivityTestRule.getActivity(),
                AppCompatDelegate.MODE_NIGHT_AUTO_TIME);

        // Verify that we're currently in day mode
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_DAY)));

        final CountDownLatch resumeCompleteLatch = new CountDownLatch(1);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final NightModeActivity activity = mActivityTestRule.getActivity();
                final Instrumentation instrumentation =
                        InstrumentationRegistry.getInstrumentation();
                // Now fool the Activity into thinking that it has gone into the background
                instrumentation.callActivityOnPause(activity);
                instrumentation.callActivityOnStop(activity);

                // Now update the twilight manager while the Activity is in the 'background'
                twilightManager.setIsNight(true);

                // Now tell the Activity that it has gone into the foreground again
                instrumentation.callActivityOnStart(activity);
                instrumentation.callActivityOnResume(activity);

                resumeCompleteLatch.countDown();
            }
        });

        resumeCompleteLatch.await();
        // finally check that the text has changed, signifying that night resources are being used
        onView(withId(R.id.text_night_mode)).check(matches(withText(STRING_NIGHT)));
    }

    @Test
    public void testOnNightModeChangedCalled() throws Throwable {
        // Set local night mode to YES
        setLocalNightModeAndWait(mActivityTestRule, AppCompatDelegate.MODE_NIGHT_YES);
        // Assert that the Activity received a new value
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                mActivityTestRule.getActivity().getLastNightModeAndReset());

        // Set local night mode to NO
        setLocalNightModeAndWait(mActivityTestRule, AppCompatDelegate.MODE_NIGHT_NO);
        // Assert that the Activity received a new value
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                mActivityTestRule.getActivity().getLastNightModeAndReset());
    }

    @Test
    public void testRotateRecreatesActivity() throws Throwable {
        // Set local night mode to YES
        setLocalNightModeAndWait(mActivityTestRule, AppCompatDelegate.MODE_NIGHT_YES);

        final Activity activity = mActivityTestRule.getActivity();

        // Assert that the current Activity is 'dark'
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES,
                activity.getResources().getConfiguration());

        // Now rotate the device
        onView(withId(android.R.id.content)).perform(rotateScreenOrientation(activity));

        // And assert that we have a new Activity, and thus was recreated
        assertNotSame(activity, mActivityTestRule.getActivity());
    }

    @Test
    public void testDialogDoesNotOverrideActivityConfiguration() throws Throwable {
        // Set Activity local night mode to YES
        final NightModeActivity activity = setLocalNightModeAndWaitForRecreate(
                mActivityTestRule.getActivity(), AppCompatDelegate.MODE_NIGHT_YES);

        // Assert that the uiMode is as expected
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES, activity);

        // Now show a AppCompatDialog
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AppCompatDialog dialog = new AppCompatDialog(activity);
                dialog.show();
            }
        });

        // Assert that the uiMode is unchanged
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES, activity);
    }

    @Test
    public void testLoadingWebViewMaintainsConfiguration() throws Throwable {
        // Set night mode and wait for the new Activity
        final NightModeActivity activity = setLocalNightModeAndWaitForRecreate(
                mActivityTestRule.getActivity(), AppCompatDelegate.MODE_NIGHT_YES);

        // Assert that the context still has a night themed configuration
        assertConfigurationNightModeEquals(
                Configuration.UI_MODE_NIGHT_YES,
                activity.getResources().getConfiguration());

        // Now load a WebView into the Activity
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final WebView webView = new WebView(activity);
            }
        });

        // Now assert that the context still has a night themed configuration
        assertConfigurationNightModeEquals(
                Configuration.UI_MODE_NIGHT_YES,
                activity.getResources().getConfiguration());
    }

    private static class FakeTwilightManager extends TwilightManager {
        private boolean mIsNight;

        FakeTwilightManager() {
            super(null, null);
        }

        @Override
        boolean isNight() {
            return mIsNight;
        }

        void setIsNight(boolean night) {
            mIsNight = night;
        }
    }

    private NightModeActivity setLocalNightModeAndWaitForRecreate(
            final NightModeActivity activity,
            @AppCompatDelegate.NightMode final int nightMode) throws Throwable {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getDelegate().setLocalNightMode(nightMode);
            }
        });
        final NightModeActivity result =
                AppCompatActivityUtils.recreateActivity(mActivityTestRule, activity);
        AppCompatActivityUtils.waitForExecution(mActivityTestRule);

        instrumentation.waitForIdleSync();
        return result;
    }
}
