/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.car.app.sample.showcase.common.renderer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.car.app.CarContext;
import androidx.car.app.annotations.ExperimentalCarApi;
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.car.app.hardware.info.Accelerometer;
import androidx.car.app.hardware.info.CarHardwareLocation;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.CarSensors;
import androidx.car.app.hardware.info.Compass;
import androidx.car.app.hardware.info.EnergyLevel;
import androidx.car.app.hardware.info.EvStatus;
import androidx.car.app.hardware.info.Gyroscope;
import androidx.car.app.hardware.info.Mileage;
import androidx.car.app.hardware.info.Speed;
import androidx.car.app.hardware.info.TollCard;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Renderer which aggregates information about the car hardware to be drawn on a surface.
 */
public final class CarHardwareRenderer implements Renderer {
    private static final String TAG = "showcase";

    private static final int ROW_SPACING = 10;
    private static final int LEFT_MARGIN = 15;
    private static final int MAX_FONT_SIZE = 32;

    private final Executor mCarHardwareExecutor;
    private final Paint mCarInfoPaint = new Paint();
    private final CarContext mCarContext;

    @Nullable
    TollCard mTollCard;
    @Nullable
    EnergyLevel mEnergyLevel;
    @Nullable
    Speed mSpeed;
    @Nullable
    Mileage mMileage;
    @Nullable
    EvStatus mEvStatus;
    @Nullable
    Accelerometer mAccelerometer;
    @Nullable
    Gyroscope mGyroscope;
    @Nullable
    Compass mCompass;
    @Nullable
    CarHardwareLocation mCarHardwareLocation;
    @Nullable
    private Runnable mRequestRenderRunnable;
    private boolean mHasTollCardPermission;
    private boolean mHasEnergyLevelPermission;
    private boolean mHasSpeedPermission;
    private boolean mHasMileagePermission;
    private boolean mHasEvStatusPermission;
    private boolean mHasAccelerometerPermission;
    private boolean mHasGyroscopePermission;
    private boolean mHasCompassPermission;
    private boolean mHasCarHardwareLocationPermission;

    private final OnCarDataAvailableListener<TollCard> mTollListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received toll information:" + data);
            mTollCard = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<EnergyLevel> mEnergyLevelListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received energy level information: " + data);
            mEnergyLevel = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<Speed> mSpeedListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received speed information: " + data);
            mSpeed = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<Mileage> mMileageListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received mileage: " + data);
            mMileage = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<Accelerometer> mAccelerometerListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received accelerometer: " + data);
            mAccelerometer = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<Gyroscope> mGyroscopeListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received gyroscope: " + data);
            mGyroscope = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<Compass> mCompassListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received compass: " + data);
            mCompass = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<CarHardwareLocation> mCarLocationListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received car location: " + data);
            mCarHardwareLocation = data;
            requestRenderFrame();
        }
    };
    private final OnCarDataAvailableListener<EvStatus> mEvStatusListener = data -> {
        synchronized (this) {
            Log.i(TAG, "Received car EV status: " + data);
            mEvStatus = data;
            requestRenderFrame();
        }
    };

    public CarHardwareRenderer(@NonNull CarContext carContext) {
        mCarContext = carContext;
        mCarInfoPaint.setColor(Color.BLACK);
        mCarInfoPaint.setAntiAlias(true);
        mCarInfoPaint.setStyle(Paint.Style.STROKE);
        mCarHardwareExecutor = ContextCompat.getMainExecutor(mCarContext);
    }

    // TODO(b/216177515): Remove this annotation once EvStatus is ready.
    @OptIn(markerClass = ExperimentalCarApi.class)
    @Override
    public void enable(@NonNull Runnable onChangeListener) {
        mRequestRenderRunnable = onChangeListener;
        CarHardwareManager carHardwareManager =
                mCarContext.getCarService(CarHardwareManager.class);
        CarInfo carInfo = carHardwareManager.getCarInfo();
        CarSensors carSensors = carHardwareManager.getCarSensors();

        // Request car info subscription items.
        mTollCard = null;
        try {
            carInfo.addTollListener(mCarHardwareExecutor, mTollListener);
            mHasTollCardPermission = true;
        } catch (SecurityException e) {
            mHasTollCardPermission = false;
        }

        mEnergyLevel = null;
        try {
            carInfo.addEnergyLevelListener(mCarHardwareExecutor, mEnergyLevelListener);
            mHasEnergyLevelPermission = true;
        } catch (SecurityException e) {
            mHasEnergyLevelPermission = false;
        }

        mSpeed = null;
        try {
            carInfo.addSpeedListener(mCarHardwareExecutor, mSpeedListener);
            mHasSpeedPermission = true;
        } catch (SecurityException e) {
            mHasSpeedPermission = false;
        }

        mMileage = null;
        try {
            carInfo.addMileageListener(mCarHardwareExecutor, mMileageListener);
            mHasMileagePermission = true;
        } catch (SecurityException e) {
            mHasMileagePermission = false;
        }

        mEvStatus = null;
        try {
            carInfo.addEvStatusListener(mCarHardwareExecutor, mEvStatusListener);
            mHasEvStatusPermission = true;
        } catch (SecurityException e) {
            mHasEvStatusPermission = false;
        }

        // Request sensors
        mCompass = null;
        try {
            carSensors.addCompassListener(CarSensors.UPDATE_RATE_NORMAL, mCarHardwareExecutor,
                    mCompassListener);
            mHasCompassPermission = true;
        } catch (SecurityException e) {
            mHasCompassPermission = false;
        }

        mGyroscope = null;
        try {
            carSensors.addGyroscopeListener(CarSensors.UPDATE_RATE_NORMAL, mCarHardwareExecutor,
                    mGyroscopeListener);
            mHasGyroscopePermission = true;
        } catch (SecurityException e) {
            mHasGyroscopePermission = false;
        }

        mAccelerometer = null;
        try {
            carSensors.addAccelerometerListener(CarSensors.UPDATE_RATE_NORMAL,
                    mCarHardwareExecutor,
                    mAccelerometerListener);
            mHasAccelerometerPermission = true;
        } catch (SecurityException e) {
            mHasAccelerometerPermission = false;
        }

        mCarHardwareLocation = null;
        try {
            carSensors.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_NORMAL,
                    mCarHardwareExecutor, mCarLocationListener);
            mHasCarHardwareLocationPermission = true;
        } catch (SecurityException e) {
            mHasCarHardwareLocationPermission = false;
        }
    }

    // TODO(b/216177515): Remove this annotation once EvStatus is ready.
    @OptIn(markerClass = ExperimentalCarApi.class)
    @Override
    public void disable() {
        mRequestRenderRunnable = null;
        CarHardwareManager carHardwareManager =
                mCarContext.getCarService(CarHardwareManager.class);
        CarInfo carInfo = carHardwareManager.getCarInfo();
        CarSensors carSensors = carHardwareManager.getCarSensors();

        try {
            // Unsubscribe carinfo
            carInfo.removeTollListener(mTollListener);
            mHasTollCardPermission = true;
        } catch (SecurityException e) {
            mHasTollCardPermission = false;
        }
        mTollCard = null;

        try {
            carInfo.removeEnergyLevelListener(mEnergyLevelListener);
            mHasEnergyLevelPermission = true;
        } catch (SecurityException e) {
            mHasEnergyLevelPermission = false;
        }
        mEnergyLevel = null;

        try {
            carInfo.removeSpeedListener(mSpeedListener);
            mHasSpeedPermission = true;
        } catch (SecurityException e) {
            mHasSpeedPermission = false;
        }
        mSpeed = null;

        try {
            carInfo.removeMileageListener(mMileageListener);
            mHasMileagePermission = true;
        } catch (SecurityException e) {
            mHasMileagePermission = false;
        }
        mMileage = null;

        try {
            // Unsubscribe sensors
            carSensors.removeCompassListener(mCompassListener);
            mHasCompassPermission = true;
        } catch (SecurityException e) {
            mHasCompassPermission = false;
        }
        mCompass = null;

        try {
            carInfo.removeEvStatusListener(mEvStatusListener);
            mHasEvStatusPermission = true;
        } catch (SecurityException e) {
            mHasEvStatusPermission = false;
        }
        mEvStatus = null;


        try {
            carSensors.removeGyroscopeListener(mGyroscopeListener);
            mHasGyroscopePermission = true;
        } catch (SecurityException e) {
            mHasGyroscopePermission = false;
        }
        mGyroscope = null;

        try {
            carSensors.removeAccelerometerListener(mAccelerometerListener);
            mHasAccelerometerPermission = true;
        } catch (SecurityException e) {
            mHasAccelerometerPermission = false;
        }
        mAccelerometer = null;

        try {
            carSensors.removeCarHardwareLocationListener(mCarLocationListener);
            mHasCarHardwareLocationPermission = true;
        } catch (SecurityException e) {
            mHasCarHardwareLocationPermission = false;
        }
        mCarHardwareLocation = null;
    }

    // TODO(b/216177515): Remove this annotation once EvStatus is ready.
    @OptIn(markerClass = ExperimentalCarApi.class)
    @Override
    public void renderFrame(@NonNull Canvas canvas, @Nullable Rect visibleArea,
            @Nullable Rect stableArea) {
        if (stableArea != null) {
            if (stableArea.isEmpty()) {
                // No inset set. The entire area is considered safe to draw.
                stableArea.set(0, 0, canvas.getWidth() - 1, canvas.getHeight() - 1);
            }

            int height = Math.min(stableArea.height() / 8, MAX_FONT_SIZE);
            int updatedSize = height - ROW_SPACING;
            mCarInfoPaint.setTextSize(updatedSize);

            canvas.drawRect(stableArea, mCarInfoPaint);

            Paint.FontMetrics fm = mCarInfoPaint.getFontMetrics();
            float verticalPos = stableArea.top - fm.ascent;

            // Prepare text for Toll card status
            StringBuilder info = new StringBuilder();
            if (!mHasTollCardPermission) {
                info.append("No TollCard Permission.");
            } else if (mTollCard == null) {
                info.append("Fetching Toll information.");
            } else {
                info.append(
                        generateCarValueText("Toll card state", mTollCard.getCardState(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Energy Level
            info = new StringBuilder();
            if (!mHasEnergyLevelPermission) {
                info.append("No EnergyLevel Permission.");
            } else if (mEnergyLevel == null) {
                info.append("Fetching Energy Level.");
            } else {
                info.append(
                        generateCarValueText("Low energy", mEnergyLevel.getEnergyIsLow(), ". "));
                info.append(
                        generateCarValueText("Range", mEnergyLevel.getRangeRemainingMeters(),
                                " m. "));
                info.append(generateCarValueText("Fuel", mEnergyLevel.getFuelPercent(), " %. "));
                info.append(
                        generateCarValueText("Battery", mEnergyLevel.getBatteryPercent(), " %. "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Speed
            info = new StringBuilder();
            if (!mHasSpeedPermission) {
                info.append("No Speed Permission.");
            } else if (mSpeed == null) {
                info.append("Fetching Speed.");
            } else {
                info.append(generateCarValueText("Display Speed",
                        mSpeed.getDisplaySpeedMetersPerSecond(), " m/s. "));
                info.append(generateCarValueText("Raw Speed", mSpeed.getRawSpeedMetersPerSecond(),
                        " m/s. "));
                info.append(generateCarValueText("Unit", mSpeed.getSpeedDisplayUnit(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Odometer
            info = new StringBuilder();
            if (!mHasMileagePermission) {
                info.append("No Mileage Permission.");
            } else if (mMileage == null) {
                info.append("Fetching mileage.");
            } else {
                info.append(generateCarValueText("Odometer", mMileage.getOdometerMeters(), " m. "));
                info.append(generateCarValueText("Unit", mMileage.getDistanceDisplayUnit(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            if (mCarContext.getCarAppApiLevel() >= CarAppApiLevels.LEVEL_4) {
                // Prepare text for EV status
                info = new StringBuilder();
                if (!mHasEvStatusPermission) {
                    info.append("No EV status Permission.");
                } else if (mEvStatus == null) {
                    info.append("Fetching EV status.");
                } else {
                    info.append(generateCarValueText("Ev Charge Port Connected",
                            mEvStatus.getEvChargePortConnected(), ". "));
                    info.append(generateCarValueText("Ev Charge Port Open",
                            mEvStatus.getEvChargePortOpen(), ". "));
                }
                canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
                verticalPos += height;
            }

            // Prepare text for Accelerometer
            info = new StringBuilder();
            if (!mHasAccelerometerPermission) {
                info.append("No Accelerometer Permission.");
            } else if (mAccelerometer == null) {
                info.append("Fetching accelerometer");
            } else {
                info.append(
                        generateCarValueText("Accelerometer", mAccelerometer.getForces(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Gyroscope
            info = new StringBuilder();
            if (!mHasGyroscopePermission) {
                info.append("No Gyroscope Permission.");
            } else if (mGyroscope == null) {
                info.append("Fetching gyroscope");
            } else {
                info.append(generateCarValueText("Gyroscope", mGyroscope.getRotations(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Compass
            info = new StringBuilder();
            if (!mHasCompassPermission) {
                info.append("No Compass Permission.");
            } else if (mCompass == null) {
                info.append("Fetching compass");
            } else {
                info.append(generateCarValueText("Compass", mCompass.getOrientations(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
            verticalPos += height;

            // Prepare text for Location
            info = new StringBuilder();
            if (!mHasCarHardwareLocationPermission) {
                info.append("No CarHardwareLocation Permission.");
            } else if (mCarHardwareLocation == null) {
                info.append("Fetching location");
            } else {
                info.append(generateCarValueText("Car Hardware Location",
                        mCarHardwareLocation.getLocation(), ". "));
            }
            canvas.drawText(info.toString(), LEFT_MARGIN, verticalPos, mCarInfoPaint);
        }
    }

    private String generateCarValueText(String item, CarValue<?> carValue, String ending) {
        StringBuilder stringBuilder = new StringBuilder(item);
        if (carValue.getStatus() != CarValue.STATUS_SUCCESS) {
            stringBuilder.append(" N/A. ");
        } else {
            stringBuilder.append(": ");
            if (carValue.getValue() instanceof List) {
                List<?> list = (List<?>) carValue.getValue();
                appendList(stringBuilder, list);
            } else {
                stringBuilder.append(carValue.getValue());
            }
            stringBuilder.append(ending);
        }
        return stringBuilder.toString();
    }

    private void requestRenderFrame() {
        if (mRequestRenderRunnable != null) {
            mRequestRenderRunnable.run();
        }
    }

    private void appendList(StringBuilder builder, List<?> values) {
        builder.append("[ ");
        for (Object value : values) {
            builder.append(value);
            builder.append(" ");
        }
        builder.append("]");
    }
}
