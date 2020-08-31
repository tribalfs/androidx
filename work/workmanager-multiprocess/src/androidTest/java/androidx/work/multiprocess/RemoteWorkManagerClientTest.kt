/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.work.multiprocess

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.work.impl.utils.futures.SettableFuture
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class RemoteWorkManagerClientTest {

    private lateinit var mContext: Context
    private lateinit var mExecutor: Executor
    private lateinit var mClient: RemoteWorkManagerClient

    @Before
    fun setUp() {
        mContext = mock(Context::class.java)
        `when`(mContext.applicationContext).thenReturn(mContext)
        mExecutor = Executor {
            it.run()
        }
        mClient = spy(RemoteWorkManagerClient(mContext, mExecutor))
    }

    @Test
    @MediumTest
    fun failGracefullyWhenBindFails() {
        if (Build.VERSION.SDK_INT <= 26) {
            // Exclude <= API 26, from tests because it causes a SIGSEGV.
            return
        }

        `when`(
            mContext.bindService(
                any(Intent::class.java), any(ServiceConnection::class.java),
                anyInt()
            )
        ).thenReturn(false)
        val intent = mock(Intent::class.java)
        var exception: Throwable? = null
        try {
            mClient.getSession(intent).get()
        } catch (throwable: Throwable) {
            exception = throwable
        }
        assertNotNull(exception)
        val message = exception?.cause?.message ?: ""
        assertTrue(message.contains("Unable to bind to service"))
    }

    @Test
    @MediumTest
    fun cleanUpWhenDispatcherFails() {
        val binder = mock(IBinder::class.java)
        val remoteDispatcher = mock(RemoteWorkManagerClient.RemoteDispatcher::class.java)
        val remoteStub = mock(IWorkManagerImpl::class.java)
        val callback = spy(RemoteCallback())
        val message = "Something bad happened"
        `when`(remoteDispatcher.execute(remoteStub, callback)).thenThrow(RuntimeException(message))
        `when`(remoteStub.asBinder()).thenReturn(binder)
        val session = SettableFuture.create<IWorkManagerImpl>()
        session.set(remoteStub)
        var exception: Throwable? = null
        try {
            mClient.execute(session, remoteDispatcher, callback).get()
        } catch (throwable: Throwable) {
            exception = throwable
        }
        assertNotNull(exception)
        verify(callback).onFailure(message)
        verify(mClient, never()).cleanUp()
    }

    @Test
    @MediumTest
    fun cleanUpWhenSessionIsInvalid() {
        val remoteDispatcher = mock(RemoteWorkManagerClient.RemoteDispatcher::class.java)
        val callback = spy(RemoteCallback())
        val session = SettableFuture.create<IWorkManagerImpl>()
        session.setException(RuntimeException("Something bad happened"))
        var exception: Throwable? = null
        try {
            mClient.execute(session, remoteDispatcher, callback).get()
        } catch (throwable: Throwable) {
            exception = throwable
        }
        assertNotNull(exception)
        verify(callback).onFailure(anyString())
        verify(mClient).cleanUp()
    }

    @Test
    @MediumTest
    fun cleanUpOnSuccessfulDispatch() {
        val binder = mock(IBinder::class.java)
        val remoteDispatcher = RemoteWorkManagerClient.RemoteDispatcher { _, callback ->
            callback.onSuccess(ByteArray(0))
        }
        val remoteStub = mock(IWorkManagerImpl::class.java)
        val callback = spy(RemoteCallback())
        `when`(remoteStub.asBinder()).thenReturn(binder)
        val session = SettableFuture.create<IWorkManagerImpl>()
        session.set(remoteStub)
        var exception: Throwable? = null
        try {
            mClient.execute(session, remoteDispatcher, callback).get()
        } catch (throwable: Throwable) {
            exception = throwable
        }
        assertNull(exception)
        verify(callback).onSuccess(any())
        verify(mClient, never()).cleanUp()
    }
}
