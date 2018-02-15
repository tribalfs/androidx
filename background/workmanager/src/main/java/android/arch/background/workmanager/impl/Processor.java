/*
 * Copyright 2017 The Android Open Source Project
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
package android.arch.background.workmanager.impl;

import android.arch.background.workmanager.impl.logger.Logger;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A Processor can intelligently schedule and execute work on demand.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Processor implements ExecutionListener {
    private static final String TAG = "Processor";

    private Context mAppContext;
    private WorkDatabase mWorkDatabase;

    private Map<String, Future<?>> mEnqueuedWorkMap;
    private List<Scheduler> mSchedulers;
    private ExecutorService mExecutorService;

    private Set<String> mCancelledIds;

    private final List<ExecutionListener> mOuterListeners;

    public Processor(
            Context appContext,
            WorkDatabase workDatabase,
            List<Scheduler> schedulers,
            ExecutorService executorService) {
        mAppContext = appContext;
        mWorkDatabase = workDatabase;
        mEnqueuedWorkMap = new HashMap<>();
        mSchedulers = schedulers;
        mExecutorService = executorService;
        mCancelledIds = new HashSet<>();
        mOuterListeners = new ArrayList<>();
    }

    /**
     * Starts a given unit of work in the background.
     *
     * @param id The work id to execute.
     * @return {@code true} if the work was successfully enqueued for processing
     */
    public synchronized boolean startWork(String id) {
        // Work may get triggered multiple times if they have passing constraints and new work with
        // those constraints are added.
        if (mEnqueuedWorkMap.containsKey(id)) {
            Logger.debug(TAG, "Work %s is already enqueued for processing", id);
            return false;
        }

        WorkerWrapper workWrapper = new WorkerWrapper.Builder(mAppContext, mWorkDatabase, id)
                .withListener(this)
                .withSchedulers(mSchedulers)
                .build();
        mEnqueuedWorkMap.put(id, mExecutorService.submit(workWrapper));
        Logger.debug(TAG, "%s: processing %s", getClass().getSimpleName(), id);
        return true;
    }

    /**
     * Tries to stop a unit of work.
     *
     * @param id The work id to stop
     * @param mayInterruptIfRunning If {@code true}, we try to interrupt the {@link Future} if it's
     *                              running
     * @return {@code true} if the work was stopped successfully
     */
    public synchronized boolean stopWork(String id, boolean mayInterruptIfRunning) {
        Logger.debug(TAG,
                "%s canceling %s; mayInterruptIfRunning = %s",
                getClass().getSimpleName(),
                id,
                mayInterruptIfRunning);
        Future<?> future = mEnqueuedWorkMap.get(id);
        if (future != null) {
            boolean cancelled = future.cancel(mayInterruptIfRunning);
            if (cancelled) {
                mEnqueuedWorkMap.remove(id);
                Logger.debug(TAG, "Future successfully canceled for %s", id);
            } else {
                Logger.debug(TAG, "Future could not be canceled for %s", id);
            }
            return cancelled;
        } else {
            Logger.debug(TAG, "%s future could not be found for %s",
                    getClass().getSimpleName(), id);
        }
        return false;
    }

    /**
     * Sets the given {@code id} as cancelled.  This does not actually stop any processing; call
     * {@link #stopWork(String, boolean)} to do that.
     *
     * @param id  The work id to mark as cancelled
     */
    public synchronized void setCancelled(String id) {
        mCancelledIds.add(id);
    }

    /**
     * Determines if the given {@code id} is marked as cancelled.
     *
     * @param id The work id to query
     * @return {@code true} if the id has already been marked as cancelled
     */
    public synchronized boolean isCancelled(String id) {
        return mCancelledIds.contains(id);
    }

    /**
     * @return {@code true} if the processor has work to process.
     */
    public synchronized boolean hasWork() {
        return !mEnqueuedWorkMap.isEmpty();
    }

    /**
     * @param workSpecId The {@link android.arch.background.workmanager.impl.model.WorkSpec} id
     * @return {@code true} if the id was enqueued in the processor.
     */
    public synchronized boolean isEnqueued(@NonNull String workSpecId) {
        return mEnqueuedWorkMap.containsKey(workSpecId);
    }

    /**
     * Adds an {@link ExecutionListener} to track when work finishes.
     *
     * @param executionListener The {@link ExecutionListener} to add
     */
    public synchronized void addExecutionListener(ExecutionListener executionListener) {
        // TODO(sumir): Let's get some synchronization guarantees here.
        mOuterListeners.add(executionListener);
    }

    /**
     * Removes a tracked {@link ExecutionListener}.
     *
     * @param executionListener The {@link ExecutionListener} to remove
     */
    public synchronized void removeExecutionListener(ExecutionListener executionListener) {
        // TODO(sumir): Let's get some synchronization guarantees here.
        mOuterListeners.remove(executionListener);
    }

    @Override
    public synchronized void onExecuted(
            @NonNull String workSpecId,
            boolean isSuccessful,
            boolean needsReschedule) {

        mEnqueuedWorkMap.remove(workSpecId);
        Logger.debug(TAG, "%s %s executed; isSuccessful = %s, reschedule = %s",
                getClass().getSimpleName(), workSpecId, isSuccessful, needsReschedule);

        // TODO(sumir): Let's get some synchronization guarantees here.
        for (ExecutionListener executionListener : mOuterListeners) {
            executionListener.onExecuted(workSpecId, isSuccessful, needsReschedule);
        }
    }
}
