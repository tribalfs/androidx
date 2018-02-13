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

import static android.support.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import android.app.job.JobInfo;
import android.arch.background.workmanager.BackoffPolicy;
import android.arch.background.workmanager.Constraints;
import android.arch.background.workmanager.ContentUriTriggers;
import android.arch.background.workmanager.NetworkType;
import android.arch.background.workmanager.impl.WorkManagerImpl;
import android.arch.background.workmanager.impl.logger.Logger;
import android.arch.background.workmanager.impl.model.WorkSpec;
import android.arch.background.workmanager.impl.utils.IdGenerator;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;

/**
 * Converts a {@link WorkSpec} into a JobInfo.
 */
@RequiresApi(api = WorkManagerImpl.MIN_JOB_SCHEDULER_API_LEVEL)
class SystemJobInfoConverter {
    private static final String TAG = "SystemJobInfoConverter";

    static final String EXTRA_WORK_SPEC_ID = "EXTRA_WORK_SPEC_ID";
    static final String EXTRA_IS_PERIODIC = "EXTRA_IS_PERIODIC";

    private final ComponentName mWorkServiceComponent;
    private final IdGenerator mIdGenerator;

    /**
     * Constructs a {@link IdGenerator}.
     *
     * @param context A non-null {@link Context}.
     */
    SystemJobInfoConverter(@NonNull Context context) {
        this(context, new IdGenerator(context));
    }

    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    SystemJobInfoConverter(@NonNull Context context, IdGenerator idGenerator) {
        Context appContext = context.getApplicationContext();
        mWorkServiceComponent = new ComponentName(appContext, SystemJobService.class);
        mIdGenerator = idGenerator;
    }

    /**
     * Converts a {@link WorkSpec} into a {@link JobInfo}.
     *
     * Note: All {@link JobInfo} are set to persist on reboot.
     *
     * @param workSpec The {@link WorkSpec} to convert
     * @return The {@link JobInfo} representing the same information as the {@link WorkSpec}
     */
    JobInfo convert(WorkSpec workSpec) {
        Constraints constraints = workSpec.getConstraints();
        int jobId = mIdGenerator.nextJobSchedulerId();
        // TODO(janclarin): Support newer required network types if unsupported by API version.
        int jobInfoNetworkType = convertNetworkType(constraints.getRequiredNetworkType());
        PersistableBundle extras = new PersistableBundle();
        extras.putString(EXTRA_WORK_SPEC_ID, workSpec.getId());
        extras.putBoolean(EXTRA_IS_PERIODIC, workSpec.isPeriodic());
        JobInfo.Builder builder = new JobInfo.Builder(jobId, mWorkServiceComponent)
                .setRequiredNetworkType(jobInfoNetworkType)
                .setRequiresCharging(constraints.requiresCharging())
                .setRequiresDeviceIdle(constraints.requiresDeviceIdle())
                .setExtras(extras);

        if (!constraints.requiresDeviceIdle()) {
            // Device Idle and Backoff Criteria cannot be set together
            int backoffPolicy = workSpec.getBackoffPolicy() == BackoffPolicy.LINEAR
                    ? JobInfo.BACKOFF_POLICY_LINEAR : JobInfo.BACKOFF_POLICY_EXPONENTIAL;
            builder.setBackoffCriteria(workSpec.getBackoffDelayDuration(), backoffPolicy);
        }

        if (workSpec.isPeriodic()) {
            builder.setPeriodic(workSpec.getIntervalDuration(), workSpec.getFlexDuration());
        } else {
            // Even if a Work has no constraints, setMinimumLatency(0) still needs to be called due
            // to an issue in JobInfo.Builder#build and JobInfo with no constraints. See b/67716867.
            builder.setMinimumLatency(workSpec.getInitialDelay());
        }

        if (constraints.hasContentUriTriggers()) {
            for (ContentUriTriggers.Trigger trigger : constraints.getContentUriTriggers()) {
                builder.addTriggerContentUri(convertContentUriTrigger(trigger));
            }
        } else {
            // Jobs with Content Uri Triggers cannot be persisted
            builder.setPersisted(true);
        }

        // TODO(janclarin): Support requires[Battery|Storage]NotLow for versions older than 26.
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setRequiresBatteryNotLow(constraints.requiresBatteryNotLow());
            builder.setRequiresStorageNotLow(constraints.requiresStorageNotLow());
        }
        return builder.build();
    }

    private static JobInfo.TriggerContentUri convertContentUriTrigger(
            ContentUriTriggers.Trigger trigger) {
        int flag = trigger.shouldTriggerForDescendants()
                ? JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS : 0;
        return new JobInfo.TriggerContentUri(trigger.getUri(), flag);
    }

    /**
     * Converts {@link NetworkType} into {@link JobInfo}'s network values.
     *
     * @param networkType The {@link NetworkType} network type
     * @return The {@link JobInfo} network type
     */
    static int convertNetworkType(NetworkType networkType) {
        switch(networkType) {
            case NOT_REQUIRED:
                return JobInfo.NETWORK_TYPE_NONE;
            case CONNECTED:
                return JobInfo.NETWORK_TYPE_ANY;
            case UNMETERED:
                return JobInfo.NETWORK_TYPE_UNMETERED;
            case NOT_ROAMING:
                return JobInfo.NETWORK_TYPE_NOT_ROAMING;
            case METERED:
                if (Build.VERSION.SDK_INT >= 26) {
                    return JobInfo.NETWORK_TYPE_METERED;
                }
                break;
        }
        Logger.debug(TAG, "API version too low. Cannot convert network type value %s", networkType);
        return JobInfo.NETWORK_TYPE_ANY;
    }
}
