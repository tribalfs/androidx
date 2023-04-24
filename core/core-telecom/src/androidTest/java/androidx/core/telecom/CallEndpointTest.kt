/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.core.telecom

import android.os.Build.VERSION_CODES
import android.os.ParcelUuid
import android.telecom.CallAudioState
import androidx.annotation.RequiresApi
import androidx.core.telecom.internal.utils.EndpointUtils
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

@RequiresApi(VERSION_CODES.O)
class CallEndpointTest {

    @Test
    fun testCallEndpointConstructor() {
        val name = "Endpoint"
        val type = CallEndpoint.TYPE_EARPIECE
        val identifier = ParcelUuid.fromString(UUID.randomUUID().toString())
        val endpoint = CallEndpoint(name, type, identifier)
        assertEquals(name, endpoint.name)
        assertEquals(type, endpoint.type)
        assertEquals(identifier, endpoint.identifier)
    }

    @Test
    fun testWrappingAudioStateIntoAEndpoint() {
        val state = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, 0)
        val endpoint = EndpointUtils.wrapCallAudioStateIntoCurrentEndpoint(state)
        assertEquals("EARPIECE", endpoint.name)
        assertEquals(CallEndpoint.TYPE_EARPIECE, endpoint.type)
    }

    @Test
    fun testSupportedMask() {
        val supportedRouteMask = CallAudioState.ROUTE_EARPIECE or
            CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_WIRED_HEADSET
        val state = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, supportedRouteMask)
        val endpoints = EndpointUtils.wrapCallAudioStateIntoAvailableEndpoints(state)
        assertEquals(3, endpoints.size)
    }

    @Test
    fun testCallAudioRouteToEndpointTypeMapping() {
        assertEquals(
            CallEndpoint.TYPE_EARPIECE,
            EndpointUtils.mapRouteToType(CallAudioState.ROUTE_EARPIECE)
        )
        assertEquals(
            CallEndpoint.TYPE_SPEAKER,
            EndpointUtils.mapRouteToType(CallAudioState.ROUTE_SPEAKER)
        )
        assertEquals(
            CallEndpoint.TYPE_WIRED_HEADSET,
            EndpointUtils.mapRouteToType(CallAudioState.ROUTE_WIRED_HEADSET)
        )
        assertEquals(
            CallEndpoint.TYPE_BLUETOOTH,
            EndpointUtils.mapRouteToType(CallAudioState.ROUTE_BLUETOOTH)
        )
        assertEquals(
            CallEndpoint.TYPE_STREAMING,
            EndpointUtils.mapRouteToType(CallAudioState.ROUTE_STREAMING)
        )
        assertEquals(CallEndpoint.TYPE_UNKNOWN, EndpointUtils.mapRouteToType(-1))
    }

    @Test
    fun testTypeToRouteMapping() {
        assertEquals(
            CallAudioState.ROUTE_EARPIECE,
            EndpointUtils.mapTypeToRoute(CallEndpoint.TYPE_EARPIECE)
        )
        assertEquals(
            CallAudioState.ROUTE_SPEAKER,
            EndpointUtils.mapTypeToRoute(CallEndpoint.TYPE_SPEAKER)
        )
        assertEquals(
            CallAudioState.ROUTE_BLUETOOTH,
            EndpointUtils.mapTypeToRoute(CallEndpoint.TYPE_BLUETOOTH)
        )
        assertEquals(
            CallAudioState.ROUTE_WIRED_HEADSET,
            EndpointUtils.mapTypeToRoute(CallEndpoint.TYPE_WIRED_HEADSET)
        )
        assertEquals(
            CallAudioState.ROUTE_STREAMING,
            EndpointUtils.mapTypeToRoute(CallEndpoint.TYPE_STREAMING)
        )
        assertEquals(
            CallAudioState.ROUTE_EARPIECE,
            EndpointUtils.mapTypeToRoute(-1)
        )
    }
}