/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ads.identifier.benchmark;

import static androidx.ads.identifier.AdvertisingIdUtils.GET_AD_ID_ACTION;
import static androidx.ads.identifier.testing.MockPackageManagerHelper.createServiceResolveInfo;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import androidx.ads.identifier.AdvertisingIdClient;
import androidx.ads.identifier.AdvertisingIdInfo;
import androidx.ads.identifier.provider.internal.AdvertisingIdService;
import androidx.ads.identifier.testing.MockPackageManagerHelper;
import androidx.annotation.NonNull;
import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AdvertisingIdBenchmark {

    private static final int CONCURRENCY_NUM = 10;
    private static final String SERVICE_NAME = AdvertisingIdService.class.getName();

    @Rule
    public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    private MockPackageManagerHelper mMockPackageManagerHelper = new MockPackageManagerHelper();

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        Context applicationContext = ApplicationProvider.getApplicationContext();

        mContext = new ContextWrapper(applicationContext) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public PackageManager getPackageManager() {
                return mMockPackageManagerHelper.getMockPackageManager();
            }
        };

        mMockPackageManagerHelper.mockQueryGetAdIdServices(Lists.newArrayList(
                createServiceResolveInfo(mContext.getPackageName(), SERVICE_NAME)));
    }

    @After
    public void tearDown() {
        stopAdvertisingIdService();
    }

    private void stopAdvertisingIdService() {
        Intent serviceIntent = new Intent(GET_AD_ID_ACTION);
        serviceIntent.setClassName(mContext.getPackageName(), SERVICE_NAME);
        mContext.stopService(serviceIntent);
    }

    @Test
    public void getAdvertisingIdInfo() throws Exception {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            getAdvertisingIdInfoListenableFuture(countDownLatch);
            countDownLatch.await();
        }
    }

    private void getAdvertisingIdInfoListenableFuture(CountDownLatch countDownLatch) {
        ListenableFuture<AdvertisingIdInfo> advertisingIdInfoListenableFuture =
                AdvertisingIdClient.getAdvertisingIdInfo(mContext);
        Futures.addCallback(advertisingIdInfoListenableFuture,
                new FutureCallback<AdvertisingIdInfo>() {
                    @Override
                    public void onSuccess(AdvertisingIdInfo advertisingIdInfo) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                }, MoreExecutors.directExecutor());
    }

    @Test
    public void getAdvertisingIdInfo_worker() throws Exception {
        WorkManager workManager = WorkManager.getInstance(mContext);
        workManager.cancelAllWork();
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            workManager.enqueue(OneTimeWorkRequest.from(GetAdInfoWorker.class)).getResult().get();
        }
    }

    /** Get the Advertising ID on a worker thread. */
    private class GetAdInfoWorker extends Worker {
        GetAdInfoWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                AdvertisingIdClient.getAdvertisingIdInfo(mContext).get();
            } catch (Exception e) {
                return Result.failure();
            }
            return Result.success();
        }
    }

    @Test
    public void getAdvertisingIdInfo_asyncTask() throws Exception {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            new AsyncTask<Void, Void, AdvertisingIdInfo>() {
                @Override
                protected AdvertisingIdInfo doInBackground(Void... voids) {
                    try {
                        return AdvertisingIdClient.getAdvertisingIdInfo(mContext).get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }.execute().get();
        }
    }

    @Test
    public void getAdvertisingIdInfo_thread() throws Exception {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            Thread thread = new Thread(() -> {
                try {
                    AdvertisingIdClient.getAdvertisingIdInfo(mContext).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
            thread.join();
        }
    }

    @Test
    public void getAdvertisingIdInfo_concurrency() throws Exception {
        getAdvertisingIdInfo_concurrencyWithDelay(0);
    }

    @Test
    public void getAdvertisingIdInfo_concurrencyWithDelay1Millis() throws Exception {
        getAdvertisingIdInfo_concurrencyWithDelay(1);
    }

    @Test
    public void getAdvertisingIdInfo_concurrencyWithDelay10Millis() throws Exception {
        getAdvertisingIdInfo_concurrencyWithDelay(10);
    }

    private void getAdvertisingIdInfo_concurrencyWithDelay(long millis) throws Exception {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            CountDownLatch countDownLatch = new CountDownLatch(CONCURRENCY_NUM);
            for (int i = 0; i < CONCURRENCY_NUM; i++) {
                if (millis != 0) {
                    Thread.sleep(millis);
                }

                getAdvertisingIdInfoListenableFuture(countDownLatch);
            }
            countDownLatch.await();
        }
    }
}
