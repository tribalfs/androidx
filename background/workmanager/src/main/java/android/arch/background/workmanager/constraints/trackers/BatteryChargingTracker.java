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
package android.arch.background.workmanager.constraints.trackers;

import android.arch.background.workmanager.constraints.listeners.BatteryChargingListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.support.annotation.VisibleForTesting;

/**
 * A {@link BroadcastReceiver} for battery charging status.
 */

public class BatteryChargingTracker extends ConstraintTracker<BatteryChargingListener> {

    @VisibleForTesting
    Boolean mIsCharging;

    public BatteryChargingTracker(Context context) {
        super(context);
    }

    @Override
    public void setUpInitialState(BatteryChargingListener listener) {
        if (mIsCharging == null) {
            // {@link ACTION_CHARGING} and {@link ACTION_DISCHARGING} are not sticky broadcasts, so
            // we use {@link ACTION_BATTERY_CHANGED} on all APIs to get the initial state.
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);

            Intent intent = mAppContext.registerReceiver(null, intentFilter);
            if (intent != null) {
                mIsCharging = isBatteryChangedIntentCharging(intent);
                listener.setBatteryCharging(mIsCharging);
            }
        } else {
            listener.setBatteryCharging(mIsCharging);
        }
    }

    @Override
    public IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= 23) {
            intentFilter.addAction(BatteryManager.ACTION_CHARGING);
            intentFilter.addAction(BatteryManager.ACTION_DISCHARGING);
        } else {
            intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        }
        return intentFilter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case BatteryManager.ACTION_CHARGING:
                setIsChargingAndNotify(true);
                break;

            case BatteryManager.ACTION_DISCHARGING:
                setIsChargingAndNotify(false);
                break;

            case Intent.ACTION_BATTERY_CHANGED: {
                setIsChargingAndNotify(isBatteryChangedIntentCharging(intent));
                break;
            }
        }
    }

    private void setIsChargingAndNotify(boolean isCharging) {
        if (mIsCharging == null || mIsCharging != isCharging) {
            mIsCharging = isCharging;
            for (BatteryChargingListener listener : mListeners) {
                listener.setBatteryCharging(mIsCharging);
            }
        }
    }

    private boolean isBatteryChangedIntentCharging(Intent intent) {
        boolean charging;
        if (Build.VERSION.SDK_INT >= 23) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL);
        } else {
            int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            charging = (chargePlug != 0);
        }
        return charging;
    }
}
