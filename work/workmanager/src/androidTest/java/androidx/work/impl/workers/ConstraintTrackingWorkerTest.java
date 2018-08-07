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

package androidx.work.impl.workers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.DatabaseTest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.State;
import androidx.work.impl.ExecutionListener;
import androidx.work.impl.Extras;
import androidx.work.impl.Scheduler;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.WorkerWrapper;
import androidx.work.impl.constraints.trackers.BatteryChargingTracker;
import androidx.work.impl.constraints.trackers.BatteryNotLowTracker;
import androidx.work.impl.constraints.trackers.NetworkStateTracker;
import androidx.work.impl.constraints.trackers.StorageNotLowTracker;
import androidx.work.impl.constraints.trackers.Trackers;
import androidx.work.impl.model.WorkSpec;
import androidx.work.worker.EchoingWorker;
import androidx.work.worker.SleepTestWorker;
import androidx.work.worker.TestWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ConstraintTrackingWorkerTest extends DatabaseTest implements ExecutionListener {

    private static final long DELAY_IN_MILLIS = 100;
    private static final long TEST_TIMEOUT_IN_SECONDS = 6;
    private static final String TEST_ARGUMENT_NAME = "test";

    private Context mContext;
    private Handler mMainHandler;
    private CountDownLatch mLatch;
    private ExecutorService mExecutorService;

    private OneTimeWorkRequest mWork;
    private WorkerWrapper mWorkerWrapper;
    private ConstraintTrackingWorker mWorker;
    private WorkManagerImpl mWorkManagerImpl;
    private Configuration mConfiguration;
    private Scheduler mScheduler;
    private Extras.RuntimeExtras mRuntimeExtras;
    private Trackers mTracker;
    private BatteryChargingTracker mBatteryChargingTracker;
    private BatteryNotLowTracker mBatteryNotLowTracker;
    private NetworkStateTracker mNetworkStateTracker;
    private StorageNotLowTracker mStorageNotLowTracker;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext().getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutorService = Executors.newSingleThreadScheduledExecutor();
        mLatch = new CountDownLatch(1);
        mConfiguration = new Configuration.Builder().build();
        mRuntimeExtras = new Extras.RuntimeExtras();
        mRuntimeExtras.mExecutionListener = this;

        mWorkManagerImpl = mock(WorkManagerImpl.class);
        mScheduler = mock(Scheduler.class);
        when(mWorkManagerImpl.getWorkDatabase()).thenReturn(mDatabase);
        when(mWorkManagerImpl.getConfiguration()).thenReturn(mConfiguration);

        mBatteryChargingTracker = spy(new BatteryChargingTracker(mContext));
        mBatteryNotLowTracker = spy(new BatteryNotLowTracker(mContext));
        // Requires API 24+ types.
        mNetworkStateTracker = mock(NetworkStateTracker.class);
        mStorageNotLowTracker = spy(new StorageNotLowTracker(mContext));
        mTracker = mock(Trackers.class);

        when(mTracker.getBatteryChargingTracker()).thenReturn(mBatteryChargingTracker);
        when(mTracker.getBatteryNotLowTracker()).thenReturn(mBatteryNotLowTracker);
        when(mTracker.getNetworkStateTracker()).thenReturn(mNetworkStateTracker);
        when(mTracker.getStorageNotLowTracker()).thenReturn(mStorageNotLowTracker);

        // Override Trackers being used by WorkConstraintsProxy
        Trackers.setInstance(mTracker);
    }

    @After
    public void tearDown() {
        mExecutorService.shutdownNow();
    }

    @Test
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testConstraintTrackingWorker_onConstraintsMet() throws InterruptedException {
        when(mBatteryNotLowTracker.getInitialState()).thenReturn(true);
        setupDelegateForExecution(EchoingWorker.class.getName());

        WorkerWrapper.Builder builder =
                new WorkerWrapper.Builder(mContext, mConfiguration, mDatabase, mWork.getStringId());
        builder.withWorker(mWorker)
                .withListener(null) // set on ConstraintTrackingWorker
                .withSchedulers(Collections.singletonList(mScheduler));
        mWorkerWrapper = builder.build();
        mExecutorService.submit(mWorkerWrapper);

        mLatch.await(TEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(mWork.getStringId());
        assertThat(mLatch.getCount(), is(0L));
        assertThat(workSpec.state, is(State.SUCCEEDED));
        Data output = workSpec.output;
        assertThat(output.getBoolean(TEST_ARGUMENT_NAME, false), is(true));
    }

    @Test
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testConstraintTrackingWorker_onConstraintsNotMet() throws InterruptedException {
        when(mBatteryNotLowTracker.getInitialState()).thenReturn(false);
        setupDelegateForExecution(TestWorker.class.getName());
        WorkerWrapper.Builder builder =
                new WorkerWrapper.Builder(mContext, mConfiguration, mDatabase, mWork.getStringId());
        builder.withWorker(mWorker)
                .withListener(null) // set on ConstraintTrackingWorker
                .withSchedulers(Collections.singletonList(mScheduler));

        mWorkerWrapper = builder.build();
        mExecutorService.submit(mWorkerWrapper);
        mLatch.await(TEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(mWork.getStringId());
        assertThat(mLatch.getCount(), is(0L));
        assertThat(workSpec.state, is(State.ENQUEUED));
    }

    @Test
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testConstraintTrackingWorker_onConstraintsChanged() throws InterruptedException {
        when(mBatteryNotLowTracker.getInitialState()).thenReturn(true);
        setupDelegateForExecution(SleepTestWorker.class.getName());
        WorkerWrapper.Builder builder =
                new WorkerWrapper.Builder(mContext, mConfiguration, mDatabase, mWork.getStringId());
        builder.withWorker(mWorker)
                .withListener(null) // set on ConstraintTrackingWorker
                .withSchedulers(Collections.singletonList(mScheduler));

        mWorkerWrapper = builder.build();
        mExecutorService.submit(mWorkerWrapper);
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBatteryNotLowTracker.setState(false);
            }
        }, DELAY_IN_MILLIS);

        mLatch.await(TEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(mWork.getStringId());
        assertThat(mLatch.getCount(), is(0L));
        assertThat(workSpec.state, is(State.ENQUEUED));
    }

    @Test
    @SdkSuppress(minSdkVersion = 23, maxSdkVersion = 25)
    public void testConstraintTrackingWorker_onConstraintsChangedTwice()
            throws InterruptedException {
        when(mBatteryNotLowTracker.getInitialState()).thenReturn(true);
        setupDelegateForExecution(SleepTestWorker.class.getName());

        WorkerWrapper.Builder builder =
                new WorkerWrapper.Builder(mContext, mConfiguration, mDatabase, mWork.getStringId());
        builder.withWorker(mWorker)
                .withListener(null) // set on ConstraintTrackingWorker
                .withSchedulers(Collections.singletonList(mScheduler));

        mWorkerWrapper = builder.build();
        mExecutorService.submit(mWorkerWrapper);

        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBatteryNotLowTracker.setState(false);
            }
        }, DELAY_IN_MILLIS);

        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBatteryNotLowTracker.setState(true);
            }
        }, DELAY_IN_MILLIS);

        mLatch.await(TEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        WorkSpec workSpec = mDatabase.workSpecDao().getWorkSpec(mWork.getStringId());
        assertThat(mLatch.getCount(), is(0L));
        assertThat(workSpec.state, is(State.ENQUEUED));
    }

    @Override
    public void onExecuted(
            @NonNull String workSpecId,
            boolean isSuccessful,
            boolean needsReschedule) {

        // Complete the execution of the ConstraintTrackingWorker like the real WorkerWrapper would.
        // TODO (rahulrav@) Once we move to the world where NonBlockingWorker is public, this is
        // no longer required.
        mWorkerWrapper.onWorkFinished(mWorker.getResult());
        mLatch.countDown();
    }

    private void setupDelegateForExecution(@NonNull String delegateName) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        Data input = new Data.Builder()
                .putString(ConstraintTrackingWorker.ARGUMENT_CLASS_NAME, delegateName)
                .putBoolean(TEST_ARGUMENT_NAME, true)
                .build();

        mWork = new OneTimeWorkRequest.Builder(ConstraintTrackingWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .build();

        insertWork(mWork);

        ConstraintTrackingWorker worker =
                (ConstraintTrackingWorker) WorkerWrapper.workerFromClassName(
                        mContext,
                        ConstraintTrackingWorker.class.getName(),
                        mWork.getId(),
                        new Extras(input, Collections.<String>emptyList(), mRuntimeExtras, 1));

        mWorker = spy(worker);
        when(mWorker.getWorkDatabase()).thenReturn(mDatabase);
    }

}
