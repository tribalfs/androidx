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

package androidx.compose.ui.node

import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.snapshots.SnapshotStateObserver

/**
 * Performs snapshot observation for blocks like draw and layout which should be re-invoked
 * automatically when the snapshot value has been changed.
 */
// TODO make it internal once Owner is internal
@OptIn(ExperimentalComposeApi::class, ExperimentalLayoutNodeApi::class)
@Suppress("CallbackName") // TODO rename this and SnapshotStateObserver. b/173401548
class OwnerSnapshotObserver(onChangedExecutor: (callback: () -> Unit) -> Unit) {

    private val observer = SnapshotStateObserver(onChangedExecutor)

    private val onCommitAffectingMeasure: (LayoutNode) -> Unit = { layoutNode ->
        if (layoutNode.isValid) {
            layoutNode.requestRemeasure()
        }
    }

    private val onCommitAffectingLayout: (LayoutNode) -> Unit = { layoutNode ->
        if (layoutNode.isValid) {
            layoutNode.requestRelayout()
        }
    }

    private val onCommitAffectingLayer: (OwnedLayer) -> Unit = { layer ->
        if (layer.isValid) {
            layer.invalidate()
        }
    }

    private val onCommitAffectingLayerParams: (OwnedLayer) -> Unit = { layer ->
        if (layer.isValid) {
            updateLayerProperties(layer)
        }
    }

    /**
     * Observing the snapshot reads are temporary disabled during the [block] execution.
     * For example if we are currently within the measure stage and we want some code block to
     * be skipped from the observing we disable if before calling the block, execute block and
     * then enable it again.
     */
    internal fun pauseSnapshotReadObservation(block: () -> Unit) {
        observer.pauseObservingReads(block)
    }

    /**
     * Observe snapshot reads during layout of [node], executed in [block].
     */
    internal fun observeLayoutSnapshotReads(node: LayoutNode, block: () -> Unit) {
        observeReads(node, onCommitAffectingLayout, block)
    }

    /**
     * Observe snapshot reads during measure of [node], executed in [block].
     */
    internal fun observeMeasureSnapshotReads(node: LayoutNode, block: () -> Unit) {
        observeReads(node, onCommitAffectingMeasure, block)
    }

    /**
     * Observe snapshot reads during drawing the content of an [OwnedLayer], executed in [block].
     */
    internal fun observeLayerSnapshotReads(layer: OwnedLayer, block: () -> Unit) {
        observeReads(layer, onCommitAffectingLayer, block)
    }

    /**
     * Observe snapshot reads for any target, allowing consumers to determine how to respond
     * to state changes.
     */
    internal fun <T : OwnerScope> observeReads(
        target: T,
        onChanged: (T) -> Unit,
        block: () -> Unit
    ) {
        observer.observeReads(target, onChanged, block)
    }

    internal fun clearInvalidObservations() {
        observer.removeObservationsFor { !(it as OwnerScope).isValid }
    }

    internal fun clear(target: Any) {
        observer.clear(target)
    }

    internal fun updateLayerProperties(layer: OwnedLayer) {
        observer.observeReads(layer, onCommitAffectingLayerParams) {
            layer.updateLayerProperties()
        }
    }

    internal fun startObserving() {
        observer.enableStateUpdatesObserving(true)
    }

    internal fun stopObserving() {
        observer.enableStateUpdatesObserving(false)
        observer.clear()
    }
}
