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

@file:Suppress("UNCHECKED_CAST")

package androidx.compose.ui.platform

import android.annotation.SuppressLint
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import java.io.Serializable

/**
 * Creates [DisposableSaveableStateRegistry] associated with these [view] and [owner].
 */
internal fun DisposableSaveableStateRegistry(
    view: View,
    owner: SavedStateRegistryOwner
): DisposableSaveableStateRegistry {
    // When AndroidComposeView is composed into some ViewGroup we just added as a child for this
    // ViewGroup. And we don't have any id on AndroidComposeView as we can't make it unique, but
    // we require this parent ViewGroup to have an unique id for the saved instance state mechanism
    // to work (similarly to how it works without Compose). When we composed into Activity our
    // parent is the ViewGroup with android.R.id.content.
    val parentId: Int = (view.parent as? View)?.id ?: View.NO_ID
    return DisposableSaveableStateRegistry(parentId, owner)
}

/**
 * Creates [DisposableSaveableStateRegistry] with the restored values using [SavedStateRegistry] and
 * saves the values when [SavedStateRegistry] performs save.
 *
 * To provide a namespace we require unique [id]. We can't use the default way of doing it when we
 * have unique id on [AndroidComposeView] because we dynamically create [AndroidComposeView]s and
 * there is no way to have a unique id given there are could be any number of
 * [AndroidComposeView]s inside the same Activity. If we use [View.generateViewId]
 * this id will not survive Activity recreation.
 * But it is reasonable to ask our users to have an unique id on the parent ViewGroup in which we
 * compose our [AndroidComposeView]. If Activity.setContent is used then it will be a View with
 * [android.R.id.content], if ViewGroup.setContent is used then we will ask users to provide an
 * id for this ViewGroup. If @GenerateView will be used then we will ask users to set an id on
 * this generated View.
 */
internal fun DisposableSaveableStateRegistry(
    id: Int,
    savedStateRegistryOwner: SavedStateRegistryOwner
): DisposableSaveableStateRegistry {
    val key = "${SaveableStateRegistry::class.java.simpleName}:$id"

    val androidxRegistry = savedStateRegistryOwner.savedStateRegistry
    val bundle = androidxRegistry.consumeRestoredStateForKey(key)
    val restored: Map<String, List<Any?>>? = bundle?.toMap()

    val saveableStateRegistry = SaveableStateRegistry(restored) {
        canBeSavedToBundle(it)
    }
    val registered = try {
        androidxRegistry.registerSavedStateProvider(key) {
            saveableStateRegistry.performSave().toBundle()
        }
        true
    } catch (ignore: IllegalArgumentException) {
        // this means there are two AndroidComposeViews composed into different parents with the
        // same view id. currently we will just not save/restore state for the second
        // AndroidComposeView.
        // TODO: we should verify our strategy for such cases and improve it. b/162397322
        false
    }
    return DisposableSaveableStateRegistry(saveableStateRegistry) {
        if (registered) {
            androidxRegistry.unregisterSavedStateProvider(key)
        }
    }
}

/**
 * [SaveableStateRegistry] which can be disposed using [dispose].
 */
internal class DisposableSaveableStateRegistry(
    saveableStateRegistry: SaveableStateRegistry,
    private val onDispose: () -> Unit
) : SaveableStateRegistry by saveableStateRegistry {

    fun dispose() {
        onDispose()
    }
}

/**
 * Checks that [value] can be stored inside [Bundle].
 */
private fun canBeSavedToBundle(value: Any): Boolean {
    for (cl in AcceptableClasses) {
        if (cl.isInstance(value)) {
            return true
        }
    }
    if (value is SnapshotMutableState<*>) {
        if (value.policy === neverEqualPolicy<Any?>() ||
            value.policy === structuralEqualityPolicy<Any?>() ||
            value.policy === referentialEqualityPolicy<Any?>()
        ) {
            val stateValue = value.value
            return if (stateValue == null) true else canBeSavedToBundle(stateValue)
        }
    }
    return false
}

/**
 * Contains Classes which can be stored inside [Bundle].
 *
 * Some of the classes are not added separately because:
 *
 * This classes implement Serializable:
 * - Arrays (DoubleArray, BooleanArray, IntArray, LongArray, ByteArray, FloatArray, ShortArray,
 * CharArray, Array<Parcelable, Array<String>)
 * - ArrayList
 * - Primitives (Boolean, Int, Long, Double, Float, Byte, Short, Char) will be boxed when casted
 * to Any, and all the boxed classes implements Serializable.
 * This class implements Parcelable:
 * - Bundle
 *
 * Note: it is simplified copy of the array from SavedStateHandle (lifecycle-viewmodel-savedstate).
 */
private val AcceptableClasses = arrayOf(
    Serializable::class.java,
    Parcelable::class.java,
    String::class.java,
    SparseArray::class.java,
    Binder::class.java,
    Size::class.java,
    SizeF::class.java
)

private fun Bundle.toMap(): Map<String, List<Any?>>? {
    val map = mutableMapOf<String, List<Any?>>()
    this.keySet().forEach { key ->
        val list = getParcelableArrayList<Parcelable?>(key) as ArrayList<Any?>
        unwrapMutableStatesIn(list)
        map[key] = list
    }
    return map
}

private fun Map<String, List<Any?>>.toBundle(): Bundle {
    val bundle = Bundle()
    forEach { (key, list) ->
        val arrayList = if (list is ArrayList<Any?>) list else ArrayList(list)
        wrapMutableStatesIn(arrayList)
        bundle.putParcelableArrayList(
            key,
            arrayList as ArrayList<Parcelable?>
        )
    }
    return bundle
}

private fun wrapMutableStatesIn(list: MutableList<Any?>) {
    list.forEachIndexed { index, value ->
        if (value is SnapshotMutableState<*>) {
            list[index] = ParcelableMutableStateHolder(value)
        } else {
            wrapMutableStatesInListOrMap(value)
        }
    }
}

private fun wrapMutableStatesIn(map: MutableMap<Any?, Any?>) {
    map.forEach { (key, value) ->
        if (value is SnapshotMutableState<*>) {
            map[key] = ParcelableMutableStateHolder(value)
        } else {
            wrapMutableStatesInListOrMap(value)
        }
    }
}

private fun wrapMutableStatesInListOrMap(value: Any?) {
    when (value) {
        is MutableList<*> -> {
            wrapMutableStatesIn(value as MutableList<Any?>)
        }
        is List<*> -> {
            value.forEach {
                check(it !is SnapshotMutableState<*>) {
                    "Unexpected immutable list containing MutableState!"
                }
            }
        }
        is MutableMap<*, *> -> {
            wrapMutableStatesIn(value as MutableMap<Any?, Any?>)
        }
        is Map<*, *> -> {
            value.forEach {
                check(it.value !is SnapshotMutableState<*>) {
                    "Unexpected immutable map containing MutableState!"
                }
            }
        }
    }
}

private fun unwrapMutableStatesIn(list: MutableList<Any?>) {
    list.forEachIndexed { index, value ->
        if (value is ParcelableMutableStateHolder) {
            list[index] = value.state
        } else {
            unwrapMutableStatesInListOrMap(value)
        }
    }
}

private fun unwrapMutableStatesIn(map: MutableMap<Any?, Any?>) {
    map.forEach { (key, value) ->
        if (value is ParcelableMutableStateHolder) {
            map[key] = value.state
        } else {
            unwrapMutableStatesInListOrMap(value)
        }
    }
}

private fun unwrapMutableStatesInListOrMap(value: Any?) {
    when (value) {
        is MutableList<*> -> {
            unwrapMutableStatesIn(value as MutableList<Any?>)
        }
        is MutableMap<*, *> -> {
            unwrapMutableStatesIn(value as MutableMap<Any?, Any?>)
        }
    }
}

@SuppressLint("BanParcelableUsage")
private class ParcelableMutableStateHolder : Parcelable {

    val state: SnapshotMutableState<*>

    constructor(state: SnapshotMutableState<*>) {
        this.state = state
    }

    private constructor(parcel: Parcel, loader: ClassLoader?) {
        val value = parcel.readValue(loader ?: javaClass.classLoader)
        val policyIndex = parcel.readInt()
        state = mutableStateOf(
            value,
            when (policyIndex) {
                PolicyNeverEquals -> neverEqualPolicy()
                PolicyStructuralEquality -> structuralEqualityPolicy()
                PolicyReferentialEquality -> referentialEqualityPolicy()
                else -> throw IllegalStateException(
                    "Restored an incorrect MutableState policy $policyIndex"
                )
            }
        ) as SnapshotMutableState
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(state.value)
        parcel.writeInt(
            when (state.policy) {
                neverEqualPolicy<Any?>() -> PolicyNeverEquals
                structuralEqualityPolicy<Any?>() -> PolicyStructuralEquality
                referentialEqualityPolicy<Any?>() -> PolicyReferentialEquality
                else -> throw IllegalStateException(
                    "Only known types of MutableState's SnapshotMutationPolicy are supported"
                )
            }
        )
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        private const val PolicyNeverEquals = 0
        private const val PolicyStructuralEquality = 1
        private const val PolicyReferentialEquality = 2

        @Suppress("unused")
        @JvmField
        val CREATOR: Parcelable.Creator<ParcelableMutableStateHolder> =
            object : Parcelable.ClassLoaderCreator<ParcelableMutableStateHolder> {
                override fun createFromParcel(parcel: Parcel, loader: ClassLoader) =
                    ParcelableMutableStateHolder(parcel, loader)

                override fun createFromParcel(parcel: Parcel) =
                    ParcelableMutableStateHolder(parcel, null)

                override fun newArray(size: Int) = arrayOfNulls<ParcelableMutableStateHolder?>(size)
            }
    }
}
