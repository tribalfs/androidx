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
package android.arch.background.workmanager.impl.background.systemalarm;

import static android.arch.background.workmanager.NetworkType.NOT_REQUIRED;

import android.arch.background.workmanager.Constraints;
import android.arch.background.workmanager.impl.logger.Logger;
import android.arch.background.workmanager.impl.model.WorkSpec;
import android.arch.background.workmanager.impl.utils.PackageManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

abstract class ConstraintProxy extends BroadcastReceiver {
    private static final String TAG = "ConstraintProxy";

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.debug(TAG, "onReceive : %s", intent);
        Intent constraintChangedIntent = CommandHandler.createConstraintsChangedIntent(context);
        context.startService(constraintChangedIntent);
    }

    /**
     * Proxy for Battery Not Low constraint
     */
    public static class BatteryNotLowProxy extends ConstraintProxy {
    }

    /**
     * Proxy for Battery Charging constraint
     */
    public static class BatteryChargingProxy extends ConstraintProxy {
    }

    /**
     * Proxy for Storage Not Low constraint
     */
    public static class StorageNotLowProxy extends ConstraintProxy {
    }

    /**
     * Proxy for Network State constraints
     */
    public static class NetworkStateProxy extends ConstraintProxy {
    }

    /**
     * Enables/Disables proxies based on constraints in {@link WorkSpec}s
     *
     * @param context   {@link Context}
     * @param workSpecs list of {@link WorkSpec}s to update proxies against
     */
    static void updateAll(Context context, List<WorkSpec> workSpecs) {
        boolean batteryNotLowProxyEnabled = false;
        boolean batteryChargingProxyEnabled = false;
        boolean storageNotLowProxyEnabled = false;
        boolean networkStateProxyEnabled = false;

        for (WorkSpec workSpec : workSpecs) {
            Constraints constraints = workSpec.getConstraints();
            batteryNotLowProxyEnabled |= constraints.requiresBatteryNotLow();
            batteryChargingProxyEnabled |= constraints.requiresCharging();
            storageNotLowProxyEnabled |= constraints.requiresStorageNotLow();
            networkStateProxyEnabled |=
                    constraints.getRequiredNetworkType() != NOT_REQUIRED;

            if (batteryNotLowProxyEnabled && batteryChargingProxyEnabled
                    && storageNotLowProxyEnabled && networkStateProxyEnabled) {
                break;
            }
        }

        PackageManagerHelper.setComponentEnabled(context, BatteryNotLowProxy.class,
                batteryNotLowProxyEnabled);
        PackageManagerHelper.setComponentEnabled(context, BatteryChargingProxy.class,
                batteryChargingProxyEnabled);
        PackageManagerHelper.setComponentEnabled(context, StorageNotLowProxy.class,
                storageNotLowProxyEnabled);
        PackageManagerHelper.setComponentEnabled(context, NetworkStateProxy.class,
                networkStateProxyEnabled);
    }
}
