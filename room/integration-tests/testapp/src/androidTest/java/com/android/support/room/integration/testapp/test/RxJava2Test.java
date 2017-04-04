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

package com.android.support.room.integration.testapp.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.support.executors.AppToolkitTaskExecutor;
import com.android.support.executors.TaskExecutor;
import com.android.support.room.integration.testapp.vo.User;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subscribers.TestSubscriber;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RxJava2Test extends TestDatabaseTest {

    private TestScheduler mTestScheduler;

    @Before
    public void setupSchedulers() {
        mTestScheduler = new TestScheduler();
        mTestScheduler.start();
        AppToolkitTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(Runnable runnable) {
                mTestScheduler.scheduleDirect(runnable);
            }

            @Override
            public void postToMainThread(Runnable runnable) {
                Assert.fail("no main thread in this test");
            }

            @Override
            public boolean isMainThread() {
                return false;
            }
        });
    }

    @After
    public void clearSchedulers() {
        mTestScheduler.shutdown();
        AppToolkitTaskExecutor.getInstance().setDelegate(null);
    }

    private void drain() throws InterruptedException {
        mTestScheduler.triggerActions();
    }

    @Test
    public void observeOnce() throws InterruptedException {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        drain();
        TestSubscriber<User> consumer = new TestSubscriber<>();
        Disposable disposable = mUserDao.flowableUserById(3).subscribeWith(consumer);
        drain();
        consumer.assertValue(user);
        disposable.dispose();
    }

    @Test
    public void observeChangeAndDispose() throws InterruptedException {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        drain();
        TestSubscriber<User> consumer = new TestSubscriber<>();
        Disposable disposable = mUserDao.flowableUserById(3).observeOn(mTestScheduler)
                .subscribeWith(consumer);
        drain();
        assertThat(consumer.values().get(0), is(user));
        user.setName("rxy");
        mUserDao.insertOrReplace(user);
        drain();
        User next = consumer.values().get(1);
        assertThat(next, is(user));
        disposable.dispose();
        user.setName("foo");
        mUserDao.insertOrReplace(user);
        drain();
        assertThat(consumer.valueCount(), is(2));
    }

    @Test
    @MediumTest
    public void observeEmpty() throws InterruptedException {
        TestSubscriber<User> consumer = new TestSubscriber<>();
        Disposable disposable = mUserDao.flowableUserById(3).observeOn(mTestScheduler)
                .subscribeWith(consumer);
        drain();
        consumer.assertNoValues();
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        drain();
        assertThat(consumer.values().get(0), is(user));
        disposable.dispose();
        user.setAge(88);
        mUserDao.insertOrReplace(user);
        drain();
        assertThat(consumer.valueCount(), is(1));
    }

    @Test
    public void flowableCountUsers() throws InterruptedException {
        TestSubscriber<Integer> consumer = new TestSubscriber<>();
        mUserDao.flowableCountUsers()
                .observeOn(mTestScheduler)
                .subscribe(consumer);
        drain();
        assertThat(consumer.values().get(0), is(0));
        mUserDao.insertAll(TestUtil.createUsersArray(1, 3, 4, 6));
        drain();
        assertThat(consumer.values().get(1), is(4));
        mUserDao.deleteByUids(3, 7);
        drain();
        assertThat(consumer.values().get(2), is(3));
        mUserDao.deleteByUids(101);
        drain();
        assertThat(consumer.valueCount(), is(3));
    }

    @Test
    @MediumTest
    public void publisherCountUsers() throws InterruptedException {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        mUserDao.publisherCountUsers().subscribe(subscriber);
        drain();
        subscriber.assertSubscribed();
        subscriber.request(2);
        drain();
        subscriber.assertValue(0);
        mUserDao.insert(TestUtil.createUser(2));
        drain();
        subscriber.assertValues(0, 1);
        subscriber.cancel();
        subscriber.assertNoErrors();
    }
}
