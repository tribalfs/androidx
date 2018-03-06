/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.work.impl;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.work.Arguments;
import androidx.work.BackoffPolicy;
import androidx.work.BaseWork;
import androidx.work.Constraints;
import androidx.work.PeriodicWork;
import androidx.work.State;
import androidx.work.Worker;
import androidx.work.impl.model.WorkSpec;

/**
 * A concrete implementation of {@link PeriodicWork}.
 */

public class PeriodicWorkImpl extends PeriodicWork implements InternalWorkImpl {

    private WorkSpec mWorkSpec;
    private Set<String> mTags;

    PeriodicWorkImpl(Builder builder) {
        mWorkSpec = builder.mWorkSpec;
        mTags = builder.mTags;
    }

    @Override
    public String getId() {
        return mWorkSpec.getId();
    }

    @Override
    public WorkSpec getWorkSpec() {
        return mWorkSpec;
    }

    @Override
    public Set<String> getTags() {
        return mTags;
    }

    /**
     * Builder for {@link PeriodicWorkImpl} class.
     */
    public static class Builder implements BaseWork.Builder<PeriodicWorkImpl, Builder> {

        private boolean mBackoffCriteriaSet = false;
        WorkSpec mWorkSpec = new WorkSpec(UUID.randomUUID().toString());
        Set<String> mTags = new HashSet<>();

        public Builder(
                @NonNull Class<? extends Worker> workerClass,
                long repeatInterval,
                @NonNull TimeUnit repeatIntervalTimeUnit) {
            mWorkSpec.setWorkerClassName(workerClass.getName());
            mWorkSpec.setPeriodic(repeatIntervalTimeUnit.toMillis(repeatInterval));
        }

        public Builder(
                @NonNull Class<? extends Worker> workerClass,
                long repeatInterval,
                @NonNull TimeUnit repeatIntervalTimeUnit,
                long flexInterval,
                @NonNull TimeUnit flexIntervalTimeUnit) {
            mWorkSpec.setWorkerClassName(workerClass.getName());
            mWorkSpec.setPeriodic(
                    repeatIntervalTimeUnit.toMillis(repeatInterval),
                    flexIntervalTimeUnit.toMillis(flexInterval));
        }

        @VisibleForTesting
        @Override
        public Builder withInitialState(@NonNull State state) {
            mWorkSpec.setState(state);
            return this;
        }

        @VisibleForTesting
        @Override
        public Builder withInitialRunAttemptCount(int runAttemptCount) {
            mWorkSpec.setRunAttemptCount(runAttemptCount);
            return this;
        }

        @VisibleForTesting
        @Override
        public Builder withPeriodStartTime(long periodStartTime, @NonNull TimeUnit timeUnit) {
            mWorkSpec.setPeriodStartTime(timeUnit.toMillis(periodStartTime));
            return this;
        }

        @Override
        public Builder withBackoffCriteria(
                @NonNull BackoffPolicy backoffPolicy,
                long backoffDelay,
                @NonNull TimeUnit timeUnit) {
            mBackoffCriteriaSet = true;
            mWorkSpec.setBackoffPolicy(backoffPolicy);
            mWorkSpec.setBackoffDelayDuration(timeUnit.toMillis(backoffDelay));
            return this;
        }

        @Override
        public Builder withConstraints(@NonNull Constraints constraints) {
            mWorkSpec.setConstraints(constraints);
            return this;
        }

        @Override
        public Builder withArguments(@NonNull Arguments arguments) {
            mWorkSpec.setArguments(arguments);
            return this;
        }

        @Override
        public Builder addTag(@NonNull String tag) {
            mTags.add(tag);
            return this;
        }

        @Override
        public PeriodicWorkImpl build() {
            if (mBackoffCriteriaSet && Build.VERSION.SDK_INT >= 23
                    && mWorkSpec.getConstraints().requiresDeviceIdle()) {
                throw new IllegalArgumentException(
                        "Cannot set backoff criteria on an idle mode job");
            }
            return new PeriodicWorkImpl(this);
        }
    }
}
