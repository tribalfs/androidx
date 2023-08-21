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

package androidx.core.telecom.test.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import androidx.core.telecom.internal.JetpackConnectionService
import androidx.core.telecom.internal.utils.Utils
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.testutils.TestExecutor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert
import org.junit.Before

@RequiresApi(Build.VERSION_CODES.O)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O /* api=26 */)
abstract class BaseTelecomTest {
    val L_TAG = "BaseTelecomTest"
    val mContext: Context = ApplicationProvider.getApplicationContext()
    val mWorkerExecutor = TestExecutor()
    val mWorkerContext: CoroutineContext = mWorkerExecutor.asCoroutineDispatcher()

    lateinit var mPreviousDefaultDialer: String
    lateinit var mTelecomManager: TelecomManager
    lateinit var mCallsManager: CallsManager
    lateinit var mAudioManager: AudioManager
    lateinit var mPackagePhoneAccountHandle: PhoneAccountHandle
    internal lateinit var mConnectionService: JetpackConnectionService

    @Before
    fun setUpBase() {
        Log.i(L_TAG, "setUpBase: in function")
        mTelecomManager = mContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mCallsManager = CallsManager(mContext)
        mConnectionService = mCallsManager.mConnectionService
        mCallsManager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        mPackagePhoneAccountHandle = mCallsManager.getPhoneAccountHandleForPackage()
        mPreviousDefaultDialer = TestUtils.getDefaultDialer()
        TestUtils.setDefaultDialer(TestUtils.TEST_PACKAGE)
        maybeCleanupStuckCalls()
        Utils.resetUtils()
        TestUtils.resetCallbackConfigs()
    }

    @After
    fun onDestroyBase() {
        Log.i(L_TAG, "onDestroyBase: in function")
        Utils.resetUtils()
        TestUtils.resetCallbackConfigs()
        TestUtils.setDefaultDialer(mPreviousDefaultDialer)
        maybeCleanupStuckCalls()
    }

    fun setUpV2Test() {
        Log.i(L_TAG, "setUpV2Test: core-telecom w/ [V2] APIs")
        Utils.setUtils(TestUtils.mV2Build)
        mCallsManager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        logTelecomState()
    }

    fun setUpBackwardsCompatTest() {
        Log.i(L_TAG, "setUpBackwardsCompatTest: core-telecom w/ [ConnectionService] APIs")
        Utils.setUtils(TestUtils.mBackwardsCompatBuild)
        mCallsManager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        logTelecomState()
    }

    private fun logTelecomState() {
        val telecomDumpsysString = TestUtils.runShellCommand(TestUtils.COMMAND_DUMP_TELECOM)
        val isInCallXmCallsDump = isInCallFromTelDumpsys(telecomDumpsysString)

        Log.i(L_TAG, "logTelecomState: " +
            "hasTelecomFeature=[${hasTelecomFeature()}]," +
            "isInCall=[${isInCallXmCallsDump.first}], " +
            "mCalls={${isInCallXmCallsDump.second}}, " +
            "sdkInt=[${Build.VERSION.SDK_INT}], " +
            "phoneAccounts=[${getPhoneAccountsFromTelDumpsys(telecomDumpsysString)}]")
    }

    private fun hasTelecomFeature(): Boolean {
        return mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELECOM)
    }

    private fun maybeCleanupStuckCalls() {
        JetpackConnectionService.mPendingConnectionRequests.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ManagedConnectionService.mPendingConnectionRequests.clear()
        }
        MockInCallService.destroyAllCalls()
        TestUtils.runShellCommand(TestUtils.COMMAND_CLEANUP_STUCK_CALLS)
    }

    private fun isInCallFromTelDumpsys(telecomDumpsysString: String): Pair<Boolean, String> {
        val allCallsText = telecomDumpsysString
            .substringBefore("mCallAudioManager")
            .substringAfter("mCalls:")
        if (allCallsText.contains("Call")) {
            return Pair(true, allCallsText)
        }
        return Pair(false, "")
    }

    private fun getPhoneAccountsFromTelDumpsys(telecomDumpsysString: String): String {
        return telecomDumpsysString
            .substringBefore("Analytics")
            .substringAfter("phoneAccounts:")
    }

    /**
     * This helper requires an asserBlock (a set of assert statements), creates a timer, and
     * either halts execution until all the asserts are completed or times out if the asserts
     * are not completed in time. It's important to do this
     */
    suspend fun assertWithinTimeout_addCall(
        attributes: CallAttributesCompat,
        assertBlock: CallControlScope.() -> (Unit)
    ) {
        Log.i(TestUtils.LOG_TAG, "assertWithinTimeout_addCall")
        var callControlScope: CallControlScope? = null
        try {
            withTimeout(TestUtils.WAIT_ON_ASSERTS_TO_FINISH_TIMEOUT) {
                mCallsManager.addCall(
                    attributes,
                    TestUtils.mOnAnswerLambda,
                    TestUtils.mOnDisconnectLambda,
                    TestUtils.mOnSetActiveLambda,
                    TestUtils.mOnSetInActiveLambda,
                ) {
                    callControlScope = this
                    assertBlock()
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.i(TestUtils.LOG_TAG, "assertWithinTimeout: reached timeout; dumping telecom")
            TestUtils.dumpTelecom()
            callControlScope?.disconnect(DisconnectCause(DisconnectCause.LOCAL, "timeout in test"))
            Assert.fail(TestUtils.VERIFICATION_TIMEOUT_MSG)
        }
    }
}
