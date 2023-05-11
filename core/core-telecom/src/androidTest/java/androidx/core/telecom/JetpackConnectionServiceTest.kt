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
import android.telecom.Connection
import android.telecom.ConnectionRequest
import androidx.annotation.RequiresApi
import androidx.core.telecom.TestUtils.Companion.TEST_CALL_ATTRIB_NAME
import androidx.core.telecom.TestUtils.Companion.TEST_CALL_ATTRIB_NUMBER
import androidx.core.telecom.internal.CallChannels
import androidx.core.telecom.internal.JetpackConnectionService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@RequiresApi(VERSION_CODES.O)
@SdkSuppress(minSdkVersion = VERSION_CODES.O /* api=26 */)
class JetpackConnectionServiceTest : BaseTelecomTest() {
    private val callChannels = CallChannels()

    @After
    fun onDestroy() {
        super.onDestroyBase()
        callChannels.closeAllChannels()
    }

    /**
     * Ensure an outgoing Connection object has its properties set before sending it off to the
     * platform.  The properties should reflect everything that is set in CallAttributes.
     */
    @SmallTest
    @Test
    fun testConnectionServicePropertiesAreSet_outgoingCall() {
        // create the CallAttributes
        val attributes = TestUtils.createCallAttributes(
            CallAttributesCompat.DIRECTION_OUTGOING,
            mPackagePhoneAccountHandle
        )
        // simulate the connection being created
        val connection = mConnectionService.createSelfManagedConnection(
            createConnectionRequest(attributes),
            CallAttributesCompat.DIRECTION_OUTGOING
        )
        // verify / assert connection properties
        verifyConnectionPropertiesBasics(connection)
        assertEquals(Connection.STATE_DIALING, connection!!.state)
    }

    /**
     * Ensure an incoming Connection object has its properties set before sending it off to the
     * platform.  The properties should reflect everything that is set in CallAttributes.
     */
    @SmallTest
    @Test
    fun testConnectionServicePropertiesAreSet_incomingCall() {
        // create the CallAttributes
        val attributes = TestUtils.createCallAttributes(
            CallAttributesCompat.DIRECTION_INCOMING,
            mPackagePhoneAccountHandle
        )
        // simulate the connection being created
        val connection = mConnectionService.createSelfManagedConnection(
            createConnectionRequest(attributes),
            CallAttributesCompat.DIRECTION_INCOMING
        )
        // verify / assert connection properties
        verifyConnectionPropertiesBasics(connection)
        assertEquals(Connection.STATE_RINGING, connection!!.state)
    }

    private fun verifyConnectionPropertiesBasics(connection: Connection?) {
        // assert it's not null
        assertNotNull(connection)
        // unwrap for testing
        val unwrappedConnection = connection!!
        // assert all the properties are the same
        assertEquals(TEST_CALL_ATTRIB_NAME, unwrappedConnection.callerDisplayName)
        assertEquals(TEST_CALL_ATTRIB_NUMBER, unwrappedConnection.address)
        assertEquals(
            Connection.CAPABILITY_HOLD,
            unwrappedConnection.connectionCapabilities
                and Connection.CAPABILITY_HOLD
        )
        assertEquals(
            Connection.CAPABILITY_SUPPORT_HOLD,
            unwrappedConnection.connectionCapabilities
                and Connection.CAPABILITY_SUPPORT_HOLD
        )
        assertEquals(0, JetpackConnectionService.mPendingConnectionRequests.size)
    }

    private fun createConnectionRequest(callAttributesCompat: CallAttributesCompat):
        ConnectionRequest {
        // wrap in PendingRequest
        val pr = JetpackConnectionService.PendingConnectionRequest(
            callAttributesCompat, callChannels, mWorkerContext, null
        )
        // add to the list of pendingRequests
        JetpackConnectionService.mPendingConnectionRequests.add(pr)
        // create a ConnectionRequest
        return ConnectionRequest(mPackagePhoneAccountHandle, TEST_CALL_ATTRIB_NUMBER, null)
    }
}