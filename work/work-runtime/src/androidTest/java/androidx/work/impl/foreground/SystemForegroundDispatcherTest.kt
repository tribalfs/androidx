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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.impl.Processor
import androidx.work.impl.Scheduler
import androidx.work.impl.WorkDatabase
import androidx.work.impl.model.WorkGenerationalId
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.StartStopToken
import androidx.work.impl.constraints.WorkConstraintsCallback
import androidx.work.impl.constraints.WorkConstraintsTracker
import androidx.work.impl.foreground.SystemForegroundDispatcher.createCancelWorkIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createNotifyIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createStartForegroundIntent
import androidx.work.impl.foreground.SystemForegroundDispatcher.createStopForegroundIntent
import androidx.work.impl.utils.StopWorkRunnable
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.InstantWorkTaskExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.worker.TestWorker
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
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
        workDatabase = WorkDatabase.create(context, taskExecutor.serialTaskExecutor, true)
        processor = spy(Processor(context, config, taskExecutor, workDatabase))
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
        tracker = mock(WorkConstraintsTracker::class.java)
        // Initialize dispatcher
        dispatcherCallback = mock(SystemForegroundDispatcher.Callback::class.java)
        dispatcher = spy(SystemForegroundDispatcher(context, workManager, tracker))
        dispatcher.setCallback(dispatcherCallback)
    }

    @Test
    fun testStartForeground_noInteractions_workSpecHasNoConstraints() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java).build()
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.stringId, 0), metadata)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1))
            .startForeground(eq(notificationId), eq(0), any<Notification>())
        verifyZeroInteractions(tracker)
    }

    @Test
    fun testStartForeground_trackConstraints_workSpecHasConstraints() {
        val request = OneTimeWorkRequest.Builder(NeverResolvedWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        processor.startWork(StartStopToken(WorkGenerationalId(request.stringId, 0)))
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.stringId, 0), metadata)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1))
            .startForeground(eq(notificationId), eq(0), any<Notification>())
        verify(tracker, times(1)).replace(setOf(request.workSpec))
    }

    @Test
    fun testStopForeground() {
        // The Foreground Service now calls handleStop() directly without the need for an
        // additional startService().
        dispatcher.handleStop(createStopForegroundIntent(context))
        verify(dispatcherCallback, times(1)).stop()
    }

    @Test
    fun testStartForeground() {
        val workSpecId = WorkGenerationalId("workSpecId", 0)
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createNotifyIntent(context, workSpecId, metadata)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1))
            .startForeground(eq(notificationId), eq(0), any<Notification>())
    }

    @Test
    fun testNotify() {
        val workSpecId = WorkGenerationalId("workSpecId", 0)
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createNotifyIntent(context, workSpecId, metadata)
        dispatcher.mCurrentForegroundId = WorkGenerationalId("anotherWorkSpecId", 0)
        dispatcher.onStartCommand(intent)
        verify(dispatcherCallback, times(1))
            .notify(eq(notificationId), any<Notification>())
    }

    @Test
    fun testPromoteWorkSpecForStartForeground() {
        val firstWorkSpecId = WorkGenerationalId("first", 0)
        val firstId = 1
        val notification = mock(Notification::class.java)
        val firstInfo = ForegroundInfo(firstId, notification)
        val firstIntent = createNotifyIntent(context, firstWorkSpecId, firstInfo)

        val secondWorkSpecId = WorkGenerationalId("second", 0)
        val secondId = 2
        val secondInfo = ForegroundInfo(secondId, notification)
        val secondIntent = createNotifyIntent(context, secondWorkSpecId, secondInfo)

        dispatcher.onStartCommand(firstIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .startForeground(eq(firstId), eq(0), any<Notification>())

        dispatcher.onStartCommand(secondIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(2))

        dispatcher.onExecuted(firstWorkSpecId, false)
        verify(dispatcherCallback, times(1))
            .startForeground(eq(secondId), eq(0), any<Notification>())
        verify(dispatcherCallback, times(1))
            .cancelNotification(secondId)
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(1))
        reset(dispatcherCallback)
        dispatcher.onExecuted(secondWorkSpecId, false)
        verify(dispatcherCallback, times(1))
            .cancelNotification(secondId)
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(0))
    }

    @Test
    fun promoteWorkSpecForStartForeground2() {
        val firstWorkSpecId = WorkGenerationalId("first", 0)
        val firstId = 1
        val notification = mock(Notification::class.java)
        val firstInfo = ForegroundInfo(firstId, notification)
        val firstIntent = createNotifyIntent(context, firstWorkSpecId, firstInfo)

        val secondWorkSpecId = WorkGenerationalId("second", 0)
        val secondId = 2
        val secondInfo = ForegroundInfo(secondId, notification)
        val secondIntent = createNotifyIntent(context, secondWorkSpecId, secondInfo)

        val thirdWorkSpecId = WorkGenerationalId("third", 0)
        val thirdId = 3
        val thirdInfo = ForegroundInfo(thirdId, notification)
        val thirdIntent = createNotifyIntent(context, thirdWorkSpecId, thirdInfo)

        dispatcher.onStartCommand(firstIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .startForeground(eq(firstId), eq(0), any<Notification>())

        dispatcher.onStartCommand(secondIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(2))

        dispatcher.onStartCommand(thirdIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(3))

        dispatcher.onExecuted(firstWorkSpecId, false)
        verify(dispatcherCallback, times(1))
            .startForeground(eq(thirdId), eq(0), any<Notification>())
        verify(dispatcherCallback, times(1))
            .cancelNotification(thirdId)
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(2))
    }

    @Test
    fun promoteWorkSpecForStartForeground3() {
        val firstWorkSpecId = WorkGenerationalId("first", 0)
        val firstId = 1
        val notification = mock(Notification::class.java)
        val firstInfo = ForegroundInfo(firstId, notification)
        val firstIntent = createNotifyIntent(context, firstWorkSpecId, firstInfo)

        val secondWorkSpecId = WorkGenerationalId("second", 0)
        val secondId = 2
        val secondInfo = ForegroundInfo(secondId, notification)
        val secondIntent = createNotifyIntent(context, secondWorkSpecId, secondInfo)

        val thirdWorkSpecId = WorkGenerationalId("third", 0)
        val thirdId = 3
        val thirdInfo = ForegroundInfo(thirdId, notification)
        val thirdIntent = createNotifyIntent(context, thirdWorkSpecId, thirdInfo)

        dispatcher.onStartCommand(firstIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .startForeground(eq(firstId), eq(0), any<Notification>())

        dispatcher.onStartCommand(secondIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(2))

        dispatcher.onStartCommand(thirdIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(3))

        dispatcher.onExecuted(secondWorkSpecId, false)
        verify(dispatcherCallback, times(1))
            .cancelNotification(eq(secondId))
        assertThat(dispatcher.mForegroundInfoById.count(), `is`(2))
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun testUpdateNotificationWithDifferentForegroundServiceType() {
        val firstWorkSpecId = WorkGenerationalId("first", 0)
        val firstId = 1
        val notification = mock(Notification::class.java)
        val firstInfo =
            ForegroundInfo(firstId, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        val firstIntent = createNotifyIntent(context, firstWorkSpecId, firstInfo)

        val secondWorkSpecId = WorkGenerationalId("second", 0)
        val secondId = 2
        val secondInfo = ForegroundInfo(secondId, notification, FOREGROUND_SERVICE_TYPE_LOCATION)
        val secondIntent = createNotifyIntent(context, secondWorkSpecId, secondInfo)

        dispatcher.onStartCommand(firstIntent)
        verify(dispatcherCallback, times(1))
            .startForeground(
                eq(firstId),
                eq(FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE),
                any<Notification>()
            )

        dispatcher.onStartCommand(secondIntent)
        assertThat(dispatcher.mCurrentForegroundId, `is`(firstWorkSpecId))
        verify(dispatcherCallback, times(1))
            .notify(eq(secondId), any<Notification>())

        val expectedNotificationType =
            FOREGROUND_SERVICE_TYPE_LOCATION or FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

        verify(dispatcherCallback, times(1))
            .startForeground(
                eq(firstId),
                eq(expectedNotificationType),
                any<Notification>()
            )
    }

    @Test
    fun testStartForeground_trackConstraints_constraintsUnMet() {
        val request = OneTimeWorkRequest.Builder(NeverResolvedWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        processor.startWork(StartStopToken(WorkGenerationalId(request.stringId, 0)))
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.stringId, 0), metadata)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
        dispatcher.onAllConstraintsNotMet(listOf(request.workSpec))
        verify(workManager, times(1)).stopForegroundWork(eq(
            WorkGenerationalId(request.workSpec.id, 0)
        ))
    }

    @Test
    fun testCancelForegroundWork() {
        val request = OneTimeWorkRequest.Builder(NeverResolvedWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        processor.startWork(StartStopToken(WorkGenerationalId(request.stringId, 0)))
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.workSpec.id, 0), metadata)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
        val stopIntent = createCancelWorkIntent(context, request.stringId)
        dispatcher.onStartCommand(stopIntent)
        verify(workManager, times(1)).cancelWorkById(eq(UUID.fromString(request.workSpec.id)))
        assertThat(processor.hasWork(), `is`(false))
    }

    @Test
    fun testStopForegroundWork() {
        val request = OneTimeWorkRequest.Builder(TestWorker::class.java)
            .setInitialState(WorkInfo.State.RUNNING)
            .build()

        `when`(processor.isEnqueuedInForeground(eq(request.stringId))).thenReturn(true)
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.stringId, 0), metadata)
        dispatcher.onStartCommand(intent)
        val stopWorkRunnable = StopWorkRunnable(
            workManager, StartStopToken(WorkGenerationalId(request.stringId, 0)), false
        )
        stopWorkRunnable.run()
        val state = workDatabase.workSpecDao().getState(request.stringId)
        assertThat(state, `is`(WorkInfo.State.RUNNING))
        val stopAndCancelIntent = createCancelWorkIntent(context, request.stringId)
        dispatcher.onStartCommand(stopAndCancelIntent)
        verify(workManager, times(1)).cancelWorkById(eq(UUID.fromString(request.workSpec.id)))
        assertThat(processor.hasWork(), `is`(false))
    }

    @Test
    fun testUseRunningWork() {
        val request = OneTimeWorkRequest.Builder(NeverResolvedWorker::class.java)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        workDatabase.workSpecDao().insertWorkSpec(request.workSpec)
        processor.startWork(StartStopToken(WorkGenerationalId(request.stringId, 0)))
        val updatedRequest = OneTimeWorkRequest.Builder(NeverResolvedWorker::class.java)
            .setId(request.id)
            .build()
        workDatabase.workSpecDao().updateWorkSpec(updatedRequest.workSpec)
        val notificationId = 1
        val notification = mock(Notification::class.java)
        val metadata = ForegroundInfo(notificationId, notification)
        val intent = createStartForegroundIntent(context,
            WorkGenerationalId(request.stringId, 0), metadata)
        dispatcher.onStartCommand(intent)
        verify(tracker, times(1)).replace(setOf(request.workSpec))
    }
}

class NeverResolvedWorker(
    context: Context,
    workerParams: WorkerParameters
) : ListenableWorker(context, workerParams) {
    override fun startWork(): ListenableFuture<Result> {
        return SettableFuture.create()
    }
}
