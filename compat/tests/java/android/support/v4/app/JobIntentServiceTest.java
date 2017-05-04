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

package android.support.v4.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.BaseInstrumentationTestCase;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class JobIntentServiceTest extends BaseInstrumentationTestCase<TestSupportActivity> {
    static final String TAG = "JobIntentServiceTest";

    static final int JOB_ID = 0x1000;

    static final Object sLock = new Object();
    static CountDownLatch sReadyToRunLatch;
    static CountDownLatch sServiceFinishedLatch;

    static boolean sFinished;
    static ArrayList<Intent> sFinishedWork;
    static String sFinishedErrorMsg;

    public static final class TestIntentItem implements Parcelable {
        public final Intent intent;
        public final TestIntentItem[] subitems;
        public final Uri[] requireUrisGranted;
        public final Uri[] requireUrisNotGranted;

        public TestIntentItem(Intent intent) {
            this.intent = intent;
            subitems = null;
            requireUrisGranted = null;
            requireUrisNotGranted = null;
        }

        public TestIntentItem(Intent intent, TestIntentItem[] subitems) {
            this.intent = intent;
            this.subitems = subitems;
            intent.putExtra("subitems", subitems);
            requireUrisGranted = null;
            requireUrisNotGranted = null;
        }

        public TestIntentItem(Intent intent, Uri[] requireUrisGranted,
                Uri[] requireUrisNotGranted) {
            this.intent = intent;
            subitems = null;
            this.requireUrisGranted = requireUrisGranted;
            this.requireUrisNotGranted = requireUrisNotGranted;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(64);
            sb.append("TestIntentItem { ");
            sb.append(intent);
            sb.append(" }");
            return sb.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            intent.writeToParcel(parcel, flags);
            parcel.writeTypedArray(subitems, flags);
        }

        TestIntentItem(Parcel parcel) {
            intent = Intent.CREATOR.createFromParcel(parcel);
            subitems = parcel.createTypedArray(CREATOR);
            requireUrisGranted = null;
            requireUrisNotGranted = null;
        }

        public static final Parcelable.Creator<TestIntentItem> CREATOR =
                new Parcelable.Creator<TestIntentItem>() {

                    public TestIntentItem createFromParcel(Parcel source) {
                        return new TestIntentItem(source);
                    }

                    public TestIntentItem[] newArray(int size) {
                        return new TestIntentItem[size];
                    }
                };
    }

    static void initStatics() {
        synchronized (sLock) {
            sReadyToRunLatch = new CountDownLatch(1);
            sServiceFinishedLatch = new CountDownLatch(1);
            sFinished = false;
            sFinishedWork = null;
            sFinishedErrorMsg = null;
        }
    }

    static void allowServiceToRun() {
        sReadyToRunLatch.countDown();
    }

    static void finishServiceExecution(ArrayList<Intent> work, String errorMsg) {
        synchronized (sLock) {
            if (!sFinished) {
                sFinishedWork = work;
                sFinishedErrorMsg = errorMsg;
                sServiceFinishedLatch.countDown();
            }
        }
    }

    void waitServiceFinish() {
        try {
            if (!sServiceFinishedLatch.await(10, TimeUnit.SECONDS)) {
                fail("Timed out waiting for service to finish");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for service to finish: " + e);
        }
        synchronized (sLock) {
            if (sFinishedErrorMsg != null) {
                fail(sFinishedErrorMsg);
            }
        }
    }

    public static class TargetService extends JobIntentService {
        final ArrayList<Intent> mReceivedWork = new ArrayList<>();

        @Override
        public void onCreate() {
            super.onCreate();
            Log.i(TAG, "Creating: " + this);
            Log.i(TAG, "Waiting for ready to run...");
            try {
                if (!sReadyToRunLatch.await(10, TimeUnit.SECONDS)) {
                    finishServiceExecution(null, "Timeout waiting for ready");
                }
            } catch (InterruptedException e) {
                finishServiceExecution(null, "Interrupted waiting for ready: " + e);
            }
            Log.i(TAG, "Running!");
        }

        @Override
        protected void onHandleWork(@Nullable Intent intent) {
            Log.i(TAG, "Handling work: " + intent);
            mReceivedWork.add(intent);
            intent.setExtrasClassLoader(TestIntentItem.class.getClassLoader());
            Parcelable[] subitems = intent.getParcelableArrayExtra("subitems");
            if (subitems != null) {
                for (Parcelable pitem : subitems) {
                    JobIntentService.enqueueWork(this, TargetService.class,
                            JOB_ID, ((TestIntentItem) pitem).intent);
                }
            }
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "Destroying: " + this);
            finishServiceExecution(mReceivedWork, null);
            super.onDestroy();
        }
    }

    public JobIntentServiceTest() {
        super(TestSupportActivity.class);
    }

    private boolean intentEquals(Intent i1, Intent i2) {
        if (i1 == i2) {
            return true;
        }
        if (i1 == null || i2 == null) {
            return false;
        }
        return i1.filterEquals(i2);
    }

    private void compareIntents(TestIntentItem[] expected, ArrayList<Intent> received) {
        if (received == null) {
            fail("Didn't receive any expected work.");
        }
        ArrayList<TestIntentItem> expectedArray = new ArrayList<>();
        for (int i = 0; i < expected.length; i++) {
            expectedArray.add(expected[i]);
        }

        ComponentName serviceComp = new ComponentName(mActivityTestRule.getActivity(),
                TargetService.class.getName());

        for (int i = 0; i < received.size(); i++) {
            Intent r = received.get(i);
            if (i < expected.length && expected[i].subitems != null) {
                TestIntentItem[] sub = expected[i].subitems;
                for (int j = 0; j < sub.length; j++) {
                    expectedArray.add(sub[j]);
                }
            }
            if (i >= expectedArray.size()) {
                fail("Received more than " + expected.length + " work items, first extra is "
                        + r);
            }
            if (r.getComponent() != null) {
                // Intents we get back from the compat service will have a component... make
                // sure that is correct, and then erase it so the intentEquals() will pass.
                assertEquals(serviceComp, r.getComponent());
                r.setComponent(null);
            }
            if (!intentEquals(r, expectedArray.get(i).intent)) {
                fail("Received intent #" + i + " " + r + " but expected " + expected[i]);
            }
        }
        if (received.size() < expected.length) {
            fail("Received only " + received.size() + " work items, but expected "
                    + expected.length);
        }
    }

    /**
     * Test simple case of enqueueing one piece of work.
     */
    @MediumTest
    @Test
    public void testEnqueueOne() throws Throwable {
        initStatics();

        TestIntentItem[] items = new TestIntentItem[] {
                new TestIntentItem(new Intent("FIRST")),
        };

        for (TestIntentItem item : items) {
            JobIntentService.enqueueWork(mActivityTestRule.getActivity(), TargetService.class,
                    JOB_ID, item.intent);
        }
        allowServiceToRun();

        waitServiceFinish();
        compareIntents(items, sFinishedWork);
    }

    /**
     * Test case of enqueueing multiple pieces of work.
     */
    @MediumTest
    @Test
    public void testEnqueueMultiple() throws Throwable {
        initStatics();

        TestIntentItem[] items = new TestIntentItem[] {
                new TestIntentItem(new Intent("FIRST")),
                new TestIntentItem(new Intent("SECOND")),
                new TestIntentItem(new Intent("THIRD")),
                new TestIntentItem(new Intent("FOURTH")),
        };

        for (TestIntentItem item : items) {
            JobIntentService.enqueueWork(mActivityTestRule.getActivity(), TargetService.class,
                    JOB_ID, item.intent);
        }
        allowServiceToRun();

        waitServiceFinish();
        compareIntents(items, sFinishedWork);
    }

    /**
     * Test case of enqueueing multiple pieces of work.
     */
    @MediumTest
    @Test
    public void testEnqueueSubWork() throws Throwable {
        initStatics();

        TestIntentItem[] items = new TestIntentItem[] {
                new TestIntentItem(new Intent("FIRST")),
                new TestIntentItem(new Intent("SECOND")),
                new TestIntentItem(new Intent("THIRD"), new TestIntentItem[] {
                        new TestIntentItem(new Intent("FIFTH")),
                        new TestIntentItem(new Intent("SIXTH")),
                        new TestIntentItem(new Intent("SEVENTH")),
                        new TestIntentItem(new Intent("EIGTH")),
                }),
                new TestIntentItem(new Intent("FOURTH")),
        };

        for (TestIntentItem item : items) {
            JobIntentService.enqueueWork(mActivityTestRule.getActivity(), TargetService.class,
                    JOB_ID, item.intent);
        }
        allowServiceToRun();

        waitServiceFinish();
        compareIntents(items, sFinishedWork);
    }
}
