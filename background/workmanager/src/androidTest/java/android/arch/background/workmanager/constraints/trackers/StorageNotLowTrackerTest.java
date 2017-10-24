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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.arch.background.workmanager.constraints.listeners.StorageNotLowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StorageNotLowTrackerTest {

    private StorageNotLowTracker mTracker;
    private StorageNotLowListener mListener;

    @Before
    public void setUp() {
        mTracker = new StorageNotLowTracker(InstrumentationRegistry.getTargetContext());
        mListener = mock(StorageNotLowListener.class);
        mTracker.mListeners.add(mListener);  // Add it silently so no broadcasts trigger.
    }

    @After
    public void shutDown() {
        mTracker.mListeners.remove(mListener);
    }

    @Test
    public void testGetIntentFilter() {
        IntentFilter intentFilter = mTracker.getIntentFilter();
        assertThat(intentFilter.hasAction(Intent.ACTION_DEVICE_STORAGE_OK), is(true));
        assertThat(intentFilter.hasAction(Intent.ACTION_DEVICE_STORAGE_LOW), is(true));
        assertThat(intentFilter.countActions(), is(2));
    }

    @Test
    public void testOnBroadcastReceive_invalidIntentAction_doesNotNotifyListeners() {
        mTracker.onBroadcastReceive(
                InstrumentationRegistry.getTargetContext(),
                new Intent("INVALID"));
        verify(mListener, never()).setStorageNotLow(anyBoolean());
    }

    @Test
    public void testOnBroadcastReceive_notifiesListeners() {
        mTracker.onBroadcastReceive(
                InstrumentationRegistry.getTargetContext(),
                new Intent(Intent.ACTION_DEVICE_STORAGE_OK));
        verify(mListener).setStorageNotLow(true);
        mTracker.onBroadcastReceive(
                InstrumentationRegistry.getTargetContext(),
                new Intent(Intent.ACTION_DEVICE_STORAGE_LOW));
        verify(mListener).setStorageNotLow(false);
    }
}
