/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.integration.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** The main activity. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private boolean mCheckedPermissions = false;
    private boolean mUsePreviewView = false;
    private static final int PREVIEW_UPDATE_COUNT = 6;
    private CountDownLatch mPreviewUpdatingLatch = new CountDownLatch(PREVIEW_UPDATE_COUNT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getAction()
                .equals("androidx.camera.integration.view.action.PREVIEWVIEWAPP")) {
            mUsePreviewView = true;
        }

        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (allPermissionsGranted()) {
                    startCamera();
                } else if (!mCheckedPermissions) {
                    requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    mCheckedPermissions = true;
                }
            } else {
                startCamera();
            }
        }
    }

    // Here we use OnPreDrawListener in ViewTreeObserver to detect if view is being updating.
    // If yes, preview should be working to update the view. We could use more precise approach
    // like TextureView.SurfaceTextureListener#onSurfaceTextureUpdated but it will require to add
    // API in PreviewView which is not a good idea. And we use OnPreDrawListener instead of
    // OnDrawListener because OnDrawListener is not invoked on some low API level devices.
    private ViewTreeObserver.OnPreDrawListener mOnPreDrawListener = () -> {
        mPreviewUpdatingLatch.countDown();
        return true;
    };


    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);

    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(
                mOnPreDrawListener);
    }

    /**
     * Waiting for preview update at least PREVIEW_UPDATE_COUNT times.  Returns true if preview
     * updated successfully, otherwise return false after timeout.
     */
    @VisibleForTesting
    public boolean waitForPreviewUpdating(long timeOutInMs) {
        mPreviewUpdatingLatch = new CountDownLatch(PREVIEW_UPDATE_COUNT);

        try {
            return mPreviewUpdatingLatch.await(timeOutInMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                report("Permissions not granted by the user.");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actionbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.camera_view:
                mUsePreviewView = false;
                startCameraView();
                return true;
            case R.id.preview_view:
                mUsePreviewView = true;
                startPreviewView();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        if (mUsePreviewView) {
            startPreviewView();
        } else {
            startCameraView();
        }
    }

    private void startCameraView() {
        getSupportActionBar().setTitle(R.string.camera_view);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, new CameraViewFragment())
                .commit();
    }

    private void startPreviewView() {
        getSupportActionBar().setTitle(R.string.preview_view);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, new PreviewViewFragment())
                .commit();
    }

    private void report(String msg) {
        Log.d(TAG, msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
