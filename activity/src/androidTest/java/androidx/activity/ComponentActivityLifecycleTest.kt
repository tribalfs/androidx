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

package androidx.activity

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.test.filters.MediumTest
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(AndroidJUnit4::class)
class ComponentActivityLifecycleTest {

    @get:Rule
    val activityRule = ActivityTestRule(LifecycleComponentActivity::class.java, false, false)

    @Test
    @Throws(Throwable::class)
    fun testLifecycleObserver() {
        activityRule.launchActivity(null)
        val activity = activityRule.activity
        val activityCallbackLifecycleOwner = activity.activityCallbackLifecycleOwner
        val lifecycleObserver = activity.lifecycleObserver
        val countDownLatch = activity.destroyCountDownLatch
        activityRule.finishActivity()
        countDownLatch.await(1, TimeUnit.SECONDS)

        // The Activity's lifecycle callbacks should fire first,
        // followed by the activity's lifecycle observers
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_CREATE)
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_CREATE)
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_START)
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_START)
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_RESUME)
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_RESUME)
        // Now the order reverses as things unwind
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_PAUSE)
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_PAUSE)
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_STOP)
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_STOP)
        verify(lifecycleObserver)
                .onStateChanged(activity, Lifecycle.Event.ON_DESTROY)
        verify(lifecycleObserver)
                .onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_DESTROY)
        verifyNoMoreInteractions(lifecycleObserver)
    }
}

class LifecycleComponentActivity : ComponentActivity() {
    val activityCallbackLifecycleOwner: LifecycleOwner = mock(LifecycleOwner::class.java)
    val lifecycleObserver: LifecycleEventObserver = mock(LifecycleEventObserver::class.java)
    val destroyCountDownLatch = CountDownLatch(1)

    init {
        lifecycle.addObserver(lifecycleObserver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_CREATE)
    }

    override fun onStart() {
        super.onStart()
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_RESUME)
    }

    override fun onPause() {
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleObserver.onStateChanged(activityCallbackLifecycleOwner, Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        destroyCountDownLatch.countDown()
    }
}
