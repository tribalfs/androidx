/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.window.extensions.embedding;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.window.extensions.RequiresVendorApiLevel;
import androidx.window.extensions.core.util.function.Function;

/**
 * The parameter container used in standalone {@link ActivityStack} calculator function to report
 * {@link ParentContainerInfo} and associated {@link ActivityStack#getTag()} to calculate
 * {@link ActivityStackAttributes} when there's a parent container information update or a
 * standalone {@link ActivityStack} is going to be launched.
 *
 * @see ActivityEmbeddingComponent#setActivityStackAttributesCalculator(Function)
 */
public class ActivityStackAttributesCalculatorParams {

    @NonNull
    private final ParentContainerInfo mParentContainerInfo;

    @NonNull
    private final String mActivityStackTag;

    @NonNull
    private final Bundle mLaunchOptions;

    /**
     * {@code ActivityStackAttributesCalculatorParams} constructor.
     *
     * @param parentContainerInfo The {@link ParentContainerInfo} of the standalone
     *                            {@link ActivityStack} to apply the
     *                            {@link ActivityStackAttributes}.
     * @param activityStackTag The unique identifier of {@link ActivityStack} to apply the
     *                         {@link ActivityStackAttributes}.
     * @param launchOptions The options to launch the {@link ActivityStack}.
     */
    ActivityStackAttributesCalculatorParams(@NonNull ParentContainerInfo parentContainerInfo,
            @NonNull String activityStackTag, @NonNull Bundle launchOptions) {
        mParentContainerInfo = parentContainerInfo;
        mActivityStackTag = activityStackTag;
        mLaunchOptions = launchOptions;
    }

    /**
     * Returns {@link ParentContainerInfo} of the standalone {@link ActivityStack} to calculate.
     */
    @RequiresVendorApiLevel(level = 5)
    @NonNull
    public ParentContainerInfo getParentContainerInfo() {
        return mParentContainerInfo;
    }

    /**
     * Returns unique identifier of the standalone {@link ActivityStack} to calculate.
     */
    @RequiresVendorApiLevel(level = 5)
    @NonNull
    public String getActivityStackTag() {
        return mActivityStackTag;
    }

    /**
     * Returns options that passed from WM Jetpack to WM Extensions library to launch an
     * {@link ActivityStack}. {@link Bundle#isEmpty() empty} options mean there's no launch options.
     * <p>
     * For example, an {@link ActivityStack} launch options could be an
     * {@link android.app.ActivityOptions} bundle that contains information to build an overlay
     * {@link ActivityStack}.
     * <p>
     * The launch options will be used for initializing standalone {@link ActivityStack} with
     * {@link #getActivityStackTag()} specified. The logic is owned by WM Jetpack, which is usually
     * from the {@link android.app.ActivityOptions}, WM Extensions library must not touch the
     * options.
     */
    @RequiresVendorApiLevel(level = 5)
    @NonNull
    public Bundle getLaunchOptions() {
        return mLaunchOptions;
    }

    @NonNull
    @Override
    public String toString() {
        return ActivityStackAttributesCalculatorParams.class.getSimpleName() + ":{"
                + "parentContainerInfo=" + mParentContainerInfo
                + "activityStackTag=" + mActivityStackTag
                + "launchOptions=" + mLaunchOptions
                + "}";
    }
}
