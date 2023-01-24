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
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.StreamSpec;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.impl.UseCaseConfigFactory.CaptureType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake {@link UseCase}.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public class FakeUseCase extends UseCase {
    private volatile boolean mIsDetached = false;
    private final AtomicInteger mStateAttachedCount = new AtomicInteger(0);
    private final CaptureType mCaptureType;
    private boolean mMergedConfigRetrieved = false;

    /**
     * Creates a new instance of a {@link FakeUseCase} with a given configuration and capture type.
     */
    public FakeUseCase(@NonNull FakeUseCaseConfig config, @NonNull CaptureType captureType) {
        super(config);
        mCaptureType = captureType;
    }

    /**
     * Creates a new instance of a {@link FakeUseCase} with a given configuration.
     */
    public FakeUseCase(@NonNull FakeUseCaseConfig config) {
        this(config, CaptureType.PREVIEW);
    }

    /**
     * Creates a new instance of a {@link FakeUseCase} with a default configuration.
     */
    public FakeUseCase() {
        this(new FakeUseCaseConfig.Builder().getUseCaseConfig());
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder(@NonNull Config config) {
        return new FakeUseCaseConfig.Builder(config)
                .setSessionOptionUnpacker((useCaseConfig, sessionConfigBuilder) -> {
                });
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public UseCaseConfig<?> getDefaultConfig(boolean applyDefaultConfig,
            @NonNull UseCaseConfigFactory factory) {
        Config config = factory.getConfig(
                mCaptureType,
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);
        return config == null ? null : getUseCaseConfigBuilder(config).getUseCaseConfig();
    }

    @NonNull
    @Override
    protected UseCaseConfig<?> onMergeConfig(@NonNull CameraInfoInternal cameraInfo,
            @NonNull UseCaseConfig.Builder<?, ?, ?> builder) {
        mMergedConfigRetrieved = true;
        return builder.getUseCaseConfig();
    }

    @Override
    public void onUnbind() {
        super.onUnbind();
        mIsDetached = true;
    }

    @Override
    public void onStateAttached() {
        super.onStateAttached();
        mStateAttachedCount.incrementAndGet();
    }

    @Override
    public void onStateDetached() {
        super.onStateDetached();
        mStateAttachedCount.decrementAndGet();
    }

    @Override
    @NonNull
    protected StreamSpec onSuggestedStreamSpecUpdated(@NonNull StreamSpec suggestedStreamSpec) {
        return suggestedStreamSpec;
    }

    /**
     * Returns true if {@link #onUnbind()} has been called previously.
     */
    public boolean isDetached() {
        return mIsDetached;
    }

    /**
     * Returns true if {@link #onStateAttached()} has been called previously.
     */
    public int getStateAttachedCount() {
        return mStateAttachedCount.get();
    }

    /**
     * Returns true if {@link #mergeConfigs} have been invoked.
     */
    public boolean getMergedConfigRetrieved() {
        return mMergedConfigRetrieved;
    }
}
