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

package androidx.work.impl.background.systemalarm;

import static androidx.work.impl.background.systemalarm.CommandHandler.WORK_PROCESSING_TIME_IN_MS;
import static androidx.work.impl.constraints.WorkConstraintsTrackerKt.listen;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;
import androidx.work.Logger;
import androidx.work.impl.StartStopToken;
import androidx.work.impl.constraints.ConstraintsState;
import androidx.work.impl.constraints.OnConstraintsStateChangedListener;
import androidx.work.impl.constraints.WorkConstraintsTracker;
import androidx.work.impl.constraints.trackers.Trackers;
import androidx.work.impl.model.WorkGenerationalId;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.utils.WakeLocks;
import androidx.work.impl.utils.WorkTimer;

import java.util.concurrent.Executor;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Job;

/**
 * This is a command handler which attempts to run a work spec given its id.
 * Also handles constraints gracefully.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DelayMetCommandHandler implements
        OnConstraintsStateChangedListener,
        WorkTimer.TimeLimitExceededListener {

    private static final String TAG = Logger.tagWithPrefix("DelayMetCommandHandler");

    /**
     * The initial state of the delay met command handler.
     * The handler always starts off at this state.
     */
    private static final int STATE_INITIAL = 0;
    /**
     * The command handler moves to STATE_START_REQUESTED when all constraints are met.
     * This should only happen once per instance of the command handler.
     */
    private static final int STATE_START_REQUESTED = 1;
    /**
     * The command handler moves to STATE_STOP_REQUESTED when some constraints are unmet.
     * This should only happen once per instance of the command handler.
     */
    private static final int STATE_STOP_REQUESTED = 2;

    /**
     * State Transitions.
     *
     *
     *                   |----> STATE_STOP_REQUESTED
     *                   |
     *                   |
     * STATE_INITIAL---->|
     *                   |
     *                   |
     *                   |----> STATE_START_REQUESTED ---->STATE_STOP_REQUESTED
     *
     */

    private final Context mContext;
    private final int mStartId;
    private final WorkGenerationalId mWorkGenerationalId;
    private final SystemAlarmDispatcher mDispatcher;
    private final WorkConstraintsTracker mWorkConstraintsTracker;
    private final Object mLock;
    // should be accessed only from SerialTaskExecutor
    private int mCurrentState;
    private final Executor mSerialExecutor;
    private final Executor mMainThreadExecutor;

    @Nullable private PowerManager.WakeLock mWakeLock;
    private boolean mHasConstraints;
    private final StartStopToken mToken;
    private final CoroutineDispatcher mCoroutineDispatcher;

    private volatile Job mJob;

    DelayMetCommandHandler(
            @NonNull Context context,
            int startId,
            @NonNull SystemAlarmDispatcher dispatcher,
            @NonNull StartStopToken startStopToken) {
        mContext = context;
        mStartId = startId;
        mDispatcher = dispatcher;
        mWorkGenerationalId = startStopToken.getId();
        mToken = startStopToken;
        Trackers trackers = dispatcher.getWorkManager().getTrackers();
        mSerialExecutor = dispatcher.getTaskExecutor().getSerialTaskExecutor();
        mMainThreadExecutor = dispatcher.getTaskExecutor().getMainThreadExecutor();
        mCoroutineDispatcher = dispatcher.getTaskExecutor().getTaskCoroutineDispatcher();
        mWorkConstraintsTracker = new WorkConstraintsTracker(trackers);
        mHasConstraints = false;
        mCurrentState = STATE_INITIAL;
        mLock = new Object();
    }

    @Override
    public void onConstraintsStateChanged(@NonNull WorkSpec workSpec,
            @NonNull ConstraintsState state) {
        if (state instanceof ConstraintsState.ConstraintsMet) {
            mSerialExecutor.execute(this::startWork);
        } else {
            mSerialExecutor.execute(this::stopWork);
        }
    }

    private void startWork() {
        if (mCurrentState == STATE_INITIAL) {
            mCurrentState = STATE_START_REQUESTED;

            Logger.get().debug(TAG, "onAllConstraintsMet for " + mWorkGenerationalId);
            // Constraints met, schedule execution
            // Not using WorkManagerImpl#startWork() here because we need to know if the
            // processor actually enqueued the work here.
            boolean isEnqueued = mDispatcher.getProcessor().startWork(mToken);

            if (isEnqueued) {
                // setup timers to enforce quotas on workers that have
                // been enqueued
                mDispatcher.getWorkTimer()
                        .startTimer(mWorkGenerationalId, WORK_PROCESSING_TIME_IN_MS, this);
            } else {
                // if we did not actually enqueue the work, it was enqueued before
                // cleanUp and pretend this never happened.
                cleanUp();
            }
        } else {
            Logger.get().debug(TAG, "Already started work for " + mWorkGenerationalId);
        }
    }

    void onExecuted(boolean needsReschedule) {
        Logger.get().debug(TAG, "onExecuted " + mWorkGenerationalId + ", " + needsReschedule);
        cleanUp();
        if (needsReschedule) {
            // We need to reschedule the WorkSpec. WorkerWrapper may also call Scheduler.schedule()
            // but given that we will only consider WorkSpecs that are eligible that it safe.
            Intent reschedule = CommandHandler.createScheduleWorkIntent(mContext,
                    mWorkGenerationalId);
            mMainThreadExecutor.execute(
                    new SystemAlarmDispatcher.AddRunnable(mDispatcher, reschedule, mStartId));
        }

        if (mHasConstraints) {
            // The WorkSpec had constraints. Once the execution of the worker is complete,
            // we might need to disable constraint proxies which were previously enabled for
            // this WorkSpec. Hence, trigger a constraints changed command.
            Intent intent = CommandHandler.createConstraintsChangedIntent(mContext);
            mMainThreadExecutor.execute(
                    new SystemAlarmDispatcher.AddRunnable(mDispatcher, intent, mStartId));
        }
    }

    @Override
    public void onTimeLimitExceeded(@NonNull WorkGenerationalId id) {
        Logger.get().debug(TAG, "Exceeded time limits on execution for " + id);
        mSerialExecutor.execute(this::stopWork);
    }

    @WorkerThread
    void handleProcessWork() {
        String workSpecId = mWorkGenerationalId.getWorkSpecId();
        mWakeLock = WakeLocks.newWakeLock(mContext, workSpecId + " (" + mStartId + ")");
        Logger.get().debug(TAG,
                "Acquiring wakelock " + mWakeLock + "for WorkSpec " + workSpecId);
        mWakeLock.acquire();

        WorkSpec workSpec = mDispatcher.getWorkManager()
                .getWorkDatabase()
                .workSpecDao()
                .getWorkSpec(workSpecId);
        // This should typically never happen. Cancelling work should remove alarms, but if an
        // alarm has already fired, then fire a stop work request to remove the pending delay met
        // command handler.
        if (workSpec == null) {
            mSerialExecutor.execute(this::stopWork);
            return;
        }

        // Keep track of whether the WorkSpec had constraints. This is useful for updating the
        // state of constraint proxies when onExecuted().
        mHasConstraints = workSpec.hasConstraints();

        if (!mHasConstraints) {
            Logger.get().debug(TAG, "No constraints for " + workSpecId);
            mSerialExecutor.execute(this::startWork);
        } else {
            // Allow tracker to report constraint changes
            mJob = listen(mWorkConstraintsTracker, workSpec, mCoroutineDispatcher, this);
        }
    }

    private void stopWork() {
        // No need to release the wake locks here. The stopWork command will eventually call
        // onExecuted() if there is a corresponding pending delay met command handler; which in
        // turn calls cleanUp().
        String workSpecId = mWorkGenerationalId.getWorkSpecId();
        if (mCurrentState < STATE_STOP_REQUESTED) {
            mCurrentState = STATE_STOP_REQUESTED;
            Logger.get().debug(TAG, "Stopping work for WorkSpec " + workSpecId);
            Intent stopWork = CommandHandler.createStopWorkIntent(mContext, mWorkGenerationalId);
            mMainThreadExecutor.execute(
                    new SystemAlarmDispatcher.AddRunnable(mDispatcher, stopWork, mStartId));
            // There are cases where the work may not have been enqueued at all, and therefore
            // the processor is completely unaware of such a workSpecId in which case a
            // reschedule should not happen. For e.g. DELAY_MET when constraints are not met,
            // should not result in a reschedule.
            if (mDispatcher.getProcessor().isEnqueued(mWorkGenerationalId.getWorkSpecId())) {
                Logger.get().debug(TAG, "WorkSpec " + workSpecId + " needs to be rescheduled");
                Intent reschedule = CommandHandler.createScheduleWorkIntent(mContext,
                        mWorkGenerationalId);
                mMainThreadExecutor.execute(
                        new SystemAlarmDispatcher.AddRunnable(mDispatcher, reschedule, mStartId)
                );
            } else {
                Logger.get().debug(TAG, "Processor does not have WorkSpec " + workSpecId
                        + ". No need to reschedule");
            }
        } else {
            Logger.get().debug(TAG, "Already stopped work for " + workSpecId);
        }
    }

    private void cleanUp() {
        // cleanUp() may occur from one of 2 threads.
        // * In the call to bgProcessor.startWork() returns false,
        //   it probably means that the worker is already being processed
        //   so we just need to call cleanUp to release wakelocks on the command processor thread.
        // * It could also happen on the onExecutionCompleted() pass of the bgProcessor.
        // To avoid calling mWakeLock.release() twice, we are synchronizing here.
        synchronized (mLock) {
            // clean up constraint trackers
            if (mJob != null) {
                mJob.cancel(null);
            }
            // stop timers
            mDispatcher.getWorkTimer().stopTimer(mWorkGenerationalId);

            // release wake locks
            if (mWakeLock != null && mWakeLock.isHeld()) {
                Logger.get().debug(TAG, "Releasing wakelock " + mWakeLock
                        + "for WorkSpec " + mWorkGenerationalId);
                mWakeLock.release();
            }
        }
    }
}
