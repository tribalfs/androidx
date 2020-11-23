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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import android.content.Context;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EnumColumnTypeAdapterTest {

    private EnumColumnTypeAdapterDatabase mDb;

    @Entity
    public static class EntityWithEnum {
        @PrimaryKey
        public Long id;
        public Fruit fruit;
    }

    public enum Fruit {
        BANANA,
        STRAWBERRY,
        WILDBERRY
    }

    @Dao
    public interface SampleDao {
        @Query("INSERT INTO EntityWithEnum (id, fruit) VALUES (:id, :fruit)")
        long insert(long id, Fruit fruit);

        @Query("SELECT * FROM EntityWithEnum WHERE id = :id")
        EntityWithEnum getValueWithId(long id);
    }

    @Database(entities = {EntityWithEnum.class}, version = 1, exportSchema = false)
    public abstract static class EnumColumnTypeAdapterDatabase extends RoomDatabase {
        public abstract EnumColumnTypeAdapterTest.SampleDao dao();
    }

    @Before
    public void initDb() {
        Context context = ApplicationProvider.getApplicationContext();
        mDb = Room.inMemoryDatabaseBuilder(
                context,
                EnumColumnTypeAdapterDatabase.class)
                .build();
    }

    @Test
    public void readAndWriteEnumToDatabase() {
        final long id1 = mDb.dao().insert(1, Fruit.BANANA);
        final long id2 = mDb.dao().insert(2, Fruit.STRAWBERRY);

        assertThat(mDb.dao().getValueWithId(1).fruit, is(equalTo(Fruit.BANANA)));
        assertThat(mDb.dao().getValueWithId(2).fruit, is(equalTo(Fruit.STRAWBERRY)));
    }
}
