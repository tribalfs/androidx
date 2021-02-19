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
package androidx.navigation.fragment

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.RestrictTo
import androidx.core.content.res.use
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.FloatingWindow
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.fragment.DialogFragmentNavigator.Destination

/**
 * Navigator that uses [DialogFragment.show]. Every
 * destination using this Navigator must set a valid DialogFragment class name with
 * `android:name` or [Destination.setClassName].
 */
@Navigator.Name("dialog")
public class DialogFragmentNavigator(
    private val context: Context,
    private val fragmentManager: FragmentManager
) : Navigator<Destination>() {
    private var dialogCount = 0
    private val restoredTagsAwaitingAttach = mutableSetOf<String?>()
    private val observer = LifecycleEventObserver { source, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            val dialogFragment = source as DialogFragment
            if (!dialogFragment.requireDialog().isShowing) {
                NavHostFragment.findNavController(dialogFragment).popBackStack()
            }
        }
    }

    public override fun popBackStack(): Boolean {
        if (dialogCount == 0) {
            return false
        }
        if (fragmentManager.isStateSaved) {
            Log.i(
                TAG, "Ignoring popBackStack() call: FragmentManager has already saved its state"
            )
            return false
        }
        val existingFragment = fragmentManager.findFragmentByTag(DIALOG_TAG + --dialogCount)
        if (existingFragment != null) {
            existingFragment.lifecycle.removeObserver(observer)
            (existingFragment as DialogFragment).dismiss()
        }
        return true
    }

    public override fun createDestination(): Destination {
        return Destination(this)
    }

    public override fun navigate(
        destination: Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ): NavDestination? {
        if (fragmentManager.isStateSaved) {
            Log.i(TAG, "Ignoring navigate() call: FragmentManager has already saved its state")
            return null
        }
        var className = destination.className
        if (className[0] == '.') {
            className = context.packageName + className
        }
        val frag = fragmentManager.fragmentFactory.instantiate(
            context.classLoader, className
        )
        require(DialogFragment::class.java.isAssignableFrom(frag.javaClass)) {
            "Dialog destination ${destination.className} is not an instance of DialogFragment"
        }
        val dialogFragment = frag as DialogFragment
        dialogFragment.arguments = args
        dialogFragment.lifecycle.addObserver(observer)
        dialogFragment.show(fragmentManager, DIALOG_TAG + dialogCount++)
        return destination
    }

    public override fun onSaveState(): Bundle? {
        if (dialogCount == 0) {
            return null
        }
        val b = Bundle()
        b.putInt(KEY_DIALOG_COUNT, dialogCount)
        return b
    }

    public override fun onRestoreState(savedState: Bundle) {
        dialogCount = savedState.getInt(KEY_DIALOG_COUNT, 0)
        for (index in 0 until dialogCount) {
            val fragment = fragmentManager
                .findFragmentByTag(DIALOG_TAG + index) as DialogFragment?
            fragment?.lifecycle?.addObserver(observer)
                ?: restoredTagsAwaitingAttach.add(DIALOG_TAG + index)
        }
    }

    // TODO: Switch to FragmentOnAttachListener once we depend on Fragment 1.3
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun onAttachFragment(childFragment: Fragment) {
        val needToAddObserver = restoredTagsAwaitingAttach.remove(childFragment.tag)
        if (needToAddObserver) {
            childFragment.lifecycle.addObserver(observer)
        }
    }

    /**
     * NavDestination specific to [DialogFragmentNavigator].
     */
    @NavDestination.ClassType(DialogFragment::class)
    public open class Destination
    /**
     * Construct a new fragment destination. This destination is not valid until you set the
     * Fragment via [.setClassName].
     *
     * @param fragmentNavigator The [DialogFragmentNavigator] which this destination will be
     *                          associated with. Generally retrieved via a [NavController]'s
     *                          [NavigatorProvider.getNavigator] method.
     */
    public constructor(fragmentNavigator: Navigator<out Destination?>) :
        NavDestination(fragmentNavigator), FloatingWindow {
        private var _className: String? = null
        /**
         * Gets the DialogFragment's class name associated with this destination
         *
         * @throws IllegalStateException when no DialogFragment class was set.
         */
        public val className: String
            get() {
                checkNotNull(_className) { "DialogFragment class was not set" }
                return _className as String
            }

        /**
         * Construct a new fragment destination. This destination is not valid until you set the
         * Fragment via [.setClassName].
         *
         * @param navigatorProvider The [NavController] which this destination
         * will be associated with.
         */
        public constructor(navigatorProvider: NavigatorProvider) : this(
            navigatorProvider.getNavigator(DialogFragmentNavigator::class.java)
        )

        @CallSuper
        public override fun onInflate(context: Context, attrs: AttributeSet) {
            super.onInflate(context, attrs)
            context.resources.obtainAttributes(
                attrs,
                R.styleable.DialogFragmentNavigator
            ).use { array ->
                val className = array.getString(R.styleable.DialogFragmentNavigator_android_name)
                className?.let { setClassName(it) }
            }
        }

        /**
         * Set the DialogFragment class name associated with this destination
         * @param className The class name of the DialogFragment to show when you navigate to this
         *                  destination
         * @return this [Destination]
         */
        public fun setClassName(className: String): Destination {
            _className = className
            return this
        }
    }

    private companion object {
        private const val TAG = "DialogFragmentNavigator"
        private const val KEY_DIALOG_COUNT = "androidx-nav-dialogfragment:navigator:count"
        private const val DIALOG_TAG = "androidx-nav-fragment:navigator:dialog:"
    }
}
