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

package androidx.appactions.interaction.service

import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews
import androidx.appactions.interaction.capabilities.core.ExecutionCallback
import androidx.appactions.interaction.capabilities.core.ExecutionResult
import androidx.appactions.interaction.capabilities.core.HostProperties
import androidx.appactions.interaction.capabilities.testing.internal.ArgumentUtils.buildArgs
import androidx.appactions.interaction.capabilities.testing.internal.ArgumentUtils.buildRequestArgs
import androidx.appactions.interaction.capabilities.testing.internal.FakeCallbackInternal
import androidx.appactions.interaction.capabilities.testing.internal.TestingUtils.CB_TIMEOUT
import androidx.appactions.interaction.proto.FulfillmentRequest.Fulfillment.Type.SYNC
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.service.test.R
import androidx.appactions.interaction.service.testing.internal.FakeCapability
import androidx.appactions.interaction.service.testing.internal.FakeCapability.Arguments
import androidx.appactions.interaction.service.testing.internal.FakeCapability.ExecutionSession
import androidx.appactions.interaction.service.testing.internal.FakeCapability.Output
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("deprecation") // For backwards compatibility.
class UiSessionsTest {
    private class SessionList {
        private val sessions = mutableListOf<ExecutionSession>()
        private var index = 0
        val sessionFactory: (hostProperties: HostProperties?) -> ExecutionSession =
            { _ -> sessions[index++] }

        fun addExecutionSessions(vararg session: ExecutionSession) {
            sessions.addAll(session)
        }

        fun reset() {
            sessions.clear()
            index = 0
        }
    }
    private val sessionList = SessionList()
    private val sessionId = "fakeSessionId"
    private val hostProperties =
        HostProperties.Builder().setMaxHostSizeDp(SizeF(300f, 500f)).build()
    private val multiTurnCapability = FakeCapability.CapabilityBuilder()
        .setId("multiTurnCapability")
        .setExecutionSessionFactory(sessionList.sessionFactory).build()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val remoteViewsFactoryId = 123
    private val remoteViews = RemoteViews(context.packageName, R.layout.remote_view)
    private val remoteViewsUiResponse =
        UiResponse.RemoteViewsUiBuilder()
            .setRemoteViews(remoteViews, SizeF(10f, 15f))
            .addRemoteViewsFactory(remoteViewsFactoryId, FakeRemoteViewsFactory())
            .build()

    @After
    fun cleanup() {
        sessionList.reset()
        UiSessions.removeUiCache(sessionId)
    }

    private fun createFakeSessionWithUiResponses(vararg uiResponses: UiResponse): ExecutionSession {
        return object : ExecutionSession {
            override suspend fun onExecute(
                arguments: Arguments,
            ): ExecutionResult<Output> {
                for (uiResponse in uiResponses) {
                    this.updateUi(uiResponse)
                }
                return ExecutionResult.Builder<Output>().build()
            }
        }
    }

    @Test
    fun sessionExtensionMethod_createCache_removeCache() {
        assertThat(UiSessions.getUiCacheOrNull(sessionId)).isNull()

        sessionList.addExecutionSessions(
            createFakeSessionWithUiResponses(remoteViewsUiResponse),
        )
        val session = multiTurnCapability.createSession(sessionId, hostProperties)
        val callback = FakeCallbackInternal(CB_TIMEOUT)
        session.execute(
            buildRequestArgs(
                SYNC,
                "fieldOne",
                "hello",
            ),
            callback,
        )
        callback.receiveResponse()
        val uiCache = UiSessions.getUiCacheOrNull(sessionId)
        assertThat(uiCache).isNotNull()
        assertThat(uiCache?.hasUnreadUiResponse).isTrue()
        assertThat(uiCache?.cachedRemoteViewsInternal?.size).isEqualTo(SizeF(10f, 15f))
        assertThat(uiCache?.cachedRemoteViewsInternal?.remoteViews).isEqualTo(remoteViews)

        // Test removing.
        assertThat(UiSessions.removeUiCache(sessionId)).isTrue()
        assertThat(UiSessions.getUiCacheOrNull(sessionId)).isNull()
    }

    @Test
    fun multipleUpdate_sharesCache() {
        assertThat(UiSessions.getUiCacheOrNull(sessionId)).isNull()
        sessionList.addExecutionSessions(object : ExecutionSession {
            override suspend fun onExecute(
                arguments: Arguments,
            ): ExecutionResult<Output> {
                this.updateUi(remoteViewsUiResponse)

                return ExecutionResult.Builder<Output>().build()
            }
        })
        val session = multiTurnCapability.createSession(sessionId, hostProperties)
        val callback = FakeCallbackInternal(CB_TIMEOUT)
        session.execute(
            buildRequestArgs(
                SYNC,
                "fieldOne",
                "hello",
            ),
            callback,
        )
        callback.receiveResponse()
        val uiCache = UiSessions.getUiCacheOrNull(sessionId)
        assertThat(uiCache).isNotNull()
        assertThat(uiCache?.hasUnreadUiResponse).isTrue()
        assertThat(uiCache?.cachedRemoteViewsInternal?.size).isEqualTo(SizeF(10f, 15f))
        assertThat(uiCache?.cachedRemoteViewsInternal?.remoteViews).isEqualTo(remoteViews)
    }

    @Test
    fun multipleSession_haveTheirOwnCache() {
        val sessionId1 = "fakeSessionId1"
        val sessionId2 = "fakeSessionId2"
        sessionList.addExecutionSessions(
            object : ExecutionSession {
                override suspend fun onExecute(
                    arguments: Arguments,
                ): ExecutionResult<Output> {
                    this.updateUi(remoteViewsUiResponse)
                    return ExecutionResult.Builder<Output>().build()
                }
            },
            object : ExecutionSession {
                override suspend fun onExecute(
                    arguments: Arguments,
                ): ExecutionResult<Output> {
                    this.updateUi(remoteViewsUiResponse)
                    return ExecutionResult.Builder<Output>().build()
                }
            },
        )
        val session1 = multiTurnCapability.createSession(sessionId1, hostProperties)
        val session2 = multiTurnCapability.createSession(sessionId2, hostProperties)

        val callback1 = FakeCallbackInternal(CB_TIMEOUT)
        val callback2 = FakeCallbackInternal(CB_TIMEOUT)

        session1.execute(
            buildRequestArgs(
                SYNC,
                "fieldOne",
                "hello",
            ),
            callback1,
        )
        session2.execute(
            buildRequestArgs(
                SYNC,
                "fieldOne",
                "hello",
            ),
            callback2,
        )
        callback1.receiveResponse()
        callback2.receiveResponse()

        val uiCache1 = UiSessions.getUiCacheOrNull(sessionId1)
        assertThat(uiCache1).isNotNull()
        assertThat(uiCache1?.hasUnreadUiResponse).isTrue()
        assertThat(uiCache1?.cachedRemoteViewsInternal?.remoteViews).isEqualTo(remoteViews)

        val uiCache2 = UiSessions.getUiCacheOrNull(sessionId2)
        assertThat(uiCache2).isNotNull()
        assertThat(uiCache2?.hasUnreadUiResponse).isTrue()
        assertThat(uiCache2?.cachedRemoteViewsInternal?.remoteViews).isEqualTo(remoteViews)

        // Assert that UiCache2 response still marked unread.
        uiCache1?.resetUnreadUiResponse()
        assertThat(uiCache2?.hasUnreadUiResponse).isTrue()

        UiSessions.removeUiCache(sessionId1)
        UiSessions.removeUiCache(sessionId2)
    }

    @Test
    fun executionCallback_hasUpdateUiExtension() {
        assertThat(UiSessions.getUiCacheOrNull(sessionId)).isNull()
        val oneShotCapability = FakeCapability.CapabilityBuilder().setId(
            "oneShotCapability",
        ).setExecutionCallback(object : ExecutionCallback<Arguments, Output> {
            override suspend fun onExecute(arguments: Arguments): ExecutionResult<Output> {
                this.updateUi(remoteViewsUiResponse)
                return ExecutionResult.Builder<Output>().build()
            }
        }).build()
        val session = oneShotCapability.createSession(
            sessionId,
            hostProperties,
        )
        val callback = FakeCallbackInternal(CB_TIMEOUT)
        session.execute(
            buildArgs(
                mapOf(
                    "fieldOne" to ParamValue.newBuilder().setStringValue("hello").build(),
                ),
            ),
            callback,
        )
        callback.receiveResponse()
        val uiCache = UiSessions.getUiCacheOrNull(sessionId)
        assertThat(uiCache).isNotNull()
        assertThat(uiCache?.hasUnreadUiResponse).isTrue()
        assertThat(uiCache?.cachedRemoteViewsInternal?.size).isEqualTo(SizeF(10f, 15f))
        assertThat(uiCache?.cachedRemoteViewsInternal?.remoteViews).isEqualTo(remoteViews)
    }
}
