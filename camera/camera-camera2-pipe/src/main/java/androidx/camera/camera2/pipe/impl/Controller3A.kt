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

package androidx.camera.camera2.pipe.impl

import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER_CANCEL
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER_START
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.MeteringRectangle
import androidx.annotation.GuardedBy
import androidx.camera.camera2.pipe.AeMode
import androidx.camera.camera2.pipe.AfMode
import androidx.camera.camera2.pipe.AwbMode
import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.CameraGraph.Constants3A.FRAME_NUMBER_INVALID
import androidx.camera.camera2.pipe.Lock3ABehavior
import androidx.camera.camera2.pipe.Result3A
import androidx.camera.camera2.pipe.Status3A
import androidx.camera.camera2.pipe.impl.Log.debug
import androidx.camera.camera2.pipe.shouldUnlockAe
import androidx.camera.camera2.pipe.shouldUnlockAf
import androidx.camera.camera2.pipe.shouldUnlockAwb
import androidx.camera.camera2.pipe.shouldWaitForAeToConverge
import androidx.camera.camera2.pipe.shouldWaitForAfToConverge
import androidx.camera.camera2.pipe.shouldWaitForAwbToConverge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel

/**
 * This class implements the 3A methods of [CameraGraphSessionImpl].
 */
class Controller3A(
    private val graphProcessor: GraphProcessor,
    private val graphState3A: GraphState3A,
    private val graphListener3A: Listener3A
) {
    companion object {
        private val aeConvergedStateList = listOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
            CaptureResult.CONTROL_AE_STATE_LOCKED
        )

        private val awbConvergedStateList = listOf(
            CaptureResult.CONTROL_AWB_STATE_CONVERGED,
            CaptureResult.CONTROL_AWB_STATE_LOCKED
        )

        private val afConvergedStateList = listOf(
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
        )

        private val aeLockedStateList = listOf(CaptureResult.CONTROL_AE_STATE_LOCKED)

        private val awbLockedStateList = listOf(CaptureResult.CONTROL_AWB_STATE_LOCKED)

        private val afLockedStateList = listOf(
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
        )

        val parameterForAfTriggerStart = mapOf<CaptureRequest.Key<*>, Any>(
            CONTROL_AF_TRIGGER to CONTROL_AF_TRIGGER_START
        )

        val parameterForAfTriggerCancel = mapOf<CaptureRequest.Key<*>, Any>(
            CONTROL_AF_TRIGGER to CONTROL_AF_TRIGGER_CANCEL
        )

        private val result3ASubmitFailed = Result3A(FRAME_NUMBER_INVALID, Status3A.SUBMIT_FAILED)
    }

    // Keep track of the result associated with latest call to update3A. If update3A is called again
    // and the current result is not complete, we will cancel the current result.
    @GuardedBy("this")
    private var lastUpdate3AResult: Deferred<Result3A>? = null

    fun update3A(
        aeMode: AeMode? = null,
        afMode: AfMode? = null,
        awbMode: AwbMode? = null,
        aeRegions: List<MeteringRectangle>? = null,
        afRegions: List<MeteringRectangle>? = null,
        awbRegions: List<MeteringRectangle>? = null
    ): Deferred<Result3A> {
        // Add the listener to a global pool of 3A listeners to monitor the state change to the
        // desired one.
        val listener = createListenerFor3AParams(aeMode, afMode, awbMode)
        graphListener3A.addListener(listener)

        // Update the 3A state of the graph. This will make sure then when GraphProcessor builds
        // the next request it will apply the 3A parameters corresponding to the updated 3A state
        // to the request.
        graphState3A.update(aeMode, afMode, awbMode, aeRegions, afRegions, awbRegions, null, null)
        // Try submitting a new repeating request with the 3A parameters corresponding to the new
        // 3A state and corresponding listeners.
        graphProcessor.invalidate()

        val result = listener.getDeferredResult()
        synchronized(this) {
            lastUpdate3AResult?.cancel("A newer update3A call initiated.")
            lastUpdate3AResult = result
        }
        return result
    }

    suspend fun submit3A(
        aeMode: AeMode? = null,
        afMode: AfMode? = null,
        awbMode: AwbMode? = null,
        aeRegions: List<MeteringRectangle>? = null,
        afRegions: List<MeteringRectangle>? = null,
        awbRegions: List<MeteringRectangle>? = null
    ): Deferred<Result3A> {
        // Add the listener to a global pool of 3A listeners to monitor the state change to the
        // desired one.
        val listener = createListenerFor3AParams(aeMode, afMode, awbMode)
        graphListener3A.addListener(listener)

        val extra3AParams = mutableMapOf<CaptureRequest.Key<*>, Any>()
        aeMode?.let { extra3AParams.put(CaptureRequest.CONTROL_AE_MODE, it.value) }
        afMode?.let { extra3AParams.put(CaptureRequest.CONTROL_AF_MODE, it.value) }
        awbMode?.let { extra3AParams.put(CaptureRequest.CONTROL_AWB_MODE, it.value) }
        aeRegions?.let {
            extra3AParams.put(
                CaptureRequest.CONTROL_AE_REGIONS,
                it.toTypedArray()
            )
        }
        afRegions?.let {
            extra3AParams.put(
                CaptureRequest.CONTROL_AF_REGIONS,
                it.toTypedArray()
            )
        }
        awbRegions?.let {
            extra3AParams.put(
                CaptureRequest.CONTROL_AWB_REGIONS,
                it.toTypedArray()
            )
        }

        if (!graphProcessor.submit(extra3AParams)) {
            graphListener3A.removeListener(listener)
            return CompletableDeferred(result3ASubmitFailed)
        }
        return listener.getDeferredResult()
    }

    /**
     * Given the desired lock behaviors for ae, af and awb, this method, (a) first unlocks them and
     * wait for them to converge, and then (b) locks them.
     *
     * (a) In this step, as needed, we first send a single request with 'af trigger = cancel' to
     * unlock af, and then a repeating request to unlock ae and awb. We suspend till we receive a
     * response from the camera that each of the ae, af awb are converged.
     * (b) In this step, as needed, we submit a repeating request to lock ae and awb, and then a
     * single request to lock af by setting 'af trigger = start'. Once these requests are submitted
     * we don't wait further and immediately return a Deferred<Result3A> which gets completed when
     * the capture result with correct lock states for ae, af and awb is received.
     *
     * If we received an error when submitting any of the above requests or if waiting for the
     * desired 3A state times out then we return early with the appropriate status code.
     *
     * Note: the frameLimit and timeLimitNs applies to each of the above steps (a) and (b) and not
     * as a whole for the whole lock3A method. Thus, in the worst case this method including the
     * completion of returned Deferred<Result3A> can take 2 * min(time equivalent of frameLimit,
     * timeLimit) to complete
     */
    suspend fun lock3A(
        aeLockBehavior: Lock3ABehavior? = null,
        afLockBehavior: Lock3ABehavior? = null,
        awbLockBehavior: Lock3ABehavior? = null,
        frameLimit: Int = CameraGraph.DEFAULT_FRAME_LIMIT,
        timeLimitMsNs: Long? = CameraGraph.DEFAULT_TIME_LIMIT_NS
    ): Deferred<Result3A> {
        // If we explicitly need to unlock af first before proceeding to lock it, we need to send
        // a single request with TRIGGER = TRIGGER_CANCEL so that af can start a fresh scan.
        if (afLockBehavior.shouldUnlockAf()) {
            debug { "lock3A - sending a request to unlock af first." }
            if (!graphProcessor.submit(parameterForAfTriggerCancel)) {
                return CompletableDeferred(result3ASubmitFailed)
            }
        }

        // As needed unlock ae, awb and wait for ae, af and awb to converge.
        if (aeLockBehavior.shouldWaitForAeToConverge() ||
            afLockBehavior.shouldWaitForAfToConverge() ||
            awbLockBehavior.shouldWaitForAwbToConverge()
        ) {
            val converged3AExitConditions = createConverged3AExitConditions(
                aeLockBehavior.shouldWaitForAeToConverge(),
                afLockBehavior.shouldWaitForAfToConverge(),
                awbLockBehavior.shouldWaitForAwbToConverge()
            )
            val listener = Result3AStateListenerImpl(
                converged3AExitConditions,
                frameLimit,
                timeLimitMsNs
            )
            graphListener3A.addListener(listener)

            // If we have to explicitly unlock ae, awb, then update the 3A state of the camera
            // graph. This is because ae, awb lock values should stay as part of repeating
            // request to the camera device. For af we need only one single request to trigger it,
            // leaving it unset in the subsequent requests to the camera device will not affect the
            // previously sent af trigger.
            val aeLockValue = if (aeLockBehavior.shouldUnlockAe()) false else null
            val awbLockValue = if (awbLockBehavior.shouldUnlockAwb()) false else null
            if (aeLockValue != null || awbLockValue != null) {
                debug { "lock3A - setting aeLock=$aeLockValue, awbLock=$awbLockValue" }
                graphState3A.update(
                    aeLock = aeLockValue,
                    awbLock = awbLockValue
                )
            }
            graphProcessor.invalidate()

            debug {
                """lock3A - waiting for
                ${(if (aeLockBehavior.shouldWaitForAeToConverge()) " ae" else "")}
                ${(if (afLockBehavior.shouldWaitForAfToConverge()) " af" else "")}
                ${(if (awbLockBehavior.shouldWaitForAwbToConverge()) " awb" else "")}
                to converge before locking them."""
            }
            val result = listener.getDeferredResult().await()
            debug {
                "lock3A - converged at frame number=${result.frameNumber.value}, status=${result
                    .status}"
            }
            // Return immediately if we encounter an error when unlocking and waiting for
            // convergence.
            if (result.status != Status3A.OK) {
                return CompletableDeferred(result)
            }
        }

        return lock3ANow(aeLockBehavior, afLockBehavior, awbLockBehavior, frameLimit, timeLimitMsNs)
    }

    private suspend fun lock3ANow(
        aeLockBehavior: Lock3ABehavior?,
        afLockBehavior: Lock3ABehavior?,
        awbLockBehavior: Lock3ABehavior?,
        frameLimit: Int?,
        timeLimitMsNs: Long?
    ): Deferred<Result3A> {
        val finalAeLockValue = if (aeLockBehavior == null) null else true
        val finalAwbLockValue = if (awbLockBehavior == null) null else true
        val locked3AExitConditions = createLocked3AExitConditions(
            finalAeLockValue != null,
            afLockBehavior != null,
            finalAwbLockValue != null
        )

        var resultForLocked: Deferred<Result3A>? = null
        if (locked3AExitConditions.isNotEmpty()) {
            val listener = Result3AStateListenerImpl(
                locked3AExitConditions,
                frameLimit,
                timeLimitMsNs
            )
            graphListener3A.addListener(listener)
            graphState3A.update(aeLock = finalAeLockValue, awbLock = finalAwbLockValue)
            debug {
                "lock3A - submitting request with aeLock=$finalAeLockValue , " +
                    "awbLock=$finalAwbLockValue"
            }
            graphProcessor.invalidate()
            resultForLocked = listener.getDeferredResult()
        }

        if (afLockBehavior == null) {
            return resultForLocked!!
        }

        debug { "lock3A - submitting a request to lock af." }
        if (!graphProcessor.submit(parameterForAfTriggerStart)) {
            // TODO(sushilnath@): Change the error code to a more specific code so it's clear
            // that one of the request in sequence of requests failed and the caller should
            // unlock 3A to bring the 3A system to an initial state and then try again if they
            // want to. The other option is to reset or restore the 3A state here.
            return CompletableDeferred(result3ASubmitFailed)
        }
        return resultForLocked!!
    }

    private fun createConverged3AExitConditions(
        waitForAeToConverge: Boolean,
        waitForAfToConverge: Boolean,
        waitForAwbToConverge: Boolean
    ): Map<CaptureResult.Key<*>, List<Any>> {
        if (
            !waitForAeToConverge && !waitForAfToConverge && !waitForAwbToConverge
        ) {
            return mapOf()
        }
        val exitConditionMapForConverged = mutableMapOf<CaptureResult.Key<*>, List<Any>>()
        if (waitForAeToConverge) {
            exitConditionMapForConverged[CaptureResult.CONTROL_AE_STATE] = aeConvergedStateList
        }
        if (waitForAwbToConverge) {
            exitConditionMapForConverged[CaptureResult.CONTROL_AWB_STATE] = awbConvergedStateList
        }
        if (waitForAfToConverge) {
            exitConditionMapForConverged[CaptureResult.CONTROL_AF_STATE] = afConvergedStateList
        }
        return exitConditionMapForConverged
    }

    private fun createLocked3AExitConditions(
        waitForAeToLock: Boolean,
        waitForAfToLock: Boolean,
        waitForAwbToLock: Boolean
    ): Map<CaptureResult.Key<*>, List<Any>> {
        if (!waitForAeToLock && !waitForAfToLock && !waitForAwbToLock) {
            return mapOf()
        }
        val exitConditionMapForLocked = mutableMapOf<CaptureResult.Key<*>, List<Any>>()
        if (waitForAeToLock) {
            exitConditionMapForLocked[CaptureResult.CONTROL_AE_STATE] = aeLockedStateList
        }
        if (waitForAfToLock) {
            exitConditionMapForLocked[CaptureResult.CONTROL_AF_STATE] = afLockedStateList
        }
        if (waitForAwbToLock) {
            exitConditionMapForLocked[CaptureResult.CONTROL_AWB_STATE] = awbLockedStateList
        }
        return exitConditionMapForLocked
    }

    // We create a map for the 3A modes and the desired values and leave out the keys
    // corresponding to the metering regions. The reason being the camera framework can chose to
    // crop or modify the metering regions as per its constraints. So when we receive at least
    // one capture result corresponding to this request it is assumed that the framework has
    // applied the desired metering regions to the best of its judgement, and we don't need an
    // exact match between the metering regions sent in the capture request and the metering
    // regions received from the camera device.
    private fun createListenerFor3AParams(
        aeMode: AeMode?,
        afMode: AfMode?,
        awbMode: AwbMode?
    ): Result3AStateListenerImpl {
        val resultModesMap = mutableMapOf<CaptureResult.Key<*>, List<Any>>()
        aeMode?.let { resultModesMap.put(CaptureResult.CONTROL_AE_MODE, listOf(it.value)) }
        afMode?.let { resultModesMap.put(CaptureResult.CONTROL_AF_MODE, listOf(it.value)) }
        awbMode?.let { resultModesMap.put(CaptureResult.CONTROL_AWB_MODE, listOf(it.value)) }
        return Result3AStateListenerImpl(resultModesMap.toMap())
    }
}