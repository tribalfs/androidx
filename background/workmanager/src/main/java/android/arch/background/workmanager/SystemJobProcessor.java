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
package android.arch.background.workmanager;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A {@link Processor} that handles execution for work coming via
 * {@link android.app.job.JobScheduler}.
 */

public class SystemJobProcessor extends Processor {

    private ExecutionListener mOuterListener;

    public SystemJobProcessor(
            Context appContext,
            WorkDatabase workDatabase,
            ExecutionListener outerListener) {
        super(appContext, workDatabase);
        mOuterListener = outerListener;
    }

    @Override
    public ExecutorService createExecutorService() {
        // TODO(sumir): Be more intelligent about this.
        return Executors.newScheduledThreadPool(1);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void onExecuted(String workSpecId, @WorkerWrapper.ExecutionResult int result) {
        super.onExecuted(workSpecId, result);
        if (mOuterListener != null) {
            mOuterListener.onExecuted(workSpecId, result);
        }
    }
}
