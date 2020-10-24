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

package androidx.paging

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LivePagedListTest {
    @JvmField
    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun toLiveData_dataSourceConfig() {
        @Suppress("DEPRECATION")
        val livePagedList = dataSourceFactory.toLiveData(config)
        livePagedList.observeForever {}
        assertNotNull(livePagedList.value)
        assertEquals(config, livePagedList.value!!.config)
    }

    @Test
    fun toLiveData_dataSourcePageSize() {
        @Suppress("DEPRECATION")
        val livePagedList = dataSourceFactory.toLiveData(24)
        livePagedList.observeForever {}
        assertNotNull(livePagedList.value)
        assertEquals(24, livePagedList.value!!.config.pageSize)
    }

    @Test
    fun toLiveData_pagingSourceConfig() {
        @Suppress("DEPRECATION")
        val livePagedList = pagingSourceFactory.toLiveData(config)
        livePagedList.observeForever {}
        assertNotNull(livePagedList.value)
        assertEquals(config, livePagedList.value!!.config)
    }

    @Test
    fun toLiveData_pagingSourcePageSize() {
        @Suppress("DEPRECATION")
        val livePagedList = pagingSourceFactory.toLiveData(24)
        livePagedList.observeForever {}
        assertNotNull(livePagedList.value)
        assertEquals(24, livePagedList.value!!.config.pageSize)
    }

    companion object {
        @Suppress("DEPRECATION")
        private val dataSource = object : PositionalDataSource<String>() {
            override fun loadInitial(
                params: LoadInitialParams,
                callback: LoadInitialCallback<String>
            ) {
            }

            override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<String>) {}
        }

        private val dataSourceFactory = object : DataSource.Factory<Int, String>() {
            override fun create(): DataSource<Int, String> = dataSource
        }

        private val pagingSourceFactory = dataSourceFactory.asPagingSourceFactory(
            fetchDispatcher = Dispatchers.Main
        )

        private val config = Config(10)
    }
}
