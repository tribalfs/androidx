/*
 * Copyright 2022 The Android Open Source Project
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
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
package androidx.work.testing

import androidx.annotation.GuardedBy
import androidx.annotation.RestrictTo
import androidx.work.Clock
import androidx.work.Worker
import androidx.work.impl.Scheduler
import androidx.work.impl.StartStopTokens
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkLauncher
import androidx.work.impl.model.WorkSpec
import androidx.work.impl.model.WorkSpecDao
import androidx.work.impl.model.generationalId
import java.util.UUID

/**
 * A test scheduler that schedules unconstrained, non-timed workers. It intentionally does
 * not acquire any WakeLocks, instead trying to brute-force them as time allows before the process
 * gets killed.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class TestScheduler(
    private val workDatabase: WorkDatabase,
    private val launcher: WorkLauncher,
    private val clock: Clock
) : Scheduler, TestDriver {
    @GuardedBy("lock")
    private val pendingWorkStates = mutableMapOf<String, InternalWorkState>()
    private val lock = Any()
    private val startStopTokens = StartStopTokens()

    override fun hasLimitedSchedulingSlots() = true

    override fun schedule(vararg workSpecs: WorkSpec) {
        if (workSpecs.isEmpty()) {
            return
        }
        val toSchedule = mutableMapOf<WorkSpec, InternalWorkState>()
        synchronized(lock) {
            workSpecs.forEach {
                val oldState = pendingWorkStates[it.id] ?: InternalWorkState()
                val state = oldState.copy(isScheduled = true)
                pendingWorkStates[it.id] = state
                toSchedule[it] = state
            }
        }
        toSchedule.forEach { (spec, state) ->
            // don't even try to run a worker that WorkerWrapper won't execute anyway.
            // similar to logic in WorkerWrapper
            if (spec.isBackedOff && spec.calculateNextRunTime() > clock.currentTimeMillis()) {
                return@forEach
            }
            scheduleInternal(spec, state)
        }
    }

    // cancel called in two situations when:
    // 1. a worker was cancelled via workManager.cancelWorkById
    // 2. a worker finished (no matter successfully or not), see comment in
    // Schedulers.registerRescheduling
    override fun cancel(workSpecId: String) {
        val tokens = startStopTokens.remove(workSpecId)
        tokens.forEach { launcher.stopWork(it) }
    }

    /**
     * Tells the [TestScheduler] to pretend that all constraints on the [Worker] with
     * the given `workSpecId` are met.
     *
     * @param workSpecId The [Worker]'s id
     * @throws IllegalArgumentException if `workSpecId` is not enqueued
     */
    override fun setAllConstraintsMet(workSpecId: UUID) {
        val id = workSpecId.toString()
        val spec = loadSpec(id)
        val state: InternalWorkState
        synchronized(lock) {
            val oldState = pendingWorkStates[id] ?: InternalWorkState(initialDelayMet = false)
            state = oldState.copy(constraintsMet = true)
            pendingWorkStates[id] = state
        }
        scheduleInternal(spec, state)
    }

    /**
     * Tells the [TestScheduler] to pretend that the initial delay on the [Worker] with
     * the given `workSpecId` are met.
     *
     * @param workSpecId The [Worker]'s id
     * @throws IllegalArgumentException if `workSpecId` is not enqueued
     */
    override fun setInitialDelayMet(workSpecId: UUID) {
        val id = workSpecId.toString()
        val state: InternalWorkState
        val spec = loadSpec(id)
        synchronized(lock) {
            val oldState = pendingWorkStates[id] ?: InternalWorkState()
            state = oldState.copy(initialDelayMet = true)
            pendingWorkStates[id] = state
        }
        scheduleInternal(spec, state)
    }

    /**
     * Tells the [TestScheduler] to pretend that the periodic delay on the [Worker] with
     * the given `workSpecId` are met.
     *
     * @param workSpecId The [Worker]'s id
     * @throws IllegalArgumentException if `workSpecId` is not enqueued
     */
    override fun setPeriodDelayMet(workSpecId: UUID) {
        val id = workSpecId.toString()
        val spec = loadSpec(id)
        if (!spec.isPeriodic) throw IllegalArgumentException("Work with id $id isn't periodic!")

        val state: InternalWorkState
        synchronized(lock) {
            val oldState = pendingWorkStates[id] ?: InternalWorkState()
            state = oldState.copy(periodDelayMet = true)
            pendingWorkStates[id] = state
        }
        scheduleInternal(spec, state)
    }

    private fun scheduleInternal(spec: WorkSpec, state: InternalWorkState) {
        val generationalId = spec.generationalId()
        if (isRunnable(spec, state)) {
            val token = synchronized(lock) {
                pendingWorkStates.remove(generationalId.workSpecId)
                startStopTokens.tokenFor(generationalId)
            }
            workDatabase.rewindLastEnqueueTime(spec.id, clock)
            launcher.startWork(token)
        }
    }

    private fun loadSpec(id: String): WorkSpec {
        val workSpec = workDatabase.workSpecDao().getWorkSpec(id)
            ?: throw IllegalArgumentException("Work with id $id is not enqueued!")
        return workSpec
    }
}

internal data class InternalWorkState(
    val constraintsMet: Boolean = false,
    val initialDelayMet: Boolean = false,
    val periodDelayMet: Boolean = false,
    /* means that TestScheduler received this workrequest in schedule(....) function */
    val isScheduled: Boolean = false,
)

internal fun isRunnable(spec: WorkSpec, state: InternalWorkState): Boolean {
    val constraints = !spec.hasConstraints() || state.constraintsMet
    val initialDelay = spec.initialDelay == 0L || state.initialDelayMet || !spec.isFirstPeriodicRun
    val periodic = if (spec.isPeriodic) (state.periodDelayMet || spec.isFirstPeriodicRun) else true
    return state.isScheduled && constraints && periodic && initialDelay
}

private val WorkSpec.isFirstPeriodicRun get() = periodCount == 0 && runAttemptCount == 0

private fun WorkDatabase.rewindLastEnqueueTime(id: String, clock: Clock): WorkSpec {
    // We need to pass check that mWorkSpec.calculateNextRunTime() < now
    // so we reset "rewind" enqueue time to pass the check
    // we don't reuse available internalWorkState.mWorkSpec, because it
    // is not update with period_count and last_enqueue_time
    val dao: WorkSpecDao = workSpecDao()
    val workSpec: WorkSpec = dao.getWorkSpec(id)
        ?: throw IllegalStateException("WorkSpec is already deleted from WM's db")
    val now = clock.currentTimeMillis()
    val timeOffset = workSpec.calculateNextRunTime() - now
    if (timeOffset > 0) {
        dao.setLastEnqueueTime(id, workSpec.lastEnqueueTime - timeOffset)
    }
    return dao.getWorkSpec(id)
        ?: throw IllegalStateException("WorkSpec is already deleted from WM's db")
}