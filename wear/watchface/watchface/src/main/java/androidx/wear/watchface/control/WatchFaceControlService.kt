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

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.wear.watchface.utility.AsyncTraceEvent
import androidx.wear.watchface.utility.TraceEvent
import androidx.wear.watchface.IndentingPrintWriter
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.control.data.CrashInfoParcel
import androidx.wear.watchface.control.data.DefaultProviderPoliciesParams
import androidx.wear.watchface.control.data.GetComplicationSlotMetadataParams
import androidx.wear.watchface.control.data.GetUserStyleFlavorsParams
import androidx.wear.watchface.control.data.GetUserStyleSchemaParams
import androidx.wear.watchface.control.data.HeadlessWatchFaceInstanceParams
import androidx.wear.watchface.control.data.IdTypeAndDefaultProviderPolicyWireFormat
import androidx.wear.watchface.control.data.WallpaperInteractiveWatchFaceInstanceParams
import androidx.wear.watchface.data.ComplicationSlotMetadataWireFormat
import androidx.wear.watchface.editor.EditorService
import androidx.wear.watchface.runBlockingWithTracing
import androidx.wear.watchface.style.data.UserStyleFlavorsWireFormat
import androidx.wear.watchface.style.data.UserStyleSchemaWireFormat
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/**
 * A service for creating and controlling watch face instances.
 *
 * @hide
 */
@RequiresApi(27)
@VisibleForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class WatchFaceControlService : Service() {
    private val watchFaceInstanceServiceStub by lazy { createServiceStub() }

    /** @hide */
    public companion object {
        public const val ACTION_WATCHFACE_CONTROL_SERVICE: String =
            "com.google.android.wearable.action.WATCH_FACE_CONTROL"
    }

    override fun onBind(intent: Intent?): IBinder? =
        TraceEvent("WatchFaceControlService.onBind").use {
            if (ACTION_WATCHFACE_CONTROL_SERVICE == intent?.action) {
                watchFaceInstanceServiceStub
            } else {
                null
            }
        }

    @VisibleForTesting
    public open fun createServiceStub(): IWatchFaceInstanceServiceStub =
        TraceEvent("WatchFaceControlService.createServiceStub").use {
            IWatchFaceInstanceServiceStub(this, MainScope())
        }

    @VisibleForTesting
    public fun setContext(context: Context) {
        attachBaseContext(context)
    }

    @UiThread
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<String>) {
        val indentingPrintWriter = IndentingPrintWriter(writer)
        indentingPrintWriter.println("WatchFaceControlService:")
        InteractiveInstanceManager.dump(indentingPrintWriter)
        HeadlessWatchFaceImpl.dump(indentingPrintWriter)
        indentingPrintWriter.flush()
    }
}

/** @hide */
@RequiresApi(27)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class IWatchFaceInstanceServiceStub(
    private val context: Context,
    private val uiThreadCoroutineScope: CoroutineScope
) : IWatchFaceControlService.Stub() {
    override fun getApiVersion(): Int = IWatchFaceControlService.API_VERSION

    internal companion object {
        const val TAG = "IWatchFaceInstanceServiceStub"
    }

    override fun getInteractiveWatchFaceInstance(instanceId: String): IInteractiveWatchFace? =
        TraceEvent("IWatchFaceInstanceServiceStub.getInteractiveWatchFaceInstance").use {
            // This call is thread safe so we don't need to trampoline via the UI thread.
            InteractiveInstanceManager.getAndRetainInstance(instanceId)
        }

    override fun createHeadlessWatchFaceInstance(
        params: HeadlessWatchFaceInstanceParams
    ): IHeadlessWatchFace? = TraceEvent(
        "IWatchFaceInstanceServiceStub.createHeadlessWatchFaceInstance"
    ).use {
        val engine = createHeadlessEngine(params.watchFaceName, context)
        engine?.let {
            // This is serviced on a background thread so it should be fine to block.
            uiThreadCoroutineScope.runBlockingWithTracing("createHeadlessInstance") {
                // However the WatchFaceService.createWatchFace method needs to be run on the UI
                // thread.
                it.createHeadlessInstance(params)
            }
        }
    }

    private fun createHeadlessEngine(
        watchFaceName: ComponentName,
        context: Context
    ) = TraceEvent("IWatchFaceInstanceServiceStub.createEngine").use {
        // Attempt to construct the class for the specified watchFaceName, failing if it either
        // doesn't exist or isn't a [WatchFaceService].
        try {
            val watchFaceServiceClass = Class.forName(watchFaceName.className) ?: return null
            if (!WatchFaceService::class.java.isAssignableFrom(WatchFaceService::class.java)) {
                null
            } else {
                val watchFaceService =
                    watchFaceServiceClass.getConstructor().newInstance() as WatchFaceService
                watchFaceService.setContext(context)
                val engine =
                    watchFaceService.createHeadlessEngine() as WatchFaceService.EngineWrapper
                engine
            }
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    override fun getOrCreateInteractiveWatchFace(
        params: WallpaperInteractiveWatchFaceInstanceParams,
        callback: IPendingInteractiveWatchFace
    ): IInteractiveWatchFace? {
        val asyncTraceEvent =
            AsyncTraceEvent("IWatchFaceInstanceServiceStub.getOrCreateInteractiveWatchFaceWCS")
        return InteractiveInstanceManager
            .getExistingInstanceOrSetPendingWallpaperInteractiveWatchFaceInstance(
                InteractiveInstanceManager.PendingWallpaperInteractiveWatchFaceInstance(
                    params,
                    // Wrapped IPendingInteractiveWatchFace to support tracing.
                    object : IPendingInteractiveWatchFace.Stub() {
                        override fun getApiVersion() = callback.apiVersion

                        override fun onInteractiveWatchFaceCreated(
                            iInteractiveWatchFaceWcs: IInteractiveWatchFace?
                        ) {
                            asyncTraceEvent.close()
                            callback.onInteractiveWatchFaceCreated(iInteractiveWatchFaceWcs)
                        }

                        override fun onInteractiveWatchFaceCrashed(exception: CrashInfoParcel) {
                            asyncTraceEvent.close()
                            callback.onInteractiveWatchFaceCrashed(exception)
                        }
                    }
                )
            )
    }

    override fun getEditorService(): EditorService = EditorService.globalEditorService

    override fun getDefaultProviderPolicies(
        params: DefaultProviderPoliciesParams
    ): Array<IdTypeAndDefaultProviderPolicyWireFormat>? = TraceEvent(
        "IWatchFaceInstanceServiceStub.getDefaultProviderPolicies"
    ).use {
        createHeadlessEngine(params.watchFaceName, context)?.let { engine ->
            try {
                engine.getDefaultProviderPolicies()
            } catch (e: Exception) {
                Log.e(TAG, "getDefaultProviderPolicies failed due to exception", e)
                throw e
            } finally {
                try {
                    engine.onDestroy()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "WatchfaceService.EngineWrapper.onDestroy failed due to exception",
                        e
                    )
                    throw e
                }
            }
        }
    }

    override fun getUserStyleSchema(
        params: GetUserStyleSchemaParams
    ): UserStyleSchemaWireFormat? = TraceEvent(
        "IWatchFaceInstanceServiceStub.getUserStyleSchema"
    ).use {
        createHeadlessEngine(params.watchFaceName, context)?.let { engine ->
            try {
                engine.getUserStyleSchemaWireFormat()
            } catch (e: Exception) {
                Log.e(TAG, "getUserStyleSchema failed due to exception", e)
                throw e
            } finally {
                try {
                    engine.onDestroy()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "WatchfaceService.EngineWrapper.onDestroy failed due to exception",
                        e
                    )
                    throw e
                }
            }
        }
    }

    override fun getComplicationSlotMetadata(
        params: GetComplicationSlotMetadataParams
    ): Array<ComplicationSlotMetadataWireFormat>? = TraceEvent(
        "IWatchFaceInstanceServiceStub.getComplicationSlotMetadata"
    ).use {
        createHeadlessEngine(params.watchFaceName, context)?.let { engine ->
            val result: Array<ComplicationSlotMetadataWireFormat>?
            try {
                result = engine.getComplicationSlotMetadataWireFormats()
            } catch (e: Exception) {
                Log.e(TAG, "getComplicationSlotMetadata failed due to exception", e)
                throw e
            }
            engine.onDestroy()
            result
        }
    }

    override fun hasComplicationCache() = true

    override fun getUserStyleFlavors(
        params: GetUserStyleFlavorsParams
    ): UserStyleFlavorsWireFormat? = TraceEvent(
        "IWatchFaceInstanceServiceStub.getUserStyleFlavors"
    ).use {
        createHeadlessEngine(params.watchFaceName, context)?.let { engine ->
            try {
                engine.getUserStyleFlavorsWireFormat()
            } catch (e: Exception) {
                Log.e(TAG, "getUserStyleFlavors failed due to exception", e)
                throw e
            } finally {
                try {
                    engine.onDestroy()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "WatchfaceService.EngineWrapper.onDestroy failed due to exception",
                        e
                    )
                    throw e
                }
            }
        }
    }
}