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

package android.arch.background.workmanager;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import android.arch.background.workmanager.model.Arguments;
import android.arch.background.workmanager.model.Constraints;
import android.arch.background.workmanager.model.DependencyDao;
import android.arch.background.workmanager.model.WorkSpec;
import android.arch.background.workmanager.model.WorkSpecDao;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class WorkManagerTest {
    private static final long DEFAULT_SLEEP_TIME_MS = 1000L;
    private WorkDatabase mDatabase;
    private WorkManager mWorkManager;

    @Before
    public void setUp() {
        mWorkManager = new WorkManager(InstrumentationRegistry.getTargetContext(), true);
        mDatabase = mWorkManager.getWorkDatabase();
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void testEnqueue_insertWork() throws InterruptedException {
        final int workCount = 3;
        final Work[] workArray = new Work[workCount];
        for (int i = 0; i < workCount; ++i) {
            workArray[i] = new Work.Builder(TestWorker.class).build();
        }
        mWorkManager.enqueue(workArray[0]).then(workArray[1]).then(workArray[2]);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        for (int i = 0; i < workCount; ++i) {
            String id = workArray[i].getId();
            assertThat(mDatabase.workSpecDao().getWorkSpec(id), is(notNullValue()));
            assertThat(
                    "index " + i + " does not have expected number of dependencies!",
                    mDatabase.dependencyDao().hasPrerequisites(id),
                    is(i > 0));
        }
    }

    @Test
    public void testEnqueue_insertMultipleWork() throws InterruptedException {
        Work work1 = new Work.Builder(TestWorker.class).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        Work work3 = new Work.Builder(TestWorker.class).build();

        mWorkManager.enqueue(work1, work2, work3);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3.getId()), is(notNullValue()));
    }

    @Test
    public void testEnqueue_insertWithDependencies() throws InterruptedException {
        Work work1a = new Work.Builder(TestWorker.class).build();
        Work work1b = new Work.Builder(TestWorker.class).build();
        Work work2 = new Work.Builder(TestWorker.class).build();
        Work work3a = new Work.Builder(TestWorker.class).build();
        Work work3b = new Work.Builder(TestWorker.class).build();

        mWorkManager.enqueue(work1a, work1b).then(work2).then(work3a, work3b);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getWorkSpec(work1a.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work1b.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work2.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3a.getId()), is(notNullValue()));
        assertThat(workSpecDao.getWorkSpec(work3b.getId()), is(notNullValue()));

        DependencyDao dependencyDao = mDatabase.dependencyDao();
        assertThat(dependencyDao.hasPrerequisites(work1a.getId()), is(false));
        assertThat(dependencyDao.hasPrerequisites(work1b.getId()), is(false));

        List<String> prerequisites = dependencyDao.getPrerequisites(work2.getId());
        assertThat(prerequisites, containsInAnyOrder(work1a.getId(), work1b.getId()));

        prerequisites = dependencyDao.getPrerequisites(work3a.getId());
        assertThat(prerequisites, containsInAnyOrder(work2.getId()));

        prerequisites = dependencyDao.getPrerequisites(work3b.getId());
        assertThat(prerequisites, containsInAnyOrder(work2.getId()));
    }

    @Test
    public void testEnqueue_insertWorkConstraints() throws InterruptedException {
        final long expectedInitialDelay = 1000L;
        Work work0 = new Work.Builder(TestWorker.class)
                .withConstraints(
                        new Constraints.Builder()
                                .setRequiresCharging(true)
                                .setRequiresDeviceIdle(true)
                                .setRequiredNetworkType(Constraints.NETWORK_TYPE_METERED)
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true)
                                .setInitialDelay(expectedInitialDelay)
                                .build())
                .build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManager.enqueue(work0).then(work1);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        Constraints constraints = workSpec0.getConstraints();
        assertThat(constraints, is(notNullValue()));
        assertThat(constraints.requiresCharging(), is(true));
        assertThat(constraints.requiresDeviceIdle(), is(true));
        assertThat(constraints.requiresBatteryNotLow(), is(true));
        assertThat(constraints.requiresStorageNotLow(), is(true));
        assertThat(constraints.getInitialDelay(), is(expectedInitialDelay));
        assertThat(constraints.getRequiredNetworkType(), is(Constraints.NETWORK_TYPE_METERED));

        constraints = workSpec1.getConstraints();
        assertThat(constraints, is(notNullValue()));
        assertThat(constraints.requiresCharging(), is(false));
        assertThat(constraints.requiresDeviceIdle(), is(false));
        assertThat(constraints.requiresBatteryNotLow(), is(false));
        assertThat(constraints.requiresStorageNotLow(), is(false));
        assertThat(constraints.getInitialDelay(), is(0L));
        assertThat(constraints.getRequiredNetworkType(), is(Constraints.NETWORK_TYPE_NONE));
    }

    @Test
    public void testEnqueue_insertWorkBackoffPolicy() throws InterruptedException {
        Work work0 = new Work.Builder(TestWorker.class)
                .withBackoffCriteria(Work.BACKOFF_POLICY_LINEAR, 50000)
                .build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManager.enqueue(work0).then(work1);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.getBackoffPolicy(), is(Work.BACKOFF_POLICY_LINEAR));
        assertThat(workSpec0.getBackoffDelayDuration(), is(50000L));

        assertThat(workSpec1.getBackoffPolicy(), is(Work.BACKOFF_POLICY_EXPONENTIAL));
        assertThat(workSpec1.getBackoffDelayDuration(), is(Work.DEFAULT_BACKOFF_DELAY_DURATION));
    }

    @Test
    public void testEnqueue_insertWorkArguments() throws InterruptedException {
        String key = "key";
        String expectedValue = "value";

        Arguments args = new Arguments();
        args.putString(key, expectedValue);

        Work work0 = new Work.Builder(TestWorker.class)
                .withArguments(args)
                .build();
        Work work1 = new Work.Builder(TestWorker.class).build();
        mWorkManager.enqueue(work0).then(work1);
        Thread.sleep(DEFAULT_SLEEP_TIME_MS);

        WorkSpec workSpec0 = mDatabase.workSpecDao().getWorkSpec(work0.getId());
        WorkSpec workSpec1 = mDatabase.workSpecDao().getWorkSpec(work1.getId());

        assertThat(workSpec0.getArguments(), is(notNullValue()));
        assertThat(workSpec1.getArguments(), is(notNullValue()));

        assertThat(workSpec0.getArguments().size(), is(1));
        assertThat(workSpec1.getArguments().size(), is(0));

        String actualValue = workSpec0.getArguments().getString(key, null);
        assertThat(actualValue, is(notNullValue()));
        assertThat(actualValue, is(expectedValue));
    }

    @Test
    public void testGenerateCleanupCallback_resetsRunningWorkStatuses() {
        WorkSpecDao workSpecDao = mDatabase.workSpecDao();

        Work work = new Work.Builder(TestWorker.class).build();
        WorkSpec workSpec = work.getWorkSpec();
        workSpec.setStatus(Work.STATUS_RUNNING);
        workSpecDao.insertWorkSpec(work.getWorkSpec());

        assertThat(workSpecDao.getWorkSpec(work.getId()).getStatus(), is(Work.STATUS_RUNNING));

        SupportSQLiteOpenHelper openHelper = mDatabase.getOpenHelper();
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        WorkDatabase.generateCleanupCallback().onOpen(db);

        assertThat(workSpecDao.getWorkSpec(work.getId()).getStatus(), is(Work.STATUS_ENQUEUED));
    }
}
