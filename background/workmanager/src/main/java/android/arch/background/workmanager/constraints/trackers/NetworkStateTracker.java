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
package android.arch.background.workmanager.constraints.trackers;

import android.arch.background.workmanager.constraints.NetworkState;
import android.arch.background.workmanager.constraints.listeners.NetworkStateListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.support.v4.net.ConnectivityManagerCompat;
import android.util.Log;

/**
 * A {@link ConstraintTracker} for monitoring network state.
 * <p>
 * For API 24 and up: Network state is tracked using a registered {@link NetworkCallback} with
 * {@link ConnectivityManager#registerDefaultNetworkCallback(NetworkCallback)}, added in API 24.
 * <p>
 * For API 23 and below: Network state is tracked using a {@link android.content.BroadcastReceiver}.
 * Much less efficient than tracking with {@link NetworkCallback}s and {@link ConnectivityManager}.
 * <p>
 * Based on {@link android.app.job.JobScheduler}'s ConnectivityController on API 26.
 * {@see https://android.googlesource.com/platform/frameworks/base/+/oreo-release/services/core/java/com/android/server/job/controllers/ConnectivityController.java}
 */
public class NetworkStateTracker extends ConstraintTracker<NetworkStateListener> {
    private static final String TAG = "NetworkStateTracker";

    private final ConnectivityManager mConnectivityManager;

    @VisibleForTesting
    NetworkState mCurrentNetworkState;

    @RequiresApi(24)
    private NetworkStateCallback mNetworkCallback;
    private NetworkStateBroadcastReceiver mBroadcastReceiver;

    NetworkStateTracker(Context context) {
        super(context);
        mConnectivityManager =
                (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (isNetworkCallbackSupported()) {
            mNetworkCallback = new NetworkStateCallback();
        } else {
            mBroadcastReceiver = new NetworkStateBroadcastReceiver();
        }
    }

    @Override
    protected void initState() {
        mCurrentNetworkState = getActiveNetworkState();
        Log.d(TAG, "Network state initialized to: " + mCurrentNetworkState);
    }

    @Override
    protected void notifyListener(@NonNull NetworkStateListener listener) {
        if (mCurrentNetworkState != null) {
            listener.setNetworkState(mCurrentNetworkState);
        }
    }

    @Override
    public void startTracking() {
        if (isNetworkCallbackSupported()) {
            Log.d(TAG, "Registering network callback");
            mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
        } else {
            Log.d(TAG, "Registering broadcast receiver");
            mAppContext.registerReceiver(mBroadcastReceiver,
                    new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    @Override
    public void stopTracking() {
        if (isNetworkCallbackSupported()) {
            Log.d(TAG, "Unregistering network callback");
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        } else {
            Log.d(TAG, "Unregistering broadcast receiver");
            mAppContext.unregisterReceiver(mBroadcastReceiver);
        }
    }

    private static boolean isNetworkCallbackSupported() {
        // Based on requiring ConnectivityManager#registerDefaultNetworkCallback - added in API 24.
        return Build.VERSION.SDK_INT >= 24;
    }

    private void setNetworkStateAndNotify(@NonNull final NetworkState networkState) {
        Log.d(TAG, "Attempting to update network state: " + networkState);
        if (mCurrentNetworkState == null || !mCurrentNetworkState.equals(networkState)) {
            mCurrentNetworkState = networkState;
            for (NetworkStateListener listener : mListeners) {
                notifyListener(listener);
            }
            Log.d(TAG, "Updated network state: " + networkState);
        }
    }

    private NetworkState getActiveNetworkState() {
        // Use getActiveNetworkInfo() instead of getNetworkInfo(network) because it can detect VPNs.
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        boolean isConnected = info != null && info.isConnected();
        boolean isValidated = isActiveNetworkValidated();
        boolean isMetered = ConnectivityManagerCompat.isActiveNetworkMetered(mConnectivityManager);
        boolean isNotRoaming = info != null && !info.isRoaming();
        return new NetworkState(isConnected, isValidated, isMetered, isNotRoaming);
    }

    private boolean isActiveNetworkValidated() {
        if (Build.VERSION.SDK_INT < 23) {
            return false; // NET_CAPABILITY_VALIDATED not available until API 23. Used on API 26+.
        }
        Network network = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    @RequiresApi(24)
    private class NetworkStateCallback extends NetworkCallback {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            // The Network parameter is unreliable when a VPN app is running - use active network.
            Log.d(TAG, "Network capabilities changed: " + capabilities);
            setNetworkStateAndNotify(getActiveNetworkState());
        }

        @Override
        public void onLost(Network network) {
            Log.d(TAG, "Network connection lost");
            setNetworkStateAndNotify(getActiveNetworkState());
        }
    }

    private class NetworkStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, "Network broadcast received");
                setNetworkStateAndNotify(getActiveNetworkState());
            }
        }
    }
}
