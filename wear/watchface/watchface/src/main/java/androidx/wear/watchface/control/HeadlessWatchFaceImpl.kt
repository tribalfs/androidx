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

package androidx.wear.watchface.control

import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.wear.watchface.utility.TraceEvent
import androidx.wear.watchface.IndentingPrintWriter
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.control.data.ComplicationRenderParams
import androidx.wear.watchface.control.data.WatchFaceRenderParams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * A headless watch face instance. This doesn't render asynchronously and the exposed API makes it
 * effectively stateless.
 */
@RequiresApi(27)
internal class HeadlessWatchFaceImpl(
    internal var engine: WatchFaceService.EngineWrapper?,
    internal var watchFaceService: WatchFaceService?
) : IHeadlessWatchFace.Stub() {

    internal companion object {
        const val TAG = "HeadlessWatchFaceImpl"

        @UiThread
        fun dump(indentingPrintWriter: IndentingPrintWriter) {
            indentingPrintWriter.println("HeadlessWatchFace instances:")
            indentingPrintWriter.increaseIndent()
            for (instance in headlessInstances) {
                require(instance.engine!!.getUiThreadHandler().looper.isCurrentThread) {
                    "dump must be called from the UIThread"
                }
                indentingPrintWriter.println("HeadlessWatchFaceImpl:")
                indentingPrintWriter.increaseIndent()
                instance.engine?.dump(indentingPrintWriter)
                indentingPrintWriter.decreaseIndent()
            }
            indentingPrintWriter.decreaseIndent()
        }

        private val headlessInstances = HashSet<HeadlessWatchFaceImpl>()
    }

    init {
        TraceEvent("HeadlessWatchFaceImpl.init").use {
            runBlocking {
                val coroutineContext = synchronized(this) {
                    engine!!.uiThreadCoroutineScope.coroutineContext
                }
                withContext(coroutineContext) {
                    headlessInstances.add(this@HeadlessWatchFaceImpl)
                }
            }
        }
    }

    override fun getApiVersion() = IHeadlessWatchFace.API_VERSION

    override fun renderWatchFaceToBitmap(params: WatchFaceRenderParams) =
        WatchFaceService.awaitDeferredWatchFaceImplThenRunOnUiThreadBlocking(
            engine,
            "HeadlessWatchFaceImpl.renderWatchFaceToBitmap"
        ) { watchFaceImpl -> watchFaceImpl.renderWatchFaceToBitmap(params) }

    override fun getPreviewReferenceTimeMillis() =
        WatchFaceService.awaitDeferredWatchFaceImplThenRunOnUiThreadBlocking(
            engine,
            "HeadlessWatchFaceImpl.getPreviewReferenceTimeMillis"
        ) { watchFaceImpl -> watchFaceImpl.previewReferenceInstant.toEpochMilli() } ?: 0

    override fun getComplicationState() =
        WatchFaceService.awaitDeferredWatchFaceImplThenRunOnUiThreadBlocking(
            engine,
            "HeadlessWatchFaceImpl.getComplicationState"
        ) { watchFaceImpl -> watchFaceImpl.getComplicationState() }

    override fun renderComplicationToBitmap(params: ComplicationRenderParams) =
        WatchFaceService.awaitDeferredWatchFaceImplThenRunOnUiThreadBlocking(
            engine,
            "HeadlessWatchFaceImpl.renderComplicationToBitmap"
        ) { watchFaceImpl -> watchFaceImpl.renderComplicationToBitmap(params) }

    override fun getUserStyleSchema() =
        WatchFaceService.awaitDeferredWatchFaceAndComplicationManagerThenRunOnBinderThread(
            engine,
            "HeadlessWatchFaceImpl.getUserStyleSchema"
        ) { watchFaceInitDetails ->
            watchFaceInitDetails.userStyleRepository.schema.toWireFormat()
        }

    override fun computeUserStyleSchemaDigestHash() =
        WatchFaceService.awaitDeferredWatchFaceAndComplicationManagerThenRunOnBinderThread(
            engine,
            "HeadlessWatchFaceImpl.computeUserStyleSchemaDigestHash"
        ) { watchFaceInitDetails ->
            watchFaceInitDetails.userStyleRepository.schema.getDigestHash()
        }

    override fun getUserStyleFlavors() =
        WatchFaceService.awaitDeferredWatchFaceAndComplicationManagerThenRunOnBinderThread(
            engine,
            "HeadlessWatchFaceImpl.getUserStyleFlavors"
        ) { watchFaceInitDetails ->
            watchFaceInitDetails.userStyleFlavors.toWireFormat()
        }

    override fun release() {
        TraceEvent("HeadlessWatchFaceImpl.release").use {
            runBlocking {
                val engineCopy = synchronized(this) { engine!! }
                withContext(engineCopy.uiThreadCoroutineScope.coroutineContext) {
                    headlessInstances.remove(this@HeadlessWatchFaceImpl)
                    synchronized(this@HeadlessWatchFaceImpl) {
                        engine!!.onDestroy()
                        engine = null

                        watchFaceService!!.onDestroy()
                        watchFaceService = null
                    }
                }
                engineCopy
            }.cancelCoroutineScopes()
        }
    }
}
