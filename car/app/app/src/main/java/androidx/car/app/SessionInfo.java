/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.car.app;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.ExperimentalCarApi;
import androidx.car.app.annotations.RequiresCarApi;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.versioning.CarAppApiLevel;
import androidx.car.app.versioning.CarAppApiLevels;

import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Retention;
import java.util.Set;

/** Information about a {@link Session}, such as the physical display and the session ID. */
@RequiresCarApi(5)
@CarProtocol
public class SessionInfo {
    /** The primary infotainment display usually in the center column of the vehicle. */
    public static final int DISPLAY_TYPE_MAIN = 0;

    /** The cluster display, usually located behind the steering wheel. */
    public static final int DISPLAY_TYPE_CLUSTER = 1;

    private static final ImmutableSet<Class<? extends Template>> CLUSTER_SUPPORTED_TEMPLATES_API_5 =
            ImmutableSet.of(NavigationTemplate.class);
    private static final ImmutableSet<Class<? extends Template>>
            CLUSTER_SUPPORTED_TEMPLATES_LESS_THAN_API_5 = ImmutableSet.of();

    /**
     * @hide
     */
    @IntDef({DISPLAY_TYPE_MAIN, DISPLAY_TYPE_CLUSTER})
    @Retention(SOURCE)
    public @interface DisplayType {
    }

    /**
     * A default {@link SessionInfo} for the main display, used when the host is on a version
     * that doesn't support this new class.
     */
    @NonNull
    public static final SessionInfo DEFAULT_SESSION_INFO = new SessionInfo(
            DISPLAY_TYPE_MAIN, "main");

    /** A string identifier unique per physical display. */
    @Keep
    @NonNull
    private final String mSessionId;

    /** The type of display the {@link Session} is rendering on. */
    @Keep
    @DisplayType
    private final int mDisplayType;

    /**
     * Returns a session-stable ID, unique to the display that the {@link Session} is rendering on.
     */
    @NonNull
    public String getSessionId() {
        return mSessionId;
    }

    /** Returns the type of display that the {@link Session} is rendering on. */
    @DisplayType
    public int getDisplayType() {
        return mDisplayType;
    }

    /**
     * Creates a new {@link SessionInfo} with the provided {@code displayType} and {@code
     * sessionId}.
     */
    public SessionInfo(@DisplayType int displayType, @NonNull String sessionId) {
        mDisplayType = displayType;
        mSessionId = sessionId;
    }

    // Required for Bundler
    private SessionInfo() {
        mSessionId = "main";
        mDisplayType = DISPLAY_TYPE_MAIN;
    }

    /**
     * Returns the set of templates that are allowed for this {@link Session}, or {@code null} if
     * there are no restrictions (ie. all templates are allowed).
     */
    @Nullable
    @SuppressWarnings("NullableCollection") // Set does not contain nulls
    @ExperimentalCarApi
    public Set<Class<? extends Template>> getSupportedTemplates(
            @CarAppApiLevel int carAppApiLevel) {
        if (mDisplayType == DISPLAY_TYPE_CLUSTER) {
            if (carAppApiLevel >= CarAppApiLevels.LEVEL_5) {
                return CLUSTER_SUPPORTED_TEMPLATES_API_5;
            }

            return CLUSTER_SUPPORTED_TEMPLATES_LESS_THAN_API_5;
        }

        return null;
    }
}
