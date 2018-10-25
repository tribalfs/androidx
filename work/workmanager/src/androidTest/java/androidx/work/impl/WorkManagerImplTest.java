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
import static androidx.work.impl.model.WorkSpec.SCHEDULE_NOT_REQUESTED_YET;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.isOneOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.arch.core.executor.ArchTaskExecutor;
import android.arch.core.executor.TaskExecutor;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.work.BackoffPolicy;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.ContentUriTriggers;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Logger;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.TestLifecycleOwner;
import androidx.work.WorkContinuation;
import androidx.work.WorkRequest;
import androidx.work.WorkStatus;
import androidx.work.impl.background.systemalarm.RescheduleReceiver;
import androidx.work.impl.model.Dependency;
import androidx.work.impl.model.DependencyDao;
import androidx.work.impl.model.WorkName;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.model.WorkSpecDao;
import androidx.work.impl.model.WorkTag;
import androidx.work.impl.model.WorkTagDao;
import androidx.work.impl.utils.CancelWorkRunnable;
import androidx.work.impl.utils.Preferences;
import androidx.work.impl.utils.RepeatRule;
import androidx.work.impl.utils.taskexecutor.InstantWorkTaskExecutor;
import androidx.work.impl.workers.ConstraintTrackingWorker;
import androidx.work.worker.InfiniteTestWorker;
import androidx.work.worker.StopAwareWorker;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class WorkManagerImplTest {

    private static final String TAG = "WorkManagerImplTest";

    private static final long SLEEP_DURATION_SMALL_MILLIS = 500L;

    private Context mContext;
    private Configuration mConfiguration;
    private WorkDatabase mDatabase;
    private WorkManagerImpl mWorkManagerImpl;

    @Rule
    public RepeatRule mRepeatRule = new RepeatRule();

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
        mContext = InstrumentationRegistry.getTargetContext();
        mConfiguration = new Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
        mWorkManagerImpl =
                new WorkManagerImpl(mContext, mConfiguration, new InstantWorkTaskExecutor());
        WorkManagerImpl.setDelegate(mWorkManagerImpl);
        mDatabase = mWorkManagerImpl.getWorkDatabase();
        Logger.setMinimumLoggingLevel(Log.DEBUG);
    }

    @After
    public void tearDown() {
        WorkManagerImpl.setDelegate(null);
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWork() throws ExecutionException, InterruptedException {
        final int workCount = 3;
        final OneTimeWorkRequest[] workArray = new OneTimeWorkRequest[workCount];
        for (int i = 0; i < workCount; ++i) {
            workArray[i] = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        }

        mWorkManagerImpl.beginWith(workArray[0]).then(workArray[1])
                .then(workArray[2])
                .enqueue().get();

        for (int i = 0; i < workCount; ++i) {
            String id = workArray[i].getStringId();
            assertThat(mDatabase.workSpecDao().getWorkSpec(id), is(notNullValue()));
            assertThat(
                    "index " + i + " does not have expected number of dependencies!",
                    mDatabase.dependencyDao().getPrerequisites(id).size() > 0,
                    is(i > 0));
        }
    }

    @Test
    @SmallTest
    public void testEnqueue_AddsImplicitTags() throws ExecutionException, InterruptedException {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.enqueueInternal(Collections.singletonList(work)).get();

        WorkTagDao workTagDao = mDatabase.workTagDao();
        List<String> tags = workTagDao.getTagsForWorkSpecId(work.getStringId());
        assertThat(tags, is(notNullValue()));
        assertThat(tags, contains(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertMultipleWork() throws ExecutionException, InterruptedException {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.enqueueInternal(Arrays.asList(work1, work2, work3)).get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getStringId()), is(notNullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertMultipleWork_continuationBlocking()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3 = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginWith(work1, work2, work3)
                .enqueue()
                .get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getStringId()), is(notNullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithDependencies()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work1a = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1b = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3a = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work3b = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginWith(work1a, work1b).then(work2)
                .then(work3a, work3b)
                .enqueue()
                .get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1a.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work1b.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3a.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3b.getStringId()), is(notNullValue()));

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        assertThat(dependencyDao.getPrerequisites(work1a.getStringId()),
                is(emptyCollectionOf(String.class)));
        assertThat(dependencyDao.getPrerequisites(work1b.getStringId()),
                is(emptyCollectionOf(String.class)));

        List<String> prerequisites = dependencyDao.getPrerequisites(work2.getStringId());
        assertThat(prerequisites, containsInAnyOrder(work1a.getStringId(), work1b.getStringId()));

        prerequisites = dependencyDao.getPrerequisites(work3a.getStringId());
        assertThat(prerequisites, containsInAnyOrder(work2.getStringId()));

        prerequisites = dependencyDao.getPrerequisites(work3b.getStringId());
        assertThat(prerequisites, containsInAnyOrder(work2.getStringId()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithCompletedDependencies_isNotStatusBlocked()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.enqueue().get();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getStringId()), is(SUCCEEDED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        workContinuation.then(work2).enqueue().get();
        assertThat(workSpecDao.getState(work2.getStringId()), isOneOf(ENQUEUED, RUNNING));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithFailedDependencies_isStatusFailed()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(FAILED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.enqueue().get();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getStringId()), is(FAILED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        workContinuation.then(work2).enqueue().get();
        assertThat(workSpecDao.getState(work2.getStringId()), is(FAILED));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithCancelledDependencies_isStatusCancelled()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(CANCELLED)
                .build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.enqueue().get();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));

        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        workContinuation.then(work2).enqueue().get();
        assertThat(workSpecDao.getState(work2.getStringId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23)
    public void testEnqueue_insertWorkConstraints()
            throws ExecutionException, InterruptedException {

        Uri testUri1 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri testUri2 = MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(
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
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue().get();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getStringId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getStringId());

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
    public void testEnqueue_insertWorkInitialDelay()
            throws ExecutionException, InterruptedException {

        final long expectedInitialDelay = 5000L;
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialDelay(expectedInitialDelay, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue().get();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getStringId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getStringId());

        assertThat(workSpec0.initialDelay, is(expectedInitialDelay));
        assertThat(workSpec1.initialDelay, is(0L));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkBackoffPolicy()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 50000, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue().get();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getStringId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getStringId());

        assertThat(workSpec0.backoffPolicy, is(BackoffPolicy.LINEAR));
        assertThat(workSpec0.backoffDelayDuration, is(50000L));

        assertThat(workSpec1.backoffPolicy, is(BackoffPolicy.EXPONENTIAL));
        assertThat(workSpec1.backoffDelayDuration,
                is(WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkTags() throws ExecutionException, InterruptedException {
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
        mWorkManagerImpl.beginWith(work0).then(work1).then(work2).enqueue().get();

        WorkTagDao workTagDao = mDatabase.workTagDao();
        assertThat(workTagDao.getWorkSpecIdsWithTag(firstTag),
                containsInAnyOrder(work0.getStringId(), work1.getStringId()));
        assertThat(workTagDao.getWorkSpecIdsWithTag(secondTag),
                containsInAnyOrder(work0.getStringId()));
        assertThat(workTagDao.getWorkSpecIdsWithTag(thirdTag), emptyCollectionOf(String.class));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertPeriodicWork() throws ExecutionException, InterruptedException {
        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS)
                .build();

        mWorkManagerImpl.enqueueInternal(Collections.singletonList(periodicWork)).get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getStringId());
        assertThat(workSpec.isPeriodic(), is(true));
        assertThat(workSpec.intervalDuration, is(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS));
        assertThat(workSpec.flexDuration, is(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueued_work_setsPeriodStartTime()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        assertThat(work.getWorkSpec().periodStartTime, is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.beginWith(work).enqueue().get();
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.periodStartTime, is(greaterThanOrEqualTo(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testEnqueued_periodicWork_setsPeriodStartTime()
            throws ExecutionException, InterruptedException {

        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS)
                .build();
        assertThat(periodicWork.getWorkSpec().periodStartTime, is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.enqueueInternal(Collections.singletonList(periodicWork)).get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getStringId());
        assertThat(workSpec.periodStartTime, is(greaterThanOrEqualTo(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_setsUniqueName()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest next = new OneTimeWorkRequest.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginUniqueWork(uniqueName, REPLACE, work)
                .then(next)
                .enqueue().get();

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(work.getStringId(), isIn(workSpecIds));
    }

    @Test
    @SmallTest
    public void testEnqueueUniquePeriodicWork_setsUniqueName()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        PeriodicWorkRequest periodicWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                15L,
                TimeUnit.MINUTES)
                .build();
        mWorkManagerImpl.enqueueUniquePeriodicWorkInternal(
                uniqueName,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodicWork).get();

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(periodicWork.getStringId(), isIn(workSpecIds));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_deletesOldWorkOnReplace()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, REPLACE, replacementWork1)
                .then(replacementWork2)
                .enqueue().get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(
                workSpecIds,
                containsInAnyOrder(replacementWork1.getStringId(), replacementWork2.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getStringId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testEnqueueUniquePeriodicWork_deletesOldWorkOnReplace()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        PeriodicWorkRequest originalWork = new PeriodicWorkRequest.Builder(
                InfiniteTestWorker.class,
                15L,
                TimeUnit.MINUTES)
                .build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        PeriodicWorkRequest replacementWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                30L,
                TimeUnit.MINUTES)
                .build();
        mWorkManagerImpl.enqueueUniquePeriodicWorkInternal(
                uniqueName,
                ExistingPeriodicWorkPolicy.REPLACE,
                replacementWork).get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, contains(replacementWork.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork.getStringId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_keepsExistingWorkOnKeep()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, KEEP, replacementWork1)
                .then(replacementWork2)
                .enqueue()
                .get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, contains(originalWork.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getStringId()), is(nullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueueUniquePeriodicWork_keepsExistingWorkOnKeep()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        PeriodicWorkRequest originalWork = new PeriodicWorkRequest.Builder(
                InfiniteTestWorker.class,
                15L,
                TimeUnit.MINUTES)
                .build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        PeriodicWorkRequest replacementWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                30L,
                TimeUnit.MINUTES)
                .build();
        mWorkManagerImpl.enqueueUniquePeriodicWorkInternal(
                uniqueName,
                ExistingPeriodicWorkPolicy.KEEP,
                replacementWork).get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, contains(originalWork.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork.getStringId()), is(nullValue()));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_replacesExistingWorkOnKeepWhenExistingWorkIsDone()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        OneTimeWorkRequest replacementWork1 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest replacementWork2 =
                new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, KEEP, replacementWork1)
                .then(replacementWork2)
                .enqueue().get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(replacementWork1.getStringId(), replacementWork2.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getStringId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testEnqueueUniquePeriodicWork_replacesExistingWorkOnKeepWhenExistingWorkIsDone()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        PeriodicWorkRequest originalWork = new PeriodicWorkRequest.Builder(
                InfiniteTestWorker.class,
                15L,
                TimeUnit.MINUTES)
                .setInitialState(SUCCEEDED)
                .build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        PeriodicWorkRequest replacementWork = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                30L,
                TimeUnit.MINUTES)
                .build();
        mWorkManagerImpl.enqueueUniquePeriodicWorkInternal(
                uniqueName,
                ExistingPeriodicWorkPolicy.KEEP,
                replacementWork).get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, contains(replacementWork.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork.getStringId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_appendsExistingWorkOnAppend()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue().get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork.getStringId(),
                        appendWork1.getStringId(),
                        appendWork2.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getState(appendWork1.getStringId()), is(BLOCKED));
        assertThat(workSpecDao.getState(appendWork2.getStringId()), is(BLOCKED));

        assertThat(mDatabase.dependencyDao().getDependentWorkIds(originalWork.getStringId()),
                containsInAnyOrder(appendWork1.getStringId()));
    }


    @Test
    @SmallTest
    public void testEnqueueUniqueWork_appendsExistingWorkOnAppend()
            throws ExecutionException, InterruptedException {
        // Not duplicating other enqueueUniqueWork with different work policies as they
        // call the same underlying continuation which have tests. This test exists to ensure
        // we delegate to the underlying continuation correctly.

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, originalWork);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getStringId()));

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl.enqueueUniqueWorkInternal(
                uniqueName,
                APPEND,
                Arrays.asList(appendWork1, appendWork2)).get();
        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork.getStringId(),
                        appendWork1.getStringId(),
                        appendWork2.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getState(appendWork1.getStringId()), is(BLOCKED));
        assertThat(workSpecDao.getState(appendWork2.getStringId()), is(BLOCKED));

        assertThat(mDatabase.dependencyDao().getDependentWorkIds(originalWork.getStringId()),
                containsInAnyOrder(appendWork1.getStringId(), appendWork2.getStringId()));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_appendsExistingWorkToOnlyLeavesOnAppend()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest originalWork1 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork2 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork3 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest originalWork4 =
                new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();

        insertNamedWorks(uniqueName, originalWork1, originalWork2, originalWork3, originalWork4);
        insertDependency(originalWork4, originalWork2);
        insertDependency(originalWork3, originalWork2);
        insertDependency(originalWork2, originalWork1);

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork1.getStringId(),
                        originalWork2.getStringId(),
                        originalWork3.getStringId(),
                        originalWork4.getStringId()));

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue().get();

        workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(
                        originalWork1.getStringId(),
                        originalWork2.getStringId(),
                        originalWork3.getStringId(),
                        originalWork4.getStringId(),
                        appendWork1.getStringId(),
                        appendWork2.getStringId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork1.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork2.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork3.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(originalWork4.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getState(appendWork1.getStringId()), is(BLOCKED));
        assertThat(workSpecDao.getState(appendWork2.getStringId()), is(BLOCKED));

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        assertThat(dependencyDao.getPrerequisites(appendWork1.getStringId()),
                containsInAnyOrder(originalWork3.getStringId(), originalWork4.getStringId()));
        assertThat(dependencyDao.getPrerequisites(appendWork2.getStringId()),
                containsInAnyOrder(appendWork1.getStringId()));
    }

    @Test
    @SmallTest
    public void testBeginUniqueWork_insertsExistingWorkWhenNothingToAppendTo()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest appendWork1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest appendWork2 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginUniqueWork(uniqueName, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue().get();

        List<String> workSpecIds = mDatabase.workNameDao().getWorkSpecIdsWithName(uniqueName);
        assertThat(workSpecIds,
                containsInAnyOrder(appendWork1.getStringId(), appendWork2.getStringId()));
    }

    @Test
    @SmallTest
    public void testGetStatusByIdSync() throws ExecutionException, InterruptedException {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work);

        WorkStatus workStatus = mWorkManagerImpl.getStatusById(work.getId()).get();
        assertThat(workStatus.getId().toString(), is(work.getStringId()));
        assertThat(workStatus.getState(), is(SUCCEEDED));
    }

    @Test
    @SmallTest
    public void testGetStatusByIdSync_returnsNullIfNotInDatabase()
            throws ExecutionException, InterruptedException {

        WorkStatus workStatus = mWorkManagerImpl.getStatusById(UUID.randomUUID()).get();
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
        LiveData<List<WorkStatus>> liveData = mWorkManagerImpl.getStatusesById(
                Arrays.asList(work0.getStringId(), work1.getStringId()));
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.singletonList(TestWorker.class.getName()));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.singletonList(TestWorker.class.getName()));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        workSpecDao.setState(RUNNING, work0.getStringId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.singletonList(TestWorker.class.getName()));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        clearInvocations(mockObserver);
        workSpecDao.setState(RUNNING, work1.getStringId());

        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus1 = new WorkStatus(
                work1.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.singletonList(TestWorker.class.getName()));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void testGetStatusesByTagSync() throws ExecutionException, InterruptedException {
        final String firstTag = "first_tag";
        final String secondTag = "second_tag";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .addTag(secondTag)
                .setInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(firstTag)
                .setInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .addTag(secondTag)
                .setInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Arrays.asList(TestWorker.class.getName(), firstTag, secondTag));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Arrays.asList(TestWorker.class.getName(), firstTag));
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                SUCCEEDED,
                Data.EMPTY,
                Arrays.asList(TestWorker.class.getName(), secondTag));

        List<WorkStatus> workStatuses = mWorkManagerImpl.getStatusesByTag(firstTag).get();
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus1));

        workStatuses = mWorkManagerImpl.getStatusesByTag(secondTag).get();
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus2));

        workStatuses = mWorkManagerImpl.getStatusesByTag("dummy").get();
        assertThat(workStatuses.size(), is(0));
    }

    @Test
    @SmallTest
    public void getStatusByNameSync() throws ExecutionException, InterruptedException {
        final String uniqueName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(BLOCKED)
                .build();
        insertNamedWorks(uniqueName, work0, work1, work2);
        insertDependency(work1, work0);
        insertDependency(work2, work1);

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));

        List<WorkStatus> workStatuses = mWorkManagerImpl.getStatusesForUniqueWork(uniqueName).get();
        assertThat(workStatuses, containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        workStatuses = mWorkManagerImpl.getStatusesForUniqueWork("dummy").get();
        assertThat(workStatuses.size(), is(0));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testGetStatusesByName() {
        final String uniqueName = "myname";
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(RUNNING)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(BLOCKED)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(BLOCKED)
                .build();
        insertNamedWorks(uniqueName, work0, work1, work2);
        insertDependency(work1, work0);
        insertDependency(work2, work1);

        Observer<List<WorkStatus>> mockObserver = mock(Observer.class);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<List<WorkStatus>> liveData =
                mWorkManagerImpl.getStatusesForUniqueWorkLiveData(uniqueName);
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(3));

        WorkStatus workStatus0 = new WorkStatus(
                work0.getId(),
                RUNNING,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        WorkStatus workStatus1 = new WorkStatus(
                work1.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        WorkStatus workStatus2 = new WorkStatus(
                work2.getId(),
                BLOCKED,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        workSpecDao.setState(ENQUEUED, work0.getStringId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(3));

        workStatus0 = new WorkStatus(
                work0.getId(),
                ENQUEUED,
                Data.EMPTY,
                Collections.singletonList(InfiniteTestWorker.class.getName()));
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1, workStatus2));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void testCancelWorkById() throws ExecutionException, InterruptedException {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);

        mWorkManagerImpl.cancelWorkByIdInternal(work0.getId()).get();
        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelWorkById_cancelsDependentWork()
            throws ExecutionException, InterruptedException {

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(BLOCKED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertDependency(work1, work0);

        mWorkManagerImpl.cancelWorkByIdInternal(work0.getId()).get();

        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelWorkById_cancelsUnfinishedWorkOnly()
            throws ExecutionException, InterruptedException {

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(ENQUEUED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertDependency(work1, work0);

        mWorkManagerImpl.cancelWorkByIdInternal(work0.getId()).get();

        assertThat(workSpecDao.getState(work0.getStringId()), is(SUCCEEDED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkByTag() throws ExecutionException, InterruptedException {
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

        mWorkManagerImpl.cancelAllWorkByTagInternal(tagToClear).get();

        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work2.getStringId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getState(work3.getStringId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkByTag_cancelsDependentWork()
            throws ExecutionException, InterruptedException {

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

        mWorkManagerImpl.cancelAllWorkByTagInternal(tag).get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work2.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work3.getStringId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getState(work4.getStringId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelWorkByName() throws ExecutionException, InterruptedException {
        final String uniqueName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, work0, work1);

        mWorkManagerImpl.cancelUniqueWorkInternal(uniqueName).get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
    }

    @Test
    @LargeTest
    public void testCancelWorkByName_ignoresFinishedWork()
            throws ExecutionException, InterruptedException {

        final String uniqueName = "myname";

        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(InfiniteTestWorker.class).build();
        insertNamedWorks(uniqueName, work0, work1);

        mWorkManagerImpl.cancelUniqueWorkInternal(uniqueName).get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getStringId()), is(SUCCEEDED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testCancelAllWork() throws ExecutionException, InterruptedException {
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work0.getStringId()), is(ENQUEUED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(ENQUEUED));
        assertThat(workSpecDao.getState(work2.getStringId()), is(SUCCEEDED));

        mWorkManagerImpl.cancelAllWorkInternal().get();
        assertThat(workSpecDao.getState(work0.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work1.getStringId()), is(CANCELLED));
        assertThat(workSpecDao.getState(work2.getStringId()), is(SUCCEEDED));
    }

    @Test
    @MediumTest
    public void testCancelAllWork_updatesLastCancelAllTime() {
        Preferences preferences = new Preferences(InstrumentationRegistry.getTargetContext());
        preferences.setLastCancelAllTimeMillis(0L);

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work);

        CancelWorkRunnable.forAll(mWorkManagerImpl).run();
        assertThat(preferences.getLastCancelAllTimeMillis(), is(greaterThan(0L)));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testCancelAllWork_updatesLastCancelAllTimeLiveData() throws InterruptedException {
        Preferences preferences = new Preferences(InstrumentationRegistry.getTargetContext());
        preferences.setLastCancelAllTimeMillis(0L);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<Long> cancelAllTimeLiveData =
                mWorkManagerImpl.getLastCancelAllTimeMillisLiveData();
        Observer<Long> mockObserver = mock(Observer.class);
        cancelAllTimeLiveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(0L));

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work);

        clearInvocations(mockObserver);
        CancelWorkRunnable.forAll(mWorkManagerImpl).run();

        Thread.sleep(1000L);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(greaterThan(0L)));

        cancelAllTimeLiveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void pruneFinishedWork() throws InterruptedException, ExecutionException {
        OneTimeWorkRequest enqueuedWork = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        OneTimeWorkRequest finishedWork =
                new OneTimeWorkRequest.Builder(TestWorker.class).setInitialState(SUCCEEDED).build();
        OneTimeWorkRequest finishedWorkWithUnfinishedDependent =
                new OneTimeWorkRequest.Builder(TestWorker.class).setInitialState(SUCCEEDED).build();
        OneTimeWorkRequest finishedWorkWithLongKeepForAtLeast =
                new OneTimeWorkRequest.Builder(TestWorker.class)
                        .setInitialState(SUCCEEDED)
                        .keepResultsForAtLeast(999, TimeUnit.DAYS)
                        .build();

        insertWorkSpecAndTags(enqueuedWork);
        insertWorkSpecAndTags(finishedWork);
        insertWorkSpecAndTags(finishedWorkWithUnfinishedDependent);
        insertWorkSpecAndTags(finishedWorkWithLongKeepForAtLeast);

        insertDependency(enqueuedWork, finishedWorkWithUnfinishedDependent);

        mWorkManagerImpl.pruneWorkInternal().get();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(enqueuedWork.getStringId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedWork.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedWorkWithUnfinishedDependent.getStringId()),
                is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedWorkWithLongKeepForAtLeast.getStringId()),
                is(nullValue()));
    }

    @Test
    @SmallTest
    public void testSynchronousCancelAndGetStatus()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work.getStringId()), is(ENQUEUED));

        mWorkManagerImpl.cancelWorkByIdInternal(work.getId()).get();
        assertThat(mWorkManagerImpl.getStatusById(work.getId()).get().getState(), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_resetsRunningWorkStatuses() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(RUNNING)
                .build();
        workSpecDao.insertWorkSpec(work.getWorkSpec());

        assertThat(workSpecDao.getState(work.getStringId()), is(RUNNING));

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        assertThat(workSpecDao.getState(work.getStringId()), is(ENQUEUED));
        assertThat(work.getWorkSpec().scheduleRequestedAt, is(SCHEDULE_NOT_REQUESTED_YET));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_deletesOldFinishedWork() {
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .setPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setPeriodStartTime(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
                .build();

        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getStringId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_doesNotDeleteOldFinishedWorkWithActiveDependents() {
        OneTimeWorkRequest work0 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .setPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work1 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(SUCCEEDED)
                .setPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
                .build();
        OneTimeWorkRequest work2 = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setInitialState(ENQUEUED)
                .setPeriodStartTime(WorkDatabase.getPruneDate() - 1L, TimeUnit.MILLISECONDS)
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
        assertThat(workSpecDao.getWorkSpec(work0.getStringId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work1.getStringId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(work2.getStringId()), is(not(nullValue())));
    }

    @Test
    @LargeTest
    public void testEnableDisableRescheduleReceiver()
            throws ExecutionException, InterruptedException {

        final PackageManager packageManager = mock(PackageManager.class);
        mContext = new ContextWrapper(InstrumentationRegistry.getTargetContext()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public PackageManager getPackageManager() {
                return packageManager;
            }
        };
        mWorkManagerImpl =
                new WorkManagerImpl(mContext, mConfiguration, new InstantWorkTaskExecutor());
        WorkManagerImpl.setDelegate(mWorkManagerImpl);
        // Call getSchedulers() so WM calls createBestAvailableBackgroundScheduler()
        // which in turn initializes the right System(*)Service.
        mWorkManagerImpl.getSchedulers();
        mDatabase = mWorkManagerImpl.getWorkDatabase();
        // Initialization of WM enables SystemJobService which needs to be discounted.
        reset(packageManager);
        OneTimeWorkRequest stopAwareWorkRequest =
                new OneTimeWorkRequest.Builder(StopAwareWorker.class)
                        .build();

        mWorkManagerImpl.enqueueInternal(Collections.singletonList(stopAwareWorkRequest)).get();
        ComponentName componentName = new ComponentName(mContext, RescheduleReceiver.class);
        verify(packageManager, times(1))
                .setComponentEnabledSetting(eq(componentName),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                        eq(PackageManager.DONT_KILL_APP));

        reset(packageManager);
        mWorkManagerImpl.cancelWorkByIdInternal(stopAwareWorkRequest.getId()).get();
        // Sleeping for a little bit, to give the listeners a chance to catch up.
        Thread.sleep(SLEEP_DURATION_SMALL_MILLIS);
        // There is a small chance that we will call this method twice. Once when the Worker was
        // cancelled, and once after the StopAwareWorker realizes that it has been stopped
        // and returns a Result.SUCCESS
        verify(packageManager, atLeastOnce())
                .setComponentEnabledSetting(eq(componentName),
                        eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                        eq(PackageManager.DONT_KILL_APP));

    }

    @Test
    @SmallTest
    @SdkSuppress(maxSdkVersion = 22)
    public void testEnqueueApi22OrLower_withBatteryNotLowConstraint_expectsOriginalWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(maxSdkVersion = 22)
    public void testEnqueueApi22OrLower_withStorageNotLowConstraint_expectsOriginalWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testEnqueueApi23To25_withBatteryNotLowConstraint_expectsConstraintTrackingWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(ConstraintTrackingWorker.class.getName()));
        assertThat(workSpec.input.getString(
                ConstraintTrackingWorker.ARGUMENT_CLASS_NAME),
                is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testEnqueueApi23To25_withStorageNotLowConstraint_expectsConstraintTrackingWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(ConstraintTrackingWorker.class.getName()));
        assertThat(workSpec.input.getString(
                ConstraintTrackingWorker.ARGUMENT_CLASS_NAME),
                is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 26)
    public void testEnqueueApi26OrHigher_withBatteryNotLowConstraint_expectsOriginalWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    @Test
    @SmallTest
    @SdkSuppress(minSdkVersion = 26)
    public void testEnqueueApi26OrHigher_withStorageNotLowConstraint_expectsOriginalWorker()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(TestWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();
        mWorkManagerImpl.beginWith(work).enqueue().get();

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getStringId());
        assertThat(workSpec.workerClassName, is(TestWorker.class.getName()));
    }

    // TODO (rahulrav@)  Before this test can be added to this test suite, we need to clean up our
    // TaskExecutor so it's not a singleton.
    // Right now, these tests fail because we don't seem to clean up correctly.

    /*
    @Test
    @SmallTest
    @RepeatRule.Repeat(times = 10)
    @SdkSuppress(maxSdkVersion = 22)    // We can't force JobScheduler to run quicker than 15 mins.
    public void testPeriodicWork_ExecutesRepeatedly() throws InterruptedException {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                TestWorker.class,
                15,
                TimeUnit.MINUTES)
                .build();
        WorkSpec workSpec = work.getWorkSpec();
        workSpec.intervalDuration = 100L; // Manually override this to a smaller value for tests.
        workSpec.flexDuration = 0L;         // Manually override this to a smaller value for tests.

        final CountDownLatch latch = new CountDownLatch(3);
        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();

        LiveData<WorkStatus> status = mWorkManagerImpl.getStatusByIdLiveData(work.getId());
        status.observe(testLifecycleOwner, new Observer<WorkStatus>() {
            @Override
            public void onChanged(@Nullable WorkStatus workStatus) {
                if (workStatus != null) {
                    if (workStatus.getState() == RUNNING) {
                        latch.countDown();
                    }
                }
            }
        });

        mWorkManagerImpl.enqueueSync(work);
        // latch.await();
        latch.await(20, TimeUnit.SECONDS);
        assertThat(latch.getCount(), is(0L));
        status.removeObservers(testLifecycleOwner);
        mWorkManagerImpl.cancelWorkById(work.getId());
    }
    */

    private void insertWorkSpecAndTags(WorkRequest work) {
        mDatabase.workSpecDao().insertWorkSpec(work.getWorkSpec());
        for (String tag : work.getTags()) {
            mDatabase.workTagDao().insert(new WorkTag(tag, work.getStringId()));
        }
    }

    private void insertNamedWorks(String name, WorkRequest... works) {
        for (WorkRequest work : works) {
            insertWorkSpecAndTags(work);
            mDatabase.workNameDao().insert(new WorkName(name, work.getStringId()));
        }
    }

    private void insertDependency(OneTimeWorkRequest work, OneTimeWorkRequest prerequisiteWork) {
        mDatabase.dependencyDao().insertDependency(
                new Dependency(work.getStringId(), prerequisiteWork.getStringId()));
    }
}
