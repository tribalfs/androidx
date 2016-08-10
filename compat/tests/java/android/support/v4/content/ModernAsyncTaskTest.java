/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.support.v4.content;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ModernAsyncTaskTest {

    ModernAsyncTask mModernAsyncTask;

    /**
     * Test to ensure that onCancelled is always called, even if doInBackground throws an exception.
     *
     * @throws Throwable
     */
    @Test
    public void testCancellationWithException() throws Throwable {
        final CountDownLatch readyToCancel = new CountDownLatch(1);
        final CountDownLatch readyToThrow = new CountDownLatch(1);
        final CountDownLatch calledOnCancelled = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mModernAsyncTask = new ModernAsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] params) {
                        readyToCancel.countDown();
                        try {
                            readyToThrow.await();
                        } catch (InterruptedException e) {}
                        // This exception is expected to be caught and ignored
                        throw new RuntimeException();
                    }

                    @Override
                    protected void onCancelled(Object o) {
                        calledOnCancelled.countDown();
                    }
                };
            }
        });

        mModernAsyncTask.execute();
        if (!readyToCancel.await(5, TimeUnit.SECONDS)) {
            fail("Test failure: doInBackground did not run in time.");
        }
        mModernAsyncTask.cancel(false);
        readyToThrow.countDown();
        if (!calledOnCancelled.await(5, TimeUnit.SECONDS)) {
            fail("onCancelled not called!");
        }
    }
}
