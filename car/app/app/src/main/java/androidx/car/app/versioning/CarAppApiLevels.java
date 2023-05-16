/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.car.app.versioning;

import static java.util.Objects.requireNonNull;

import androidx.annotation.RestrictTo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * API levels supported by this library.
 *
 * <p>Each level denotes a set of elements (classes, fields and methods) known to both clients and
 * hosts.
 *
 * @see androidx.car.app.CarContext#getCarAppApiLevel()
 * @see
 * <a href="https://developer.android.com/jetpack/androidx/releases/car-app">Car App Library Release Notes</a>
 */
public final class CarAppApiLevels {
    /**
     * API level 7.
     *
     * <p>Includes a Badge feature for GridItem image.</p>
     */
    @CarAppApiLevel
    public static final int LEVEL_7 = 7;

    /**
     * API level 6.
     */
    @CarAppApiLevel
    public static final int LEVEL_6 = 6;

    /**
     * API level 5.
     *
     * <p>Includes features such as voice access, alerters, map-pane template details view,
     * responsive-turn-cards, tap on map, list within the navigation template for non-places
     * content, map interactivity (zoom and pan) on POIs and route preview, content refresh on
     * POIs (with speedbump).
     */
    @CarAppApiLevel
    public static final int LEVEL_5 = 5;

    /**
     * API level 4.
     *
     * <p>Includes AAOS support and other features such as QR code sign-in, show current location
     * in place-list-map template, contrast check capability.
     */
    @CarAppApiLevel
    public static final int LEVEL_4 = 4;

    /**
     * API level 3.
     *
     * <p>Includes a car hardware manager for access to sensors and other vehicle properties.
     */
    @CarAppApiLevel
    public static final int LEVEL_3 = 3;

    /**
     * API level 2.
     *
     * <p>Includes features such as sign-in template, long-message template, and multi-variant
     * text support.
     */
    @CarAppApiLevel
    public static final int LEVEL_2 = 2;

    /**
     * Initial API level.
     *
     * <p>Includes core API services and managers, and templates for parking, charging, and
     * navigation apps.
     */
    @CarAppApiLevel
    public static final int LEVEL_1 = 1;

    /**
     * Unknown API level.
     *
     * <p>Used when the API level hasn't been established yet
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @CarAppApiLevel
    public static final int UNKNOWN = 0;

    private static final String CAR_API_LEVEL_FILE = "car-app-api.level";

    /**
     * Returns whether the given integer is a valid {@link CarAppApiLevel}
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static boolean isValid(int carApiLevel) {
        return carApiLevel >= getOldest() && carApiLevel <= getLatest();
    }

    /**
     * Returns the highest API level implemented by this library.
     */
    @CarAppApiLevel
    public static int getLatest() {
        // The latest Car API level is defined as java resource, generated via build.gradle. This
        // has to be read through the class loader because we do not have access to the context
        // to retrieve an Android resource.
        ClassLoader classLoader = requireNonNull(CarAppApiLevels.class.getClassLoader());
        InputStream inputStream = classLoader.getResourceAsStream(CAR_API_LEVEL_FILE);

        if (inputStream == null) {
            throw new IllegalStateException(String.format("Car API level file %s not found",
                    CAR_API_LEVEL_FILE));
        }

        try {
            InputStreamReader streamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(streamReader);
            String line = reader.readLine();


            int apiLevel = Integer.parseInt(line);
            if (apiLevel < LEVEL_1 || apiLevel > LEVEL_7) {
                throw new IllegalStateException("Unrecognized Car API level: " + line);
            }
            return apiLevel;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Car API level file");
        }
    }

    /**
     * Returns the lowest API level implemented by this library.
     */
    @CarAppApiLevel
    public static int getOldest() {
        return LEVEL_1;
    }

    private CarAppApiLevels() {
    }
}
