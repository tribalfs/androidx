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

import android.app.job.JobInfo;
import android.arch.background.workmanager.model.Constraints;
import android.arch.background.workmanager.model.WorkSpec;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

/**
 * Converts a {@link WorkSpec} into a JobInfo.
 */
@RequiresApi(api = 21)
class JobSchedulerConverter implements WorkSpecConverter<JobInfo> {
    private static final String TAG = "JobSchedulerConverter";

    private final ComponentName mWorkServiceComponent;

    JobSchedulerConverter(@NonNull Context context) {
        mWorkServiceComponent = new ComponentName(context, WorkService.class);
    }

    @Override
    public JobInfo convert(WorkSpec workSpec) {
        Constraints constraints = workSpec.getConstraints();
        int jobId = generateJobId(workSpec.getId());
        int jobNetworkType = convertNetworkType(constraints.getRequiredNetworkType());
        JobInfo.Builder builder =
                new JobInfo.Builder(jobId, mWorkServiceComponent)
                        .setMinimumLatency(constraints.getInitialDelay())
                        .setRequiredNetworkType(jobNetworkType)
                        .setRequiresCharging(constraints.requiresCharging())
                        .setRequiresDeviceIdle(constraints.requiresDeviceIdle());

        if (Build.VERSION.SDK_INT >= 26) {
            builder.setRequiresBatteryNotLow(constraints.requiresBatteryNotLow());
            builder.setRequiresStorageNotLow(constraints.requiresStorageNotLow());
        } else {
            // TODO(janclarin): Create compat version of batteryNotLow/storageNotLow constraints.
            Log.w(TAG, "Could not set requiresBatteryNowLow or requiresStorageNotLow constraints.");
        }
        return builder.build();
    }

    @Override
    public int convertNetworkType(@Constraints.NetworkType int networkType)
            throws IllegalArgumentException {
        switch(networkType) {
            case Constraints.NETWORK_TYPE_ANY:
                return JobInfo.NETWORK_TYPE_NONE;
            case Constraints.NETWORK_TYPE_CONNECTED:
                return JobInfo.NETWORK_TYPE_ANY;
            case Constraints.NETWORK_TYPE_UNMETERED:
                return JobInfo.NETWORK_TYPE_UNMETERED;
            case Constraints.NETWORK_TYPE_NOT_ROAMING:
                if (Build.VERSION.SDK_INT >= 24) {
                    return JobInfo.NETWORK_TYPE_NOT_ROAMING;
                }
                break;
            case Constraints.NETWORK_TYPE_METERED:
                if (Build.VERSION.SDK_INT >= 26) {
                    return JobInfo.NETWORK_TYPE_METERED;
                }
                break;
        }
        throw new IllegalArgumentException("NetworkType of " + networkType + " is not supported.");
    }

    // TODO(janclarin): Store UUID mapping with incrementing integer work ID.
    static int generateJobId(String uuid) {
        return uuid.hashCode();
    }
}
