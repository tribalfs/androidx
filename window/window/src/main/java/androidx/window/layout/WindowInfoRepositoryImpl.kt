/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.window.layout

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.core.util.Consumer
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * An implementation of [WindowInfoRepository] that provides the [WindowLayoutInfo] and
 * [WindowMetrics] for the given [Activity].
 *
 * @param activity that the provided window is based on.
 * @param windowMetricsCalculator a helper to calculate the [WindowMetrics] for the [Activity].
 * @param windowBackend a helper to provide the [WindowLayoutInfo].
 */
internal class WindowInfoRepositoryImpl(
    private val activity: Activity,
    private val windowMetricsCalculator: WindowMetricsCalculator,
    private val windowBackend: WindowBackend
) : WindowInfoRepository {

    /**
     * Returns the [WindowMetrics] according to the current system state.
     *
     *
     * The metrics describe the size of the area the window would occupy with
     * [MATCH_PARENT][android.view.WindowManager.LayoutParams.MATCH_PARENT] width and height
     * and any combination of flags that would allow the window to extend behind display cutouts.
     *
     *
     * The value of this is based on the **current** windowing state of the system. For
     * example, for activities in multi-window mode, the metrics returned are based on the
     * current bounds that the user has selected for the [Activity][android.app.Activity]'s
     * window.
     *
     * @see android.view.WindowManager.getCurrentWindowMetrics
     */
    override val currentWindowMetrics: Flow<WindowMetrics>
        get() {
            return configurationChanged {
                windowMetricsCalculator.computeCurrentWindowMetrics(activity)
            }.distinctUntilChanged()
        }

    private fun <T> configurationChanged(producer: () -> T): Flow<T> {
        return flow {
            val channel = Channel<T>(
                capacity = BUFFER_CAPACITY,
                onBufferOverflow = DROP_OLDEST
            )
            val publish: () -> Unit = { channel.trySend(producer()) }
            // TODO(b/199442549) switch back to ComponentCallbacks when possible
            val configChangeObserver =
                View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> publish() }
            publish()
            activity.window?.decorView
                ?.addOnLayoutChangeListener(configChangeObserver)
            try {
                for (item in channel) {
                    emit(item)
                }
            } finally {
                activity.window?.decorView
                    ?.removeOnLayoutChangeListener(configChangeObserver)
            }
        }
    }

    /**
     * A [Flow] of window layout changes in the current visual [Context].
     *
     * @see Activity.onAttachedToWindow
     */
    override val windowLayoutInfo: Flow<WindowLayoutInfo>
        get() {
            // TODO(b/191386826) migrate to callbackFlow once the API is stable
            return flow {
                val channel = Channel<WindowLayoutInfo>(
                    capacity = BUFFER_CAPACITY,
                    onBufferOverflow = DROP_OLDEST
                )
                val listener = Consumer<WindowLayoutInfo> { info -> channel.trySend(info) }
                windowBackend.registerLayoutChangeCallback(activity, Runnable::run, listener)
                try {
                    for (item in channel) {
                        emit(item)
                    }
                } finally {
                    windowBackend.unregisterLayoutChangeCallback(listener)
                }
            }
        }

    internal companion object {
        private const val BUFFER_CAPACITY = 10
    }
}
