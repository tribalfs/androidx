/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.camera2.impl;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Looper;

import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraControl.OperationCanceledException;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.math.MathUtils;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Implementation of zoom control used within CameraControl and CameraInfo.
 *
 * <p>It consists of setters and getters. Setters like {@link #setZoomRatio(float)} and
 * {@link #setZoomPercentage(float)} return a {@link ListenableFuture} which apps can
 * use to await the async result.  Getters like {@link #getZoomRatio()},
 * {@link #getZoomPercentage()}, {@link #getMaxZoomRatio()}, and {@link #getMinZoomRatio()}
 * return a {@link LiveData} which apps can get immediate value from by
 * {@link LiveData#getValue()} or observe the changes by
 * {@link LiveData#observe(LifecycleOwner, Observer)}.
 *
 * <p>{@link #setZoomRatio(float)} accepts zoom ratio from {@link #getMinZoomRatio()} to
 * {@link #getMaxZoomRatio()}. Alternatively, app can call {@link #setZoomPercentage(float)} to
 * specify the zoom by percentage. The percentage value is a float ranging from 0 to 1 representing
 * the minimum zoom to maximum zoom respectively. The benefits of using zoom percentage is it
 * ensures the FOV width/height is changed linearly.
 *
 * <p>The operation (the setters) will throw {@link IllegalStateException} if {@link ZoomControl} is
 * not active. All states are reset to default values once it is inactive. We should set active
 * on {@link ZoomControl} when apps are ready to accept zoom operations and set inactive if camera
 * is closing or closed.
 */
final class ZoomControl {
    private static final String TAG = "ZoomControl";
    public static final float DEFAULT_ZOOM_RATIO = 1.0f;
    public static final float MIN_ZOOM = DEFAULT_ZOOM_RATIO;

    private final Camera2CameraControl mCamera2CameraControl;
    private final CameraCharacteristics mCameraCharacteristics;

    // MutableLiveData is thread-safe, thus no needs for synchronization.
    private final MutableLiveData<Float> mZoomRatioLiveData;
    private final MutableLiveData<Float> mMaxZoomRatioLiveData;
    private final MutableLiveData<Float> mMinZoomRatioLiveData;
    // Stores separate percentage data because we want to preserve the exact percentage value which
    // developers pass to us. Inferring the percentage from zoomRatio could be different from it.
    private final MutableLiveData<Float> mZoomPercentageLiveData;

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    final Object mCompleterLock = new Object();
    @GuardedBy("mCompleterLock")
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    CallbackToFutureAdapter.Completer<Void> mPendingZoomRatioCompleter;
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    @GuardedBy("mCompleterLock")
    Rect mPendingZoomCropRegion = null;

    final Object mActiveLock = new Object();

    /**
     * true if it is ready to accept zoom operation. Any zoom operation during inactive state will
     * throw{@link IllegalStateException}.
     */
    @GuardedBy("mActiveLock")
    private boolean mIsActive = false;

    ZoomControl(@NonNull Camera2CameraControl camera2CameraControl,
            @NonNull CameraCharacteristics cameraCharacteristics) {
        mCamera2CameraControl = camera2CameraControl;
        mCameraCharacteristics = cameraCharacteristics;

        mZoomRatioLiveData = new MutableLiveData<>(DEFAULT_ZOOM_RATIO);
        mMaxZoomRatioLiveData = new MutableLiveData<>(getMaxDigitalZoom());
        mMinZoomRatioLiveData = new MutableLiveData<>(MIN_ZOOM);
        mZoomPercentageLiveData = new MutableLiveData<>(0f);
        camera2CameraControl.addCaptureResultListener(mCaptureResultListener);
    }

    /**
     * Set current active state. Set active if it is ready to accept zoom operations.
     *
     * <p>Any zoom operation during inactive state will do nothing and report a error in
     * ListenableFuture. All zoom states are reset to default once it is changed to inactive state.
     */
    @WorkerThread
    void setActive(boolean isActive) {
        CallbackToFutureAdapter.Completer<Void> completerToSetException = null;
        boolean shouldResetDefault = false;

        // Only do variable assignment within the synchronized block to prevent form dead lock.
        synchronized (mActiveLock) {
            if (mIsActive == isActive) {
                return;
            }

            mIsActive = isActive;

            if (!mIsActive) {
                // Fails the pending ListenableFuture.
                synchronized (mCompleterLock) {
                    if (mPendingZoomRatioCompleter != null) {
                        completerToSetException = mPendingZoomRatioCompleter;
                        mPendingZoomRatioCompleter = null;
                        mPendingZoomCropRegion = null;
                    }
                }

                // Reset all values if zoomControl is inactive.
                shouldResetDefault = true;
            }
        }

        if (shouldResetDefault) {
            setLiveDataValue(mZoomRatioLiveData, DEFAULT_ZOOM_RATIO);
            setLiveDataValue(mZoomPercentageLiveData, 0f);
            mCamera2CameraControl.setCropRegion(null);
        }

        if (completerToSetException != null) {
            completerToSetException
                    .setException(new OperationCanceledException("Camera is not active."));
        }
    }

    private Camera2CameraControl.CaptureResultListener mCaptureResultListener =
            new Camera2CameraControl.CaptureResultListener() {
                @WorkerThread
                @Override
                public boolean onCaptureResult(@NonNull TotalCaptureResult captureResult) {
                    // Compare the requested crop region, not the result's crop region because HAL
                    // could modify the requested crop region.
                    CallbackToFutureAdapter.Completer<Void> completerToSet = null;
                    synchronized (mCompleterLock) {
                        if (mPendingZoomRatioCompleter != null) {
                            CaptureRequest request = captureResult.getRequest();
                            Rect cropRect = (request == null) ? null :
                                    request.get(CaptureRequest.SCALER_CROP_REGION);

                            // crop region becomes null when zoomRatio==1.0f so we have to check
                            // separately.
                            boolean isRatioOneNullMatched =
                                    cropRect == null && mPendingZoomCropRegion == null;
                            boolean isRatioOthersRegionMatched =
                                    mPendingZoomCropRegion != null
                                            && mPendingZoomCropRegion.equals(cropRect);

                            if (isRatioOneNullMatched || isRatioOthersRegionMatched) {
                                completerToSet = mPendingZoomRatioCompleter;
                                mPendingZoomRatioCompleter = null;
                                mPendingZoomCropRegion = null;
                            }
                        }
                    }

                    if (completerToSet != null) {
                        completerToSet.set(null);
                    }
                    return false; // continue checking
                }
            };

    /**
     * Sets current zoom by ratio.
     *
     * <p>It modifies both current zoom ratio and zoom percentage so if apps are observing
     * zoomRatio or zoomPercentage, they will get the update as well. If the ratio is
     * smaller than {@link CameraInfo#getMinZoomRatio()} or larger than
     * {@link CameraInfo#getMaxZoomRatio()}, it won't modify current zoom ratio. It is
     * applications' duty to clamp the ratio.
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     *     result contains the requested zoom ratio. It fails with
     *     {@link OperationCanceledException} if there is newer value being set or camera is closed.
     *     If ratio is out of range, it fails with
     *     {@link CameraControl.ArgumentOutOfRangeException}.
     */
    @NonNull
    ListenableFuture<Void> setZoomRatio(float ratio) {
        // Wrapping the whole method in synchronized block in case mActive is changed to false in
        // the middle of the method. To avoid the deadlock problem, we only perform variable
        // assignment in the setActive() synchronized block.
        synchronized (mActiveLock) {
            if (!mIsActive) {
                return Futures.immediateFailedFuture(
                        new OperationCanceledException("Camera is not active."));
            }

            // If the requested ratio is out of range, it will not modify zoom value but report
            // ArgumentOutOfRangeException in returned ListenableFuture.
            if (ratio > getMaxZoomRatio().getValue() || ratio < getMinZoomRatio().getValue()) {
                String outOfRangeDesc = "Requested zoomRatio " + ratio + " is not within valid "
                        + "range [" + getMinZoomRatio().getValue() + " , "
                        + getMaxZoomRatio().getValue() + "]";

                return Futures.immediateFailedFuture(
                        new CameraControl.ArgumentOutOfRangeException(outOfRangeDesc));
            }

            return setZoomRatioInternal(ratio, true);
        }
    }

    @VisibleForTesting
    static Rect getCropRectByRatio(Rect sensorRect, float ratio) {
        float cropWidth = (sensorRect.width() / ratio);
        float cropHeight = (sensorRect.height() / ratio);
        float left = ((sensorRect.width() - cropWidth) / 2.0f);
        float top = ((sensorRect.height() - cropHeight) / 2.0f);
        return new Rect((int) left, (int) top, (int) (left + cropWidth),
                (int) (top + cropHeight));
    }

    @NonNull
    @GuardedBy("mActiveLock")
    private ListenableFuture<Void> setZoomRatioInternal(float ratio, boolean updatePercentage) {
        Rect sensorRect = mCamera2CameraControl.getSensorRect();
        if (sensorRect == null) {
            throw new IllegalStateException("Cannot get sensor active array");
        }

        setLiveDataValue(mZoomRatioLiveData, ratio);
        if (updatePercentage) {
            setLiveDataValue(mZoomPercentageLiveData, getPercentageByRatio(ratio));
        }

        Rect targetRegion;
        // if Ratio is 1.0f, we simply remove the crop region parameter.
        if (ratio == 1.0f) {
            targetRegion = null;
        } else {
            targetRegion = getCropRectByRatio(sensorRect, ratio);
        }
        mCamera2CameraControl.setCropRegion(targetRegion);

        return CallbackToFutureAdapter.getFuture(new CallbackToFutureAdapter.Resolver<Void>() {
            @Nullable
            @Override
            public Object attachCompleter(
                    @NonNull CallbackToFutureAdapter.Completer<Void> completer) throws Exception {
                CallbackToFutureAdapter.Completer<Void> completerToCancel = null;
                synchronized (mCompleterLock) {
                    if (mPendingZoomRatioCompleter != null) {
                        completerToCancel = mPendingZoomRatioCompleter;
                        mPendingZoomRatioCompleter = null;
                    }
                    mPendingZoomCropRegion = targetRegion;
                    mPendingZoomRatioCompleter = completer;
                }

                if (completerToCancel != null) {
                    completerToCancel.setException(
                            new OperationCanceledException("There is a new zoomRatio being set"));
                }

                return "setZoomRatio";
            }
        });
    }

    /**
     * Sets current zoom by percentage ranging from 0f to 1.0f. Percentage 0f represents the
     * minimum zoom while percentage 1.0f represents the maximum zoom. One advantage of zoom
     * percentage is that it ensures FOV varies linearly with the percentage value.
     *
     * <p>It modifies both current zoom ratio and zoom percentage so if apps are observing
     * zoomRatio or zoomPercentage, they will get the update as well. If the percentage is not in
     * the range [0..1], it won't modify current zoom percentage and zoom ratio. It is
     * applications' duty to clamp the zoomPercentage within [0..1].
     *
     * @return a {@link ListenableFuture} which is finished when current repeating request
     *     result contains the requested zoom percentage. It fails with
     *     {@link OperationCanceledException} if there is newer value being set or camera is closed.
     *     If percentage is out of range, it fails with
     *     {@link CameraControl.ArgumentOutOfRangeException}.
     */
    @NonNull
    ListenableFuture<Void> setZoomPercentage(@FloatRange(from = 0f, to = 1f) float percentage) {
        // Wrapping the whole method in synchronized block in case mActive is changed to false in
        // the middle of the method. To avoid the deadlock problem, we only perform variable
        // assignment in the setActive() synchronized block.
        synchronized (mActiveLock) {
            if (!mIsActive) {
                return Futures.immediateFailedFuture(
                        new OperationCanceledException("Camera is not active."));
            }

            // If the requested percentage is out of range, it will not modify zoom value but
            // report ArgumentOutOfRangeException in returned ListenableFuture.
            if (percentage > 1.0f || percentage < 0f) {
                String outOfRangeDesc = "Requested zoomPercentage " + percentage + " is not within"
                        + " valid range [0..1]";
                return Futures.immediateFailedFuture(
                        new CameraControl.ArgumentOutOfRangeException(outOfRangeDesc));
            }

            float ratio = getRatioByPercentage(percentage);
            setLiveDataValue(mZoomPercentageLiveData, percentage);
            return setZoomRatioInternal(ratio, false);
        }
    }

    private <T> void setLiveDataValue(@NonNull MutableLiveData<T> liveData, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    /**
     * Returns a {@link LiveData} of current zoom ratio.
     *
     * <p>Apps can either get immediate value via {@link LiveData#getValue()} (The value is never
     * null, it has default value in the beginning) or they can observe it via
     * {@link LiveData#observe(LifecycleOwner, Observer)} to update zoom UI accordingly.
     *
     * <p>Setting zoom ratio or zoom percentage will both trigger the change event.
     *
     * @return a {@link LiveData} containing current zoom ratio.
     */
    @NonNull
    LiveData<Float> getZoomRatio() {
        return mZoomRatioLiveData;
    }

    /**
     * Returns a {@link LiveData} of the maximum zoom ratio.
     *
     * <p>Apps can either get immediate value via {@link LiveData#getValue()} (The value is never
     * null, it has default value in the beginning) or they can observe it via
     * {@link LiveData#observe(LifecycleOwner, Observer)} to update zoom UI accordingly.
     *
     * <p>While the value is fixed most of the time, enabling extension could change the maximum
     * zoom ratio.
     *
     * @return a {@link LiveData} containing the maximum zoom ratio value.
     */
    @NonNull
    LiveData<Float> getMaxZoomRatio() {
        return mMaxZoomRatioLiveData;
    }

    /**
     * Returns a {@link LiveData} of the minimum zoom ratio.
     *
     * <p>Apps can either get immediate value via {@link LiveData#getValue()} (The value is never
     * null, it has default value in the beginning) or they can observe it via
     * {@link LiveData#observe(LifecycleOwner, Observer)} to update zoom UI accordingly.
     *
     * <p>While the value is fixed most of the time, enabling extension could change the minimum
     * zoom ratio value.
     *
     * @return a {@link LiveData} containing the minimum zoom ratio value.
     */
    @NonNull
    LiveData<Float> getMinZoomRatio() {
        return mMinZoomRatioLiveData;
    }

    /**
     * Returns a {@link LiveData} of current zoom percentage which is in range [0..1].
     * Percentage 0 represents the maximum zoom while percentage 1.0 represents the maximum zoom.
     *
     * <p>Apps can either get immediate value via {@link LiveData#getValue()} (The value is never
     * null, it has default value in the beginning) or they can observe it via
     * {@link LiveData#observe(LifecycleOwner, Observer)} to update zoom UI accordingly.
     * <p>Setting zoom ratio or zoom percentage will both trigger the change event.
     *
     * @return a {@link LiveData} containing current zoom percentage.
     */
    @NonNull
    LiveData<Float> getZoomPercentage() {
        return mZoomPercentageLiveData;
    }

    private float getMaxDigitalZoom() {
        Float maxZoom = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

        if (maxZoom == null) {
            return MIN_ZOOM;
        }

        return maxZoom;
    }

    private float getRatioByPercentage(float percentage) {
        // Make sure 1.0f and 0.0 return exactly the same max/min ratio.
        if (percentage == 1.0f) {
            return getMaxDigitalZoom();
        } else if (percentage == 0f) {
            return MIN_ZOOM;
        }
        // This crop width is proportional to the real crop width.
        // The real crop with = sensorWidth/ zoomRatio,  but we need the ratio only so we can
        // assume sensorWidth as 1.0f.
        double cropWidthInMaxZoom = 1.0f / getMaxZoomRatio().getValue();
        double cropWidthInMinZoom = 1.0f / getMinZoomRatio().getValue();

        double cropWidth = cropWidthInMinZoom + (cropWidthInMaxZoom - cropWidthInMinZoom)
                * percentage;

        double ratio = 1.0 / cropWidth;

        return (float) MathUtils.clamp(ratio, getMinZoomRatio().getValue(),
                getMaxZoomRatio().getValue());
    }

    private float getPercentageByRatio(float ratio) {
        // if zoom is not supported, return 0
        if (getMaxDigitalZoom() == MIN_ZOOM) {
            return 0f;
        }

        // To make the min/max same value when doing conversion between ratio / percentage.
        // We return the max/min value directly.
        if (ratio == getMaxDigitalZoom()) {
            return 1f;
        } else if (ratio == MIN_ZOOM) {
            return 0f;
        }

        float cropWidth = 1.0f / ratio;
        float cropWidthInMaxZoom = 1.0f / getMaxZoomRatio().getValue();
        float cropWidthInMinZoom = 1.0f / getMinZoomRatio().getValue();

        return (cropWidth - cropWidthInMinZoom)
                / (cropWidthInMaxZoom - cropWidthInMinZoom);
    }
}
