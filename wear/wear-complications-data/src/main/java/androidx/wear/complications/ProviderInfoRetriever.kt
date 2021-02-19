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
package androidx.wear.complications

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.support.wearable.complications.ComplicationProviderInfo
import android.support.wearable.complications.IPreviewComplicationDataCallback
import android.support.wearable.complications.IProviderInfoService
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.asApiComplicationData
import androidx.wear.utility.TraceEvent
import kotlinx.coroutines.CompletableDeferred

/**
 * Retrieves [ComplicationProviderInfo] for a watch face's complications.
 *
 *
 * To use construct an instance and call [.retrieveProviderInfo] which returns a [ ].
 *
 *
 * Further calls to [.retrieveProviderInfo] may be made using the same instance of this
 * class, but [.close] must be called when it is no longer needed. Once release has been
 * called, further retrieval attempts will fail.
 */
public class ProviderInfoRetriever : AutoCloseable {
    /** Results for [.retrieveProviderInfo]. */
    public class ProviderInfo internal constructor(
        /** The id for the complication, as provided to [retrieveProviderInfo].  */
        public val watchFaceComplicationId: Int,

        /**
         * Details of the provider for that complication, or `null` if no provider is currently
         * configured.
         */
        public val info: ComplicationProviderInfo?
    )

    private inner class ProviderInfoServiceConnection : ServiceConnection {
        @SuppressLint("SyntheticAccessor")
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            deferredService.complete(IProviderInfoService.Stub.asInterface(service))
        }

        @SuppressLint("SyntheticAccessor")
        override fun onServiceDisconnected(name: ComponentName) {
            deferredService.completeExceptionally(ServiceDisconnectedException())
        }
    }

    @SuppressLint("SyntheticAccessor")
    private val serviceConnection: ServiceConnection = ProviderInfoServiceConnection()
    private var context: Context? = null
    private val deferredService = CompletableDeferred<IProviderInfoService>()

    /** @param context the current context */
    public constructor(context: Context) {
        this.context = context
        val intent = Intent(ACTION_GET_COMPLICATION_CONFIG)
        intent.setPackage(PROVIDER_INFO_SERVICE_PACKAGE)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Exception thrown if the service disconnects. */
    public class ServiceDisconnectedException : Exception()

    /**
     * @hide
     */
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public constructor(service: IProviderInfoService) {
        deferredService.complete(service)
    }

    /**
     * Requests [ComplicationProviderInfo] for the specified complication ids on the specified
     * watch face. When the info is received, the listener will receive a callback for each id.
     * These callbacks will occur on the main thread.
     *
     *
     * This will only work if the package of the current app is the same as the package of the
     * specified watch face.
     *
     * @param watchFaceComponent the ComponentName of the WatchFaceService for which info is
     * being requested
     * @param watchFaceComplicationIds ids of the complications that info is being requested for
     * @return The requested provider info. If the look up fails null will be returned
     * @throws [ServiceDisconnectedException] if the service disconnected during the call.
     */
    @Throws(ServiceDisconnectedException::class)
    public suspend fun retrieveProviderInfo(
        watchFaceComponent: ComponentName,
        watchFaceComplicationIds: IntArray
    ): Array<ProviderInfo>? = TraceEvent("ProviderInfoRetriever.retrieveProviderInfo").use {
        awaitDeferredService().getProviderInfos(
            watchFaceComponent, watchFaceComplicationIds
        )?.mapIndexed { index, info ->
            ProviderInfo(watchFaceComplicationIds[index], info)
        }?.toTypedArray()
    }

    /**
     * Requests preview [ComplicationData] for a provider [ComponentName] and
     * [ComplicationType].
     *
     * @param providerComponent The [ComponentName] of the complication provider from which
     * preview data is requested.
     * @param complicationType The requested [ComplicationType] for the preview data.
     * @return The preview [ComplicationData] or `null` if the provider component doesn't exist, or
     * if it doesn't support complicationType, or if the remote service doesn't support this API.
     * @throws [ServiceDisconnectedException] if the service disconnected during the call.
     */
    @Throws(ServiceDisconnectedException::class)
    @RequiresApi(Build.VERSION_CODES.R)
    public suspend fun requestPreviewComplicationData(
        providerComponent: ComponentName,
        complicationType: ComplicationType
    ): ComplicationData? = TraceEvent(
        "ProviderInfoRetriever.requestPreviewComplicationData"
    ).use {
        val service = awaitDeferredService()
        if (service.apiVersion < 1) {
            return null
        }
        val result = CompletableDeferred<ComplicationData?>()
        val deathObserver = IBinder.DeathRecipient {
            result.completeExceptionally(ServiceDisconnectedException())
        }
        service.asBinder().linkToDeath(deathObserver, 0)
        if (!service.requestPreviewComplicationData(
                providerComponent,
                complicationType.asWireComplicationType(),
                object : IPreviewComplicationDataCallback.Stub() {
                    override fun updateComplicationData(
                        data: android.support.wearable.complications.ComplicationData?
                    ) {
                        service.asBinder().unlinkToDeath(deathObserver, 0)
                        result.complete(data?.asApiComplicationData())
                    }
                }
            )
        ) {
            service.asBinder().unlinkToDeath(deathObserver, 0)
            return null
        }
        return result.await()
    }

    private suspend fun awaitDeferredService(): IProviderInfoService =
        TraceEvent("ProviderInfoRetriever.awaitDeferredService").use {
            deferredService.await()
        }

    /**
     * Releases the connection to the complication system used by this class. This must
     * be called when the retriever is no longer needed.
     *
     *
     * Any outstanding or subsequent futures returned by [.retrieveProviderInfo] will
     * resolve with null.
     *
     * This class implements the Java `AutoClosable` interface and
     * may be used with try-with-resources.
     */
    override fun close() {
        context?.unbindService(serviceConnection)
    }

    private companion object {
        /** The package of the service that supplies provider info.  */
        private const val PROVIDER_INFO_SERVICE_PACKAGE = "com.google.android.wearable.app"
        private const val ACTION_GET_COMPLICATION_CONFIG =
            "android.support.wearable.complications.ACTION_GET_COMPLICATION_CONFIG"
    }
}
