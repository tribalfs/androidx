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

package androidx.work

import android.content.Context
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.work.impl.WorkDatabase
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
@SmallTest
class CoroutineWorkerTest {

    private lateinit var context: Context
    private lateinit var configuration: Configuration
    private lateinit var database: WorkDatabase
    private lateinit var workManagerImpl: WorkManagerImpl

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance()
            .setDelegate(object : androidx.arch.core.executor.TaskExecutor() {
                override fun executeOnDiskIO(runnable: Runnable) {
                    runnable.run()
                }

                override fun postToMainThread(runnable: Runnable) {
                    runnable.run()
                }

                override fun isMainThread(): Boolean {
                    return true
                }
            })

        context = ApplicationProvider.getApplicationContext() as android.content.Context
        configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        workManagerImpl = WorkManagerImpl(context, configuration,
            InstantWorkTaskExecutor()
        )
        WorkManagerImpl.setDelegate(workManagerImpl)
        database = workManagerImpl.workDatabase
    }

    @After
    fun tearDown() {
        WorkManagerImpl.setDelegate(null)
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun testCoroutineWorker_basicUsage() {
        val workerFactory = WorkerFactory.getDefaultWorkerFactory()
        val worker = workerFactory.createWorkerWithDefaultFallback(
            context,
            SynchronousCoroutineWorker::class.java.name,
            WorkerParameters(
                UUID.randomUUID(),
                Data.EMPTY,
                emptyList(),
                WorkerParameters.RuntimeExtras(),
                1,
                configuration.executor,
                workManagerImpl.workTaskExecutor,
                workerFactory)) as SynchronousCoroutineWorker

        assertThat(worker.job.isCompleted, `is`(false))

        val future = worker.startWork()
        val result = future.get()

        assertThat(future.isDone, `is`(true))
        assertThat(future.isCancelled, `is`(false))
        assertThat(result, `is`(instanceOf(ListenableWorker.Result.Success::class.java)))
        assertThat((result as ListenableWorker.Result.Success).outputData.getLong(
            "output", 0L),
            `is`(999L))
    }

    @Test
    fun testCoroutineWorker_cancellingFutureCancelsJob() {
        val workerFactory = WorkerFactory.getDefaultWorkerFactory()
        val worker = workerFactory.createWorkerWithDefaultFallback(
            context,
            SynchronousCoroutineWorker::class.java.name,
            WorkerParameters(
                UUID.randomUUID(),
                Data.EMPTY,
                emptyList(),
                WorkerParameters.RuntimeExtras(),
                1,
                configuration.executor,
                workManagerImpl.workTaskExecutor,
                workerFactory)) as SynchronousCoroutineWorker

        assertThat(worker.job.isCancelled, `is`(false))
        worker.future.cancel(true)
        assertThat(worker.job.isCancelled, `is`(true))
    }

    class SynchronousExecutor : Executor {

        override fun execute(command: Runnable) {
            command.run()
        }
    }

    class InstantWorkTaskExecutor : TaskExecutor {

        private val mSynchronousExecutor = SynchronousExecutor()

        override fun postToMainThread(runnable: Runnable) {
            runnable.run()
        }

        override fun getMainThreadExecutor(): Executor {
            return mSynchronousExecutor
        }

        override fun executeOnBackgroundThread(runnable: Runnable) {
            runnable.run()
        }

        override fun getBackgroundExecutor(): Executor {
            return mSynchronousExecutor
        }
    }

    class SynchronousCoroutineWorker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            return Result.success(workDataOf("output" to 999L))
        }

        override val coroutineContext = SynchronousExecutor().asCoroutineDispatcher()
    }
}