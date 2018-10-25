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

package androidx.work.impl.background.firebase;

import static androidx.test.espresso.matcher.ViewMatchers.assertThat;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.arch.core.executor.ArchTaskExecutor;
import android.arch.core.executor.TaskExecutor;
import android.content.Context;
import android.support.annotation.NonNull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.OneTimeWorkRequest;
import androidx.work.State;
import androidx.work.WorkRequest;
import androidx.work.impl.WorkDatabase;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.model.WorkSpecDao;
import androidx.work.impl.utils.taskexecutor.InstantWorkTaskExecutor;
import androidx.work.worker.FirebaseInfiniteTestWorker;

import com.firebase.jobdispatcher.JobParameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(maxSdkVersion = WorkManagerImpl.MAX_PRE_JOB_SCHEDULER_API_LEVEL)
public class FirebaseJobServiceTest {

    private WorkManagerImpl mWorkManagerImpl;
    private WorkDatabase mDatabase;
    private FirebaseJobService mFirebaseJobService;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        Configuration configuration = new Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
        mWorkManagerImpl =
                new WorkManagerImpl(context, configuration, new InstantWorkTaskExecutor());
        WorkManagerImpl.setDelegate(mWorkManagerImpl);
        mDatabase = mWorkManagerImpl.getWorkDatabase();
        mFirebaseJobService = new FirebaseJobService();
        mFirebaseJobService.onCreate();

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
    }

    @After
    public void tearDown() {
        mFirebaseJobService.onDestroy();
        WorkManagerImpl.setDelegate(null);
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    @LargeTest
    public void testOnStopJob_ResetsWorkStatus() throws InterruptedException {
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(FirebaseInfiniteTestWorker.class).build();
        insertWork(work);

        JobParameters mockParams = createMockJobParameters(work.getStringId());
        mFirebaseJobService.onStartJob(mockParams);

        // TODO(sumir): Remove later.  Put here because WorkerWrapper sets state to RUNNING.
        Thread.sleep(5000L);

        WorkSpecDao workSpecDao = mDatabase.workSpecDao();
        assertThat(workSpecDao.getState(work.getStringId()), is(State.RUNNING));

        mFirebaseJobService.onStopJob(mockParams);
        assertThat(workSpecDao.getState(work.getStringId()), is(State.ENQUEUED));
    }

    @Test
    @SmallTest
    public void testOnStopJob_ReschedulesWhenNotCancelled() {
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(FirebaseInfiniteTestWorker.class).build();
        insertWork(work);

        JobParameters mockParams = createMockJobParameters(work.getStringId());
        assertThat(mFirebaseJobService.onStartJob(mockParams), is(true));
        assertThat(mFirebaseJobService.onStopJob(mockParams), is(true));
    }

    @Test
    @LargeTest
    public void testOnStopJob_DoesNotRescheduleWhenCancelled()
            throws ExecutionException, InterruptedException {

        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(FirebaseInfiniteTestWorker.class).build();
        insertWork(work);

        JobParameters mockParams = createMockJobParameters(work.getStringId());
        assertThat(mFirebaseJobService.onStartJob(mockParams), is(true));
        WorkManagerImpl.getInstance().cancelWorkByIdInternal(work.getId()).get();
        assertThat(mFirebaseJobService.onStopJob(mockParams), is(false));
    }

    private JobParameters createMockJobParameters(String id) {
        JobParameters jobParameters = mock(JobParameters.class);
        when(jobParameters.getTag()).thenReturn(id);
        return jobParameters;
    }

    private void insertWork(WorkRequest work) {
        mDatabase.workSpecDao().insertWorkSpec(work.getWorkSpec());
    }
}
