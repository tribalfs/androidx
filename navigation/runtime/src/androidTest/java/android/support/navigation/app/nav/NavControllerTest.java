/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.navigation.app.nav;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.navigation.app.nav.activity.NavigationActivity;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;

import com.android.support.navigation.test.R;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class NavControllerTest {
    private static final String TEST_ARG = "test";
    private static final String TEST_ARG_VALUE = "value";

    @Rule
    public ActivityTestRule<NavigationActivity> mActivityRule =
            new ActivityTestRule<>(NavigationActivity.class, false, false);

    private Instrumentation mInstrumentation;

    @Before
    public void getInstrumentation() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testStartDestination() {
        Context context = InstrumentationRegistry.getTargetContext();
        NavController navController = new NavController(context);
        TestNavigator navigator = new TestNavigator();
        navController.addNavigator(navigator);
        navController.setGraph(R.xml.nav_start_destination);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
    }

    @Test
    public void testNestedStartDestination() {
        Context context = InstrumentationRegistry.getTargetContext();
        NavController navController = new NavController(context);
        TestNavigator navigator = new TestNavigator();
        navController.addNavigator(navigator);
        navController.setGraph(R.xml.nav_nested_start_destination);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.nested_test));
    }

    @Test
    public void testSetGraph() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        assertThat(navController.getGraph(), is(nullValue(NavGraph.class)));

        navController.setGraph(R.xml.nav_start_destination);
        assertThat(navController.getGraph(), is(notNullValue(NavGraph.class)));
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
    }

    @Test
    public void testNavigateTo() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_simple);
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        assertThat(navigator.mBackStack.size(), is(1));

        navController.navigateTo(R.id.second_test);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.second_test));
        assertThat(navigator.mBackStack.size(), is(2));
    }

    @Test
    public void testNavigateToThenPop() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_simple);
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        assertThat(navigator.mBackStack.size(), is(1));

        navController.navigateTo(R.id.second_test);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.second_test));
        assertThat(navigator.mBackStack.size(), is(2));

        navController.popBackStack();
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        assertThat(navigator.mBackStack.size(), is(1));
    }

    @Test
    public void testNavigateToThenNavigateUp() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_simple);
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        assertThat(navigator.mBackStack.size(), is(1));

        navController.navigateTo(R.id.second_test);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.second_test));
        assertThat(navigator.mBackStack.size(), is(2));

        // This should function identically to popBackStack()
        navController.navigateUp();
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        assertThat(navigator.mBackStack.size(), is(1));
    }

    @Test
    public void testNavigate() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_simple);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.start_test));
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navigator.mBackStack.size(), is(1));

        navController.navigate(R.id.second);
        assertThat(navController.getCurrentDestination().getId(), is(R.id.second_test));
        assertThat(navigator.mBackStack.size(), is(2));
    }

    @Test
    public void testDeeplink() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_deep_link);

        Intent intent = navController.createDeepLinkIntent(R.id.deep_link_test, null);
        assertThat(intent, is(notNullValue(Intent.class)));

        // Now launch the deeplink Intent
        NavigationActivity deeplinkActivity = launchActivity(intent);
        navController = deeplinkActivity.getNavController();
        navController.setGraph(R.xml.nav_deep_link);

        assertThat(navController.getCurrentDestination().getId(), is(R.id.deep_link_test));
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navigator.mBackStack.size(), is(1));
    }

    @Test
    public void testDeeplinkWithArgs() throws Throwable {
        NavigationActivity activity = launchActivity();
        NavController navController = activity.getNavController();
        navController.setGraph(R.xml.nav_deep_link);

        Bundle args = new Bundle();
        args.putString(TEST_ARG, TEST_ARG_VALUE);
        Intent intent = navController.createDeepLinkIntent(R.id.deep_link_test, args);
        assertThat(intent, is(notNullValue(Intent.class)));

        // Now launch the deeplink Intent
        NavigationActivity deeplinkActivity = launchActivity(intent);
        navController = deeplinkActivity.getNavController();
        navController.setGraph(R.xml.nav_deep_link);

        assertThat(navController.getCurrentDestination().getId(), is(R.id.deep_link_test));
        TestNavigator navigator = (TestNavigator) navController.getNavigator(TestNavigator.class);
        assertThat(navigator.mBackStack.size(), is(1));
        assertThat(navigator.mBackStack.peekLast().second.getString(TEST_ARG), is(TEST_ARG_VALUE));
    }

    private NavigationActivity launchActivity() throws Throwable {
        return launchActivity(new Intent(mInstrumentation.getTargetContext(),
                NavigationActivity.class));
    }

    private NavigationActivity launchActivity(Intent intent) throws Throwable {
        NavigationActivity activity = mActivityRule.launchActivity(intent);
        mInstrumentation.waitForIdleSync();
        NavController navController = activity.getNavController();
        assertThat(navController, is(notNullValue(NavController.class)));
        TestNavigator navigator = new TestNavigator();
        navController.addNavigator(navigator);
        return activity;
    }
}
