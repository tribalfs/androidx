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

package androidx.camera.testing.fakes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraDeviceConfig;
import androidx.camera.core.CameraIdFilter;
import androidx.camera.core.CaptureConfig;
import androidx.camera.core.Config;
import androidx.camera.core.LensFacing;
import androidx.camera.core.MutableConfig;
import androidx.camera.core.MutableOptionsBundle;
import androidx.camera.core.OptionsBundle;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseConfig;

import java.util.Set;
import java.util.UUID;

/** A fake configuration for {@link FakeUseCase}. */
public class FakeUseCaseConfig
        implements UseCaseConfig<FakeUseCase>, CameraDeviceConfig {

    private final Config mConfig;

    FakeUseCaseConfig(Config config) {
        mConfig = config;
    }

    // Start of the default implementation of Config
    // *********************************************************************************************

    // Implementations of Config.Reader default methods

    @Override
    public boolean containsOption(@NonNull Option<?> id) {
        return mConfig.containsOption(id);
    }

    @Override
    @Nullable
    public <ValueT> ValueT retrieveOption(@NonNull Option<ValueT> id) {
        return mConfig.retrieveOption(id);
    }

    @Override
    @Nullable
    public <ValueT> ValueT retrieveOption(@NonNull Option<ValueT> id,
            @Nullable ValueT valueIfMissing) {
        return mConfig.retrieveOption(id, valueIfMissing);
    }

    @Override
    public void findOptions(@NonNull String idStem, @NonNull OptionMatcher matcher) {
        mConfig.findOptions(idStem, matcher);
    }

    @Override
    @NonNull
    public Set<Option<?>> listOptions() {
        return mConfig.listOptions();
    }

    // Implementations of TargetConfig default methods

    @Override
    @Nullable
    public Class<FakeUseCase> getTargetClass(
            @Nullable Class<FakeUseCase> valueIfMissing) {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<FakeUseCase> storedClass = (Class<FakeUseCase>) retrieveOption(
                OPTION_TARGET_CLASS,
                valueIfMissing);
        return storedClass;
    }

    @Override
    @NonNull
    public Class<FakeUseCase> getTargetClass() {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<FakeUseCase> storedClass = (Class<FakeUseCase>) retrieveOption(
                OPTION_TARGET_CLASS);
        return storedClass;
    }

    @Override
    @Nullable
    public String getTargetName(@Nullable String valueIfMissing) {
        return retrieveOption(OPTION_TARGET_NAME, valueIfMissing);
    }

    @Override
    @NonNull
    public String getTargetName() {
        return retrieveOption(OPTION_TARGET_NAME);
    }

    // Implementations of CameraDeviceConfig default methods

    @Override
    @Nullable
    public LensFacing getLensFacing(@Nullable LensFacing valueIfMissing) {
        return retrieveOption(OPTION_LENS_FACING, valueIfMissing);
    }

    @Override
    @NonNull
    public LensFacing getLensFacing() {
        return retrieveOption(OPTION_LENS_FACING);
    }

    @Override
    @Nullable
    public CameraIdFilter getCameraIdFilter(@Nullable CameraIdFilter valueIfMissing) {
        return retrieveOption(OPTION_CAMERA_ID_FILTER, valueIfMissing);
    }

    @Override
    @NonNull
    public CameraIdFilter getCameraIdFilter() {
        return retrieveOption(OPTION_CAMERA_ID_FILTER);
    }

    // Implementations of UseCaseConfig default methods

    @Override
    @Nullable
    public SessionConfig getDefaultSessionConfig(@Nullable SessionConfig valueIfMissing) {
        return retrieveOption(OPTION_DEFAULT_SESSION_CONFIG, valueIfMissing);
    }

    @Override
    @NonNull
    public SessionConfig getDefaultSessionConfig() {
        return retrieveOption(OPTION_DEFAULT_SESSION_CONFIG);
    }

    @Override
    @Nullable
    public CaptureConfig getDefaultCaptureConfig(@Nullable CaptureConfig valueIfMissing) {
        return retrieveOption(OPTION_DEFAULT_CAPTURE_CONFIG, valueIfMissing);
    }

    @Override
    @NonNull
    public CaptureConfig getDefaultCaptureConfig() {
        return retrieveOption(OPTION_DEFAULT_CAPTURE_CONFIG);
    }

    @Override
    @Nullable
    public SessionConfig.OptionUnpacker getSessionOptionUnpacker(
            @Nullable SessionConfig.OptionUnpacker valueIfMissing) {
        return retrieveOption(OPTION_SESSION_CONFIG_UNPACKER, valueIfMissing);
    }

    @Override
    @NonNull
    public SessionConfig.OptionUnpacker getSessionOptionUnpacker() {
        return retrieveOption(OPTION_SESSION_CONFIG_UNPACKER);
    }

    @Override
    @Nullable
    public CaptureConfig.OptionUnpacker getCaptureOptionUnpacker(
            @Nullable CaptureConfig.OptionUnpacker valueIfMissing) {
        return retrieveOption(OPTION_CAPTURE_CONFIG_UNPACKER, valueIfMissing);
    }

    @Override
    @NonNull
    public CaptureConfig.OptionUnpacker getCaptureOptionUnpacker() {
        return retrieveOption(OPTION_CAPTURE_CONFIG_UNPACKER);
    }

    @Override
    public int getSurfaceOccupancyPriority(int valueIfMissing) {
        return retrieveOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, valueIfMissing);
    }

    @Override
    public int getSurfaceOccupancyPriority() {
        return retrieveOption(OPTION_SURFACE_OCCUPANCY_PRIORITY);
    }

    @Override
    @Nullable
    public UseCase.EventCallback getUseCaseEventCallback(
            @Nullable UseCase.EventCallback valueIfMissing) {
        return retrieveOption(OPTION_USE_CASE_EVENT_CALLBACK, valueIfMissing);
    }

    @Override
    @NonNull
    public UseCase.EventCallback getUseCaseEventCallback() {
        return retrieveOption(OPTION_USE_CASE_EVENT_CALLBACK);
    }

    // End of the default implementation of Config
    // *********************************************************************************************

    /** Builder for an empty Config */
    public static final class Builder
            implements
            UseCaseConfig.Builder<FakeUseCase, FakeUseCaseConfig, FakeUseCaseConfig.Builder>,
            CameraDeviceConfig.Builder<FakeUseCaseConfig.Builder> {

        private final MutableOptionsBundle mOptionsBundle;

        public Builder() {
            mOptionsBundle = MutableOptionsBundle.create();
            setTargetClass(FakeUseCase.class);
            setLensFacing(LensFacing.BACK);
        }

        @Override
        @NonNull
        public MutableConfig getMutableConfig() {
            return mOptionsBundle;
        }

        @Override
        @NonNull
        public FakeUseCaseConfig build() {
            return new FakeUseCaseConfig(OptionsBundle.from(mOptionsBundle));
        }

        // Implementations of TargetConfig.Builder default methods

        @Override
        @NonNull
        public Builder setTargetClass(@NonNull Class<FakeUseCase> targetClass) {
            getMutableConfig().insertOption(OPTION_TARGET_CLASS, targetClass);

            // If no name is set yet, then generate a unique name
            if (null == getMutableConfig().retrieveOption(OPTION_TARGET_NAME, null)) {
                String targetName = targetClass.getCanonicalName() + "-" + UUID.randomUUID();
                setTargetName(targetName);
            }

            return this;
        }

        @Override
        @NonNull
        public Builder setTargetName(@NonNull String targetName) {
            getMutableConfig().insertOption(OPTION_TARGET_NAME, targetName);
            return this;
        }

        // Implementations of CameraDeviceConfig.Builder default methods

        @Override
        @NonNull
        public Builder setLensFacing(@NonNull LensFacing lensFacing) {
            getMutableConfig().insertOption(OPTION_LENS_FACING, lensFacing);
            return this;
        }

        @Override
        @NonNull
        public Builder setCameraIdFilter(@NonNull CameraIdFilter cameraIdFilter) {
            getMutableConfig().insertOption(OPTION_CAMERA_ID_FILTER, cameraIdFilter);
            return this;
        }

        // Implementations of UseCaseConfig.Builder default methods

        @Override
        @NonNull
        public Builder setDefaultSessionConfig(@NonNull SessionConfig sessionConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_SESSION_CONFIG, sessionConfig);
            return this;
        }

        @Override
        @NonNull
        public Builder setDefaultCaptureConfig(@NonNull CaptureConfig captureConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_CAPTURE_CONFIG, captureConfig);
            return this;
        }

        @Override
        @NonNull
        public Builder setSessionOptionUnpacker(
                @NonNull SessionConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_SESSION_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        @Override
        @NonNull
        public Builder setCaptureOptionUnpacker(
                @NonNull CaptureConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_CAPTURE_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        @Override
        @NonNull
        public Builder setSurfaceOccupancyPriority(int priority) {
            getMutableConfig().insertOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, priority);
            return this;
        }

        @Override
        @NonNull
        public Builder setUseCaseEventCallback(@NonNull UseCase.EventCallback eventCallback) {
            getMutableConfig().insertOption(OPTION_USE_CASE_EVENT_CALLBACK, eventCallback);
            return this;
        }
    }
}
