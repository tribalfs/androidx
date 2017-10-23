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
package android.arch.background.workmanager.constraints.controllers;

import android.arch.background.workmanager.WorkDatabase;
import android.arch.background.workmanager.constraints.listeners.BatteryChargingListener;
import android.arch.background.workmanager.constraints.trackers.Trackers;
import android.arch.lifecycle.LifecycleOwner;
import android.content.Context;

/**
 * A {@link ConstraintController} for battery charging events.
 */

public class BatteryChargingController extends ConstraintController<BatteryChargingListener> {

    private boolean mIsBatteryCharging;
    private final BatteryChargingListener mChargingListener = new BatteryChargingListener() {
        @Override
        public void setBatteryCharging(boolean isBatteryCharging) {
            mIsBatteryCharging = isBatteryCharging;
            updateListener();
        }
    };

    public BatteryChargingController(
            Context context,
            WorkDatabase workDatabase,
            LifecycleOwner lifecycleOwner,
            OnConstraintUpdatedListener onConstraintUpdatedListener) {
        super(
                workDatabase.workSpecDao()
                        .getEnqueuedOrRunningWorkSpecIdsWithBatteryChargingConstraint(),
                lifecycleOwner,
                Trackers.getInstance(context).getBatteryChargingTracker(),
                onConstraintUpdatedListener);
    }

    @Override
    BatteryChargingListener getListener() {
        return mChargingListener;
    }

    @Override
    boolean isConstrained() {
        return !mIsBatteryCharging;
    }
}
