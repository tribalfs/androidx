/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.work;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Network;
import android.net.Uri;
import android.support.annotation.Keep;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;

import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * A class that can perform work asynchronously in {@link WorkManager}.  For most cases, we
 * recommend using {@link Worker}, which offers a simple synchronous API that is executed on a
 * pre-specified background thread.
 * <p>
 * ListenableWorker classes are instantiated at runtime by the {@link WorkerFactory} specified in
 * the {@link Configuration}.  The {@link #startWork()} method is called on the main thread.
 * <p>
 * In case the work is preempted and later restarted for any reason, a new instance of
 * ListenableWorker is created. This means that {@code startWork} is called exactly once per
 * ListenableWorker instance.  A new ListenableWorker is created if a unit of work needs to be
 * rerun.
 */

public abstract class ListenableWorker {

    private @NonNull Context mAppContext;
    private @NonNull WorkerParameters mWorkerParams;

    private volatile boolean mStopped;

    private boolean mUsed;

    /**
     * @param appContext The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    @Keep
    @SuppressLint("BanKeepAnnotation")
    public ListenableWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        // Actually make sure we don't get nulls.
        if (appContext == null) {
            throw new IllegalArgumentException("Application Context is null");
        }

        if (workerParams == null) {
            throw new IllegalArgumentException("WorkerParameters is null");
        }

        mAppContext = appContext;
        mWorkerParams = workerParams;
    }

    /**
     * Gets the application {@link android.content.Context}.
     *
     * @return The application {@link android.content.Context}
     */
    public final @NonNull Context getApplicationContext() {
        return mAppContext;
    }

    /**
     * Gets the ID of the {@link WorkRequest} that created this Worker.
     *
     * @return The ID of the creating {@link WorkRequest}
     */
    public final @NonNull UUID getId() {
        return mWorkerParams.getId();
    }

    /**
     * Gets the input data.  Note that in the case that there are multiple prerequisites for this
     * Worker, the input data has been run through an {@link InputMerger}.
     *
     * @return The input data for this work
     * @see OneTimeWorkRequest.Builder#setInputMerger(Class)
     */
    public final @NonNull Data getInputData() {
        return mWorkerParams.getInputData();
    }

    /**
     * Gets a {@link java.util.Set} of tags associated with this Worker's {@link WorkRequest}.
     *
     * @return The {@link java.util.Set} of tags associated with this Worker's {@link WorkRequest}
     * @see WorkRequest.Builder#addTag(String)
     */
    public final @NonNull Set<String> getTags() {
        return mWorkerParams.getTags();
    }

    /**
     * Gets the list of content {@link android.net.Uri}s that caused this Worker to execute.  See
     * {@code JobParameters#getTriggeredContentUris()} for relevant {@code JobScheduler} code.
     *
     * @return The list of content {@link android.net.Uri}s that caused this Worker to execute
     * @see Constraints.Builder#addContentUriTrigger(android.net.Uri, boolean)
     */
    @RequiresApi(24)
    public final @NonNull List<Uri> getTriggeredContentUris() {
        return mWorkerParams.getTriggeredContentUris();
    }

    /**
     * Gets the list of content authorities that caused this Worker to execute.  See
     * {@code JobParameters#getTriggeredContentAuthorities()} for relevant {@code JobScheduler}
     * code.
     *
     * @return The list of content authorities that caused this Worker to execute
     */
    @RequiresApi(24)
    public final @NonNull List<String> getTriggeredContentAuthorities() {
        return mWorkerParams.getTriggeredContentAuthorities();
    }

    /**
     * Gets the {@link android.net.Network} to use for this Worker.  This method returns
     * {@code null} if there is no network needed for this work request.
     *
     * @return The {@link android.net.Network} specified by the OS to be used with this Worker
     */
    @RequiresApi(28)
    public final @Nullable Network getNetwork() {
        return mWorkerParams.getNetwork();
    }

    /**
     * Gets the current run attempt count for this work.  Note that for periodic work, this value
     * gets reset between periods.
     *
     * @return The current run attempt count for this work.
     */
    public final int getRunAttemptCount() {
        return mWorkerParams.getRunAttemptCount();
    }

    /**
     * Override this method to start your actual background processing. This method is called on
     * the main thread.
     *
     * @return A {@link ListenableFuture} with the {@link Result} of the computation.  If you
     *         cancel this Future, WorkManager will treat this unit of work as failed.
     */
    @MainThread
    public abstract @NonNull ListenableFuture<Result> startWork();

    /**
     * Returns {@code true} if this Worker has been told to stop.  This could be because of an
     * explicit cancellation signal by the user, or because the system has decided to preempt the
     * task. In these cases, the results of the work will be ignored by WorkManager and it is safe
     * to stop the computation.  WorkManager will retry the work at a later time if necessary.
     *
     * @return {@code true} if the work operation has been interrupted
     */
    public final boolean isStopped() {
        return mStopped;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public final void stop() {
        mStopped = true;
        onStopped();
    }

    /**
     * This method is invoked when this Worker has been told to stop.  This could happen due
     * to an explicit cancellation signal by the user, or because the system has decided to preempt
     * the task.  In these cases, the results of the work will be ignored by WorkManager.  All
     * processing in this method should be lightweight - there are no contractual guarantees about
     * which thread will invoke this call, so this should not be a long-running or blocking
     * operation.
     */
    public void onStopped() {
        // Do nothing by default.
    }

    /**
     * @return {@code true} if this worker has already been marked as used
     * @see #setUsed()
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public final boolean isUsed() {
        return mUsed;
    }

    /**
     * Marks this worker as used to make sure we enforce the policy that workers can only be used
     * once and that WorkerFactories return a new instance each time.
     * @see #isUsed()
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public final void setUsed() {
        mUsed = true;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @NonNull Executor getBackgroundExecutor() {
        return mWorkerParams.getBackgroundExecutor();
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @NonNull TaskExecutor getTaskExecutor() {
        return mWorkerParams.getTaskExecutor();
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @NonNull WorkerFactory getWorkerFactory() {
        return mWorkerParams.getWorkerFactory();
    }
}
