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

package androidx.compose.runtime.savedinstancestate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember

/**
 * Remember the value produced by [init].
 *
 * It behaves similarly to [remember], but the stored value will survive the activity or process
 * recreation using the saved instance state mechanism (for example it happens when the screen is
 * rotated in the Android application).
 *
 * @sample androidx.compose.runtime.savedinstancestate.samples.RememberSavedInstanceStateSample
 *
 * This function works nicely with mutable objects, when you update the state of this object
 * instead of recreating it. If you work with immutable objects [savedInstanceState] can suit you
 * more as it wraps the value into the [MutableState].
 *
 * If you use it with types which can be stored inside the Bundle then it will be saved and
 * restored automatically using [autoSaver], otherwise you will need to provide a custom [Saver]
 * implementation via the [saver] param.
 *
 * @sample androidx.compose.runtime.savedinstancestate.samples.CustomSaverSample
 *
 * @param inputs A set of inputs such that, when any of them have changed, will cause the state to
 * reset and [init] to be rerun
 * @param saver The [Saver] object which defines how the state is saved and restored.
 * @param key An optional key to be used as a key for the saved value. If not provided we use the
 * automatically generated by the Compose runtime which is unique for the every exact code location
 * in the composition tree
 * @param init A factory function to create the initial value of this state
 */
@OptIn(ExperimentalComposeApi::class)
@Composable
fun <T : Any> rememberSavedInstanceState(
    vararg inputs: Any?,
    saver: Saver<T, out Any> = autoSaver(),
    key: String? = null,
    init: () -> T
): T {
    // key is the one provided by the user or the one generated by the compose runtime
    val finalKey = if (!key.isNullOrEmpty()) {
        key
    } else {
        currentComposer.currentCompoundKeyHash.toString()
    }
    @Suppress("UNCHECKED_CAST")
    (saver as Saver<T, Any>)

    val registry = AmbientUiSavedStateRegistry.current
    // value is restored using the registry or created via [init] lambda
    val value = remember(*inputs) {
        // TODO not restore when the input values changed (use hashKeys?) b/152014032
        val restored = registry?.consumeRestored(finalKey)?.let {
            saver.restore(it)
        }
        restored ?: init()
    }

    // save the latest passed saver object into a state object to be able to use it when we will
    // be saving the value. keeping value in mutableStateOf() allows us to properly handle
    // possible compose transactions cancellations
    val saverHolder = remember { mutableStateOf(saver) }
    saverHolder.value = saver

    // re-register if the registry or key has been changed
    onCommit(registry, finalKey) {
        if (registry != null) {
            val valueProvider = {
                with(saverHolder.value) { SaverScopeImpl(registry::canBeSaved).save(value) }
            }
            registry.requireCanBeSaved(valueProvider())
            registry.registerProvider(finalKey, valueProvider)
            onDispose {
                registry.unregisterProvider(finalKey, valueProvider)
            }
        }
    }
    return value
}

private fun UiSavedStateRegistry.requireCanBeSaved(value: Any?) {
    if (value != null && !canBeSaved(value)) {
        throw IllegalArgumentException(
            if (value is MutableState<*>) {
                "Please use savedInstanceState() if you want to save a MutableState"
            } else {
                "$value cannot be saved using the current UiSavedStateRegistry. The default " +
                    "implementation only supports types which can be stored inside the Bundle" +
                    ". Please consider implementing a custom Saver for this class and pass it" +
                    " to savedInstanceState() or rememberSavedInstanceState()."
            }
        )
    }
}

// TODO this will not be needed when we make SaverScope "fun interface"
private class SaverScopeImpl(val canBeSaved: (Any) -> Boolean) : SaverScope {
    override fun canBeSaved(value: Any) = canBeSaved.invoke(value)
}
