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

package android.arch.background.workmanager.impl;

import static android.arch.background.workmanager.State.BLOCKED;
import static android.arch.background.workmanager.State.CANCELLED;
import static android.arch.background.workmanager.State.ENQUEUED;
import static android.arch.background.workmanager.State.FAILED;
import static android.arch.background.workmanager.State.RUNNING;
import static android.arch.background.workmanager.State.SUCCEEDED;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.arch.background.workmanager.Arguments;
import android.arch.background.workmanager.ArrayCreatingInputMerger;
import android.arch.background.workmanager.DatabaseTest;
import android.arch.background.workmanager.PeriodicWork;
import android.arch.background.workmanager.Work;
import android.arch.background.workmanager.Worker;
import android.arch.background.workmanager.impl.model.Dependency;
import android.arch.background.workmanager.impl.model.DependencyDao;
import android.arch.background.workmanager.impl.model.WorkSpec;
import android.arch.background.workmanager.impl.model.WorkSpecDao;
import android.arch.background.workmanager.impl.utils.taskexecutor.InstantTaskExecutorRule;
import android.arch.background.workmanager.worker.ChainedArgumentWorker;
import android.arch.background.workmanager.worker.EchoingWorker;
import android.arch.background.workmanager.worker.FailureWorker;
import android.arch.background.workmanager.worker.RetryWorker;
import android.arch.background.workmanager.worker.SleepTestWorker;
import android.arch.background.workmanager.worker.TestWorker;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class WorkerWrapperTest extends DatabaseTest {
    private WorkSpecDao mWorkSpecDao;
    private DependencyDao mDependencyDao;
    private Context mContext;
    private ExecutionListener mMockListener;
    private Scheduler mMockScheduler;

    @Rule
    public InstantTaskExecutorRule mRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mWorkSpecDao = mDatabase.workSpecDao();
        mDependencyDao = mDatabase.dependencyDao();
        mMockListener = mock(ExecutionListener.class);
        mMockScheduler = mock(Scheduler.class);
    }

    @Test
    @SmallTest
    public void testSuccess() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), true, false);
        assertThat(mWorkSpecDao.getState(work.getId()), is(SUCCEEDED));
    }

    @Test
    @SmallTest
    public void testRunAttemptCountIncremented_successfulExecution() {
        Work work = new Work.Builder(TestWorker.class).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        WorkSpec latestWorkSpec = mWorkSpecDao.getWorkSpec(work.getId());
        assertThat(latestWorkSpec.getRunAttemptCount(), is(1));
    }

    @Test
    @SmallTest
    public void testRunAttemptCountIncremented_failedExecution() {
        Work work = new Work.Builder(FailureWorker.class).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        WorkSpec latestWorkSpec = mWorkSpecDao.getWorkSpec(work.getId());
        assertThat(latestWorkSpec.getRunAttemptCount(), is(1));
    }

    @Test
    @SmallTest
    public void testPermanentErrorWithInvalidWorkSpecId() throws InterruptedException {
        final String invalidWorkSpecId = "INVALID_ID";
        new WorkerWrapper.Builder(mContext, mDatabase, invalidWorkSpecId)
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(invalidWorkSpecId, false, false);
    }

    @Test
    @SmallTest
    public void testNotEnqueued() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).withInitialState(RUNNING).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), false, true);
    }

    @Test
    @SmallTest
    public void testCancelled() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).withInitialState(CANCELLED).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), false, false);
        assertThat(mWorkSpecDao.getState(work.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testPermanentErrorWithInvalidWorkerClass() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).build();
        getWorkSpec(work).setWorkerClassName("INVALID_CLASS_NAME");
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), false, false);
        assertThat(mWorkSpecDao.getState(work.getId()), is(FAILED));
    }

    @Test
    @SmallTest
    public void testPermanentErrorWithInvalidInputMergerClass() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).build();
        getWorkSpec(work).setInputMergerClassName("INVALID_CLASS_NAME");
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), false, false);
        assertThat(mWorkSpecDao.getState(work.getId()), is(FAILED));
    }

    @Test
    @SmallTest
    public void testFailed() throws InterruptedException {
        Work work = new Work.Builder(FailureWorker.class).build();
        insertWork(work);
        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build()
                .run();
        verify(mMockListener).onExecuted(work.getId(), false, false);
        assertThat(mWorkSpecDao.getState(work.getId()), is(FAILED));
    }

    @Test
    @LargeTest
    public void testRunning() throws InterruptedException {
        Work work = new Work.Builder(SleepTestWorker.class).build();
        insertWork(work);
        WorkerWrapper wrapper = new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withListener(mMockListener)
                .build();
        Executors.newSingleThreadExecutor().submit(wrapper);
        Thread.sleep(2000L); // Async wait duration.
        assertThat(mWorkSpecDao.getState(work.getId()), is(RUNNING));
        Thread.sleep(SleepTestWorker.SLEEP_DURATION);
        verify(mMockListener).onExecuted(work.getId(), true, false);
    }

    @Test
    @SmallTest
    public void testDependencies() {
        Work prerequisiteWork = new Work.Builder(TestWorker.class).build();
        Work work = new Work.Builder(TestWorker.class).withInitialState(BLOCKED).build();
        Dependency dependency = new Dependency(work.getId(), prerequisiteWork.getId());

        mDatabase.beginTransaction();
        try {
            insertWork(prerequisiteWork);
            insertWork(work);
            mDependencyDao.insertDependency(dependency);
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }

        assertThat(mWorkSpecDao.getState(prerequisiteWork.getId()), is(ENQUEUED));
        assertThat(mWorkSpecDao.getState(work.getId()), is(BLOCKED));
        assertThat(mDependencyDao.hasCompletedAllPrerequisites(work.getId()), is(false));

        new WorkerWrapper.Builder(mContext, mDatabase, prerequisiteWork.getId())
                .withListener(mMockListener)
                .withSchedulers(Collections.singletonList(mMockScheduler))
                .build()
                .run();

        assertThat(mWorkSpecDao.getState(prerequisiteWork.getId()), is(SUCCEEDED));
        assertThat(mWorkSpecDao.getState(work.getId()), is(ENQUEUED));
        assertThat(mDependencyDao.hasCompletedAllPrerequisites(work.getId()), is(true));

        ArgumentCaptor<WorkSpec> captor = ArgumentCaptor.forClass(WorkSpec.class);
        verify(mMockScheduler).schedule(captor.capture());
        assertThat(captor.getValue().getId(), is(work.getId()));
    }

    @Test
    @SmallTest
    public void testDependencies_passesOutputs() {
        Work prerequisiteWork = new Work.Builder(ChainedArgumentWorker.class).build();
        Work work = new Work.Builder(TestWorker.class).withInitialState(BLOCKED).build();
        Dependency dependency = new Dependency(work.getId(), prerequisiteWork.getId());

        mDatabase.beginTransaction();
        try {
            insertWork(prerequisiteWork);
            insertWork(work);
            mDependencyDao.insertDependency(dependency);
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }

        new WorkerWrapper.Builder(mContext, mDatabase, prerequisiteWork.getId()).build().run();

        List<Arguments> arguments = mWorkSpecDao.getInputsFromPrerequisites(work.getId());
        assertThat(arguments.size(), is(1));
        assertThat(arguments, contains(ChainedArgumentWorker.getChainedArguments()));
    }

    @Test
    @SmallTest
    public void testDependencies_passesMergedOutputs() {
        String key = "key";
        String value1 = "value1";
        String value2 = "value2";

        Work prerequisiteWork1 = new Work.Builder(EchoingWorker.class)
                .withArguments(new Arguments.Builder().putString(key, value1).build())
                .build();
        Work prerequisiteWork2 = new Work.Builder(EchoingWorker.class)
                .withArguments(new Arguments.Builder().putString(key, value2).build())
                .build();
        Work work = new Work.Builder(TestWorker.class)
                .withInputMerger(ArrayCreatingInputMerger.class)
                .build();
        Dependency dependency1 = new Dependency(work.getId(), prerequisiteWork1.getId());
        Dependency dependency2 = new Dependency(work.getId(), prerequisiteWork2.getId());

        mDatabase.beginTransaction();
        try {
            insertWork(prerequisiteWork1);
            insertWork(prerequisiteWork2);
            insertWork(work);
            mDependencyDao.insertDependency(dependency1);
            mDependencyDao.insertDependency(dependency2);
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }

        // Run the prerequisites.
        new WorkerWrapper.Builder(mContext, mDatabase, prerequisiteWork1.getId()).build().run();
        new WorkerWrapper.Builder(mContext, mDatabase, prerequisiteWork2.getId()).build().run();

        // Create and run the dependent work.
        WorkerWrapper workerWrapper = new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .build();
        workerWrapper.run();

        Arguments arguments = workerWrapper.mWorker.getArguments();
        assertThat(arguments.size(), is(1));
        assertThat(Arrays.asList(arguments.getStringArray(key)),
                containsInAnyOrder(value1, value2));
    }

    @Test
    @SmallTest
    public void testDependencies_setsPeriodStartTimesForUnblockedWork() {
        Work prerequisiteWork = new Work.Builder(TestWorker.class).build();
        Work work = new Work.Builder(TestWorker.class).withInitialState(BLOCKED).build();
        Dependency dependency = new Dependency(work.getId(), prerequisiteWork.getId());

        mDatabase.beginTransaction();
        try {
            insertWork(prerequisiteWork);
            insertWork(work);
            mDependencyDao.insertDependency(dependency);
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }

        long beforeUnblockedTime = System.currentTimeMillis();

        new WorkerWrapper.Builder(mContext, mDatabase, prerequisiteWork.getId())
                .withListener(mMockListener)
                .withSchedulers(Collections.singletonList(mMockScheduler))
                .build()
                .run();

        WorkSpec workSpec = mWorkSpecDao.getWorkSpec(work.getId());
        assertThat(workSpec.getPeriodStartTime(), is(greaterThan(beforeUnblockedTime)));
    }

    @Test
    @SmallTest
    public void testRun_periodicWork_success_updatesPeriodStartTime() {
        long intervalDuration = PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS;
        long periodStartTime = System.currentTimeMillis();
        long expectedNextPeriodStartTime = periodStartTime + intervalDuration;

        PeriodicWork periodicWork = new PeriodicWork.Builder(
                TestWorker.class, intervalDuration).build();

        getWorkSpec(periodicWork).setPeriodStartTime(periodStartTime);

        insertWork(periodicWork);

        new WorkerWrapper.Builder(mContext, mDatabase, periodicWork.getId())
                .withListener(mMockListener)
                .build()
                .run();

        WorkSpec updatedWorkSpec = mWorkSpecDao.getWorkSpec(periodicWork.getId());
        assertThat(updatedWorkSpec.getPeriodStartTime(), is(expectedNextPeriodStartTime));
    }

    @Test
    @SmallTest
    public void testRun_periodicWork_failure_updatesPeriodStartTime() {
        long intervalDuration = PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS;
        long periodStartTime = System.currentTimeMillis();
        long expectedNextPeriodStartTime = periodStartTime + intervalDuration;

        PeriodicWork periodicWork = new PeriodicWork.Builder(
                FailureWorker.class, intervalDuration).build();

        getWorkSpec(periodicWork).setPeriodStartTime(periodStartTime);

        insertWork(periodicWork);

        new WorkerWrapper.Builder(mContext, mDatabase, periodicWork.getId())
                .withListener(mMockListener)
                .build()
                .run();

        WorkSpec updatedWorkSpec = mWorkSpecDao.getWorkSpec(periodicWork.getId());
        assertThat(updatedWorkSpec.getPeriodStartTime(), is(expectedNextPeriodStartTime));
    }

    @Test
    @SmallTest
    public void testPeriodicWork_success() throws InterruptedException {
        PeriodicWork periodicWork = new PeriodicWork.Builder(
                TestWorker.class,
                PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS)
                .build();

        final String periodicWorkId = periodicWork.getId();
        insertWork(periodicWork);
        new WorkerWrapper.Builder(mContext, mDatabase, periodicWorkId)
                .withListener(mMockListener)
                .build()
                .run();

        WorkSpec periodicWorkSpecAfterFirstRun = mWorkSpecDao.getWorkSpec(periodicWorkId);
        verify(mMockListener).onExecuted(periodicWorkId, true, false);
        assertThat(periodicWorkSpecAfterFirstRun.getRunAttemptCount(), is(0));
        assertThat(periodicWorkSpecAfterFirstRun.getState(), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testPeriodicWork_fail() throws InterruptedException {
        PeriodicWork periodicWork = new PeriodicWork.Builder(
                FailureWorker.class,
                PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS)
                .build();

        final String periodicWorkId = periodicWork.getId();
        insertWork(periodicWork);
        new WorkerWrapper.Builder(mContext, mDatabase, periodicWorkId)
                .withListener(mMockListener)
                .build()
                .run();

        WorkSpec periodicWorkSpecAfterFirstRun = mWorkSpecDao.getWorkSpec(periodicWorkId);
        verify(mMockListener).onExecuted(periodicWorkId, false, false);
        assertThat(periodicWorkSpecAfterFirstRun.getRunAttemptCount(), is(0));
        assertThat(periodicWorkSpecAfterFirstRun.getState(), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testPeriodicWork_retry() throws InterruptedException {
        PeriodicWork periodicWork = new PeriodicWork.Builder(
                RetryWorker.class,
                PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS)
                .build();

        final String periodicWorkId = periodicWork.getId();
        insertWork(periodicWork);
        new WorkerWrapper.Builder(mContext, mDatabase, periodicWorkId)
                .withListener(mMockListener)
                .build()
                .run();

        WorkSpec periodicWorkSpecAfterFirstRun = mWorkSpecDao.getWorkSpec(periodicWorkId);
        verify(mMockListener).onExecuted(periodicWorkId, false, true);
        assertThat(periodicWorkSpecAfterFirstRun.getRunAttemptCount(), is(1));
        assertThat(periodicWorkSpecAfterFirstRun.getState(), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testScheduler() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).build();
        insertWork(work);
        Scheduler mockScheduler = mock(Scheduler.class);

        new WorkerWrapper.Builder(mContext, mDatabase, work.getId())
                .withSchedulers(Collections.singletonList(mockScheduler))
                .build()
                .run();

        verify(mockScheduler).schedule();
    }

    @Test
    @SmallTest
    public void testFromWorkSpec_hasAppContext() throws InterruptedException {
        Work work = new Work.Builder(TestWorker.class).build();
        Worker worker =
                WorkerWrapper.workerFromWorkSpec(mContext, getWorkSpec(work), Arguments.EMPTY);

        assertThat(worker, is(notNullValue()));
        assertThat(worker.getAppContext(), is(equalTo(mContext.getApplicationContext())));
    }

    @Test
    @SmallTest
    public void testFromWorkSpec_hasCorrectArguments() throws InterruptedException {
        String key = "KEY";
        String expectedValue = "VALUE";
        Arguments arguments = new Arguments.Builder().putString(key, expectedValue).build();

        Work work = new Work.Builder(TestWorker.class).withArguments(arguments).build();
        Worker worker = WorkerWrapper.workerFromWorkSpec(mContext, getWorkSpec(work), arguments);

        assertThat(worker, is(notNullValue()));
        assertThat(worker.getArguments().getString(key, null), is(expectedValue));

        work = new Work.Builder(TestWorker.class).build();
        worker = WorkerWrapper.workerFromWorkSpec(mContext, getWorkSpec(work), Arguments.EMPTY);

        assertThat(worker, is(notNullValue()));
        assertThat(worker.getArguments().size(), is(0));
    }
}
