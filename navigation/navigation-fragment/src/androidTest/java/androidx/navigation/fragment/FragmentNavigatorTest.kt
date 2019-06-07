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

package androidx.navigation.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.test.EmptyFragment
import androidx.navigation.fragment.test.R
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FragmentNavigatorTest {

    companion object {
        private const val INITIAL_FRAGMENT = 1
        private const val SECOND_FRAGMENT = 2
        private const val THIRD_FRAGMENT = 3
        private const val FOURTH_FRAGMENT = 4
    }

    @get:Rule
    var activityRule = ActivityTestRule(EmptyActivity::class.java)

    private lateinit var emptyActivity: EmptyActivity
    private lateinit var fragmentManager: FragmentManager

    @Before
    fun setup() {
        emptyActivity = activityRule.activity
        fragmentManager = emptyActivity.supportFragmentManager
    }

    @UiThreadTest
    @Test
    fun testNavigate() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination().apply {
            id = INITIAL_FRAGMENT
            className = EmptyFragment::class.java.name
        }

        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)
        assertEquals("Fragment should be the correct type",
                EmptyFragment::class.java, fragment!!::class.java)
        assertEquals("Fragment should be the primary navigation Fragment",
                fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testNavigateWithFragmentFactory() {
        fragmentManager.fragmentFactory = NonEmptyFragmentFactory()
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination().apply {
            id = INITIAL_FRAGMENT
            className = NonEmptyConstructorFragment::class.java.name
        }

        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Fragment should be added")
            .that(fragment)
            .isNotNull()
        assertWithMessage("Fragment should be the correct type")
            .that(fragment)
            .isInstanceOf(NonEmptyConstructorFragment::class.java)
        assertWithMessage("Fragment should be the primary navigation Fragment")
            .that(fragment)
            .isSameInstanceAs(fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testNavigateTwice() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination().apply {
            id = INITIAL_FRAGMENT
            className = EmptyFragment::class.java.name
        }

        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)
        assertEquals("Fragment should be the correct type",
                EmptyFragment::class.java, fragment!!::class.java)
        assertEquals("Fragment should be the primary navigation Fragment",
                fragment, fragmentManager.primaryNavigationFragment)

        // Now push a second fragment
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertEquals("Replacement Fragment should be the correct type",
                EmptyFragment::class.java, replacementFragment!!::class.java)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testNavigateWithPopUpToThenPop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // Push initial fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()

        // Push a second fragment
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()

        // Pop and then push third fragment, simulating popUpTo to initial.
        val success = fragmentNavigator.popBackStack()
        assertTrue("FragmentNavigator should return true when popping the third fragment", success)
        destination.id = THIRD_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null,
                NavOptions.Builder().setPopUpTo(INITIAL_FRAGMENT, false).build(), null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()

        // Now pop the Fragment
        val popped = fragmentNavigator.popBackStack()
        assertTrue("FragmentNavigator should return true when popping the third fragment", popped)
    }

    @UiThreadTest
    @Test
    fun testSingleTopInitial() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.className = EmptyFragment::class.java.name

        fragmentNavigator.navigate(destination, null, null, null)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)
        val lifecycle = fragment!!.lifecycle

        assertThat(fragmentNavigator.navigate(destination, null,
                NavOptions.Builder().setLaunchSingleTop(true).build(), null))
            .isNull()
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
                replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)
        assertNotEquals("Replacement should be a new instance", fragment,
                replacementFragment)
        assertEquals("Old instance should be destroyed", Lifecycle.State.DESTROYED,
                lifecycle.currentState)
    }

    @UiThreadTest
    @Test
    fun testSingleTop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.className = EmptyFragment::class.java.name

        // First push an initial Fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val initialFragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Initial Fragment should be added")
            .that(initialFragment)
            .isNotNull()

        // Now push the Fragment that we want to replace with a singleTop operation
        destination.id = 1
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Fragment should be added")
            .that(fragment)
            .isNotNull()
        val lifecycle = fragment!!.lifecycle

        assertThat(fragmentNavigator.navigate(destination, null,
                NavOptions.Builder().setLaunchSingleTop(true).build(), null))
            .isNull()
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Replacement Fragment should be added")
            .that(replacementFragment)
            .isNotNull()
        assertWithMessage("Replacement Fragment should be the correct type")
            .that(replacementFragment)
            .isInstanceOf(EmptyFragment::class.java)
        assertWithMessage("Replacement Fragment should be the primary navigation Fragment")
            .that(fragmentManager.primaryNavigationFragment)
            .isSameInstanceAs(replacementFragment)
        assertWithMessage("Replacement should be a new instance")
            .that(replacementFragment)
            .isNotSameInstanceAs(fragment)
        assertWithMessage("Old instance should be destroyed")
            .that(lifecycle.currentState)
            .isEqualTo(Lifecycle.State.DESTROYED)

        assertThat(fragmentNavigator.popBackStack())
            .isTrue()
        fragmentManager.executePendingTransactions()
        assertWithMessage("Initial Fragment should be on top of back stack after pop")
            .that(fragmentManager.findFragmentById(R.id.container))
            .isSameInstanceAs(initialFragment)
        assertWithMessage("Initial Fragment should be the primary navigation Fragment")
            .that(fragmentManager.primaryNavigationFragment)
            .isSameInstanceAs(initialFragment)
    }

    @UiThreadTest
    @Test
    fun testPopInitial() {
        val fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push an initial Fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)

        // Now pop the initial Fragment
        val popped = fragmentNavigator.popBackStack()
        assertWithMessage("FragmentNavigator should return false when popping " +
                "the initial Fragment")
            .that(popped)
            .isTrue()
    }

    @UiThreadTest
    @Test
    fun testPop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push an initial Fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)

        // Now push the Fragment that we want to pop
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
                replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)

        // Now pop the Fragment
        val popped = fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        assertTrue("FragmentNavigator should return true when popping a Fragment", popped)
        assertEquals("Fragment should be the primary navigation Fragment after pop",
                fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testPopWithSameDestinationTwice() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push an initial Fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Fragment should be added")
            .that(fragment)
            .isNotNull()

        // Now push a second Fragment
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Replacement Fragment should be added")
            .that(replacementFragment)
            .isNotNull()
        assertWithMessage("Replacement Fragment should be the primary navigation Fragment")
            .that(fragmentManager.primaryNavigationFragment)
            .isSameInstanceAs(replacementFragment)

        // Push the same Fragment a second time, creating a stack of two
        // identical Fragments
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragmentToPop = fragmentManager.findFragmentById(R.id.container)
        assertWithMessage("Fragment to pop should be added")
            .that(fragmentToPop)
            .isNotNull()
        assertWithMessage("Fragment to pop should be the primary navigation Fragment")
            .that(fragmentManager.primaryNavigationFragment)
            .isSameInstanceAs(fragmentToPop)

        // Now pop the Fragment
        val popped = fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        assertWithMessage("FragmentNavigator should return true when popping a Fragment")
            .that(popped)
            .isTrue()
        assertWithMessage("Replacement Fragment should be the primary navigation Fragment " +
                "after pop")
            .that(fragmentManager.primaryNavigationFragment)
            .isSameInstanceAs(replacementFragment)
    }

    @UiThreadTest
    @Test
    fun testPopWithChildFragmentBackStack() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push an initial Fragment
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)

        // Now push the Fragment that we want to pop
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
            replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
            replacementFragment, fragmentManager.primaryNavigationFragment)

        // Add a Fragment to the replacementFragment's childFragmentManager back stack
        replacementFragment?.childFragmentManager?.run {
            beginTransaction()
                .add(EmptyFragment(), "child")
                .addToBackStack(null)
                .commit()
            executePendingTransactions()
        }

        // Now pop the Fragment
        val popped = fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        assertTrue("FragmentNavigator should return true when popping a Fragment", popped)
        assertEquals("Fragment should be the primary navigation Fragment after pop",
            fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testDeepLinkPop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity, fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push two Fragments as our 'deep link'
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)

        // Now push the Fragment that we want to pop
        destination.id = THIRD_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
                replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)

        // Now pop the Fragment
        fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertEquals("Fragment should be the primary navigation Fragment after pop",
                fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testDeepLinkPopWithSaveState() {
        var fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        // First push two Fragments as our 'deep link'
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)

        // Now push the Fragment that we want to pop
        destination.id = THIRD_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        val replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
                replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)

        // Create a new FragmentNavigator, replacing the previous one
        val savedState = fragmentNavigator.onSaveState()
        fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        fragmentNavigator.onRestoreState(savedState)

        // Now pop the Fragment
        fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        val fragment = fragmentManager.findFragmentById(R.id.container)
        assertEquals("Fragment should be the primary navigation Fragment after pop",
                fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testNavigateThenPopAfterSaveState() {
        var fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.id = INITIAL_FRAGMENT
        destination.className = EmptyFragment::class.java.name

        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        var fragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Fragment should be added", fragment)
        assertEquals("Fragment should be the correct type",
                EmptyFragment::class.java, fragment!!::class.java)
        assertEquals("Fragment should be the primary navigation Fragment",
                fragment, fragmentManager.primaryNavigationFragment)

        // Now push a second fragment
        destination.id = SECOND_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        var replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertEquals("Replacement Fragment should be the correct type",
                EmptyFragment::class.java, replacementFragment!!::class.java)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)

        // Create a new FragmentNavigator, replacing the previous one
        val savedState = fragmentNavigator.onSaveState()
        fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        fragmentNavigator.onRestoreState(savedState)

        // Now push a third fragment after the state save
        destination.id = THIRD_FRAGMENT
        assertThat(fragmentNavigator.navigate(destination, null, null, null))
            .isEqualTo(destination)
        fragmentManager.executePendingTransactions()
        replacementFragment = fragmentManager.findFragmentById(R.id.container)
        assertNotNull("Replacement Fragment should be added", replacementFragment)
        assertTrue("Replacement Fragment should be the correct type",
                replacementFragment is EmptyFragment)
        assertEquals("Replacement Fragment should be the primary navigation Fragment",
                replacementFragment, fragmentManager.primaryNavigationFragment)

        // Now pop the Fragment
        fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        fragment = fragmentManager.findFragmentById(R.id.container)
        assertEquals("Fragment should be the primary navigation Fragment after pop",
                fragment, fragmentManager.primaryNavigationFragment)
    }

    @UiThreadTest
    @Test
    fun testMultipleNavigateFragmentTransactionsThenPop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity,
            fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.className = EmptyFragment::class.java.name
        val destination2 = fragmentNavigator.createDestination()
        destination2.className = Fragment::class.java.name

        // Push 3 fragments without executing pending transactions.
        destination.id = INITIAL_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)
        destination2.id = SECOND_FRAGMENT
        fragmentNavigator.navigate(destination2, null, null, null)
        destination.id = THIRD_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)

        // Now pop the Fragment
        val popped = fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        assertWithMessage("FragmentNavigator should return true when popping the third fragment")
            .that(popped).isTrue()
        // We should ensure the fragment manager is on the proper fragment at the end
        assertWithMessage("FragmentManager back stack should have only SECOND_FRAGMENT")
            .that(fragmentManager.backStackEntryCount)
            .isEqualTo(1)
        assertWithMessage("PrimaryFragment should be the correct type")
            .that(fragmentManager.primaryNavigationFragment)
            .isNotInstanceOf(EmptyFragment::class.java)
    }

    @UiThreadTest
    @Test
    fun testMultiplePopFragmentTransactionsThenPop() {
        val fragmentNavigator = FragmentNavigator(emptyActivity,
                fragmentManager, R.id.container)
        val destination = fragmentNavigator.createDestination()
        destination.className = EmptyFragment::class.java.name

        // Push 4 fragments
        destination.id = INITIAL_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)
        destination.id = SECOND_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)
        destination.id = THIRD_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)
        destination.id = FOURTH_FRAGMENT
        fragmentNavigator.navigate(destination, null, null, null)
        fragmentManager.executePendingTransactions()

        // Pop 2 fragments without executing pending transactions.
        fragmentNavigator.popBackStack()
        fragmentNavigator.popBackStack()

        val popped = fragmentNavigator.popBackStack()
        fragmentManager.executePendingTransactions()
        assertTrue("FragmentNavigator should return true when popping the third fragment", popped)
    }
}

class EmptyActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.empty_activity)
    }
}

class NonEmptyConstructorFragment(val test: String) : Fragment()

class NonEmptyFragmentFactory : FragmentFactory() {
    override fun instantiate(
        classLoader: ClassLoader,
        className: String
    ) = if (className == NonEmptyConstructorFragment::class.java.name) {
        NonEmptyConstructorFragment("test")
    } else {
        super.instantiate(classLoader, className)
    }
}
