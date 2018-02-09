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

import static android.arch.background.workmanager.ExistingWorkPolicy.APPEND;
import static android.arch.background.workmanager.ExistingWorkPolicy.KEEP;
import static android.arch.background.workmanager.ExistingWorkPolicy.REPLACE;
import static android.arch.background.workmanager.NetworkType.METERED;
import static android.arch.background.workmanager.NetworkType.NOT_REQUIRED;
import static android.arch.background.workmanager.State.BLOCKED;
import static android.arch.background.workmanager.State.CANCELLED;
import static android.arch.background.workmanager.State.ENQUEUED;
import static android.arch.background.workmanager.State.FAILED;
import static android.arch.background.workmanager.State.RUNNING;
import static android.arch.background.workmanager.State.SUCCEEDED;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.isIn;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.arch.background.workmanager.Arguments;
import android.arch.background.workmanager.BackoffPolicy;
import android.arch.background.workmanager.BaseWork;
import android.arch.background.workmanager.Constraints;
import android.arch.background.workmanager.ContentUriTriggers;
import android.arch.background.workmanager.PeriodicWork;
import android.arch.background.workmanager.TestLifecycleOwner;
import android.arch.background.workmanager.Work;
import android.arch.background.workmanager.WorkContinuation;
import android.arch.background.workmanager.WorkManagerTest;
import android.arch.background.workmanager.WorkStatus;
import android.arch.background.workmanager.executors.SynchronousExecutorService;
import android.arch.background.workmanager.impl.model.Dependency;
import android.arch.background.workmanager.impl.model.DependencyDao;
import android.arch.background.workmanager.impl.model.WorkSpec;
import android.arch.background.workmanager.impl.model.WorkSpecDao;
import android.arch.background.workmanager.impl.model.WorkTag;
import android.arch.background.workmanager.impl.model.WorkTagDao;
import android.arch.background.workmanager.impl.utils.taskexecutor.InstantTaskExecutorRule;
import android.arch.background.workmanager.worker.TestWorker;
import android.arch.core.executor.ArchTaskExecutor;
import android.arch.core.executor.TaskExecutor;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WorkManagerImplTest extends WorkManagerTest {
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
        WorkManagerConfiguration configuration = new WorkManagerConfiguration(
                context,
                true,
                new SynchronousExecutorService());
        mWorkManagerImpl = new WorkManagerImpl(context, configuration);
        mDatabase = mWorkManagerImpl.getWorkDatabase();
    }

    @After
    public void tearDown() {
        List<String> ids = mDatabase.workSpecDao().getAllWorkSpecIds();
        for (String id : ids) {
            mWorkManagerImpl.cancelWorkForId(id);
        }
        mDatabase.close();
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWork() throws InterruptedException {
        final int workCount = 3;
        final Work[] workArray = new Work[workCount];
        for (int i = 0; i < workCount; ++i) {
            workArray[i] = new Work.Builder(TestWorker.class).build();
        }
        mWorkManagerImpl.beginWith(workArray[0]).then(workArray[1]).then(workArray[2]).enqueue();

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
        Work work1 = new Work.Builder(TestWorker.class).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        Work work3 = new Work.Builder(TestWorker.class).build();

        mWorkManagerImpl.enqueue(work1, work2, work3);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getId()), is(notNullValue()));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWithDependencies() {
        Work work1a = new Work.Builder(TestWorker.class).build();
        Work work1b = new Work.Builder(TestWorker.class).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        Work work3a = new Work.Builder(TestWorker.class).build();
        Work work3b = new Work.Builder(TestWorker.class).build();

        mWorkManagerImpl.beginWith(work1a, work1b).then(work2).then(work3a, work3b).enqueue();

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
        Work work1 = new Work.Builder(TestWorker.class).withInitialState(SUCCEEDED).build();

        WorkContinuation workContinuation = mWorkManagerImpl.beginWith(work1);
        workContinuation.enqueue();
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpecState(work1.getId()), is(SUCCEEDED));

        Work work2 = new Work.Builder(TestWorker.class).build();
        workContinuation.then(work2).enqueue();
        assertThat(workSpecDao.getWorkSpecState(work2.getId()), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkConstraints() {
        Uri testUri1 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri testUri2 = MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        Work work0 = new Work.Builder(TestWorker.class)
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
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        ContentUriTriggers expectedTriggers = new ContentUriTriggers();
        expectedTriggers.add(testUri1, true);
        expectedTriggers.add(testUri2, false);

        Constraints constraints = workSpec0.getConstraints();
        assertThat(constraints, is(notNullValue()));
        assertThat(constraints.requiresCharging(), is(true));
        assertThat(constraints.requiresDeviceIdle(), is(true));
        assertThat(constraints.requiresBatteryNotLow(), is(true));
        assertThat(constraints.requiresStorageNotLow(), is(true));
        assertThat(constraints.getRequiredNetworkType(), is(METERED));
        assertThat(constraints.getContentUriTriggers(), is(expectedTriggers));

        constraints = workSpec1.getConstraints();
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
        Work work0 = new Work.Builder(TestWorker.class)
                .withInitialDelay(expectedInitialDelay)
                .build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.getInitialDelay(), is(expectedInitialDelay));
        assertThat(workSpec1.getInitialDelay(), is(0L));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkBackoffPolicy() {
        Work work0 = new Work.Builder(TestWorker.class)
                .withBackoffCriteria(BackoffPolicy.LINEAR, 50000)
                .build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).enqueue();

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.getBackoffPolicy(), is(BackoffPolicy.LINEAR));
        assertThat(workSpec0.getBackoffDelayDuration(), is(50000L));

        assertThat(workSpec1.getBackoffPolicy(), is(BackoffPolicy.EXPONENTIAL));
        assertThat(workSpec1.getBackoffDelayDuration(), is(BaseWork.DEFAULT_BACKOFF_DELAY_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertWorkTags() {
        final String firstTag = "first_tag";
        final String secondTag = "second_tag";
        final String thirdTag = "third_tag";

        Work work0 = new Work.Builder(TestWorker.class).addTag(firstTag).addTag(secondTag).build();
        Work work1 = new Work.Builder(TestWorker.class).addTag(firstTag).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWith(work0).then(work1).then(work2).enqueue();

        WorkTagDao workTagDao = mDatabase.workTagDao();
        assertThat(workTagDao.getWorkSpecsWithTag(firstTag),
                containsInAnyOrder(work0.getId(), work1.getId()));
        assertThat(workTagDao.getWorkSpecsWithTag(secondTag), containsInAnyOrder(work0.getId()));
        assertThat(workTagDao.getWorkSpecsWithTag(thirdTag), emptyCollectionOf(String.class));
    }

    @Test
    @SmallTest
    public void testEnqueue_insertPeriodicWork() {
        PeriodicWork periodicWork = new PeriodicWork.Builder(
                TestWorker.class,
                PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS)
                .build();
        mWorkManagerImpl.enqueue(periodicWork);

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getId());
        assertThat(workSpec.isPeriodic(), is(true));
        assertThat(workSpec.getIntervalDuration(), is(PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS));
        assertThat(workSpec.getFlexDuration(), is(PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS));
    }

    @Test
    @SmallTest
    public void testEnqueued_work_setsPeriodStartTime() {
        Work work = new Work.Builder(TestWorker.class).build();
        assertThat(getWorkSpec(work).getPeriodStartTime(), is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.enqueue(work);

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(work.getId());
        assertThat(workSpec.getPeriodStartTime(), is(greaterThan(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testEnqueued_periodicWork_setsPeriodStartTime() {
        PeriodicWork periodicWork = new PeriodicWork.Builder(
                TestWorker.class,
                PeriodicWork.MIN_PERIODIC_INTERVAL_MILLIS)
                .build();
        assertThat(getWorkSpec(periodicWork).getPeriodStartTime(), is(0L));

        long beforeEnqueueTime = System.currentTimeMillis();

        mWorkManagerImpl.enqueue(periodicWork);

        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(periodicWork.getId());
        assertThat(workSpec.getPeriodStartTime(), is(greaterThan(beforeEnqueueTime)));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_setsUniqueTag() {
        final String testTag = "mytag";

        Work work = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl.beginWithUniqueTag(testTag, REPLACE)
                .then(work)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
        assertThat(work.getId(), isIn(workSpecIds));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_deletesOldTagsOnReplace() {
        final String testTag = "mytag";

        Work originalWork = new Work.Builder(TestWorker.class).addTag(testTag).build();
        insertWorkSpecAndTags(originalWork);

        Work replacementWork1 = new Work.Builder(TestWorker.class).build();
        Work replacementWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, REPLACE, replacementWork1)
                .then(replacementWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
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
    public void testUniqueTagSequence_keepsExistingWorkOnKeep() {
        final String testTag = "mytag";

        Work originalWork = new Work.Builder(TestWorker.class).addTag(testTag).build();
        insertWorkSpecAndTags(originalWork);

        Work replacementWork1 = new Work.Builder(TestWorker.class).build();
        Work replacementWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, KEEP, replacementWork1)
                .then(replacementWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
        assertThat(workSpecIds, containsInAnyOrder(originalWork.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getId()), is(nullValue()));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_replacesExistingWorkOnKeepWhenExistingWorkIsFinished() {
        final String testTag = "mytag";

        Work originalWork = new Work.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .addTag(testTag)
                .build();
        insertWorkSpecAndTags(originalWork);

        Work replacementWork1 = new Work.Builder(TestWorker.class).build();
        Work replacementWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, KEEP, replacementWork1)
                .then(replacementWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
        assertThat(workSpecIds,
                containsInAnyOrder(replacementWork1.getId(), replacementWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(replacementWork1.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(replacementWork2.getId()), is(not(nullValue())));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_appendsExistingWorkOnAppend() {
        final String testTag = "mytag";

        Work originalWork = new Work.Builder(TestWorker.class).addTag(testTag).build();
        insertWorkSpecAndTags(originalWork);

        Work appendWork1 = new Work.Builder(TestWorker.class).build();
        Work appendWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
        assertThat(workSpecIds,
                containsInAnyOrder(originalWork.getId(), appendWork1.getId(), appendWork2.getId()));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(originalWork.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpecState(appendWork1.getId()), is(BLOCKED));
        assertThat(workSpecDao.getWorkSpecState(appendWork2.getId()), is(BLOCKED));

        assertThat(mDatabase.dependencyDao().getDependentWorkIds(originalWork.getId()),
                containsInAnyOrder(appendWork1.getId()));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_appendsExistingWorkToOnlyLeavesOnAppend() {
        final String testTag = "mytag";

        Work originalWork1 = new Work.Builder(TestWorker.class).addTag(testTag).build();
        Work originalWork2 = new Work.Builder(TestWorker.class).addTag(testTag).build();
        Work originalWork3 = new Work.Builder(TestWorker.class).addTag(testTag).build();
        Work originalWork4 = new Work.Builder(TestWorker.class).addTag(testTag).build();
        insertWorkSpecAndTags(originalWork1);
        insertWorkSpecAndTags(originalWork2);
        insertWorkSpecAndTags(originalWork3);
        insertWorkSpecAndTags(originalWork4);

        Dependency dependency21 = new Dependency(originalWork2.getId(), originalWork1.getId());
        Dependency dependency32 = new Dependency(originalWork3.getId(), originalWork2.getId());
        Dependency dependency42 = new Dependency(originalWork4.getId(), originalWork2.getId());

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        dependencyDao.insertDependency(dependency21);
        dependencyDao.insertDependency(dependency32);
        dependencyDao.insertDependency(dependency42);

        Work appendWork1 = new Work.Builder(TestWorker.class).build();
        Work appendWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
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
        assertThat(workSpecDao.getWorkSpecState(appendWork1.getId()), is(BLOCKED));
        assertThat(workSpecDao.getWorkSpecState(appendWork2.getId()), is(BLOCKED));

        assertThat(dependencyDao.getPrerequisites(appendWork1.getId()),
                containsInAnyOrder(originalWork3.getId(), originalWork4.getId()));
        assertThat(dependencyDao.getPrerequisites(appendWork2.getId()),
                containsInAnyOrder(appendWork1.getId()));
    }

    @Test
    @SmallTest
    public void testUniqueTagSequence_insertsExistingWorkWhenNothingToAppendTo() {
        final String testTag = "mytag";

        Work appendWork1 = new Work.Builder(TestWorker.class).build();
        Work appendWork2 = new Work.Builder(TestWorker.class).build();
        mWorkManagerImpl
                .beginWithUniqueTag(testTag, APPEND, appendWork1)
                .then(appendWork2)
                .enqueue();

        List<String> workSpecIds = mDatabase.workTagDao().getWorkSpecsWithTag(testTag);
        assertThat(workSpecIds,
                containsInAnyOrder(appendWork1.getId(), appendWork2.getId()));
    }

    @Test
    @SmallTest
    @SuppressWarnings("unchecked")
    public void testGetStatuses() {
        Work work0 = new Work.Builder(TestWorker.class).build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);

        Observer<List<WorkStatus>> mockObserver = mock(Observer.class);

        TestLifecycleOwner testLifecycleOwner = new TestLifecycleOwner();
        LiveData<List<WorkStatus>> liveData =
                mWorkManagerImpl.getStatuses(Arrays.asList(work0.getId(), work1.getId()));
        liveData.observe(testLifecycleOwner, mockObserver);

        ArgumentCaptor<List<WorkStatus>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        WorkStatus workStatus0 = new WorkStatus(work0.getId(), ENQUEUED, Arguments.EMPTY);
        WorkStatus workStatus1 = new WorkStatus(work1.getId(), ENQUEUED, Arguments.EMPTY);
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        workSpecDao.setState(RUNNING, work0.getId());

        verify(mockObserver, times(2)).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus0 = new WorkStatus(work0.getId(), RUNNING, Arguments.EMPTY);
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        clearInvocations(mockObserver);
        workSpecDao.setState(RUNNING, work1.getId());

        verify(mockObserver).onChanged(captor.capture());
        assertThat(captor.getValue(), is(not(nullValue())));
        assertThat(captor.getValue().size(), is(2));

        workStatus1 = new WorkStatus(work1.getId(), RUNNING, Arguments.EMPTY);
        assertThat(captor.getValue(), containsInAnyOrder(workStatus0, workStatus1));

        liveData.removeObservers(testLifecycleOwner);
    }

    @Test
    @SmallTest
    public void testCancelWorkForId() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        Work work0 = new Work.Builder(TestWorker.class).build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);

        mWorkManagerImpl.cancelWorkForId(work0.getId());

        assertThat(workSpecDao.getWorkSpecState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work1.getId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkWithTag() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        final String tagToClear = "tag_to_clear";
        final String tagNotToClear = "tag_not_to_clear";

        Work work0 = new Work.Builder(TestWorker.class).addTag(tagToClear).build();
        Work work1 = new Work.Builder(TestWorker.class).addTag(tagToClear).build();
        Work work2 = new Work.Builder(TestWorker.class).addTag(tagNotToClear).build();
        Work work3 = new Work.Builder(TestWorker.class).addTag(tagNotToClear).build();
        insertWorkSpecAndTags(work0);
        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);
        insertWorkSpecAndTags(work3);

        mWorkManagerImpl.cancelAllWorkWithTag(tagToClear);

        assertThat(workSpecDao.getWorkSpecState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work1.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work2.getId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getWorkSpecState(work3.getId()), is(not(CANCELLED)));
    }

    @Test
    @SmallTest
    public void testCancelAllWorkWithTag_deletesDependentWork() {
        String tag = "tag";

        Work work0 = new Work.Builder(TestWorker.class).addTag(tag).build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        Work work3 = new Work.Builder(TestWorker.class).build();
        Work work4 = new Work.Builder(TestWorker.class).build();

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

        Dependency dependency21 = new Dependency(work2.getId(), work1.getId());
        Dependency dependency23 = new Dependency(work2.getId(), work3.getId());
        Dependency dependency10 = new Dependency(work1.getId(), work0.getId());
        Dependency dependency40 = new Dependency(work4.getId(), work0.getId());

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        dependencyDao.insertDependency(dependency21);
        dependencyDao.insertDependency(dependency23);
        dependencyDao.insertDependency(dependency10);
        dependencyDao.insertDependency(dependency40);

        mWorkManagerImpl.cancelAllWorkWithTag(tag);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpecState(work0.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work1.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work2.getId()), is(CANCELLED));
        assertThat(workSpecDao.getWorkSpecState(work3.getId()), is(not(CANCELLED)));
        assertThat(workSpecDao.getWorkSpecState(work4.getId()), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testSynchronousCancelAndGetStatus() {
        Work work = new Work.Builder(TestWorker.class).build();
        insertWorkSpecAndTags(work);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpecState(work.getId()), is(ENQUEUED));

        mWorkManagerImpl.cancelWorkForIdSync(work.getId());
        assertThat(mWorkManagerImpl.getStatusSync(work.getId()).getState(), is(CANCELLED));
    }

    @Test
    @SmallTest
    public void testPruneDatabase() {
        Work enqueuedWork = new Work.Builder(TestWorker.class).build();
        Work finishedPrerequisiteWork1A =
                new Work.Builder(TestWorker.class).withInitialState(SUCCEEDED).build();
        Work finishedPrerequisiteWork1B =
                new Work.Builder(TestWorker.class).withInitialState(SUCCEEDED).build();
        Work finishedPrerequisiteWork2 =
                new Work.Builder(TestWorker.class).withInitialState(SUCCEEDED).build();
        Work finishedFinalWork =
                new Work.Builder(TestWorker.class).withInitialState(SUCCEEDED).build();

        insertWorkSpecAndTags(enqueuedWork);
        insertWorkSpecAndTags(finishedPrerequisiteWork1A);
        insertWorkSpecAndTags(finishedPrerequisiteWork1B);
        insertWorkSpecAndTags(finishedPrerequisiteWork2);
        insertWorkSpecAndTags(finishedFinalWork);

        Dependency dependency21A = new Dependency(
                finishedPrerequisiteWork2.getId(), finishedPrerequisiteWork1A.getId());
        Dependency dependency21B = new Dependency(
                finishedPrerequisiteWork2.getId(), finishedPrerequisiteWork1B.getId());
        Dependency dependencyFinal2 = new Dependency(
                finishedFinalWork.getId(), finishedPrerequisiteWork2.getId());

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        dependencyDao.insertDependency(dependency21A);
        dependencyDao.insertDependency(dependency21B);
        dependencyDao.insertDependency(dependencyFinal2);

        mWorkManagerImpl.pruneDatabase();

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(enqueuedWork.getId()), is(not(nullValue())));
        assertThat(workSpecDao.getWorkSpec(finishedPrerequisiteWork1A.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedPrerequisiteWork1B.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedPrerequisiteWork2.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(finishedFinalWork.getId()), is(nullValue()));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_resetsRunningWorkStatuses() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        Work work = new Work.Builder(TestWorker.class).withInitialState(RUNNING).build();
        workSpecDao.insertWorkSpec(getWorkSpec(work));

        assertThat(workSpecDao.getWorkSpecState(work.getId()), is(RUNNING));

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        assertThat(workSpecDao.getWorkSpecState(work.getId()), is(ENQUEUED));
    }

    @Test
    @SmallTest
    public void testGenerateCleanupCallback_deletesOldFinishedWork() {
        Work work1 = new Work.Builder(TestWorker.class)
                .withInitialState(SUCCEEDED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L)
                .build();
        Work work2 = new Work.Builder(TestWorker.class).withPeriodStartTime(Long.MAX_VALUE).build();

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
    public void testGenerateCleanupCallback_deletesDanglingBlockedDependencies() {
        Work work1 = new Work.Builder(TestWorker.class)
                .withInitialState(FAILED)
                .withPeriodStartTime(WorkDatabase.getPruneDate() - 1L)
                .build();
        Work work2 = new Work.Builder(TestWorker.class)
                .withInitialState(BLOCKED)
                .withPeriodStartTime(Long.MAX_VALUE)
                .build();
        Work work3 = new Work.Builder(TestWorker.class)
                .withInitialState(BLOCKED)
                .withPeriodStartTime(Long.MAX_VALUE)
                .build();

        insertWorkSpecAndTags(work1);
        insertWorkSpecAndTags(work2);
        insertWorkSpecAndTags(work3);

        Dependency dependency21 = new Dependency(work2.getId(), work1.getId());
        Dependency dependency32 = new Dependency(work3.getId(), work2.getId());

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        dependencyDao.insertDependency(dependency21);
        dependencyDao.insertDependency(dependency32);

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(nullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getId()), is(nullValue()));
    }

    private void insertWorkSpecAndTags(Work work) {
        mDatabase.workSpecDao().insertWorkSpec(getWorkSpec(work));
        for (String tag : getTags(work)) {
            mDatabase.workTagDao().insert(new WorkTag(tag, work.getId()));
        }
    }
}
