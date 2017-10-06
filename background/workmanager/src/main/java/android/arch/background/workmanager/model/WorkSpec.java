/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.background.workmanager.model;

import android.arch.background.workmanager.Work;
import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.NonNull;

/**
 * Stores information about a logical unit of work.
 */
@Entity
@TypeConverters(Arguments.class)
public class WorkSpec {

    @ColumnInfo(name = "id")
    @PrimaryKey
    @NonNull
    String mId;

    // TODO(xbhatnag)
    @ColumnInfo(name = "repeat_duration")
    long mRepeatDuration;

    // TODO(xbhatnag)
    @ColumnInfo(name = "flex_duration")
    long mFlexDuration;

    @ColumnInfo(name = "status")
    @Work.WorkStatus
    int mStatus = Work.STATUS_ENQUEUED;

    @ColumnInfo(name = "initial_delay")
    long mInitialDelay;

    @ColumnInfo(name = "worker_class_name")
    String mWorkerClassName;

    @Embedded
    Constraints mConstraints = new Constraints.Builder().build();

    Arguments mArguments = new Arguments();

    String mTag;

    // TODO(sumir): Should Backoff be disabled by default?
    @ColumnInfo(name = "backoff_policy")
    @Work.BackoffPolicy
    int mBackoffPolicy = Work.BACKOFF_POLICY_EXPONENTIAL;

    @ColumnInfo(name = "backoff_delay_duration")
    long mBackoffDelayDuration = Work.DEFAULT_BACKOFF_DELAY_DURATION;

    public WorkSpec(@NonNull String id) {
        mId = id;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    public void setId(@NonNull String id) {
        mId = id;
    }

    public long getRepeatDuration() {
        return mRepeatDuration;
    }

    public void setRepeatDuration(long repeatDuration) {
        mRepeatDuration = repeatDuration;
    }

    public long getFlexDuration() {
        return mFlexDuration;
    }

    public void setFlexDuration(long flexDuration) {
        mFlexDuration = flexDuration;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public String getWorkerClassName() {
        return mWorkerClassName;
    }

    public void setWorkerClassName(String workerClassName) {
        mWorkerClassName = workerClassName;
    }

    public Constraints getConstraints() {
        return mConstraints;
    }

    public void setConstraints(Constraints constraints) {
        mConstraints = constraints;
    }

    public Arguments getArguments() {
        return mArguments;
    }

    public void setArguments(Arguments arguments) {
        mArguments = arguments;
    }

    public String getTag() {
        return mTag;
    }

    public void setTag(String tag) {
        mTag = tag;
    }

    public int getBackoffPolicy() {
        return mBackoffPolicy;
    }

    public void setBackoffPolicy(int backoffPolicy) {
        mBackoffPolicy = backoffPolicy;
    }

    public long getBackoffDelayDuration() {
        return mBackoffDelayDuration;
    }

    public void setBackoffDelayDuration(long backoffDelayDuration) {
        mBackoffDelayDuration = backoffDelayDuration;
    }

    public long getInitialDelay() {
        return mInitialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        mInitialDelay = initialDelay;
    }
}
