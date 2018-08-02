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

package androidx.navigation.fragment;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.arch.lifecycle.Lifecycle;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import androidx.navigation.NavOptions;
import androidx.navigation.Navigator;
import androidx.navigation.fragment.test.EmptyActivity;
import androidx.navigation.fragment.test.EmptyFragment;
import androidx.navigation.fragment.test.R;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class FragmentNavigatorTest {

    private static final int INITIAL_FRAGMENT = 1;
    private static final int SECOND_FRAGMENT = 2;
    private static final int THIRD_FRAGMENT = 3;
    private static final int FOURTH_FRAGMENT = 4;

    @Rule
    public ActivityTestRule<EmptyActivity> mActivityRule =
            new ActivityTestRule<>(EmptyActivity.class);

    private EmptyActivity mEmptyActivity;
    private FragmentManager mFragmentManager;

    @Before
    public void setup() {
        mEmptyActivity = mActivityRule.getActivity();
        mFragmentManager = mEmptyActivity.getSupportFragmentManager();
    }

    @UiThreadTest
    @Test
    public void testNavigate() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));
        assertThat("Fragment should be the correct type", fragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testNavigateTwice() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));
        assertThat("Fragment should be the correct type", fragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));

        // Now push a second fragment
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testNavigateWithPopUpToThenPop() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // Push initial fragment
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Push a second fragment
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Pop and then push third fragment, simulating popUpTo to initial.
        fragmentNavigator.popBackStack();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null,
                new NavOptions.Builder().setPopUpTo(INITIAL_FRAGMENT, false).build());
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                THIRD_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Now pop the Fragment
        boolean popped = fragmentNavigator.popBackStack();
        assertThat("FragmentNavigator should return true when popping the third fragment", popped,
                is(true));
        // 2nd time we pop to initial fragment
        verify(listener, times(2)).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);

        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testNavigateWithPopUpToThenPopWithFragmentManager() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // Push initial fragment
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Push a second fragment
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Pop and then push third fragment, simulating popUpTo to initial.
        fragmentNavigator.popBackStack();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null,
                new NavOptions.Builder().setPopUpTo(INITIAL_FRAGMENT, false).build());
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                THIRD_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Now pop the Fragment
        boolean popped = mFragmentManager.popBackStackImmediate();
        assertThat("FragmentNavigator should return true when popping the third fragment", popped,
                is(true));
        // 2nd time we pop to initial fragment
        verify(listener, times(2)).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);

        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testSingleTopInitial() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setFragmentClass(EmptyFragment.class);

        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));

        fragmentNavigator.navigate(destination, null,
                new NavOptions.Builder().setLaunchSingleTop(true).build());
        mFragmentManager.executePendingTransactions();
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));
        assertThat("Replacement should be a new instance", replacementFragment,
                is(not(equalTo(fragment))));
        assertThat("Old instance should be destroyed", fragment.getLifecycle().getCurrentState(),
                is(equalTo(Lifecycle.State.DESTROYED)));
    }

    @UiThreadTest
    @Test
    public void testSingleTop() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setFragmentClass(EmptyFragment.class);

        // First push an initial Fragment
        fragmentNavigator.navigate(destination, null, null);

        // Now push the Fragment that we want to replace with a singleTop operation
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));

        fragmentNavigator.navigate(destination, null,
                new NavOptions.Builder().setLaunchSingleTop(true).build());
        mFragmentManager.executePendingTransactions();
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));
        assertThat("Replacement should be a new instance", replacementFragment,
                is(not(equalTo(fragment))));
        assertThat("Old instance should be destroyed", fragment.getLifecycle().getCurrentState(),
                is(equalTo(Lifecycle.State.DESTROYED)));
    }

    @UiThreadTest
    @Test
    public void testPopInitial() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // First push an initial Fragment
        fragmentNavigator.navigate(destination, null, null);
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Now pop the initial Fragment
        boolean popped = fragmentNavigator.popBackStack();
        assertThat("FragmentNavigator should return false when popping the initial Fragment",
                popped, is(false));
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                0,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testPop() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // First push an initial Fragment
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));

        // Now push the Fragment that we want to pop
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Now pop the Fragment
        boolean popped = fragmentNavigator.popBackStack();
        mFragmentManager.executePendingTransactions();
        assertThat("FragmentNavigator should return true when popping a Fragment",
                popped, is(true));
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        assertThat("Fragment should be the primary navigation Fragment after pop",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testPopWithFragmentManager() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // First push an initial Fragment
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));

        // Now push the Fragment that we want to pop
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Now pop the Fragment
        mFragmentManager.popBackStackImmediate();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        assertThat("Fragment should be the primary navigation Fragment after pop",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testDeepLinkPopWithFragmentManager() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // First push two Fragments as our 'deep link'
        fragmentNavigator.navigate(destination, null, null);
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Now push the Fragment that we want to pop
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                THIRD_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Now pop the Fragment
        mFragmentManager.popBackStackImmediate();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be the primary navigation Fragment after pop",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testDeepLinkPopWithFragmentManagerWithSaveState() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        // First push two Fragments as our 'deep link'
        fragmentNavigator.navigate(destination, null, null);
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);

        // Now push the Fragment that we want to pop
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                THIRD_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Create a new FragmentNavigator, replacing the previous one
        Bundle savedState = fragmentNavigator.onSaveState();
        fragmentNavigator.removeOnNavigatorNavigatedListener(listener);
        fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        fragmentNavigator.onRestoreState(savedState);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);

        // Now pop the Fragment
        mFragmentManager.popBackStackImmediate();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be the primary navigation Fragment after pop",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testNavigateThenPopAfterSaveState() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setId(INITIAL_FRAGMENT);
        destination.setFragmentClass(EmptyFragment.class);

        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be added", fragment, is(notNullValue()));
        assertThat("Fragment should be the correct type", fragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));

        // Now push a second fragment
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        Fragment replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Create a new FragmentNavigator, replacing the previous one
        Bundle savedState = fragmentNavigator.onSaveState();
        fragmentNavigator.removeOnNavigatorNavigatedListener(listener);
        fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        fragmentNavigator.onRestoreState(savedState);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);

        // Now push a third fragment after the state save
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                THIRD_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_ADDED);
        replacementFragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Replacement Fragment should be added", replacementFragment,
                is(notNullValue()));
        assertThat("Replacement Fragment should be the correct type", replacementFragment,
                is(CoreMatchers.<Fragment>instanceOf(EmptyFragment.class)));
        assertThat("Replacement Fragment should be the primary navigation Fragment",
                mFragmentManager.getPrimaryNavigationFragment(), is(replacementFragment));

        // Now pop the Fragment
        mFragmentManager.popBackStackImmediate();
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
        fragment = mFragmentManager.findFragmentById(R.id.container);
        assertThat("Fragment should be the primary navigation Fragment after pop",
                mFragmentManager.getPrimaryNavigationFragment(), is(fragment));

        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testMultipleNavigateFragmentTransactionsThenPopWithFragmentManager() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setFragmentClass(EmptyFragment.class);

        // Push 4 fragments without executing pending transactions.
        destination.setId(INITIAL_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);

        // Now pop the Fragment
        boolean popped = mFragmentManager.popBackStackImmediate();
        assertThat("FragmentNavigator should return true when popping the third fragment", popped,
                is(true));
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                SECOND_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
    }

    @UiThreadTest
    @Test
    public void testMultiplePopFragmentTransactionsThenPopWithFragmentManager() {
        FragmentNavigator fragmentNavigator = new FragmentNavigator(mEmptyActivity,
                mFragmentManager, R.id.container);
        Navigator.OnNavigatorNavigatedListener listener =
                mock(Navigator.OnNavigatorNavigatedListener.class);
        fragmentNavigator.addOnNavigatorNavigatedListener(listener);
        FragmentNavigator.Destination destination = fragmentNavigator.createDestination();
        destination.setFragmentClass(EmptyFragment.class);

        // Push 4 fragments
        destination.setId(INITIAL_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        destination.setId(SECOND_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        destination.setId(THIRD_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        destination.setId(FOURTH_FRAGMENT);
        fragmentNavigator.navigate(destination, null, null);
        mFragmentManager.executePendingTransactions();

        // Pop 2 fragments without executing pending transactions.
        fragmentNavigator.popBackStack();
        fragmentNavigator.popBackStack();

        boolean popped = mFragmentManager.popBackStackImmediate();
        assertThat("FragmentNavigator should return true when popping the third fragment", popped,
                is(true));
        verify(listener).onNavigatorNavigated(
                fragmentNavigator,
                INITIAL_FRAGMENT,
                Navigator.BACK_STACK_DESTINATION_POPPED);
    }
}
