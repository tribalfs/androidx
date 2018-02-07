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

package android.arch.background.workmanager.impl.background.systemalarm;

import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.PendingIntent.FLAG_ONE_SHOT;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.background.workmanager.impl.ExecutionListener;
import android.arch.background.workmanager.impl.logger.Logger;
import android.arch.background.workmanager.impl.model.WorkSpec;
import android.arch.background.workmanager.impl.utils.IdGenerator;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The command handler used by {@link SystemAlarmDispatcher}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CommandHandler implements ExecutionListener {

    private static final String TAG = "CommandHandler";

    // actions
    private static final String ACTION_SCHEDULE_WORK = "ACTION_SCHEDULE_WORK";
    private static final String ACTION_DELAY_MET = "ACTION_DELAY_MET";
    private static final String ACTION_CANCEL_WORK = "ACTION_CANCEL_WORK";
    private static final String ACTION_CONSTRAINTS_CHANGED = "ACTION_CONSTRAINTS_CHANGED";

    // keys
    private static final String KEY_WORKSPEC_ID = "KEY_WORKSPEC_ID";

    // constants
    static final long WORK_PROCESSING_TIME_IN_MS = 10 * 60 * 1000L;;

    // utilities

    static Intent createScheduleWorkIntent(@NonNull Context context, @NonNull String workSpecId) {
        Intent intent = new Intent(context, SystemAlarmService.class);
        intent.setAction(ACTION_SCHEDULE_WORK);
        intent.putExtra(KEY_WORKSPEC_ID, workSpecId);
        return intent;
    }

    static Intent createDelayMetIntent(@NonNull Context context, @NonNull String workSpecId) {
        Intent intent = new Intent(context, SystemAlarmService.class);
        intent.setAction(ACTION_DELAY_MET);
        intent.putExtra(KEY_WORKSPEC_ID, workSpecId);
        return intent;
    }

    static Intent createCancelWorkIntent(@NonNull Context context, @NonNull String workSpecId) {
        Intent intent = new Intent(context, SystemAlarmService.class);
        intent.setAction(ACTION_CANCEL_WORK);
        intent.putExtra(KEY_WORKSPEC_ID, workSpecId);
        return intent;
    }

    static Intent createConstraintsChangedIntent(@NonNull Context context) {
        Intent intent = new Intent(context, SystemAlarmService.class);
        intent.setAction(ACTION_CONSTRAINTS_CHANGED);
        return intent;
    }

    // members
    private final Context mContext;
    private final IdGenerator mIdGenerator;
    private final AlarmManager mAlarmManager;
    private final Map<String, ExecutionListener> mPendingDelayMet;
    private final Object mLock;

    CommandHandler(@NonNull Context context) {
        mContext = context;
        mIdGenerator = new IdGenerator(mContext);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        mPendingDelayMet = new HashMap<>();
        mLock = new Object();
    }

    /**
     * @return <code>true</code> if there is work pending.
     */
    boolean hasPendingCommands() {
        // Needs to be synchronized as this could be checked from
        // both the command processing thread, as well as the
        // onExecuted callback.
        synchronized (mLock) {
            // If we have pending work being executed on the background
            // processor - we are not done yet.
            return !mPendingDelayMet.isEmpty();
        }
    }

    /**
     * The actual command handler.
     */
    void onHandleIntent(
            @NonNull Intent intent,
            int startId,
            @NonNull SystemAlarmDispatcher dispatcher) {

        String action = intent.getAction();

        Bundle extras = intent.getExtras();
        if (!hasKeys(extras, KEY_WORKSPEC_ID)) {
            Logger.error(TAG, "Invalid request for %s, requires %s.",
                    ACTION_SCHEDULE_WORK, KEY_WORKSPEC_ID);
        } else {
            if (ACTION_SCHEDULE_WORK.equals(action)) {
                handleScheduleWorkIntent(intent, startId, dispatcher);
            } else if (ACTION_DELAY_MET.equals(action)) {
                handleDelayMet(intent, startId, dispatcher);
            } else if (ACTION_CANCEL_WORK.equals(action)) {
                handleCancelWork(intent, startId, dispatcher);
            } else if (ACTION_CONSTRAINTS_CHANGED.equals(action)) {
                handleConstraintsChanged(intent, startId, dispatcher);
            } else {
                Logger.warn(TAG, "Ignoring intent %s", intent);
            }
        }
    }

    private void handleScheduleWorkIntent(
            @NonNull Intent intent,
            int startId,
            @NonNull SystemAlarmDispatcher dispatcher) {

        Bundle extras = intent.getExtras();
        String workSpecId = extras.getString(KEY_WORKSPEC_ID);
        Logger.debug(TAG, "Handling schedule work for %s", workSpecId);
        WorkSpec workSpec = dispatcher.getWorkManager()
                .getWorkDatabase()
                .workSpecDao()
                .getWorkSpec(workSpecId);
        long triggerAt = workSpec.calculateNextRunTime();
        Intent delayMet = CommandHandler.createDelayMetIntent(mContext, workSpecId);

        if (triggerAt <= System.currentTimeMillis()) {
            // We should be already processing this worker
            // Request dispatcher to treat this as a delayMet intent
            Logger.debug(TAG, "triggerAt is in the past. Processing the worker %s", workSpecId);
            dispatcher.postOnMainThread(
                    new SystemAlarmDispatcher.AddRunnable(dispatcher, delayMet, startId));
        } else {
            // Update constraint proxies for the given workSpec
            ConstraintProxy.updateAll(mContext, Collections.singletonList(workSpec));
            Logger.debug(TAG, "Setting up Alarms for %s", workSpecId);
            setExactAlarm(delayMet, triggerAt);
        }
    }

    private void handleDelayMet(
            @NonNull Intent intent,
            int startId,
            @NonNull SystemAlarmDispatcher dispatcher) {

        Bundle extras = intent.getExtras();
        synchronized (mLock) {
            String workSpecId = extras.getString(KEY_WORKSPEC_ID);
            Logger.debug(TAG, "Handing delay met for %s", workSpecId);
            DelayMetCommandHandler delayMetCommandHandler =
                    new DelayMetCommandHandler(mContext, startId, workSpecId, dispatcher);
            mPendingDelayMet.put(workSpecId, delayMetCommandHandler);
            delayMetCommandHandler.handleProcessWork();
        }
    }

    private void handleCancelWork(
            @NonNull Intent intent, int startId,
            @NonNull SystemAlarmDispatcher dispatcher) {

        Bundle extras = intent.getExtras();
        String workSpecId = extras.getString(KEY_WORKSPEC_ID);
        Logger.debug(TAG, "Handing cancel work for %s", workSpecId);
        // TODO(rahulrav@) Cancel alarm when necessary.
        // TODO(rahulrav@) This is not a great signal if this was a
        // user initiated cancel.
        boolean shouldReschedule = mPendingDelayMet.containsKey(workSpecId);
        // Request background processor to cancel the worker
        boolean isCancelled = dispatcher.getProcessor().cancel(workSpecId, true);
        // reschedule if necessary
        if (shouldReschedule) {
            Logger.debug(TAG, "WorkSpec %s needs to be rescheduled", workSpecId);
            Intent reschedule = CommandHandler.createScheduleWorkIntent(mContext, workSpecId);
            dispatcher.postOnMainThread(
                    new SystemAlarmDispatcher.AddRunnable(dispatcher, reschedule, startId));
        }
        // If the cancellation was successful, notify dispatcher, so
        // it can clean up.
        if (isCancelled) {
            dispatcher.onExecuted(workSpecId, false, shouldReschedule);
        }
    }

    private void handleConstraintsChanged(
            @NonNull Intent intent, int startId,
            @NonNull SystemAlarmDispatcher dispatcher) {

        Logger.debug(TAG, "Handling constraints changed");
        // TODO(rahulrav@) Enable constraint proxies
    }

    @VisibleForTesting
    private void setExactAlarm(@NonNull Intent intent, long triggerAtMillis) {
        int alarmId = mIdGenerator.nextAlarmManagerId();
        PendingIntent pendingIntent =
                PendingIntent.getService(mContext, alarmId, intent, FLAG_ONE_SHOT);

        if (Build.VERSION.SDK_INT >= 19) {
            mAlarmManager.setExact(RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            mAlarmManager.set(RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    @Override
    public void onExecuted(
            @NonNull String workSpecId,
            boolean isSuccessful,
            boolean needsReschedule) {

        synchronized (mLock) {
            Logger.debug(TAG, "onExecuted (%s, %s, %s)", workSpecId, isSuccessful, needsReschedule);
            // This listener is only necessary for knowing when a pending work is complete.
            // Delegate to the underlying execution listener itself.
            ExecutionListener listener = mPendingDelayMet.remove(workSpecId);
            if (listener != null) {
                listener.onExecuted(workSpecId, isSuccessful, needsReschedule);
            }
        }
    }

    private static boolean hasKeys(@Nullable Bundle bundle, @NonNull String... keys) {
        if (bundle == null || bundle.isEmpty()) {
            return false;
        } else {
            for (String key : keys) {
                if (!bundle.containsKey(key) || bundle.get(key) == null) {
                    return false;
                }
            }
            return true;
        }
    }
}
