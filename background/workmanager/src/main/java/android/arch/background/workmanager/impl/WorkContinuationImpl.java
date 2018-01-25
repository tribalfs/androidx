/*
 * Copyright 2018 The Android Open Source Project
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

package android.arch.background.workmanager.impl;

import android.arch.background.workmanager.BaseWork;
import android.arch.background.workmanager.Work;
import android.arch.background.workmanager.WorkContinuation;
import android.arch.background.workmanager.WorkManager;
import android.arch.background.workmanager.Worker;
import android.arch.background.workmanager.impl.utils.BaseWorkHelper;
import android.arch.background.workmanager.impl.utils.EnqueueRunnable;
import android.arch.background.workmanager.impl.workers.JoinWorker;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A concrete implementation of {@link WorkContinuation}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class WorkContinuationImpl extends WorkContinuation {

    private static final String TAG = "WorkContinuationImpl";

    private final WorkManagerImpl mWorkManagerImpl;
    private final String mUniqueTag;
    private final @WorkManager.ExistingWorkPolicy int mExistingWorkPolicy;
    private final BaseWork[] mWork;
    private final List<String> mIds;
    private final List<String> mAllIds;
    private final List<WorkContinuationImpl> mParents;
    private boolean mEnqueued;

    @NonNull
    public WorkManagerImpl getWorkManagerImpl() {
        return mWorkManagerImpl;
    }

    @Nullable
    public String getUniqueTag() {
        return mUniqueTag;
    }

    public @WorkManager.ExistingWorkPolicy int getExistingWorkPolicy() {
        return mExistingWorkPolicy;
    }

    @NonNull
    public BaseWork[] getWork() {
        return mWork;
    }

    @NonNull
    public List<String> getIds() {
        return mIds;
    }

    public List<String> getAllIds() {
        return mAllIds;
    }

    public boolean isEnqueued() {
        return mEnqueued;
    }

    /**
     * Marks the {@link WorkContinuationImpl} as enqueued.
     */
    public void markEnqueued() {
        mEnqueued = true;
    }

    public List<WorkContinuationImpl> getParents() {
        return mParents;
    }

    WorkContinuationImpl(
            @NonNull WorkManagerImpl workManagerImpl,
            @NonNull BaseWork... work) {
        this(workManagerImpl, null, WorkManager.KEEP_EXISTING_WORK, work, null);
    }

    WorkContinuationImpl(@NonNull WorkManagerImpl workManagerImpl,
            String uniqueTag,
            @WorkManager.ExistingWorkPolicy int existingWorkPolicy,
            @NonNull BaseWork... work) {
        this(workManagerImpl, uniqueTag, existingWorkPolicy, work, null);
    }

    WorkContinuationImpl(@NonNull WorkManagerImpl workManagerImpl,
            String uniqueTag,
            @WorkManager.ExistingWorkPolicy int existingWorkPolicy,
            @NonNull BaseWork[] work,
            @Nullable List<WorkContinuationImpl> parents) {
        mWorkManagerImpl = workManagerImpl;
        mUniqueTag = uniqueTag;
        mExistingWorkPolicy = existingWorkPolicy;
        mWork = work;
        mParents = parents;
        mIds = new ArrayList<>(mWork.length);
        mAllIds = new ArrayList<>();
        if (parents != null) {
            for (WorkContinuationImpl parent : parents) {
                mAllIds.addAll(parent.mAllIds);
            }
        }
        for (int i = 0; i < work.length; i++) {
            mIds.add(work[i].getId());
            mAllIds.add(work[i].getId());
        }
    }

    @Override
    public WorkContinuation then(Work... work) {
        // TODO (rahulrav@) We need to decide if we want to allow chaining of continuations after
        // an initial call to enqueue()
        return new WorkContinuationImpl(mWorkManagerImpl,
                mUniqueTag,
                WorkManager.KEEP_EXISTING_WORK,
                work,
                Collections.singletonList(this));
    }

    @Override
    public WorkContinuation then(List<Class<? extends Worker>> workerClasses) {
        return new WorkContinuationImpl(mWorkManagerImpl,
                mUniqueTag,
                WorkManager.KEEP_EXISTING_WORK,
                BaseWorkHelper.convertWorkerClassListToWorkArray(workerClasses),
                Collections.singletonList(this));
    }

    @Override
    public LiveData<Map<String, Integer>> getStatuses() {
        return mWorkManagerImpl.getStatusesFor(mAllIds);
    }

    @Override
    public void enqueue() {
        // Only enqueue if not already enqueued.
        if (!mEnqueued) {
            // The runnable walks the hierarchy of the continuations
            // and marks them enqueued using the markEnqueued() method, parent first.
            mWorkManagerImpl.getTaskExecutor()
                    .executeOnBackgroundThread(new EnqueueRunnable(this));
        } else {
            Log.w(TAG,
                    String.format("Already enqueued work ids (%s).", TextUtils.join(", ", mIds)));
        }
    }

    @Override
    protected WorkContinuation joinInternal(
            @Nullable Work work,
            @NonNull WorkContinuation... continuations) {

        if (work == null) {
            work = new Work.Builder(JoinWorker.class).build();
        }

        List<WorkContinuationImpl> parents = new ArrayList<>(continuations.length);
        for (WorkContinuation continuation : continuations) {
            parents.add((WorkContinuationImpl) continuation);
        }

        return new WorkContinuationImpl(mWorkManagerImpl,
                null,
                WorkManager.KEEP_EXISTING_WORK,
                new BaseWork[]{work},
                parents);
    }
}
