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

package androidx.work.impl.background.greedy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManagerTest;
import androidx.work.impl.Processor;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.constraints.WorkConstraintsTracker;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;
import androidx.work.worker.TestWorker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


@RunWith(AndroidJUnit4.class)
public class GreedySchedulerTest extends WorkManagerTest {

    private static final String TEST_ID = "test";

    private WorkManagerImpl mWorkManagerImpl;
    private Processor mMockProcessor;
    private WorkConstraintsTracker mMockWorkConstraintsTracker;
    private GreedyScheduler mGreedyScheduler;

    @Before
    public void setUp() {
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        mWorkManagerImpl = mock(WorkManagerImpl.class);
        mMockProcessor = mock(Processor.class);
        mMockWorkConstraintsTracker = mock(WorkConstraintsTracker.class);
        when(mWorkManagerImpl.getProcessor()).thenReturn(mMockProcessor);
        when(mWorkManagerImpl.getWorkTaskExecutor()).thenReturn(taskExecutor);
        mGreedyScheduler = new GreedyScheduler(mWorkManagerImpl, mMockWorkConstraintsTracker);
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_startsUnconstrainedWork() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        WorkSpec workSpec = getWorkSpec(work);
        mGreedyScheduler.schedule(workSpec);
        verify(mWorkManagerImpl).startWork(workSpec.id);
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_ignoresPeriodicWork() {
        PeriodicWorkRequest periodicWork =
                new PeriodicWorkRequest.Builder(TestWorker.class, 0L, TimeUnit.MILLISECONDS)
                        .build();
        mGreedyScheduler.schedule(getWorkSpec(periodicWork));
        verify(mMockWorkConstraintsTracker, never()).replace(ArgumentMatchers.<WorkSpec>anyList());
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_ignoresInitialDelayWork() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialDelay(1000L, TimeUnit.MILLISECONDS)
                .build();
        mGreedyScheduler.schedule(getWorkSpec(work));
        verify(mMockWorkConstraintsTracker, never()).replace(ArgumentMatchers.<WorkSpec>anyList());
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_ignoresBackedOffWork() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialRunAttemptCount(5)
                .build();
        mGreedyScheduler.schedule(getWorkSpec(work));
        verify(mMockWorkConstraintsTracker, never()).replace(ArgumentMatchers.<WorkSpec>anyList());
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_startsWorkWhenConstraintsMet() {
        mGreedyScheduler.onAllConstraintsMet(Collections.singletonList(TEST_ID));
        verify(mWorkManagerImpl).startWork(TEST_ID);
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_stopsWorkWhenConstraintsNotMet() {
        mGreedyScheduler.onAllConstraintsNotMet(Collections.singletonList(TEST_ID));
        verify(mWorkManagerImpl).stopWork(TEST_ID);
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_constraintsAreAddedAndRemovedForTracking() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder().setRequiresCharging(true).build())
                .build();
        WorkSpec workSpec = getWorkSpec(work);
        mGreedyScheduler.schedule(workSpec);
        verify(mMockWorkConstraintsTracker).replace(Collections.singletonList(workSpec));
        reset(mMockWorkConstraintsTracker);

        mGreedyScheduler.onExecuted(workSpec.id, false);
        verify(mMockWorkConstraintsTracker).replace(Collections.<WorkSpec>emptyList());
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_executionListenerIsRegistered() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        WorkSpec workSpec = getWorkSpec(work);
        mGreedyScheduler.schedule(workSpec);
        verify(mMockProcessor).addExecutionListener(mGreedyScheduler);
    }

    @Test
    @SmallTest
    public void testGreedyScheduler_executionListenerIsRegisteredOnlyOnce() {
        for (int i = 0; i < 2; ++i) {
            OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
            WorkSpec workSpec = getWorkSpec(work);
            mGreedyScheduler.schedule(workSpec);
        }
        verify(mMockProcessor, times(1)).addExecutionListener(mGreedyScheduler);
    }
}
