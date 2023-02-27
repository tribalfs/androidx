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

package androidx.work.impl.utils

import android.os.Build
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.impl.Scheduler
import androidx.work.impl.Schedulers
import androidx.work.impl.WorkContinuationImpl
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.WorkManagerImpl.MAX_PRE_JOB_SCHEDULER_API_LEVEL
import androidx.work.impl.WorkManagerImpl.MIN_JOB_SCHEDULER_API_LEVEL
import androidx.work.impl.model.WorkSpec
import androidx.work.impl.workers.ARGUMENT_CLASS_NAME
import androidx.work.impl.workers.ConstraintTrackingWorker
import java.lang.IllegalArgumentException

internal fun checkContentUriTriggerWorkerLimits(
    workDatabase: WorkDatabase,
    configuration: Configuration,
    continuation: WorkContinuationImpl
) {
    if (Build.VERSION.SDK_INT < WorkManagerImpl.CONTENT_URI_TRIGGER_API_LEVEL) return
    val continuations = mutableListOf(continuation)
    var newCount = 0
    while (continuations.isNotEmpty()) {
        val current = continuations.removeLast()
        newCount += current.work.count { it.workSpec.constraints.hasContentUriTriggers() }
        (current.parents as List<WorkContinuationImpl>?)?.let { continuations.addAll(it) }
    }
    if (newCount == 0) return
    val alreadyEnqueuedCount = workDatabase.workSpecDao().countNonFinishedContentUriTriggerWorkers()
    val limit = configuration.contentUriTriggerWorkersLimit
    if (alreadyEnqueuedCount + newCount > limit)
        throw IllegalArgumentException(
            "Too many workers with contentUriTriggers are enqueued:\n" +
            "contentUriTrigger workers limit: $limit;\n" +
            "already enqueued count: $alreadyEnqueuedCount;\n" +
            "current enqueue operation count: $newCount.\n" +
            "To address this issue you can: \n" +
            "1. enqueue less workers or batch some of workers " +
            "with content uri triggers together;\n" +
            "2. increase limit via Configuration.Builder.setContentUriTriggerWorkersLimit;\n" +
            "Please beware that workers with content uri triggers immediately occupy " +
            "slots in JobScheduler so no updates to content uris are missed."
        )
}

internal fun tryDelegateConstrainedWorkSpec(workSpec: WorkSpec): WorkSpec {
    // requiresBatteryNotLow and requiresStorageNotLow require API 26 for JobScheduler.
    // Delegate to ConstraintTrackingWorker between API 23-25.
    val constraints = workSpec.constraints
    val workerClassName = workSpec.workerClassName
    // Check if the Worker is a ConstraintTrackingWorker already. Otherwise we could end up
    // wrapping a ConstraintTrackingWorker with another and build a taller stack.
    // This usually happens when a developer accidentally enqueues() a named WorkRequest
    // with an ExistingWorkPolicy.KEEP and subsequent inserts no-op (while the state of the
    // Worker is not ENQUEUED or RUNNING i.e. the Worker probably just got done & the app is
    // holding on to a reference of WorkSpec which got updated). We end up reusing the
    // WorkSpec, and get a ConstraintTrackingWorker (instead of the original Worker class).
    val isConstraintTrackingWorker = workerClassName == ConstraintTrackingWorker::class.java.name
    if (!isConstraintTrackingWorker &&
        (constraints.requiresBatteryNotLow() || constraints.requiresStorageNotLow())
    ) {
        val newInputData = Data.Builder().putAll(workSpec.input)
            .putString(ARGUMENT_CLASS_NAME, workerClassName)
            .build()
        return workSpec.copy(
            input = newInputData,
            workerClassName = ConstraintTrackingWorker::class.java.name
        )
    }
    return workSpec
}

internal fun wrapInConstraintTrackingWorkerIfNeeded(
    schedulers: List<Scheduler>,
    workSpec: WorkSpec,
): WorkSpec {
    return when {
        Build.VERSION.SDK_INT in MIN_JOB_SCHEDULER_API_LEVEL..25 ->
            tryDelegateConstrainedWorkSpec(workSpec)
        Build.VERSION.SDK_INT <= MAX_PRE_JOB_SCHEDULER_API_LEVEL &&
            usesScheduler(schedulers, Schedulers.GCM_SCHEDULER) ->
            tryDelegateConstrainedWorkSpec(workSpec)
        else -> workSpec
    }
}

private fun usesScheduler(
    schedulers: List<Scheduler>,
    className: String
): Boolean {
    return try {
        val klass = Class.forName(className)
        return schedulers.any { scheduler -> klass.isAssignableFrom(scheduler.javaClass) }
    } catch (ignore: ClassNotFoundException) {
        false
    }
}
