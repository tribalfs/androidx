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

package androidx.work.impl.foreground

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.Processor
import androidx.work.impl.Scheduler
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.constraints.WorkConstraintsCallback
import androidx.work.impl.constraints.WorkConstraintsTracker
import androidx.work.impl.foreground.SystemForegroundDispatcher.NOTIFICATION_ID
import androidx.work.impl.foreground.SystemForegroundDispatcher.createNotifyIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createStartForegroundIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createStopForegroundIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createCancelWorkIntent
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.impl.utils.taskexecutor.InstantWorkTaskExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.worker.TestWorker
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@MediumTest
class SystemForegroundDispatcherTest {

    private lateinit var context: Context
    private lateinit var config: Configuration
    private lateinit var taskExecutor: TaskExecutor
    private lateinit var workManager: WorkManagerImpl
    private lateinit var workDatabase: WorkDatabase
    private lateinit var processor: Processor
    private lateinit var tracker: WorkConstraintsTracker
    private lateinit var constraintsCallback: WorkConstraintsCallback
    private lateinit var dispatcher: SystemForegroundDispatcher
    private lateinit var dispatcherCallback: SystemForegroundDispatcher.Callback

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        taskExecutor = InstantWorkTaskExecutor()
        val scheduler = mock(Scheduler::class.java)
        workDatabase = WorkDatabase.create(context, taskExecutor.backgroundExecutor, true)
        processor = Processor(context, config, taskExecutor, workDatabase, listOf(scheduler))
        workManager = spy(
            WorkManagerImpl(
                context,
                config,
                taskExecutor,
                workDatabase,
                listOf(scheduler),
                processor
            )
        )
        workDatabase = workManager.workDatabase
        // Initialize WorkConstraintsTracker
        constraintsCallback = mock(WorkConstraintsCallback::class.java)
        tracker = spy(WorkConstraintsTracker(context, taskExecutor, constraintsCallback))
        // Initialize dispatcher
        dispatcherCallback = mock(SystemForegroundDispatcher.Callback::class.java)
        dispatcher = spy(SystemForegroundDispatcher(context, workManager, tracker))
        dispatcher.setCallback(dispatcherCallback)
    }

    @Test
    fun testStartForeground_noInteractions_workSpecHasNoConstraints() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        val intent = createStartForegroundIntent(context, request.stringId)
        dispatcher.onStartCommand(intent)
        verifyZeroInteractions(tracker)
    }

    @Test
    fun testStartForeground_trackConstraints_workSpecHasConstraints() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)

        val intent = createStartForegroundIntent(context, request.stringId)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
    }

    @Test
    fun testStopForeground() {
        val intent = createStopForegroundIntent(context)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1)).stop()
    }

    @Test
    fun testHandleNotify() {
        val workSpecId = "workSpecId"
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notification)
        val intent = createNotifyIntent(context, workSpecId, metadata)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1))
            .notify(eq(NOTIFICATION_ID), eq(0), eq(workSpecId), any<Notification>())
    }

    @Test
    fun testStartForeground_trackConstraints_constraintsUnMet() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)

        val intent = createStartForegroundIntent(context, request.stringId)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
        dispatcher.onAllConstraintsNotMet(listOf(request.workSpec.id))
        verify(workManager, times(1)).stopForegroundWork(eq(request.workSpec.id))
    }

    @Test
    fun testCancelForegroundWork() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)

        val intent = createStartForegroundIntent(context, request.stringId)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
        val stopIntent = createCancelWorkIntent(context, request.stringId)
        dispatcher.onStartCommand(stopIntent)
        verify(workManager, times(1)).cancelWorkById(eq(UUID.fromString(request.workSpec.id)))
        val stopForegroundIntent = createStopForegroundIntent(context)
        verify(dispatcher, times(1)).onStartCommand(stopForegroundIntent)
    }
}
