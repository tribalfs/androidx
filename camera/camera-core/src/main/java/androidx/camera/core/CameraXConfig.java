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

package androidx.camera.core;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.impl.CameraDeviceSurfaceManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * A configuration for adding implementation and user-specific behavior to CameraX.
 *
 * <p>CameraXConfig provides customizable options for camera provider instances that persist for
 * the lifetime of the provider.
 *
 * <p>An implementation of AppConfig must be provided by subclassing the
 * {@link Application} object and implementing {@link CameraXConfig.Provider}.
 * {@linkplain androidx.camera.lifecycle.ProcessCameraProvider#getInstance(android.content.Context)
 * An example} of how this is used can be found in the {@link androidx.camera.lifecycle} package.
 *
 * <p>Applications can create and use {@linkplain androidx.camera.camera2.Camera2Config the
 * implementation} of AppConfig provided in {@link androidx.camera.camera2}.
 *
 * @see androidx.camera.lifecycle
 * @see CameraXConfig.Builder
 */
public final class CameraXConfig implements TargetConfig<CameraX>, Config {

    /**
     * An interface which can be implemented to provide the configuration for CameraX.
     *
     * <p>When implemented by an {@link Application}, this can provide on-demand initialization
     * of CameraX.
     *
     * <p>{@linkplain
     * androidx.camera.lifecycle.ProcessCameraProvider#getInstance(android.content.Context) An
     * example} of how this is used can be found in the {@link androidx.camera.lifecycle} package.
     */
    public interface Provider {
        /** Returns the configuration to use for initializing an instance of CameraX. */
        @NonNull
        CameraXConfig getCameraXConfig();
    }

    // Option Declarations:
    // *********************************************************************************************

    static final Option<CameraFactory> OPTION_CAMERA_FACTORY =
            Option.create("camerax.core.appConfig.cameraFactory", CameraFactory.class);
    static final Option<CameraDeviceSurfaceManager> OPTION_DEVICE_SURFACE_MANAGER =
            Option.create(
                    "camerax.core.appConfig.deviceSurfaceManager",
                    CameraDeviceSurfaceManager.class);
    static final Option<UseCaseConfigFactory> OPTION_USECASE_CONFIG_FACTORY =
            Option.create(
                    "camerax.core.appConfig.useCaseConfigFactory",
                    UseCaseConfigFactory.class);
    static final Option<Executor> OPTION_CAMERA_EXECUTOR =
            Option.create(
                    "camerax.core.appConfig.cameraExecutor",
                    Executor.class);

    // *********************************************************************************************

    private final OptionsBundle mConfig;

    CameraXConfig(OptionsBundle options) {
        mConfig = options;
    }

    /**
     * Returns the {@link CameraFactory} implementation for the application.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public CameraFactory getCameraFactory(@Nullable CameraFactory valueIfMissing) {
        return mConfig.retrieveOption(OPTION_CAMERA_FACTORY, valueIfMissing);
    }

    /**
     * Returns the {@link CameraDeviceSurfaceManager} implementation for the application.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public CameraDeviceSurfaceManager getDeviceSurfaceManager(
            @Nullable CameraDeviceSurfaceManager valueIfMissing) {
        return mConfig.retrieveOption(OPTION_DEVICE_SURFACE_MANAGER, valueIfMissing);
    }

    /**
     * Returns the {@link UseCaseConfigFactory} implementation for the application.
     *
     * <p>This factory should produce all default configurations for the application's use cases.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public UseCaseConfigFactory getUseCaseConfigRepository(
            @Nullable UseCaseConfigFactory valueIfMissing) {
        return mConfig.retrieveOption(OPTION_USECASE_CONFIG_FACTORY, valueIfMissing);
    }

    /**
     * Returns the camera executor.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public Executor getCameraExecutor(@Nullable Executor valueIfMissing) {
        return mConfig.retrieveOption(OPTION_CAMERA_EXECUTOR, valueIfMissing);
    }

    // Start of the default implementation of Config
    // *********************************************************************************************

    // Implementations of Config default methods

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public boolean containsOption(@NonNull Option<?> id) {
        return mConfig.containsOption(id);
    }


    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public <ValueT> ValueT retrieveOption(@NonNull Option<ValueT> id) {
        return mConfig.retrieveOption(id);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public <ValueT> ValueT retrieveOption(@NonNull Option<ValueT> id,
            @Nullable ValueT valueIfMissing) {
        return mConfig.retrieveOption(id, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void findOptions(@NonNull String idStem, @NonNull OptionMatcher matcher) {
        mConfig.findOptions(idStem, matcher);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public Set<Option<?>> listOptions() {
        return mConfig.listOptions();
    }

    // Implementations of TargetConfig default methods

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public Class<CameraX> getTargetClass(
            @Nullable Class<CameraX> valueIfMissing) {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<CameraX> storedClass = (Class<CameraX>) retrieveOption(
                OPTION_TARGET_CLASS,
                valueIfMissing);
        return storedClass;
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public Class<CameraX> getTargetClass() {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<CameraX> storedClass = (Class<CameraX>) retrieveOption(
                OPTION_TARGET_CLASS);
        return storedClass;
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public String getTargetName(@Nullable String valueIfMissing) {
        return retrieveOption(OPTION_TARGET_NAME, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public String getTargetName() {
        return retrieveOption(OPTION_TARGET_NAME);
    }

    // End of the default implementation of Config
    // *********************************************************************************************

    /** A builder for generating {@link CameraXConfig} objects. */
    public static final class Builder
            implements TargetConfig.Builder<CameraX, CameraXConfig.Builder> {

        private final MutableOptionsBundle mMutableConfig;

        /** Creates a new Builder object. */
        public Builder() {
            this(MutableOptionsBundle.create());
        }

        private Builder(MutableOptionsBundle mutableConfig) {
            mMutableConfig = mutableConfig;

            Class<?> oldConfigClass =
                    mutableConfig.retrieveOption(TargetConfig.OPTION_TARGET_CLASS, null);
            if (oldConfigClass != null && !oldConfigClass.equals(CameraX.class)) {
                throw new IllegalArgumentException(
                        "Invalid target class configuration for "
                                + CameraXConfig.Builder.this
                                + ": "
                                + oldConfigClass);
            }

            setTargetClass(CameraX.class);
        }

        /**
         * Generates a Builder from another {@link CameraXConfig} object
         *
         * @param configuration An immutable configuration to pre-populate this builder.
         * @return The new Builder.
         */
        @NonNull
        public static Builder fromConfig(@NonNull CameraXConfig configuration) {
            return new Builder(MutableOptionsBundle.from(configuration));
        }

        /**
         * Sets the {@link CameraFactory} implementation for the application.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setCameraFactory(@NonNull CameraFactory cameraFactory) {
            getMutableConfig().insertOption(OPTION_CAMERA_FACTORY, cameraFactory);
            return this;
        }

        /**
         * Sets the {@link CameraDeviceSurfaceManager} implementation for the application.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setDeviceSurfaceManager(@NonNull CameraDeviceSurfaceManager repository) {
            getMutableConfig().insertOption(OPTION_DEVICE_SURFACE_MANAGER, repository);
            return this;
        }

        /**
         * Sets the {@link UseCaseConfigFactory} implementation for the application.
         *
         * <p>This factory should produce all default configurations for the application's use
         * cases.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setUseCaseConfigFactory(@NonNull UseCaseConfigFactory repository) {
            getMutableConfig().insertOption(OPTION_USECASE_CONFIG_FACTORY, repository);
            return this;
        }

        /**
         * Sets an executor which CameraX will use to initialize and shutdown.
         *
         * <p>It is not necessary to set an executor for normal use.  If not set, CameraX will
         * create and use a default internal executor.
         */
        @NonNull
        public Builder setCameraExecutor(@NonNull Executor executor) {
            getMutableConfig().insertOption(OPTION_CAMERA_EXECUTOR, executor);
            return this;
        }

        @NonNull
        private MutableConfig getMutableConfig() {
            return mMutableConfig;
        }

        /**
         * Builds an immutable {@link CameraXConfig} from the current state.
         *
         * @return A {@link CameraXConfig} populated with the current state.
         */
        @NonNull
        public CameraXConfig build() {
            return new CameraXConfig(OptionsBundle.from(mMutableConfig));
        }

        // Implementations of TargetConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setTargetClass(@NonNull Class<CameraX> targetClass) {
            getMutableConfig().insertOption(OPTION_TARGET_CLASS, targetClass);

            // If no name is set yet, then generate a unique name
            if (null == getMutableConfig().retrieveOption(OPTION_TARGET_NAME, null)) {
                String targetName = targetClass.getCanonicalName() + "-" + UUID.randomUUID();
                setTargetName(targetName);
            }

            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setTargetName(@NonNull String targetName) {
            getMutableConfig().insertOption(OPTION_TARGET_NAME, targetName);
            return this;
        }
    }
}
