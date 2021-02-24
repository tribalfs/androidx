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

package androidx.wear.tiles.connection

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.wear.tiles.ResourcesCallback
import androidx.wear.tiles.ResourcesData
import androidx.wear.tiles.ResourcesRequestData
import androidx.wear.tiles.TileAddEventData
import androidx.wear.tiles.TileCallback
import androidx.wear.tiles.TileData
import androidx.wear.tiles.TileEnterEventData
import androidx.wear.tiles.TileLeaveEventData
import androidx.wear.tiles.TileProvider
import androidx.wear.tiles.TileRemoveEventData
import androidx.wear.tiles.TileRequestData
import androidx.wear.tiles.TilesTestRunner
import androidx.wear.tiles.proto.RequestProto
import androidx.wear.tiles.proto.ResourceProto
import androidx.wear.tiles.proto.TileProto
import androidx.wear.tiles.protobuf.InvalidProtocolBufferException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.lang.IllegalArgumentException

@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(TilesTestRunner::class)
public class TilesConnectionTest {
    public companion object {
        private val TILE_PROVIDER = ComponentName("HelloWorld", "FooBarBaz")
    }

    private lateinit var appContext: Context
    private lateinit var fakeTileProvider: FakeTileProviderService
    private lateinit var fakeCoroutineDispatcher: TestCoroutineDispatcher
    private lateinit var fakeCoroutineScope: TestCoroutineScope
    private lateinit var connectionUnderTest: TilesConnection

    @Before
    public fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        fakeTileProvider = FakeTileProviderService()
        fakeCoroutineDispatcher = TestCoroutineDispatcher()
        fakeCoroutineScope = TestCoroutineScope()

        Shadows.shadowOf(appContext as Application)
            .setComponentNameAndServiceForBindService(TILE_PROVIDER, fakeTileProvider.asBinder())

        connectionUnderTest = TilesConnection(
            appContext, TILE_PROVIDER,
            fakeCoroutineScope, fakeCoroutineDispatcher
        )
    }

    @After
    public fun tearDown() {
        fakeCoroutineDispatcher.advanceUntilIdle()

        fakeCoroutineDispatcher.cleanupTestCoroutines()
        fakeCoroutineScope.cleanupTestCoroutines()
    }

    @Test
    public fun getTileContents_canGetTileContents(): Unit = fakeCoroutineScope.runBlockingTest {
        val expectedTile = TileProto.Tile.newBuilder().setResourcesVersion("5").build()
        fakeTileProvider.returnTile = expectedTile.toByteArray()

        val result = async {
            connectionUnderTest.tileRequest(RequestProto.TileRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.await().toProto()).isEqualTo(expectedTile)
    }

    @Test
    public fun getTileContents_failsIfUnparsableResult(): Unit =
        fakeCoroutineScope.runBlockingTest {
            // Put some random payload in and see if it breaks.
            fakeTileProvider.returnTile = byteArrayOf(127)

            val result = async {
                connectionUnderTest.tileRequest(RequestProto.TileRequest.getDefaultInstance())
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertThat(result.isCompleted).isTrue()
            assertThat(result.getCompletionExceptionOrNull()).isInstanceOf(
                InvalidProtocolBufferException::class.java
            )
        }

    @Test
    public fun getTileContents_failsIfVersionMismatch(): Unit = fakeCoroutineScope.runBlockingTest {
        // Put some random payload in and see if it breaks.
        val expectedTile = TileProto.Tile.newBuilder().setResourcesVersion("5").build()
        fakeTileProvider.returnTile = expectedTile.toByteArray()
        fakeTileProvider.returnTileVersion = -1

        val result = async {
            connectionUnderTest.tileRequest(RequestProto.TileRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.isCompleted).isTrue()
        assertThat(result.getCompletionExceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    public fun getTileContents_failsOnTimeout(): Unit = fakeCoroutineScope.runBlockingTest {
        val expectedTile = TileProto.Tile.newBuilder().setResourcesVersion("5").build()
        fakeTileProvider.returnTile = expectedTile.toByteArray()
        fakeTileProvider.shouldReturnTile = false

        // This has to be dispatched on the correct dispatcher, so we can fully control its timing.
        val result = async(fakeCoroutineDispatcher) {
            connectionUnderTest.tileRequest(RequestProto.TileRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle() // Ensure it actually binds...

        assertThat(result.isCompleted).isFalse()

        fakeCoroutineDispatcher.advanceTimeBy(TilesConnection.TIMEOUT / 2)
        assertThat(result.isCompleted).isFalse()

        fakeCoroutineDispatcher.advanceTimeBy(TilesConnection.TIMEOUT / 2)
        assertThat(result.isCompleted).isTrue()

        assertThat(result.getCompletionExceptionOrNull())
            .isInstanceOf(TimeoutCancellationException::class.java)
    }

    @Test
    public fun getResources_canGetResources(): Unit = fakeCoroutineScope.runBlockingTest {
        val expectedResources = ResourceProto.Resources.newBuilder().setVersion("5").build()
        fakeTileProvider.returnResources = expectedResources.toByteArray()

        val result = async {
            connectionUnderTest.resourcesRequest(RequestProto.ResourcesRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.await().toProto()).isEqualTo(expectedResources)
    }

    @Test
    public fun getResources_failsIfUnparsableResult(): Unit = fakeCoroutineScope.runBlockingTest {
        fakeTileProvider.returnResources = byteArrayOf(127)

        val result = async {
            connectionUnderTest.resourcesRequest(RequestProto.ResourcesRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.isCompleted).isTrue()
        assertThat(result.getCompletionExceptionOrNull()).isInstanceOf(
            InvalidProtocolBufferException::class.java
        )
    }

    @Test
    public fun getResources_failsIfVersionMismatch(): Unit = fakeCoroutineScope.runBlockingTest {
        val expectedResources = ResourceProto.Resources.newBuilder().setVersion("5").build()
        fakeTileProvider.returnResources = expectedResources.toByteArray()
        fakeTileProvider.returnResourcesVersion = -2

        val result = async {
            connectionUnderTest.resourcesRequest(RequestProto.ResourcesRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.isCompleted).isTrue()
        assertThat(result.getCompletionExceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    public fun getResources_failsOnTimeout(): Unit = fakeCoroutineScope.runBlockingTest {
        val expectedResources = ResourceProto.Resources.newBuilder().setVersion("5").build()
        fakeTileProvider.returnResources = expectedResources.toByteArray()
        fakeTileProvider.shouldReturnResources = false

        // This has to be dispatched on the correct dispatcher, so we can fully control its timing.
        val result = async(fakeCoroutineDispatcher) {
            connectionUnderTest.resourcesRequest(RequestProto.ResourcesRequest.getDefaultInstance())
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle() // Ensure it actually binds...

        assertThat(result.isCompleted).isFalse()

        fakeCoroutineDispatcher.advanceTimeBy(TilesConnection.TIMEOUT / 2)
        assertThat(result.isCompleted).isFalse()

        fakeCoroutineDispatcher.advanceTimeBy(TilesConnection.TIMEOUT / 2)
        assertThat(result.isCompleted).isTrue()

        assertThat(result.getCompletionExceptionOrNull())
            .isInstanceOf(TimeoutCancellationException::class.java)
    }

    private class FakeTileProviderService : TileProvider.Stub() {
        var shouldReturnTile = true
        var returnTile = ByteArray(0)
        var returnTileVersion = TileData.VERSION_PROTOBUF

        var shouldReturnResources = true
        var returnResources = ByteArray(0)
        var returnResourcesVersion = ResourcesData.VERSION_PROTOBUF

        override fun getApiVersion(): Int {
            return 5
        }

        override fun onTileRequest(
            id: Int,
            requestData: TileRequestData?,
            callback: TileCallback?
        ) {
            if (shouldReturnTile) {
                callback!!.updateTileData(TileData(returnTile, returnTileVersion))
            }
        }

        override fun onResourcesRequest(
            id: Int,
            requestData: ResourcesRequestData?,
            callback: ResourcesCallback?
        ) {
            if (shouldReturnResources) {
                callback!!.updateResources(ResourcesData(returnResources, returnResourcesVersion))
            }
        }

        override fun onTileAddEvent(requestData: TileAddEventData?) {
            TODO("Not yet implemented")
        }

        override fun onTileRemoveEvent(requestData: TileRemoveEventData?) {
            TODO("Not yet implemented")
        }

        override fun onTileEnterEvent(requestData: TileEnterEventData?) {
            TODO("Not yet implemented")
        }

        override fun onTileLeaveEvent(requestData: TileLeaveEventData?) {
            TODO("Not yet implemented")
        }
    }
}