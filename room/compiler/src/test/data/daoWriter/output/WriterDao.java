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

package foo.bar;

import com.android.support.db.SupportSqliteStatement;
import com.android.support.room.EntityInsertionAdapter;
import com.android.support.room.RoomDatabase;
import java.lang.Override;
import java.lang.String;
import java.util.List;

public class WriterDao_Impl implements WriterDao {
    private final RoomDatabase __db;

    private final EntityInsertionAdapter __insertionAdapterOfUser;

    private final EntityInsertionAdapter __insertionAdapterOfUser_1;

    public WriterDao_Impl(RoomDatabase __db) {
        this.__db = __db;
        this.__insertionAdapterOfUser = new EntityInsertionAdapter<User>(__db) {
            @Override
            public String createInsertQuery() {
                return "INSERT OR ABORT INTO `User`(`uid`) VALUES (?)";
            }

            @Override
            public void bind(SupportSqliteStatement stmt, User value) {
                stmt.bindLong(0, value.uid);
            }
        };

        this.__insertionAdapterOfUser_1 = new EntityInsertionAdapter<User>(__db) {
            @Override
            public String createInsertQuery() {
                return "INSERT OR REPLACE INTO `User`(`uid`) VALUES (?)";
            }

            @Override
            public void bind(SupportSqliteStatement stmt, User value) {
                stmt.bindLong(0, value.uid);
            }
        };
    }

    @Override
    public void insertUser(User user) {
        __db.beginTransaction();
        try {
            __insertionAdapterOfUser.insert(user);
            __db.setTransactionSuccessful();
        } finally {
            __db.endTransaction();
        }
    }

    @Override
    public void insertUsers(User user1, List<User> others) {
        __db.beginTransaction();
        try {
            __insertionAdapterOfUser.insert(user1);
            __insertionAdapterOfUser.insert(others);
            __db.setTransactionSuccessful();
        } finally {
            __db.endTransaction();
        }
    }

    @Override
    public void insertUsers(User[] users) {
        __db.beginTransaction();
        try {
            __insertionAdapterOfUser_1.insert(users);
            __db.setTransactionSuccessful();
        } finally {
            __db.endTransaction();
        }
    }
}