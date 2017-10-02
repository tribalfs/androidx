/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.background.workmanager.systemjob;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.background.workmanager.ExecutionListener;
import android.arch.background.workmanager.WorkDatabase;
import android.arch.background.workmanager.WorkManager;
import android.arch.background.workmanager.WorkerWrapper;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Service invoked by {@link android.app.job.JobScheduler} to run work tasks.
 */
@TargetApi(21)
public class SystemJobService extends JobService implements ExecutionListener {
    private static final String TAG = "SystemJobService";
    private SystemJobProcessor mSystemJobProcessor;
    private Map<String, JobParameters> mJobParameters = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(context);
        WorkDatabase database = workManager.getWorkDatabase();
        mSystemJobProcessor =
                new SystemJobProcessor(context, database, workManager.getScheduler(), this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        String workSpecId = params.getExtras().getString(SystemJobConverter.EXTRAS_WORK_SPEC_ID);
        if (TextUtils.isEmpty(workSpecId)) {
            Log.e(TAG, "WorkSpec id not found!");
            return false;
        }
        Log.d(TAG, workSpecId + " started on JobScheduler");
        mJobParameters.put(workSpecId, params);
        mSystemJobProcessor.process(workSpecId);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        String workSpecId = params.getExtras().getString(SystemJobConverter.EXTRAS_WORK_SPEC_ID);
        if (TextUtils.isEmpty(workSpecId)) {
            Log.e(TAG, "WorkSpec id not found!");
            return false;
        }
        boolean cancelled = mSystemJobProcessor.cancel(workSpecId, true);
        Log.d(TAG, workSpecId + "; cancel = " + cancelled);
        return cancelled;
    }

    @Override
    public void onExecuted(String workSpecId, @WorkerWrapper.ExecutionResult int result) {
        Log.d(TAG, workSpecId + " executed on JobScheduler");
        JobParameters parameters = mJobParameters.get(workSpecId);
        boolean needsReschedule = (result == WorkerWrapper.RESULT_INTERRUPTED
                || result == WorkerWrapper.RESULT_RESCHEDULED);
        jobFinished(parameters, needsReschedule);
    }
}
