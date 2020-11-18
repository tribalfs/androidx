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

package androidx.room.integration.testapp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.CountingTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.testing.TestLifecycleOwner;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.integration.testapp.TestDatabase;
import androidx.room.integration.testapp.dao.UserDao;
import androidx.room.integration.testapp.vo.User;
import androidx.room.util.SneakyThrow;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoClosingRoomOpenHelperTest {
    @Rule
    public CountingTaskExecutorRule mExecutorRule = new CountingTaskExecutorRule();
    private UserDao mUserDao;
    private TestDatabase mDb;
    private final DatabaseCallbackTest.TestDatabaseCallback mCallback =
            new DatabaseCallbackTest.TestDatabaseCallback();

    @Before
    public void createDb() throws TimeoutException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        mDb = Room.databaseBuilder(context, TestDatabase.class, "testDb")
                .setAutoCloseTimeout(10, TimeUnit.MILLISECONDS)
                .addCallback(mCallback).build();
        mUserDao = mDb.getUserDao();
        drain();
    }

    @After
    public void cleanUp() throws Exception {
        mDb.clearAllTables();
    }

    @Test
    @MediumTest
    public void inactiveConnection_shouldAutoClose() throws Exception {
        assertFalse(mCallback.mOpened);
        User user = TestUtil.createUser(1);
        user.setName("bob");
        mUserDao.insert(user);
        assertTrue(mCallback.mOpened);
        assertTrue(mDb.isOpen());
        Thread.sleep(100);
        assertFalse(mDb.isOpen());

        User readUser = mUserDao.load(1);
        assertEquals(readUser.getName(), user.getName());
    }

    @Test
    @MediumTest
    public void slowTransaction_keepsDbAlive() throws Exception {
        assertFalse(mCallback.mOpened);

        User user = TestUtil.createUser(1);
        user.setName("bob");
        mUserDao.insert(user);
        assertTrue(mCallback.mOpened);
        Thread.sleep(30);
        mUserDao.load(1);
        assertTrue(mDb.isOpen());

        mDb.runInTransaction(
                () -> {
                    try {
                        Thread.sleep(100);
                        assertTrue(mDb.isOpen());
                    } catch (InterruptedException e) {
                        SneakyThrow.reThrow(e);
                    }
                }
        );

        assertTrue(mDb.isOpen());
        Thread.sleep(100);
        assertFalse(mDb.isOpen());
    }

    @Test
    @MediumTest
    public void slowCursorClosing_keepsDbAlive() throws Exception {
        assertFalse(mCallback.mOpened);
        User user = TestUtil.createUser(1);
        user.setName("bob");
        mUserDao.insert(user);
        assertTrue(mCallback.mOpened);
        mUserDao.load(1);
        assertTrue(mDb.isOpen());

        Cursor cursor = mDb.query("select * from user", null);

        assertTrue(mDb.isOpen());
        Thread.sleep(100);
        assertTrue(mDb.isOpen());
        cursor.close();

        Thread.sleep(100);
        assertFalse(mDb.isOpen());
    }

    @Test
    @MediumTest
    public void autoClosedConnection_canReopen() throws Exception {
        User user1 = TestUtil.createUser(1);
        user1.setName("bob");
        mUserDao.insert(user1);

        assertTrue(mDb.isOpen());
        Thread.sleep(100);
        assertFalse(mDb.isOpen());

        User user2 = TestUtil.createUser(2);
        user2.setName("bob2");
        mUserDao.insert(user2);
        assertTrue(mDb.isOpen());
        Thread.sleep(100);
        assertFalse(mDb.isOpen());
    }

    @Test
    @MediumTest
    public void liveDataTriggers_shouldApplyOnReopen() throws Exception {
        LiveData<Boolean> adminLiveData = mUserDao.isAdminLiveData(1);

        final TestLifecycleOwner lifecycleOwner = new TestLifecycleOwner();
        final TestObserver<Boolean> observer = new AutoClosingRoomOpenHelperTest
                .MyTestObserver<>();
        TestUtil.observeOnMainThread(adminLiveData, lifecycleOwner, observer);
        assertNull(observer.get());

        User user = TestUtil.createUser(1);
        user.setAdmin(true);
        mUserDao.insert(user);

        assertNotNull(observer.get());
        assertTrue(observer.get());

        user.setAdmin(false);
        mUserDao.insertOrReplace(user);
        assertNotNull(observer.get());
        assertFalse(observer.get());

        Thread.sleep(100);
        assertFalse(mDb.isOpen());

        user.setAdmin(true);
        mUserDao.insertOrReplace(user);
        assertNotNull(observer.get());
        assertTrue(observer.get());
    }

    @Test
    @MediumTest
    public void testCanExecSqlInCallback() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        mDb = Room.databaseBuilder(context, TestDatabase.class, "testDb")
                        .setAutoCloseTimeout(10, TimeUnit.MILLISECONDS)
                        .addCallback(new ExecSqlInCallback())
                        .build();

        mDb.getUserDao().insert(TestUtil.createUser(1));
    }

    @Test
    @MediumTest
    public void invalidationObserver_isCalledOnEachInvalidation()
            throws TimeoutException, InterruptedException {
        AtomicInteger invalidationCount = new AtomicInteger(0);

        UserTableObserver userTableObserver =
                new UserTableObserver(invalidationCount::getAndIncrement);

        mDb.getInvalidationTracker().addObserver(userTableObserver);

        mUserDao.insert(TestUtil.createUser(1));

        drain();
        assertEquals(1, invalidationCount.get());

        User user1 = TestUtil.createUser(1);
        user1.setAge(123);
        mUserDao.insertOrReplace(user1);

        drain();
        assertEquals(2, invalidationCount.get());

        Thread.sleep(15);
        assertFalse(mDb.isOpen());

        mUserDao.insert(TestUtil.createUser(2));

        drain();
        assertEquals(3, invalidationCount.get());
    }

    @Test
    @MediumTest
    public void invalidationObserver_canRequeryDb() throws TimeoutException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();

        context.deleteDatabase("testDb");
        mDb = Room.databaseBuilder(context, TestDatabase.class, "testDb")
                // create contention for callback
                .setAutoCloseTimeout(0, TimeUnit.MILLISECONDS)
                .addCallback(mCallback).build();

        AtomicInteger userCount = new AtomicInteger(0);

        UserTableObserver userTableObserver = new UserTableObserver(
                () -> userCount.set(mUserDao.count()));

        mDb.getInvalidationTracker().addObserver(userTableObserver);

        mDb.getUserDao().insert(TestUtil.createUser(1));
        mDb.getUserDao().insert(TestUtil.createUser(2));
        mDb.getUserDao().insert(TestUtil.createUser(3));
        mDb.getUserDao().insert(TestUtil.createUser(4));
        mDb.getUserDao().insert(TestUtil.createUser(5));
        mDb.getUserDao().insert(TestUtil.createUser(6));
        mDb.getUserDao().insert(TestUtil.createUser(7));

        drain();
        assertEquals(7, userCount.get());
    }

    @Test
    @MediumTest
    public void invalidationObserver_notifiedByTableName() throws TimeoutException,
            InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();

        context.deleteDatabase("testDb");
        mDb = Room.databaseBuilder(context, TestDatabase.class, "testDb")
                // create contention for callback
                .setAutoCloseTimeout(0, TimeUnit.MILLISECONDS)
                .addCallback(mCallback).build();

        AtomicInteger invalidationCount = new AtomicInteger(0);

        UserTableObserver userTableObserver =
                new UserTableObserver(invalidationCount::getAndIncrement);

        mDb.getInvalidationTracker().addObserver(userTableObserver);


        mDb.getUserDao().insert(TestUtil.createUser(1));

        drain();
        assertEquals(1, invalidationCount.get());

        Thread.sleep(100); // Let db auto close

        mDb.getInvalidationTracker().notifyObserversByTableNames("user");

        drain();
        assertEquals(2, invalidationCount.get());

    }

    private void drain() throws TimeoutException, InterruptedException {
        mExecutorRule.drainTasks(1, TimeUnit.MINUTES);
    }

    private class MyTestObserver<T> extends TestObserver<T> {
        @Override
        protected void drain() throws TimeoutException, InterruptedException {
            AutoClosingRoomOpenHelperTest.this.drain();
        }
    }

    private static class ExecSqlInCallback extends RoomDatabase.Callback {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            db.query("select * from user").close();
        }
    }

    private static class UserTableObserver extends InvalidationTracker.Observer {

        private final Runnable mInvalidationCallback;

        UserTableObserver(Runnable invalidationCallback) {
            super("user");
            mInvalidationCallback = invalidationCallback;
        }

        @Override
        public void onInvalidated(@NonNull @NotNull Set<String> tables) {
            mInvalidationCallback.run();
        }
    }
}
