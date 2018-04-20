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

import static androidx.work.ExistingWorkPolicy.APPEND;
import static androidx.work.ExistingWorkPolicy.KEEP;
import static androidx.work.ExistingWorkPolicy.REPLACE;
import static androidx.work.NetworkType.METERED;
import static androidx.work.NetworkType.NOT_REQUIRED;
import static androidx.work.State.BLOCKED;
import static androidx.work.State.CANCELLED;
import static androidx.work.State.ENQUEUED;
import static androidx.work.State.FAILED;
import static androidx.work.State.RUNNING;
import static androidx.work.State.SUCCEEDED;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.isOneOf;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.arch.core.executor.ArchTaskExecutor;
import android.arch.core.executor.TaskExecutor;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.ContentUriTriggers;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.TestLifecycleOwner;
import androidx.work.WorkContinuation;
import androidx.work.WorkRequest;
import androidx.work.WorkStatus;
import androidx.work.impl.logger.InternalLogger;
import androidx.work.impl.model.Dependency;
import androidx.work.impl.model.DependencyDao;
import androidx.work.impl.model.WorkName;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.model.WorkSpecDao;
import androidx.work.impl.model.WorkTag;
import androidx.work.impl.model.WorkTagDao;
import androidx.work.impl.utils.taskexecutor.InstantTaskExecutorRule;
import androidx.work.impl.workers.ConstraintTrackingWorker;
import androidx.work.worker.InfiniteTestWorker;
import androidx.work.worker.TestWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class WorkManagerImplTest {

    static {
        InternalLogger.LOG_LEVEL = Log.DEBUG;
    }

    private WorkDatabase mDatabase;
    private WorkManagerImpl mWorkManagerImpl;

    @Rule
    public InstantTaskExecutorRule mRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });

        Context context = InstrumentationRegistry.getTargetContext();
        Configuration configuration = new Configuration.Builder()
                .withExecutor(Executors.newSingleThreadExecutor())
                .build();
        mWorkManagerImpl = new WorkManagerImpl(context, configuration);
        WorkManagerImpl.setDelegate(mWorkManagerImpl);
        mDatabase = mWorkManagerImpl.getWorkDatabase();
    }

    @After
    public void tearDown() {
        WorkManagerImpl.setDelegate(null);
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWork() throws InterruptedException {
        final int workCount = 3;
        final OneTimeWorkRequest[] workArray = new OneTimeWorkRequest[workCount];
        for (int i = 0; i < workCount; ++i) {
            workArray[i] = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        }

        mWorkManagerImpl.beginWith(workArray[0]).then(workArray[1])
                .then(workArray[2])
                .blocking().enqueueBlocking();

        for (int i = 0; i < workCount; ++i) {
            String id = workArray[i].getId();
            assertThat(mDatabase.workSpecDao().getWorkSpec(id), is(notNullValue()));
            assertThat(
                    "index " + i + " does not have expected number of dependencies!",
                    mDatabase.dependencyDao().getPrerequisites(id).size() > 0,
                    is(i > 0));
        }
    }

    @Test
    @SmallTest
    public void testEnqueue_insertMultipleWork() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.blocking().enqueueBlocking(work1, work2, work3);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getId()), is(notNullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertMultipleWork_continuationBlocking() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginWith(work1, work2, work3)
                .blocking()
                .enqueueBlocking();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getId()), is(notNullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithDependencies() {
        OneTimeWorkRequest work1a = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1b = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3a = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3b = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginWith(work1a, work1b).then(work2)
                .then(work3a, work3b)
                .blocking()
                .enqueueBlocking();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1a.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work1b.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3a.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3b.getId()), is(notNullValue()));

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        assertThat(dependencyDao.getPrerequisites(work1a.getId()),
                is(emptyCollectionOf(String.class)));
        assertThat(dependencyDao.getPrerequisites(work1b.getId()),
                is(emptyCollectionOf(String.class)));

        List<String> prerequisites = dependencyDao.getPrerequisites(work2.getId());
        assertThat(prerequisites, containsInAnyOrder(work1a.getId(), work1b.getId()));

        prerequisites = dependencyDao.getPrerequisites(work3a.getId());
        assertThat(prerequisites, containsInAnyOrder(work2.getId()));

        prerequisites = dependencyDao.getPrerequisites(work3b.getId());
        assertThat(prerequisites, containsInAnyOrder(work2.getId()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithCompletedDependencies_isNotStatusBlocked() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.blocking().enqueueBlocking();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getId()), is(SUCCEEDED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        workContinuation.then(work2).blocking().enqueueBlocking();
        assertThat(workSpecDao.getState(work2.getId()), isOneOf(ENQUEUED, RUNNING));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithFailedDependencies_isStatusFailed() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(FAILED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.blocking().enqueueBlocking();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getId()), is(FAILED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        workContinuation.then(work2).blocking().enqueueBlocking();
        assertThat(workSpecDao.getState(work2.getId()), is(FAILED));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithCancelledDependencies_isStatusCancelled() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(CANCELLED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.blocking().enqueueBlocking();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        workContinuation.then(work2).blocking().enqueueBlocking();
        assertThat(workSpecDao.getState(work2.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23)
    public void testEnqueue_insertWorkConstraints() {
        Uri testUri1 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri testUri2 = MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(
                        new Constraints.Builder()
                                .setRequiresCharging(true)
                                .setRequiresDeviceIdle(true)
                                .setRequiredNetworkType(METERED)
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true)
                                .addContentUriTrigger(testUri1, true)
                                .addContentUriTrigger(testUri2, false)
                                .build())
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).blocking().enqueueBlocking();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        ContentUriTriggers expectedTriggers = new ContentUriTriggers();
        expectedTriggers.add(testUri1, true);
        expectedTriggers.add(testUri2, false);

        Constraints constraints = workSpec0.constraints;
        assertThat(constraints, is(notNullValue()));
        assertThat(constraints.requiresCharging(), is(true));
        assertThat(constraints.requiresDeviceIdle(), is(true));
        assertThat(constraints.requiresBatteryNotLow(), is(true));
        assertThat(constraints.requiresStorageNotLow(), is(true));
        assertThat(constraints.getRequiredNetworkType(), is(METERED));
        if (Build.VERSION.SDK_INT >= 24) {
            assertThat(constraints.getContentUriTriggers(), is(expectedTriggers));
        } else {
            assertThat(constraints.getContentUriTriggers(), is(new ContentUriTriggers()));
        }

        constraints = workSpec1.constraints;
        assertThat(constraints, is(notNullValue()));
        assertThat(constraints.requiresCharging(), is(false));
        assertThat(constraints.requiresDeviceIdle(), is(false));
        assertThat(constraints.requiresBatteryNotLow(), is(false));
        assertThat(constraints.requiresStorageNotLow(), is(false));
        assertThat(constraints.getRequiredNetworkType(), is(NOT_REQUIRED));
        assertThat(constraints.getContentUriTriggers().size(), is(0));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkInitialDelay() {
        final long expectedInitialDelay = 5000L;
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialDelay(expectedInitialDelay, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).blocking().enqueueBlocking();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.initialDelay, is(expectedInitialDelay));
        assertThat(workSpec1.initialDelay, is(0L));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkBackoffPolicy() {
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withBackoffCriteria(BackoffPolicy.LINEAR, 50000, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).blocking().enqueueBlocking();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.backoffPolicy, is(BackoffPolicy.LINEAR));
        assertThat(workSpec0.backoffDelayDuration, is(50000L));

        assertThat(workSpec1.backoffPolicy, is(BackoffPolicy.EXPONENTIAL));
        assertThat(workSpec1.backoffDelayDuration,
                is(WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkTags() {
        final String firstTag = "first_tag";
        final String secondTag = "second_tag";
        final String thirdTag = "third_tag";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .addTag(secondTag)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).then(work2).blocking().enqueueBlocking();

        WorkTagDao workTagDao = mDatabase.workTagDao();
        assertThat(workTagDao.getWorkSpecIdsWithTag(firstTag),
                containsInAnyOrder(work0.getId(), work1.getId()));
        assertThat(workTagDao.getWorkSpecIdsWithTag(secondTag), containsInAnyOrder(work0.getId()));
        assertThat(workTagDao.getWorkSpecIdsWithTag(thirdTag), emptyCollectionOf(String.class));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertPeriodicWork() {
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS)
                .build();

        mWorkManagerImpl.blocking().enqueueBlocking(periodicWork);

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getId());
        assertThat(workSpec.isPeriodic(), is(true));
        assertThat(workSpec.intervalDuration, is(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS));
        assertThat(workSpec.flexDuration, is(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueued_work_setsPeriodStartTime() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        assertThat(work.getWorkSpec().periodStartTime, is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.periodStartTime, is(greaterThanOrEqualTo(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testEnqueued_periodicWork_setsPeriodStartTime() {
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS)
                .build();
        assertThat(periodicWork.getWorkSpec().periodStartTime, is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.blocking().enqueueBlocking(periodicWork);

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getId());
        assertThat(workSpec.periodStartTime, is(greaterThanOrEqualTo(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testBeginWithName_setsUniqueName() {
        final String testName = "myname";

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginUniqueWork(testName, REPLACE)
                .then(work)
                .blocking()
                .enqueueBlocking();

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(work.getId(), isIn(workSpecIds));
    }

    @Test
    @SmallTest
    public void testBeginWithName_deletesOldWorkOnReplace() {
        final String testName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(testName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, REPLACE, replacementWork1)
                .then(replacementWork2)
                .blocking()
                .enqueueBlocking();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(
                workSpecIds,
                containsInAnyOrder(replacementWork1.getId(), replacementWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testBeginWithName_keepsExistingWorkOnKeep() {
        final String testName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(testName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, KEEP, replacementWork1)
                .then(replacementWork2)
                .blocking()
                .enqueueBlocking();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getId()), is(nullValue()));
    }

    @Test
    @SmallTest
    public void testBeginWithName_replacesExistingWorkOnKeepWhenExistingWorkIsFinished() {
        final String testName = "myname";

        OneTimeWorkRequest originalWork = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .build();
        insertNamedWorks(testName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, KEEP, replacementWork1)
                .then(replacementWork2)
                .blocking()
                .enqueueBlocking();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds,
                containsInAnyOrder(replacementWork1.getId(), replacementWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testBeginWithName_appendsExistingWorkOnAppend() {
        final String testName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(testName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, APPEND, appendWork1)
                .then(appendWork2)
                .blocking()
                .enqueueBlocking();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds,
                containsInAnyOrder(originalWork.getId(), appendWork1.getId(), appendWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getState(appendWork1.getId()), is(BLOCKED));
        assertThat(workSpecDao.getState(appendWork2.getId()), is(BLOCKED));

        assertThat(mDatabase.dependencyDao().getDependentWorkIds(originalWork.getId()),
                containsInAnyOrder(appendWork1.getId()));
    }

    @Test
    @SmallTest
    public void testBeginWithName_appendsExistingWorkToOnlyLeavesOnAppend() {
        final String testName = "myname";

        OneTimeWorkRequest originalWork1 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork2 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork3 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork4 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();

        insertNamedWorks(testName, originalWork1, originalWork2, originalWork3, originalWork4);
        insertDependency(originalWork4, originalWork2);
        insertDependency(originalWork3, originalWork2);
        insertDependency(originalWork2, originalWork1);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork1.getId(),
                        originalWork2.getId(),
                        originalWork3.getId(),
                        originalWork4.getId()));

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, APPEND, appendWork1)
                .then(appendWork2)
                .blocking()
                .enqueueBlocking();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork1.getId(),
                        originalWork2.getId(),
                        originalWork3.getId(),
                        originalWork4.getId(),
                        appendWork1.getId(),
                        appendWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork1.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork2.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork3.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork4.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getState(appendWork1.getId()), is(BLOCKED));
        assertThat(workSpecDao.getState(appendWork2.getId()), is(BLOCKED));

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        assertThat(dependencyDao.getPrerequisites(appendWork1.getId()),
                containsInAnyOrder(originalWork3.getId(), originalWork4.getId()));
        assertThat(dependencyDao.getPrerequisites(appendWork2.getId()),
                containsInAnyOrder(appendWork1.getId()));
    }

    @Test
    @SmallTest
    public void testBeginWithName_insertsExistingWorkWhenNothingToAppendTo() {
        final String testName = "myname";

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(testName, APPEND, appendWork1)
                .then(appendWork2)
                .blocking()
                .enqueueBlocking();

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(testName);
        assertThat(workSpecIds,
                containsInAnyOrder(appendWork1.getId(), appendWork2.getId()));
    }

    @Test
    @SmallTest
    public void testGetStatusByIdSync() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work);

        WorkStatus workStatus = mWorkManagerImpl.getStatusByIdBlocking(work.getId());
        assertThat(workStatus.getId(), is(work.getId()));
        assertThat(workStatus.getState(), is(SUCCEEDED));
    }

    @Test
    @SmallTest
    public void testGetStatusByIdSync_returnsNullIfNotInDatabase() {
        WorkStatus workStatus = mWorkManagerImpl.getStatusByIdBlocking("dummy");
        assertThat(workStatus, is(nullValue()));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testGetStatusesById() {
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);

        Observer<List<WorkStatus>> mockObserver = mock(Observer.class);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<List<WorkStatus>> liveData =
                mWorkManagerImpl.getStatusesById(Arrays.asList(work0.getId(), work1.getId()));
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.<String>emptyList());
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.<String>emptyList());
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        workSpecDao.setState(RUNNING, work0.getId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.<String>emptyList());
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        clearInvocations(mockObserver);
        workSpecDao.setState(RUNNING, work1.getId());

        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus1 = new WorkStatus(
                work1.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.<String>emptyList());
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void testGetStatusesByTagSync() {
        final String firstTag = "first_tag";
        final String secondTag = "second_tag";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .addTag(secondTag)
                .withInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .withInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(secondTag)
                .withInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Arrays.asList(firstTag, secondTag));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(firstTag));
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                SUCCEEDED,
                Data.EMPTY,
                Collections.singletonList(secondTag));

        List<WorkStatus> workStatuses = mWorkManagerImpl.getStatusesByTagBlocking(firstTag);
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus1));

        workStatuses = mWorkManagerImpl.getStatusesByTagBlocking(secondTag);
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus2));

        workStatuses = mWorkManagerImpl.getStatusesByTagBlocking("dummy");
        assertThat(workStatuses.size(), is(0));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testGetStatusesByTag() {
        final String firstTag = "first_tag";
        final String secondTag = "second_tag";
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .addTag(secondTag)
                .withInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .withInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(secondTag)
                .withInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        Observer<List<WorkStatus>> mockObserver = mock(Observer.class);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<List<WorkStatus>> liveData = mWorkManagerImpl.getStatusesByTag(firstTag);
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Arrays.asList(firstTag, secondTag));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(firstTag));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        workSpecDao.setState(ENQUEUED, work0.getId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus0 = new WorkStatus(
                work0.getId(),
                ENQUEUED,
                Data.EMPTY,
                Arrays.asList(firstTag, secondTag));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void getStatusByNameSync() {
        final String testName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(BLOCKED)
                .build();
        insertNamedWorks(testName, work0, work1, work2);
        insertDependency(work1, work0);
        insertDependency(work2, work1);

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.<String>emptyList());
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.<String>emptyList());
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.<String>emptyList());

        List<WorkStatus> workStatuses = mWorkManagerImpl.getStatusesForUniqueWorkBlocking(testName);
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        workStatuses = mWorkManagerImpl.getStatusesForUniqueWorkBlocking("dummy");
        assertThat(workStatuses.size(), is(0));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testGetStatusesByName() {
        final String testName = "myname";
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(BLOCKED)
                .build();
        insertNamedWorks(testName, work0, work1, work2);
        insertDependency(work1, work0);
        insertDependency(work2, work1);

        Observer<List<WorkStatus>> mockObserver = mock(Observer.class);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<List<WorkStatus>> liveData = mWorkManagerImpl.getStatusesForUniqueWork(testName);
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(3));

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.<String>emptyList());
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.<String>emptyList());
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.<String>emptyList());
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        workSpecDao.setState(ENQUEUED, work0.getId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(3));

        workStatus0 = new WorkStatus(
                work0.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.<String>emptyList());
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void testCancelWorkById() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);

        mWorkManagerImpl.blocking().cancelWorkByIdBlocking(work0.getId());
        assertThat(workSpecDao.getState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelWorkById_cancelsDependentWork() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(BLOCKED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertDependency(work1, work0);

        mWorkManagerImpl.blocking().cancelWorkByIdBlocking(work0.getId());

        assertThat(workSpecDao.getState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelWorkById_cancelsUnfinishedWorkOnly() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(ENQUEUED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertDependency(work1, work0);

        mWorkManagerImpl.blocking().cancelWorkByIdBlocking(work0.getId());

        assertThat(workSpecDao.getState(work0.getId()), is(SUCCEEDED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkByTag() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        final String tagToClear = "tag_to_clear";
        final String tagNotToClear = "tag_not_to_clear";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(tagToClear)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(tagToClear)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(tagNotToClear)
                .build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(tagNotToClear)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);
        insertWorkSpecAndTags(work3);

        mWorkManagerImpl.blocking().cancelAllWorkByTagBlocking(tagToClear);

        assertThat(workSpecDao.getState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work2.getId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getState(work3.getId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkByTag_cancelsDependentWork() {
        String tag = "tag";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(tag)
                .build();

        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work4 = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);
        insertWorkSpecAndTags(work3);
        insertWorkSpecAndTags(work4);

        // Dependency graph:
        //                             0
        //                             |
        //                       |------------|
        //            3          1            4
        //            |          |
        //            ------------
        //                 |
        //                 2

        insertDependency(work2, work1);
        insertDependency(work2, work3);
        insertDependency(work1, work0);
        insertDependency(work4, work0);

        mWorkManagerImpl.blocking().cancelAllWorkByTagBlocking(tag);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work2.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work3.getId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getState(work4.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelWorkByName() {
        final String testName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(testName, work0, work1);

        mWorkManagerImpl.blocking().cancelUniqueWorkBlocking(testName);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelWorkByName_ignoresFinishedWork() {
        final String testName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .withInitialState(SUCCEEDED)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(testName, work0, work1);

        mWorkManagerImpl.blocking().cancelUniqueWorkBlocking(testName);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getId()), is(SUCCEEDED));
        assertThat(workSpecDao.getState(work1.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testSynchronousCancelAndGetStatus() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work.getId()), is(ENQUEUED));

        mWorkManagerImpl.blocking().cancelWorkByIdBlocking(work.getId());
        assertThat(mWorkManagerImpl.getStatusByIdBlocking(work.getId()).getState(), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_resetsRunningWorkStatuses() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(RUNNING)
                .build();
        workSpecDao.insertWorkSpec(work.getWorkSpec());

        assertThat(workSpecDao.getState(work.getId()), is(RUNNING));

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        assertThat(workSpecDao.getState(work.getId()), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_deletesOldFinishedWork() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withPeriodStartTime(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
                .build();

        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_doesNotDeleteOldFinishedWorkWithActiveDependents() {
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withInitialState(ENQUEUED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();

        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        // Dependency graph: 0 -> 1 -> 2
        insertDependency(work1, work0);
        insertDependency(work2, work1);

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work0.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    @SdkSuppress(maxSdkVersion = 22)
    public void testEnqueueApi22OrLower_withBatteryNotLowConstraint_expectsOriginalWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(maxSdkVersion = 22)
    public void testEnqueueApi22OrLower_withStorageNotLowConstraint_expectsOriginalWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testEnqueueApi23To25_withBatteryNotLowConstraint_expectsConstraintTrackingWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(ConstraintTrackingWorker.class.getName()));
        assertThat(workSpec.input.getString(
                ConstraintTrackingWorker.ARGUMENT_CLASS_NAME, null),
                is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testEnqueueApi23To25_withStorageNotLowConstraint_expectsConstraintTrackingWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(ConstraintTrackingWorker.class.getName()));
        assertThat(workSpec.input.getString(
                ConstraintTrackingWorker.ARGUMENT_CLASS_NAME, null),
                is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 26)
    public void testEnqueueApi26OrHigher_withBatteryNotLowConstraint_expectsOriginalWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 26)
    public void testEnqueueApi26OrHigher_withStorageNotLowConstraint_expectsOriginalWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .withConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).blocking().enqueueBlocking();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    private void insertWorkSpecAndTags(OneTimeWorkRequest work) {
        mDatabase.workSpecDao().insertWorkSpec(work.getWorkSpec());
        for (String tag : work.getTags()) {
            mDatabase.workTagDao().insert(new WorkTag(tag, work.getId()));
        }
    }

    private void insertNamedWorks(String name, OneTimeWorkRequest... works) {
        for (OneTimeWorkRequest work : works) {
            insertWorkSpecAndTags(work);
            mDatabase.workNameDao().insert(new WorkName(name, work.getId()));
        }
    }

    private void insertDependency(OneTimeWorkRequest work, OneTimeWorkRequest prerequisiteWork) {
        mDatabase.dependencyDao().insertDependency(
                new Dependency(work.getId(), prerequisiteWork.getId()));
    }
}
