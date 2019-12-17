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

package androidx.camera.core;

import android.graphics.ImageFormat;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.ImageCapture.CaptureMode;
import androidx.camera.core.impl.CameraDeviceConfig;
import androidx.camera.core.impl.CameraIdFilter;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.CaptureProcessor;
import androidx.camera.core.impl.CaptureStage;
import androidx.camera.core.impl.OptionsBundle;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Configuration for an image capture use case.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class ImageCaptureConfig
        implements UseCaseConfig<ImageCapture>,
        ImageOutputConfig,
        CameraDeviceConfig, // TODO(b/142840814): Remove in favor of CameraSelector
        IoConfig {

    // Option Declarations:
    // *********************************************************************************************

    static final Option<Integer> OPTION_IMAGE_CAPTURE_MODE =
            Option.create(
                    "camerax.core.imageCapture.captureMode", int.class);
    static final Option<Integer> OPTION_FLASH_MODE =
            Option.create("camerax.core.imageCapture.flashMode", int.class);
    static final Option<CaptureBundle> OPTION_CAPTURE_BUNDLE =
            Option.create("camerax.core.imageCapture.captureBundle", CaptureBundle.class);
    static final Option<CaptureProcessor> OPTION_CAPTURE_PROCESSOR =
            Option.create("camerax.core.imageCapture.captureProcessor", CaptureProcessor.class);
    static final Option<Integer> OPTION_BUFFER_FORMAT =
            Option.create("camerax.core.imageCapture.bufferFormat", Integer.class);
    static final Option<Integer> OPTION_MAX_CAPTURE_STAGES =
            Option.create("camerax.core.imageCapture.maxCaptureStages", Integer.class);

    // *********************************************************************************************

    private final OptionsBundle mConfig;

    /** Creates a new configuration instance. */
    ImageCaptureConfig(OptionsBundle config) {
        mConfig = config;
    }

    /**
     * Returns whether a {@link CaptureMode} option has been set in this configuration.
     *
     * @return true if a {@link CaptureMode} option has been set in this configuration, false
     * otherwise.
     */
    public boolean hasCaptureMode() {
        return containsOption(OPTION_IMAGE_CAPTURE_MODE);
    }

    /**
     * Returns the {@link CaptureMode}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @CaptureMode
    public int getCaptureMode() {
        return retrieveOption(OPTION_IMAGE_CAPTURE_MODE);
    }

    /**
     * Returns the {@link ImageCapture.FlashMode}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @ImageCapture.FlashMode
    public int getFlashMode() {
        return retrieveOption(OPTION_FLASH_MODE);
    }

    /**
     * Returns the {@link CaptureBundle}.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public CaptureBundle getCaptureBundle(@Nullable CaptureBundle valueIfMissing) {
        return retrieveOption(OPTION_CAPTURE_BUNDLE, valueIfMissing);
    }

    /**
     * Returns the {@link CaptureBundle}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public CaptureBundle getCaptureBundle() {
        return retrieveOption(OPTION_CAPTURE_BUNDLE);
    }

    /**
     * Returns the {@link CaptureProcessor}.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public CaptureProcessor getCaptureProcessor(@Nullable CaptureProcessor valueIfMissing) {
        return retrieveOption(OPTION_CAPTURE_PROCESSOR, valueIfMissing);
    }

    /**
     * Returns the {@link CaptureProcessor}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public CaptureProcessor getCaptureProcessor() {
        return retrieveOption(OPTION_CAPTURE_PROCESSOR);
    }

    /**
     * Returns the {@link ImageFormat} of the capture in memory.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>ValueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public Integer getBufferFormat(@Nullable Integer valueIfMissing) {
        return retrieveOption(OPTION_BUFFER_FORMAT, valueIfMissing);
    }

    /**
     * Returns the {@link ImageFormat} of the capture in memory.
     *
     * @return The stored value, if it exists in the configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public Integer getBufferFormat() {
        return retrieveOption(OPTION_BUFFER_FORMAT);
    }

    /**
     * Returns the max number of {@link CaptureStage}.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in
     * this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public int getMaxCaptureStages(int valueIfMissing) {
        return retrieveOption(OPTION_MAX_CAPTURE_STAGES, valueIfMissing);
    }

    /**
     * Returns the max number of {@link CaptureStage}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public int getMaxCaptureStages() {
        return retrieveOption(OPTION_MAX_CAPTURE_STAGES);
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
    public Class<ImageCapture> getTargetClass(
            @Nullable Class<ImageCapture> valueIfMissing) {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<ImageCapture> storedClass =
                (Class<ImageCapture>) retrieveOption(
                        OPTION_TARGET_CLASS,
                        valueIfMissing);
        return storedClass;
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public Class<ImageCapture> getTargetClass() {
        @SuppressWarnings("unchecked") // Value should only be added via Builder#setTargetClass()
                Class<ImageCapture> storedClass =
                (Class<ImageCapture>) retrieveOption(
                        OPTION_TARGET_CLASS);
        return storedClass;
    }

    /**
     * Retrieves the name of the target object being configured.
     *
     * <p>The name should be a value that can uniquely identify an instance of the object being
     * configured.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Override
    @Nullable
    public String getTargetName(@Nullable String valueIfMissing) {
        return retrieveOption(OPTION_TARGET_NAME, valueIfMissing);
    }

    /**
     * Retrieves the name of the target object being configured.
     *
     * <p>The name should be a value that can uniquely identify an instance of the object being
     * configured.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @Override
    @NonNull
    public String getTargetName() {
        return retrieveOption(OPTION_TARGET_NAME);
    }

    // Implementations of CameraDeviceConfig default methods

    /**
     * Returns the lens-facing direction of the camera being configured.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public Integer getLensFacing(@Nullable Integer valueIfMissing) {
        return retrieveOption(OPTION_LENS_FACING, valueIfMissing);
    }

    /**
     * Retrieves the lens facing direction for the primary camera to be configured.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @CameraSelector.LensFacing
    public int getLensFacing() {
        return retrieveOption(OPTION_LENS_FACING);
    }

    /**
     * Returns the set of {@link CameraIdFilter} that filter out unavailable camera id.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>ValueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public CameraIdFilter getCameraIdFilter(@Nullable CameraIdFilter valueIfMissing) {
        return retrieveOption(OPTION_CAMERA_ID_FILTER, valueIfMissing);
    }

    /**
     * Returns the set of {@link CameraIdFilter} that filter out unavailable camera id.
     *
     * @return The stored value, if it exists in the configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public CameraIdFilter getCameraIdFilter() {
        return retrieveOption(OPTION_CAMERA_ID_FILTER);
    }

    // Implementations of ImageOutputConfig default methods

    /**
     * Retrieves the aspect ratio of the target intending to use images from this configuration.
     *
     * <p>This is the ratio of the target's width to the image's height, where the numerator of the
     * provided {@link Rational} corresponds to the width, and the denominator corresponds to the
     * height.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public Rational getTargetAspectRatioCustom(@Nullable Rational valueIfMissing) {
        return retrieveOption(OPTION_TARGET_ASPECT_RATIO_CUSTOM, valueIfMissing);
    }

    /**
     * Retrieves the aspect ratio of the target intending to use images from this configuration.
     *
     * <p>This is the ratio of the target's width to the image's height, where the numerator of the
     * provided {@link Rational} corresponds to the width, and the denominator corresponds to the
     * height.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public Rational getTargetAspectRatioCustom() {
        return retrieveOption(OPTION_TARGET_ASPECT_RATIO_CUSTOM);
    }

    @Override
    public boolean hasTargetAspectRatio() {
        return containsOption(OPTION_TARGET_ASPECT_RATIO);
    }

    /**
     * Retrieves the aspect ratio of the target intending to use images from this configuration.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @AspectRatio.Ratio
    @Override
    public int getTargetAspectRatio() {
        return retrieveOption(OPTION_TARGET_ASPECT_RATIO);
    }

    /**
     * Retrieves the rotation of the target intending to use images from this configuration.
     *
     * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
     * {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}. Rotation values are relative to
     * the device's "natural" rotation, {@link Surface#ROTATION_0}.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Override
    @RotationValue
    public int getTargetRotation(int valueIfMissing) {
        return retrieveOption(OPTION_TARGET_ROTATION, valueIfMissing);
    }

    /**
     * Retrieves the rotation of the target intending to use images from this configuration.
     *
     * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
     * {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}. Rotation values are relative to
     * the device's "natural" rotation, {@link Surface#ROTATION_0}.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @Override
    @RotationValue
    public int getTargetRotation() {
        return retrieveOption(OPTION_TARGET_ROTATION);
    }

    /**
     * Retrieves the resolution of the target intending to use from this configuration.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Override
    @Nullable
    public Size getTargetResolution(@Nullable Size valueIfMissing) {
        return retrieveOption(ImageOutputConfig.OPTION_TARGET_RESOLUTION, valueIfMissing);
    }

    /**
     * Retrieves the resolution of the target intending to use from this configuration.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @Override
    @NonNull
    public Size getTargetResolution() {
        return retrieveOption(ImageOutputConfig.OPTION_TARGET_RESOLUTION);
    }

    /**
     * Retrieves the default resolution of the target intending to use from this configuration.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     * @hide
     */
    @Nullable
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public Size getDefaultResolution(@Nullable Size valueIfMissing) {
        return retrieveOption(OPTION_DEFAULT_RESOLUTION, valueIfMissing);
    }

    /**
     * Retrieves the default resolution of the target intending to use from this configuration.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public Size getDefaultResolution() {
        return retrieveOption(OPTION_DEFAULT_RESOLUTION);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public Size getMaxResolution(@Nullable Size valueIfMissing) {
        return retrieveOption(OPTION_MAX_RESOLUTION, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public Size getMaxResolution() {
        return retrieveOption(OPTION_MAX_RESOLUTION);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public List<Pair<Integer, Size[]>> getSupportedResolutions(
            @Nullable List<Pair<Integer, Size[]>> valueIfMissing) {
        return retrieveOption(OPTION_SUPPORTED_RESOLUTIONS, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public List<Pair<Integer, Size[]>> getSupportedResolutions() {
        return retrieveOption(OPTION_SUPPORTED_RESOLUTIONS);
    }

    // Implementations of IO default methods

    /**
     * Returns the executor that will be used for IO tasks.
     *
     * <p> This executor will be used for any IO tasks specifically for ImageCapture, such as
     * {@link ImageCapture#takePicture(File, Executor, ImageCapture.OnImageSavedCallback)}
     * and {@link ImageCapture#takePicture(File, ImageCapture.Metadata, Executor,
     * ImageCapture.OnImageSavedCallback)}. If no executor is set, then a default Executor
     * specifically for IO will be used instead.
     *
     * @param valueIfMissing The value to return if this configuration option has not been set.
     * @return The stored value or <code>valueIfMissing</code> if the value does not exist in this
     * configuration.
     */
    @Nullable
    @Override
    public Executor getIoExecutor(@Nullable Executor valueIfMissing) {
        return retrieveOption(OPTION_IO_EXECUTOR, valueIfMissing);
    }

    /**
     * Returns the executor that will be used for IO tasks.
     *
     * <p> This executor will be used for any IO tasks specifically for ImageCapture, such as
     * {@link ImageCapture#takePicture(File, Executor, ImageCapture.OnImageSavedCallback)}
     * and {@link ImageCapture#takePicture(File, ImageCapture.Metadata, Executor,
     * ImageCapture.OnImageSavedCallback)}. If no executor is set, then a default Executor
     * specifically for IO will be used instead.
     *
     * @return The stored value, if it exists in this configuration.
     * @throws IllegalArgumentException if the option does not exist in this configuration.
     */
    @NonNull
    @Override
    public Executor getIoExecutor() {
        return retrieveOption(OPTION_IO_EXECUTOR);
    }

    // Implementations of UseCaseConfig default methods

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public SessionConfig getDefaultSessionConfig(@Nullable SessionConfig valueIfMissing) {
        return retrieveOption(OPTION_DEFAULT_SESSION_CONFIG, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public SessionConfig getDefaultSessionConfig() {
        return retrieveOption(OPTION_DEFAULT_SESSION_CONFIG);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public CaptureConfig getDefaultCaptureConfig(@Nullable CaptureConfig valueIfMissing) {
        return retrieveOption(OPTION_DEFAULT_CAPTURE_CONFIG, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public CaptureConfig getDefaultCaptureConfig() {
        return retrieveOption(OPTION_DEFAULT_CAPTURE_CONFIG);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public SessionConfig.OptionUnpacker getSessionOptionUnpacker(
            @Nullable SessionConfig.OptionUnpacker valueIfMissing) {
        return retrieveOption(OPTION_SESSION_CONFIG_UNPACKER, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public SessionConfig.OptionUnpacker getSessionOptionUnpacker() {
        return retrieveOption(OPTION_SESSION_CONFIG_UNPACKER);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public CaptureConfig.OptionUnpacker getCaptureOptionUnpacker(
            @Nullable CaptureConfig.OptionUnpacker valueIfMissing) {
        return retrieveOption(OPTION_CAPTURE_CONFIG_UNPACKER, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public CaptureConfig.OptionUnpacker getCaptureOptionUnpacker() {
        return retrieveOption(OPTION_CAPTURE_CONFIG_UNPACKER);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public int getSurfaceOccupancyPriority(int valueIfMissing) {
        return retrieveOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public int getSurfaceOccupancyPriority() {
        return retrieveOption(OPTION_SURFACE_OCCUPANCY_PRIORITY);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public UseCase.EventCallback getUseCaseEventCallback(
            @Nullable UseCase.EventCallback valueIfMissing) {
        return retrieveOption(OPTION_USE_CASE_EVENT_CALLBACK, valueIfMissing);
    }

    /** @hide */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @NonNull
    public UseCase.EventCallback getUseCaseEventCallback() {
        return retrieveOption(OPTION_USE_CASE_EVENT_CALLBACK);
    }

    // End of the default implementation of Config
    // *********************************************************************************************
}
