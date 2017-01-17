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

package com.android.support.room.testutil;

import com.android.support.executors.AppToolkitTaskExecutor;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

import java.util.List;

/**
 * A JUnit rule that swaps the task executor with a more controllable one.
 * Once we have the TaskExecutor API, we should consider making this public (via some test package).
 */
public class JunitTaskExecutorRule implements TestRule {
    private final TaskExecutorWIthFakeMainThread mTaskExecutor;

    public JunitTaskExecutorRule(int ioThreadCount, boolean spyOnExecutor) {
        if (spyOnExecutor) {
            mTaskExecutor = Mockito.spy(new TaskExecutorWIthFakeMainThread(ioThreadCount));
        } else {
            mTaskExecutor = new TaskExecutorWIthFakeMainThread(ioThreadCount);
        }

    }

    private void beforeStart() {
        AppToolkitTaskExecutor.getInstance().setDelegate(mTaskExecutor);
    }

    private void afterFinished() {
        AppToolkitTaskExecutor.getInstance().setDelegate(null);
    }

    public TaskExecutorWIthFakeMainThread getTaskExecutor() {
        return mTaskExecutor;
    }

    public void drainTasks(int seconds) throws InterruptedException {
        mTaskExecutor.drainTasks(seconds);
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                beforeStart();
                try {
                    base.evaluate();
                    finishExecutors();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                } finally {
                    afterFinished();
                }
            }
        };
    }

    private void finishExecutors() throws InterruptedException, MultipleFailureException {
        mTaskExecutor.shutdown(10);
        final List<Throwable> errors = mTaskExecutor.getErrors();
        if (!errors.isEmpty()) {
            throw new MultipleFailureException(errors);
        }
    }
}
