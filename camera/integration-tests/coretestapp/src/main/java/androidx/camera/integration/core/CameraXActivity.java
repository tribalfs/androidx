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

package androidx.camera.integration.core;

import static androidx.camera.core.ImageCapture.FLASH_MODE_AUTO;
import static androidx.camera.core.ImageCapture.FLASH_MODE_OFF;
import static androidx.camera.core.ImageCapture.FLASH_MODE_ON;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.UseCase;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.VideoCaptureConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.math.BigDecimal;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An activity with four use cases: (1) view finder, (2) image capture, (3) image analysis, (4)
 * video capture.
 *
 * <p>All four use cases are created with CameraX and tied to the activity's lifecycle. CameraX
 * automatically connects and disconnects the use cases from the camera in response to changes in
 * the activity's lifecycle. Therefore, the use cases function properly when the app is paused and
 * resumed and when the device is rotated. The complex interactions between the camera and these
 * lifecycle events are handled internally by CameraX.
 */
public class CameraXActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "CameraXActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 42;
    // Possible values for this intent key: "backward" or "forward".
    private static final String INTENT_EXTRA_CAMERA_DIRECTION = "camera_direction";
    static final CameraSelector BACK_SELECTOR =
            new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
    static final CameraSelector FRONT_SELECTOR =
            new CameraSelector.Builder().requireLensFacing(
                    CameraSelector.LENS_FACING_FRONT).build();

    private boolean mPermissionsGranted = false;
    private CallbackToFutureAdapter.Completer<Boolean> mPermissionsCompleter;
    private final AtomicLong mImageAnalysisFrameCount = new AtomicLong(0);
    private final MutableLiveData<String> mImageAnalysisResult = new MutableLiveData<>();
    private VideoFileSaver mVideoFileSaver;
    /** The camera to use */
    CameraSelector mCurrentCameraSelector = BACK_SELECTOR;
    @CameraSelector.LensFacing
    int mCurrentCameraLensFacing = CameraSelector.LENS_FACING_BACK;
    ProcessCameraProvider mCameraProvider;

    // TODO: Move the analysis processing, capture processing to separate threads, so
    // there is smaller impact on the preview.
    private String mCurrentCameraDirection = "BACKWARD";
    private Preview mPreview;
    private ImageAnalysis mImageAnalysis;
    private ImageCapture mImageCapture;
    private VideoCapture mVideoCapture;
    private Camera mCamera;
    @ImageCapture.CaptureMode
    private int mCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
    // Synthetic Accessor
    @SuppressWarnings("WeakerAccess")
    TextureView mTextureView;

    // Espresso testing variables
    @VisibleForTesting
    CountingIdlingResource mViewIdlingResource = new CountingIdlingResource("view");
    private static final int FRAMES_UNTIL_VIEW_IS_READY = 5;
    @VisibleForTesting
    CountingIdlingResource mAnalysisIdlingResource =
            new CountingIdlingResource("analysis");
    @VisibleForTesting
    final CountingIdlingResource mImageSavedIdlingResource =
            new CountingIdlingResource("imagesaved");

    /**
     * Retrieve idling resource that waits for capture to complete (save or error).
     *
     * @return idline resource for image capture
     */
    @VisibleForTesting
    @NonNull
    public IdlingResource getImageSavedIdlingResource() {
        return mImageSavedIdlingResource;
    }

    /**
     * Creates a view finder use case.
     *
     * <p>This use case observes a {@link SurfaceTexture}. The texture is connected to a {@link
     * TextureView} to display a camera preview.
     */
    private void createPreview() {
        Button button = this.findViewById(R.id.PreviewToggle);
        button.setBackgroundColor(Color.LTGRAY);
        enablePreview();

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Button buttonView = (Button) view;
                        if (mPreview != null) {
                            // Remove the use case
                            buttonView.setBackgroundColor(Color.RED);
                            mCameraProvider.unbind(mPreview);
                            mPreview = null;
                        } else {
                            // Add the use case
                            buttonView.setBackgroundColor(Color.LTGRAY);
                            enablePreview();
                        }
                    }
                });

        Log.i(TAG, "Got UseCase: " + mPreview);
    }

    void enablePreview() {
        mPreview = new Preview.Builder()
                .setTargetName("Preview")
                .build();
        Log.d(TAG, "enablePreview");

        mPreview.setPreviewSurfaceProvider(
                (resolution, surfaceReleaseFuture) -> CallbackToFutureAdapter.getFuture(
                        completer -> {
                            ((PreviewSurfaceTextureListener) mTextureView
                                    .getSurfaceTextureListener())
                                    .setSurfaceFutureCompleter(completer,
                                            resolution, surfaceReleaseFuture);
                            return "PreviewSurfaceCreation";
                        }));

        for (int i = 0; i < FRAMES_UNTIL_VIEW_IS_READY; i++) {
            mViewIdlingResource.increment();
        }

        if (bindToLifecycleSafely(mPreview, R.id.PreviewToggle) == null) {
            mPreview = null;
            return;
        }
    }

    void transformPreview(@NonNull Size resolution) {
        if (resolution.getWidth() == 0 || resolution.getHeight() == 0) {
            return;
        }

        if (mTextureView.getWidth() == 0 || mTextureView.getHeight() == 0) {
            return;
        }

        Matrix matrix = new Matrix();

        int left = mTextureView.getLeft();
        int right = mTextureView.getRight();
        int top = mTextureView.getTop();
        int bottom = mTextureView.getBottom();

        // Compute the preview ui size based on the available width, height, and ui orientation.
        int viewWidth = (right - left);
        int viewHeight = (bottom - top);

        int displayRotation = getDisplayRotation();
        Size scaled =
                calculatePreviewViewDimens(
                        resolution, viewWidth, viewHeight, displayRotation);

        // Compute the center of the view.
        int centerX = viewWidth / 2;
        int centerY = viewHeight / 2;

        // Do corresponding rotation to correct the preview direction
        matrix.postRotate(-getDisplayRotation(), centerX, centerY);

        // Compute the scale value for center crop mode
        float xScale = scaled.getWidth() / (float) viewWidth;
        float yScale = scaled.getHeight() / (float) viewHeight;

        if (getDisplayRotation() == 90 || getDisplayRotation() == 270) {
            xScale = scaled.getWidth() / (float) viewHeight;
            yScale = scaled.getHeight() / (float) viewWidth;
        }

        // Only two digits after the decimal point are valid for postScale. Need to get ceiling of
        // two
        // digits floating value to do the scale operation. Otherwise, the result may be scaled not
        // large enough and will have some blank lines on the screen.
        xScale = new BigDecimal(xScale).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
        yScale = new BigDecimal(yScale).setScale(2, BigDecimal.ROUND_CEILING).floatValue();

        // Do corresponding scale to resolve the deformation problem
        matrix.postScale(xScale, yScale, centerX, centerY);

        // Compute the new left/top positions to do translate
        int layoutL = centerX - (scaled.getWidth() / 2);
        int layoutT = centerY - (scaled.getHeight() / 2);

        mTextureView.setTransform(matrix);
    }

    /** @return One of 0, 90, 180, 270. */
    private int getDisplayRotation() {
        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

        switch (displayRotation) {
            case Surface.ROTATION_0:
                displayRotation = 0;
                break;
            case Surface.ROTATION_90:
                displayRotation = 90;
                break;
            case Surface.ROTATION_180:
                displayRotation = 180;
                break;
            case Surface.ROTATION_270:
                displayRotation = 270;
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported display rotation: " + displayRotation);
        }

        return displayRotation;
    }

    private Size calculatePreviewViewDimens(
            Size srcSize, int parentWidth, int parentHeight, int displayRotation) {
        int inWidth = srcSize.getWidth();
        int inHeight = srcSize.getHeight();
        if (displayRotation == 0 || displayRotation == 180) {
            // Need to reverse the width and height since we're in landscape orientation.
            inWidth = srcSize.getHeight();
            inHeight = srcSize.getWidth();
        }

        int outWidth = parentWidth;
        int outHeight = parentHeight;
        if (inWidth != 0 && inHeight != 0) {
            float vfRatio = inWidth / (float) inHeight;
            float parentRatio = parentWidth / (float) parentHeight;

            // Match shortest sides together.
            if (vfRatio < parentRatio) {
                outWidth = parentWidth;
                outHeight = Math.round(parentWidth / vfRatio);
            } else {
                outWidth = Math.round(parentHeight * vfRatio);
                outHeight = parentHeight;
            }
        }

        return new Size(outWidth, outHeight);
    }

    /**
     * Creates an image analysis use case.
     *
     * <p>This use case observes a stream of analysis results computed from the frames.
     */
    private void createImageAnalysis() {
        Button button = this.findViewById(R.id.AnalysisToggle);
        button.setBackgroundColor(Color.LTGRAY);
        enableImageAnalysis();

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Button buttonView = (Button) view;
                        if (mImageAnalysis != null) {
                            // Remove the use case
                            buttonView.setBackgroundColor(Color.RED);
                            mCameraProvider.unbind(mImageAnalysis);
                            mImageAnalysis = null;
                        } else {
                            // Add the use case
                            buttonView.setBackgroundColor(Color.LTGRAY);
                            CameraXActivity.this.enableImageAnalysis();
                        }
                    }
                });

        Log.i(TAG, "Got UseCase: " + mImageAnalysis);
    }

    void enableImageAnalysis() {
        mImageAnalysis = new ImageAnalysis.Builder()
                .setTargetName("ImageAnalysis")
                .build();
        TextView textView = this.findViewById(R.id.textView);
        mAnalysisIdlingResource.increment();

        if (bindToLifecycleSafely(mImageAnalysis, R.id.AnalysisToggle) == null) {
            mImageAnalysis = null;
            return;
        }

        mImageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this),
                (image) -> {
                    // Since we set the callback handler to a main thread handler, we can call
                    // setValue() here. If we weren't on the main thread, we would have to call
                    // postValue() instead.
                    mImageAnalysisResult.setValue(
                            Long.toString(image.getImageInfo().getTimestamp()));

                    if (!mAnalysisIdlingResource.isIdleNow()) {
                        mAnalysisIdlingResource.decrement();
                    }

                    image.close();
                }
        );
        mImageAnalysisResult.observe(
                this,
                new Observer<String>() {
                    @Override
                    public void onChanged(String text) {
                        if (mImageAnalysisFrameCount.getAndIncrement() % 30 == 0) {
                            textView.setText(
                                    "ImgCount: " + mImageAnalysisFrameCount.get() + " @ts: "
                                            + text);
                        }
                    }
                });
    }

    /**
     * Creates an image capture use case.
     *
     * <p>This use case takes a picture and saves it to a file, whenever the user clicks a button.
     */
    private void createImageCapture() {

        Button button = this.findViewById(R.id.PhotoToggle);
        button.setBackgroundColor(Color.LTGRAY);
        enableImageCapture();

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Button buttonView = (Button) view;
                        if (mImageCapture != null) {
                            // Remove the use case
                            buttonView.setBackgroundColor(Color.RED);
                            CameraXActivity.this.disableImageCapture();
                        } else {
                            // Add the use case
                            buttonView.setBackgroundColor(Color.LTGRAY);
                            CameraXActivity.this.enableImageCapture();
                        }
                    }
                });

        Log.i(TAG, "Got UseCase: " + mImageCapture);
    }

    void enableImageCapture() {
        mImageCapture = new ImageCapture.Builder()
                .setCaptureMode(mCaptureMode)
                .setTargetName("ImageCapture")
                .build();

        Camera camera = bindToLifecycleSafely(mImageCapture, R.id.PhotoToggle);
        if (camera == null) {
            Button button = this.findViewById(R.id.Picture);
            button.setOnClickListener(null);
            mImageCapture = null;
            return;
        }

        Button button = this.findViewById(R.id.Picture);
        final Format formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
        final File dir = this.getExternalFilesDir(null);
        button.setOnClickListener(
                new View.OnClickListener() {
                    long mStartCaptureTime = 0;

                    @Override
                    public void onClick(View view) {
                        mImageSavedIdlingResource.increment();

                        mStartCaptureTime = SystemClock.elapsedRealtime();
                        mImageCapture.takePicture(
                                new File(
                                        dir,
                                        formatter.format(Calendar.getInstance().getTime())
                                                + ".jpg"),
                                ContextCompat.getMainExecutor(CameraXActivity.this),
                                new ImageCapture.OnImageSavedCallback() {
                                    @Override
                                    public void onImageSaved(@NonNull File file) {
                                        Log.d(TAG, "Saved image to " + file);
                                        try {
                                            mImageSavedIdlingResource.decrement();
                                        } catch (IllegalStateException e) {
                                            Log.e(TAG, "Error: unexpected onImageSaved "
                                                    + "callback received. Continuing.");
                                        }

                                        long duration =
                                                SystemClock.elapsedRealtime() - mStartCaptureTime;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(CameraXActivity.this,
                                                        "Image captured in " + duration + " ms",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }

                                    @Override
                                    public void onError(
                                            @ImageCapture.ImageCaptureError int error,
                                            @NonNull String message,
                                            Throwable cause) {
                                        Log.e(TAG, "Failed to save image.", cause);
                                        try {
                                            mImageSavedIdlingResource.decrement();
                                        } catch (IllegalStateException e) {
                                            Log.e(TAG, "Error: unexpected onImageSaved "
                                                    + "callback received. Continuing.");
                                        }
                                    }
                                });
                    }
                });

        refreshFlashButton();


        Button btnCaptureQuality = this.findViewById(R.id.capture_quality);
        btnCaptureQuality.setVisibility(View.VISIBLE);
        btnCaptureQuality.setText(
                mCaptureMode == ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY ? "MAX" : "MIN");
        btnCaptureQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCaptureMode = (mCaptureMode == ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        ? ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        : ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);
                rebindUseCases();
            }
        });

    }

    void disableImageCapture() {
        mCameraProvider.unbind(mImageCapture);

        mImageCapture = null;
        Button button = this.findViewById(R.id.Picture);
        button.setOnClickListener(null);

        Button btnCaptureQuality = this.findViewById(R.id.capture_quality);
        btnCaptureQuality.setVisibility(View.GONE);

        refreshFlashButton();
    }

    private void refreshFlashButton() {
        ImageButton flashToggle = findViewById(R.id.flash_toggle);
        if (mImageCapture != null) {
            CameraInfo cameraInfo = getCameraInfo();
            if (cameraInfo != null && cameraInfo.hasFlashUnit()) {
                flashToggle.setVisibility(View.VISIBLE);
                flashToggle.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        @ImageCapture.FlashMode int flashMode = mImageCapture.getFlashMode();
                        if (flashMode == FLASH_MODE_ON) {
                            mImageCapture.setFlashMode(FLASH_MODE_OFF);
                        } else if (flashMode == FLASH_MODE_OFF) {
                            mImageCapture.setFlashMode(FLASH_MODE_AUTO);
                        } else if (flashMode == FLASH_MODE_AUTO) {
                            mImageCapture.setFlashMode(FLASH_MODE_ON);
                        }
                        refreshFlashButtonIcon();
                    }
                });
                refreshFlashButtonIcon();
            } else {
                flashToggle.setVisibility(View.INVISIBLE);
                flashToggle.setOnClickListener(null);
            }
        } else {
            flashToggle.setVisibility(View.GONE);
            flashToggle.setOnClickListener(null);
        }
    }

    private void refreshFlashButtonIcon() {
        ImageButton flashToggle = findViewById(R.id.flash_toggle);
        @ImageCapture.FlashMode int flashMode = mImageCapture.getFlashMode();
        switch (flashMode) {
            case FLASH_MODE_ON:
                flashToggle.setImageResource(R.drawable.ic_flash_on);
                break;
            case FLASH_MODE_OFF:
                flashToggle.setImageResource(R.drawable.ic_flash_off);
                break;
            case FLASH_MODE_AUTO:
                flashToggle.setImageResource(R.drawable.ic_flash_auto);
                break;
        }
    }

    private void refreshTorchButton() {
        ImageButton torchToggle = findViewById(R.id.torch_toggle);
        CameraInfo cameraInfo = getCameraInfo();
        if (cameraInfo != null && cameraInfo.hasFlashUnit()) {
            torchToggle.setVisibility(View.VISIBLE);
            torchToggle.setOnClickListener(v -> {
                Integer torchState = cameraInfo.getTorchState().getValue();
                boolean toggledState = !(torchState == TorchState.ON);
                CameraControl cameraControl = getCameraControl();
                if (cameraControl != null) {
                    Log.d(TAG, "Set camera torch: " + toggledState);
                    cameraControl.enableTorch(toggledState);
                }
            });
        } else {
            Log.d(TAG, "No flash unit");
            torchToggle.setVisibility(View.INVISIBLE);
            torchToggle.setOnClickListener(null);
        }
    }

    /**
     * Creates a video capture use case.
     *
     * <p>This use case records a video segment and saves it to a file, in response to user button
     * clicks.
     */
    private void createVideoCapture() {
        Button button = this.findViewById(R.id.VideoToggle);
        button.setBackgroundColor(Color.LTGRAY);
        enableVideoCapture();

        mVideoFileSaver = new VideoFileSaver();
        mVideoFileSaver.setRootDirectory(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));

        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Button buttonView = (Button) view;
                        if (mVideoCapture != null) {
                            // Remove the use case
                            buttonView.setBackgroundColor(Color.RED);
                            CameraXActivity.this.disableVideoCapture();
                        } else {
                            // Add the use case
                            buttonView.setBackgroundColor(Color.LTGRAY);
                            CameraXActivity.this.enableVideoCapture();
                        }
                    }
                });

        Log.i(TAG, "Got UseCase: " + mVideoCapture);
    }

    void enableVideoCapture() {
        mVideoCapture = new VideoCaptureConfig.Builder()
                .setTargetName("VideoCapture")
                .build();

        if (bindToLifecycleSafely(mVideoCapture, R.id.VideoToggle) == null) {
            Button button = this.findViewById(R.id.Video);
            button.setOnClickListener(null);
            mVideoCapture = null;
            return;
        }

        Button button = this.findViewById(R.id.Video);
        button.setOnClickListener((view) -> {
            Button buttonView = (Button) view;
            String text = button.getText().toString();
            if (text.equals("Record") && !mVideoFileSaver.isSaving()) {
                mVideoCapture.startRecording(
                        mVideoFileSaver.getNewVideoFile(),
                        ContextCompat.getMainExecutor(CameraXActivity.this),
                        mVideoFileSaver);
                mVideoFileSaver.setSaving();
                buttonView.setText("Stop");
            } else if (text.equals("Stop") && mVideoFileSaver.isSaving()) {
                buttonView.setText("Record");
                mVideoCapture.stopRecording();
            } else if (text.equals("Record") && mVideoFileSaver.isSaving()) {
                buttonView.setText("Stop");
                mVideoFileSaver.setSaving();
            } else if (text.equals("Stop") && !mVideoFileSaver.isSaving()) {
                buttonView.setText("Record");
            }
        });
    }

    void disableVideoCapture() {
        Button button = this.findViewById(R.id.Video);
        button.setOnClickListener(null);
        mCameraProvider.unbind(mVideoCapture);

        mVideoCapture = null;
    }

    /** Creates all the use cases. */
    private void createUseCases() {
        createImageCapture();
        createPreview();
        createImageAnalysis();
        createVideoCapture();
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_xmain);
        mTextureView = findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(new PreviewSurfaceTextureListener());

        StrictMode.VmPolicy policy =
                new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build();
        StrictMode.setVmPolicy(policy);

        // Get params from adb extra string
        Bundle bundle = this.getIntent().getExtras();
        if (bundle != null) {
            String newCameraDirection = bundle.getString(INTENT_EXTRA_CAMERA_DIRECTION);
            if (newCameraDirection != null) {
                mCurrentCameraDirection = newCameraDirection;
            }
        }

        ListenableFuture<Void> cameraProviderFuture =
                Futures.transform(ProcessCameraProvider.getInstance(this),
                        provider -> {
                            mCameraProvider = provider;
                            return null;
                        },
                        ContextCompat.getMainExecutor(this));

        ListenableFuture<Void> permissionFuture = Futures.transform(setupPermissions(),
                permissionGranted -> {
                    mPermissionsGranted = Preconditions.checkNotNull(permissionGranted);
                    return null;
                }, ContextCompat.getMainExecutor(this));

        Futures.addCallback(
                Futures.allAsList(cameraProviderFuture, permissionFuture),
                new FutureCallback<List<Void>>() {
                    @Override
                    public void onSuccess(@Nullable List<Void> ignored) {
                        CameraXActivity.this.setupCamera();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        throw new RuntimeException("Initialization failed.", t);
                    }
                }, ContextCompat.getMainExecutor(this));
    }

    void setupCamera() {
        // Check for permissions before proceeding.
        if (!mPermissionsGranted) {
            Log.d(TAG, "Permissions denied.");
            return;
        }

        Log.d(TAG, "Camera direction: " + mCurrentCameraDirection);
        if (mCurrentCameraDirection.equalsIgnoreCase("BACKWARD")) {
            mCurrentCameraSelector = BACK_SELECTOR;
            mCurrentCameraLensFacing = CameraSelector.LENS_FACING_BACK;
        } else if (mCurrentCameraDirection.equalsIgnoreCase("FORWARD")) {
            mCurrentCameraSelector = FRONT_SELECTOR;
            mCurrentCameraLensFacing = CameraSelector.LENS_FACING_FRONT;
        } else {
            throw new RuntimeException("Invalid camera direction: " + mCurrentCameraDirection);
        }
        Log.d(TAG, "Using camera lens facing: " + mCurrentCameraSelector);

        CameraXActivity.this.createUseCases();
        refreshTorchButton();

        ImageButton directionToggle = findViewById(R.id.direction_toggle);
        directionToggle.setVisibility(View.VISIBLE);
        directionToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentCameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                    mCurrentCameraSelector = FRONT_SELECTOR;
                    mCurrentCameraLensFacing = CameraSelector.LENS_FACING_FRONT;
                } else if (mCurrentCameraLensFacing == CameraSelector.LENS_FACING_FRONT) {
                    mCurrentCameraSelector = BACK_SELECTOR;
                    mCurrentCameraLensFacing = CameraSelector.LENS_FACING_BACK;
                }

                Log.d(TAG, "Change camera direction: " + mCurrentCameraSelector);
                rebindUseCases();
                refreshTorchButton();

            }
        });
    }

    private void rebindUseCases() {
        // Rebind all use cases.
        mCameraProvider.unbindAll();
        if (mImageCapture != null) {
            enableImageCapture();
        }
        if (mPreview != null) {
            enablePreview();
        }
        if (mImageAnalysis != null) {
            enableImageAnalysis();
        }
        if (mVideoCapture != null) {
            enableVideoCapture();
        }
    }

    private ListenableFuture<Boolean> setupPermissions() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mPermissionsCompleter = completer;
            if (!allPermissionsGranted()) {
                makePermissionRequest();
            } else {
                mPermissionsCompleter.set(true);
            }

            return "get_permissions";
        });
    }

    private void makePermissionRequest() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
    }

    /** Returns true if all the necessary permissions have been granted already. */
    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Tries to acquire all the necessary permissions through a dialog. */
    private String[] getRequiredPermissions() {
        PackageInfo info;
        try {
            info =
                    getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException exception) {
            Log.e(TAG, "Failed to obtain all required permissions.", exception);
            return new String[0];
        }
        String[] permissions = info.requestedPermissions;
        if (permissions != null && permissions.length > 0) {
            return permissions;
        } else {
            return new String[0];
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permissions Granted.");
                    mPermissionsCompleter.set(true);
                } else {
                    Log.d(TAG, "Permissions Denied.");
                    mPermissionsCompleter.set(false);
                }
                return;
            }
            default:
                // No-op
        }
    }

    @Nullable
    private Camera bindToLifecycleSafely(UseCase useCase, int buttonViewId) {
        try {
            mCamera = mCameraProvider.bindToLifecycle(this, mCurrentCameraSelector,
                    useCase);
            return mCamera;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(getApplicationContext(), "Bind too many use cases.", Toast.LENGTH_SHORT)
                    .show();
            Button button = this.findViewById(buttonViewId);
            button.setBackgroundColor(Color.RED);
        }
        return null;
    }

    Preview getPreview() {
        return mPreview;
    }

    ImageAnalysis getImageAnalysis() {
        return mImageAnalysis;
    }

    ImageCapture getImageCapture() {
        return mImageCapture;
    }

    VideoCapture getVideoCapture() {
        return mVideoCapture;
    }

    @VisibleForTesting
    @Nullable
    CameraInfo getCameraInfo() {
        return mCamera != null ? mCamera.getCameraInfo() : null;
    }

    @VisibleForTesting
    @Nullable
    CameraControl getCameraControl() {
        return mCamera != null ? mCamera.getCameraControl() : null;
    }

    /**
     * Manages the life cycle of the {@link TextureView} {@link SurfaceTexture} for {@link Preview}
     * use cases.
     *
     * This class is responsible for resolving two race conditions: the creation
     * and the destroy of the {@link Surface}/{@link SurfaceTexture} pair.
     */
    final class PreviewSurfaceTextureListener implements
            TextureView.SurfaceTextureListener {

        @GuardedBy("this")
        CallbackToFutureAdapter.Completer<Surface> mSurfaceCompleter;
        @GuardedBy("this")
        Size mResolution;
        @GuardedBy("this")
        SurfaceTexture mSurfaceTexture;
        @GuardedBy("this")
        ListenableFuture<Void> mSurfaceSafeToReleaseFuture;

        // Whether the handle is still being used by the consumer(TextureView).
        AtomicBoolean mIsSurfaceTextureUsedByTextureView = new AtomicBoolean(true);
        // Whether the handle is still being used by the producer(CameraX).
        AtomicBoolean mIsSurfaceTextureUsedByPreview = new AtomicBoolean(false);

        /**
         * Resolves the race condition in {@link SurfaceTexture} creation.
         */
        private synchronized void tryToSetSurface() {
            if (mResolution != null && mSurfaceTexture != null && mSurfaceCompleter != null) {
                Log.d(TAG, "SurfaceTexture set " + hashCode());
                // Only set Surface when both the consumer and the producer are ready.
                mSurfaceTexture.setDefaultBufferSize(mResolution.getWidth(),
                        mResolution.getHeight());
                final Surface surface = new Surface(mSurfaceTexture);
                mSurfaceCompleter.set(surface);
                mIsSurfaceTextureUsedByPreview.set(true);

                mSurfaceSafeToReleaseFuture.addListener(() -> {
                    Log.d(TAG, "Preview is ready to release Surface." + hashCode());
                    surface.release();
                    mIsSurfaceTextureUsedByPreview.set(false);
                    tryToReleaseSurfaceTexture();
                }, ContextCompat.getMainExecutor(CameraXActivity.this));
            }
        }

        /**
         * Resolves the race condition in {@link SurfaceTexture} releasing.
         */
        private synchronized void tryToReleaseSurfaceTexture() {
            if (!mIsSurfaceTextureUsedByTextureView.get()
                    && !mIsSurfaceTextureUsedByPreview.get()
                    && mSurfaceTexture != null) {
                Log.d(TAG, "SurfaceTexture released " + hashCode());
                // Only release when both the consumer and the producer are done.
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
        }

        synchronized void setSurfaceFutureCompleter(
                CallbackToFutureAdapter.Completer<Surface> surfaceCompleter,
                Size resolution,
                ListenableFuture<Void> surfaceSafeToReleaseFuture) {
            Log.d(TAG, "SurfaceCompleter set. " + hashCode());
            mSurfaceCompleter = surfaceCompleter;
            mResolution = resolution;
            mSurfaceSafeToReleaseFuture = surfaceSafeToReleaseFuture;
            transformPreview(resolution);
            tryToSetSurface();
        }

        @Override
        public synchronized void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width,
                int height) {
            Log.d(TAG, "TextureView is ready to set SurfaceTexture. " + hashCode());
            mSurfaceTexture = surfaceTexture;
            mIsSurfaceTextureUsedByTextureView.set(true);
            tryToSetSurface();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width,
                int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged " + width + "x" + height);
            // No-op.
        }

        @Override
        public synchronized boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.d(TAG, "TextureView is ready to release SurfaceTexture. " + hashCode());
            mIsSurfaceTextureUsedByTextureView.set(false);
            ContextCompat.getMainExecutor(CameraXActivity.this).execute(
                    this::tryToReleaseSurfaceTexture);
            // Never auto release. Premature releasing causes CaptureSession to misbehave on LEGACY
            // devices. Wait for CameraX's signal to release it.
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Wait until surface texture receives enough updates. This is for testing.
            if (!mViewIdlingResource.isIdleNow()) {
                mViewIdlingResource.decrement();
            }
        }
    }
}
