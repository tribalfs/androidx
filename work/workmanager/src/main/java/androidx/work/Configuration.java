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

import static androidx.work.impl.Scheduler.MAX_SCHEDULER_LIMIT;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.work.impl.Scheduler;
import androidx.work.impl.utils.IdGenerator;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The Configuration object used to customize {@link WorkManager} upon initialization.
 * Configuration contains various parameters used to setup WorkManager.  For example, it is possible
 * to customize the {@link Executor} used by {@link Worker}s here.
 * <p>
 * To set a custom Configuration for WorkManager, see
 * {@link WorkManager#initialize(Context, Configuration)}.
 */

public final class Configuration {

    /**
     * The minimum number of system requests which can be enqueued by {@link WorkManager}
     * when using {@link android.app.job.JobScheduler} or {@link android.app.AlarmManager}.
     */
    public static final int MIN_SCHEDULER_LIMIT = 20;

    private final @NonNull Executor mExecutor;
    private final @NonNull Executor mTaskExecutor;
    private final @NonNull WorkerFactory mWorkerFactory;
    private final int mLoggingLevel;
    private final int mMinJobSchedulerId;
    private final int mMaxJobSchedulerId;
    private final int mMaxSchedulerLimit;

    Configuration(@NonNull Configuration.Builder builder) {
        if (builder.mExecutor == null) {
            mExecutor = createDefaultExecutor();
        } else {
            mExecutor = builder.mExecutor;
        }

        if (builder.mTaskExecutor == null) {
            // This executor is used for *both* WorkManager's tasks and Room's query executor.
            // So this should not be a single threaded executor. Writes will still be serialized
            // as this will be wrapped with an SerialExecutor.
            mTaskExecutor = createDefaultExecutor();
        } else {
            mTaskExecutor = builder.mTaskExecutor;
        }

        if (builder.mWorkerFactory == null) {
            mWorkerFactory = WorkerFactory.getDefaultWorkerFactory();
        } else {
            mWorkerFactory = builder.mWorkerFactory;
        }

        mLoggingLevel = builder.mLoggingLevel;
        mMinJobSchedulerId = builder.mMinJobSchedulerId;
        mMaxJobSchedulerId = builder.mMaxJobSchedulerId;
        mMaxSchedulerLimit = builder.mMaxSchedulerLimit;
    }

    /**
     * Gets the {@link Executor} used by {@link WorkManager} to execute {@link Worker}s.
     *
     * @return The {@link Executor} used by {@link WorkManager} to execute {@link Worker}s
     */
    public @NonNull Executor getExecutor() {
        return mExecutor;
    }

    /**
     * Gets the {@link Executor} used by {@link WorkManager} for all its internal business logic.
     *
     * @return The {@link Executor} used by {@link WorkManager} for all its internal business logic
     */
    @NonNull
    public Executor getTaskExecutor() {
        return mTaskExecutor;
    }

    /**
     * Gets the {@link WorkerFactory} used by {@link WorkManager} to create
     * {@link ListenableWorker}s.
     *
     * @return The {@link WorkerFactory} used by {@link WorkManager} to create
     *         {@link ListenableWorker}s
     */
    public @NonNull WorkerFactory getWorkerFactory() {
        return mWorkerFactory;
    }

    /**
     * Gets the minimum logging level for {@link WorkManager}.
     *
     * @return The minimum logging level, corresponding to the constants found in
     * {@link android.util.Log}
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public int getMinimumLoggingLevel() {
        return mLoggingLevel;
    }

    /**
     * Gets the first valid id used when scheduling work with {@link android.app.job.JobScheduler}.
     *
     * @return The first valid id (inclusive) used by {@link WorkManager} when creating new
     *         instances of {@link android.app.job.JobInfo}s.  If the current {@code jobId} goes
     *         beyond the bounds of the defined range of
     *         ({@link Configuration.Builder#getMinJobSchedulerId()},
     *         {@link Configuration.Builder#getMaxJobSchedulerId()}), it is reset to
     *         ({@link Configuration.Builder#getMinJobSchedulerId()}).
     */
    public int getMinJobSchedulerId() {
        return mMinJobSchedulerId;
    }

    /**
     * Gets the last valid id when scheduling work with {@link android.app.job.JobScheduler}.
     *
     * @return The last valid id (inclusive) used by {@link WorkManager} when
     *         creating new instances of {@link android.app.job.JobInfo}s.  If the current
     *         {@code jobId} goes beyond the bounds of the defined range of
     *         ({@link Configuration.Builder#getMinJobSchedulerId()},
     *         {@link Configuration.Builder#getMaxJobSchedulerId()}), it is reset to
     *         ({@link Configuration.Builder#getMinJobSchedulerId()}).
     */
    public int getMaxJobSchedulerId() {
        return mMaxJobSchedulerId;
    }

    /**
     * Gets the maximum number of system requests that can be made by {@link WorkManager} when using
     * {@link android.app.job.JobScheduler} or {@link android.app.AlarmManager}.
     *
     * @return The maximum number of system requests which can be enqueued by {@link WorkManager}
     *         when using {@link android.app.job.JobScheduler} or {@link android.app.AlarmManager}
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @IntRange(from = Configuration.MIN_SCHEDULER_LIMIT, to = Scheduler.MAX_SCHEDULER_LIMIT)
    public int getMaxSchedulerLimit() {
        // We double schedule jobs in SDK 23. So use half the number of max slots specified.
        if (Build.VERSION.SDK_INT == 23) {
            return mMaxSchedulerLimit / 2;
        } else {
            return mMaxSchedulerLimit;
        }
    }

    private @NonNull Executor createDefaultExecutor() {
        return Executors.newFixedThreadPool(
                // This value is the same as the core pool size for AsyncTask#THREAD_POOL_EXECUTOR.
                Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4)));
    }

    /**
     * A Builder for {@link Configuration}s.
     */
    public static final class Builder {

        Executor mExecutor;
        WorkerFactory mWorkerFactory;
        Executor mTaskExecutor;

        int mLoggingLevel = Log.INFO;
        int mMinJobSchedulerId = IdGenerator.INITIAL_ID;
        int mMaxJobSchedulerId = Integer.MAX_VALUE;
        int mMaxSchedulerLimit = MIN_SCHEDULER_LIMIT;

        /**
         * Specifies a custom {@link WorkerFactory} for WorkManager.
         *
         * @param workerFactory A {@link WorkerFactory} for creating {@link ListenableWorker}s
         * @return This {@link Builder} instance
         */
        public @NonNull Builder setWorkerFactory(@NonNull WorkerFactory workerFactory) {
            mWorkerFactory = workerFactory;
            return this;
        }

        /**
         * Specifies a custom {@link Executor} for WorkManager.
         *
         * @param executor An {@link Executor} for running {@link Worker}s
         * @return This {@link Builder} instance
         */
        public @NonNull Builder setExecutor(@NonNull Executor executor) {
            mExecutor = executor;
            return this;
        }

        /**
         * Specifies a {@link Executor} which will be used by WorkManager for all its
         * internal book-keeping.
         *
         * For best performance this {@link Executor} should be bounded.
         *
         * For more information look at
         * {@link androidx.room.RoomDatabase.Builder#setQueryExecutor(Executor)}.
         *
         * @param taskExecutor The {@link Executor} which will be used by WorkManager for
         *                             all its internal book-keeping
         * @return This {@link Builder} instance
         */
        public @NonNull Builder setTaskExecutor(@NonNull Executor taskExecutor) {
            mTaskExecutor = taskExecutor;
            return this;
        }

        /**
         * Specifies the range of {@link android.app.job.JobInfo} IDs that can be used by
         * {@link WorkManager}.  WorkManager needs a range of at least {@code 1000} IDs.
         * <p>
         * JobScheduler uses integers as identifiers for jobs, and WorkManager delegates to
         * JobScheduler on certain API levels.  In order to not clash job codes used in the rest of
         * your app, you can use this method to tell WorkManager the valid range of job IDs that it
         * can use.
         * <p>
         * The default values are {@code 0} and {@code Integer#MAX_VALUE}.
         *
         * @param minJobSchedulerId The first valid {@link android.app.job.JobInfo} ID (inclusive).
         * @param maxJobSchedulerId The last valid {@link android.app.job.JobInfo} ID (inclusive).
         * @return This {@link Builder} instance
         * @throws IllegalArgumentException when the size of the range is less than 1000
         */
        public @NonNull Builder setJobSchedulerJobIdRange(
                int minJobSchedulerId,
                int maxJobSchedulerId) {
            if ((maxJobSchedulerId - minJobSchedulerId) < 1000) {
                throw new IllegalArgumentException(
                        "WorkManager needs a range of at least 1000 job ids.");
            }

            mMinJobSchedulerId = minJobSchedulerId;
            mMaxJobSchedulerId = maxJobSchedulerId;
            return this;
        }

        /**
         * Specifies the maximum number of system requests made by {@link WorkManager}
         * when using {@link android.app.job.JobScheduler} or {@link android.app.AlarmManager}.
         * <p>
         * By default, WorkManager might schedule a large number of alarms or JobScheduler
         * jobs.  If your app uses JobScheduler or AlarmManager directly, this might exhaust the
         * OS-enforced limit on the number of jobs or alarms an app is allowed to schedule.  To help
         * manage this situation, you can use this method to reduce the number of underlying jobs
         * and alarms that WorkManager might schedule.
         * <p>
         * When the application exceeds this limit, WorkManager maintains an internal queue of
         * {@link WorkRequest}s, and schedules them when slots become free.
         * <p>
         * WorkManager requires a minimum of {@link Configuration#MIN_SCHEDULER_LIMIT} slots; this
         * is also the default value. The total number of slots also cannot exceed {@code 50}.
         *
         * @param maxSchedulerLimit The total number of jobs which can be enqueued by
         *                          {@link WorkManager} when using
         *                          {@link android.app.job.JobScheduler}.
         * @return This {@link Builder} instance
         * @throws IllegalArgumentException if {@code maxSchedulerLimit} is less than
         *                                  {@link Configuration#MIN_SCHEDULER_LIMIT}
         */
        public @NonNull Builder setMaxSchedulerLimit(int maxSchedulerLimit) {
            if (maxSchedulerLimit < MIN_SCHEDULER_LIMIT) {
                throw new IllegalArgumentException(
                        "WorkManager needs to be able to schedule at least 20 jobs in "
                                + "JobScheduler.");
            }
            mMaxSchedulerLimit = Math.min(maxSchedulerLimit, MAX_SCHEDULER_LIMIT);
            return this;
        }

        /**
         * Specifies the minimum logging level, corresponding to the constants found in
         * {@link android.util.Log}.  For example, specifying {@link android.util.Log#VERBOSE} will
         * log everything, whereas specifying {@link android.util.Log#ERROR} will only log errors
         * and assertions.The default value is {@link android.util.Log#INFO}.
         *
         * @param loggingLevel The minimum logging level, corresponding to the constants found in
         *                     {@link android.util.Log}
         * @return This {@link Builder} instance
         */
        public @NonNull Builder setMinimumLoggingLevel(int loggingLevel) {
            mLoggingLevel = loggingLevel;
            return this;
        }

        /**
         * Builds a {@link Configuration} object.
         *
         * @return A {@link Configuration} object with this {@link Builder}'s parameters.
         */
        public @NonNull Configuration build() {
            return new Configuration(this);
        }
    }

    /**
     * A class that can provide the {@link Configuration} for WorkManager and allow for on-demand
     * initialization of WorkManager.  To do this:
     * <p><ul>
     *   <li>Disable {@code androidx.work.impl.WorkManagerInitializer} in your manifest</li>
     *   <li>Implement the {@link Configuration.Provider} interface on your
     *   {@link android.app.Application} class</li>
     *   <li>Use {@link WorkManager#getInstance(Context)} when accessing WorkManger (NOT
     *   {@link WorkManager#getInstance()})</li>
     * </ul></p>
     * <p>
     * Note that on-demand initialization may delay some useful features of WorkManager such as
     * automatic rescheduling of work following a crash and recovery from the application being
     * force-stopped by the user or device.
     *
     * @see WorkManager#initialize(Context, Configuration) for manual initialization.
     */
    public interface Provider {

        /**
         * @return The {@link Configuration} used to initialize WorkManager
         */
        @NonNull Configuration getWorkManagerConfiguration();
    }
}
