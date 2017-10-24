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
package android.arch.background.workmanager.constraints.trackers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import android.arch.background.workmanager.constraints.listeners.ConstraintListener;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ConstraintTrackerTest {

    private TestConstraintTracker mTracker;

    @Before
    public void setUp() {
        mTracker = new TestConstraintTracker(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() {
        try {
            mTracker.stopTracking();
        } catch (IllegalArgumentException e) {
            // Ignore any exceptions if the receiver isn't registered.
        }
    }

    @Test
    public void testStartTracking_setsInitialState() {
        ConstraintListener constraintListener = mock(ConstraintListener.class);
        mTracker.addListener(constraintListener);
        assertThat(mTracker.mSetupInitialState, is(true));
    }

    @Test
    public void testTracking_registersOnSizeEqualsOne() {
        ConstraintListener constraintListener1 = mock(ConstraintListener.class);
        mTracker.addListener(constraintListener1);
        assertThat(mTracker.mIsTracking, is(true));
        assertThat(mTracker.mStartTrackingCount, is(1));

        ConstraintListener constraintListener2 = mock(ConstraintListener.class);
        mTracker.addListener(constraintListener2);
        assertThat(mTracker.mIsTracking, is(true));
        assertThat(mTracker.mStartTrackingCount, is(1));
    }

    @Test
    public void testTracking_unregistersOnSizeEqualsZero() {
        ConstraintListener constraintListener1 = mock(ConstraintListener.class);
        mTracker.addListener(constraintListener1);
        ConstraintListener constraintListener2 = mock(ConstraintListener.class);
        mTracker.addListener(constraintListener2);

        mTracker.removeListener(constraintListener1);
        assertThat(mTracker.mIsTracking, is(true));
        assertThat(mTracker.mStopTrackingCount, is(0));
        mTracker.removeListener(constraintListener2);
        assertThat(mTracker.mIsTracking, is(false));
        assertThat(mTracker.mStopTrackingCount, is(1));
    }

    @Test
    public void testTracking_doesNotAddListenerTwice() {
        ConstraintListener constraintListener = mock(ConstraintListener.class);
        for (int i = 0; i < 2; ++i) {
            mTracker.addListener(constraintListener);
            assertThat(mTracker.mListeners.size(), is(1));
            assertThat(mTracker.mListeners, contains(constraintListener));
        }
    }

    private static class TestConstraintTracker extends ConstraintTracker<ConstraintListener> {

        boolean mSetupInitialState;
        boolean mIsTracking;
        int mStartTrackingCount;
        int mStopTrackingCount;

        TestConstraintTracker(Context context) {
            super(context);
        }

        @Override
        public void setUpInitialState(ConstraintListener constraintListener) {
            mSetupInitialState = true;
        }

        @Override
        public void startTracking() {
            mIsTracking = true;
            mStartTrackingCount++;
        }

        @Override
        public void stopTracking() {
            mIsTracking = false;
            mStopTrackingCount++;
        }
    }
}
