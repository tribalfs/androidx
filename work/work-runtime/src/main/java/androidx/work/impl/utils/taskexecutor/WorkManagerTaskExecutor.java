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

package androidx.work.impl.utils.taskexecutor;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.work.impl.utils.SerialExecutorImpl;

import java.util.concurrent.Executor;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.ExecutorsKt;

/**
 * Default Task Executor for executing common tasks in WorkManager
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class WorkManagerTaskExecutor implements TaskExecutor {

    private final SerialExecutorImpl mBackgroundExecutor;
    private final CoroutineDispatcher mTaskDispatcher;

    public WorkManagerTaskExecutor(@NonNull Executor backgroundExecutor) {
        // Wrap it with a serial executor so we have ordering guarantees on commands
        // being executed.
        mBackgroundExecutor = new SerialExecutorImpl(backgroundExecutor);
        mTaskDispatcher = ExecutorsKt.from(mBackgroundExecutor);
    }

    final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private final Executor mMainThreadExecutor = new Executor() {
        @Override
        public void execute(@NonNull Runnable command) {
            mMainThreadHandler.post(command);
        }
    };

    @Override
    @NonNull
    public Executor getMainThreadExecutor() {
        return mMainThreadExecutor;
    }

    @Override
    @NonNull
    public SerialExecutorImpl getSerialTaskExecutor() {
        return mBackgroundExecutor;
    }

    @NonNull
    @Override
    public CoroutineDispatcher getTaskCoroutineDispatcher() {
        return mTaskDispatcher;
    }
}
