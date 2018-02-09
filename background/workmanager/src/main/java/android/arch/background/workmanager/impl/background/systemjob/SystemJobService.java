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

package android.arch.background.workmanager.impl.background.systemjob;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.background.workmanager.impl.ExecutionListener;
import android.arch.background.workmanager.impl.Scheduler;
import android.arch.background.workmanager.impl.WorkManagerImpl;
import android.arch.background.workmanager.impl.logger.Logger;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Service invoked by {@link android.app.job.JobScheduler} to run work tasks.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TargetApi(23)
public class SystemJobService extends JobService implements ExecutionListener {
    private static final String TAG = "SystemJobService";
    private WorkManagerImpl mWorkManagerImpl;
    private Scheduler mScheduler;
    private final Map<String, JobParameters> mJobParameters = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mWorkManagerImpl = WorkManagerImpl.getInstance();
        mWorkManagerImpl.getProcessor().addExecutionListener(this);
        mScheduler = mWorkManagerImpl.getBackgroundScheduler();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWorkManagerImpl.getProcessor().removeExecutionListener(this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        String workSpecId = extras.getString(SystemJobInfoConverter.EXTRA_WORK_SPEC_ID);
        if (TextUtils.isEmpty(workSpecId)) {
            Logger.error(TAG, "WorkSpec id not found!");
            return false;
        }

        boolean isPeriodic = extras.getBoolean(SystemJobInfoConverter.EXTRA_IS_PERIODIC, false);
        if (isPeriodic && params.isOverrideDeadlineExpired()) {
            Logger.debug(TAG, "Override deadline expired for id %s. Retry requested", workSpecId);
            jobFinished(params, true);
            return false;
        }

        Logger.debug(TAG, "onStartJob for %s", workSpecId);

        synchronized (mJobParameters) {
            mJobParameters.put(workSpecId, params);
        }
        mWorkManagerImpl.startWork(workSpecId);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        String workSpecId = params.getExtras().getString(SystemJobInfoConverter.EXTRA_WORK_SPEC_ID);
        if (TextUtils.isEmpty(workSpecId)) {
            Logger.error(TAG, "WorkSpec id not found!");
            return false;
        }

        Logger.debug(TAG, "onStopJob for %s", workSpecId);

        synchronized (mJobParameters) {
            mJobParameters.remove(workSpecId);
        }
        mWorkManagerImpl.stopWork(workSpecId);
        return !mScheduler.isCancelled(workSpecId);
    }

    @Override
    public void onExecuted(
            @NonNull String workSpecId,
            boolean isSuccessful,
            boolean needsReschedule) {
        Logger.debug(TAG, "%s executed on JobScheduler", workSpecId);
        JobParameters parameters;
        synchronized (mJobParameters) {
            parameters = mJobParameters.get(workSpecId);
        }
        if (parameters != null) {
            jobFinished(parameters, needsReschedule);
        }
    }
}
