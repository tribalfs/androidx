/*
 * Copyright 2023 The Android Open Source Project
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

package com.example.androidx.mediarouting.activities.systemrouting;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.androidx.mediarouting.R;
import com.example.androidx.mediarouting.activities.systemrouting.source.AndroidXMediaRouterSystemRoutesSource;
import com.example.androidx.mediarouting.activities.systemrouting.source.AudioManagerSystemRoutesSource;
import com.example.androidx.mediarouting.activities.systemrouting.source.BluetoothManagerSystemRoutesSource;
import com.example.androidx.mediarouting.activities.systemrouting.source.MediaRouter2SystemRoutesSource;
import com.example.androidx.mediarouting.activities.systemrouting.source.MediaRouterSystemRoutesSource;
import com.example.androidx.mediarouting.activities.systemrouting.source.SystemRoutesSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows available system routes gathered from different sources.
 */
public final class SystemRoutingActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_BLUETOOTH_CONNECT = 4199;

    private final SystemRoutesAdapter mSystemRoutesAdapter = new SystemRoutesAdapter();
    private final List<SystemRoutesSource> mSystemRoutesSources = new ArrayList<>();

    /**
     * Creates and launches an intent to start current activity.
     */
    public static void launch(@NonNull Context context) {
        Intent intent = new Intent(context, SystemRoutingActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_routing);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.pull_to_refresh_layout);

        recyclerView.setAdapter(mSystemRoutesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        swipeRefreshLayout.setOnRefreshListener(
                () -> {
                    refreshSystemRoutesList();
                    swipeRefreshLayout.setRefreshing(false);
                });

        if (hasBluetoothPermission()) {
            initializeSystemRoutesSources();
            refreshSystemRoutesList();
        } else {
            requestBluetoothPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_BLUETOOTH_CONNECT
                && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onBluetoothPermissionGranted();
            } else {
                onBluetoothPermissionDenied();
            }
        }
    }

    private void refreshSystemRoutesList() {
        List<SystemRouteItem> systemRoutes = new ArrayList<>();
        for (SystemRoutesSource source : mSystemRoutesSources) {
            systemRoutes.addAll(source.fetchRoutes());
        }
        mSystemRoutesAdapter.setItems(systemRoutes);
    }

    private boolean hasBluetoothPermission() {
        return ContextCompat.checkSelfPermission(
                        /* context= */ this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_CODE_BLUETOOTH_CONNECT);
    }

    private void onBluetoothPermissionGranted() {
        initializeSystemRoutesSources();
        refreshSystemRoutesList();
    }

    private void onBluetoothPermissionDenied() {
        Toast.makeText(this, getString(R.string.system_routing_activity_bluetooth_denied),
                Toast.LENGTH_LONG).show();
    }

    private void initializeSystemRoutesSources() {
        mSystemRoutesSources.clear();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mSystemRoutesSources.add(MediaRouterSystemRoutesSource.create(/* context= */ this));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mSystemRoutesSources.add(MediaRouter2SystemRoutesSource.create(/* context= */ this));
        }

        mSystemRoutesSources.add(AndroidXMediaRouterSystemRoutesSource.create(/* context= */ this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && hasBluetoothPermission()) {
            mSystemRoutesSources.add(
                    BluetoothManagerSystemRoutesSource.create(/* context= */ this));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mSystemRoutesSources.add(AudioManagerSystemRoutesSource.create(/* context= */ this));
        }
    }
}
