/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.arch.persistence.room.integration.testapp.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertTrue;

import android.arch.persistence.room.Room;
import android.arch.persistence.room.integration.testapp.TestDatabase;
import android.arch.persistence.room.integration.testapp.dao.BlobEntityDao;
import android.arch.persistence.room.integration.testapp.dao.UserDao;
import android.arch.persistence.room.integration.testapp.vo.BlobEntity;
import android.arch.persistence.room.integration.testapp.vo.User;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimpleEntityReadWriteTest {
    private UserDao mUserDao;
    private BlobEntityDao mBlobEntityDao;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getTargetContext();
        TestDatabase db = Room.inMemoryDatabaseBuilder(context, TestDatabase.class).build();
        mUserDao = db.getUserDao();
        mBlobEntityDao = db.getBlobEntityDao();
    }

    @Test
    public void writeUserAndReadInList() throws Exception {
        User user = TestUtil.createUser(3);
        user.setName("george");
        mUserDao.insert(user);
        List<User> byName = mUserDao.findUsersByName("george");
        assertThat(byName.get(0), equalTo(user));
    }

    @Test
    public void throwExceptionOnConflict() {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);

        User user2 = TestUtil.createUser(3);
        try {
            mUserDao.insert(user2);
            throw new AssertionFailedError("didn't throw in conflicting insertion");
        } catch (SQLiteException ignored) {
        }
    }

    @Test
    public void replaceOnConflict() {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);

        User user2 = TestUtil.createUser(3);
        mUserDao.insertOrReplace(user2);

        assertThat(mUserDao.load(3), equalTo(user2));
        assertThat(mUserDao.load(3), not(equalTo(user)));
    }

    @Test
    public void updateSimple() {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        user.setName("i am an updated name");
        assertThat(mUserDao.update(user), is(1));
        assertThat(mUserDao.load(user.getId()), equalTo(user));
    }

    @Test
    public void updateNonExisting() {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        User user2 = TestUtil.createUser(4);
        assertThat(mUserDao.update(user2), is(0));
    }

    @Test
    public void updateList() {
        List<User> users = TestUtil.createUsersList(3, 4, 5);
        mUserDao.insertAll(users.toArray(new User[3]));
        for (User user : users) {
            user.setName("name " + user.getId());
        }
        assertThat(mUserDao.updateAll(users), is(3));
        for (User user : users) {
            assertThat(mUserDao.load(user.getId()).getName(), is("name " + user.getId()));
        }
    }

    @Test
    public void updateListPartial() {
        List<User> existingUsers = TestUtil.createUsersList(3, 4, 5);
        mUserDao.insertAll(existingUsers.toArray(new User[3]));
        for (User user : existingUsers) {
            user.setName("name " + user.getId());
        }
        List<User> allUsers = TestUtil.createUsersList(7, 8, 9);
        allUsers.addAll(existingUsers);
        assertThat(mUserDao.updateAll(allUsers), is(3));
        for (User user : existingUsers) {
            assertThat(mUserDao.load(user.getId()).getName(), is("name " + user.getId()));
        }
    }

    @Test
    public void delete() {
        User user = TestUtil.createUser(3);
        mUserDao.insert(user);
        assertThat(mUserDao.delete(user), is(1));
        assertThat(mUserDao.delete(user), is(0));
        assertThat(mUserDao.load(3), is(nullValue()));
    }

    @Test
    public void deleteAll() {
        User[] users = TestUtil.createUsersArray(3, 5, 7, 9);
        mUserDao.insertAll(users);
        // there is actually no guarantee for this order by works fine since they are ordered for
        // the test and it is a new database (no pages to recycle etc)
        assertThat(mUserDao.loadByIds(3, 5, 7, 9), is(users));
        int deleteCount = mUserDao.deleteAll(new User[]{users[0], users[3],
                TestUtil.createUser(9)});
        assertThat(deleteCount, is(2));
        assertThat(mUserDao.loadByIds(3, 5, 7, 9), is(new User[]{users[1], users[2]}));
    }

    @Test
    public void findByBoolean() {
        User user1 = TestUtil.createUser(3);
        user1.setAdmin(true);
        User user2 = TestUtil.createUser(5);
        user2.setAdmin(false);
        mUserDao.insert(user1);
        mUserDao.insert(user2);
        assertThat(mUserDao.findByAdmin(true), is(Arrays.asList(user1)));
        assertThat(mUserDao.findByAdmin(false), is(Arrays.asList(user2)));
    }

    @Test
    public void deleteByAge() {
        User user1 = TestUtil.createUser(3);
        user1.setAge(30);
        User user2 = TestUtil.createUser(5);
        user2.setAge(45);
        mUserDao.insert(user1);
        mUserDao.insert(user2);
        assertThat(mUserDao.deleteAgeGreaterThan(60), is(0));
        assertThat(mUserDao.deleteAgeGreaterThan(45), is(0));
        assertThat(mUserDao.deleteAgeGreaterThan(35), is(1));
        assertThat(mUserDao.loadByIds(3, 5), is(new User[]{user1}));
    }

    @Test
    public void deleteByAgeRange() {
        User user1 = TestUtil.createUser(3);
        user1.setAge(30);
        User user2 = TestUtil.createUser(5);
        user2.setAge(45);
        mUserDao.insert(user1);
        mUserDao.insert(user2);
        assertThat(mUserDao.deleteByAgeRange(35, 40), is(0));
        assertThat(mUserDao.deleteByAgeRange(25, 30), is(1));
        assertThat(mUserDao.loadByIds(3, 5), is(new User[]{user2}));
    }

    @Test
    public void deleteByUIds() {
        User[] users = TestUtil.createUsersArray(3, 5, 7, 9, 11);
        mUserDao.insertAll(users);
        assertThat(mUserDao.deleteByUids(2, 4, 6), is(0));
        assertThat(mUserDao.deleteByUids(3, 11), is(2));
        assertThat(mUserDao.loadByIds(3, 5, 7, 9, 11), is(new User[]{
                users[1], users[2], users[3]
        }));
    }

    @Test
    public void updateNameById() {
        User[] usersArray = TestUtil.createUsersArray(3, 5, 7);
        mUserDao.insertAll(usersArray);
        assertThat("test sanity", usersArray[1].getName(), not(equalTo("updated name")));
        int changed = mUserDao.updateById(5, "updated name");
        assertThat(changed, is(1));
        assertThat(mUserDao.load(5).getName(), is("updated name"));
    }

    @Test
    public void incrementIds() {
        User[] usersArr = TestUtil.createUsersArray(2, 4, 6);
        mUserDao.insertAll(usersArr);
        mUserDao.incrementIds(1);
        assertThat(mUserDao.loadIds(), is(Arrays.asList(3, 5, 7)));
    }

    @Test
    public void findByIntQueryParameter() {
        User user = TestUtil.createUser(1);
        final String name = "my name";
        user.setName(name);
        mUserDao.insert(user);
        assertThat(mUserDao.findByNameLength(name.length()), is(Collections.singletonList(user)));
    }

    @Test
    public void findByIntFieldMatch() {
        User user = TestUtil.createUser(1);
        user.setAge(19);
        mUserDao.insert(user);
        assertThat(mUserDao.findByAge(19), is(Collections.singletonList(user)));
    }

    @Test
    public void customConverterField() {
        User user = TestUtil.createUser(20);
        Date theDate = new Date(System.currentTimeMillis() - 200);
        user.setBirthday(theDate);
        mUserDao.insert(user);
        assertThat(mUserDao.findByBirthdayRange(new Date(theDate.getTime() - 100),
                new Date(theDate.getTime() + 1)).get(0), is(user));
        assertThat(mUserDao.findByBirthdayRange(new Date(theDate.getTime()),
                new Date(theDate.getTime() + 1)).size(), is(0));
    }

    @Test
    public void renamedField() {
        User user = TestUtil.createUser(3);
        user.setCustomField("foo laaa");
        mUserDao.insertOrReplace(user);
        User loaded = mUserDao.load(3);
        assertThat(loaded.getCustomField(), is("foo laaa"));
        assertThat(loaded, is(user));
    }

    @Test
    public void readViaCursor() {
        User[] users = TestUtil.createUsersArray(3, 5, 7, 9);
        mUserDao.insertAll(users);
        Cursor cursor = mUserDao.findUsersAsCursor(3, 5, 9);
        try {
            assertThat(cursor.getCount(), is(3));
            assertThat(cursor.moveToNext(), is(true));
            assertThat(cursor.getInt(0), is(3));
            assertThat(cursor.moveToNext(), is(true));
            assertThat(cursor.getInt(0), is(5));
            assertThat(cursor.moveToNext(), is(true));
            assertThat(cursor.getInt(0), is(9));
            assertThat(cursor.moveToNext(), is(false));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void readDirectWithTypeAdapter() {
        User user = TestUtil.createUser(3);
        user.setBirthday(null);
        mUserDao.insert(user);
        assertThat(mUserDao.getBirthday(3), is(nullValue()));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 3);
        Date birthday = calendar.getTime();
        user.setBirthday(birthday);

        mUserDao.update(user);
        assertThat(mUserDao.getBirthday(3), is(birthday));
    }

    @Test
    public void emptyInQuery() {
        User[] users = mUserDao.loadByIds();
        assertThat(users, is(new User[0]));
    }

    @Test
    public void blob() {
        BlobEntity a = new BlobEntity(1, "abc".getBytes());
        BlobEntity b = new BlobEntity(2, "def".getBytes());
        mBlobEntityDao.insert(a);
        mBlobEntityDao.insert(b);
        List<BlobEntity> list = mBlobEntityDao.selectAll();
        assertThat(list, hasSize(2));
        mBlobEntityDao.updateContent(2, "ghi".getBytes());
        assertThat(mBlobEntityDao.getContent(2), is(equalTo("ghi".getBytes())));
    }

    @Test
    public void transactionByRunnable() {
        User a = TestUtil.createUser(3);
        User b = TestUtil.createUser(5);
        mUserDao.insertBothByRunnable(a, b);
        assertThat(mUserDao.count(), is(2));
    }

    @Test
    public void transactionByRunnable_failure() {
        User a = TestUtil.createUser(3);
        User b = TestUtil.createUser(3);
        boolean caught = false;
        try {
            mUserDao.insertBothByRunnable(a, b);
        } catch (SQLiteConstraintException e) {
            caught = true;
        }
        assertTrue("SQLiteConstraintException expected", caught);
        assertThat(mUserDao.count(), is(0));
    }

    @Test
    public void transactionByCallable() {
        User a = TestUtil.createUser(3);
        User b = TestUtil.createUser(5);
        int count = mUserDao.insertBothByCallable(a, b);
        assertThat(mUserDao.count(), is(2));
        assertThat(count, is(2));
    }

    @Test
    public void transactionByCallable_failure() {
        User a = TestUtil.createUser(3);
        User b = TestUtil.createUser(3);
        boolean caught = false;
        try {
            mUserDao.insertBothByCallable(a, b);
        } catch (SQLiteConstraintException e) {
            caught = true;
        }
        assertTrue("SQLiteConstraintException expected", caught);
        assertThat(mUserDao.count(), is(0));
    }
}
