/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.ui.test.android

import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.ui.core.AndroidOwner
import java.util.Collections
import java.util.WeakHashMap

/**
 * Registry where all [AndroidOwner]s should be registered while they are attached to the window.
 * This registry is used by the testing library to query the owners's state.
 */
internal object AndroidOwnerRegistry {
    private val owners = Collections.newSetFromMap(WeakHashMap<AndroidOwner, Boolean>())
    private val notYetDrawn = Collections.newSetFromMap(WeakHashMap<AndroidOwner, Boolean>())
    private var onDrawnCallback: (() -> Unit)? = null
    private val registryListeners = mutableSetOf<OnRegistrationChangedListener>()

    /**
     * Returns if the registry is setup to receive registrations from [AndroidOwner]s
     */
    val isSetUp: Boolean
        get() = AndroidOwner.onAndroidOwnerCreatedCallback == ::onAndroidOwnerCreated

    /**
     * Sets up this registry to be notified of any [AndroidOwner] created
     */
    internal fun setupRegistry() {
        AndroidOwner.onAndroidOwnerCreatedCallback = ::onAndroidOwnerCreated
    }

    /**
     * Cleans up the changes made by [setupRegistry]. Call this after your test has run.
     */
    internal fun tearDownRegistry() {
        AndroidOwner.onAndroidOwnerCreatedCallback = null
    }

    private fun onAndroidOwnerCreated(owner: AndroidOwner) {
        owner.view.addOnAttachStateChangeListener(OwnerAttachedListener(owner))
    }

    /**
     * Returns a copy of the set of all registered [AndroidOwner]s, including ones that are
     * normally not relevant (like those whose lifecycle state is not RESUMED).
     */
    fun getUnfilteredOwners(): Set<AndroidOwner> {
        return owners.toSet()
    }

    /**
     * Returns a copy of the set of all registered [AndroidOwner]s that can be interacted with.
     * This method is almost always preferred over [getUnfilteredOwners].
     */
    fun getOwners(): Set<AndroidOwner> {
        return owners.filterTo(mutableSetOf()) {
            // lifecycleOwner can only be null if it.view is not yet attached, and since owners
            // are only in the registry when they're attached we don't care about the
            // lifecycleOwner being null.
            val lifecycleOwner = it.lifecycleOwner ?: return@filterTo false
            lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
        }
    }

    /**
     * Adds the given [listener], to be notified when an [AndroidOwner] registers or unregisters.
     */
    fun addOnRegistrationChangedListener(listener: OnRegistrationChangedListener) {
        registryListeners.add(listener)
    }

    /**
     * Removes the given [listener].
     */
    fun removeOnRegistrationChangedListener(listener: OnRegistrationChangedListener) {
        registryListeners.remove(listener)
    }

    private fun dispatchOnRegistrationChanged(owner: AndroidOwner, isRegistered: Boolean) {
        registryListeners.toList().forEach {
            it.onRegistrationChanged(owner, isRegistered)
        }
    }

    /**
     * Returns if all registered owners have finished at least one draw call.
     */
    fun haveAllDrawn(): Boolean {
        return notYetDrawn.all {
            val lifecycleOwner = ViewTreeLifecycleOwner.get(it.view) ?: return false
            lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED
        }
    }

    /**
     * Adds a [callback] to be called when all registered [AndroidOwner]s have drawn at least
     * once. The callback will be removed after it is called.
     */
    fun setOnDrawnCallback(callback: (() -> Unit)?) {
        onDrawnCallback = callback
    }

    /**
     * Registers the [owner] in this registry. Must be called from [View.onAttachedToWindow].
     */
    internal fun registerOwner(owner: AndroidOwner) {
        owners.add(owner)
        notYetDrawn.add(owner)
        owner.view.viewTreeObserver.addOnDrawListener(FirstDrawListener(owner))
        dispatchOnRegistrationChanged(owner, true)
    }

    /**
     * Unregisters the [owner] from this registry. Must be called from [View.onDetachedFromWindow].
     */
    internal fun unregisterOwner(owner: AndroidOwner) {
        owners.remove(owner)
        notYetDrawn.remove(owner)
        dispatchOnDrawn()
        dispatchOnRegistrationChanged(owner, false)
    }

    /**
     * Should be called when a registered owner has drawn for the first time. Can be called after
     * subsequent draws as well, but that is not required.
     */
    // TODO(b/155742511): Move all onDrawn functionality to another class, so the registry
    //  remains purely about tracking AndroidOwners.
    private fun notifyOwnerDrawn(owner: AndroidOwner) {
        notYetDrawn.remove(owner)
        dispatchOnDrawn()
    }

    private fun dispatchOnDrawn() {
        if (haveAllDrawn()) {
            onDrawnCallback?.invoke()
            onDrawnCallback = null
        }
    }

    /**
     * Interface to be implemented by components that want to be notified when an [AndroidOwner]
     * registers or unregisters at this registry.
     */
    interface OnRegistrationChangedListener {
        fun onRegistrationChanged(owner: AndroidOwner, registered: Boolean)
    }

    private class FirstDrawListener(private val owner: AndroidOwner) :
        ViewTreeObserver.OnDrawListener {
        private var invoked = false

        override fun onDraw() {
            if (!invoked) {
                invoked = true
                owner.view.post {
                    // the view was drawn
                    notifyOwnerDrawn(owner)
                    val viewTreeObserver = owner.view.viewTreeObserver
                    if (viewTreeObserver.isAlive) {
                        viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
        }
    }

    private class OwnerAttachedListener(
        private val owner: AndroidOwner
    ) : View.OnAttachStateChangeListener {

        // Note: owner.view === view, because the owner _is_ the view,
        // and this listener is only referenced from within the view.

        override fun onViewAttachedToWindow(view: View) {
            registerOwner(owner)
        }

        override fun onViewDetachedFromWindow(view: View) {
            unregisterOwner(owner)
        }
    }
}
