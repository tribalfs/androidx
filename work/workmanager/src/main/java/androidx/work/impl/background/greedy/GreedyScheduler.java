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

package androidx.work.impl.background.greedy;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Build.VERSION.SDK_INT;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.work.Logger;
import androidx.work.WorkInfo;
import androidx.work.impl.ExecutionListener;
import androidx.work.impl.Scheduler;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.constraints.WorkConstraintsCallback;
import androidx.work.impl.constraints.WorkConstraintsTracker;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * A greedy {@link Scheduler} that schedules unconstrained, non-timed work.  It intentionally does
 * not acquire any WakeLocks, instead trying to brute-force them as time allows before the process
 * gets killed.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class GreedyScheduler implements Scheduler, WorkConstraintsCallback, ExecutionListener {

    private static final String TAG = Logger.tagWithPrefix("GreedyScheduler");

    private final Context mContext;
    private final WorkManagerImpl mWorkManagerImpl;
    private final WorkConstraintsTracker mWorkConstraintsTracker;
    private List<WorkSpec> mConstrainedWorkSpecs = new ArrayList<>();
    private boolean mRegisteredExecutionListener;
    private final Object mLock;

    // Internal State
    private Boolean mIsMainProcess;

    public GreedyScheduler(
            @NonNull Context context,
            @NonNull TaskExecutor taskExecutor,
            @NonNull WorkManagerImpl workManagerImpl) {
        mContext = context;
        mWorkManagerImpl = workManagerImpl;
        mWorkConstraintsTracker = new WorkConstraintsTracker(context, taskExecutor, this);
        mLock = new Object();
    }

    @VisibleForTesting
    public GreedyScheduler(
            @NonNull Context context,
            @NonNull WorkManagerImpl workManagerImpl,
            @NonNull WorkConstraintsTracker workConstraintsTracker) {
        mContext = context;
        mWorkManagerImpl = workManagerImpl;
        mWorkConstraintsTracker = workConstraintsTracker;
        mLock = new Object();
    }

    @Override
    public void schedule(@NonNull WorkSpec... workSpecs) {
        if (mIsMainProcess == null) {
            // The default process name is the package name.
            mIsMainProcess = TextUtils.equals(mContext.getPackageName(), getProcessName());
        }

        if (!mIsMainProcess) {
            Logger.get().info(TAG, "Ignoring schedule request in non-main process");
            return;
        }

        registerExecutionListenerIfNeeded();

        // Keep track of the list of new WorkSpecs whose constraints need to be tracked.
        // Add them to the known list of constrained WorkSpecs and call replace() on
        // WorkConstraintsTracker. That way we only need to synchronize on the part where we
        // are updating mConstrainedWorkSpecs.
        List<WorkSpec> constrainedWorkSpecs = new ArrayList<>();
        List<String> constrainedWorkSpecIds = new ArrayList<>();
        for (WorkSpec workSpec : workSpecs) {
            if (workSpec.state == WorkInfo.State.ENQUEUED
                    && !workSpec.isPeriodic()
                    && workSpec.initialDelay == 0L
                    && !workSpec.isBackedOff()) {
                if (workSpec.hasConstraints()) {
                    if (SDK_INT >= 23 && workSpec.constraints.requiresDeviceIdle()) {
                        // Ignore requests that have an idle mode constraint.
                        Logger.get().debug(TAG,
                                String.format("Ignoring WorkSpec %s, Requires device idle.",
                                        workSpec));
                    } else if (SDK_INT >= 24 && workSpec.constraints.hasContentUriTriggers()) {
                        // Ignore requests that have content uri triggers.
                        Logger.get().debug(TAG,
                                String.format("Ignoring WorkSpec %s, Requires ContentUri triggers.",
                                        workSpec));
                    } else {
                        constrainedWorkSpecs.add(workSpec);
                        constrainedWorkSpecIds.add(workSpec.id);
                    }
                } else {
                    Logger.get().debug(TAG, String.format("Starting work for %s", workSpec.id));
                    mWorkManagerImpl.startWork(workSpec.id);
                }
            }
        }

        // onExecuted() which is called on the main thread also modifies the list of mConstrained
        // WorkSpecs. Therefore we need to lock here.
        synchronized (mLock) {
            if (!constrainedWorkSpecs.isEmpty()) {
                Logger.get().debug(TAG, String.format("Starting tracking for [%s]",
                        TextUtils.join(",", constrainedWorkSpecIds)));
                mConstrainedWorkSpecs.addAll(constrainedWorkSpecs);
                mWorkConstraintsTracker.replace(mConstrainedWorkSpecs);
            }
        }
    }

    @Override
    public void cancel(@NonNull String workSpecId) {
        if (mIsMainProcess == null) {
            // The default process name is the package name.
            mIsMainProcess = TextUtils.equals(mContext.getPackageName(), getProcessName());
        }

        if (!mIsMainProcess) {
            Logger.get().info(TAG, "Ignoring schedule request in non-main process");
            return;
        }

        registerExecutionListenerIfNeeded();
        Logger.get().debug(TAG, String.format("Cancelling work ID %s", workSpecId));
        // onExecutionCompleted does the cleanup.
        mWorkManagerImpl.stopWork(workSpecId);
    }

    @Override
    public void onAllConstraintsMet(@NonNull List<String> workSpecIds) {
        for (String workSpecId : workSpecIds) {
            Logger.get().debug(
                    TAG,
                    String.format("Constraints met: Scheduling work ID %s", workSpecId));
            mWorkManagerImpl.startWork(workSpecId);
        }
    }

    @Override
    public void onAllConstraintsNotMet(@NonNull List<String> workSpecIds) {
        for (String workSpecId : workSpecIds) {
            Logger.get().debug(TAG,
                    String.format("Constraints not met: Cancelling work ID %s", workSpecId));
            mWorkManagerImpl.stopWork(workSpecId);
        }
    }

    @Override
    public void onExecuted(@NonNull String workSpecId, boolean needsReschedule) {
        removeConstraintTrackingFor(workSpecId);
    }

    private void removeConstraintTrackingFor(@NonNull String workSpecId) {
        synchronized (mLock) {
            // This is synchronized because onExecuted is on the main thread but
            // Schedulers#schedule() can modify the list of mConstrainedWorkSpecs on the task
            // executor thread.
            for (int i = 0, size = mConstrainedWorkSpecs.size(); i < size; ++i) {
                if (mConstrainedWorkSpecs.get(i).id.equals(workSpecId)) {
                    Logger.get().debug(TAG, String.format("Stopping tracking for %s", workSpecId));
                    mConstrainedWorkSpecs.remove(i);
                    mWorkConstraintsTracker.replace(mConstrainedWorkSpecs);
                    break;
                }
            }
        }
    }

    private void registerExecutionListenerIfNeeded() {
        // This method needs to be called *after* Processor is created, since Processor needs
        // Schedulers and is created after this class.
        if (!mRegisteredExecutionListener) {
            mWorkManagerImpl.getProcessor().addExecutionListener(this);
            mRegisteredExecutionListener = true;
        }
    }

    @Nullable
    private String getProcessName() {
        int pid = Process.myPid();
        ActivityManager am =
                (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);

        if (am != null) {
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null && !processes.isEmpty()) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == pid) {
                        return process.processName;
                    }
                }
            }
        }

        return null;
    }
}
