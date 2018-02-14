/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.arch.persistence.room.integration.kotlintestapp.test

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Database
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Insert
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.RoomWarnings
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemWithNullableConstructor {
    lateinit var db: Db
    @Before
    fun initDb() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getTargetContext(),
                Db::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertWithNull() {
        db.dao.insert(TestItem(null, null))
        assertThat(db.dao.get(), `is`(TestItem(1, null)))
    }

    @Entity
    data class TestItem(
            @PrimaryKey(autoGenerate = true)
            val id: Long? = null,
            val nullable: Boolean?
    )

    @Dao
    interface TestDao {
        @Insert
        fun insert(testItem: TestItem)

        @Query("SELECT * FROM TestItem LIMIT 1")
        fun get(): TestItem?
    }

    @Database(
            version = 1,
            entities = [TestItem::class],
            exportSchema = false
    )
    @SuppressWarnings(RoomWarnings.MISSING_SCHEMA_LOCATION)
    abstract class Db : RoomDatabase() {
        abstract val dao: TestDao
    }
}