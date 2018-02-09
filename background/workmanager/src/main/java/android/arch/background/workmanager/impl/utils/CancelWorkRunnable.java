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

package android.arch.background.workmanager.impl.utils;

import static android.arch.background.workmanager.State.CANCELLED;
import static android.arch.background.workmanager.State.FAILED;
import static android.arch.background.workmanager.State.SUCCEEDED;

import android.arch.background.workmanager.State;
import android.arch.background.workmanager.impl.WorkDatabase;
import android.arch.background.workmanager.impl.WorkManagerImpl;
import android.arch.background.workmanager.impl.model.DependencyDao;
import android.arch.background.workmanager.impl.model.WorkSpecDao;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import java.util.List;

/**
 * A {@link Runnable} to cancel work.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CancelWorkRunnable implements Runnable {

    private WorkManagerImpl mWorkManagerImpl;
    private WorkDatabase mWorkDatabase;
    private String mId;
    private String mTag;

    public CancelWorkRunnable(WorkManagerImpl workManagerImpl, String id, String tag) {
        mWorkManagerImpl = workManagerImpl;
        mWorkDatabase = mWorkManagerImpl.getWorkDatabase();
        mId = id;
        mTag = tag;
    }

    @WorkerThread
    @Override
    public void run() {
        mWorkDatabase.beginTransaction();
        try {
            WorkSpecDao workSpecDao = mWorkDatabase.workSpecDao();
            if (mTag != null) {
                List<String> workSpecIds = workSpecDao.getUnfinishedWorkWithTag(mTag);
                for (String workSpecId : workSpecIds) {
                    cancel(workSpecId);
                }
            } else if (mId != null) {
                cancel(mId);
            }
            mWorkDatabase.setTransactionSuccessful();
        } finally {
            mWorkDatabase.endTransaction();
        }
    }

    private void cancel(String workSpecId) {
        recursivelyCancelWorkAndDependencies(workSpecId);
        mWorkManagerImpl.getProcessor().stopWork(workSpecId, true);
        mWorkManagerImpl.getBackgroundScheduler().cancel(workSpecId);
    }

    private void recursivelyCancelWorkAndDependencies(String workSpecId) {
        WorkSpecDao workSpecDao = mWorkDatabase.workSpecDao();
        DependencyDao dependencyDao = mWorkDatabase.dependencyDao();

        List<String> dependentIds = dependencyDao.getDependentWorkIds(workSpecId);
        for (String id : dependentIds) {
            recursivelyCancelWorkAndDependencies(id);
        }

        State state = workSpecDao.getState(workSpecId);
        if (state != SUCCEEDED && state != FAILED) {
            workSpecDao.setState(CANCELLED, workSpecId);
        }
    }
}
